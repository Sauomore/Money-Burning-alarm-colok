package com.dsh.burnalarm.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFD9501E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF9A6A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDF9B),
    onSecondaryContainer = Color(0xFF2E1F00),
    background = Color(0xFFFDF7F5),
    surface = Color(0xFFFDF7F5),
    surfaceVariant = Color(0xFFF3E0DA),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF6B35),
    onPrimary = Color(0xFF3A0B00),
    primaryContainer = Color(0xFF7A2A0B),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFFFD166),
    onSecondary = Color(0xFF2E1F00),
    secondaryContainer = Color(0xFF6B4A00),
    onSecondaryContainer = Color(0xFFFFDF9B),
    background = Color(0xFF12121C),
    surface = Color(0xFF12121C),
    surfaceVariant = Color(0xFF2A2A38),
    error = Color(0xFFFF5252)
)

@Composable
fun BurnAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
