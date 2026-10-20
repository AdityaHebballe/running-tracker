package com.aerotrack.running.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val DarkBg = Color(0xFF0B0F17)
val SurfaceCard = Color(0xFF141C2B)
val SurfaceCardHover = Color(0xFF1D283E)
val BorderSubtle = Color(0xFF233047)

val AccentCyan = Color(0xFF06B6D4)
val AccentOrange = Color(0xFFFF6B35)
val AccentEmerald = Color(0xFF10B981)
val AccentRose = Color(0xFFF43F5E)
val AccentAmber = Color(0xFFF59E0B)

val ZonePeak = Color(0xFFEF4444)
val ZoneThreshold = Color(0xFFF97316)
val ZoneAerobic = Color(0xFFFBBF24)
val ZoneFatBurn = Color(0xFF34D399)
val ZoneWarmup = Color(0xFF60A5FA)

private val ExpressiveDarkColorScheme = darkColorScheme(
    primary = AccentOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B1D11),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = AccentCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004F58),
    onSecondaryContainer = Color(0xFF97F0FF),
    tertiary = AccentEmerald,
    background = DarkBg,
    surface = SurfaceCard,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9)
)

val ExpressiveShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun AeroTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ExpressiveDarkColorScheme,
        shapes = ExpressiveShapes,
        typography = Typography(),
        content = content
    )
}
