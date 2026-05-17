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
 * 写入前将坐标统一为 **GCJ-02**（与高德 Web 一致）：**GPS** 恒按 WGS→GCJ；**fused** 仅当
 * [GooglePlayServicesAvailability.isPlayServicesUsable] 为真时按 WGS→GCJ，否则假定 fused 已是 GCJ。
 * **网络**定位在国内多已为 GCJ，一般不转。
 *
 * 各 provider **并行**单次拉取，按精度/时效合并；在中国大陆且 GPS 精度尚可时 **优先 GPS**，
 * 减轻融合坐标系与高德不一致造成的固定偏移。
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

        val fusedAssumedWgs84 = GooglePlayServicesAvailability.isPlayServicesUsable(context)
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
     * 各 provider 并行单次拉取（顺序与语义：GPS → fused → 网络），再综合精度/时效与国内 GPS 优先策略取一点。
     */
    private suspend fun fetchBestLocation(): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = kotlin.collections.buildList {
            add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
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
                chooseLocationForAmap(candidates)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun chooseLocationForAmap(candidates: List<Location>): Location? {
        if (candidates.isEmpty()) return null
        val anyMainland = candidates.any {
            ChinaCoordinateTransform.appliesChinaOffset(it.latitude, it.longitude)
        }
        if (anyMainland) {
            val gpsReadings = candidates.filter { loc ->
                loc.provider == LocationManager.GPS_PROVIDER || loc.provider.equals("gps", ignoreCase = true)
            }
            val bestGps = pickBestLocation(gpsReadings)
            if (bestGps != null) {
                val accuracyOk =
                    !bestGps.hasAccuracy() || bestGps.accuracy <= MAINLAND_GPS_PREFER_WHEN_ACCURACY_LE_METERS
                if (accuracyOk) return bestGps
            }
        }
        return pickBestLocation(candidates)
    }

    private fun pickBestLocation(locations: List<Location>): Location? {
        var best: Location? = null
        for (loc in locations) {
            if (isBetterLocation(loc, best)) best = loc
        }
        return best
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
     * 与 Android 位置策略一致：综合精度与时间戳。
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

    private fun formatCoordinateLabel(lat: Double, lng: Double): String =
        String.format(Locale.CHINA, "约 %.4f，%.4f", lat, lng)

    private companion object {
        private const val LOCATION_TIMEOUT_MS = 15_000L
        private const val PER_PROVIDER_TIMEOUT_MS = 14_000L
        private const val TWO_MINUTES_MS = 120_000L
        private const val ACCURACY_COMPARISON_RATIO = 2f
        /** 国内优先 GPS 的最大可接受精度；更差时退回 fused/网络以免室内飘移过大 */
        private const val MAINLAND_GPS_PREFER_WHEN_ACCURACY_LE_METERS = 150f
    }
}
