package com.example.moment.domain.nearby

import java.io.IOException
import java.io.Reader
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 网格里传输的一帧数据。每帧编码成一行 JSON，用 `\n` 分隔。 */
@Serializable
sealed interface NearbyChatFrame {
    /** 链路刚接上时发给这一个邻居，告诉对方「这条链路那头是谁」。 */
    @Serializable
    @SerialName("hello")
    data class Hello(val self: MeshMember) : NearbyChatFrame

    /** 某个节点的在线状态变化，会在网格里洪泛。 */
    @Serializable
    @SerialName("presence")
    data class Presence(val member: MeshMember) : NearbyChatFrame

    /** 新邻居接上时把已知成员一次性同步给它，省得它靠洪泛慢慢凑齐。 */
    @Serializable
    @SerialName("roster")
    data class Roster(val members: List<MeshMember>) : NearbyChatFrame

    /**
     * 聊天消息，会在网格里洪泛。
     *
     * [messageId] 用于去重，[ttl] 每转发一跳减一，两者共同保证消息不会在环路里打转。
     */
    @Serializable
    @SerialName("msg")
    data class Message(
        val messageId: String,
        val senderId: String,
        val senderName: String,
        val body: String,
        val sentAtEpochMillis: Long,
        val ttl: Int
    ) : NearbyChatFrame

    /** 压缩过的头像，跟 Presence 一样按时间戳去重后洪泛。 */
    @Serializable
    @SerialName("avatar")
    data class Avatar(
        val nodeId: String,
        val jpeg: ByteArray,
        val updatedAtEpochMillis: Long
    ) : NearbyChatFrame

    /** 分享一条本机碎片。走和消息一样的去重 + TTL 洪泛。 */
    @Serializable
    @SerialName("fragment")
    data class FragmentShare(
        val messageId: String,
        val senderId: String,
        val senderName: String,
        val sentAtEpochMillis: Long,
        val ttl: Int,
        val card: SharedFragmentCard,
        val jpeg: ByteArray = byteArrayOf()
    ) : NearbyChatFrame
}

/**
 * 网格链路的行协议：一帧一行 JSON。
 *
 * JSON 会把正文里的换行转义成 `\n`，所以正文本身不会把一帧拆成两行。
 */
object NearbyChatWire {
    /** 聊天室主机监听的端口，两端写死同一个值，无需再交换。 */
    const val PORT = 8899

    /** 单帧上限，避免对方（或坏掉的连接）不发换行时把内存撑爆。 */
    const val MAX_FRAME_CHARS = 32 * 1024

    /** 单条消息的字符上限，留出 JSON 转义和字段名的余量。 */
    const val MAX_MESSAGE_CHARS = 2000

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(frame: NearbyChatFrame): String = json.encodeToString(frame)

    /** 解析一行；对方版本更新带来的未知帧或半截数据都返回 null，不该中断链路。 */
    fun decode(line: String): NearbyChatFrame? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        return runCatching { json.decodeFromString<NearbyChatFrame>(trimmed) }.getOrNull()
    }

    /** 裁掉首尾空白并截断超长内容；纯空白返回 null，表示不该发出去。 */
    fun sanitizeMessage(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.length > MAX_MESSAGE_CHARS) trimmed.take(MAX_MESSAGE_CHARS) else trimmed
    }

    /**
     * 读一帧。相比 [Reader.readLine] 会限制长度，并把 `\r` 忽略掉以兼容 CRLF。
     *
     * @return 一行内容；流结束且没有残留内容时返回 null。
     */
    @Throws(IOException::class)
    fun readFrameLine(reader: Reader, maxChars: Int = MAX_FRAME_CHARS): String? {
        val builder = StringBuilder()
        while (true) {
            val ch = reader.read()
            if (ch == -1) return if (builder.isEmpty()) null else builder.toString()
            if (ch == NEW_LINE) return builder.toString()
            if (ch == CARRIAGE_RETURN) continue
            builder.append(ch.toChar())
            if (builder.length > maxChars) throw IOException("聊天数据帧超长（>$maxChars）")
        }
    }

    private const val NEW_LINE = '\n'.code
    private const val CARRIAGE_RETURN = '\r'.code
}
