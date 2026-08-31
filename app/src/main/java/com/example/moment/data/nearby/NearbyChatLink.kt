package com.example.moment.data.nearby

import com.example.moment.domain.nearby.NearbyChatFrame
import com.example.moment.domain.nearby.NearbyChatWire
import java.io.Closeable
import java.io.IOException
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 已建立的点对点消息通道。
 *
 * [incoming] 里的读操作是阻塞的，协程取消不会打断它——想停下来必须调用 [close]。
 */
class NearbyChatLink internal constructor(private val socket: Socket) : Closeable {
    private val reader = socket.getInputStream().reader(Charsets.UTF_8).buffered()
    private val writer = socket.getOutputStream().writer(Charsets.UTF_8).buffered()
    private val writeMutex = Mutex()

    val remoteAddress: String = socket.inetAddress?.hostAddress.orEmpty()

    suspend fun send(frame: NearbyChatFrame) {
        val line = NearbyChatWire.encode(frame)
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }
        }
    }

    /** 读到对方关闭或链路断开为止。正常结束即代表通道已经断了，不会抛 IO 异常。 */
    fun incoming(): Flow<NearbyChatFrame> = flow {
        while (currentCoroutineContext().isActive) {
            val line = try {
                NearbyChatWire.readFrameLine(reader)
            } catch (_: IOException) {
                null
            } ?: break
            // 无法识别的帧（对方版本更新、半截数据）跳过即可，不必断开。
            NearbyChatWire.decode(line)?.let { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        runCatching { socket.close() }
    }
}
