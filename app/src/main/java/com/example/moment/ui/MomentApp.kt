package com.example.moment.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.moment.ui.capture.CaptureScreen
import com.example.moment.ui.diary.DiaryDetailScreen
import com.example.moment.ui.diary.DiaryPreviewScreen
import com.example.moment.ui.history.HistoryEvent
import com.example.moment.ui.history.HistoryScreen
import com.example.moment.ui.history.HistoryViewModel
import com.example.moment.ui.place.PlacePickScreen
import java.time.LocalDate

@Composable
fun MomentApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.RootCapture) {
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
                }
            )
        }
        composable(
            route = Routes.Preview,
            arguments = listOf(navArgument("date") { type = NavType.StringType })
        ) { entry ->
            DiaryPreviewScreen(
                navController = navController,
                previewBackStackEntry = entry,
                diaryId = 0L,
                onClose = { navController.popBackStack() }
            )
        }
        composable(Routes.History) {
            val historyViewModel: HistoryViewModel = hiltViewModel()
            LaunchedEffect(historyViewModel) {
                historyViewModel.events.collect { event ->
                    when (event) {
                        is HistoryEvent.OpenSavedDiary -> navController.navigate("detail/${event.id}")
                        is HistoryEvent.OpenDiaryPreview -> navController.navigate("preview/${event.date}")
                    }
                }
            }
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onAddFragment = { date -> navController.navigate(Routes.capture(0L, date)) },
                onContinueEditFragment = { id -> navController.navigate(Routes.capture(id)) },
                onGenerateDiary = { date -> navController.navigate("preview/$date") },
                onOpenDiary = { id -> navController.navigate("detail/$id") },
                viewModel = historyViewModel
            )
        }
        composable(
            route = Routes.Detail,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val diaryId = entry.arguments!!.getLong("id")
            DiaryDetailScreen(
                navController = navController,
                diaryId = diaryId,
                onBack = { navController.popBackStack() }
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

object Routes {
    val RootCapture: String = capture(0L, null)
    const val Capture = "capture?fragmentId={fragmentId}&forDate={forDate}"
    const val Preview = "preview/{date}"
    const val History = "history"
    const val Detail = "detail/{id}"
    const val PlacePick = "placePick?lat={lat}&lng={lng}&hint={hint}&fragmentId={fragmentId}&diaryId={diaryId}"

    fun capture(fragmentId: Long, forDate: LocalDate? = null): String =
        buildString {
            append("capture?fragmentId=$fragmentId")
            if (fragmentId == 0L && forDate != null) {
                append("&forDate=$forDate")
            } else {
                append("&forDate=")
            }
        }

    fun placePick(lat: Double, lng: Double, hint: String, fragmentId: Long, diaryId: Long): String =
        "placePick?lat=$lat&lng=$lng&hint=${Uri.encode(hint)}&fragmentId=$fragmentId&diaryId=$diaryId"
}
