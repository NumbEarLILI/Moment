package com.example.moment.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MomentColors = lightColorScheme(
    primary = Color(0xFF7A4E2D),
    onPrimary = Color(0xFFFFFBF7),
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF2C1608),
    secondary = Color(0xFF6B5B4F),
    onSecondary = Color(0xFFFFF8F3),
    secondaryContainer = Color(0xFFF2DFD0),
    onSecondaryContainer = Color(0xFF261812),
    tertiary = Color(0xFF5C6B4E),
    onTertiary = Color(0xFFF5FCEF),
    tertiaryContainer = Color(0xFFDFEAD2),
    onTertiaryContainer = Color(0xFF1A2114),
    background = Color(0xFFFFFAF5),
    onBackground = Color(0xFF221A14),
    surface = Color(0xFFFFF6EC),
    onSurface = Color(0xFF221A14),
    surfaceVariant = Color(0xFFF0E4D8),
    onSurfaceVariant = Color(0xFF52473D),
    outline = Color(0xFFD1BDA8),
    outlineVariant = Color(0xFFE8D9CA),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val MomentShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MomentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MomentColors,
        shapes = MomentShapes,
        content = content
    )
}
