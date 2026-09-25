package com.tablodecori.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Green=Color(0xFF185B49)
private val GreenDark=Color(0xFF0F4538)
private val Mint=Color(0xFFDCEFE8)
private val MintSoft=Color(0xFFEDF7F3)
private val Gold=Color(0xFFB58A38)
private val Ink=Color(0xFF17221E)

private val Light=lightColorScheme(
    primary=Green,onPrimary=Color.White,primaryContainer=Mint,onPrimaryContainer=GreenDark,
    secondary=Gold,onSecondary=Color.White,secondaryContainer=Color(0xFFF5EACD),onSecondaryContainer=Color(0xFF4B3910),
    background=Color(0xFFF7F8F6),onBackground=Ink,surface=Color.White,onSurface=Ink,
    surfaceVariant=MintSoft,onSurfaceVariant=Color(0xFF53615C),outline=Color(0xFFB8C5C0)
)
private val Dark=darkColorScheme(
    primary=Color(0xFF80CDB3),onPrimary=Color(0xFF00382B),primaryContainer=Color(0xFF164F40),onPrimaryContainer=Color(0xFFB8F1DE),
    secondary=Color(0xFFE0C16F),background=Color(0xFF0D1512),surface=Color(0xFF14201C),onSurface=Color(0xFFEAF1ED),surfaceVariant=Color(0xFF20332B)
)
private val AppTypography=Typography(
    headlineSmall=TextStyle(fontSize=25.sp,lineHeight=36.sp,fontWeight=FontWeight.ExtraBold),
    titleLarge=TextStyle(fontSize=21.sp,lineHeight=30.sp,fontWeight=FontWeight.Bold),
    titleMedium=TextStyle(fontSize=17.sp,lineHeight=25.sp,fontWeight=FontWeight.Bold),
    bodyLarge=TextStyle(fontSize=16.sp,lineHeight=28.sp),
    bodyMedium=TextStyle(fontSize=14.sp,lineHeight=24.sp),
    labelLarge=TextStyle(fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold)
)
private val AppShapes=Shapes(
    extraSmall=androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge=androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
)
@Composable fun TablodecoriTheme(darkMode:Boolean?=null,content:@Composable()->Unit){MaterialTheme(colorScheme=if(darkMode?:isSystemInDarkTheme())Dark else Light,typography=AppTypography,shapes=AppShapes,content=content)}
