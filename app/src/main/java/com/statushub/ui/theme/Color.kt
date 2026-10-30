package com.statushub.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand: the sunset gradient of the app icon
val SunsetOrange = Color(0xFFFF8A3D)
val SunsetOrangeDeep = Color(0xFFF4511E)
val SunsetPink = Color(0xFFFF2E7E)
val SunsetPurple = Color(0xFF7B3FE4)

// Light scheme
val LightPrimary = Color(0xFFD81B60)
val LightOnPrimary = Color.White
val LightPrimaryContainer = Color(0xFFFFD9E3)
val LightOnPrimaryContainer = Color(0xFF3E0018)
val LightSecondary = Color(0xFF7B3FE4)
val LightSecondaryContainer = Color(0xFFEADDFF)
val LightOnSecondaryContainer = Color(0xFF24005A)
val LightTertiary = Color(0xFFF4511E)
val LightTertiaryContainer = Color(0xFFFFDBCF)
val LightOnTertiaryContainer = Color(0xFF3A0A00)
val LightBackground = Color(0xFFFBF7F8)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF3EBEE)
val LightOnSurfaceVariant = Color(0xFF5E5458)
val LightOutline = Color(0xFFD8CCD0)

// Dark scheme
val DarkPrimary = Color(0xFFFF8FB1)
val DarkOnPrimary = Color(0xFF5E0028)
val DarkPrimaryContainer = Color(0xFF8C1D47)
val DarkOnPrimaryContainer = Color(0xFFFFD9E3)
val DarkSecondary = Color(0xFFCFBCFF)
val DarkSecondaryContainer = Color(0xFF4F2C9E)
val DarkOnSecondaryContainer = Color(0xFFEADDFF)
val DarkTertiary = Color(0xFFFFB59C)
val DarkTertiaryContainer = Color(0xFF7A2A0F)
val DarkOnTertiaryContainer = Color(0xFFFFDBCF)
val DarkBackground = Color(0xFF140F12)
val DarkSurface = Color(0xFF1C1619)
val DarkSurfaceVariant = Color(0xFF2B2226)
val DarkOnSurfaceVariant = Color(0xFFD3C3C8)
val DarkOutline = Color(0xFF4A3E43)

/** Header / accent gradients: all from the icon's sunset palette, one shade per area */
object BrandGradients {
    val status = Brush.linearGradient(listOf(SunsetOrange, SunsetPink))
    val instagram = Brush.linearGradient(listOf(SunsetPink, SunsetPurple))
    val saved = Brush.linearGradient(listOf(SunsetPurple, SunsetPink))
    val splash = Brush.linearGradient(listOf(SunsetOrange, SunsetPink, SunsetPurple))
}
