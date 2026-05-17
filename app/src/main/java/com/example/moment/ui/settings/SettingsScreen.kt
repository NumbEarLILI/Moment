package com.example.moment.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moment.domain.model.AppThemeMode
import com.example.moment.domain.model.NasArchiveConflictChoice
import com.example.moment.ui.theme.appScaffoldContainerColor
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val aiBaseUrl by viewModel.aiBaseUrl.collectAsStateWithLifecycle()
    val aiApiKey by viewModel.aiApiKey.collectAsStateWithLifecycle()
    val aiModel by viewModel.aiModel.collectAsStateWithLifecycle()
    val nasBaseUrl by viewModel.nasBaseUrl.collectAsStateWithLifecycle()
    val nasUsername by viewModel.nasUsername.collectAsStateWithLifecycle()
    val nasPassword by viewModel.nasPassword.collectAsStateWithLifecycle()
    val nasTrustSelfSigned by viewModel.nasTrustSelfSigned.collectAsStateWithLifecycle()
    val nasBusy by viewModel.nasBusy.collectAsStateWithLifecycle()
    val nasStatusMessage by viewModel.nasStatusMessage.collectAsStateWithLifecycle()
    val nasMomentAccountUsernameDraft by viewModel.nasMomentAccountUsernameDraft.collectAsStateWithLifecycle()
    val nasMomentAccountPasswordDraft by viewModel.nasMomentAccountPasswordDraft.collectAsStateWithLifecycle()
    val nasBackupRunIds by viewModel.nasBackupRunIds.collectAsStateWithLifecycle()
    val selectedNasRunId by viewModel.selectedNasRunId.collectAsStateWithLifecycle()
    val nasArchiveConflictInfo by viewModel.nasArchiveConflictInfo.collectAsStateWithLifecycle()
    var showDeleteNasConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.reloadDraftFieldsFromStore()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.saveSuccessMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = appScaffoldContainerColor()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onBack, modifier = Modifier.padding(0.dp)) {
                Text("返回")
            }
            Text(
                "设置",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                "主题",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeChip(
                    label = "暗黑",
                    selected = prefs.themeMode == AppThemeMode.DARK,
                    onClick = { viewModel.selectTheme(AppThemeMode.DARK) }
                )
                ThemeChip(
                    label = "白",
                    selected = prefs.themeMode == AppThemeMode.LIGHT,
                    onClick = { viewModel.selectTheme(AppThemeMode.LIGHT) }
                )
                ThemeChip(
                    label = "冷色",
                    selected = prefs.themeMode == AppThemeMode.COOL,
                    onClick = { viewModel.selectTheme(AppThemeMode.COOL) }
                )
                ThemeChip(
                    label = "跟随系统",
                    selected = prefs.themeMode == AppThemeMode.SYSTEM,
                    onClick = { viewModel.selectTheme(AppThemeMode.SYSTEM) }
                )
            }

            Text(
                "自定义背景图",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            val context = LocalContext.current
            val wallpaperPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    viewModel.setCustomBackgroundImageUri(uri.toString())
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { wallpaperPicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("选择背景图")
                }
                OutlinedButton(
                    onClick = { viewModel.clearCustomBackgroundImage() },
                    enabled = prefs.customBackgroundImageUri.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清除")
                }
            }
            if (prefs.customBackgroundImageUri.isNotBlank()) {
                Text(
                    "已启用自定义背景",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                "大模型",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "OpenAI 兼容接口（如 DeepSeek）。留空则用手写规则生成手帐。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = aiBaseUrl,
                onValueChange = viewModel::setAiBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API 根地址") },
                placeholder = { Text("例如 https://api.deepseek.com") },
                singleLine = true
            )
            OutlinedTextField(
                value = aiApiKey,
                onValueChange = viewModel::setAiApiKey,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            OutlinedTextField(
                value = aiModel,
                onValueChange = viewModel::setAiModel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模型名称") },
                placeholder = { Text("例如 deepseek-v4-flash") },
                singleLine = true
            )
            Button(
                onClick = { viewModel.saveAiSettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存大模型配置")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                "家庭 NAS（WebDAV）",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "备份与手帐存档走 WebDAV。登录 Moment 后数据在 MomentApp/users/… 下，与未登录路径互相独立。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = nasBaseUrl,
                onValueChange = viewModel::setNasBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("WebDAV 根地址") },
                placeholder = { Text("例如 https://192.168.1.10:5006/backup") },
                singleLine = true
            )
            OutlinedTextField(
                value = nasUsername,
                onValueChange = viewModel::setNasUsername,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("用户名（可留空）") },
                singleLine = true
            )
            OutlinedTextField(
                value = nasPassword,
                onValueChange = viewModel::setNasPassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密码（可留空）") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "信任自签名证书",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = nasTrustSelfSigned,
                    onCheckedChange = viewModel::setNasTrustSelfSigned
                )
            }
            Text(
                "仅在 NAS 用自签名 HTTPS 时需要；会降低安全性。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { viewModel.saveNasWebdavSettings() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !nasBusy
            ) {
                Text("保存 NAS 配置")
            }
            Text(
                "Moment 账号（可选）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "多人共用同一 WebDAV 时可注册/登录；备份与存档只读当前账号目录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (prefs.nasMomentStorageUserId.isNotBlank()) {
                Text(
                    "当前已登录：${prefs.nasMomentAccountUsername.ifBlank { prefs.nasMomentStorageUserId }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    "未登录：读写根目录下 MomentBackup / MomentArchive。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = nasMomentAccountUsernameDraft,
                onValueChange = viewModel::setNasMomentAccountUsernameDraft,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Moment 账户名") },
                placeholder = { Text("字母数字中文等，最多 32 字符") },
                singleLine = true,
                enabled = !nasBusy
            )
            OutlinedTextField(
                value = nasMomentAccountPasswordDraft,
                onValueChange = viewModel::setNasMomentAccountPasswordDraft,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Moment 密码") },
                placeholder = { Text("至少 8 位") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !nasBusy
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.registerNasMomentAccount() },
                    modifier = Modifier.weight(1f),
                    enabled = !nasBusy
                ) {
                    Text("注册并登录")
                }
                Button(
                    onClick = { viewModel.loginNasMomentAccount() },
                    modifier = Modifier.weight(1f),
                    enabled = !nasBusy
                ) {
                    Text("登录")
                }
            }
            OutlinedButton(
                onClick = { viewModel.logoutNasMomentAccount() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !nasBusy && prefs.nasMomentStorageUserId.isNotBlank()
            ) {
                Text("退出 Moment 账号")
            }
            Text(
                "手帐存档（双向同步）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "与快照备份无关：按日同步 MomentArchive，开关联动拉取，首页下拉可再同步；冲突时询问保留本机或 NAS。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "保存后自动同步到 NAS 存档",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = prefs.nasArchiveSyncEnabled,
                    onCheckedChange = viewModel::setNasArchiveSyncEnabled,
                    enabled = !nasBusy
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "上传原图到 NAS",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = prefs.uploadOriginalImagesToNas,
                    onCheckedChange = viewModel::setUploadOriginalImagesToNas,
                    enabled = !nasBusy
                )
            }
            Text(
                "默认关闭：备份/存档会先压缩图片再上传，减轻手帐同步的网络压力；开启后保留原图。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.testNasWebdavConnection() },
                    modifier = Modifier.weight(1f),
                    enabled = !nasBusy
                ) {
                    Text("测试连接")
                }
                Button(
                    onClick = { viewModel.backupDiariesToNas() },
                    modifier = Modifier.weight(1f),
                    enabled = !nasBusy
                ) {
                    Text("备份日记")
                }
            }
            Text(
                "从 NAS 读回备份",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "从 MomentBackup/runs/ 选一次备份合并到本机（同日覆盖本地该日手帐）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.refreshNasBackupRuns() },
                    modifier = Modifier.weight(1f),
                    enabled = !nasBusy
                ) {
                    Text("刷新备份列表")
                }
                OutlinedButton(
                    onClick = { viewModel.restoreNasLatestBackup() },
                    modifier = Modifier.weight(1f),
                    enabled = !nasBusy
                ) {
                    Text("同步最新")
                }
            }
            if (nasBackupRunIds.isNotEmpty()) {
                Text(
                    "选择备份",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (runId in nasBackupRunIds) {
                        key(runId) {
                            val label = runId.removePrefix("run_").let { t ->
                                if (t.length > 14) "…${t.takeLast(12)}" else t
                            }
                            FilterChip(
                                selected = runId == selectedNasRunId,
                                onClick = { viewModel.selectNasBackupRun(runId) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
            Button(
                onClick = { viewModel.restoreNasSelectedBackup() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !nasBusy && selectedNasRunId != null
            ) {
                Text("同步选中备份到本机")
            }
            OutlinedButton(
                onClick = { showDeleteNasConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !nasBusy && selectedNasRunId != null
            ) {
                Text(
                    "删除选中备份（NAS）",
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (showDeleteNasConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteNasConfirm = false },
                    title = { Text("删除 NAS 备份") },
                    text = {
                        Text(
                            "将永久删除远端目录 MomentBackup/runs/${selectedNasRunId.orEmpty()}/" +
                                "及其全部内容，且无法撤销。确定删除？"
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteNasConfirm = false
                                viewModel.deleteNasSelectedBackup()
                            }
                        ) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteNasConfirm = false }) {
                            Text("取消")
                        }
                    }
                )
            }
            nasArchiveConflictInfo?.let { conflict ->
                AlertDialog(
                    onDismissRequest = {
                        viewModel.resolveNasArchiveConflict(NasArchiveConflictChoice.KEEP_LOCAL)
                    },
                    title = { Text("NAS 存档冲突") },
                    text = {
                        Text(
                            "「${conflict.date}」手帐：本机修改时间更新，但与 NAS 正文不一致。\n\n" +
                                "本机标题：${conflict.localTitle}\nNAS 标题：${conflict.remoteTitle}\n\n保留本机还是用 NAS 覆盖？"
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.resolveNasArchiveConflict(NasArchiveConflictChoice.USE_REMOTE)
                            }
                        ) {
                            Text("使用 NAS")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                viewModel.resolveNasArchiveConflict(NasArchiveConflictChoice.KEEP_LOCAL)
                            }
                        ) {
                            Text("保留本地")
                        }
                    }
                )
            }
            if (nasBusy) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp))
            }
            nasStatusMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}
