package com.driveguard.mobile.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Ink90,
    onPrimary = SurfaceLight,
    secondary = Copper60,
    onSecondary = SurfaceLight,
    tertiary = Mint80,
    background = Sand10,
    surface = SurfaceLight,
    onBackground = Ink90,
    onSurface = Ink90,
)

@Composable
fun DriveGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = DriveGuardTypography,
        content = content,
    )
}
