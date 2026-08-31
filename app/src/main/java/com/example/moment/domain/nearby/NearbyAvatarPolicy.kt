package com.example.moment.domain.nearby

object NearbyAvatarPolicy {
    const val MAX_EDGE_PX = 96
    const val JPEG_QUALITY = 55
    const val MAX_BYTES = 8 * 1024

    fun acceptable(size: Int): Boolean = size in 1..MAX_BYTES
}
