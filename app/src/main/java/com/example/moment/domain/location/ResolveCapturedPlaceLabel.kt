package com.example.moment.domain.location

import com.example.moment.domain.model.FragmentLocation

object ResolveCapturedPlaceLabel {
    fun apply(
        location: FragmentLocation,
        amapLabel: String?,
        nominatimLabel: String?
    ): FragmentLocation {
        val name = amapLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: nominatimLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: return location
        return location.copy(label = name)
    }
}
