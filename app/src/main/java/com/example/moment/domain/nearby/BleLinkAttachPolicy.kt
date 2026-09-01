package com.example.moment.domain.nearby

/**
 * 本机用 [android.bluetooth.BluetoothDevice.connectGatt] 连出去时，本地 GATT Server
 * 也常会收到同一台设备的 CONNECTED。若再按外围把这条连接当成 server link，
 * 会把真正的 client GATT 拆掉，结果变成只有「写 inbox」一条方向通。
 */
object BleLinkAttachPolicy {
    fun shouldAcceptAsServer(outgoingClient: Boolean): Boolean = !outgoingClient
}
