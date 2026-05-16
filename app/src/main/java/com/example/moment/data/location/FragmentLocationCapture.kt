package com.example.moment.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.example.moment.domain.model.FragmentLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Uses Android framework [LocationManager] only — no Google Play services / Fused provider.
 * Suitable for devices without GMS (common in mainland China). Address text is a short
 * coordinate summary instead of reverse-geocoding (which often depends on Google backends).
 *
 * **Coordinates:** GPS / 融合定位得到的是 WGS-84，会在写入前转为 **GCJ-02**，与高德 Web 地图及逆地理接口一致；
 * 网络定位在国内机型上可能已是 GCJ-02，则不再二次转换。
 */
@Singleton
class FragmentLocationCapture @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun captureIfPermitted(): FragmentLocation? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) return@withContext null

        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            fetchBestLocation()
        } ?: return@withContext null

        val (lat, lng) =
            if (ChinaCoordinateTransform.shouldConvertCapturedLocationToGcj02(location.provider)) {
                ChinaCoordinateTransform.wgs84ToGcj02(location.latitude, location.longitude)
            } else {
                location.latitude to location.longitude
            }

        FragmentLocation(
            latitude = lat,
            longitude = lng,
            label = formatCoordinateLabel(lat, lng)
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

    private suspend fun fetchBestLocation(): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }

        val executor = Executors.newSingleThreadExecutor()
        return try {
            for (provider in providers) {
                if (!lm.isProviderEnabled(provider)) continue
                val cancel = CancellationSignal()
                val location = suspendCancellableCoroutine { cont ->
                    val finished = AtomicBoolean(false)
                    cont.invokeOnCancellation { cancel.cancel() }
                    LocationManagerCompat.getCurrentLocation(
                        lm,
                        provider,
                        cancel,
                        executor
                    ) { l ->
                        if (finished.compareAndSet(false, true)) {
                            cont.resume(l)
                        }
                    }
                }
                if (location != null) return location
            }
            null
        } finally {
            executor.shutdownNow()
        }
    }

    private fun formatCoordinateLabel(lat: Double, lng: Double): String =
        String.format(Locale.CHINA, "约 %.4f，%.4f", lat, lng)

    private companion object {
        private const val LOCATION_TIMEOUT_MS = 10_000L
    }
}
