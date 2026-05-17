package com.example.moment.data.location

import android.content.Context
import android.content.pm.PackageManager

/**
 * 判断是否应把系统 [android.location.LocationManager.FUSED_PROVIDER] 按 **WGS-84** 对待。
 *
 * 仅检测 `com.google.android.gms` 包存在会产生误判：部分机型带不可用的占位/残装 GMS，或厂商 fused 仍为 GCJ，
 * 若再按 WGS 做火星坐标转换会 **二次偏移**。以 [GoogleApiAvailability.isGooglePlayServicesAvailable] 为准（反射调用，不增加 Play 依赖）。
 */
internal object GooglePlayServicesAvailability {

    private const val GMS_PACKAGE = "com.google.android.gms"
    /** [com.google.android.gms.common.ConnectionResult.SUCCESS] */
    private const val CONNECTION_RESULT_SUCCESS = 0

    fun isPlayServicesUsable(context: Context): Boolean {
        if (!isGmsPackageInstalled(context)) return false
        return runCatching {
            val clazz = Class.forName("com.google.android.gms.common.GoogleApiAvailability")
            val instance = clazz.getMethod("getInstance").invoke(null)
            val code = clazz
                .getMethod("isGooglePlayServicesAvailable", Context::class.java)
                .invoke(instance, context.applicationContext) as Int
            code == CONNECTION_RESULT_SUCCESS
        }.getOrDefault(false)
    }

    private fun isGmsPackageInstalled(context: Context): Boolean =
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(GMS_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
}
