package com.example.moment.domain.nearby

/** 搜索到的附近设备。[deviceAddress] 是 Wi-Fi Direct 的 MAC，用作连接时的唯一标识。 */
data class NearbyPeer(
    val deviceAddress: String,
    val deviceName: String,
    val statusText: String,
    val connectable: Boolean
)

/** 聊天气泡。[id] 由发送方生成，接收方原样保留以便去重。 */
data class NearbyChatMessage(
    val id: String,
    val text: String,
    val senderName: String,
    val fromMe: Boolean,
    val sentAtEpochMillis: Long
)

enum class NearbyChatStage {
    /** 还没开始搜索。 */
    Idle,

    /** 正在搜索附近设备。 */
    Discovering,

    /** 已发出邀请，等待对方接受并组网。 */
    Connecting,

    /** Wi-Fi Direct 组已建立，正在打开消息通道。 */
    Handshaking,

    /** 通道已打开，可以收发消息。 */
    Connected,

    /** 会话已结束（对方退出、链路断开或本机失败），聊天记录仍留在屏幕上。 */
    Closed
}
