package com.example.moment.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moment.domain.model.AppThemeMode

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

    LaunchedEffect(Unit) {
        viewModel.reloadAiDraftsFromStore()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
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
                    label = "跟随系统",
                    selected = prefs.themeMode == AppThemeMode.SYSTEM,
                    onClick = { viewModel.selectTheme(AppThemeMode.SYSTEM) }
                )
            }
            Text(
                "跟随系统：设备为深色时使用暗黑主题，否则使用浅色（白）主题。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                "大模型",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "填写 OpenAI 兼容接口（如 /v1/chat/completions）、密钥与模型名。已配置时，「生成手帐」会调用模型整合当日碎片；未配置则使用内置规则。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = aiBaseUrl,
                onValueChange = viewModel::setAiBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API 根地址") },
                placeholder = { Text("例如 https://api.openai.com/v1") },
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
                placeholder = { Text("例如 gpt-4o-mini") },
                singleLine = true
            )
            Button(
                onClick = { viewModel.saveAiSettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存大模型配置")
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
