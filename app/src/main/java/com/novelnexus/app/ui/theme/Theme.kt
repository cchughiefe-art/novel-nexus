package com.novelnexus.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE75A3C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4D2118),
    onPrimaryContainer = Color(0xFFFFDBD2),
    secondary = Color(0xFFD8B98E),
    background = Color(0xFF0F0F10),
    surface = Color(0xFF171719),
    surfaceVariant = Color(0xFF242426),
    onBackground = Color(0xFFF7F4EF),
    onSurface = Color(0xFFF7F4EF),
    onSurfaceVariant = Color(0xFFBFB9B0),
    outline = Color(0xFF595553)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFC93D25),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD0),
    onPrimaryContainer = Color(0xFF3C0A02),
    secondary = Color(0xFF725B3F),
    background = Color(0xFFFFFBF7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF4EFE9),
    onBackground = Color(0xFF211A17),
    onSurface = Color(0xFF211A17),
    onSurfaceVariant = Color(0xFF6E625B),
    outline = Color(0xFF9C8E86)
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp
    )
)

@Composable
fun NovelNexusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
