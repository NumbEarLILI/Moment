package com.example.moment.data.location

import com.example.moment.domain.location.ResolveCapturedPlaceLabel
import com.example.moment.domain.model.FragmentLocation
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CapturedPlaceResolver @Inject constructor(
    private val fragmentLocationCapture: FragmentLocationCapture,
    private val amapReverseGeocoder: AmapReverseGeocoder,
    private val nominatim: NominatimReverseGeocoder
) {
    suspend fun currentPlace(): FragmentLocation? {
        val loc = fragmentLocationCapture.captureLastKnownIfPermitted()
            ?: fragmentLocationCapture.captureIfPermitted()
            ?: return null
        val amapLabel = runCatching {
            amapReverseGeocoder.reverseGeocode(loc.latitude, loc.longitude).label
        }.getOrNull()
        val nominatimLabel = runCatching {
            val (wgsLat, wgsLng) = ChinaCoordinateTransform.gcj02ToWgs84(loc.latitude, loc.longitude)
            nominatim.reverseLabel(wgsLat, wgsLng)
        }.getOrNull()
        return ResolveCapturedPlaceLabel.apply(loc, amapLabel, nominatimLabel)
    }
}
