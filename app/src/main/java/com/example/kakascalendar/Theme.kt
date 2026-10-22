package com.example.kakascalendar

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF7B4F32), onPrimary = Color.White,
    primaryContainer = Color(0xFFF1E0D0), onPrimaryContainer = Color(0xFF3E2415),
    secondary = Color(0xFFC98B5B), onSecondary = Color.White,
    background = Color(0xFFFBF6F0), surface = Color(0xFFFBF6F0),
    surfaceVariant = Color(0xFFF3EAE0), onSurface = Color(0xFF2B1E16),
    onSurfaceVariant = Color(0xFF6B5A4E)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE0B08A), onPrimary = Color(0xFF3E2415),
    primaryContainer = Color(0xFF5A3A24), onPrimaryContainer = Color(0xFFF7E3D2),
    secondary = Color(0xFFC98B5B), onSecondary = Color.White,
    background = Color(0xFF1C1411), surface = Color(0xFF1C1411),
    surfaceVariant = Color(0xFF2E231D), onSurface = Color(0xFFF1E6DD),
    onSurfaceVariant = Color(0xFFCDBBAD)
)

@Composable
fun KakascalendarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}
