package com.example.moment.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.moment.ui.capture.CaptureScreen
import com.example.moment.ui.diary.DiaryDetailScreen
import com.example.moment.ui.diary.DiaryPreviewScreen
import com.example.moment.ui.history.HistoryScreen
import com.example.moment.ui.home.HomeScreen

@Composable
fun MomentApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.Home) {
        composable(Routes.Home) {
            HomeScreen(
                onAddFragment = { navController.navigate(Routes.Capture) },
                onGenerateDiary = { date -> navController.navigate("preview/$date") },
                onOpenHistory = { navController.navigate(Routes.History) }
            )
        }
        composable(Routes.Capture) {
            CaptureScreen(onClose = { navController.popBackStack() })
        }
        composable(
            route = Routes.Preview,
            arguments = listOf(navArgument("date") { type = NavType.StringType })
        ) {
            DiaryPreviewScreen(onClose = { navController.popBackStack(Routes.Home, inclusive = false) })
        }
        composable(Routes.History) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenDiary = { id -> navController.navigate("detail/$id") }
            )
        }
        composable(
            route = Routes.Detail,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) {
            DiaryDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}

object Routes {
    const val Home = "home"
    const val Capture = "capture"
    const val Preview = "preview/{date}"
    const val History = "history"
    const val Detail = "detail/{id}"
}
