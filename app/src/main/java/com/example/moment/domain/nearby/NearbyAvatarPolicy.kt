package com.example.moment.domain.nearby

object NearbyAvatarPolicy {
    const val MAX_EDGE_PX = 96
    const val JPEG_QUALITY = 55
    const val MAX_BYTES = 8 * 1024

    fun acceptable(size: Int): Boolean = size in 1..MAX_BYTES

    /** BitmapFactory inSampleSize：解码后最长边仍 ≥ [MAX_EDGE_PX]，再交给缩放。 */
    fun decodeSampleSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE_PX): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
        var sampleSize = 1
        val halfWidth = width / 2
        val halfHeight = height / 2
        while (maxOf(halfWidth / sampleSize, halfHeight / sampleSize) >= maxEdge) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
