package com.example.moment.domain.naschat

/** 当前块超过 1MB 后滚到下一块，避免单文件无限变大。 */
object NasChatChunkPolicy {
    const val MAX_CHUNK_BYTES = 1 * 1024 * 1024

    fun shouldRollOver(currentBytes: Int, incomingUtf8Bytes: Int): Boolean {
        if (currentBytes <= 0) return false
        return currentBytes + incomingUtf8Bytes > MAX_CHUNK_BYTES
    }

    fun activeIndex(existing: List<Int>): Int = existing.maxOrNull() ?: 0

    fun nextIndex(existing: List<Int>): Int = activeIndex(existing) + 1
}
