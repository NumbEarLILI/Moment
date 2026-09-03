package com.example.moment.data.nearby

import com.example.moment.data.local.NearbyChatDao
import com.example.moment.data.local.entity.NearbyChatMessageEntity
import com.example.moment.domain.naschat.NasChatThreadPreview
import com.example.moment.domain.nearby.NearbyChatMessage
import com.example.moment.domain.nearby.NearbyTransport
import com.example.moment.domain.nearby.SharedFragmentCard
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class NearbyChatStore @Inject constructor(
    private val dao: NearbyChatDao,
    private val shareImages: NearbyShareImageStore
) {
    fun observe(transport: NearbyTransport, peerId: String = ""): Flow<List<NearbyChatMessage>> =
        dao.observeByTransportAndPeer(transport.name, peerId).map { rows ->
            val trimmed = if (rows.size > KEEP) rows.takeLast(KEEP) else rows
            trimmed.map { it.toDomain() }
        }

    fun observeThreads(transport: NearbyTransport): Flow<List<NasChatThreadPreview>> =
        dao.observeByTransport(transport.name).map { rows ->
            rows
                .filter { it.peerId.isNotBlank() }
                .groupBy { it.peerId }
                .map { (peerId, messages) ->
                    val last = messages.maxBy { it.sentAtEpochMillis }
                    val peerName = messages
                        .filter { it.senderId == peerId }
                        .maxByOrNull { it.sentAtEpochMillis }
                        ?.senderName
                        .orEmpty()
                    NasChatThreadPreview(
                        peerId = peerId,
                        peerName = peerName,
                        lastText = last.text,
                        lastAtEpochMillis = last.sentAtEpochMillis
                    )
                }
                .sortedByDescending { it.lastAtEpochMillis }
        }

    suspend fun save(message: NearbyChatMessage, transport: NearbyTransport) {
        dao.insert(message.toEntity(transport))
        val extra = dao.countByTransportAndPeer(transport.name, message.peerId) - KEEP
        if (extra > 0) {
            val doomed = dao.oldestByTransportAndPeer(transport.name, message.peerId, extra)
            dao.deleteOldestByTransportAndPeer(transport.name, message.peerId, extra)
            doomed.forEach { shareImages.deleteIfManaged(it.imagePath) }
        }
    }

    private companion object {
        const val KEEP = 300
    }
}

private val cardJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun NearbyChatMessageEntity.toDomain(): NearbyChatMessage = NearbyChatMessage(
    messageId = messageId,
    senderId = senderId,
    senderName = senderName,
    text = text,
    fromMe = fromMe,
    sentAtEpochMillis = sentAtEpochMillis,
    fragment = fragmentJson.takeIf { it.isNotBlank() }?.let {
        runCatching { cardJson.decodeFromString<SharedFragmentCard>(it) }.getOrNull()
    },
    imagePath = imagePath,
    peerId = peerId
)

internal fun NearbyChatMessage.toEntity(transport: NearbyTransport): NearbyChatMessageEntity =
    NearbyChatMessageEntity(
        messageId = messageId,
        senderId = senderId,
        senderName = senderName,
        text = text,
        fromMe = fromMe,
        sentAtEpochMillis = sentAtEpochMillis,
        transport = transport.name,
        fragmentJson = fragment?.let { cardJson.encodeToString(SharedFragmentCard.serializer(), it) }.orEmpty(),
        imagePath = imagePath,
        peerId = peerId
    )
