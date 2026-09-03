package com.example.moment.data.location

import android.location.LocationManager

internal data class CapturedLocationCandidate(
    val provider: String?,
    val accuracyMeters: Float?,
    val elapsedRealtimeNanos: Long? = null
)

internal object CapturedLocationQuality {
    private const val GOOD_ENOUGH_ACCURACY_METERS = 50f
    private const val NEWER_BY_NANOS = 30L * 1_000_000_000L
    private const val MAX_FRESH_AGE_NANOS = 60L * 1_000_000_000L

    fun isGoodEnough(accuracyMeters: Float?): Boolean =
        accuracyMeters != null && accuracyMeters <= GOOD_ENOUGH_ACCURACY_METERS

    fun isFreshEnough(
        elapsedRealtimeNanos: Long?,
        nowElapsedRealtimeNanos: Long,
        maxAgeNanos: Long = MAX_FRESH_AGE_NANOS
    ): Boolean {
        if (elapsedRealtimeNanos == null || elapsedRealtimeNanos <= 0L) return false
        val age = nowElapsedRealtimeNanos - elapsedRealtimeNanos
        return age in 0L..maxAgeNanos
    }

    fun isBetter(candidate: CapturedLocationCandidate, current: CapturedLocationCandidate?): Boolean {
        if (current == null) return true
        val candidateTime = candidate.elapsedRealtimeNanos
        val currentTime = current.elapsedRealtimeNanos
        when {
            candidateTime != null && currentTime != null -> {
                val delta = candidateTime - currentTime
                if (delta >= NEWER_BY_NANOS) return true
                if (delta <= -NEWER_BY_NANOS) return false
            }
            candidateTime != null && currentTime == null -> return true
            candidateTime == null && currentTime != null -> return false
        }
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
