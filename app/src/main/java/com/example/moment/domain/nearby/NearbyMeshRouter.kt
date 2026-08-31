package com.example.moment.domain.nearby

/** 有界的「已见消息」表，用来终止洪泛。满了以后丢最早见到的那条。 */
class SeenMessageLog(private val capacity: Int = DEFAULT_CAPACITY) {
    private val seen = LinkedHashSet<String>()

    /** @return true 表示第一次见到，应当处理并继续转发。 */
    fun markSeen(messageId: String): Boolean {
        if (!seen.add(messageId)) return false
        while (seen.size > capacity) {
            seen.remove(seen.first())
        }
        return true
    }

    private companion object {
        const val DEFAULT_CAPACITY = 512
    }
}

/** 成员表：同一个 nodeId 以时间戳更新的那条记录为准。 */
class MeshRoster(private val capacity: Int = DEFAULT_CAPACITY) {
    private val members = LinkedHashMap<String, MeshMember>()

    /** @return true 表示这条记录带来了新信息，应当继续转发。 */
    fun apply(member: MeshMember): Boolean {
        if (member.nodeId.isBlank()) return false
        val existing = members[member.nodeId]
        if (existing != null && member.updatedAtEpochMillis <= existing.updatedAtEpochMillis) {
            return false
        }
        members[member.nodeId] = member
        prune()
        return true
    }

    fun snapshot(): List<MeshMember> = members.values.toList()

    fun present(): List<MeshMember> = members.values.filter { it.present }

    fun displayNameOf(nodeId: String): String? = members[nodeId]?.displayName

    /** 先清掉已经离开的旧成员，实在还超额才动在线的，避免对方乱发把表撑爆。 */
    private fun prune() {
        if (members.size <= capacity) return
        val departed = members.values.filter { !it.present }.map { it.nodeId }
        for (nodeId in departed) {
            if (members.size <= capacity) return
            members.remove(nodeId)
        }
        while (members.size > capacity) {
            members.remove(members.keys.first())
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 64
    }
}

/** 收到一帧后该做的事。[forward] 里的帧要发给「除来源以外」的所有邻居。 */
data class MeshOutcome(
    val deliver: NearbyChatFrame.Message? = null,
    val forward: List<NearbyChatFrame> = emptyList(),
    val rosterChanged: Boolean = false,
    /** Hello 帧带来的邻居身份，用于把链路和节点对应起来。 */
    val learnedNodeId: String? = null,
    val avatar: NearbyChatFrame.Avatar? = null,
    val fragmentShare: NearbyChatFrame.FragmentShare? = null
)

/**
 * 网格的路由逻辑：去重、TTL、成员表、转发决策。
 *
 * 不碰 socket 也不碰 Android，拓扑长什么样都适用——现在是「主机转发」的星形，
 * 以后换成多跳链路也不用改这里。
 */
class NearbyMeshRouter(
    val selfNodeId: String,
    private val seen: SeenMessageLog = SeenMessageLog(),
    private val roster: MeshRoster = MeshRoster()
) {
    private val avatarSeen = mutableMapOf<String, Long>()
    fun members(): List<MeshMember> = roster.present()

    fun displayNameOf(nodeId: String): String? = roster.displayNameOf(nodeId)

    /** 本机上线（或改名），返回要洪泛出去的通告。 */
    fun announceSelf(displayName: String, atEpochMillis: Long): NearbyChatFrame.Presence {
        val self = MeshMember(
            nodeId = selfNodeId,
            displayName = displayName,
            present = true,
            updatedAtEpochMillis = atEpochMillis
        )
        roster.apply(self)
        return NearbyChatFrame.Presence(self)
    }

    /** 新邻居刚接上时，单独发给它的那几帧。 */
    fun greeting(displayName: String, atEpochMillis: Long): List<NearbyChatFrame> {
        val self = MeshMember(
            nodeId = selfNodeId,
            displayName = displayName,
            present = true,
            updatedAtEpochMillis = atEpochMillis
        )
        roster.apply(self)
        return listOf(
            NearbyChatFrame.Hello(self),
            NearbyChatFrame.Roster(roster.snapshot())
        )
    }

    /** 本机要发的消息：先记进去重表，免得转了一圈回来又显示一遍。 */
    fun compose(
        messageId: String,
        body: String,
        displayName: String,
        atEpochMillis: Long
    ): NearbyChatFrame.Message {
        seen.markSeen(messageId)
        return NearbyChatFrame.Message(
            messageId = messageId,
            senderId = selfNodeId,
            senderName = displayName,
            body = body,
            sentAtEpochMillis = atEpochMillis,
            ttl = DEFAULT_TTL
        )
    }

    fun composeFragment(
        messageId: String,
        displayName: String,
        atEpochMillis: Long,
        card: SharedFragmentCard,
        jpeg: ByteArray = byteArrayOf()
    ): NearbyChatFrame.FragmentShare {
        seen.markSeen(messageId)
        return NearbyChatFrame.FragmentShare(
            messageId = messageId,
            senderId = selfNodeId,
            senderName = displayName,
            sentAtEpochMillis = atEpochMillis,
            ttl = DEFAULT_TTL,
            card = card,
            jpeg = jpeg
        )
    }

    fun receive(frame: NearbyChatFrame): MeshOutcome = when (frame) {
        is NearbyChatFrame.Hello -> {
            val changed = applySelfSafe(frame.self)
            MeshOutcome(
                forward = if (changed) listOf(NearbyChatFrame.Presence(frame.self)) else emptyList(),
                rosterChanged = changed,
                learnedNodeId = frame.self.nodeId.takeIf { it.isNotBlank() }
            )
        }

        is NearbyChatFrame.Presence -> {
            val changed = applySelfSafe(frame.member)
            MeshOutcome(
                forward = if (changed) listOf(frame) else emptyList(),
                rosterChanged = changed
            )
        }

        is NearbyChatFrame.Roster -> {
            val fresh = frame.members.filter { applySelfSafe(it) }
            MeshOutcome(
                forward = fresh.map { NearbyChatFrame.Presence(it) },
                rosterChanged = fresh.isNotEmpty()
            )
        }

        is NearbyChatFrame.Message -> {
            if (frame.senderId == selfNodeId || !seen.markSeen(frame.messageId)) {
                MeshOutcome()
            } else {
                MeshOutcome(deliver = frame, forward = listOfNotNull(forwarded(frame)))
            }
        }

        is NearbyChatFrame.FragmentShare -> {
            if (frame.senderId == selfNodeId || !seen.markSeen(frame.messageId)) {
                MeshOutcome()
            } else {
                MeshOutcome(fragmentShare = frame, forward = listOfNotNull(forwarded(frame)))
            }
        }

        is NearbyChatFrame.Avatar -> {
            if (frame.nodeId == selfNodeId || frame.jpeg.isEmpty()) {
                MeshOutcome()
            } else {
                val last = avatarSeen[frame.nodeId] ?: Long.MIN_VALUE
                if (frame.updatedAtEpochMillis <= last) {
                    MeshOutcome()
                } else {
                    avatarSeen[frame.nodeId] = frame.updatedAtEpochMillis
                    MeshOutcome(forward = listOf(frame), avatar = frame)
                }
            }
        }
    }

    /** 邻居掉线：本机是唯一知情的节点，替它把离线状态通告出去。 */
    fun onNeighborLost(nodeId: String, atEpochMillis: Long): NearbyChatFrame.Presence? {
        val known = roster.displayNameOf(nodeId) ?: return null
        val departed = MeshMember(
            nodeId = nodeId,
            displayName = known,
            present = false,
            updatedAtEpochMillis = atEpochMillis
        )
        return if (roster.apply(departed)) NearbyChatFrame.Presence(departed) else null
    }

    /** 别人替本机发的状态一律不采信，否则一次错误的离线通告会把自己踢出成员表。 */
    private fun applySelfSafe(member: MeshMember): Boolean =
        member.nodeId != selfNodeId && roster.apply(member)

    private fun forwarded(message: NearbyChatFrame.Message): NearbyChatFrame.Message? {
        val ttl = message.ttl - 1
        return if (ttl >= 1) message.copy(ttl = ttl) else null
    }

    private fun forwarded(share: NearbyChatFrame.FragmentShare): NearbyChatFrame.FragmentShare? {
        val ttl = share.ttl - 1
        return if (ttl >= 1) share.copy(ttl = ttl) else null
    }

    companion object {
        /** 初始跳数预算。星形拓扑只需要 2 跳，留出余量给以后的多跳链路。 */
        const val DEFAULT_TTL = 8
    }
}
