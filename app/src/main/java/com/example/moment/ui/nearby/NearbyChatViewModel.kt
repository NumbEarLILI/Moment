package com.example.moment.ui.nearby

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.data.nas.NasChatRepository
import com.example.moment.data.nearby.BleMeshController
import com.example.moment.data.nearby.MeshRole
import com.example.moment.data.nearby.NearbyAvatarThumbnail
import com.example.moment.data.nearby.NearbyChatConnector
import com.example.moment.data.nearby.NearbyChatStore
import com.example.moment.data.nearby.NearbyMeshNode
import com.example.moment.data.nearby.NearbyShareImageBytes
import com.example.moment.data.nearby.NearbyShareImageStore
import com.example.moment.data.nearby.PeerAvatarStore
import com.example.moment.data.nearby.WifiDirectController
import com.example.moment.data.nearby.WifiDirectEvent
import com.example.moment.data.nearby.WifiDirectGroup
import com.example.moment.data.preferences.UserPreferencesRepository
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.naschat.MomentAccountRef
import com.example.moment.domain.naschat.NasChatThreadPreview
import com.example.moment.domain.nearby.MeshMember
import com.example.moment.domain.nearby.NearbyChatFrame
import com.example.moment.domain.nearby.NearbyChatMessage
import com.example.moment.domain.nearby.NearbyChatStage
import com.example.moment.domain.nearby.NearbyChatWire
import com.example.moment.domain.nearby.NearbyFragmentSharePolicy
import com.example.moment.domain.nearby.NearbyMeshRouter
import com.example.moment.domain.nearby.NearbyPeer
import com.example.moment.domain.nearby.NearbyTransport
import com.example.moment.domain.nearby.shareCaption
import com.example.moment.domain.nearby.toSharedFragmentCard
import com.example.moment.domain.repository.FragmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NearbyChatUiState(
    val transport: NearbyTransport = NearbyTransport.Bluetooth,
    val supported: Boolean = true,
    val wifiDirectEnabled: Boolean = true,
    val bluetoothEnabled: Boolean = true,
    val stage: NearbyChatStage = NearbyChatStage.Idle,
    val peers: List<NearbyPeer> = emptyList(),
    val myDeviceName: String = "",
    val myDisplayName: String = "",
    val hostingRoom: Boolean = false,
    val members: List<MeshMember> = emptyList(),
    val messages: List<NearbyChatMessage> = emptyList(),
    val myAvatarPath: String = "",
    val myAvatarUpdatedAtEpochMs: Long = 0L,
    val peerAvatarPaths: Map<String, String> = emptyMap(),
    val shareableFragments: List<LifeFragment> = emptyList(),
    val statusText: String = "",
    val nasWebdavReady: Boolean = false,
    val nasLoggedIn: Boolean = false,
    val nasContacts: List<MomentAccountRef> = emptyList(),
    val nasThreads: List<NasChatThreadPreview> = emptyList(),
    val nasQuery: String = "",
    val nasPeerId: String = "",
    val nasPeerName: String = ""
) {
    /** 聊天区是否该占据整屏（刚断开时也还要看得到消息）。 */
    val showsConversation: Boolean
        get() = if (transport == NearbyTransport.Nas) {
            nasPeerId.isNotBlank()
        } else {
            stage == NearbyChatStage.InRoom || stage == NearbyChatStage.Closed
        }

    val canSend: Boolean
        get() = if (transport == NearbyTransport.Nas) {
            nasPeerId.isNotBlank() && nasLoggedIn && nasWebdavReady
        } else {
            stage == NearbyChatStage.InRoom
        }

    val isBluetooth: Boolean
        get() = transport == NearbyTransport.Bluetooth

    val isNas: Boolean
        get() = transport == NearbyTransport.Nas
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class NearbyChatViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val wifiDirectController: WifiDirectController,
    private val bleMeshController: BleMeshController,
    private val connector: NearbyChatConnector,
    private val chatStore: NearbyChatStore,
    private val nasChatRepository: NasChatRepository,
    private val shareImageStore: NearbyShareImageStore,
    private val peerAvatarStore: PeerAvatarStore,
    private val fragmentRepository: FragmentRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow(NearbyChatUiState())
    val uiState: StateFlow<NearbyChatUiState> = _uiState.asStateFlow()

    /** 输入框单独一条流，免得每敲一个字都重建整份聊天状态。 */
    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private var router = NearbyMeshRouter(selfNodeId = UUID.randomUUID().toString())

    /** 链路 id → 对端 nodeId，链路断开时才知道该替谁发离线通告。 */
    private val neighborNodes = mutableMapOf<String, String>()

    private var eventsJob: Job? = null
    private var sessionJob: Job? = null
    private var node: NearbyMeshNode? = null
    private var activeGroup: WifiDirectGroup? = null
    private var displayName: String = ""
    private var selfNodeId: String = ""
    private var started = false
    private var nasJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                _uiState.map { it.transport }.distinctUntilChanged(),
                _uiState.map { it.nasPeerId }.distinctUntilChanged()
            ) { transport, peerId -> transport to peerId }
                .flatMapLatest { (transport, peerId) ->
                    if (transport == NearbyTransport.Nas) {
                        chatStore.observe(transport, peerId)
                    } else {
                        chatStore.observe(transport)
                    }
                }
                .collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                }
        }
        viewModelScope.launch {
            _uiState
                .map { it.transport }
                .distinctUntilChanged()
                .flatMapLatest { transport ->
                    if (transport == NearbyTransport.Nas) {
                        chatStore.observeThreads(transport)
                    } else {
                        flowOf(emptyList())
                    }
                }
                .collect { threads ->
                    _uiState.update { it.copy(nasThreads = threads) }
                }
        }
        viewModelScope.launch {
            userPreferencesRepository.preferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        myAvatarPath = prefs.avatarImagePath,
                        myAvatarUpdatedAtEpochMs = prefs.avatarUpdatedAtEpochMs,
                        nasWebdavReady = prefs.nasWebdavBaseUrl.isNotBlank(),
                        nasLoggedIn = prefs.nasMomentStorageUserId.isNotBlank()
                    )
                }
            }
        }
        viewModelScope.launch {
            fragmentRepository.observeAllFragments().collect { fragments ->
                _uiState.update { it.copy(shareableFragments = fragments.take(SHAREABLE_LIMIT)) }
            }
        }
        _uiState.update { it.copy(peerAvatarPaths = peerAvatarStore.snapshot()) }
    }

    /** 权限拿到后调用；重复调用无副作用。 */
    fun start(transport: NearbyTransport = NearbyTransport.Bluetooth) {
        if (started) return
        started = true
        _uiState.update {
            it.copy(
                transport = transport,
                supported = when (transport) {
                    NearbyTransport.Bluetooth -> bleMeshController.isSupported
                    NearbyTransport.WifiDirect -> wifiDirectController.isSupported
                    NearbyTransport.Nas -> true
                },
                bluetoothEnabled = bleMeshController.isEnabled
            )
        }
        viewModelScope.launch {
            if (selfNodeId.isBlank()) {
                selfNodeId = userPreferencesRepository.getOrCreateNearbyNodeId()
            }
            displayName = resolveDisplayName()
            _uiState.update { it.copy(myDisplayName = displayName) }
            when (transport) {
                NearbyTransport.WifiDirect -> startWifi()
                NearbyTransport.Bluetooth -> startBluetooth()
                NearbyTransport.Nas -> startNas()
            }
        }
    }

    fun switchTransport(transport: NearbyTransport) {
        if (_uiState.value.transport == transport && started) return
        viewModelScope.launch {
            closeSession(announceLeaving = true)
            if (_uiState.value.transport == NearbyTransport.WifiDirect) {
                releaseGroup()
            }
            eventsJob?.cancel()
            eventsJob = null
            nasJob?.cancel()
            nasJob = null
            started = false
            _uiState.update {
                it.copy(
                    transport = transport,
                    messages = emptyList(),
                    nasPeerId = "",
                    nasPeerName = "",
                    nasQuery = "",
                    statusText = ""
                )
            }
            start(transport)
        }
    }

    fun onBluetoothEnabled() {
        _uiState.update { it.copy(bluetoothEnabled = bleMeshController.isEnabled) }
        if (_uiState.value.isBluetooth && bleMeshController.isEnabled && sessionJob?.isActive != true) {
            openBluetoothSession()
        }
    }

    fun onNasQueryChange(value: String) {
        _uiState.update { it.copy(nasQuery = value) }
    }

    fun openNasConversation(peer: MomentAccountRef) {
        if (peer.userId.isBlank()) return
        _uiState.update {
            it.copy(
                nasPeerId = peer.userId,
                nasPeerName = peer.username,
                stage = NearbyChatStage.InRoom,
                statusText = ""
            )
        }
        viewModelScope.launch {
            nasChatRepository.pullThread(peer).onFailure { error ->
                _uiState.update { it.copy(statusText = error.message ?: "同步失败") }
            }
        }
    }

    fun submitNasQuery() {
        val query = _uiState.value.nasQuery
        viewModelScope.launch {
            nasChatRepository.findContact(query)
                .onSuccess { openNasConversation(it) }
                .onFailure { error ->
                    _uiState.update { it.copy(statusText = error.message ?: "找不到这个账号") }
                }
        }
    }

    fun leaveNasConversation() {
        _uiState.update {
            it.copy(
                nasPeerId = "",
                nasPeerName = "",
                stage = NearbyChatStage.Idle,
                statusText = ""
            )
        }
    }

    private fun startNas() {
        _uiState.update {
            it.copy(
                stage = if (it.nasPeerId.isBlank()) NearbyChatStage.Idle else NearbyChatStage.InRoom,
                statusText = ""
            )
        }
        nasJob?.cancel()
        nasJob = viewModelScope.launch {
            refreshNasContacts()
            while (currentCoroutineContext().isActive) {
                val state = _uiState.value
                if (state.nasPeerId.isNotBlank()) {
                    val peer = MomentAccountRef(state.nasPeerId, state.nasPeerName.ifBlank { state.nasPeerId })
                    nasChatRepository.pullThread(peer).onFailure { error ->
                        _uiState.update { it.copy(statusText = error.message ?: "同步失败") }
                    }
                    delay(NAS_THREAD_POLL_MS)
                } else {
                    refreshNasContacts()
                    delay(NAS_LIST_POLL_MS)
                }
            }
        }
    }

    private suspend fun refreshNasContacts() {
        nasChatRepository.listContacts()
            .onSuccess { contacts ->
                _uiState.update { it.copy(nasContacts = contacts, statusText = "") }
            }
            .onFailure { error ->
                _uiState.update { it.copy(statusText = error.message ?: "无法读取账号列表") }
            }
    }

    private fun sendNasDraft() {
        val text = NearbyChatWire.sanitizeMessage(_draft.value) ?: return
        val state = _uiState.value
        if (!state.canSend) return
        val peer = MomentAccountRef(state.nasPeerId, state.nasPeerName.ifBlank { state.nasPeerId })
        _draft.value = ""
        viewModelScope.launch {
            nasChatRepository.sendText(peer, text).onFailure { error ->
                _uiState.update { it.copy(statusText = error.message ?: "发送失败") }
            }
        }
    }

    private fun startWifi() {
        if (!wifiDirectController.isSupported || eventsJob?.isActive == true) return
        eventsJob = viewModelScope.launch {
            wifiDirectController.events().collect(::onWifiDirectEvent)
        }
        startDiscovery()
    }

    private fun startBluetooth() {
        if (!bleMeshController.isSupported) return
        if (!bleMeshController.isEnabled) {
            _uiState.update { it.copy(statusText = "请先打开蓝牙") }
            return
        }
        openBluetoothSession()
    }

    fun startDiscovery() {
        if (!wifiDirectController.isSupported) return
        viewModelScope.launch {
            _uiState.update {
                if (it.stage == NearbyChatStage.Idle || it.stage == NearbyChatStage.Discovering) {
                    it.copy(stage = NearbyChatStage.Discovering, statusText = "正在搜索附近的设备…")
                } else {
                    it
                }
            }
            wifiDirectController.discoverPeers().onFailure { error ->
                // 搜索是后台动作，失败不该把已经进房的会话打回列表。
                _uiState.update {
                    if (it.stage == NearbyChatStage.Discovering) {
                        it.copy(
                            stage = NearbyChatStage.Idle,
                            statusText = error.message ?: "搜索失败，请重试"
                        )
                    } else {
                        it
                    }
                }
            }
        }
    }

    /** 本机建一个聊天室并当转发中心，其他设备搜到后直接加入，不用一台台互相邀请。 */
    fun hostRoom() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(stage = NearbyChatStage.Connecting, statusText = "正在创建聊天室…")
            }
            // 手机上同时只能有一个 Wi-Fi Direct 组，先清掉残留的再建。
            wifiDirectController.removeGroup()
            wifiDirectController.createGroup().onFailure { error ->
                _uiState.update {
                    it.copy(
                        stage = NearbyChatStage.Discovering,
                        statusText = error.message ?: "创建聊天室失败，请重试"
                    )
                }
            }
        }
    }

    fun joinRoom(peer: NearbyPeer) {
        if (peer.deviceAddress.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    stage = NearbyChatStage.Connecting,
                    statusText = "正在加入「${peer.deviceName}」，等待对方接受…"
                )
            }
            wifiDirectController.connect(peer.deviceAddress).onFailure { error ->
                _uiState.update {
                    it.copy(
                        stage = NearbyChatStage.Discovering,
                        statusText = error.message ?: "加入失败，请重试"
                    )
                }
            }
        }
    }

    fun cancelConnecting() {
        viewModelScope.launch {
            wifiDirectController.cancelConnect()
            releaseGroup()
            _uiState.update {
                it.copy(stage = NearbyChatStage.Discovering, statusText = "已取消")
            }
        }
    }

    fun onDraftChange(value: String) {
        _draft.value = value
    }

    fun sendDraft() {
        if (_uiState.value.transport == NearbyTransport.Nas) {
            sendNasDraft()
            return
        }
        val text = NearbyChatWire.sanitizeMessage(_draft.value) ?: return
        val currentNode = node ?: return
        if (_uiState.value.stage != NearbyChatStage.InRoom) return
        _draft.value = ""
        val frame = router.compose(
            messageId = UUID.randomUUID().toString(),
            body = text,
            displayName = displayName,
            atEpochMillis = clock.millis()
        )
        appendMessage(
            NearbyChatMessage(
                messageId = frame.messageId,
                senderId = frame.senderId,
                senderName = frame.senderName,
                text = frame.body,
                fromMe = true,
                sentAtEpochMillis = frame.sentAtEpochMillis
            )
        )
        viewModelScope.launch { currentNode.broadcast(frame) }
    }

    /** 把本机一条碎片发到聊天里。对方看到卡片，不会写进对方的碎片库。 */
    fun shareFragment(fragment: LifeFragment) {
        if (_uiState.value.transport == NearbyTransport.Nas) return
        val currentNode = node ?: return
        val transport = _uiState.value.transport
        if (_uiState.value.stage != NearbyChatStage.InRoom) return
        viewModelScope.launch {
            val card = fragment.toSharedFragmentCard()
            val localImage = fragment.imageUris.firstOrNull { it.isNotBlank() }.orEmpty()
            val jpeg = if (NearbyFragmentSharePolicy.includeImage(transport)) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        NearbyShareImageBytes.fromAny(localImage, appContext.contentResolver)
                    }
                }.getOrDefault(byteArrayOf())
            } else {
                byteArrayOf()
            }
            if (node !== currentNode) return@launch
            val frame = router.composeFragment(
                messageId = UUID.randomUUID().toString(),
                displayName = displayName,
                atEpochMillis = clock.millis(),
                card = card,
                jpeg = jpeg
            )
            appendMessage(
                NearbyChatMessage(
                    messageId = frame.messageId,
                    senderId = frame.senderId,
                    senderName = frame.senderName,
                    text = fragment.shareCaption(),
                    fromMe = true,
                    sentAtEpochMillis = frame.sentAtEpochMillis,
                    fragment = card,
                    imagePath = NearbyFragmentSharePolicy.localPreviewPath(
                        transport = transport,
                        localPath = localImage,
                        attachedJpeg = jpeg
                    )
                ),
                transport
            )
            if (node === currentNode && _uiState.value.stage == NearbyChatStage.InRoom) {
                currentNode.broadcast(frame)
            }
        }
    }

    /** 断开当前组网，聊天记录留在本地。 */
    fun leaveRoom() {
        viewModelScope.launch {
            closeSession(announceLeaving = true)
            releaseGroup()
            _uiState.update {
                it.copy(
                    stage = NearbyChatStage.Discovering,
                    members = emptyList(),
                    hostingRoom = false,
                    statusText = "已离开聊天室"
                )
            }
            startDiscovery()
        }
    }

    private fun onWifiDirectEvent(event: WifiDirectEvent) {
        when (event) {
            is WifiDirectEvent.StateChanged ->
                _uiState.update { it.copy(wifiDirectEnabled = event.enabled) }

            is WifiDirectEvent.PeersChanged ->
                _uiState.update { it.copy(peers = event.peers.sortedBy { peer -> peer.deviceName }) }

            is WifiDirectEvent.ThisDeviceChanged ->
                _uiState.update { it.copy(myDeviceName = event.deviceName) }

            is WifiDirectEvent.ConnectionChanged -> onGroupChanged(event.group)
        }
    }

    private fun onGroupChanged(group: WifiDirectGroup?) {
        if (group == null) {
            if (activeGroup == null) return
            activeGroup = null
            viewModelScope.launch {
                closeSession(announceLeaving = false)
                _uiState.update {
                    it.copy(stage = NearbyChatStage.Closed, members = emptyList(), statusText = "聊天室已断开")
                }
            }
            return
        }
        if (group == activeGroup && sessionJob?.isActive == true) return
        activeGroup = group
        openSession(group)
    }

    private fun openSession(group: WifiDirectGroup) {
        sessionJob?.cancel()
        val role = if (group.isGroupOwner) MeshRole.RoomHost else MeshRole.RoomMember
        _uiState.update {
            it.copy(
                stage = NearbyChatStage.Linking,
                hostingRoom = role == MeshRole.RoomHost,
                statusText = "已组网，正在接入聊天室…"
            )
        }
        sessionJob = viewModelScope.launch {
            val meshNode = NearbyMeshNode(connector)
            node = meshNode
            var nodeEvents: Job? = null
            try {
                if (displayName.isBlank()) {
                    displayName = resolveDisplayName()
                    _uiState.update { it.copy(myDisplayName = displayName) }
                }
                router = NearbyMeshRouter(selfNodeId = resolvedSelfNodeId())
                neighborNodes.clear()
                router.announceSelf(displayName, clock.millis())
                publishMembers()
                nodeEvents = launch { meshNode.events.collect { onNodeEvent(meshNode, it) } }

                // 组主的监听一起来就算进房了；成员则要等到与组主的链路接通。
                if (role == MeshRole.RoomHost) {
                    _uiState.update {
                        it.copy(
                            stage = NearbyChatStage.InRoom,
                            statusText = "等待加入"
                        )
                    }
                }
                meshNode.run(
                    role = role,
                    hostAddress = group.groupOwnerAddress,
                    port = NearbyChatWire.PORT,
                    connectTimeoutMillis = LINK_TIMEOUT_MILLIS
                )
                // 主动离开时是先取消再关链路，这里要让 run 的返回落到取消后面，
                // 否则会用「连接已断开」盖掉「已离开聊天室」。
                currentCoroutineContext().ensureActive()
                // 组成员这边 run 返回就代表和组主的链路断了。
                _uiState.update {
                    it.copy(stage = NearbyChatStage.Closed, statusText = "与聊天室的连接已断开")
                }
                releaseGroup()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val reason = error.message?.let { "接入聊天室失败：$it" } ?: "接入聊天室失败"
                // 一条消息都没聊成的话，退回设备列表比停在空聊天页更有用。
                _uiState.update {
                    if (it.messages.isEmpty()) {
                        it.copy(stage = NearbyChatStage.Discovering, statusText = reason)
                    } else {
                        it.copy(stage = NearbyChatStage.Closed, statusText = reason)
                    }
                }
                releaseGroup()
            } finally {
                nodeEvents?.cancel()
                meshNode.close()
                if (node === meshNode) node = null
            }
        }
    }

    private fun openBluetoothSession() {
        if (sessionJob?.isActive == true) return
        sessionJob?.cancel()
        _uiState.update {
            it.copy(
                stage = NearbyChatStage.InRoom,
                hostingRoom = false,
                statusText = "正在寻找附近的人…"
            )
        }
        sessionJob = viewModelScope.launch {
            val meshNode = NearbyMeshNode(connector)
            node = meshNode
            var nodeEvents: Job? = null
            try {
                if (displayName.isBlank()) {
                    displayName = resolveDisplayName()
                    _uiState.update { it.copy(myDisplayName = displayName) }
                }
                router = NearbyMeshRouter(selfNodeId = resolvedSelfNodeId())
                neighborNodes.clear()
                router.announceSelf(displayName, clock.millis())
                publishMembers()
                nodeEvents = launch { meshNode.events.collect { onNodeEvent(meshNode, it) } }
                meshNode.runBluetooth { onLink ->
                    bleMeshController.run(router.selfNodeId, onLink)
                }
                currentCoroutineContext().ensureActive()
                _uiState.update {
                    it.copy(stage = NearbyChatStage.Closed, statusText = "蓝牙组网已停止")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        stage = NearbyChatStage.Closed,
                        statusText = error.message?.let { reason -> "蓝牙组网未能开始：$reason" }
                            ?: "蓝牙组网未能开始"
                    )
                }
            } finally {
                nodeEvents?.cancel()
                meshNode.close()
                if (node === meshNode) node = null
            }
        }
    }

    private suspend fun onNodeEvent(meshNode: NearbyMeshNode, event: NearbyMeshNode.Event) {
        when (event) {
            is NearbyMeshNode.Event.NeighborJoined -> {
                router.greeting(displayName, clock.millis()).forEach { frame ->
                    meshNode.sendTo(event.neighborId, frame)
                }
                ownAvatarFrame()?.let { meshNode.sendTo(event.neighborId, it) }
                _uiState.update {
                    it.copy(
                        stage = NearbyChatStage.InRoom,
                        statusText = roomStatusText(it.hostingRoom, meshNode.neighborCount)
                    )
                }
            }

            is NearbyMeshNode.Event.NeighborLeft -> {
                val nodeId = neighborNodes.remove(event.neighborId)
                if (nodeId != null) {
                    router.onNeighborLost(nodeId, clock.millis())?.let { departure ->
                        meshNode.broadcast(departure)
                    }
                    publishMembers()
                }
                _uiState.update {
                    it.copy(statusText = roomStatusText(it.hostingRoom, meshNode.neighborCount))
                }
            }

            is NearbyMeshNode.Event.Received -> {
                val outcome = router.receive(event.frame)
                outcome.learnedNodeId?.let { neighborNodes[event.neighborId] = it }
                outcome.deliver?.let { message ->
                    appendMessage(
                        NearbyChatMessage(
                            messageId = message.messageId,
                            senderId = message.senderId,
                            senderName = router.displayNameOf(message.senderId)
                                ?: message.senderName,
                            text = message.body,
                            fromMe = false,
                            sentAtEpochMillis = message.sentAtEpochMillis
                        )
                    )
                }
                outcome.fragmentShare?.let { rememberSharedFragment(it) }
                outcome.forward.forEach { frame ->
                    meshNode.broadcast(frame, exceptNeighborId = event.neighborId)
                }
                if (outcome.rosterChanged) publishMembers()
                outcome.avatar?.let { rememberPeerAvatar(it) }
            }
        }
    }

    private fun roomStatusText(hosting: Boolean, neighborCount: Int): String {
        if (_uiState.value.isBluetooth) {
            return if (neighborCount > 0) "已连接 $neighborCount 台" else "正在寻找附近的人…"
        }
        return when {
            neighborCount > 0 -> "已接入"
            hosting -> "等待加入"
            else -> "已断开"
        }
    }

    private fun publishMembers() {
        val members = router.members().sortedBy { it.displayName }
        _uiState.update { it.copy(members = members) }
    }

    /** 释放 Wi-Fi Direct 组，并先清掉 [activeGroup]，让随之而来的断开广播不再重复处理。 */
    private suspend fun releaseGroup() {
        activeGroup = null
        wifiDirectController.removeGroup()
    }

    private fun appendMessage(
        message: NearbyChatMessage,
        transport: NearbyTransport = _uiState.value.transport
    ) {
        viewModelScope.launch {
            chatStore.save(message, transport)
        }
    }

    private fun ownAvatarFrame(): NearbyChatFrame.Avatar? {
        val path = _uiState.value.myAvatarPath
        if (path.isBlank()) return null
        val jpeg = NearbyAvatarThumbnail.fromFile(File(path)) ?: return null
        return NearbyChatFrame.Avatar(
            nodeId = resolvedSelfNodeId(),
            jpeg = jpeg,
            updatedAtEpochMillis = _uiState.value.myAvatarUpdatedAtEpochMs
        )
    }

    private suspend fun rememberSharedFragment(share: NearbyChatFrame.FragmentShare) {
        val imagePath = if (share.jpeg.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                runCatching { shareImageStore.save(share.messageId, share.jpeg).absolutePath }
                    .getOrDefault("")
            }
        } else {
            ""
        }
        appendMessage(
            NearbyChatMessage(
                messageId = share.messageId,
                senderId = share.senderId,
                senderName = router.displayNameOf(share.senderId) ?: share.senderName,
                text = share.card.content.ifBlank {
                    share.card.contextLine().ifBlank { "分享了一条碎片" }
                },
                fromMe = false,
                sentAtEpochMillis = share.sentAtEpochMillis,
                fragment = share.card,
                imagePath = imagePath
            )
        )
    }

    private fun rememberPeerAvatar(frame: NearbyChatFrame.Avatar) {
        val file = runCatching { peerAvatarStore.save(frame.nodeId, frame.jpeg) }.getOrNull() ?: return
        _uiState.update {
            it.copy(peerAvatarPaths = it.peerAvatarPaths + (frame.nodeId to file.absolutePath))
        }
    }

    private fun resolvedSelfNodeId(): String =
        selfNodeId.ifBlank { UUID.randomUUID().toString().also { selfNodeId = it } }

    private suspend fun closeSession(announceLeaving: Boolean) {
        val current = node
        if (announceLeaving && current != null && _uiState.value.stage == NearbyChatStage.InRoom) {
            val departure = MeshMember(
                nodeId = router.selfNodeId,
                displayName = displayName,
                present = false,
                updatedAtEpochMillis = clock.millis()
            )
            runCatching { current.broadcast(NearbyChatFrame.Presence(departure)) }
        }
        sessionJob?.cancel()
        sessionJob = null
        // 阻塞读只有关掉 socket 才会退出，取消协程本身拦不住它。
        current?.close()
        node = null
        neighborNodes.clear()
    }

    private suspend fun resolveDisplayName(): String {
        val preferences = userPreferencesRepository.preferences.first()
        return preferences.nasMomentAccountUsername
            .ifBlank { preferences.nasMomentStorageUserId }
            .ifBlank { _uiState.value.myDeviceName }
            .ifBlank { "Moment 用户" }
    }

    override fun onCleared() {
        sessionJob?.cancel()
        node?.close()
        node = null
        activeGroup = null
        nasJob?.cancel()
        wifiDirectController.releaseQuietly()
        super.onCleared()
    }

    private companion object {
        /** 组建立后到链路打开的最长等待；对方可能还在弹「接受邀请」。 */
        const val LINK_TIMEOUT_MILLIS = 25_000L
        const val SHAREABLE_LIMIT = 40
        const val NAS_THREAD_POLL_MS = 3_000L
        const val NAS_LIST_POLL_MS = 15_000L
    }
}
