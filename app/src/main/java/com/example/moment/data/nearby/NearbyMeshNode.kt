package com.example.moment.data.nearby

import com.example.moment.domain.nearby.BleConnectPolicy
import com.example.moment.domain.nearby.NearbyChatFrame
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class MeshRole {
    /** Wi-Fi Direct 组主：监听所有接入的设备，并在它们之间转发。 */
    RoomHost,

    /** 组内成员：只和组主保持一条链路。 */
    RoomMember
}

/**
 * 本机在网格里的那一份：维护所有邻居链路，把收发抽象成「一组邻居」。
 *
 * 只管链路，不管路由——转发给谁、要不要转发由
 * [com.example.moment.domain.nearby.NearbyMeshRouter] 决定。
 *
 * Wi-Fi Direct 组内成员之间不能直连，所以那条路径是以组主为中心的星形。
 * 蓝牙路径里每台设备都跟身边的人直连，消息按跳数洪泛，才是去中心的网。
 */
class NearbyMeshNode(
    private val connector: NearbyChatConnector? = null
) : Closeable {

    sealed interface Event {
        data class NeighborJoined(val neighborId: String) : Event

        data class NeighborLeft(val neighborId: String) : Event

        data class Received(val neighborId: String, val frame: NearbyChatFrame) : Event
    }

    private val neighbors = ConcurrentHashMap<String, NearbyLink>()
    private val neighborSequence = AtomicLong()
    private val _events = Channel<Event>(Channel.UNLIMITED)

    val events: Flow<Event> = _events.receiveAsFlow()

    val neighborCount: Int
        get() = neighbors.size

    /**
     * 挂起运行直到取消（组主）或直到与组主的链路断开（组内成员）。
     *
     * 注意：TCP 链路的读取是阻塞的，取消协程本身弹不出来，必须再调 [close]。
     */
    suspend fun run(
        role: MeshRole,
        hostAddress: String,
        port: Int,
        connectTimeoutMillis: Long
    ) = coroutineScope {
        val connector = connector ?: error("Wi-Fi Direct 路径需要 NearbyChatConnector")
        val scope = this
        when (role) {
            MeshRole.RoomHost -> try {
                connector.serveGroupOwner(port) { link ->
                    scope.accept(link, WIFI_MAX_NEIGHBORS)
                }
            } catch (error: Throwable) {
                // 监听结束（被取消或出错）时必须先关掉所有链路：阻塞中的读弹不出来，
                // 否则这里的 coroutineScope 会永远等着那些读协程。
                close()
                throw error
            }

            MeshRole.RoomMember -> serveNeighbor(
                connector.connectToGroupOwner(hostAddress, port, connectTimeoutMillis)
            )
        }
    }

    /**
     * 蓝牙组网：广播 + 扫描 + 双向 GATT，新链路从 [onLink] 进来。
     * 挂起到 [keepAlive] 返回（通常是取消）。
     */
    suspend fun runBluetooth(
        maxNeighbors: Int = BleConnectPolicy.MAX_NEIGHBORS,
        keepAlive: suspend (onLink: (NearbyLink) -> Unit) -> Unit
    ) = coroutineScope {
        val scope = this
        try {
            keepAlive { link -> scope.accept(link, maxNeighbors) }
        } finally {
            close()
        }
    }

    /** 发给除 [exceptNeighborId] 以外的所有邻居；单条链路发失败不影响其他人。 */
    suspend fun broadcast(frame: NearbyChatFrame, exceptNeighborId: String? = null) {
        for ((neighborId, link) in neighbors) {
            if (neighborId == exceptNeighborId) continue
            runCatching { link.send(frame) }
        }
    }

    suspend fun sendTo(neighborId: String, frame: NearbyChatFrame) {
        val link = neighbors[neighborId] ?: return
        runCatching { link.send(frame) }
    }

    override fun close() {
        val open = neighbors.values.toList()
        neighbors.clear()
        open.forEach { it.close() }
    }

    private fun CoroutineScope.accept(link: NearbyLink, maxNeighbors: Int) {
        if (neighbors.size >= maxNeighbors) {
            link.close()
        } else {
            launch { serveNeighbor(link) }
        }
    }

    private suspend fun serveNeighbor(link: NearbyLink) {
        val neighborId = "n${neighborSequence.incrementAndGet()}"
        neighbors[neighborId] = link
        _events.trySend(Event.NeighborJoined(neighborId))
        try {
            link.incoming().collect { frame ->
                _events.trySend(Event.Received(neighborId, frame))
            }
        } finally {
            neighbors.remove(neighborId)
            link.close()
            _events.trySend(Event.NeighborLeft(neighborId))
        }
    }

    private companion object {
        /** Wi-Fi Direct 组主实际能带的设备数有限，超出的直接拒掉。 */
        const val WIFI_MAX_NEIGHBORS = 8
    }
}
