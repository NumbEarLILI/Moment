package com.example.moment.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moment.domain.model.NasArchiveConflictChoice
import com.example.moment.ui.capture.CaptureViewModel
import com.example.moment.ui.common.QuietTextAction
import com.example.moment.ui.theme.appScaffoldContainerColor
import com.example.moment.ui.timeline.FragmentTimelineSection
import com.example.moment.ui.timeline.FragmentTimelineViewModel

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    onCreateFragment: () -> Unit,
    onEditFragment: (Long) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
    timelineViewModel: FragmentTimelineViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val timelineState by timelineViewModel.uiState.collectAsStateWithLifecycle()
    val nasArchiveConflict by viewModel.nasArchiveConflictInfo.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.nasArchiveRefreshing,
        onRefresh = viewModel::refreshNasArchivePull
    )
    val scrollState = rememberScrollState()

    fun hasLocationPermission(): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    val weatherPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshWeather()
    }

    fun requestWeather() {
        if (hasLocationPermission()) {
            viewModel.refreshWeather()
        } else {
            weatherPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission()) {
            viewModel.refreshWeather()
        } else if (viewModel.consumeAutoRequestWeatherLocation()) {
            weatherPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(state.nasArchiveSyncMessage) {
        if (state.nasArchiveSyncMessage != null) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearNasArchiveSyncMessage()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        Scaffold(
            containerColor = appScaffoldContainerColor(),
            contentColor = MaterialTheme.colorScheme.onBackground
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .navigationBarsPadding()
                    .verticalScroll(scrollState)
            ) {
                state.nasArchiveSyncMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = state.weatherCaption,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(role = Role.Button, onClick = { requestWeather() }),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    QuietTextAction(
                        text = "记下这一刻",
                        onClick = onCreateFragment
                    )
                }
                FragmentTimelineSection(
                    state = timelineState,
                    onAddFragment = onCreateFragment,
                    onContinueEditFragment = onEditFragment,
                    onDeleteFragment = timelineViewModel::delete,
                    onClearDeleteError = timelineViewModel::clearDeleteError,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
        PullRefreshIndicator(
            refreshing = state.nasArchiveRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
    nasArchiveConflict?.let { conflict ->
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
}
