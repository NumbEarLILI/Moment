package com.example.moment.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.data.nearby.MeshRole
import com.example.moment.data.nearby.NearbyChatConnector
import com.example.moment.data.nearby.NearbyMeshNode
import com.example.moment.data.nearby.WifiDirectController
import com.example.moment.data.nearby.WifiDirectEvent
import com.example.moment.data.nearby.WifiDirectGroup
import com.example.moment.data.preferences.UserPreferencesRepository
import com.example.moment.domain.nearby.MeshMember
import com.example.moment.domain.nearby.NearbyChatFrame
import com.example.moment.domain.nearby.NearbyChatMessage
import com.example.moment.domain.nearby.NearbyChatStage
import com.example.moment.domain.nearby.NearbyChatWire
import com.example.moment.domain.nearby.NearbyMeshRouter
import com.example.moment.domain.nearby.NearbyPeer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NearbyChatUiState(
    val supported: Boolean = true,
    val wifiDirectEnabled: Boolean = true,
    val stage: NearbyChatStage = NearbyChatStage.Idle,
    val peers: List<NearbyPeer> = emptyList(),
    val myDeviceName: String = "",
    val myDisplayName: String = "",
    val hostingRoom: Boolean = false,
    val members: List<MeshMember> = emptyList(),
    val messages: List<NearbyChatMessage> = emptyList(),
    val statusText: String = ""
) {
    /** 聊天区是否该占据整屏（刚断开时也还要看得到消息）。 */
    val showsConversation: Boolean
        get() = stage == NearbyChatStage.InRoom || stage == NearbyChatStage.Closed

    val canSend: Boolean
        get() = stage == NearbyChatStage.InRoom
}

@HiltViewModel
class NearbyChatViewModel @Inject constructor(
    private val wifiDirectController: WifiDirectController,
    private val connector: NearbyChatConnector,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        NearbyChatUiState(supported = wifiDirectController.isSupported)
    )
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

    /** 权限拿到后调用；重复调用无副作用。 */
    fun start() {
        if (!wifiDirectController.isSupported || eventsJob?.isActive == true) return
        eventsJob = viewModelScope.launch {
            wifiDirectController.events().collect(::onWifiDirectEvent)
        }
        viewModelScope.launch {
            displayName = resolveDisplayName()
            _uiState.update { it.copy(myDisplayName = displayName) }
        }
        startDiscovery()
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

    /** 离开聊天室并回到设备列表，聊天记录一并清空。 */
    fun leaveRoom() {
        viewModelScope.launch {
            closeSession(announceLeaving = true)
            releaseGroup()
            _uiState.update {
                it.copy(
                    stage = NearbyChatStage.Discovering,
                    messages = emptyList(),
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
                    val stage = if (it.messages.isEmpty()) {
                        NearbyChatStage.Discovering
                    } else {
                        NearbyChatStage.Closed
                    }
                    it.copy(stage = stage, members = emptyList(), statusText = "聊天室已断开")
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
                // 每次进房都换一份路由状态，免得上一个聊天室的成员和消息 id 留下来。
                router = NearbyMeshRouter(selfNodeId = UUID.randomUUID().toString())
                neighborNodes.clear()
                router.announceSelf(displayName, clock.millis())
                publishMembers()
                nodeEvents = launch { meshNode.events.collect { onNodeEvent(meshNode, it) } }

                // 组主的监听一起来就算进房了；成员则要等到与组主的链路接通。
                if (role == MeshRole.RoomHost) {
                    _uiState.update {
                        it.copy(
                            stage = NearbyChatStage.InRoom,
                            statusText = "聊天室已就绪，等其他设备加入"
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

    private suspend fun onNodeEvent(meshNode: NearbyMeshNode, event: NearbyMeshNode.Event) {
        when (event) {
            is NearbyMeshNode.Event.NeighborJoined -> {
                router.greeting(displayName, clock.millis()).forEach { frame ->
                    meshNode.sendTo(event.neighborId, frame)
                }
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
                outcome.forward.forEach { frame ->
                    meshNode.broadcast(frame, exceptNeighborId = event.neighborId)
                }
                if (outcome.rosterChanged) publishMembers()
            }
        }
    }

    private fun roomStatusText(hosting: Boolean, neighborCount: Int): String = when {
        neighborCount > 0 -> "已接入聊天室，消息只在这些设备之间直传，不经过任何服务器"
        hosting -> "聊天室已就绪，等其他设备加入"
        else -> "与聊天室的连接已断开"
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

    private fun appendMessage(message: NearbyChatMessage) {
        _uiState.update { state ->
            if (state.messages.any { it.messageId == message.messageId }) return@update state
            val merged = state.messages + message
            state.copy(
                messages = if (merged.size > MAX_MESSAGES) {
                    merged.takeLast(MAX_MESSAGES)
                } else {
                    merged
                }
            )
        }
    }

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
        wifiDirectController.releaseQuietly()
        super.onCleared()
    }

    private companion object {
        /** 组建立后到链路打开的最长等待；对方可能还在弹「接受邀请」。 */
        const val LINK_TIMEOUT_MILLIS = 25_000L
        const val MAX_MESSAGES = 300
    }
}
