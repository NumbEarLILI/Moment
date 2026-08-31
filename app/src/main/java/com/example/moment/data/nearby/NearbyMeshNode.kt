package com.example.moment.data.nearby

import com.example.moment.domain.nearby.NearbyChatFrame
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
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
 * Wi-Fi Direct 的组内成员之间不能直连，只有组主能同时看到所有人，所以现在的拓扑是
 * 以组主为中心的星形。路由层不依赖这一点，将来接入别的链路即可变成真正的多跳。
 */
class NearbyMeshNode(
    private val connector: NearbyChatConnector
) : Closeable {

    sealed interface Event {
        data class NeighborJoined(val neighborId: String) : Event

        data class NeighborLeft(val neighborId: String) : Event

        data class Received(val neighborId: String, val frame: NearbyChatFrame) : Event
    }

    private val neighbors = ConcurrentHashMap<String, NearbyChatLink>()
    private val neighborSequence = AtomicLong()
    private val _events = Channel<Event>(Channel.UNLIMITED)

    val events: Flow<Event> = _events.receiveAsFlow()

    val neighborCount: Int
        get() = neighbors.size

    /**
     * 挂起运行直到取消（组主）或直到与组主的链路断开（组内成员）。
     *
     * 注意：链路的读取是阻塞的，取消协程本身弹不出来，必须再调 [close]。
     */
    suspend fun run(
        role: MeshRole,
        hostAddress: String,
        port: Int,
        connectTimeoutMillis: Long
    ) = coroutineScope {
        when (role) {
            MeshRole.RoomHost -> try {
                connector.serveGroupOwner(port) { link ->
                    if (neighbors.size >= MAX_NEIGHBORS) {
                        link.close()
                    } else {
                        launch { serveNeighbor(link) }
                    }
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

    private suspend fun serveNeighbor(link: NearbyChatLink) {
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
        const val MAX_NEIGHBORS = 8
    }
}
