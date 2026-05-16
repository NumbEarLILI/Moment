package com.example.moment.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DiaryLocationPin(
    val fragmentStableId: String,
    val placeName: String,
    val latitude: Double,
    val longitude: Double
)
