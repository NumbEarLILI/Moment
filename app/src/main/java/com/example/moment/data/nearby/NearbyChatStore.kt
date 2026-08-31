package com.example.moment.data.nearby

import com.example.moment.data.local.NearbyChatDao
import com.example.moment.data.local.entity.NearbyChatMessageEntity
import com.example.moment.domain.nearby.NearbyChatMessage
import com.example.moment.domain.nearby.NearbyTransport
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class NearbyChatStore @Inject constructor(
    private val dao: NearbyChatDao
) {
    fun observe(transport: NearbyTransport): Flow<List<NearbyChatMessage>> =
        dao.observeByTransport(transport.name).map { rows ->
            val trimmed = if (rows.size > KEEP) rows.takeLast(KEEP) else rows
            trimmed.map { it.toDomain() }
        }

    suspend fun save(message: NearbyChatMessage, transport: NearbyTransport) {
        dao.insert(message.toEntity(transport))
        val extra = dao.countByTransport(transport.name) - KEEP
        if (extra > 0) dao.deleteOldestByTransport(transport.name, extra)
    }

    private companion object {
        const val KEEP = 300
    }
}

internal fun NearbyChatMessageEntity.toDomain(): NearbyChatMessage = NearbyChatMessage(
    messageId = messageId,
    senderId = senderId,
    senderName = senderName,
    text = text,
    fromMe = fromMe,
    sentAtEpochMillis = sentAtEpochMillis
)

internal fun NearbyChatMessage.toEntity(transport: NearbyTransport): NearbyChatMessageEntity =
    NearbyChatMessageEntity(
        messageId = messageId,
        senderId = senderId,
        senderName = senderName,
        text = text,
        fromMe = fromMe,
        sentAtEpochMillis = sentAtEpochMillis,
        transport = transport.name
    )
