package com.example.moment.domain.weather

import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.FragmentWeather

object ResolveFragmentWeather {
    sealed interface Decision {
        data class Keep(val weather: FragmentWeather?) : Decision
        data class Use(val weather: FragmentWeather) : Decision
        data class Fetch(val location: FragmentLocation?) : Decision
    }

    fun decide(
        isEditing: Boolean,
        existing: FragmentWeather?,
        placeChanged: Boolean,
        recordedOnDifferentDay: Boolean,
        headerWeather: FragmentWeather?,
        fetchLocation: FragmentLocation?
    ): Decision {
        if (recordedOnDifferentDay) return Decision.Keep(existing)
        if (isEditing && !placeChanged) return Decision.Keep(existing)
        if (!placeChanged) {
            headerWeather?.let { return Decision.Use(it) }
        }
        return Decision.Fetch(fetchLocation)
    }
}
