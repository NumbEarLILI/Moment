package com.example.moment.domain.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyAvatarPolicyTest {

    @Test
    fun `accepts a small jpeg and rejects empty or oversized ones`() {
        assertTrue(NearbyAvatarPolicy.acceptable(1))
        assertTrue(NearbyAvatarPolicy.acceptable(NearbyAvatarPolicy.MAX_BYTES))
        assertTrue(!NearbyAvatarPolicy.acceptable(0))
        assertTrue(!NearbyAvatarPolicy.acceptable(NearbyAvatarPolicy.MAX_BYTES + 1))
    }

    @Test
    fun `samples a camera-sized bitmap down before decode`() {
        assertEquals(32, NearbyAvatarPolicy.decodeSampleSize(4000, 3000))
        assertEquals(1, NearbyAvatarPolicy.decodeSampleSize(96, 96))
        assertEquals(1, NearbyAvatarPolicy.decodeSampleSize(48, 32))
    }
}
