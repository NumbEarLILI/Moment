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
    fun isBetter_prefersRecentFixOverStaleAccurateFix() {
        val dayInNanos = 24L * 60 * 60 * 1_000_000_000
        val staleGps = CapturedLocationCandidate(
            provider = LocationManager.GPS_PROVIDER,
            accuracyMeters = 10f,
            elapsedRealtimeNanos = 1_000L
        )
        val recentNetwork = CapturedLocationCandidate(
            provider = LocationManager.NETWORK_PROVIDER,
            accuracyMeters = 80f,
            elapsedRealtimeNanos = 1_000L + dayInNanos
        )

        assertTrue(CapturedLocationQuality.isBetter(candidate = recentNetwork, current = staleGps))
        assertFalse(CapturedLocationQuality.isBetter(candidate = staleGps, current = recentNetwork))
    }

    @Test
    fun isBetter_stillPrefersAccuracyWhenFixesAreCloseInTime() {
        val accurate = CapturedLocationCandidate(
            provider = LocationManager.GPS_PROVIDER,
            accuracyMeters = 10f,
            elapsedRealtimeNanos = 5_000_000_000L
        )
        val coarse = CapturedLocationCandidate(
            provider = LocationManager.NETWORK_PROVIDER,
            accuracyMeters = 80f,
            elapsedRealtimeNanos = 8_000_000_000L
        )

        assertTrue(CapturedLocationQuality.isBetter(candidate = accurate, current = coarse))
        assertFalse(CapturedLocationQuality.isBetter(candidate = coarse, current = accurate))
    }

    @Test
    fun isBetter_prefersKnownAccuracyOverUnknownAccuracy() {
        val unknown = CapturedLocationCandidate(LocationManager.GPS_PROVIDER, null)
        val known = CapturedLocationCandidate(LocationManager.NETWORK_PROVIDER, 80f)

        assertTrue(CapturedLocationQuality.isBetter(candidate = known, current = unknown))
        assertFalse(CapturedLocationQuality.isBetter(candidate = unknown, current = known))
    }

    @Test
    fun isFreshEnough_rejectsADayOldFix() {
        val now = 10L * 24 * 60 * 60 * 1_000_000_000
        assertTrue(
            CapturedLocationQuality.isFreshEnough(
                elapsedRealtimeNanos = now - 10L * 1_000_000_000,
                nowElapsedRealtimeNanos = now
            )
        )
        assertFalse(
            CapturedLocationQuality.isFreshEnough(
                elapsedRealtimeNanos = now - 24L * 60 * 60 * 1_000_000_000,
                nowElapsedRealtimeNanos = now
            )
        )
        assertFalse(
            CapturedLocationQuality.isFreshEnough(
                elapsedRealtimeNanos = null,
                nowElapsedRealtimeNanos = now
            )
        )
    }
}
