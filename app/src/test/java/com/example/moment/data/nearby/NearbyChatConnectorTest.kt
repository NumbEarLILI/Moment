package com.example.moment.data.nearby

import com.example.moment.domain.nearby.NearbyChatFrame
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 用回环地址代替 Wi-Fi Direct 组，验证握手与行协议在真实 TCP 上能跑通。
 *
 * 读循环是阻塞的，只有关掉 socket 才会退出，所以统一放在独立的 [readerScope] 里跑，
 * 免得某次断言失败时 `runBlocking` 卡在等子协程上。
 */
class NearbyChatConnectorTest {

    private val readerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Test
    fun `both ends exchange frames once the link is open`() = runBlocking {
        val connector = NearbyChatConnector()
        val port = freePort()
        val ownerDeferred = readerScope.async { connector.acceptAsGroupOwner(port, TIMEOUT_MILLIS) }
        val client = connector.connectToGroupOwner("127.0.0.1", port, TIMEOUT_MILLIS)
        val owner = ownerDeferred.await()

        try {
            val clientInbox = client.incoming().produceIn(readerScope)
            val ownerInbox = owner.incoming().produceIn(readerScope)

            owner.send(NearbyChatFrame.Hello("组主"))
            client.send(NearbyChatFrame.Text(id = "m-1", body = "在\n楼下", sentAtEpochMillis = 42L))

            withTimeout(TIMEOUT_MILLIS) {
                assertEquals(NearbyChatFrame.Hello("组主"), clientInbox.receive())
                assertEquals(
                    NearbyChatFrame.Text(id = "m-1", body = "在\n楼下", sentAtEpochMillis = 42L),
                    ownerInbox.receive()
                )
            }
        } finally {
            client.close()
            owner.close()
        }
    }

    @Test
    fun `incoming completes when the peer closes the link`() = runBlocking {
        val connector = NearbyChatConnector()
        val port = freePort()
        val ownerDeferred = readerScope.async { connector.acceptAsGroupOwner(port, TIMEOUT_MILLIS) }
        val client = connector.connectToGroupOwner("127.0.0.1", port, TIMEOUT_MILLIS)
        val owner = ownerDeferred.await()

        try {
            val clientFrames = readerScope.async { client.incoming().toList() }

            owner.send(NearbyChatFrame.Text(id = "m-1", body = "先走了", sentAtEpochMillis = 1L))
            owner.send(NearbyChatFrame.Bye)
            owner.close()

            assertEquals(
                listOf(
                    NearbyChatFrame.Text(id = "m-1", body = "先走了", sentAtEpochMillis = 1L),
                    NearbyChatFrame.Bye
                ),
                withTimeout(TIMEOUT_MILLIS) { clientFrames.await() }
            )
        } finally {
            client.close()
        }
    }

    @After
    fun tearDown() {
        readerScope.cancel()
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
