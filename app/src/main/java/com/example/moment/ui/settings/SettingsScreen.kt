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
    val nasBackupRunIds by viewModel.nasBackupRunIds.collectAsStateWithLifecycle()
    val selectedNasRunId by viewModel.selectedNasRunId.collectAsStateWithLifecycle()
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
                    label = "原始",
                    selected = prefs.themeMode == AppThemeMode.ORIGINAL,
                    onClick = { viewModel.selectTheme(AppThemeMode.ORIGINAL) }
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
                "冷色：深色底与 App 图标一致，蓝青强调。跟随系统：设备为深色时使用暗黑主题，否则使用浅色（白）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                "自定义背景图",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "从相册选择一张图铺满全屏；各页面底栏略带透明便于阅读。若图片来自相册，请保持文件可读取权限。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                "填写 OpenAI 兼容接口（如 DeepSeek、/chat/completions）、密钥与模型名。已配置时，「生成手帐」会调用模型整合当日碎片；未配置则使用内置规则。",
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
                "用于将已保存的手帐备份到 NAS，或从 NAS 读回备份。请在 NAS 上启用 WebDAV，并填写根路径（通常为某个共享文件夹的 WebDAV 地址）。每次备份会新建 MomentBackup/runs/run_时间戳/ 目录。",
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
                "仅在 NAS 使用 HTTPS 且证书非系统信任时开启；会降低连接安全性。",
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
                "从 MomentBackup/runs/ 下列出本机备份，按日期合并到本地手帐（同一天会覆盖本地该日手帐）。图片会下载到应用私有目录。",
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
