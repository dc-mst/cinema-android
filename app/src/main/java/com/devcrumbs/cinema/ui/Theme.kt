package com.devcrumbs.cinema.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Web palette: frontend/tailwind.config.js `cinema.*`.
private val Gold = Color(0xFFD4A843)
private val GoldText = Color(0xFF8A6A10)
private val Dark = Color(0xFF1A1A2E)
private val Red = Color(0xFFC0392B)

private val LightColors = lightColorScheme(
    primary = GoldText,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6ECD0),
    onPrimaryContainer = Color(0xFF3A2C00),
    secondary = Dark,
    onSecondary = Color.White,
    error = Red,
)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Dark,
    primaryContainer = Color(0xFF4A3B10),
    onPrimaryContainer = Color(0xFFF6ECD0),
    background = Dark,
    surface = Dark,
    surfaceContainer = Color(0xFF24243C),
    surfaceContainerHigh = Color(0xFF2C2C46),
    error = Color(0xFFE57368),
)

@Composable
fun CinemaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
