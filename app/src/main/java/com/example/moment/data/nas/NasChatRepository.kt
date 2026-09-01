package com.example.moment.data.nas

import com.example.moment.data.nearby.NearbyChatStore
import com.example.moment.data.preferences.UserPreferencesRepository
import com.example.moment.domain.model.NasWebdavConfig
import com.example.moment.domain.model.toNasWebdavConfig
import com.example.moment.domain.naschat.MomentAccountRef
import com.example.moment.domain.naschat.NasChatChunkPolicy
import com.example.moment.domain.naschat.NasChatJsonl
import com.example.moment.domain.naschat.NasChatPaths
import com.example.moment.domain.naschat.NasChatWireMessage
import com.example.moment.domain.nearby.NearbyChatMessage
import com.example.moment.domain.nearby.NearbyChatWire
import com.example.moment.domain.nearby.NearbyTransport
import com.example.moment.domain.repository.NasMomentAccountRepository
import java.io.IOException
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
class NasChatRepository @Inject constructor(
    private val webDavHttp: WebDavHttp,
    private val packager: NasDiaryWebDavPackager,
    private val accounts: NasMomentAccountRepository,
    private val preferences: UserPreferencesRepository,
    private val chatStore: NearbyChatStore,
    private val clock: Clock
) {
    suspend fun session(): NasChatSession? {
        val prefs = preferences.preferences.first()
        val config = prefs.toNasWebdavConfig()
        if (!config.isConfigured()) return null
        val userId = prefs.nasMomentStorageUserId.trim()
        val username = prefs.nasMomentAccountUsername.trim()
        if (userId.isEmpty() || username.isEmpty()) return null
        return NasChatSession(
            config = config,
            self = MomentAccountRef(userId = userId, username = username)
        )
    }

    suspend fun listContacts(): Result<List<MomentAccountRef>> {
        val session = session() ?: return Result.failure(IOException("请先配置 WebDAV 并登录 Moment 账号"))
        return accounts.listMomentAccounts(session.config).map { list ->
            list.filter { it.userId != session.self.userId }
        }
    }

    suspend fun findContact(query: String): Result<MomentAccountRef> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.failure(IOException("请输入对方用户名"))
        return listContacts().mapCatching { contacts ->
            contacts.find { it.username.equals(trimmed, ignoreCase = true) || it.userId == trimmed }
                ?: throw IOException("找不到这个账号")
        }
    }

    suspend fun pullThread(peer: MomentAccountRef): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = session() ?: throw IOException("请先配置 WebDAV 并登录 Moment 账号")
            val client = webDavHttp.clientFor(session.config)
            val root = requireRoot(session.config)
            val folder = packager.childUrl(
                root,
                NasChatPaths.collectionSegments(session.self.userId, peer.userId)
            )
            val names = runCatching { webDavHttp.propfindDirectChildNames(client, folder) }
                .getOrDefault(emptyList())
            val indices = names.mapNotNull(NasChatPaths::parseChunkIndex).sorted()
            for (index in indices) {
                val url = packager.childUrl(
                    root,
                    NasChatPaths.collectionSegments(session.self.userId, peer.userId) +
                        NasChatPaths.chunkFileName(index)
                )
                val body = webDavHttp.getBytesWithEtag(client, url) ?: continue
                val messages = NasChatJsonl.parseFile(body.bytes.decodeToString())
                for (wire in messages) {
                    chatStore.save(
                        wire.toLocal(selfId = session.self.userId, peerId = peer.userId),
                        NearbyTransport.Nas
                    )
                }
            }
        }
    }

    suspend fun sendText(peer: MomentAccountRef, raw: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = session() ?: throw IOException("请先配置 WebDAV 并登录 Moment 账号")
            val body = NearbyChatWire.sanitizeMessage(raw) ?: throw IOException("请输入内容")
            val wire = NasChatWireMessage(
                messageId = UUID.randomUUID().toString(),
                senderId = session.self.userId,
                senderName = session.self.username,
                body = body,
                sentAtEpochMillis = clock.millis()
            )
            putMessage(session, peer, wire)
            chatStore.save(wire.toLocal(selfId = session.self.userId, peerId = peer.userId), NearbyTransport.Nas)
        }
    }

    private suspend fun putMessage(
        session: NasChatSession,
        peer: MomentAccountRef,
        incoming: NasChatWireMessage
    ) {
        val client = webDavHttp.clientFor(session.config)
        val root = requireRoot(session.config)
        webDavHttp.ensureCollectionPath(
            client,
            root,
            NasChatPaths.collectionSegments(session.self.userId, peer.userId)
        )
        val line = NasChatJsonl.encodeLine(incoming) + "\n"
        val incomingBytes = NasChatJsonl.utf8ByteSize(line)
        var attempt = 0
        while (attempt < MAX_PUT_ATTEMPTS) {
            attempt += 1
            val folder = packager.childUrl(
                root,
                NasChatPaths.collectionSegments(session.self.userId, peer.userId)
            )
            val names = runCatching { webDavHttp.propfindDirectChildNames(client, folder) }
                .getOrDefault(emptyList())
            val indices = names.mapNotNull(NasChatPaths::parseChunkIndex)
            val active = NasChatChunkPolicy.activeIndex(indices)
            val activeUrl = packager.childUrl(
                root,
                NasChatPaths.collectionSegments(session.self.userId, peer.userId) +
                    NasChatPaths.chunkFileName(active)
            )
            val existing = webDavHttp.getBytesWithEtag(client, activeUrl)
            val currentBytes = existing?.bytes?.size ?: 0
            if (existing != null && NasChatChunkPolicy.shouldRollOver(currentBytes, incomingBytes)) {
                val next = NasChatChunkPolicy.nextIndex(indices)
                val nextUrl = packager.childUrl(
                    root,
                    NasChatPaths.collectionSegments(session.self.userId, peer.userId) +
                        NasChatPaths.chunkFileName(next)
                )
                val created = webDavHttp.putBytesConditional(
                    client = client,
                    url = nextUrl,
                    bytes = NasChatJsonl.encodeFile(listOf(incoming)).toByteArray(Charsets.UTF_8),
                    contentType = "application/jsonl; charset=utf-8",
                    ifNoneMatchAny = true
                )
                if (created.ok) return
                if (created.conflict) continue
            } else {
                val merged = NasChatJsonl.merge(
                    NasChatJsonl.parseFile(existing?.bytes?.decodeToString().orEmpty()),
                    listOf(incoming)
                )
                val outcome = webDavHttp.putBytesConditional(
                    client = client,
                    url = activeUrl,
                    bytes = NasChatJsonl.encodeFile(merged).toByteArray(Charsets.UTF_8),
                    contentType = "application/jsonl; charset=utf-8",
                    ifMatch = existing?.etag,
                    ifNoneMatchAny = existing == null
                )
                if (outcome.ok) return
                if (outcome.conflict) continue
            }
        }
        throw IOException("发送失败，请稍后重试")
    }

    private fun requireRoot(config: NasWebdavConfig): HttpUrl =
        config.baseUrl.trim().toHttpUrlOrNull() ?: throw IOException("无效的 WebDAV 根地址")

    private fun NasChatWireMessage.toLocal(selfId: String, peerId: String) = NearbyChatMessage(
        messageId = messageId,
        senderId = senderId,
        senderName = senderName,
        text = body,
        fromMe = senderId == selfId,
        sentAtEpochMillis = sentAtEpochMillis,
        peerId = peerId
    )

    data class NasChatSession(
        val config: NasWebdavConfig,
        val self: MomentAccountRef
    )

    private companion object {
        const val MAX_PUT_ATTEMPTS = 6
    }
}
