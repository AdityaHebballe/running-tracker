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

/**
 * Computes the color corresponding to a running pace:
 * Faster pace -> Hot Red (#EF4444)
 * Moderate pace -> Yellow / Amber (#FBBF24)
 * Slower pace -> Cool Green (#10B981)
 */
fun getPaceColorInt(paceSecPerKm: Double, minPace: Double, maxPace: Double): Int {
    if (maxPace <= minPace) return android.graphics.Color.rgb(0xEF, 0x44, 0x44)
    val norm = ((paceSecPerKm - minPace) / (maxPace - minPace)).coerceIn(0.0, 1.0)
    return if (norm < 0.5) {
        val f = (norm * 2.0).toFloat()
        // Fast to Moderate: Red (#EF4444) to Yellow (#FBBF24)
        val r = (0xEF + f * (0xFB - 0xEF)).toInt().coerceIn(0, 255)
        val g = (0x44 + f * (0xBF - 0x44)).toInt().coerceIn(0, 255)
        val b = (0x44 + f * (0x24 - 0x44)).toInt().coerceIn(0, 255)
        android.graphics.Color.rgb(r, g, b)
    } else {
        val f = ((norm - 0.5) * 2.0).toFloat()
        // Moderate to Slow: Yellow (#FBBF24) to Green (#10B981)
        val r = (0xFB + f * (0x10 - 0xFB)).toInt().coerceIn(0, 255)
        val g = (0xBF + f * (0xB9 - 0xBF)).toInt().coerceIn(0, 255)
        val b = (0x24 + f * (0x81 - 0x24)).toInt().coerceIn(0, 255)
        android.graphics.Color.rgb(r, g, b)
    }
}

fun getPaceColor(paceSecPerKm: Double, minPace: Double, maxPace: Double): Color {
    return Color(getPaceColorInt(paceSecPerKm, minPace, maxPace))
}

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
