package com.example.moment.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moment.ui.settings.SettingsViewModel
import com.example.moment.ui.theme.appScaffoldContainerColor
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val nasBaseUrl by viewModel.nasBaseUrl.collectAsStateWithLifecycle()
    val nasUsername by viewModel.nasUsername.collectAsStateWithLifecycle()
    val nasPassword by viewModel.nasPassword.collectAsStateWithLifecycle()
    val nasTrustSelfSigned by viewModel.nasTrustSelfSigned.collectAsStateWithLifecycle()
    val nasBusy by viewModel.nasBusy.collectAsStateWithLifecycle()
    val nasStatusMessage by viewModel.nasStatusMessage.collectAsStateWithLifecycle()
    val nasMomentAccountUsernameDraft by viewModel.nasMomentAccountUsernameDraft.collectAsStateWithLifecycle()
    val nasMomentAccountPasswordDraft by viewModel.nasMomentAccountPasswordDraft.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.reloadDraftFieldsFromStore()
    }

    LaunchedEffect(Unit) {
        viewModel.saveSuccessMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = appScaffoldContainerColor(),
        contentColor = MaterialTheme.colorScheme.onBackground
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
                "账号与 NAS",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                "家庭 NAS（WebDAV）",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "备份与手帐存档走 WebDAV。建议使用 HTTPS；如果家庭 NAS 只能用 HTTP，应用也会允许连接。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = nasBaseUrl,
                onValueChange = viewModel::setNasBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("WebDAV 根地址") },
                placeholder = { Text("例如 https://192.168.1.10:5006/backup") },
                singleLine = true,
                enabled = !nasBusy
            )
            OutlinedTextField(
                value = nasUsername,
                onValueChange = viewModel::setNasUsername,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("用户名（可留空）") },
                singleLine = true,
                enabled = !nasBusy
            )
            OutlinedTextField(
                value = nasPassword,
                onValueChange = viewModel::setNasPassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密码（可留空）") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !nasBusy
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
                    onCheckedChange = viewModel::setNasTrustSelfSigned,
                    enabled = !nasBusy
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                "Moment 账号",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "多人共用同一 WebDAV 时可注册/登录；备份与存档只读当前账号目录。输入其他账号后点登录即可切换账号。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (prefs.nasMomentStorageUserId.isNotBlank()) {
                    "当前已登录：${prefs.nasMomentAccountUsername.ifBlank { "Moment 账号" }}"
                } else {
                    "当前未登录"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
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
                    Text(if (prefs.nasMomentStorageUserId.isNotBlank()) "切换/登录" else "登录")
                }
            }
            OutlinedButton(
                onClick = { viewModel.logoutNasMomentAccount() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !nasBusy && prefs.nasMomentStorageUserId.isNotBlank()
            ) {
                Text("退出 Moment 账号")
            }
            Button(
                onClick = { viewModel.testNasWebdavConnection() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !nasBusy
            ) {
                Text("测试 NAS 连接")
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
