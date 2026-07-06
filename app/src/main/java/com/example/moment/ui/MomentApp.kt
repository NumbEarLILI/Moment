package com.example.moment.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.moment.R
import com.example.moment.ui.capture.CaptureScreen
import com.example.moment.ui.diary.DiaryDetailScreen
import com.example.moment.ui.diary.DiaryEditScreen
import com.example.moment.ui.diary.DiaryPreviewScreen
import com.example.moment.ui.history.HistoryScreen
import com.example.moment.ui.history.HistoryViewModel
import com.example.moment.ui.mine.AccountSettingsScreen
import com.example.moment.ui.mine.MineScreen
import com.example.moment.ui.place.PlacePickScreen
import com.example.moment.ui.settings.SettingsScreen
import com.example.moment.ui.theme.appRootContainerColor
import java.time.LocalDate

@Composable
fun MomentApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val mainTabs = listOf(
        MainTab(label = "首页", iconRes = R.drawable.ic_nav_home, route = Routes.RootCapture, selectedRoute = Routes.Capture),
        MainTab(label = "历史", iconRes = R.drawable.ic_nav_history, route = Routes.History, selectedRoute = Routes.History),
        MainTab(label = "我的", iconRes = R.drawable.ic_nav_mine, route = Routes.Mine, selectedRoute = Routes.Mine)
    )
    val isRootCapture = currentRoute == Routes.Capture &&
        (backStackEntry?.arguments?.getLong("fragmentId") ?: 0L) == 0L &&
        backStackEntry?.arguments?.getString("forDate").isNullOrBlank()
    val showBottomBar = isRootCapture || currentRoute == Routes.History || currentRoute == Routes.Mine

    Scaffold(
        containerColor = appRootContainerColor(),
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (showBottomBar) {
                MomentBottomNavigation(
                    tabs = mainTabs,
                    currentRoute = currentRoute,
                    onTabClick = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.RootCapture,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            composable(
                route = Routes.Capture,
                arguments = listOf(
                    navArgument("fragmentId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("forDate") { type = NavType.StringType; defaultValue = "" }
                )
            ) { entry ->
                CaptureScreen(
                    navController = navController,
                    backStackEntry = entry,
                    onClose = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.RootCapture) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            }
                        }
                    },
                    onGenerateDiary = { date -> navController.navigate(Routes.preview(date, 0L)) },
                    onOpenDiary = { id -> navController.navigate("detail/$id") }
                )
            }
            composable(
                route = Routes.Preview,
                arguments = listOf(
                    navArgument("date") { type = NavType.StringType },
                    navArgument("diaryId") { type = NavType.LongType }
                )
            ) { entry ->
                val previewDiaryId = entry.arguments?.getLong("diaryId") ?: 0L
                DiaryPreviewScreen(
                    navController = navController,
                    previewBackStackEntry = entry,
                    diaryId = previewDiaryId,
                    onClose = { navController.popBackStack() }
                )
            }
            composable(Routes.History) {
                val historyViewModel: HistoryViewModel = hiltViewModel()
                HistoryScreen(
                    onAddFragmentForPastDay = { date -> navController.navigate(Routes.capture(0L, date)) },
                    onContinueEditFragment = { id -> navController.navigate(Routes.capture(id)) },
                    onOpenDiary = { id -> navController.navigate("detail/$id") },
                    viewModel = historyViewModel
                )
            }
            composable(Routes.Mine) {
                MineScreen(
                    onOpenAccountSettings = { navController.navigate(Routes.AccountSettings) },
                    onOpenSettings = { navController.navigate(Routes.Settings) }
                )
            }
            composable(Routes.AccountSettings) {
                AccountSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.Settings) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.Detail,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val diaryId = entry.arguments!!.getLong("id")
                DiaryDetailScreen(
                    navController = navController,
                    diaryId = diaryId,
                    onBack = { navController.popBackStack() },
                    onEditDiary = { id -> navController.navigate(Routes.editDiary(id)) }
                )
            }
            composable(
                route = Routes.DiaryEdit,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                DiaryEditScreen(
                    navController = navController,
                    editBackStackEntry = entry,
                    onClose = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.PlacePick,
                arguments = listOf(
                    navArgument("lat") { type = NavType.StringType },
                    navArgument("lng") { type = NavType.StringType },
                    navArgument("hint") { type = NavType.StringType; defaultValue = "" },
                    navArgument("fragmentId") { type = NavType.StringType; defaultValue = "0" },
                    navArgument("diaryId") { type = NavType.StringType; defaultValue = "0" }
                )
            ) {
                PlacePickScreen(
                    navController = navController,
                    onClose = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun MomentBottomNavigation(
    tabs: List<MainTab>,
    currentRoute: String?,
    onTabClick: (MainTab) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(48.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.selectedRoute
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .selectable(
                            selected = selected,
                            onClick = { onTabClick(tab) },
                            role = Role.Tab
                        )
                        .padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = null,
                        modifier = Modifier.height(20.dp),
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

private data class MainTab(
    val label: String,
    val iconRes: Int,
    val route: String,
    val selectedRoute: String
)

object Routes {
    val RootCapture: String = capture(0L, null)
    const val Capture = "capture?fragmentId={fragmentId}&forDate={forDate}"
    const val Preview = "preview/{date}/{diaryId}"

    /** @param diaryId 已保存手帐的主键；无锚点手帐时用 0（须写入路径，query 在部分机型上不进 SavedStateHandle）。 */
    fun preview(date: LocalDate, diaryId: Long): String = "preview/$date/$diaryId"
    const val History = "history"
    const val Mine = "mine"
    const val AccountSettings = "accountSettings"
    const val Settings = "settings"
    const val Detail = "detail/{id}"
    const val DiaryEdit = "edit/{id}"
    const val PlacePick = "placePick?lat={lat}&lng={lng}&hint={hint}&fragmentId={fragmentId}&diaryId={diaryId}"

    fun editDiary(id: Long): String = "edit/$id"

    fun capture(fragmentId: Long, forDate: LocalDate? = null): String =
        buildString {
            append("capture?fragmentId=$fragmentId")
            if (fragmentId == 0L && forDate != null) {
                append("&forDate=$forDate")
            } else {
                append("&forDate=")
            }
        }

    fun placePick(lat: Double, lng: Double, hint: String, fragmentStableId: String, diaryId: Long): String =
        "placePick?lat=$lat&lng=$lng&hint=${Uri.encode(hint)}&fragmentId=${
            Uri.encode(fragmentStableId)
        }&diaryId=$diaryId"
}
