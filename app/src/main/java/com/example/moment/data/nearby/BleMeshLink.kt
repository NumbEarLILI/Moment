package com.example.moment.data.nearby

import com.example.moment.domain.nearby.BleFrameCodec
import com.example.moment.domain.nearby.NearbyChatFrame
import com.example.moment.domain.nearby.NearbyChatWire
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 一条 BLE GATT 上的双向通道。发送时按当前 MTU 切块；接收侧由控制器把拼好的载荷送进来。
 */
class BleMeshLink(
    private val writeChunk: suspend (ByteArray) -> Unit,
    private val onClose: () -> Unit
) : NearbyLink {
    private val payloads = Channel<ByteArray>(Channel.UNLIMITED)
    private val writeMutex = Mutex()
    private val closed = AtomicBoolean(false)
    private val chunkSize = AtomicInteger(BleFrameCodec.DEFAULT_CHUNK_SIZE)

    fun updateChunkSize(maxChunkSize: Int) {
        chunkSize.set(maxChunkSize.coerceAtLeast(BleFrameCodec.HEADER_SIZE + 1))
    }

    fun offerPayload(payload: ByteArray) {
        payloads.trySend(payload)
    }

    override suspend fun send(frame: NearbyChatFrame) {
        val payload = NearbyChatWire.encode(frame).toByteArray(Charsets.UTF_8)
        val chunks = BleFrameCodec.split(payload, chunkSize.get())
        writeMutex.withLock {
            for (chunk in chunks) {
                writeChunk(chunk)
            }
        }
    }

    override fun incoming(): Flow<NearbyChatFrame> =
        payloads.receiveAsFlow().mapNotNull { payload ->
            NearbyChatWire.decode(payload.toString(Charsets.UTF_8))
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        payloads.close()
        onClose()
    }
}
