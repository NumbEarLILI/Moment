package com.example.moment.domain.nearby

import java.io.IOException
import java.io.Reader
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 两台设备之间传输的一帧数据。每帧编码成一行 JSON，用 `\n` 分隔。 */
@Serializable
sealed interface NearbyChatFrame {
    /** 通道打开后立刻互发，交换昵称。 */
    @Serializable
    @SerialName("hello")
    data class Hello(val displayName: String) : NearbyChatFrame

    @Serializable
    @SerialName("text")
    data class Text(
        val id: String,
        val body: String,
        val sentAtEpochMillis: Long
    ) : NearbyChatFrame

    /** 主动断开，让对方能区分「对方退出」和「信号断了」。 */
    @Serializable
    @SerialName("bye")
    data object Bye : NearbyChatFrame
}

/**
 * 聊天通道的行协议：一帧一行 JSON。
 *
 * JSON 会把正文里的换行转义成 `\n`，所以正文本身不会把一帧拆成两行。
 */
object NearbyChatWire {
    /** 组主监听的端口，两端写死同一个值，无需再交换。 */
    const val PORT = 8899

    /** 单帧上限，避免对方（或坏掉的连接）不发换行时把内存撑爆。 */
    const val MAX_FRAME_CHARS = 8 * 1024

    /** 单条消息的字符上限，留出 JSON 转义和字段名的余量。 */
    const val MAX_MESSAGE_CHARS = 2000

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(frame: NearbyChatFrame): String = json.encodeToString(frame)

    /** 解析一行；对方版本更新带来的未知帧或半截数据都返回 null，不该中断通道。 */
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
