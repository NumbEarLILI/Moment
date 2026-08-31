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
}
