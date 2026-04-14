package com.sublime.supersherpa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.sublime.supersherpa.R

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
private fun appLightColorScheme() = lightColorScheme(
    primary = colorResource(R.color.brand_primary),
    onPrimary = colorResource(R.color.brand_on_primary),
    primaryContainer = colorResource(R.color.brand_primary_container),
    onPrimaryContainer = colorResource(R.color.brand_on_primary_container),
    secondary = colorResource(R.color.brand_secondary),
    onSecondary = colorResource(R.color.brand_on_secondary),
    secondaryContainer = colorResource(R.color.brand_secondary_container),
    onSecondaryContainer = colorResource(R.color.brand_on_secondary_container),
    tertiary = colorResource(R.color.brand_tertiary),
    onTertiary = colorResource(R.color.brand_on_tertiary),
    tertiaryContainer = colorResource(R.color.brand_tertiary_container),
    onTertiaryContainer = colorResource(R.color.brand_on_tertiary_container),
    error = colorResource(R.color.brand_error),
    errorContainer = colorResource(R.color.brand_error_container),
    onError = colorResource(R.color.brand_on_error),
    onErrorContainer = colorResource(R.color.brand_on_error_container),
    background = colorResource(R.color.brand_background),
    onBackground = colorResource(R.color.brand_on_background),
    surface = colorResource(R.color.brand_surface),
    onSurface = colorResource(R.color.brand_on_surface),
    surfaceVariant = colorResource(R.color.brand_surface_variant),
    onSurfaceVariant = colorResource(R.color.brand_on_surface_variant),
    outline = colorResource(R.color.brand_outline),
    outlineVariant = colorResource(R.color.brand_outline_variant),
    scrim = colorResource(R.color.brand_scrim),
    inverseSurface = colorResource(R.color.brand_inverse_surface),
    inverseOnSurface = colorResource(R.color.brand_inverse_on_surface),
    inversePrimary = colorResource(R.color.brand_inverse_primary),
    surfaceDim = colorResource(R.color.brand_surface_dim),
    surfaceBright = colorResource(R.color.brand_surface_bright),
    surfaceContainerLowest = colorResource(R.color.brand_surface_container_lowest),
    surfaceContainerLow = colorResource(R.color.brand_surface_container_low),
    surfaceContainer = colorResource(R.color.brand_surface_container),
    surfaceContainerHigh = colorResource(R.color.brand_surface_container_high),
    surfaceContainerHighest = colorResource(R.color.brand_surface_container_highest),
)

@Composable
private fun appDarkColorScheme() = darkColorScheme(
    primary = colorResource(R.color.brand_primary),
    onPrimary = colorResource(R.color.brand_on_primary),
    primaryContainer = colorResource(R.color.brand_primary_container),
    onPrimaryContainer = colorResource(R.color.brand_on_primary_container),
    secondary = colorResource(R.color.brand_secondary),
    onSecondary = colorResource(R.color.brand_on_secondary),
    secondaryContainer = colorResource(R.color.brand_secondary_container),
    onSecondaryContainer = colorResource(R.color.brand_on_secondary_container),
    tertiary = colorResource(R.color.brand_tertiary),
    onTertiary = colorResource(R.color.brand_on_tertiary),
    tertiaryContainer = colorResource(R.color.brand_tertiary_container),
    onTertiaryContainer = colorResource(R.color.brand_on_tertiary_container),
    error = colorResource(R.color.brand_error),
    errorContainer = colorResource(R.color.brand_error_container),
    onError = colorResource(R.color.brand_on_error),
    onErrorContainer = colorResource(R.color.brand_on_error_container),
    background = colorResource(R.color.brand_background),
    onBackground = colorResource(R.color.brand_on_background),
    surface = colorResource(R.color.brand_surface),
    onSurface = colorResource(R.color.brand_on_surface),
    surfaceVariant = colorResource(R.color.brand_surface_variant),
    onSurfaceVariant = colorResource(R.color.brand_on_surface_variant),
    outline = colorResource(R.color.brand_outline),
    outlineVariant = colorResource(R.color.brand_outline_variant),
    scrim = colorResource(R.color.brand_scrim),
    inverseSurface = colorResource(R.color.brand_inverse_surface),
    inverseOnSurface = colorResource(R.color.brand_inverse_on_surface),
    inversePrimary = colorResource(R.color.brand_inverse_primary),
    surfaceDim = colorResource(R.color.brand_surface_dim),
    surfaceBright = colorResource(R.color.brand_surface_bright),
    surfaceContainerLowest = colorResource(R.color.brand_surface_container_lowest),
    surfaceContainerLow = colorResource(R.color.brand_surface_container_low),
    surfaceContainer = colorResource(R.color.brand_surface_container),
    surfaceContainerHigh = colorResource(R.color.brand_surface_container_high),
    surfaceContainerHighest = colorResource(R.color.brand_surface_container_highest),
)

@Composable
fun SuperSherpaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) appDarkColorScheme() else appLightColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
