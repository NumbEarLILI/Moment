package com.example.moment.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.moment.domain.model.AppThemeMode

private val MomentOriginalColors = lightColorScheme(
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

/** 浅色偏白、中性，与「原始」暖色区分 */
private val MomentLightColors = lightColorScheme(
    primary = Color(0xFF6B4423),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF2C1608),
    secondary = Color(0xFF5D5D5D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E8E8),
    onSecondaryContainer = Color(0xFF1C1C1C),
    tertiary = Color(0xFF4A5F3F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE8D4),
    onTertiaryContainer = Color(0xFF1A2114),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFECECEC),
    onSurfaceVariant = Color(0xFF454545),
    outline = Color(0xFFC9C9C9),
    outlineVariant = Color(0xFFE0E0E0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val MomentDarkColors = darkColorScheme(
    primary = Color(0xFFE8C4A0),
    onPrimary = Color(0xFF2C1608),
    primaryContainer = Color(0xFF5C3820),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFD4C4B8),
    onSecondary = Color(0xFF261812),
    secondaryContainer = Color(0xFF4A4038),
    onSecondaryContainer = Color(0xFFF2DFD0),
    tertiary = Color(0xFFB8C9A8),
    onTertiary = Color(0xFF1A2114),
    tertiaryContainer = Color(0xFF3D4A34),
    onTertiaryContainer = Color(0xFFDFEAD2),
    background = Color(0xFF141210),
    onBackground = Color(0xFFEDE6DF),
    surface = Color(0xFF1C1815),
    onSurface = Color(0xFFEDE6DF),
    surfaceVariant = Color(0xFF3D3530),
    onSurfaceVariant = Color(0xFFD4C8BC),
    outline = Color(0xFF8D7F72),
    outlineVariant = Color(0xFF4A423C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val MomentShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MomentTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        AppThemeMode.DARK -> MomentDarkColors
        AppThemeMode.LIGHT -> MomentLightColors
        AppThemeMode.ORIGINAL -> MomentOriginalColors
        AppThemeMode.SYSTEM -> if (systemDark) MomentDarkColors else MomentLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MomentShapes,
        content = content
    )
}
