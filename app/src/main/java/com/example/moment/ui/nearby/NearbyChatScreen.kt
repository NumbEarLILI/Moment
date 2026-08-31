package com.example.moment.ui.nearby

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.moment.domain.nearby.NearbyChatMessage
import com.example.moment.domain.nearby.NearbyChatStage
import com.example.moment.domain.nearby.NearbyPeer
import com.example.moment.domain.nearby.NearbyPermissions
import com.example.moment.domain.nearby.NearbyTransport
import com.example.moment.ui.theme.MomentHairline
import com.example.moment.ui.theme.appScaffoldContainerColor
import com.example.moment.ui.theme.momentTransparentTextFieldColors
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MessageTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun NearbyChatScreen(
    viewModel: NearbyChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val transport = state.transport

    val requiredPermissions = remember(transport) {
        if (transport == NearbyTransport.Bluetooth) {
            NearbyPermissions.bluetoothRequired(Build.VERSION.SDK_INT)
        } else {
            NearbyPermissions.wifiRequired(Build.VERSION.SDK_INT)
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

    LaunchedEffect(granted) {
        if (granted) {
            viewModel.start()
        } else if (!asked) {
            asked = true
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    val isBluetooth = transport == NearbyTransport.Bluetooth

    Scaffold(
        containerColor = appScaffoldContainerColor(),
        contentColor = MaterialTheme.colorScheme.onBackground,
        // 默认 safeDrawing 已含 IME；再套 imePadding 会空出一整块键盘高度。
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                "聊天",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TransportChip(
                    label = "蓝牙组网",
                    selected = isBluetooth,
                    onClick = { viewModel.switchTransport(NearbyTransport.Bluetooth) }
                )
                TransportChip(
                    label = "Wi-Fi 聊天室",
                    selected = !isBluetooth,
                    onClick = { viewModel.switchTransport(NearbyTransport.WifiDirect) }
                )
            }
            Text(
                if (isBluetooth) {
                    "打开就会自动寻找附近同样在聊天页的人。消息留在本机，退出再进还在。"
                } else {
                    "一台创建聊天室，其他人加入。记录留在本机。"
                },
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
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
                    Notice("请先打开蓝牙，打开后会自动寻找附近的人。")
                    TextButton(
                        onClick = {
                            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        },
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text("打开蓝牙")
                    }
                }

                !isBluetooth && !state.showsConversation -> DiscoveryBlock(
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

            ConversationBlock(
                state = state,
                draft = draft,
                onDraftChange = viewModel::onDraftChange,
                onSend = viewModel::sendDraft,
                onLeave = viewModel::leaveRoom,
                showLeave = !isBluetooth && state.showsConversation,
                modifier = Modifier.weight(1f)
            )
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
                "还没有搜到设备。让其中一台点「创建聊天室」，其余设备在这一页搜索后加入它。",
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
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onLeave: () -> Unit,
    showLeave: Boolean,
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
                    if (state.isBluetooth) {
                        "蓝牙组网（${state.members.size} 人）"
                    } else if (state.hostingRoom) {
                        "我的聊天室（${state.members.size} 人）"
                    } else {
                        "聊天室（${state.members.size} 人）"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    state.members.joinToString("、") { it.displayName }.ifBlank { "暂无其他成员" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showLeave) {
                TextButton(onClick = onLeave) {
                    Text(if (state.canSend) "离开" else "返回设备列表")
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
                Text(
                    if (state.canSend) "发条消息试试。" else "连上之后就可以聊。记录会留在这台手机上。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                        }
                    )
                }
            }
        }

        MomentHairline(Modifier.padding(top = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                enabled = state.canSend,
                placeholder = { Text(if (state.canSend) "说点什么…" else "已离开聊天室") },
                colors = momentTransparentTextFieldColors(),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            TextButton(onClick = onSend, enabled = state.canSend && draft.isNotBlank()) {
                Text("发送")
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: NearbyChatMessage,
    avatarPath: String,
    avatarUpdatedAtEpochMs: Long
) {
    val time = remember(message.sentAtEpochMillis) {
        MessageTimeFormatter.format(
            Instant.ofEpochMilli(message.sentAtEpochMillis).atZone(ZoneId.systemDefault())
        )
    }
    val name = if (message.fromMe) "我" else message.senderName
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
                    .background(
                        color = if (message.fromMe) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
