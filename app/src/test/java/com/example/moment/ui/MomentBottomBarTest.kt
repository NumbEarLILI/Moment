package com.example.moment.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentBottomBarTest {

    @Test
    fun showsOnMainTabsWhenKeyboardIsHidden() {
        assertTrue(showMomentBottomBar(Routes.Home, imeVisible = false))
        assertTrue(showMomentBottomBar(Routes.Chat, imeVisible = false))
        assertTrue(showMomentBottomBar(Routes.History, imeVisible = false))
        assertTrue(showMomentBottomBar(Routes.Mine, imeVisible = false))
    }

    @Test
    fun hidesWhenKeyboardIsVisible() {
        assertFalse(showMomentBottomBar(Routes.Chat, imeVisible = true))
        assertFalse(showMomentBottomBar(Routes.Home, imeVisible = true))
    }

    @Test
    fun hidesWhenComposerIsFocusedEvenIfImeInsetsAreZero() {
        assertFalse(
            showMomentBottomBar(Routes.Chat, imeVisible = false, composerFocused = true)
        )
    }

    @Test
    fun hidesOnNestedScreens() {
        assertFalse(showMomentBottomBar(Routes.Settings, imeVisible = false))
        assertFalse(showMomentBottomBar(null, imeVisible = false))
    }
}
