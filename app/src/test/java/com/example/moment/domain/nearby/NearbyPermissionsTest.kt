package com.example.moment.domain.nearby

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyPermissionsTest {

    @Test
    fun `android 13 and later asks for nearby wifi devices only`() {
        assertEquals(
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES),
            NearbyPermissions.required(Build.VERSION_CODES.TIRAMISU)
        )
    }

    @Test
    fun `older systems fall back to fine location`() {
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            NearbyPermissions.required(Build.VERSION_CODES.S_V2)
        )
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            NearbyPermissions.required(Build.VERSION_CODES.O)
        )
    }

    @Test
    fun `android 12 and later asks for the three bluetooth nearby permissions`() {
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ),
            NearbyPermissions.bluetoothRequired(Build.VERSION_CODES.S)
        )
    }

    @Test
    fun `older bluetooth scan falls back to fine location`() {
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            NearbyPermissions.bluetoothRequired(Build.VERSION_CODES.R)
        )
    }
}
