package com.example.moment.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class StringListConverterTest {
    private val converter = StringListConverter()

    @Test
    fun stringListsRoundTripSpecialCharacters() {
        val values = listOf("朋友,散步", "咖啡 \"热\"", "#日常")

        val restored = converter.toStringList(converter.fromStringList(values))

        assertEquals(values, restored)
    }

    @Test
    fun longListsRoundTripIds() {
        val values = listOf(1L, 20L, 300L)

        val restored = converter.toLongList(converter.fromLongList(values))

        assertEquals(values, restored)
    }
}
