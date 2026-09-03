package com.example.moment.domain.naschat

/** 一对一会话在 WebDAV 上的目录约定。 */
object NasChatPaths {
    const val ROOT = "MomentApp"
    const val CHAT = "chat"
    const val CHUNK_PREFIX = "chunk-"
    const val CHUNK_SUFFIX = ".jsonl"

    fun conversationFolder(userIdA: String, userIdB: String): String {
        val a = userIdA.trim()
        val b = userIdB.trim()
        require(a.isNotEmpty() && b.isNotEmpty()) { "userId 不能为空" }
        require(a != b) { "不能和自己建立会话" }
        return if (a < b) "${a}_$b" else "${b}_$a"
    }

    fun collectionSegments(selfUserId: String, peerUserId: String): List<String> =
        listOf(ROOT, CHAT, conversationFolder(selfUserId, peerUserId))

    fun chatRootSegments(): List<String> = listOf(ROOT, CHAT)

    fun peerIdFromFolder(folderName: String, selfUserId: String): String? {
        val parts = folderName.split('_')
        if (parts.size < 2) return null
        // userId 是 UUID，本身不含 '_'；仍按「两端」切：第一个 UUID 长度 36。
        val self = selfUserId.trim()
        if (self.isEmpty()) return null
        val idx = folderName.indexOf('_')
        if (idx <= 0 || idx == folderName.lastIndex) return null
        val left = folderName.substring(0, idx)
        val right = folderName.substring(idx + 1)
        return when (self) {
            left -> right.takeIf { it.isNotEmpty() }
            right -> left.takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    fun chunkFileName(index: Int): String {
        require(index >= 0)
        return CHUNK_PREFIX + index.toString().padStart(3, '0') + CHUNK_SUFFIX
    }

    fun parseChunkIndex(fileName: String): Int? {
        if (!fileName.startsWith(CHUNK_PREFIX) || !fileName.endsWith(CHUNK_SUFFIX)) return null
        val digits = fileName.removePrefix(CHUNK_PREFIX).removeSuffix(CHUNK_SUFFIX)
        return digits.toIntOrNull()?.takeIf { it >= 0 }
    }
}
