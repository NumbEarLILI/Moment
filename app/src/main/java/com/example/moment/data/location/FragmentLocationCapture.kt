package com.example.moment.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.example.moment.domain.model.FragmentLocation
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class FragmentLocationCapture @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }

    suspend fun captureIfPermitted(): FragmentLocation? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) return@withContext null

        val cts = CancellationTokenSource()
        val location = try {
            withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                runCatching {
                    fused.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cts.token
                    ).await()
                }.getOrNull()
            }
        } finally {
            cts.cancel()
        } ?: return@withContext null

        val label = geocodeWithTimeout(location.latitude, location.longitude, GEOCODE_TIMEOUT_MS)
        FragmentLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            label = label
        )
    }

    private fun hasLocationPermission(): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    /**
     * [Geocoder.getFromLocation] blocks and does not honor coroutine cancellation, so a hard wall
     * clock timeout on a worker thread is required to avoid freezing save.
     */
    private fun geocodeWithTimeout(lat: Double, lng: Double, timeoutMs: Long): String? {
        if (!Geocoder.isPresent()) return null
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<String?> {
            runCatching {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                addresses?.firstOrNull()?.let { addr ->
                    val line0 = addr.getAddressLine(0)
                    if (!line0.isNullOrBlank()) line0
                    else {
                        listOfNotNull(addr.locality, addr.adminArea, addr.countryName)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(" ")
                            .ifBlank { null }
                    }
                }
            }.getOrNull()
        }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            null
        } catch (_: Exception) {
            null
        } finally {
            executor.shutdownNow()
        }
    }

    private companion object {
        private const val LOCATION_TIMEOUT_MS = 8_000L
        private const val GEOCODE_TIMEOUT_MS = 3_500L
    }
}
