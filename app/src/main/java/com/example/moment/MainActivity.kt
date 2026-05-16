package com.example.moment

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.moment.data.preferences.UserPreferencesRepository
import com.example.moment.domain.model.UserAppPreferences
import com.example.moment.ui.MomentApp
import com.example.moment.ui.theme.LocalAppWallpaperUri
import com.example.moment.ui.theme.MomentTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by userPreferencesRepository.preferences.collectAsStateWithLifecycle(
                lifecycleOwner = this@MainActivity,
                initialValue = UserAppPreferences()
            )
            val wallpaperUri = prefs.customBackgroundImageUri.trim().takeIf { it.isNotEmpty() }
            Box(Modifier.fillMaxSize()) {
                if (wallpaperUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(this@MainActivity)
                            .data(Uri.parse(wallpaperUri))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                CompositionLocalProvider(LocalAppWallpaperUri provides wallpaperUri) {
                    MomentTheme(themeMode = prefs.themeMode) {
                        MomentApp()
                    }
                }
            }
        }
    }
}
