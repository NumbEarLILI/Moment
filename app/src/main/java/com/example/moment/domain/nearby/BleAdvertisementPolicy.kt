package com.example.moment.domain.nearby

import java.util.UUID

/** BLE 网格的 GATT UUID 和广播里用的厂商 ID。 */
object BleMeshIds {
    val SERVICE: UUID = UUID.fromString("6d6f6d65-6e74-4d65-7368-c0de00000001")
    val INBOX: UUID = UUID.fromString("6d6f6d65-6e74-4d65-7368-c0de00000002")
    val OUTBOX: UUID = UUID.fromString("6d6f6d65-6e74-4d65-7368-c0de00000003")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val MANUFACTURER_ID = 0x6D74

    fun nodeIdBytes(nodeId: String): ByteArray {
        val uuid = UUID.fromString(nodeId)
        val bytes = ByteArray(16)
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits
        for (i in 0..7) bytes[i] = (msb shr ((7 - i) * 8)).toByte()
        for (i in 0..7) bytes[8 + i] = (lsb shr ((7 - i) * 8)).toByte()
        return bytes
    }

    fun nodeIdFromBytes(bytes: ByteArray?): String? {
        if (bytes == null || bytes.size < 16) return null
        var msb = 0L
        var lsb = 0L
        for (i in 0..7) msb = (msb shl 8) or (bytes[i].toLong() and 0xFF)
        for (i in 8..15) lsb = (lsb shl 8) or (bytes[i].toLong() and 0xFF)
        return UUID(msb, lsb).toString()
    }
}

/**
 * 传统 BLE 广播只有 31 字节。Service UUID（18）和 16 字节节点 ID（20）加 Flags（3）塞不进同一包。
 * 节点 ID 必须放在主广播里，设备名不能进广播，否则很多手机直接 ADVERTISE_FAILED_DATA_TOO_LARGE。
 */
object BleAdvertisementPolicy {
    const val LEGACY_ADV_MAX_BYTES = 31
    const val FLAGS_BYTES = 3
    const val MANUFACTURER_OVERHEAD_BYTES = 4
    const val SERVICE_UUID_128_FIELD_BYTES = 18

    fun manufacturerFieldBytes(payloadSize: Int): Int =
        if (payloadSize <= 0) 0 else MANUFACTURER_OVERHEAD_BYTES + payloadSize

    fun serviceUuid128FieldBytes(): Int = SERVICE_UUID_128_FIELD_BYTES

    fun deviceNameFieldBytes(nameUtf8Length: Int): Int =
        if (nameUtf8Length <= 0) 0 else 2 + nameUtf8Length

    fun primaryPacketBytes(payloadSize: Int): Int =
        FLAGS_BYTES + manufacturerFieldBytes(payloadSize)

    fun scanResponsePacketBytes(
        manufacturerPayloadSize: Int,
        includeServiceUuid: Boolean,
        deviceNameUtf8Length: Int
    ): Int {
        var size = 0
        size += manufacturerFieldBytes(manufacturerPayloadSize)
        if (includeServiceUuid) size += serviceUuid128FieldBytes()
        size += deviceNameFieldBytes(deviceNameUtf8Length)
        return size
    }

    /**
     * 只要有 Advertiser 就该广播。
     * [android.bluetooth.BluetoothAdapter.isMultipleAdvertisementSupported] 在很多手机上是假阴性。
     */
    fun shouldAdvertise(
        hasAdvertiser: Boolean,
        multipleAdvertisementSupported: Boolean
    ): Boolean = hasAdvertiser

    fun peerNodeId(
        manufacturerData: ByteArray?,
        hasServiceUuid: Boolean,
        address: String
    ): String? {
        BleMeshIds.nodeIdFromBytes(manufacturerData)?.let { return it }
        if (!hasServiceUuid) return null
        val compact = address.filter { it.isLetterOrDigit() }.lowercase()
        if (compact.isBlank()) return null
        return "addr:$compact"
    }
}
