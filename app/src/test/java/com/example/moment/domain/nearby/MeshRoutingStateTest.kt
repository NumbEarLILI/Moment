package com.example.moment.domain.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenMessageLogTest {

    @Test
    fun `reports the first sighting and suppresses the rest`() {
        val log = SeenMessageLog(capacity = 4)

        assertTrue(log.markSeen("m-1"))
        assertFalse(log.markSeen("m-1"))
    }

    @Test
    fun `forgets the oldest entries instead of growing without bound`() {
        val log = SeenMessageLog(capacity = 2)

        log.markSeen("m-1")
        log.markSeen("m-2")
        log.markSeen("m-3")

        assertTrue("m-1 已被挤出，重新出现时会被当成新消息", log.markSeen("m-1"))
        assertFalse("m-3 还在表里", log.markSeen("m-3"))
    }
}

class MeshRosterTest {

    @Test
    fun `only present members show up in the room`() {
        val roster = MeshRoster()
        roster.apply(member("node-a", "阿七", present = true, at = 100L))
        roster.apply(member("node-b", "小明", present = false, at = 100L))

        assertEquals(listOf("阿七"), roster.present().map { it.displayName })
        assertEquals(2, roster.snapshot().size)
    }

    @Test
    fun `an empty node id is not a member`() {
        val roster = MeshRoster()

        assertFalse(roster.apply(member("", "无名", present = true, at = 100L)))
    }

    @Test
    fun `evicts departed members first when the table is full`() {
        val roster = MeshRoster(capacity = 2)
        roster.apply(member("gone", "走了", present = false, at = 100L))
        roster.apply(member("node-a", "阿七", present = true, at = 100L))

        roster.apply(member("node-b", "小明", present = true, at = 100L))

        assertEquals(setOf("阿七", "小明"), roster.present().map { it.displayName }.toSet())
    }

    private fun member(nodeId: String, name: String, present: Boolean, at: Long) = MeshMember(
        nodeId = nodeId,
        displayName = name,
        present = present,
        updatedAtEpochMillis = at
    )
}
