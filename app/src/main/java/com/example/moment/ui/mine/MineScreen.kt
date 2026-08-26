package com.example.moment.ui.mine

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.moment.domain.model.UserAppPreferences
import com.example.moment.ui.theme.MomentHairline
import com.example.moment.ui.theme.appScaffoldContainerColor
import java.io.File

@Composable
fun MineScreen(
    onOpenAccountSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MineViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showClearAvatarDialog by remember { mutableStateOf(false) }
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.setAvatarFromUri(uri)
        }
    }

    Scaffold(
        containerColor = appScaffoldContainerColor(),
        contentColor = MaterialTheme.colorScheme.onBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "我的",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            AccountInfoRow(
                preferences = preferences,
                onOpenAccount = onOpenAccountSettings,
                onChangeAvatar = { avatarPicker.launch(arrayOf("image/*")) },
                onClearAvatar = { showClearAvatarDialog = true }
            )
            MomentHairline()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSettings)
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "设置",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "›",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
        }
    }
    if (showClearAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showClearAvatarDialog = false },
            title = { Text("清除头像？") },
            text = { Text("清除后会恢复为首字头像。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAvatarDialog = false
                        viewModel.clearAvatar()
                    }
                ) {
                    Text("清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAvatarDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun AccountInfoRow(
    preferences: UserAppPreferences,
    onOpenAccount: () -> Unit,
    onChangeAvatar: () -> Unit,
    onClearAvatar: () -> Unit
) {
    val accountName = preferences.nasMomentAccountUsername
        .ifBlank { preferences.nasMomentStorageUserId }
        .ifBlank { "未登录" }
    val avatarText = accountName.take(1).ifBlank { "M" }
    val hasAvatar = preferences.avatarImagePath.isNotBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAccount)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            text = avatarText,
            imagePath = preferences.avatarImagePath,
            imageUpdatedAtEpochMs = preferences.avatarUpdatedAtEpochMs,
            onChange = onChangeAvatar,
            onClear = if (hasAvatar) onClearAvatar else null
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = accountName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (hasAvatar) "点击头像更换，长按清除" else "点击头像设置照片",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Avatar(
    text: String,
    imagePath: String,
    imageUpdatedAtEpochMs: Long,
    onChange: () -> Unit,
    onClear: (() -> Unit)?
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onChange,
                onLongClick = onClear
            )
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imagePath.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(imagePath))
                    .memoryCacheKey("user-avatar-$imageUpdatedAtEpochMs")
                    .diskCacheKey("user-avatar-$imageUpdatedAtEpochMs")
                    .crossfade(true)
                    .build(),
                contentDescription = "用户头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
