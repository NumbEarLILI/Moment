package com.example.moment.domain.nearby

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
}
