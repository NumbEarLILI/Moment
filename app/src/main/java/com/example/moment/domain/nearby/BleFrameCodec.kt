package com.example.moment.domain.nearby

import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * BLE 一次只能塞几十到几百字节，聊天帧往往更大，所以按块切。
 *
 * 每块第一个字节是标志：最低位为 1 表示这是一帧的最后一块。拼齐后再按 UTF-8 还原成 JSON。
 */
object BleFrameCodec {
    const val HEADER_SIZE = 1
    const val FLAG_END = 0x01

    /** 默认 ATT MTU 是 23，减去 3 字节 ATT 头再减去本协议 1 字节标志。 */
    const val DEFAULT_CHUNK_SIZE = 19

    fun split(payload: ByteArray, maxChunkSize: Int = DEFAULT_CHUNK_SIZE): List<ByteArray> {
        val maxPayload = (maxChunkSize - HEADER_SIZE).coerceAtLeast(1)
        if (payload.isEmpty()) return listOf(byteArrayOf(FLAG_END.toByte()))
        val chunks = ArrayList<ByteArray>()
        var offset = 0
        while (offset < payload.size) {
            val end = minOf(offset + maxPayload, payload.size)
            val chunk = ByteArray(1 + (end - offset))
            chunk[0] = if (end == payload.size) FLAG_END.toByte() else 0
            payload.copyInto(chunk, destinationOffset = 1, startIndex = offset, endIndex = end)
            chunks += chunk
            offset = end
        }
        return chunks
    }

    class Assembler(private val maxBytes: Int = NearbyChatWire.MAX_FRAME_CHARS) {
        private val buffer = ByteArrayOutputStream()

        fun push(chunk: ByteArray): ByteArray? {
            if (chunk.isEmpty()) return null
            val flags = chunk[0].toInt() and 0xFF
            val dataSize = chunk.size - 1
            if (buffer.size() + dataSize > maxBytes) {
                buffer.reset()
                throw IOException("蓝牙数据帧超长（>$maxBytes）")
            }
            if (dataSize > 0) buffer.write(chunk, 1, dataSize)
            if (flags and FLAG_END == 0) return null
            val complete = buffer.toByteArray()
            buffer.reset()
            return complete
        }

        fun reset() {
            buffer.reset()
        }
    }
}
