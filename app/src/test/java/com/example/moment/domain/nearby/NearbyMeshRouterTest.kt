package com.example.moment.domain.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyMeshRouterTest {

    private val router = NearbyMeshRouter(selfNodeId = SELF)

    @Test
    fun `delivers a message from another node and passes it on with one less hop`() {
        val outcome = router.receive(message(id = "m-1", from = "node-a", ttl = 8))

        assertEquals("大家好", outcome.deliver?.body)
        assertEquals(listOf(7), outcome.forward.map { (it as NearbyChatFrame.Message).ttl })
    }

    @Test
    fun `drops a message it has already seen`() {
        val frame = message(id = "m-1", from = "node-a", ttl = 8)
        router.receive(frame)

        // 星形拓扑里同一条消息可能从主机和别的链路各来一次。
        val second = router.receive(frame.copy(ttl = 6))

        assertNull(second.deliver)
        assertTrue(second.forward.isEmpty())
    }

    @Test
    fun `stops forwarding once the hop budget runs out`() {
        val outcome = router.receive(message(id = "m-1", from = "node-a", ttl = 1))

        assertEquals("大家好", outcome.deliver?.body)
        assertTrue("ttl 用尽的消息不该再往外传", outcome.forward.isEmpty())
    }

    @Test
    fun `ignores its own message coming back around`() {
        val outcome = router.receive(message(id = "m-1", from = SELF, ttl = 8))

        assertNull(outcome.deliver)
        assertTrue(outcome.forward.isEmpty())
    }

    @Test
    fun `does not show a message it composed itself when a neighbour echoes it`() {
        val sent = router.compose(
            messageId = "m-1",
            body = "大家好",
            displayName = "我",
            atEpochMillis = 100L
        )

        // 别的节点转发回来时 senderId 仍是本机，但去重表也已经登记过了。
        val echoed = router.receive(sent.copy(ttl = sent.ttl - 1))

        assertNull(echoed.deliver)
    }

    @Test
    fun `learns who is on the other end of a link from hello`() {
        val outcome = router.receive(NearbyChatFrame.Hello(member("node-a", "阿七", at = 100L)))

        assertEquals("node-a", outcome.learnedNodeId)
        assertTrue(outcome.rosterChanged)
        assertEquals(listOf("阿七"), router.members().map { it.displayName })
    }

    @Test
    fun `spreads presence it has not heard before and stays quiet about repeats`() {
        val first = router.receive(NearbyChatFrame.Presence(member("node-a", "阿七", at = 100L)))
        val repeat = router.receive(NearbyChatFrame.Presence(member("node-a", "阿七", at = 100L)))

        assertEquals(1, first.forward.size)
        assertTrue("重复的状态不再转发，否则洪泛停不下来", repeat.forward.isEmpty())
    }

    @Test
    fun `keeps the newer presence record and ignores the stale one`() {
        router.receive(NearbyChatFrame.Presence(member("node-a", "阿七", at = 200L)))

        val stale = router.receive(
            NearbyChatFrame.Presence(member("node-a", "阿七", present = false, at = 100L))
        )

        assertTrue(stale.forward.isEmpty())
        assertEquals(listOf("阿七"), router.members().map { it.displayName })
    }

    @Test
    fun `drops a member once a newer record says they left`() {
        router.receive(NearbyChatFrame.Presence(member("node-a", "阿七", at = 100L)))

        router.receive(
            NearbyChatFrame.Presence(member("node-a", "阿七", present = false, at = 200L))
        )

        assertTrue(router.members().isEmpty())
    }

    @Test
    fun `takes in a whole roster at once and only relays what is new`() {
        router.receive(NearbyChatFrame.Presence(member("node-a", "阿七", at = 100L)))

        val outcome = router.receive(
            NearbyChatFrame.Roster(
                listOf(
                    member("node-a", "阿七", at = 100L),
                    member("node-b", "小明", at = 100L)
                )
            )
        )

        assertEquals(1, outcome.forward.size)
        assertEquals(listOf("小明"), router.members().map { it.displayName }.filter { it == "小明" })
        assertEquals(2, router.members().size)
    }

    @Test
    fun `refuses records about itself so a bad peer cannot kick it out`() {
        router.announceSelf("我", atEpochMillis = 100L)

        val outcome = router.receive(
            NearbyChatFrame.Presence(member(SELF, "我", present = false, at = 999L))
        )

        assertFalse(outcome.rosterChanged)
        assertEquals(listOf("我"), router.members().map { it.displayName })
    }

    @Test
    fun `greets a new neighbour with itself and everyone it knows about`() {
        router.receive(NearbyChatFrame.Presence(member("node-a", "阿七", at = 100L)))

        val greeting = router.greeting("我", atEpochMillis = 200L)

        val hello = greeting.filterIsInstance<NearbyChatFrame.Hello>().single()
        val roster = greeting.filterIsInstance<NearbyChatFrame.Roster>().single()
        assertEquals(SELF, hello.self.nodeId)
        assertEquals(setOf(SELF, "node-a"), roster.members.map { it.nodeId }.toSet())
    }

    @Test
    fun `announces a lost neighbour on their behalf`() {
        router.receive(NearbyChatFrame.Hello(member("node-a", "阿七", at = 100L)))

        val departure = router.onNeighborLost("node-a", atEpochMillis = 200L)

        assertEquals("node-a", departure?.member?.nodeId)
        assertFalse(departure?.member?.present ?: true)
        assertTrue(router.members().isEmpty())
    }

    @Test
    fun `says nothing about a node it never knew`() {
        assertNull(router.onNeighborLost("node-z", atEpochMillis = 200L))
    }

    @Test
    fun `forwards a new avatar and ignores a repeat`() {
        val frame = NearbyChatFrame.Avatar("node-a", jpeg = byteArrayOf(9, 8, 7), updatedAtEpochMillis = 10L)

        val first = router.receive(frame)
        val repeat = router.receive(frame)

        assertTrue(first.avatar?.jpeg.contentEquals(frame.jpeg))
        assertEquals(1, first.forward.size)
        assertNull(repeat.avatar)
        assertTrue(repeat.forward.isEmpty())
    }

    @Test
    fun `ignores an avatar that claims to be from itself`() {
        val outcome = router.receive(
            NearbyChatFrame.Avatar(SELF, jpeg = byteArrayOf(1), updatedAtEpochMillis = 1L)
        )

        assertNull(outcome.avatar)
        assertTrue(outcome.forward.isEmpty())
    }

    private fun message(id: String, from: String, ttl: Int) = NearbyChatFrame.Message(
        messageId = id,
        senderId = from,
        senderName = "阿七",
        body = "大家好",
        sentAtEpochMillis = 1L,
        ttl = ttl
    )

    private fun member(
        nodeId: String,
        name: String,
        present: Boolean = true,
        at: Long
    ) = MeshMember(
        nodeId = nodeId,
        displayName = name,
        present = present,
        updatedAtEpochMillis = at
    )

    private companion object {
        const val SELF = "node-self"
    }
}
