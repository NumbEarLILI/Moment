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
 * 使用 [LocationManager]（含 Android 12+ 的 [LocationManager.FUSED_PROVIDER]），不依赖 Play 定位 SDK。
 * 自动定位标签为简短经纬度摘要，不做链路程式逆地理。
 *
 * 写入前将坐标统一为 **GCJ-02**（与高德 Web 一致）：GPS 恒按 WGS→GCJ；fused 在 **已安装 GMS** 时按 WGS→GCJ，**无 GMS** 的国内 ROM 上 fused 多已为 GCJ 则不再转换，避免二次偏移。
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

        val fusedAssumedWgs84 = hasGooglePlayServicesPackage()
        val (lat, lng) =
            if (ChinaCoordinateTransform.shouldConvertCapturedLocationToGcj02(
                    location.provider,
                    fusedOutputAssumedWgs84 = fusedAssumedWgs84,
                )
            ) {
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

    /**
     * 有 GMS 时系统 [LocationManager.FUSED_PROVIDER] 多为 WGS-84；无 GMS 的国内 ROM 上 fused 常为 GCJ，不可再转。
     */
    private fun hasGooglePlayServicesPackage(): Boolean =
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo("com.google.android.gms", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun formatCoordinateLabel(lat: Double, lng: Double): String =
        String.format(Locale.CHINA, "约 %.4f，%.4f", lat, lng)

    private companion object {
        private const val LOCATION_TIMEOUT_MS = 10_000L
    }
}
