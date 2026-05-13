package com.example.moment.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MomentColors = lightColorScheme(
    primary = Color(0xFF8B5E34),
    secondary = Color(0xFFB9845A),
    tertiary = Color(0xFF6F7D58),
    background = Color(0xFFFFFBF6),
    surface = Color(0xFFFFF7ED),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF2D2218),
    onSurface = Color(0xFF2D2218)
)

@Composable
fun MomentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MomentColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
