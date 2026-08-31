package com.example.moment.data.nearby

import android.net.wifi.p2p.WifiP2pManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiDirectFailureTest {

    @Test
    fun `known reason codes get a readable message`() {
        assertEquals("这台设备不支持 Wi-Fi 直连", WifiDirectFailure.describe(WifiP2pManager.P2P_UNSUPPORTED))
        assertEquals("系统正忙，请稍后重试", WifiDirectFailure.describe(WifiP2pManager.BUSY))
        assertEquals("没有可用的服务请求", WifiDirectFailure.describe(WifiP2pManager.NO_SERVICE_REQUESTS))
        assertEquals("操作失败，请重试", WifiDirectFailure.describe(WifiP2pManager.ERROR))
    }

    @Test
    fun `unknown reason codes keep the raw value visible`() {
        assertTrue(WifiDirectFailure.describe(99).contains("99"))
    }

    @Test
    fun `exception message matches the reason description`() {
        val exception = WifiDirectActionException(WifiP2pManager.BUSY)

        assertEquals(WifiP2pManager.BUSY, exception.reason)
        assertEquals(WifiDirectFailure.describe(WifiP2pManager.BUSY), exception.message)
    }
}
