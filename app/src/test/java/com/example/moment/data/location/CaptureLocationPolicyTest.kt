package com.example.moment.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureLocationPolicyTest {
    @Test
    fun usesAFreshFixEvenWhenLastKnownExists() {
        assertEquals(
            "now",
            CaptureLocationPolicy.preferFreshThenLastKnown(fresh = "now", lastKnown = "home-last-week")
        )
    }

    @Test
    fun fallsBackToLastKnownOnlyWhenFreshCaptureMisses() {
        assertEquals(
            "home-last-week",
            CaptureLocationPolicy.preferFreshThenLastKnown(fresh = null, lastKnown = "home-last-week")
        )
        assertNull(CaptureLocationPolicy.preferFreshThenLastKnown(fresh = null, lastKnown = null))
    }
}
