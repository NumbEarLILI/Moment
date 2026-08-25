package com.example.moment.domain.weather

import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.FragmentWeather
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveFragmentWeatherTest {

    private val park = FragmentLocation(39.9, 116.4, "公园")
    private val home = FragmentLocation(31.2, 121.5, "家")
    private val sunny = FragmentWeather("晴", 26)

    @Test
    fun newFragmentReusesHeaderWhenPlaceUnchanged() {
        val decision = ResolveFragmentWeather.decide(
            isEditing = false,
            existing = null,
            placeChanged = false,
            recordedOnDifferentDay = false,
            headerWeather = sunny,
            fetchLocation = home
        )
        assertEquals(ResolveFragmentWeather.Decision.Use(sunny), decision)
    }

    @Test
    fun newFragmentFetchesWhenHeaderMissing() {
        val decision = ResolveFragmentWeather.decide(
            isEditing = false,
            existing = null,
            placeChanged = false,
            recordedOnDifferentDay = false,
            headerWeather = null,
            fetchLocation = home
        )
        assertEquals(ResolveFragmentWeather.Decision.Fetch(home), decision)
    }

    @Test
    fun newPastDayFragmentStoresNoWeather() {
        val decision = ResolveFragmentWeather.decide(
            isEditing = false,
            existing = null,
            placeChanged = false,
            recordedOnDifferentDay = true,
            headerWeather = sunny,
            fetchLocation = home
        )
        assertEquals(ResolveFragmentWeather.Decision.Keep(null), decision)
    }

    @Test
    fun editWithoutPlaceChangeKeepsExistingIncludingNull() {
        val withWeather = ResolveFragmentWeather.decide(
            isEditing = true,
            existing = sunny,
            placeChanged = false,
            recordedOnDifferentDay = false,
            headerWeather = FragmentWeather("雨", 12),
            fetchLocation = home
        )
        val weatherless = ResolveFragmentWeather.decide(
            isEditing = true,
            existing = null,
            placeChanged = false,
            recordedOnDifferentDay = false,
            headerWeather = sunny,
            fetchLocation = home
        )
        assertEquals(ResolveFragmentWeather.Decision.Keep(sunny), withWeather)
        assertEquals(ResolveFragmentWeather.Decision.Keep(null), weatherless)
    }

    @Test
    fun editWithNewPlaceFetchesForThatLocation() {
        val decision = ResolveFragmentWeather.decide(
            isEditing = true,
            existing = sunny,
            placeChanged = true,
            recordedOnDifferentDay = false,
            headerWeather = sunny,
            fetchLocation = park
        )
        assertEquals(ResolveFragmentWeather.Decision.Fetch(park), decision)
    }

    @Test
    fun pastDayPlaceChangeDoesNotStampCurrentWeather() {
        val decision = ResolveFragmentWeather.decide(
            isEditing = true,
            existing = sunny,
            placeChanged = true,
            recordedOnDifferentDay = true,
            headerWeather = FragmentWeather("雨", 8),
            fetchLocation = park
        )
        assertEquals(ResolveFragmentWeather.Decision.Keep(sunny), decision)
    }
}
