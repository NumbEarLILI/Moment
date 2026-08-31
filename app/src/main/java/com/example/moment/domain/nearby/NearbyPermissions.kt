package com.example.moment.domain.nearby

import android.Manifest
import android.os.Build

/**
 * 附近组网需要的运行时权限。
 *
 * Wi-Fi Direct：Android 13 起用 `NEARBY_WIFI_DEVICES`（并声明 neverForLocation）；
 * 13 以下只认定位权限。
 *
 * 蓝牙：Android 12 起拆成扫描 / 连接 / 广播三条；12 以下扫描还要定位权限。
 */
object NearbyPermissions {
    fun required(sdkInt: Int): List<String> = wifiRequired(sdkInt)

    fun wifiRequired(sdkInt: Int): List<String> =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun bluetoothRequired(sdkInt: Int): List<String> =
        if (sdkInt >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun rationale(sdkInt: Int): String = wifiRationale(sdkInt)

    fun wifiRationale(sdkInt: Int): String =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            "需要「附近的设备」权限才能搜索到旁边的手机，本功能不会用它定位。"
        } else {
            "Android 13 以下的系统把 Wi-Fi 直连归在定位权限里，需要授予定位权限才能搜索到旁边的手机。"
        }

    fun bluetoothRationale(sdkInt: Int): String =
        if (sdkInt >= Build.VERSION_CODES.S) {
            "需要蓝牙权限才能自动发现附近同样打开这个页面的人。本功能不会用它定位。"
        } else {
            "Android 12 以下的系统把蓝牙扫描归在定位权限里，需要授予定位权限才能找到附近的人。"
        }
}
