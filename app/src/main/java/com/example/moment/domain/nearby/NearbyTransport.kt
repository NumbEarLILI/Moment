package com.example.moment.domain.nearby

enum class NearbyTransport {
    /** Wi-Fi Direct 聊天室：一台建房，其余加入，组主做转发。 */
    WifiDirect,

    /** 蓝牙组网：每台设备自己广播、自己扫描、自己连邻居，没有房主。 */
    Bluetooth,

    /** 家庭 NAS（WebDAV）上的 Moment 账号一对一文字聊天。 */
    Nas
}
