package com.example.moment.domain.nearby

import kotlinx.serialization.Serializable

/** 搜索到的附近设备。[deviceAddress] 是 Wi-Fi Direct 的 MAC，用作连接时的唯一标识。 */
data class NearbyPeer(
    val deviceAddress: String,
    val deviceName: String,
    val statusText: String,
    val connectable: Boolean,
    /** 对方已经建好聊天室，连过去就是加入它。 */
    val hostsRoom: Boolean
)

/**
 * 聊天室成员，同时也是网格里传播的在线状态记录。
 *
 * [nodeId] 每次进入页面重新生成，不跨会话保留；[updatedAtEpochMillis] 用来在洪泛中
 * 判断哪条记录更新，旧记录会被丢弃。
 */
@Serializable
data class MeshMember(
    val nodeId: String,
    val displayName: String,
    val present: Boolean,
    val updatedAtEpochMillis: Long
)

data class NearbyChatMessage(
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
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
    Linking,

    /** 已在聊天室里，可以收发消息。 */
    InRoom,

    /** 会话已结束，聊天记录仍留在屏幕上。 */
    Closed
}
