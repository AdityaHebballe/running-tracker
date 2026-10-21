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

// Deep Obsidian-Violet Dark Theme
val DarkBg = Color(0xFF09080E)           // Ultra-dark background with subtle violet undertone
val DarkSurface = Color(0xFF12101B)      // Slightly elevated background surface
val SurfaceCard = Color(0xFF181524)      // Primary card surface
val SurfaceCardHover = Color(0xFF241F35) // Elevated / hover state
val BorderSubtle = Color(0xFF2C2442)     // Subtle violet-tinted border
val BorderGlow = Color(0xFF7E22CE)       // Highlight border

// Vibrant Purple Accent System
val AccentPurple = Color(0xFFA855F7)        // Primary electric violet (#A855F7)
val AccentPurpleLight = Color(0xFFC084FC)   // Light purple for highlights & icons
val AccentPurpleBright = Color(0xFFD8B4FE)  // Soft bright lilac for contrast
val AccentPurpleDark = Color(0xFF7E22CE)    // Deep vivid purple for gradients
val AccentPurpleContainer = Color(0xFF2E1065) // Dark purple container fill
val AccentPurpleMuted = Color(0xFF3B1E6D)

// Backward compatibility alias for existing code
val AccentOrange = AccentPurple             // Seamlessly updates all prior orange accents to purple

// Telemetry Metric Accents
val AccentCyan = Color(0xFF38BDF8)          // Pace curve & metrics (Electric Sky)
val AccentRose = Color(0xFFF43F5E)          // Heart rate curve & metrics (Vibrant Rose)
val AccentEmerald = Color(0xFF10B981)       // Start pin & positive lap indicators
val AccentAmber = Color(0xFFF59E0B)         // Moderate intensity

// Heart Rate Zones
val ZonePeak = Color(0xFFEF4444)
val ZoneThreshold = Color(0xFFF97316)
val ZoneAerobic = Color(0xFFFBBF24)
val ZoneFatBurn = Color(0xFF34D399)
val ZoneWarmup = Color(0xFF818CF8)

private val ExpressiveDarkColorScheme = darkColorScheme(
    primary = AccentPurple,
    onPrimary = Color.White,
    primaryContainer = AccentPurpleContainer,
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = AccentPurpleLight,
    onSecondary = Color.Black,
    secondaryContainer = AccentPurpleMuted,
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = AccentCyan,
    background = DarkBg,
    surface = SurfaceCard,
    surfaceVariant = DarkSurface,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    outline = BorderSubtle
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
