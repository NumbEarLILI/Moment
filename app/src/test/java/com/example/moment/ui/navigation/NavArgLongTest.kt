package com.example.moment.ui.navigation

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Test

class NavArgLongTest {
    @Test
    fun stringPathParamParsesAsLong() {
        val handle = SavedStateHandle(mapOf("fragmentId" to "42"))
        assertEquals(42L, handle.navArgLong("fragmentId"))
    }

    @Test
    fun longPathParamPassesThrough() {
        val handle = SavedStateHandle(mapOf("fragmentId" to 42L))
        assertEquals(42L, handle.navArgLong("fragmentId"))
    }

    @Test
    fun missingArgIsZero() {
        val handle = SavedStateHandle()
        assertEquals(0L, handle.navArgLong("fragmentId"))
    }
}
