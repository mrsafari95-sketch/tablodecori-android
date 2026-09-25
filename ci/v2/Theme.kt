package com.tablodecori.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Green = Color(0xFF185B49)
private val GreenDark = Color(0xFF0F4538)
private val Mint = Color(0xFFDCEFE8)
private val MintSoft = Color(0xFFEEF6F2)
private val Gold = Color(0xFF8F681F)
private val GoldContainer = Color(0xFFF5E8C5)
private val Ink = Color(0xFF17221E)
private val MutedInk = Color(0xFF53615C)

private val Light = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = GreenDark,
    inversePrimary = Color(0xFF8FD2BC),

    secondary = Gold,
    onSecondary = Color.White,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = Color(0xFF3E2D08),

    background = Color(0xFFF7F8F6),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = MintSoft,
    onSurfaceVariant = MutedInk,
    surfaceTint = Green,

    inverseSurface = Color(0xFF2B3430),
    inverseOnSurface = Color(0xFFEFF3F0),

    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    outline = Color(0xFFA9B7B1),
    outlineVariant = Color(0xFFD2DDD8),
    scrim = Color.Black,

    surfaceBright = Color.White,
    surfaceDim = Color(0xFFD9DFDB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F6F4),
    surfaceContainer = Color(0xFFEEF2EF),
    surfaceContainerHigh = Color(0xFFE8EDE9),
    surfaceContainerHighest = Color(0xFFE1E7E3)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8DD5BD),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF164F40),
    onPrimaryContainer = Color(0xFFB8F1DE),
    inversePrimary = Green,

    secondary = Color(0xFFE1C171),
    onSecondary = Color(0xFF3B2F00),
    secondaryContainer = Color(0xFF544514),
    onSecondaryContainer = Color(0xFFF9E3A4),

    background = Color(0xFF0D1512),
    onBackground = Color(0xFFE8F0EC),
    surface = Color(0xFF111B17),
    onSurface = Color(0xFFE8F0EC),
    surfaceVariant = Color(0xFF1E2B26),
    onSurfaceVariant = Color(0xFFBBC8C2),
    surfaceTint = Color(0xFF8DD5BD),

    inverseSurface = Color(0xFFE8F0EC),
    inverseOnSurface = Color(0xFF29322E),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    outline = Color(0xFF87948F),
    outlineVariant = Color(0xFF3D4A45),
    scrim = Color.Black,

    surfaceBright = Color(0xFF35413C),
    surfaceDim = Color(0xFF111B17),
    surfaceContainerLowest = Color(0xFF0A100E),
    surfaceContainerLow = Color(0xFF16201C),
    surfaceContainer = Color(0xFF1A2520),
    surfaceContainerHigh = Color(0xFF24302B),
    surfaceContainerHighest = Color(0xFF2F3B36)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.ExtraBold
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.ExtraBold
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    )
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TablodecoriTheme(
    darkMode: Boolean? = null,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkMode ?: isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
