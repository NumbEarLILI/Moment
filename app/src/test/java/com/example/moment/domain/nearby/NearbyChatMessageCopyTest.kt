package com.example.moment.domain.nearby

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyChatMessageCopyTest {

    @Test
    fun `plain messages copy their text`() {
        assertEquals("在楼下", message(text = "在楼下").copyableText())
    }

    @Test
    fun `fragment cards copy content and context`() {
        val message = message(
            text = "出门散步",
            fragment = SharedFragmentCard(
                stableId = "sid",
                content = "出门散步",
                mood = "平静",
                place = "上海",
                createdAtEpochMillis = 1L
            )
        )

        assertEquals("出门散步\n平静  ·  上海", message.copyableText())
    }

    @Test
    fun `blank fragment content falls back to the message text`() {
        val message = message(
            text = "分享了一条碎片",
            fragment = SharedFragmentCard(
                stableId = "sid",
                content = "",
                createdAtEpochMillis = 1L
            )
        )

        assertEquals("分享了一条碎片", message.copyableText())
    }

    private fun message(
        text: String,
        fragment: SharedFragmentCard? = null
    ) = NearbyChatMessage(
        messageId = "m-1",
        senderId = "node-a",
        senderName = "阿七",
        text = text,
        fromMe = false,
        sentAtEpochMillis = 1L,
        fragment = fragment
    )
}
