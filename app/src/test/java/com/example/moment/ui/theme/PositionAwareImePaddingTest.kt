package com.example.moment.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class PositionAwareImePaddingTest {

    @Test
    fun consumesTheGapAlreadySittingAboveTheWindowBottom() {
        assertEquals(80, remainingImeConsumePx(windowHeightPx = 800, composableBottomPx = 720f))
    }

    @Test
    fun consumesNothingWhenTheComposerAlreadySitsOnTheWindowBottom() {
        assertEquals(0, remainingImeConsumePx(windowHeightPx = 800, composableBottomPx = 800f))
    }

    @Test
    fun doesNotGoNegativeWhenTheComposerReportsBelowTheWindow() {
        assertEquals(0, remainingImeConsumePx(windowHeightPx = 800, composableBottomPx = 860f))
    }
}
