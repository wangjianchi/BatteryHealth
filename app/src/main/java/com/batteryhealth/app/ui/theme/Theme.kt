package com.batteryhealth.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Custom brand colors
val BatteryGreen = Color(0xFF00C896)
val BatteryGreenDim = Color(0x1F00C896)
val BatteryYellow = Color(0xFFFFB300)
val BatteryYellowDim = Color(0x1FFFB300)
val BatteryRed = Color(0xFFE53935)
val BatteryRedDim = Color(0x1FE53935)
val BatteryBlue = Color(0xFF4A90FF)
val BatteryBlueDim = Color(0x1F4A90FF)
val BatteryPurple = Color(0xFF7C6EFF)

val Background = Color(0xFF0A0F1E)
val Surface = Color(0xFF111827)
val Surface2 = Color(0xFF1A2236)
val Surface3 = Color(0xFF1E2A40)
val OnSurface = Color(0xFFF0F4FF)
val OnSurfaceVariant = Color(0xFF8B9DC3)
val Border = Color(0x14FFFFFF)

private val DarkColorScheme = darkColorScheme(
    primary = BatteryPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2C2660),
    onPrimaryContainer = Color(0xFFCBC5FF),
    secondary = BatteryGreen,
    onSecondary = Color(0xFF003D2A),
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = Surface2,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Border,
    error = BatteryRed
)

@Composable
fun BatteryHealthTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
