package com.example.moment.data.location

import android.location.LocationManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * WGS-84（国际 GPS）与 GCJ-02（国测局 —— 高德/国内地图）互转。
 *
 * 自动定位存库时：仅对 **卫星 GPS** 结果做 WGS→GCJ；融合/网络定位在国内常已是 GCJ，避免二次偏移。
 * 选点页高德地图与逆地理均使用 GCJ-02。
 *
 * 算法源自广泛使用的 Mars 坐标变换（如 [eviltransform](https://github.com/googollee/eviltransform)）。
 */
object ChinaCoordinateTransform {

    /** 转成高德系坐标（国内地图、高德逆地理 Web 接口）。 */
    fun wgs84ToGcj02(wgsLat: Double, wgsLng: Double): Pair<Double, Double> {
        if (outOfChina(wgsLat, wgsLng)) return wgsLat to wgsLng
        val d = delta(wgsLat, wgsLng)
        return (wgsLat + d.first) to (wgsLng + d.second)
    }

    /**
     * 近似 GCJ → WGS（米级误差），供 [NominatimReverseGeocoder] 等 WGS 系服务使用。
     * 存入库内的坐标在转为 GCJ 后，调 OSM 前应做一次逆变换。
     */
    fun gcj02ToWgs84(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
        if (outOfChina(gcjLat, gcjLng)) return gcjLat to gcjLng
        val d = delta(gcjLat, gcjLng)
        return (gcjLat - d.first) to (gcjLng - d.second)
    }

    /**
     * 仅对 **卫星 GPS** 结果做 WGS→GCJ：卫星轨位与接收机解算在标准里对应 WGS-84。
     *
     * **融合定位**（`fused` / [LocationManager.FUSED_PROVIDER]）在国内机型上常已由系统/芯片输出 **GCJ-02**
     *（或与高德路网对齐的坐标）；若再按 WGS 做一次火星偏移，会产生 **二次偏千米级** 的典型问题。
     *
     * **网络定位**在国内亦多为 GCJ 或地图系，同样不再转换。
     */
    fun shouldConvertCapturedLocationToGcj02(provider: String?): Boolean {
        if (provider.isNullOrEmpty()) return false
        return provider == LocationManager.GPS_PROVIDER
    }

    private fun outOfChina(lat: Double, lng: Double): Boolean =
        lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271

    private fun delta(lat: Double, lng: Double): Pair<Double, Double> {
        val a = 6378245.0
        val ee = 0.00669342162296594323
        var dLat = transformLat(lng - 105.0, lat - 35.0)
        var dLng = transformLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - ee * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (a / sqrtMagic * cos(radLat) * PI)
        return dLat to dLng
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
