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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    /**
     * 并行向各 provider 要一次当前位置，再按精度/时效挑出最优。
     *
     * 原先顺序「融合 → GPS → 网络」会在融合先返回时直接采用，而融合常为 Wi‑Fi/基站粗定位或旧点，
     * 更准的 GPS 根本不会被用到，在高德上会表现成明显偏移。
     */
    private suspend fun fetchBestLocation(): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = kotlin.collections.buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { lm.isProviderEnabled(it) }

        if (providers.isEmpty()) return null

        val executor = Executors.newSingleThreadExecutor()
        return try {
            coroutineScope {
                val candidates: List<Location> = providers.map { provider ->
                    async {
                        withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                            fetchCurrentLocation(lm, provider, executor)
                        }
                    }
                }.awaitAll().filterNotNull()
                var best: Location? = null
                for (loc in candidates) {
                    if (isBetterLocation(loc, best)) best = loc
                }
                best
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private suspend fun fetchCurrentLocation(
        lm: LocationManager,
        provider: String,
        executor: java.util.concurrent.Executor,
    ): Location? = suspendCancellableCoroutine<Location?> { cont ->
        val cancel = CancellationSignal()
        cont.invokeOnCancellation { cancel.cancel() }
        val finished = AtomicBoolean(false)
        LocationManagerCompat.getCurrentLocation(lm, provider, cancel, executor) { l ->
            if (finished.compareAndSet(false, true)) {
                cont.resume(l)
            }
        }
    }

    /**
     * 与 Android 官方「Best Practices」一致：综合精度与时间戳挑选更可靠的读数。
     * 无精度元数据时视为最差，避免错误地压过其它读数。
     */
    private fun isBetterLocation(location: Location, currentBest: Location?): Boolean {
        if (currentBest == null) return true
        val timeDelta = location.time - currentBest.time
        val isSignificantlyNewer = timeDelta > TWO_MINUTES_MS
        val isNewer = timeDelta > 0
        val accuracyNew = effectiveAccuracyMeters(location)
        val accuracyOld = effectiveAccuracyMeters(currentBest)
        val isSignificantlyLessAccurate = accuracyNew > accuracyOld * ACCURACY_COMPARISON_RATIO
        val isLessAccurate = accuracyNew > accuracyOld
        val isMoreAccurate = accuracyNew < accuracyOld

        if (isMoreAccurate && isNewer) return true
        if (isMoreAccurate && !isNewer && !isSignificantlyLessAccurate) return true
        if (isNewer && !isLessAccurate) return true
        if (isNewer && !isMoreAccurate && !isSignificantlyLessAccurate) return true
        if (isSignificantlyNewer && !isSignificantlyLessAccurate) return true
        return false
    }

    private fun effectiveAccuracyMeters(location: Location): Float =
        if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE

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
        private const val LOCATION_TIMEOUT_MS = 12_000L
        private const val PER_PROVIDER_TIMEOUT_MS = 11_000L
        private const val TWO_MINUTES_MS = 120_000L
        private const val ACCURACY_COMPARISON_RATIO = 2f
    }
}
