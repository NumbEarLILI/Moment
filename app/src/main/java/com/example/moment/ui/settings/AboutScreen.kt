package com.example.moment.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.moment.BuildConfig
import com.example.moment.domain.about.ChangelogParser
import com.example.moment.domain.about.MomentLinks
import com.example.moment.ui.theme.appScaffoldContainerColor

@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val releases = remember {
        val markdown = runCatching {
            context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
        }.getOrDefault("")
        ChangelogParser.parse(markdown)
    }

    Scaffold(
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
                "关于",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Moment",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "GitHub",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    MomentLinks.GITHUB_REPO,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(MomentLinks.GITHUB_REPO))
                            )
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "更新说明",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (releases.isEmpty()) {
                Text(
                    "暂无更新说明。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                releases.forEach { release ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            release.version,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            release.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
