package com.example.moment.data.nearby

import com.example.moment.domain.nearby.MeshMember
import com.example.moment.domain.nearby.NearbyChatFrame
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
    private val connector = NearbyChatConnector()
    private val openLinks = CopyOnWriteArrayList<NearbyChatLink>()

    @Test
    fun `both ends exchange frames once the link is open`() = runBlocking {
        val port = freePort()
        val accepted = serve(port)
        val client = dial(port)
        val host = withTimeout(TIMEOUT_MILLIS) { accepted.receive() }

        val clientInbox = client.incoming().produceIn(readerScope)
        val hostInbox = host.incoming().produceIn(readerScope)

        val hello = NearbyChatFrame.Hello(
            MeshMember(nodeId = "host", displayName = "房主", present = true, updatedAtEpochMillis = 1L)
        )
        val message = NearbyChatFrame.Message(
            messageId = "m-1",
            senderId = "node-a",
            senderName = "阿七",
            body = "在\n楼下",
            sentAtEpochMillis = 42L,
            ttl = 8
        )
        host.send(hello)
        client.send(message)

        withTimeout(TIMEOUT_MILLIS) {
            assertEquals(hello, clientInbox.receive())
            assertEquals(message, hostInbox.receive())
        }
    }

    @Test
    fun `incoming completes when the peer closes the link`() = runBlocking {
        val port = freePort()
        val accepted = serve(port)
        val client = dial(port)
        val host = withTimeout(TIMEOUT_MILLIS) { accepted.receive() }
        val clientFrames = readerScope.async { client.incoming().toList() }

        val goodbye = NearbyChatFrame.Presence(
            MeshMember(nodeId = "host", displayName = "房主", present = false, updatedAtEpochMillis = 2L)
        )
        host.send(goodbye)
        host.close()

        assertEquals(
            listOf(goodbye),
            withTimeout(TIMEOUT_MILLIS) { clientFrames.await() }
        )
    }

    @Test
    fun `one listener takes in several devices`() = runBlocking {
        val port = freePort()
        val accepted = serve(port)

        dial(port)
        dial(port)

        withTimeout(TIMEOUT_MILLIS) {
            assertEquals(2, listOf(accepted.receive(), accepted.receive()).size)
        }
    }

    @After
    fun tearDown() {
        openLinks.forEach { it.close() }
        readerScope.cancel()
    }

    /** 起一个持续监听的组主，接入的链路都塞进队列里等测试取用。 */
    private fun serve(port: Int): Channel<NearbyChatLink> {
        val accepted = Channel<NearbyChatLink>(Channel.UNLIMITED)
        readerScope.launch {
            connector.serveGroupOwner(port) { link ->
                openLinks += link
                accepted.trySend(link)
            }
        }
        return accepted
    }

    private suspend fun dial(port: Int): NearbyChatLink =
        connector.connectToGroupOwner("127.0.0.1", port, TIMEOUT_MILLIS).also { openLinks += it }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
