package com.example.moment.data.nearby

import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * 在已经建好的 Wi-Fi Direct 组里拉 TCP 通道：组主监听，其余设备连过去。
 *
 * 两侧都用短超时轮询，这样协程取消时能及时退出，不会卡在阻塞的 accept/connect 上。
 */
@Singleton
class NearbyChatConnector @Inject constructor() {

    /**
     * 一直监听新接入的设备，每来一个就回调一次 [onLink]。挂起到协程取消为止。
     *
     * 一个 Wi-Fi Direct 组里可以有多台设备，所以这里不能收一个就收工。
     */
    suspend fun serveGroupOwner(
        port: Int,
        onLink: (NearbyChatLink) -> Unit
    ) = withContext(Dispatchers.IO) {
        ServerSocket().use { server ->
            server.reuseAddress = true
            server.bind(InetSocketAddress(port))
            server.soTimeout = ACCEPT_POLL_MILLIS
            while (true) {
                currentCoroutineContext().ensureActive()
                val socket = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                }
                socket.keepAlive = true
                onLink(NearbyChatLink(socket))
            }
        }
    }

    suspend fun connectToGroupOwner(
        hostAddress: String,
        port: Int,
        timeoutMillis: Long
    ): NearbyChatLink = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var lastError: IOException? = null
        while (System.currentTimeMillis() <= deadline) {
            currentCoroutineContext().ensureActive()
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(hostAddress, port), CONNECT_TIMEOUT_MILLIS)
                socket.keepAlive = true
                return@withContext NearbyChatLink(socket)
            } catch (error: IOException) {
                // 组主的 ServerSocket 常常比「组已建立」广播晚一点才起来，重试即可。
                lastError = error
                runCatching { socket.close() }
            }
            delay(RETRY_DELAY_MILLIS)
        }
        throw lastError ?: SocketTimeoutException("连接对方超时")
    }

    private companion object {
        const val ACCEPT_POLL_MILLIS = 500
        const val CONNECT_TIMEOUT_MILLIS = 2_000
        const val RETRY_DELAY_MILLIS = 600L
    }
}
