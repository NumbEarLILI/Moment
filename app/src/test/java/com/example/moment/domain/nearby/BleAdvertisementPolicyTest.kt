package com.example.moment.domain.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleAdvertisementPolicyTest {

    @Test
    fun `node id manufacturer data plus flags fits in a legacy advertisement`() {
        assertTrue(
            BleAdvertisementPolicy.primaryPacketBytes(payloadSize = 16) <=
                BleAdvertisementPolicy.LEGACY_ADV_MAX_BYTES
        )
    }

    @Test
    fun `service uuid and manufacturer data together do not fit with flags`() {
        val combined = BleAdvertisementPolicy.primaryPacketBytes(payloadSize = 16) +
            BleAdvertisementPolicy.serviceUuid128FieldBytes()
        assertTrue(combined > BleAdvertisementPolicy.LEGACY_ADV_MAX_BYTES)
    }

    @Test
    fun `manufacturer data plus a typical phone name overflows the scan response`() {
        val nameBytes = "HUAWEI Mate 60 Pro".toByteArray(Charsets.UTF_8).size
        val bytes = BleAdvertisementPolicy.scanResponsePacketBytes(
            manufacturerPayloadSize = 16,
            includeServiceUuid = false,
            deviceNameUtf8Length = nameBytes
        )
        assertTrue(bytes > BleAdvertisementPolicy.LEGACY_ADV_MAX_BYTES)
    }

    @Test
    fun `service uuid alone fits in the scan response without a device name`() {
        val bytes = BleAdvertisementPolicy.scanResponsePacketBytes(
            manufacturerPayloadSize = 0,
            includeServiceUuid = true,
            deviceNameUtf8Length = 0
        )
        assertTrue(bytes <= BleAdvertisementPolicy.LEGACY_ADV_MAX_BYTES)
    }

    @Test
    fun `advertising is attempted even when the chipset lies about multi advertisement`() {
        assertTrue(
            BleAdvertisementPolicy.shouldAdvertise(
                hasAdvertiser = true,
                multipleAdvertisementSupported = false
            )
        )
        assertTrue(
            !BleAdvertisementPolicy.shouldAdvertise(
                hasAdvertiser = false,
                multipleAdvertisementSupported = true
            )
        )
    }

    @Test
    fun `reads the peer node id from manufacturer data`() {
        val nodeId = "123e4567-e89b-12d3-a456-426614174000"
        assertEquals(
            nodeId,
            BleAdvertisementPolicy.peerNodeId(
                manufacturerData = BleMeshIds.nodeIdBytes(nodeId),
                hasServiceUuid = false,
                address = "AA:BB:CC:DD:EE:FF"
            )
        )
    }

    @Test
    fun `falls back to the ble address when only the service uuid is visible`() {
        assertEquals(
            "addr:aabbccddeeff",
            BleAdvertisementPolicy.peerNodeId(
                manufacturerData = null,
                hasServiceUuid = true,
                address = "AA:BB:CC:DD:EE:FF"
            )
        )
    }

    @Test
    fun `ignores unrelated ble advertisements`() {
        assertNull(
            BleAdvertisementPolicy.peerNodeId(
                manufacturerData = null,
                hasServiceUuid = false,
                address = "AA:BB:CC:DD:EE:FF"
            )
        )
    }

    @Test
    fun `round-trips a node id through manufacturer bytes`() {
        val nodeId = "6d6f6d65-6e74-4d65-7368-c0de0000000a"
        assertEquals(nodeId, BleMeshIds.nodeIdFromBytes(BleMeshIds.nodeIdBytes(nodeId)))
    }
}
