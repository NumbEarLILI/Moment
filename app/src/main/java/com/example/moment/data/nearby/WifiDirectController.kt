package com.example.moment.data.nearby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.example.moment.domain.nearby.NearbyPeer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Wi-Fi Direct 组建立后的落点，用来决定谁开监听、谁去连。 */
data class WifiDirectGroup(
    val isGroupOwner: Boolean,
    val groupOwnerAddress: String
)

sealed interface WifiDirectEvent {
    data class StateChanged(val enabled: Boolean) : WifiDirectEvent

    data class PeersChanged(val peers: List<NearbyPeer>) : WifiDirectEvent

    /** [group] 为 null 表示当前没有已成型的组。 */
    data class ConnectionChanged(val group: WifiDirectGroup?) : WifiDirectEvent

    data class ThisDeviceChanged(val deviceName: String) : WifiDirectEvent
}

/**
 * 只包一层 [WifiP2pManager]：广播转成 [Flow]，回调式操作转成挂起函数。
 *
 * Wi-Fi Direct 是 AOSP 框架能力，不依赖 Google Play 服务，也不需要路由器或流量。
 */
@Singleton
class WifiDirectController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    private val channelLock = Any()
    private var cachedChannel: WifiP2pManager.Channel? = null

    val isSupported: Boolean
        get() = manager != null &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)

    fun events(): Flow<WifiDirectEvent> = callbackFlow {
        val manager = manager
        val channel = channel()
        if (manager == null || channel == null) {
            trySend(WifiDirectEvent.StateChanged(enabled = false))
            awaitClose { }
            return@callbackFlow
        }

        val peerListener = WifiP2pManager.PeerListListener { peers ->
            trySend(WifiDirectEvent.PeersChanged(peers.deviceList.map { it.toNearbyPeer() }))
        }
        val connectionListener = WifiP2pManager.ConnectionInfoListener { info ->
            val address = info?.groupOwnerAddress?.hostAddress
            val group = if (info != null && info.groupFormed && !address.isNullOrBlank()) {
                WifiDirectGroup(isGroupOwner = info.isGroupOwner, groupOwnerAddress = address)
            } else {
                null
            }
            trySend(WifiDirectEvent.ConnectionChanged(group))
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        trySend(
                            WifiDirectEvent.StateChanged(
                                enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                            )
                        )
                    }

                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION ->
                        requestPeers(manager, channel, peerListener)

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION ->
                        manager.requestConnectionInfo(channel, connectionListener)

                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        val device = IntentCompat.getParcelableExtra(
                            intent,
                            WifiP2pManager.EXTRA_WIFI_P2P_DEVICE,
                            WifiP2pDevice::class.java
                        )
                        val name = device?.deviceName.orEmpty()
                        if (name.isNotBlank()) {
                            trySend(WifiDirectEvent.ThisDeviceChanged(name))
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // 广播只在状态变化时来，先主动问一次当前值，避免进页面后一直是空白。
        requestPeers(manager, channel, peerListener)
        manager.requestConnectionInfo(channel, connectionListener)

        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }.flowOn(Dispatchers.Main.immediate)

    suspend fun discoverPeers(): Result<Unit> = runAction { manager, channel, listener ->
        manager.discoverPeers(channel, listener)
    }

    suspend fun stopDiscovery(): Result<Unit> = runAction { manager, channel, listener ->
        manager.stopPeerDiscovery(channel, listener)
    }

    suspend fun connect(deviceAddress: String): Result<Unit> = runAction { manager, channel, listener ->
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            wps.setup = WpsInfo.PBC
        }
        manager.connect(channel, config, listener)
    }

    suspend fun cancelConnect(): Result<Unit> = runAction { manager, channel, listener ->
        manager.cancelConnect(channel, listener)
    }

    suspend fun removeGroup(): Result<Unit> = runAction { manager, channel, listener ->
        manager.removeGroup(channel, listener)
    }

    private fun channel(): WifiP2pManager.Channel? {
        val manager = manager ?: return null
        synchronized(channelLock) {
            cachedChannel?.let { return it }
            // 通道断开后必须重新 initialize，否则后续所有操作都静默失败。
            val created = manager.initialize(context, Looper.getMainLooper()) {
                synchronized(channelLock) { cachedChannel = null }
            }
            cachedChannel = created
            return created
        }
    }

    private fun requestPeers(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        listener: WifiP2pManager.PeerListListener
    ) {
        try {
            manager.requestPeers(channel, listener)
        } catch (_: SecurityException) {
            // 权限尚未授予时忽略，页面会引导用户去授权。
        }
    }

    private suspend fun runAction(
        block: (WifiP2pManager, WifiP2pManager.Channel, WifiP2pManager.ActionListener) -> Unit
    ): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        val manager = manager
        val channel = channel()
        if (manager == null || channel == null) {
            return@withContext Result.failure(
                IllegalStateException("这台设备不支持 Wi-Fi 直连")
            )
        }
        suspendCancellableCoroutine { continuation ->
            val listener = object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    if (continuation.isActive) continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(WifiDirectActionException(reason)))
                    }
                }
            }
            try {
                block(manager, channel, listener)
            } catch (error: SecurityException) {
                if (continuation.isActive) continuation.resume(Result.failure(error))
            }
        }
    }
}

private fun WifiP2pDevice.toNearbyPeer(): NearbyPeer = NearbyPeer(
    deviceAddress = deviceAddress.orEmpty(),
    deviceName = deviceName?.takeIf { it.isNotBlank() } ?: "未命名设备",
    statusText = statusText(status),
    connectable = status == WifiP2pDevice.AVAILABLE || status == WifiP2pDevice.INVITED
)

private fun statusText(status: Int): String = when (status) {
    WifiP2pDevice.AVAILABLE -> "可连接"
    WifiP2pDevice.INVITED -> "已邀请"
    WifiP2pDevice.CONNECTED -> "已连接"
    WifiP2pDevice.FAILED -> "连接失败"
    WifiP2pDevice.UNAVAILABLE -> "不可用"
    else -> "未知状态"
}
