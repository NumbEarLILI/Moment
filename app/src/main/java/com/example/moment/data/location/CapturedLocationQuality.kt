package com.example.moment.data.location

import android.location.LocationManager

internal data class CapturedLocationCandidate(
    val provider: String?,
    val accuracyMeters: Float?
)

internal object CapturedLocationQuality {
    private const val GOOD_ENOUGH_ACCURACY_METERS = 50f

    fun isGoodEnough(accuracyMeters: Float?): Boolean =
        accuracyMeters != null && accuracyMeters <= GOOD_ENOUGH_ACCURACY_METERS

    fun isBetter(candidate: CapturedLocationCandidate, current: CapturedLocationCandidate?): Boolean {
        if (current == null) return true
        val candidateAccuracy = candidate.accuracyMeters
        val currentAccuracy = current.accuracyMeters
        return when {
            candidateAccuracy != null && currentAccuracy != null -> candidateAccuracy < currentAccuracy
            candidateAccuracy != null -> true
            currentAccuracy != null -> false
            else -> providerRank(candidate.provider) < providerRank(current.provider)
        }
    }

    private fun providerRank(provider: String?): Int =
        when (provider) {
            LocationManager.GPS_PROVIDER -> 0
            "fused" -> 1
            LocationManager.NETWORK_PROVIDER -> 2
            else -> 3
        }
}
