package com.example.moment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moment.data.preferences.UserPreferencesRepository
import com.example.moment.domain.model.AppThemeMode
import com.example.moment.ui.MomentApp
import com.example.moment.ui.theme.MomentTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by userPreferencesRepository.preferences
                .map { it.themeMode }
                .collectAsStateWithLifecycle(
                    lifecycleOwner = this@MainActivity,
                    initialValue = AppThemeMode.SYSTEM
                )
            MomentTheme(themeMode = themeMode) {
                MomentApp()
            }
        }
    }
}
