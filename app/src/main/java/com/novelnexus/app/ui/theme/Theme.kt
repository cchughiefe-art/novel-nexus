package com.novelnexus.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    secondary = Color(0xFF22D3EE),
    tertiary = Color(0xFFFFB86B),
    background = Color(0xFF090C12),
    surface = Color(0xFF111620),
    surfaceVariant = Color(0xFF171D29),
    onBackground = Color(0xFFF6F7FB),
    onSurface = Color(0xFFF6F7FB),
    onSurfaceVariant = Color(0xFFB7C0D1)
)

private val Light = lightColorScheme(
    primary = Color(0xFF6D3FE8),
    secondary = Color(0xFF007C91),
    tertiary = Color(0xFFB85F00),
    background = Color(0xFFF7F8FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF0F6),
    onBackground = Color(0xFF151821),
    onSurface = Color(0xFF151821),
    onSurfaceVariant = Color(0xFF5F6675)
)

@Composable
fun NovelNexusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(),
        content = content
    )
}
