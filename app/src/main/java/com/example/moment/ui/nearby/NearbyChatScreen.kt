package com.example.moment.ui.nearby

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.moment.data.nearby.NearbyShareImageMime
import com.example.moment.data.nearby.NearbyShareImageSaver
import com.example.moment.domain.model.LifeFragment
import com.example.moment.domain.nearby.NearbyChatMessage
import com.example.moment.domain.nearby.NearbyChatStage
import com.example.moment.domain.nearby.NearbyPeer
import com.example.moment.domain.nearby.NearbyPermissions
import com.example.moment.domain.naschat.MomentAccountRef
import com.example.moment.domain.nearby.NearbyTransport
import com.example.moment.domain.nearby.SharedFragmentCard
import com.example.moment.ui.common.FullscreenImageViewer
import com.example.moment.ui.common.MoodSummaryBadge
import com.example.moment.ui.common.TagLine
import com.example.moment.ui.theme.MomentHairline
import com.example.moment.ui.theme.appScaffoldContainerColor
import com.example.moment.ui.theme.positionAwareImePadding
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MessageTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val FragmentShareTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

@Composable
fun NearbyChatScreen(
    viewModel: NearbyChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val transport = state.transport

    val requiredPermissions = remember(transport) {
        when (transport) {
            NearbyTransport.Bluetooth -> NearbyPermissions.bluetoothRequired(Build.VERSION.SDK_INT)
            NearbyTransport.WifiDirect -> NearbyPermissions.wifiRequired(Build.VERSION.SDK_INT)
            NearbyTransport.Nas -> emptyList()
        }
    }
    fun permissionsGranted(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember(transport) { mutableStateOf(permissionsGranted()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = permissionsGranted() }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.onBluetoothEnabled() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = permissionsGranted()
        if (transport == NearbyTransport.Bluetooth) viewModel.onBluetoothEnabled()
    }

    LaunchedEffect(transport) {
        granted = permissionsGranted()
    }

    LaunchedEffect(granted, transport) {
        if (granted) {
            viewModel.start(transport)
        } else if (!asked && requiredPermissions.isNotEmpty()) {
            asked = true
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    val isBluetooth = transport == NearbyTransport.Bluetooth
    val isNas = transport == NearbyTransport.Nas
    val scope = rememberCoroutineScope()
    var showFragmentPicker by remember { mutableStateOf(false) }
    var detailMessage by remember { mutableStateOf<NearbyChatMessage?>(null) }
    var fullscreenImage by remember { mutableStateOf<String?>(null) }
    var pendingSavePath by remember { mutableStateOf<String?>(null) }
    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val path = pendingSavePath
        pendingSavePath = null
        when {
            path.isNullOrBlank() -> Unit
            granted -> saveNearbyShareImage(context, path, scope)
            else -> Toast.makeText(context, "没有保存权限", Toast.LENGTH_SHORT).show()
        }
    }
    fun requestSaveImage(path: String) {
        if (path.isBlank()) return
        if (NearbyShareImageMime.needsLegacyWritePermission(Build.VERSION.SDK_INT)) {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                saveNearbyShareImage(context, path, scope)
            } else {
                pendingSavePath = path
                writePermissionLauncher.launch(permission)
            }
        } else {
            saveNearbyShareImage(context, path, scope)
        }
    }

    Scaffold(
        containerColor = appScaffoldContainerColor(),
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp)
            ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TransportChip(
                    label = "蓝牙组网",
                    selected = isBluetooth,
                    onClick = { viewModel.switchTransport(NearbyTransport.Bluetooth) }
                )
                TransportChip(
                    label = "Wi-Fi 聊天室",
                    selected = transport == NearbyTransport.WifiDirect,
                    onClick = { viewModel.switchTransport(NearbyTransport.WifiDirect) }
                )
                TransportChip(
                    label = "NAS 聊天",
                    selected = isNas,
                    onClick = { viewModel.switchTransport(NearbyTransport.Nas) }
                )
            }

            when {
                isNas -> NasGateBlock(state = state)

                !state.supported -> Notice(
                    if (isBluetooth) "这台设备不支持蓝牙低功耗，无法组网。"
                    else "这台设备不支持 Wi-Fi 直连，无法使用聊天室。"
                )

                !granted -> PermissionBlock(
                    rationale = if (isBluetooth) {
                        NearbyPermissions.bluetoothRationale(Build.VERSION.SDK_INT)
                    } else {
                        NearbyPermissions.wifiRationale(Build.VERSION.SDK_INT)
                    },
                    onRequest = { permissionLauncher.launch(requiredPermissions.toTypedArray()) },
                    onOpenAppSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    }
                )

                isBluetooth && !state.bluetoothEnabled -> {
                    Notice("请先打开蓝牙")
                    TextButton(
                        onClick = {
                            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        },
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text("打开蓝牙")
                    }
                }

                !isBluetooth && !isNas && !state.showsConversation -> DiscoveryBlock(
                    state = state,
                    onHostRoom = viewModel::hostRoom,
                    onDiscover = viewModel::startDiscovery,
                    onJoin = viewModel::joinRoom,
                    onCancelConnecting = viewModel::cancelConnecting,
                    onOpenWifiSettings = {
                        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isNas && !state.showsConversation) {
                if (state.nasWebdavReady && state.nasLoggedIn) {
                    NasDirectoryBlock(
                        state = state,
                        onQueryChange = viewModel::onNasQueryChange,
                        onSubmitQuery = viewModel::submitNasQuery,
                        onOpenContact = viewModel::openNasConversation,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
            ConversationBlock(
                state = state,
                onLeave = if (isNas) viewModel::leaveNasConversation else viewModel::leaveRoom,
                showLeave = state.showsConversation && (!isBluetooth),
                onOpenFragment = { detailMessage = it },
                onOpenFragmentImage = { fullscreenImage = it },
                modifier = Modifier.weight(1f)
            )
            }
            }
            ChatComposer(
                draft = draft,
                canSend = state.canSend,
                showShareFragment = !isNas,
                idleHint = if (isNas) "选择一个对话" else "已离开聊天室",
                onDraftChange = viewModel::onDraftChange,
                onSend = viewModel::sendDraft,
                onShareFragment = { showFragmentPicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .positionAwareImePadding()
                    .padding(horizontal = 20.dp)
            )
        }
    }

    if (showFragmentPicker) {
        FragmentSharePickerSheet(
            fragments = state.shareableFragments,
            onPick = { fragment ->
                showFragmentPicker = false
                viewModel.shareFragment(fragment)
            },
            onDismiss = { showFragmentPicker = false }
        )
    }

    detailMessage?.let { message ->
        val card = message.fragment ?: return@let
        SharedFragmentDetailSheet(
            card = card,
            imagePath = message.imagePath,
            onDismiss = { detailMessage = null },
            onOpenImage = { fullscreenImage = it },
            onSaveImage = { requestSaveImage(it) }
        )
    }
    fullscreenImage?.let { path ->
        FullscreenImageViewer(
            imageUris = listOf(path),
            initialPage = 0,
            onDismiss = { fullscreenImage = null },
            onSave = { requestSaveImage(it) }
        )
    }
}

@Composable
private fun NasGateBlock(state: NearbyChatUiState) {
    when {
        !state.nasWebdavReady -> Notice("请先在「我的 → 账号与 NAS」填写 WebDAV 地址。")
        !state.nasLoggedIn -> Notice("请先登录 Moment 账号，才能在 NAS 上和别人一对一聊天。")
        else -> Unit
    }
}

@Composable
private fun NasDirectoryBlock(
    state: NearbyChatUiState,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: () -> Unit,
    onOpenContact: (MomentAccountRef) -> Unit,
    modifier: Modifier = Modifier
) {
    val query = state.nasQuery.trim()
    val filteredContacts = remember(state.nasContacts, state.nasQuery) {
        val q = state.nasQuery.trim()
        if (q.isEmpty()) state.nasContacts
        else state.nasContacts.filter {
            it.username.contains(q, ignoreCase = true) || it.userId.contains(q, ignoreCase = true)
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.statusText.isNotBlank()) {
            Text(
                state.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = state.nasQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmitQuery() }),
                decorationBox = { inner ->
                    Box {
                        if (state.nasQuery.isEmpty()) {
                            Text(
                                "搜索或输入用户名",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                }
            )
            TextButton(onClick = onSubmitQuery, enabled = query.isNotBlank()) {
                Text("开始聊天")
            }
        }
        MomentHairline()
        if (state.nasThreads.isNotEmpty()) {
            Text(
                "最近",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
            ) {
                items(state.nasThreads, key = { it.peerId }) { thread ->
                    val name = thread.peerName.ifBlank {
                        state.nasContacts.find { it.userId == thread.peerId }?.username.orEmpty()
                            .ifBlank { thread.peerId }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenContact(
                                    MomentAccountRef(
                                        userId = thread.peerId,
                                        username = name
                                    )
                                )
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                thread.lastText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    MomentHairline()
                }
            }
        }
        Text(
            "账号",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (filteredContacts.isEmpty()) {
            Text(
                if (state.nasContacts.isEmpty()) "还没有其他 Moment 账号" else "没有匹配的人",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(filteredContacts, key = { it.userId }) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenContact(contact) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            contact.username,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    MomentHairline()
                }
            }
        }
    }
}

@Composable
private fun TransportChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = Modifier.padding(0.dp)) {
        Text(
            label,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun PermissionBlock(
    rationale: String,
    onRequest: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Notice(rationale)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRequest, modifier = Modifier.padding(0.dp)) {
                Text("授予权限")
            }
            // 多次拒绝后系统弹窗不再出现，只能去应用详情页打开。
            TextButton(onClick = onOpenAppSettings, modifier = Modifier.padding(0.dp)) {
                Text("去系统设置")
            }
        }
    }
}

@Composable
private fun DiscoveryBlock(
    state: NearbyChatUiState,
    onHostRoom: () -> Unit,
    onDiscover: () -> Unit,
    onJoin: (NearbyPeer) -> Unit,
    onCancelConnecting: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val busy = state.stage == NearbyChatStage.Connecting

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.myDeviceName.isNotBlank()) {
            Text(
                "本机名称：${state.myDeviceName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.statusText.isNotBlank()) {
            Text(
                state.statusText,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (!state.wifiDirectEnabled) {
            Notice("Wi-Fi 直连尚未开启，请先打开手机的 Wi-Fi。")
            TextButton(onClick = onOpenWifiSettings, modifier = Modifier.padding(0.dp)) {
                Text("打开 Wi-Fi 设置")
            }
        }

        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (busy) {
                TextButton(onClick = onCancelConnecting, modifier = Modifier.padding(0.dp)) {
                    Text("取消")
                }
            } else {
                TextButton(onClick = onHostRoom, modifier = Modifier.padding(0.dp)) {
                    Text("创建聊天室")
                }
                TextButton(onClick = onDiscover, modifier = Modifier.padding(0.dp)) {
                    Text(if (state.stage == NearbyChatStage.Discovering) "重新搜索" else "搜索附近设备")
                }
            }
        }
        MomentHairline(Modifier.padding(vertical = 8.dp))

        if (state.peers.isEmpty()) {
            Text(
                "还没有搜到设备",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.peers, key = { it.deviceAddress }) { peer ->
                    PeerRow(peer = peer, enabled = !busy, onClick = { onJoin(peer) })
                    MomentHairline()
                }
            }
        }
    }
}

@Composable
private fun PeerRow(
    peer: NearbyPeer,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && peer.connectable, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                peer.deviceName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                if (peer.hostsRoom) "已开聊天室 · ${peer.statusText}" else peer.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (peer.connectable) {
            Text(
                if (peer.hostsRoom) "加入" else "邀请",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ConversationBlock(
    state: NearbyChatUiState,
    onLeave: () -> Unit,
    showLeave: Boolean,
    onOpenFragment: (NearbyChatMessage) -> Unit,
    onOpenFragmentImage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    when {
                        state.isNas -> state.nasPeerName.ifBlank { "NAS 聊天" }
                        state.isBluetooth -> "蓝牙组网（${state.members.size} 人）"
                        state.hostingRoom -> "我的聊天室（${state.members.size} 人）"
                        else -> "聊天室（${state.members.size} 人）"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    state.members.joinToString("、") { it.displayName }.ifBlank {
                        if (state.isNas) "文字会同步到家庭 NAS" else "暂无其他成员"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showLeave) {
                TextButton(onClick = onLeave) {
                    Text(
                        when {
                            state.isNas -> "返回"
                            state.canSend -> "离开"
                            else -> "返回设备列表"
                        }
                    )
                }
            }
        }
        if (state.statusText.isNotBlank()) {
            Text(
                state.statusText,
                modifier = Modifier.padding(bottom = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        MomentHairline(Modifier.padding(bottom = 8.dp))

        if (state.messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (state.canSend) {
                    Text(
                        "发条消息试试。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.messages, key = { it.messageId }) { message ->
                    MessageBubble(
                        message = message,
                        avatarPath = if (message.fromMe) {
                            state.myAvatarPath
                        } else {
                            state.peerAvatarPaths[message.senderId].orEmpty()
                        },
                        avatarUpdatedAtEpochMs = if (message.fromMe) {
                            state.myAvatarUpdatedAtEpochMs
                        } else {
                            0L
                        },
                        onOpenFragment = { onOpenFragment(message) },
                        onOpenFragmentImage = onOpenFragmentImage
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatComposer(
    draft: String,
    canSend: Boolean,
    showShareFragment: Boolean = true,
    idleHint: String = "已离开聊天室",
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onShareFragment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val placeholder = if (canSend) "说点什么…" else idleHint
    val textColor = MaterialTheme.colorScheme.onSurface
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier) {
        MomentHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                enabled = canSend,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = hintColor
                            )
                        }
                        inner()
                    }
                }
            )
            if (showShareFragment) {
                TextButton(onClick = onShareFragment, enabled = canSend) {
                    Text("碎片")
                }
            }
            TextButton(onClick = onSend, enabled = canSend && draft.isNotBlank()) {
                Text("发送")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: NearbyChatMessage,
    avatarPath: String,
    avatarUpdatedAtEpochMs: Long,
    onOpenFragment: () -> Unit,
    onOpenFragmentImage: (String) -> Unit
) {
    val context = LocalContext.current
    val time = remember(message.sentAtEpochMillis) {
        MessageTimeFormatter.format(
            Instant.ofEpochMilli(message.sentAtEpochMillis).atZone(ZoneId.systemDefault())
        )
    }
    val name = if (message.fromMe) "我" else message.senderName
    val copyText = remember(message) { message.copyableText() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!message.fromMe) {
            ChatAvatar(
                name = name,
                imagePath = avatarPath,
                imageUpdatedAtEpochMs = avatarUpdatedAtEpochMs
            )
        }
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .widthIn(max = 240.dp),
            horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start
        ) {
            Text(
                "$name · $time",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        color = if (message.fromMe) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        }
                    )
                    .combinedClickable(
                        onClick = {
                            if (message.fragment != null) onOpenFragment()
                        },
                        onLongClick = {
                            if (copyText.isNotBlank()) copyNearbyChatText(context, copyText)
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                val card = message.fragment
                if (card != null) {
                    SharedFragmentBubble(
                        card = card,
                        imagePath = message.imagePath,
                        onOpenImage = {
                            if (message.imagePath.isNotBlank()) {
                                onOpenFragmentImage(message.imagePath)
                            }
                        }
                    )
                } else {
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        if (message.fromMe) {
            ChatAvatar(
                name = name,
                imagePath = avatarPath,
                imageUpdatedAtEpochMs = avatarUpdatedAtEpochMs
            )
        }
    }
}

@Composable
private fun SharedFragmentBubble(
    card: SharedFragmentCard,
    imagePath: String,
    onOpenImage: () -> Unit
) {
    val created = remember(card.createdAtEpochMillis) {
        FragmentShareTimeFormatter.format(
            Instant.ofEpochMilli(card.createdAtEpochMillis).atZone(ZoneId.systemDefault())
        )
    }
    val contextLine = card.contextLine()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "碎片",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        if (imagePath.isNotBlank()) {
            nearbyChatImageModel(imagePath)?.let { model ->
                AsyncImage(
                    model = model,
                    contentDescription = "碎片图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenImage),
                    contentScale = ContentScale.Crop
                )
            }
        }
        if (card.content.isNotBlank()) {
            Text(
                card.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (contextLine.isNotBlank()) {
            Text(
                contextLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            created,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedFragmentDetailSheet(
    card: SharedFragmentCard,
    imagePath: String,
    onDismiss: () -> Unit,
    onOpenImage: (String) -> Unit,
    onSaveImage: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val created = remember(card.createdAtEpochMillis) {
        FragmentShareTimeFormatter.format(
            Instant.ofEpochMilli(card.createdAtEpochMillis).atZone(ZoneId.systemDefault())
        )
    }
    val placeWeather = remember(card.place, card.weather) {
        listOfNotNull(
            card.weather.takeIf { it.isNotBlank() },
            card.place.takeIf { it.isNotBlank() }
        ).joinToString("  ·  ")
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "碎片",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (imagePath.isNotBlank()) {
                nearbyChatImageModel(imagePath)?.let { model ->
                    AsyncImage(
                        model = model,
                        contentDescription = "碎片图片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenImage(imagePath) },
                        contentScale = ContentScale.Fit
                    )
                }
                TextButton(
                    onClick = { onSaveImage(imagePath) },
                    modifier = Modifier.padding(0.dp)
                ) {
                    Text("保存图片")
                }
            }
            if (card.content.isNotBlank()) {
                Text(
                    card.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (card.mood.isNotBlank()) {
                MoodSummaryBadge(summary = card.mood)
            }
            TagLine(tags = card.tags)
            if (placeWeather.isNotBlank()) {
                Text(
                    placeWeather,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                created,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FragmentSharePickerSheet(
    fragments: List<LifeFragment>,
    onPick: (LifeFragment) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "分享一条碎片",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (fragments.isEmpty()) {
                Text(
                    "还没有碎片可分享",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(fragments, key = { it.stableId.ifBlank { it.id.toString() } }) { fragment ->
                        FragmentSharePickerRow(
                            fragment = fragment,
                            onClick = { onPick(fragment) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FragmentSharePickerRow(
    fragment: LifeFragment,
    onClick: () -> Unit
) {
    val preview = remember(fragment.content, fragment.imageUris) {
        fragment.content.trim().ifBlank {
            if (fragment.imageUris.any { it.isNotBlank() }) "一张照片" else "一条碎片"
        }
    }
    val time = remember(fragment.createdAt) {
        FragmentShareTimeFormatter.format(fragment.createdAt.atZone(ZoneId.systemDefault()))
    }
    val thumb = fragment.imageUris.firstOrNull { it.isNotBlank() }.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (thumb.isNotBlank()) {
            nearbyChatImageModel(thumb)?.let { model ->
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChatAvatar(
    name: String,
    imagePath: String,
    imageUpdatedAtEpochMs: Long
) {
    val context = LocalContext.current
    val letter = name.trim().take(1).ifBlank { "M" }
    val file = imagePath.takeIf { it.isNotBlank() }?.let(::File)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        if (file?.isFile == true) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .memoryCacheKey("chat-avatar-$imagePath-$imageUpdatedAtEpochMs")
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                letter,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun copyNearbyChatText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("chat", text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }
}

private fun saveNearbyShareImage(context: Context, path: String, scope: CoroutineScope) {
    scope.launch {
        val result = withContext(Dispatchers.IO) {
            NearbyShareImageSaver.save(context.applicationContext, path)
        }
        Toast.makeText(
            context,
            if (result.isSuccess) "已保存到相册" else "保存失败",
            Toast.LENGTH_SHORT
        ).show()
    }
}
