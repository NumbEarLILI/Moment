package com.example.moment.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moment.data.nearby.NearbyChatConnector
import com.example.moment.data.nearby.NearbyChatLink
import com.example.moment.data.nearby.WifiDirectController
import com.example.moment.data.nearby.WifiDirectEvent
import com.example.moment.data.nearby.WifiDirectGroup
import com.example.moment.data.preferences.UserPreferencesRepository
import com.example.moment.domain.nearby.NearbyChatFrame
import com.example.moment.domain.nearby.NearbyChatMessage
import com.example.moment.domain.nearby.NearbyChatStage
import com.example.moment.domain.nearby.NearbyChatWire
import com.example.moment.domain.nearby.NearbyPeer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val peerDisplayName: String = "",
    val isGroupOwner: Boolean = false,
    val messages: List<NearbyChatMessage> = emptyList(),
    val statusText: String = ""
) {
    /** 聊天记录区是否该占据整屏（连接中或刚断开时都还要看得到消息）。 */
    val showsConversation: Boolean
        get() = stage == NearbyChatStage.Connected || stage == NearbyChatStage.Closed

    val canSend: Boolean
        get() = stage == NearbyChatStage.Connected
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

    private var eventsJob: Job? = null
    private var sessionJob: Job? = null
    private var link: NearbyChatLink? = null
    private var activeGroup: WifiDirectGroup? = null

    /** 权限拿到后调用；重复调用无副作用。 */
    fun start() {
        if (!wifiDirectController.isSupported || eventsJob?.isActive == true) return
        eventsJob = viewModelScope.launch {
            wifiDirectController.events().collect(::onEvent)
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
                _uiState.update {
                    it.copy(
                        stage = NearbyChatStage.Idle,
                        statusText = error.message ?: "搜索失败，请重试"
                    )
                }
            }
        }
    }

    fun connectTo(peer: NearbyPeer) {
        if (peer.deviceAddress.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    stage = NearbyChatStage.Connecting,
                    peerDisplayName = peer.deviceName,
                    statusText = "已向「${peer.deviceName}」发出邀请，等待对方接受…"
                )
            }
            wifiDirectController.connect(peer.deviceAddress).onFailure { error ->
                _uiState.update {
                    it.copy(
                        stage = NearbyChatStage.Discovering,
                        statusText = error.message ?: "邀请发送失败，请重试"
                    )
                }
            }
        }
    }

    fun cancelConnecting() {
        viewModelScope.launch {
            wifiDirectController.cancelConnect()
            wifiDirectController.removeGroup()
            _uiState.update {
                it.copy(stage = NearbyChatStage.Discovering, statusText = "已取消邀请")
            }
        }
    }

    fun onDraftChange(value: String) {
        _draft.value = value
    }

    fun sendDraft() {
        val text = NearbyChatWire.sanitizeMessage(_draft.value) ?: return
        val currentLink = link ?: return
        if (_uiState.value.stage != NearbyChatStage.Connected) return
        _draft.value = ""
        val message = NearbyChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            senderName = "我",
            fromMe = true,
            sentAtEpochMillis = clock.millis()
        )
        appendMessage(message)
        viewModelScope.launch {
            runCatching {
                currentLink.send(
                    NearbyChatFrame.Text(
                        id = message.id,
                        body = message.text,
                        sentAtEpochMillis = message.sentAtEpochMillis
                    )
                )
            }.onFailure {
                _uiState.update { state -> state.copy(statusText = "消息发送失败，连接可能已断开") }
            }
        }
    }

    /** 结束当前会话并回到设备列表，聊天记录一并清空。 */
    fun leaveChat() {
        viewModelScope.launch {
            closeSession(sayBye = true)
            wifiDirectController.removeGroup()
            _uiState.update {
                it.copy(
                    stage = NearbyChatStage.Discovering,
                    messages = emptyList(),
                    peerDisplayName = "",
                    statusText = "已断开连接"
                )
            }
            startDiscovery()
        }
    }

    private fun onEvent(event: WifiDirectEvent) {
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
                closeSession(sayBye = false)
                _uiState.update {
                    val stage = if (it.messages.isEmpty()) {
                        NearbyChatStage.Discovering
                    } else {
                        NearbyChatStage.Closed
                    }
                    it.copy(stage = stage, statusText = "连接已断开")
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
        _uiState.update {
            it.copy(
                stage = NearbyChatStage.Handshaking,
                isGroupOwner = group.isGroupOwner,
                statusText = "已组网，正在打开消息通道…"
            )
        }
        sessionJob = viewModelScope.launch {
            var opened: NearbyChatLink? = null
            try {
                val newLink = if (group.isGroupOwner) {
                    connector.acceptAsGroupOwner(NearbyChatWire.PORT, LINK_TIMEOUT_MILLIS)
                } else {
                    connector.connectToGroupOwner(
                        hostAddress = group.groupOwnerAddress,
                        port = NearbyChatWire.PORT,
                        timeoutMillis = LINK_TIMEOUT_MILLIS
                    )
                }
                opened = newLink
                link = newLink
                _uiState.update {
                    it.copy(
                        stage = NearbyChatStage.Connected,
                        statusText = "已连接，消息只在两台设备之间直传，不经过任何服务器"
                    )
                }
                newLink.send(NearbyChatFrame.Hello(resolveDisplayName()))
                newLink.incoming().collect(::onFrame)
                // 流正常结束就代表对面把通道关了。
                _uiState.update {
                    it.copy(stage = NearbyChatStage.Closed, statusText = "对方已断开连接")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        stage = NearbyChatStage.Closed,
                        statusText = error.message?.let { reason -> "通道未能建立：$reason" }
                            ?: "通道未能建立"
                    )
                }
            } finally {
                opened?.close()
                if (link === opened) link = null
            }
        }
    }

    private fun onFrame(frame: NearbyChatFrame) {
        when (frame) {
            is NearbyChatFrame.Hello -> {
                val name = frame.displayName.trim()
                if (name.isNotBlank()) {
                    _uiState.update { it.copy(peerDisplayName = name) }
                }
            }

            is NearbyChatFrame.Text -> appendMessage(
                NearbyChatMessage(
                    id = frame.id,
                    text = frame.body,
                    senderName = _uiState.value.peerDisplayName.ifBlank { "对方" },
                    fromMe = false,
                    sentAtEpochMillis = frame.sentAtEpochMillis
                )
            )

            NearbyChatFrame.Bye ->
                _uiState.update { it.copy(statusText = "对方结束了这次聊天") }
        }
    }

    private fun appendMessage(message: NearbyChatMessage) {
        _uiState.update { state ->
            if (state.messages.any { it.id == message.id }) return@update state
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

    private suspend fun closeSession(sayBye: Boolean) {
        val current = link
        if (sayBye && current != null && _uiState.value.stage == NearbyChatStage.Connected) {
            runCatching { current.send(NearbyChatFrame.Bye) }
        }
        sessionJob?.cancel()
        sessionJob = null
        current?.close()
        link = null
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
        link?.close()
        link = null
        super.onCleared()
    }

    private companion object {
        /** 组建立后到通道打开的最长等待；对方可能还在弹「接受邀请」。 */
        const val LINK_TIMEOUT_MILLIS = 25_000L
        const val MAX_MESSAGES = 300
    }
}
