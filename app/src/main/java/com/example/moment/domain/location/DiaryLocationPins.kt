package com.example.moment.domain.location

import com.example.moment.domain.model.DiaryLocationPin
import com.example.moment.domain.model.LifeFragment
import java.util.Locale

fun pinsFromFragments(sorted: List<LifeFragment>): List<DiaryLocationPin> =
    sorted.mapNotNull { fragment ->
        val loc = fragment.location ?: return@mapNotNull null
        val name = loc.label?.trim()?.takeIf { it.isNotEmpty() }
            ?: String.format(Locale.CHINA, "约 %.4f，%.4f", loc.latitude, loc.longitude)
        DiaryLocationPin(
            fragmentStableId = fragment.stableId,
            placeName = name,
            latitude = loc.latitude,
            longitude = loc.longitude
        )
    }
