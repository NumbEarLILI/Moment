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

/** 冷色深色：与 `ic_launcher_background`（#0F1422）一致，蓝青强调。 */
private val MomentCoolColors = darkColorScheme(
    primary = Color(0xFF8EC5FF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = Color(0xFFB8C8DC),
    onSecondary = Color(0xFF233240),
    secondaryContainer = Color(0xFF3B4758),
    onSecondaryContainer = Color(0xFFD4E4F7),
    tertiary = Color(0xFF7FD0D4),
    onTertiary = Color(0xFF003739),
    tertiaryContainer = Color(0xFF004F52),
    onTertiaryContainer = Color(0xFFB8F4F8),
    background = Color(0xFF0F1422),
    onBackground = Color(0xFFE5E7F0),
    surface = Color(0xFF131924),
    onSurface = Color(0xFFE5E7F0),
    surfaceVariant = Color(0xFF252D3D),
    onSurfaceVariant = Color(0xFFBFC6D4),
    outline = Color(0xFF8B94A8),
    outlineVariant = Color(0xFF3D4656),
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
        AppThemeMode.COOL -> MomentCoolColors
        AppThemeMode.SYSTEM -> if (systemDark) MomentDarkColors else MomentLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MomentShapes,
        content = content
    )
}
