package com.example.moment.data.location

/**
 * 与高德等 **国内地图（GCJ-02）** 对齐时的基准策略：**不依赖 Google Play / GMS**。
 *
 * 国内厂商 ROM 上系统 [android.location.LocationManager.FUSED_PROVIDER] 常见已为 **GCJ-02**
 * 或与地图一致；若误当作 WGS-84 再火星坐标转换会 **二次偏移**。
 *
 * 因此本应用对 **fused** 固定 **不按 WGS 对待**（不落库前再转）；仅 **GPS 卫星** 链路程式为 WGS-84，
 * 经 [ChinaCoordinateTransform.wgs84ToGcj02] 与 Web 手帐/选点页一致。
 */
internal object DomesticLocationDatumPolicy {

    /** 是否把 fused 的经纬度当作 WGS-84；国产优先场景下恒为 false。 */
    fun fusedOutputAssumedWgs84(): Boolean = false
}
