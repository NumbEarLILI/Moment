package com.example.moment.domain.nearby

import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.FragmentWeather
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.model.Mood
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedFragmentCardTest {

    @Test
    fun `packs a fragment into a shareable card`() {
        val fragment = LifeFragment(
            id = 3L,
            stableId = "sid-3",
            content = "出门散步",
            imageUris = listOf("file:///tmp/a.jpg"),
            mood = Mood.CALM,
            tags = listOf("散步"),
            createdAt = Instant.parse("2026-08-25T10:00:00Z"),
            updatedAt = Instant.parse("2026-08-25T10:05:00Z"),
            location = FragmentLocation(31.23, 121.47, "上海"),
            weather = FragmentWeather(condition = "晴", temperatureCelsius = 26)
        )

        val card = fragment.toSharedFragmentCard()

        assertEquals("sid-3", card.stableId)
        assertEquals("出门散步", card.content)
        assertEquals("平静", card.mood)
        assertEquals(listOf("散步"), card.tags)
        assertEquals("上海", card.place)
        assertEquals("晴  26°", card.weather)
        assertEquals(fragment.createdAt.toEpochMilli(), card.createdAtEpochMillis)
        assertEquals("平静  ·  晴  26°  ·  上海", card.contextLine())
        assertEquals("出门散步", fragment.shareCaption())
    }

    @Test
    fun `clips overly long fragment text`() {
        val fragment = LifeFragment(
            id = 1L,
            stableId = "sid-1",
            content = "字".repeat(NearbyFragmentSharePolicy.MAX_CONTENT_CHARS + 40),
            imageUris = emptyList(),
            mood = null,
            tags = emptyList(),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )

        val card = fragment.toSharedFragmentCard()

        assertEquals(NearbyFragmentSharePolicy.MAX_CONTENT_CHARS, card.content.length)
    }

    @Test
    fun `uses a fallback caption when the fragment has no text`() {
        val fragment = LifeFragment(
            id = 1L,
            stableId = "sid-1",
            content = "  ",
            imageUris = emptyList(),
            mood = Mood.HAPPY,
            tags = emptyList(),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )

        assertEquals("开心", fragment.toSharedFragmentCard().contextLine())
        assertEquals("开心", fragment.shareCaption())
    }
}
