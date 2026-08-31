package com.example.moment.domain.nearby

/**
 * 两台设备同时广播、同时扫描时，双方都可能去连对方，会占掉两条 BLE 连接。
 *
 * 约定 nodeId 字典序更大的那台主动当 GATT 客户端；小的那台只当外围等别人连。
 * 如果对方迟迟不来（广告不成功、连失败），小的那台等一段时间后自己也发起，避免卡死。
 */
object BleConnectPolicy {
    const val MAX_NEIGHBORS = 4
    const val FALLBACK_AFTER_MS = 8_000L

    fun shouldInitiate(
        selfNodeId: String,
        peerNodeId: String,
        canAdvertise: Boolean,
        waitingMs: Long = 0L,
        fallbackAfterMs: Long = FALLBACK_AFTER_MS
    ): Boolean {
        if (selfNodeId.isBlank() || peerNodeId.isBlank()) return false
        if (selfNodeId == peerNodeId) return false
        if (!canAdvertise) return true
        if (selfNodeId > peerNodeId) return true
        return waitingMs >= fallbackAfterMs
    }

    fun canAccept(currentConnections: Int, maxNeighbors: Int = MAX_NEIGHBORS): Boolean =
        currentConnections < maxNeighbors
}
