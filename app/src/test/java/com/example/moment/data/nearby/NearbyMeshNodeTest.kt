package com.example.moment.data.nearby

import com.example.moment.domain.nearby.MeshMember
import com.example.moment.domain.nearby.NearbyChatFrame
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 三个节点跑在回环地址上，代替一个 Wi-Fi Direct 组：一台当主机，两台当成员。
 *
 * 验证的是「主机同时带多台设备并在它们之间转发」这件事本身，路由决策由
 * [com.example.moment.domain.nearby.NearbyMeshRouter] 单独测。
 */
class NearbyMeshNodeTest {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val openNodes = mutableListOf<NearbyMeshNode>()

    @Test
    fun `host takes in several members at once and reaches all of them`() = runBlocking {
        val port = freePort()
        val host = startHost(port)
        val first = startMember(port)
        val second = startMember(port)
        val hostInbox = host.events.produceIn(scope)
        val firstInbox = first.events.produceIn(scope)
        val secondInbox = second.events.produceIn(scope)

        awaitJoined(hostInbox)
        awaitJoined(hostInbox)
        awaitJoined(firstInbox)
        awaitJoined(secondInbox)
        assertEquals(2, host.neighborCount)

        val announcement = NearbyChatFrame.Presence(
            MeshMember(nodeId = "host", displayName = "房主", present = true, updatedAtEpochMillis = 1L)
        )
        host.broadcast(announcement)

        assertEquals(announcement, awaitReceived(firstInbox).frame)
        assertEquals(announcement, awaitReceived(secondInbox).frame)
    }

    @Test
    fun `host relays one member's message to the other without echoing it back`() = runBlocking {
        val port = freePort()
        val host = startHost(port)
        val sender = startMember(port)
        val listener = startMember(port)
        val hostInbox = host.events.produceIn(scope)
        val senderInbox = sender.events.produceIn(scope)
        val listenerInbox = listener.events.produceIn(scope)

        awaitJoined(hostInbox)
        awaitJoined(hostInbox)
        awaitJoined(senderInbox)
        awaitJoined(listenerInbox)

        val message = NearbyChatFrame.Message(
            messageId = "m-1",
            senderId = "node-sender",
            senderName = "阿七",
            body = "大家好",
            sentAtEpochMillis = 1L,
            ttl = 8
        )
        sender.broadcast(message)

        val atHost = awaitReceived(hostInbox)
        assertEquals(message, atHost.frame)

        // 这正是主机的转发动作：发给除来源以外的所有邻居。
        host.broadcast(atHost.frame, exceptNeighborId = atHost.neighborId)

        assertEquals(message, awaitReceived(listenerInbox).frame)
        delay(300)
        assertNull("发送方不该收到自己那条消息的回声", senderInbox.tryReceive().getOrNull())
    }

    @After
    fun tearDown() {
        // 链路的读是阻塞的，先关 socket 才轮得到取消协程。
        openNodes.forEach { it.close() }
        scope.cancel()
    }

    private fun startHost(port: Int): NearbyMeshNode = newNode().also { node ->
        scope.launch { node.run(MeshRole.RoomHost, hostAddress = "", port = port, connectTimeoutMillis = TIMEOUT_MILLIS) }
    }

    private fun startMember(port: Int): NearbyMeshNode = newNode().also { node ->
        scope.launch {
            node.run(
                role = MeshRole.RoomMember,
                hostAddress = "127.0.0.1",
                port = port,
                connectTimeoutMillis = TIMEOUT_MILLIS
            )
        }
    }

    private fun newNode(): NearbyMeshNode =
        NearbyMeshNode(NearbyChatConnector()).also { openNodes += it }

    private suspend fun awaitJoined(inbox: ReceiveChannel<NearbyMeshNode.Event>) {
        val event = withTimeout(TIMEOUT_MILLIS) { inbox.receive() }
        assertEquals(NearbyMeshNode.Event.NeighborJoined::class, event::class)
    }

    private suspend fun awaitReceived(
        inbox: ReceiveChannel<NearbyMeshNode.Event>
    ): NearbyMeshNode.Event.Received =
        withTimeout(TIMEOUT_MILLIS) { inbox.receive() } as NearbyMeshNode.Event.Received

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
