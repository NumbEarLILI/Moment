package com.example.moment.data.nearby

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import com.example.moment.domain.nearby.BleAdvertisementPolicy
import com.example.moment.domain.nearby.BleConnectPolicy
import com.example.moment.domain.nearby.BleFrameCodec
import com.example.moment.domain.nearby.BleLinkAttachPolicy
import com.example.moment.domain.nearby.BleMeshIds
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 蓝牙去中心组网：每台设备同时当外围（广播 + GATT 服务）和中心（扫描 + 主动连接）。
 *
 * 谁发起连接由 [BleConnectPolicy] 决定，避免 A、B 各连一次占两条链路。
 * 消息怎么转发不归这里管。
 */
@Singleton
class BleMeshController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = manager?.adapter

    val isSupported: Boolean
        get() = adapter != null &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    val isAdvertisingSupported: Boolean
        get() = BleAdvertisementPolicy.shouldAdvertise(
            hasAdvertiser = adapter?.bluetoothLeAdvertiser != null,
            multipleAdvertisementSupported = adapter?.isMultipleAdvertisementSupported == true
        )

    val isEnabled: Boolean
        get() = adapter?.isEnabled == true

    /**
     * 开始广播、扫描并自动连邻居。[onLink] 可能从蓝牙回调线程进来。
     * 挂起到协程取消，取消后拆掉广播、扫描和所有 GATT。
     */
    @SuppressLint("MissingPermission")
    suspend fun run(selfNodeId: String, onLink: (NearbyLink) -> Unit) {
        coroutineScope {
            val adapter = adapter ?: error("这台设备没有蓝牙")
            if (!adapter.isEnabled) error("请先打开蓝牙")
            val session = Session(
                scope = this,
                selfNodeId = selfNodeId,
                canAdvertise = isAdvertisingSupported,
                onLink = onLink
            )
            try {
                session.start()
                awaitCancellation()
            } finally {
                session.stop()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private inner class Session(
        private val scope: CoroutineScope,
        private val selfNodeId: String,
        private val canAdvertise: Boolean,
        private val onLink: (NearbyLink) -> Unit
    ) {
        private val selfBytes = BleMeshIds.nodeIdBytes(selfNodeId)
        private val advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser
        private val scanner: BluetoothLeScanner? = adapter?.bluetoothLeScanner
        private var gattServer: BluetoothGattServer? = null
        private var advertising = false
        private var scanning = false
        private var advertiseWithoutScanResponse = false
        private var unfilteredScan = false
        private val serviceAdded = CompletableDeferred<Boolean>()

        private val occupied = ConcurrentHashMap.newKeySet<String>()
        private val links = ConcurrentHashMap<String, BleMeshLink>()
        private val assemblers = ConcurrentHashMap<String, BleFrameCodec.Assembler>()
        private val writers = ConcurrentHashMap<String, GattChunkWriter>()
        private val clientGatts = ConcurrentHashMap<String, BluetoothGatt>()
        private val seenPeers = ConcurrentHashMap<String, SeenPeer>()
        private val connecting = ConcurrentHashMap.newKeySet<String>()
        private val publishedLinks = ConcurrentHashMap.newKeySet<String>()
        private val negotiatedMtu = ConcurrentHashMap<String, Int>()

        private val advertiseCallback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                advertising = false
                if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE && !advertiseWithoutScanResponse) {
                    advertiseWithoutScanResponse = true
                    startAdvertising(includeScanResponse = false)
                }
            }
        }
        private val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onScan(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::onScan)
            }

            override fun onScanFailed(errorCode: Int) {
                scanning = false
                if (!unfilteredScan) {
                    unfilteredScan = true
                    startScanning(filtered = false)
                }
            }
        }

        suspend fun start() {
            openGattServer()
            withTimeoutOrNull(SERVICE_READY_MS) { serviceAdded.await() }
            startAdvertising(includeScanResponse = true)
            startScanning(filtered = true)
            scope.launch { connectLoop() }
        }

        fun stop() {
            runCatching { if (scanning) scanner?.stopScan(scanCallback) }
            scanning = false
            runCatching { if (advertising) advertiser?.stopAdvertising(advertiseCallback) }
            advertising = false
            clientGatts.values.toList().forEach { gatt ->
                runCatching { gatt.disconnect() }
                runCatching { gatt.close() }
            }
            clientGatts.clear()
            links.values.toList().forEach { it.close() }
            links.clear()
            runCatching { gattServer?.close() }
            gattServer = null
            occupied.clear()
            writers.clear()
            assemblers.clear()
            seenPeers.clear()
            connecting.clear()
            publishedLinks.clear()
            negotiatedMtu.clear()
        }

        private fun openGattServer() {
            val server = manager?.openGattServer(context, serverCallback)
            if (server == null) {
                serviceAdded.complete(false)
                return
            }
            val service = BluetoothGattService(BleMeshIds.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val inbox = BluetoothGattCharacteristic(
                BleMeshIds.INBOX,
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            val outbox = BluetoothGattCharacteristic(
                BleMeshIds.OUTBOX,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            outbox.addDescriptor(
                BluetoothGattDescriptor(
                    BleMeshIds.CCCD,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
            )
            service.addCharacteristic(inbox)
            service.addCharacteristic(outbox)
            if (!server.addService(service)) {
                serviceAdded.complete(false)
            }
            gattServer = server
        }

        private fun startAdvertising(includeScanResponse: Boolean) {
            val advertiser = advertiser ?: return
            if (!canAdvertise) return
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0)
                .build()
            val data = AdvertiseData.Builder()
                .addManufacturerData(BleMeshIds.MANUFACTURER_ID, selfBytes)
                .setIncludeDeviceName(false)
                .build()
            val scanResponse = if (includeScanResponse) {
                AdvertiseData.Builder()
                    .addServiceUuid(ParcelUuid(BleMeshIds.SERVICE))
                    .setIncludeDeviceName(false)
                    .build()
            } else {
                null
            }
            runCatching {
                if (scanResponse != null) {
                    advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
                } else {
                    advertiser.startAdvertising(settings, data, advertiseCallback)
                }
                advertising = true
            }
        }

        private fun startScanning(filtered: Boolean) {
            val scanner = scanner ?: return
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            val filters = if (filtered) {
                listOf(
                    ScanFilter.Builder()
                        .setManufacturerData(
                            BleMeshIds.MANUFACTURER_ID,
                            ByteArray(16),
                            ByteArray(16)
                        )
                        .build(),
                    ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(BleMeshIds.SERVICE))
                        .build()
                )
            } else {
                emptyList()
            }
            runCatching {
                scanner.startScan(filters, settings, scanCallback)
                scanning = true
            }.onFailure {
                if (filtered && !unfilteredScan) {
                    unfilteredScan = true
                    startScanning(filtered = false)
                }
            }
        }

        private fun onScan(result: ScanResult) {
            val address = result.device?.address ?: return
            val record = result.scanRecord
            val peerId = BleAdvertisementPolicy.peerNodeId(
                manufacturerData = record?.getManufacturerSpecificData(BleMeshIds.MANUFACTURER_ID),
                hasServiceUuid = record?.serviceUuids?.any { it.uuid == BleMeshIds.SERVICE } == true,
                address = address
            ) ?: return
            if (peerId == selfNodeId) return
            val now = System.currentTimeMillis()
            seenPeers.compute(peerId) { _, existing ->
                existing?.copy(address = address, lastSeenMs = now)
                    ?: SeenPeer(nodeId = peerId, address = address, firstSeenMs = now, lastSeenMs = now)
            }
        }

        private suspend fun connectLoop() {
            while (true) {
                val now = System.currentTimeMillis()
                seenPeers.values
                    .filter { now - it.lastSeenMs < PEER_STALE_MS }
                    .forEach { peer ->
                        maybeConnect(peer, now)
                    }
                delay(CONNECT_TICK_MS)
            }
        }

        private fun maybeConnect(peer: SeenPeer, now: Long) {
            if (occupied.contains(peer.address) || connecting.contains(peer.address)) return
            if (links.size >= BleConnectPolicy.MAX_NEIGHBORS) return
            if (!BleConnectPolicy.shouldInitiate(
                    selfNodeId = selfNodeId,
                    peerNodeId = peer.nodeId,
                    canAdvertise = canAdvertise,
                    waitingMs = now - peer.firstSeenMs
                )
            ) {
                return
            }
            val device = runCatching { adapter?.getRemoteDevice(peer.address) }.getOrNull() ?: return
            if (!connecting.add(peer.address)) return
            connectAsClient(device)
        }

        private fun connectAsClient(device: BluetoothDevice) {
            val callback = ClientCallback(device.address)
            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, callback)
            }
            if (gatt == null) {
                connecting.remove(device.address)
                return
            }
            clientGatts[device.address] = gatt
            callback.gatt = gatt
        }

        private fun attachServerEndpoint(device: BluetoothDevice) {
            val address = device.address ?: return
            if (!occupied.add(address)) return
            val writer = GattChunkWriter { chunk -> notifyDevice(device, chunk) }
            writers[address] = writer
            assemblers[address] = BleFrameCodec.Assembler()
            val link = BleMeshLink(
                writeChunk = writer::write,
                onClose = { drop(address, disconnectClient = false, cancelServer = true) }
            )
            links[address] = link
            applyNegotiatedMtu(address)
        }

        private fun attachClientLink(gatt: BluetoothGatt, inbox: BluetoothGattCharacteristic) {
            val address = gatt.device.address ?: return
            if (!occupied.add(address)) {
                runCatching { gatt.disconnect() }
                return
            }
            val writer = GattChunkWriter { chunk -> writeCharacteristic(gatt, inbox, chunk) }
            writers[address] = writer
            assemblers[address] = BleFrameCodec.Assembler()
            val link = BleMeshLink(
                writeChunk = writer::write,
                onClose = { drop(address, disconnectClient = true, cancelServer = false) }
            )
            links[address] = link
            applyNegotiatedMtu(address)
            publishLink(address)
        }

        private fun publishLink(address: String) {
            val link = links[address] ?: return
            if (!publishedLinks.add(address)) return
            onLink(link)
        }

        private fun isOutgoingClient(address: String): Boolean =
            connecting.contains(address) || clientGatts.containsKey(address)

        private fun applyNegotiatedMtu(address: String) {
            negotiatedMtu[address]?.let { mtu ->
                links[address]?.updateChunkSize(chunkSizeForMtu(mtu))
            }
        }

        private fun drop(address: String, disconnectClient: Boolean, cancelServer: Boolean) {
            links.remove(address)?.let { runCatching { it.close() } }
            writers.remove(address)
            assemblers.remove(address)
            occupied.remove(address)
            connecting.remove(address)
            publishedLinks.remove(address)
            negotiatedMtu.remove(address)
            if (disconnectClient) {
                clientGatts.remove(address)?.let { gatt ->
                    runCatching { gatt.disconnect() }
                    runCatching { gatt.close() }
                }
            }
            if (cancelServer) {
                val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
                if (device != null) runCatching { gattServer?.cancelConnection(device) }
            }
        }

        private fun onIncomingChunk(address: String, chunk: ByteArray) {
            val assembler = assemblers[address] ?: return
            val payload = runCatching { assembler.push(chunk) }.getOrNull() ?: return
            links[address]?.offerPayload(payload)
        }

        private fun notifyDevice(device: BluetoothDevice, chunk: ByteArray): Boolean {
            val server = gattServer ?: return false
            val characteristic = server.getService(BleMeshIds.SERVICE)
                ?.getCharacteristic(BleMeshIds.OUTBOX) ?: return false
            @Suppress("DEPRECATION")
            characteristic.value = chunk
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                server.notifyCharacteristicChanged(device, characteristic, false, chunk) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                server.notifyCharacteristicChanged(device, characteristic, false)
            }
        }

        private fun writeCharacteristic(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            chunk: ByteArray
        ): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    characteristic,
                    chunk,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                characteristic.value = chunk
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }

        private val serverCallback = object : BluetoothGattServerCallback() {
            override fun onServiceAdded(status: Int, service: BluetoothGattService) {
                serviceAdded.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                val address = device.address ?: return
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (!BleLinkAttachPolicy.shouldAcceptAsServer(outgoingClient = isOutgoingClient(address))) {
                        return
                    }
                    attachServerEndpoint(device)
                    scope.launch {
                        delay(CCCD_WAIT_MS)
                        publishLink(address)
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    drop(address, disconnectClient = false, cancelServer = false)
                }
            }

            override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
                negotiatedMtu[device.address] = mtu
                links[device.address]?.updateChunkSize(chunkSizeForMtu(mtu))
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (characteristic.uuid == BleMeshIds.INBOX && value != null) {
                    onIncomingChunk(device.address, value)
                }
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (descriptor.uuid == BleMeshIds.CCCD) {
                    publishLink(device.address)
                }
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            }

            override fun onNotificationSent(device: BluetoothDevice, status: Int) {
                writers[device.address]?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        private inner class ClientCallback(private val address: String) : BluetoothGattCallback() {
            var gatt: BluetoothGatt? = null
            private val startedDiscovery = AtomicBoolean(false)
            private val cccdAck = CompletableDeferred<Boolean>()
            private val published = AtomicBoolean(false)

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.requestMtu(517)
                    // 有的机型不回调 onMtuChanged，不能一直等。
                    scope.launch {
                        delay(800)
                        discoverOnce(gatt)
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connecting.remove(address)
                    drop(address, disconnectClient = true, cancelServer = false)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                negotiatedMtu[address] = mtu
                links[address]?.updateChunkSize(chunkSizeForMtu(mtu))
                discoverOnce(gatt)
            }

            private fun discoverOnce(gatt: BluetoothGatt) {
                if (startedDiscovery.compareAndSet(false, true)) {
                    gatt.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    gatt.disconnect()
                    return
                }
                val service = gatt.getService(BleMeshIds.SERVICE) ?: run {
                    gatt.disconnect()
                    return
                }
                val inboxChar = service.getCharacteristic(BleMeshIds.INBOX)
                val outbox = service.getCharacteristic(BleMeshIds.OUTBOX)
                if (inboxChar == null || outbox == null) {
                    gatt.disconnect()
                    return
                }
                gatt.setCharacteristicNotification(outbox, true)
                val cccd = outbox.getDescriptor(BleMeshIds.CCCD)
                if (cccd == null) {
                    finishClient(gatt, inboxChar)
                    return
                }
                val submitted = writeDescriptor(gatt, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                if (!submitted) cccdAck.complete(false)
                scope.launch {
                    withTimeoutOrNull(CCCD_WAIT_MS) { cccdAck.await() }
                    finishClient(gatt, inboxChar)
                }
            }

            private fun finishClient(gatt: BluetoothGatt, inbox: BluetoothGattCharacteristic) {
                if (!published.compareAndSet(false, true)) return
                connecting.remove(address)
                attachClientLink(gatt, inbox)
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                cccdAck.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                onIncomingChunk(address, value)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                onIncomingChunk(address, value)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                writers[address]?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        private fun writeDescriptor(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            value: ByteArray
        ): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
    }

    private data class SeenPeer(
        val nodeId: String,
        val address: String,
        val firstSeenMs: Long,
        val lastSeenMs: Long
    )

    private companion object {
        const val CONNECT_TICK_MS = 1_000L
        const val PEER_STALE_MS = 30_000L
        const val SERVICE_READY_MS = 2_000L
        const val CCCD_WAIT_MS = 2_000L

        fun chunkSizeForMtu(mtu: Int): Int = (mtu - 3 - BleFrameCodec.HEADER_SIZE).coerceAtLeast(8)
    }
}

/** 把 BLE 写入串成「发出去 → 等回调」；超时就当成功，免得某个机型不回 onNotificationSent 卡死。 */
private class GattChunkWriter(
    private val perform: (ByteArray) -> Boolean
) {
    private val mutex = Mutex()
    private var pending: CompletableDeferred<Boolean>? = null

    suspend fun write(chunk: ByteArray) {
        mutex.withLock {
            val ack = CompletableDeferred<Boolean>()
            pending = ack
            val submitted = perform(chunk)
            if (!submitted) {
                pending = null
                error("蓝牙写入未能发出")
            }
            withTimeoutOrNull(WRITE_TIMEOUT_MS) { ack.await() }
            pending = null
        }
    }

    fun complete(success: Boolean) {
        pending?.complete(success)
    }

    private companion object {
        const val WRITE_TIMEOUT_MS = 3_000L
    }
}
