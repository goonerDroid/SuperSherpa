package com.sublime.supersherpa.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF74D4C2),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF0A4F48),
    onPrimaryContainer = Color(0xFFA7F0E2),
    secondary = Color(0xFFB2CDC7),
    onSecondary = Color(0xFF1D3532),
    secondaryContainer = Color(0xFF324845),
    onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFA9C8FF),
    onTertiary = Color(0xFF0C2D59),
    tertiaryContainer = Color(0xFF24497B),
    onTertiaryContainer = Color(0xFFD9E2FF),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B1211),
    onBackground = Color(0xFFE0E3E1),
    surface = Color(0xFF0B1211),
    onSurface = Color(0xFFE0E3E1),
    surfaceVariant = Color(0xFF404948),
    onSurfaceVariant = Color(0xFFBEC9C5),
    outline = Color(0xFF889390),
    outlineVariant = Color(0xFF404948),
    scrim = Color.Black,
    inverseSurface = Color(0xFFE0E3E1),
    inverseOnSurface = Color(0xFF111414),
    inversePrimary = Color(0xFF006B61),
    surfaceDim = Color(0xFF0B1211),
    surfaceBright = Color(0xFF323938),
    surfaceContainerLowest = Color(0xFF050B0A),
    surfaceContainerLow = Color(0xFF111717),
    surfaceContainer = Color(0xFF151C1B),
    surfaceContainerHigh = Color(0xFF202726),
    surfaceContainerHighest = Color(0xFF2A3130),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006B61),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F0E2),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4C635F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE8E2),
    onSecondaryContainer = Color(0xFF071413),
    tertiary = Color(0xFF40608F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9E2FF),
    onTertiaryContainer = Color(0xFF001A3B),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color.White,
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FBF8),
    onBackground = Color(0xFF161D1B),
    surface = Color(0xFFF5FBF8),
    onSurface = Color(0xFF161D1B),
    surfaceVariant = Color(0xFFDCE5E1),
    onSurfaceVariant = Color(0xFF404948),
    outline = Color(0xFF717B78),
    outlineVariant = Color(0xFFC0CAC6),
    scrim = Color.Black,
    inverseSurface = Color(0xFF2B3230),
    inverseOnSurface = Color(0xFFEFF1EE),
    inversePrimary = Color(0xFF74D4C2),
    surfaceDim = Color(0xFFD6DBD8),
    surfaceBright = Color(0xFFF5FBF8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF5F2),
    surfaceContainer = Color(0xFFE8EFEC),
    surfaceContainerHigh = Color(0xFFE2E8E5),
    surfaceContainerHighest = Color(0xFFDCE5E1),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SuperSherpaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
