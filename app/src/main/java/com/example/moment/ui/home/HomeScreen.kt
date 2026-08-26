package com.example.moment.ui.home

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.example.moment.ui.capture.CaptureViewModel
import com.example.moment.ui.capture.FragmentComposeScreen
import com.example.moment.ui.timeline.FragmentTimelineViewModel

@Composable
fun HomeScreen(
    navController: NavHostController,
    backStackEntry: NavBackStackEntry,
    onEditFragment: (Long) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
    timelineViewModel: FragmentTimelineViewModel = hiltViewModel()
) {
    FragmentComposeScreen(
        navController = navController,
        backStackEntry = backStackEntry,
        onClose = {},
        viewModel = viewModel,
        inlineOnHome = true,
        onEditFragment = onEditFragment,
        timelineViewModel = timelineViewModel
    )
}
