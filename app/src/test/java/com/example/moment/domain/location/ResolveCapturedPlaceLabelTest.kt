package com.example.moment.domain.location

import com.example.moment.domain.model.FragmentLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveCapturedPlaceLabelTest {
    private val coords = FragmentLocation(
        latitude = 39.9042,
        longitude = 116.4074,
        label = "约 39.9042，116.4074"
    )

    @Test
    fun prefersAmapLabelOverNominatimAndCoordinates() {
        val labeled = ResolveCapturedPlaceLabel.apply(
            location = coords,
            amapLabel = "北京市东城区天安门",
            nominatimLabel = "Tiananmen, Dongcheng, Beijing"
        )
        assertEquals("北京市东城区天安门", labeled.label)
        assertEquals(coords.latitude, labeled.latitude, 0.0)
        assertEquals(coords.longitude, labeled.longitude, 0.0)
    }

    @Test
    fun fallsBackToNominatimWhenAmapMissing() {
        val labeled = ResolveCapturedPlaceLabel.apply(
            location = coords,
            amapLabel = "  ",
            nominatimLabel = "望京SOHO"
        )
        assertEquals("望京SOHO", labeled.label)
    }

    @Test
    fun keepsCoordinateLabelWhenGeocodersFail() {
        val labeled = ResolveCapturedPlaceLabel.apply(
            location = coords,
            amapLabel = null,
            nominatimLabel = null
        )
        assertEquals("约 39.9042，116.4074", labeled.label)
    }
}
