package com.example.moment.domain.model

fun LifeFragment.isNasGhostPlaceholder(): Boolean =
    content.isBlank() &&
        imageUris.isEmpty() &&
        mood == null &&
        tags.isEmpty() &&
        location == null
