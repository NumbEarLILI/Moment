package com.example.moment.data.location

import android.location.LocationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturedLocationQualityTest {
    @Test
    fun isGoodEnough_requiresKnownAccuracyWithinThreshold() {
        assertTrue(CapturedLocationQuality.isGoodEnough(35f))
        assertFalse(CapturedLocationQuality.isGoodEnough(120f))
        assertFalse(CapturedLocationQuality.isGoodEnough(null))
    }

    @Test
    fun isBetter_prefersMoreAccurateCandidate() {
        val coarse = CapturedLocationCandidate(LocationManager.NETWORK_PROVIDER, 120f)
        val precise = CapturedLocationCandidate(LocationManager.GPS_PROVIDER, 20f)

        assertTrue(CapturedLocationQuality.isBetter(candidate = precise, current = coarse))
        assertFalse(CapturedLocationQuality.isBetter(candidate = coarse, current = precise))
    }

    @Test
    fun isBetter_prefersKnownAccuracyOverUnknownAccuracy() {
        val unknown = CapturedLocationCandidate(LocationManager.GPS_PROVIDER, null)
        val known = CapturedLocationCandidate(LocationManager.NETWORK_PROVIDER, 80f)

        assertTrue(CapturedLocationQuality.isBetter(candidate = known, current = unknown))
        assertFalse(CapturedLocationQuality.isBetter(candidate = unknown, current = known))
    }
}
