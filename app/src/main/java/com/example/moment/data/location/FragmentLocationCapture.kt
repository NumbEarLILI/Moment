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

        val location = withTimeoutOrNull(12_000) {
            runCatching {
                fused.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token
                ).await()
            }.getOrNull()
        } ?: return@withContext null

        val label = resolveLabel(location.latitude, location.longitude)
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

    private fun resolveLabel(lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
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
}
