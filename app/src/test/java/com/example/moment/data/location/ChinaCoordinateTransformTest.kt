package com.example.moment.data.location

import android.location.LocationManager
import kotlin.math.abs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChinaCoordinateTransformTest {

    @Test
    fun appliesChinaOffsetMatchesWgsTransformBox() {
        assertTrue(ChinaCoordinateTransform.appliesChinaOffset(39.9, 116.4))
        assertFalse(ChinaCoordinateTransform.appliesChinaOffset(35.0, 71.0))
    }

    @Test
    fun gpsAlwaysConvertedRegardlessOfFusedAssumption() {
        assertTrue(
            ChinaCoordinateTransform.shouldConvertCapturedLocationToGcj02(
                LocationManager.GPS_PROVIDER,
                fusedOutputAssumedWgs84 = false,
            ),
        )
        assertTrue(
            ChinaCoordinateTransform.shouldConvertCapturedLocationToGcj02(
                LocationManager.GPS_PROVIDER,
                fusedOutputAssumedWgs84 = true,
            ),
        )
    }

    @Test
    fun networkNeverConverted() {
        assertFalse(
            ChinaCoordinateTransform.shouldConvertCapturedLocationToGcj02(
                LocationManager.NETWORK_PROVIDER,
                fusedOutputAssumedWgs84 = false,
            ),
        )
        assertFalse(
            ChinaCoordinateTransform.shouldConvertCapturedLocationToGcj02(
                LocationManager.NETWORK_PROVIDER,
                fusedOutputAssumedWgs84 = true,
            ),
        )
    }

    @Test
    fun fusedConvertedOnlyWhenAssumedWgs84() {
        assertFalse(
            ChinaCoordinateTransform.shouldConvertCapturedLocationToGcj02(
                LocationManager.FUSED_PROVIDER,
                fusedOutputAssumedWgs84 = false,
            ),
        )
        assertTrue(
            ChinaCoordinateTransform.shouldConvertCapturedLocationToGcj02(
                LocationManager.FUSED_PROVIDER,
                fusedOutputAssumedWgs84 = true,
            ),
        )
        assertFalse(
            ChinaCoordinateTransform.shouldConvertCapturedLocationToGcj02(
                "fused",
                fusedOutputAssumedWgs84 = false,
            ),
        )
        assertTrue(
            ChinaCoordinateTransform.shouldConvertCapturedLocationToGcj02(
                "Fused",
                fusedOutputAssumedWgs84 = true,
            ),
        )
    }

    @Test
    fun nullOrEmptyProviderNeverConverted() {
        assertFalse(ChinaCoordinateTransform.shouldConvertCapturedLocationToGcj02(null, true))
        assertFalse(ChinaCoordinateTransform.shouldConvertCapturedLocationToGcj02("", false))
    }

    @Test
    fun wgs84ToGcj02ShiftsWithinExpectedRangeInBeijing() {
        val wgsLat = 39.907333
        val wgsLng = 116.391155
        val (gcjLat, gcjLng) = ChinaCoordinateTransform.wgs84ToGcj02(wgsLat, wgsLng)
        val dLat = abs(gcjLat - wgsLat)
        val dLng = abs(gcjLng - wgsLng)
        assertTrue("delta lat should be tens of meters, not zero", dLat in 1e-5..0.02)
        assertTrue("delta lng should be tens of meters, not zero", dLng in 1e-5..0.02)
    }

    @Test
    fun wgs84UnchangedOutsideChinaBox() {
        val (lat, lng) = ChinaCoordinateTransform.wgs84ToGcj02(35.0, 71.0)
        assertTrue(lat == 35.0 && lng == 71.0)
    }

    @Test
    fun gcj02ToWgs84RoughlyInvertsNearBeijing() {
        val wgsLat = 39.91
        val wgsLng = 116.40
        val (gcjLat, gcjLng) = ChinaCoordinateTransform.wgs84ToGcj02(wgsLat, wgsLng)
        val (backLat, backLng) = ChinaCoordinateTransform.gcj02ToWgs84(gcjLat, gcjLng)
        assertTrue(abs(backLat - wgsLat) < 0.0002)
        assertTrue(abs(backLng - wgsLng) < 0.0002)
    }
}
