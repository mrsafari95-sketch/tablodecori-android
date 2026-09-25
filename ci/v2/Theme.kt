package com.tablodecori.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val BrandGreen = Color(0xFF006B55)
val BrandGreenDark = Color(0xFF004E3F)
val Mint = Color(0xFFE2F5EE)
val MintStrong = Color(0xFFCBEDE1)
val WarmGold = Color(0xFFFFD978)
val SoftGold = Color(0xFFFFF0C6)
val AppBackground = Color(0xFFF9FAF8)
val Ink = Color(0xFF12211E)
val MutedInk = Color(0xFF64716E)
val LavenderSurface = Color(0xFFF8F4FC)
val DangerSoft = Color(0xFFFFE8E8)

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = BrandGreenDark,
    secondary = Color(0xFF5D6F68),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDF3F0),
    onSecondaryContainer = Ink,
    tertiary = WarmGold,
    onTertiary = Color(0xFF4B3700),
    tertiaryContainer = SoftGold,
    onTertiaryContainer = Color(0xFF4B3700),
    background = AppBackground,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = LavenderSurface,
    onSurfaceVariant = MutedInk,
    outline = Color(0xFFD5DEDA),
    outlineVariant = Color(0xFFE7ECE9),
    error = Color(0xFFB3261E),
    errorContainer = DangerSoft
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF72D7BA),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF005140),
    onPrimaryContainer = Color(0xFF90F4D5),
    secondary = Color(0xFFB6CCC4),
    background = Color(0xFF101513),
    surface = Color(0xFF161C19),
    surfaceVariant = Color(0xFF202824),
    onSurfaceVariant = Color(0xFFC3CDC9),
    outline = Color(0xFF87928D),
    tertiary = Color(0xFFFFD978)
)

val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 25.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

@Composable
fun TablodecoriTheme(darkMode: Boolean? = null, content: @Composable () -> Unit) {
    val useDarkTheme = darkMode ?: isSystemInDarkTheme()
    val colors = if (useDarkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDarkTheme
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
