package com.example.moment.domain.nearby

import android.Manifest
import android.os.Build

/**
 * Wi-Fi Direct 搜索附近设备需要的运行时权限。
 *
 * Android 13 起可用 `NEARBY_WIFI_DEVICES` 并声明 `neverForLocation`，不必再要定位权限；
 * 13 以下系统只认 `ACCESS_FINE_LOCATION`，否则 `discoverPeers` 永远搜不到设备。
 */
object NearbyPermissions {
    fun required(sdkInt: Int): List<String> =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** 权限被拒时展示的说明文案。 */
    fun rationale(sdkInt: Int): String =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            "需要「附近的设备」权限才能搜索到旁边的手机，本功能不会用它定位。"
        } else {
            "Android 13 以下的系统把 Wi-Fi 直连归在定位权限里，需要授予定位权限才能搜索到旁边的手机。"
        }
}
