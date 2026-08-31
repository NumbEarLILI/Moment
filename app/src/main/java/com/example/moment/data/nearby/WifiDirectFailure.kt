package com.example.moment.data.nearby

import android.net.wifi.p2p.WifiP2pManager

/** [WifiP2pManager.ActionListener.onFailure] 的原因码，附带给用户看的中文说明。 */
class WifiDirectActionException(val reason: Int) : Exception(WifiDirectFailure.describe(reason))

object WifiDirectFailure {
    fun describe(reason: Int): String = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "这台设备不支持 Wi-Fi 直连"
        WifiP2pManager.BUSY -> "系统正忙，请稍后重试"
        WifiP2pManager.NO_SERVICE_REQUESTS -> "没有可用的服务请求"
        WifiP2pManager.ERROR -> "操作失败，请重试"
        else -> "操作失败（错误码 $reason）"
    }
}
