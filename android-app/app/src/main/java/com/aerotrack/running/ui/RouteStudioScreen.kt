package com.aerotrack.running.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aerotrack.running.data.TrackPoint
import com.aerotrack.running.data.WorkoutSession
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.advancedpolyline.ColorMapping
import org.osmdroid.views.overlay.advancedpolyline.PolychromaticPaintList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteStudioScreen(
    workout: WorkoutSession,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Initialize OsmDroid configuration with application package user-agent
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
    }

    val validPoints = remember(workout) {
        workout.trackPoints.filter { it.latitude != 0.0 && it.longitude != 0.0 }
    }

    var selectedLapNumber by remember { mutableStateOf<Int?>(null) }

    val activeSplit = remember(selectedLapNumber, workout.splits) {
        workout.splits.firstOrNull { it.splitNumber == selectedLapNumber }
    }

    val activeLapPoints = remember(validPoints, selectedLapNumber, workout.splits) {
        if (selectedLapNumber == null) {
            validPoints
        } else {
            val split = workout.splits.firstOrNull { it.splitNumber == selectedLapNumber }
            if (split != null && split.endIndex > split.startIndex && split.endIndex < validPoints.size) {
                validPoints.subList(split.startIndex, split.endIndex + 1)
            } else if (split != null) {
                val targetStartDist = (split.splitNumber - 1) * 255.0
                val targetEndDist = split.splitNumber * 255.0
                val sIdx = validPoints.indexOfFirst { it.distanceMeters >= targetStartDist }.coerceAtLeast(0)
                val eIdx = validPoints.indexOfLast { it.distanceMeters <= targetEndDist }.takeIf { it >= sIdx } ?: (validPoints.size - 1)
                validPoints.subList(sIdx, (eIdx + 1).coerceAtMost(validPoints.size))
            } else {
                validPoints
            }
        }
    }

    var scrubIndex by remember(activeLapPoints) {
        mutableStateOf(0)
    }

    val currentPoint = activeLapPoints.getOrNull(scrubIndex.coerceIn(0, (activeLapPoints.size - 1).coerceAtLeast(0)))
    var isOsmView by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clip(CircleShape)
                            .background(SurfaceCard)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Text(
                        text = "Route Studio",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                },
                actions = {
                    // Compact View Switcher: OSM vs Vector (No navbar overflow!)
                    Surface(
                        onClick = { isOsmView = !isOsmView },
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, if (isOsmView) AccentPurple else AccentPurpleLight),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (isOsmView) Icons.Default.Map else Icons.Default.Timeline,
                                contentDescription = null,
                                tint = if (isOsmView) AccentPurple else AccentPurpleLight,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (isOsmView) "OSM" else "Vector",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Genuine Native Map Canvas (Bold, high-contrast, density-scaled markers)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0B0F17))
            ) {
                if (validPoints.isNotEmpty()) {
                    if (isOsmView) {
                        // Native OpenStreetMap MapView (High-res, deep zoom levels 4..20, kinetic pan)
                        NativeOsmMapView(
                            allPoints = validPoints,
                            lapPoints = activeLapPoints,
                            isIsolated = selectedLapNumber != null,
                            selectedLapNumber = selectedLapNumber,
                            scrubIndex = scrubIndex
                        )
                    } else {
                        // Pure High-Contrast Neon Vector Track Canvas
                        PureVectorTrackCanvas(
                            allPoints = validPoints,
                            lapPoints = activeLapPoints,
                            isIsolated = selectedLapNumber != null,
                            selectedLapNumber = selectedLapNumber,
                            scrubIndex = scrubIndex
                        )
                    }

                    // Floating Pace Heat Legend Pill (Red -> Yellow -> Green)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkBg.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Fast", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                            Box(
                                modifier = Modifier
                                    .width(42.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFEF4444), Color(0xFFFBBF24), Color(0xFF10B981))
                                        )
                                    )
                            )
                            Text("Slow", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        }
                    }

                    // Floating Lap Isolation Badge on Map
                    if (selectedLapNumber != null) {
                        Surface(
                            onClick = { selectedLapNumber = null },
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xEE1E1528),
                            border = BorderStroke(1.dp, AccentPurpleLight),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterAlt,
                                    contentDescription = null,
                                    tint = AccentPurpleLight,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Lap $selectedLapNumber Isolated",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Show All",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No GPS Trackpoints Available", color = Color(0xFF94A3B8))
                    }
                }
            }

            // 2. Fixed Bottom Telemetry Scrubber Dock with Big Graph & 2-Dot Synchronized Guideline
            if (validPoints.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, BorderSubtle),
                    shadowElevation = 24.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        // ROW 1: Real-Time Telemetry Cards with uniform icon badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Pace Chip
                            val curPaceSec = currentPoint?.paceSecPerKm ?: (activeSplit?.avgPaceSecPerKm ?: workout.avgPaceSecPerKm)
                            val pm = (curPaceSec / 60).toInt()
                            val ps = (curPaceSec % 60).toInt()
                            TelemetryBadgeChip(
                                icon = Icons.Outlined.Speed,
                                iconTint = AccentCyan,
                                label = "PACE",
                                value = "$pm:${if (ps < 10) "0" else ""}$ps",
                                unit = "/km",
                                valueColor = AccentCyan,
                                modifier = Modifier.weight(1f)
                            )

                            // Heart Rate Chip
                            TelemetryBadgeChip(
                                icon = Icons.Default.Favorite,
                                iconTint = AccentRose,
                                label = "HEART",
                                value = "${currentPoint?.heartRateBpm ?: (activeSplit?.avgHeartRate ?: workout.avgHeartRate)}",
                                unit = "bpm",
                                valueColor = AccentRose,
                                modifier = Modifier.weight(1f)
                            )

                            // Distance Chip (Relative to isolated lap if active)
                            val distMeters = if (selectedLapNumber != null && activeLapPoints.isNotEmpty()) {
                                ((currentPoint?.distanceMeters ?: 0.0) - activeLapPoints.first().distanceMeters).coerceAtLeast(0.0)
                            } else {
                                currentPoint?.distanceMeters ?: 0.0
                            }
                            val distKm = String.format("%.2f", distMeters / 1000.0)
                            TelemetryBadgeChip(
                                icon = Icons.AutoMirrored.Filled.DirectionsRun,
                                iconTint = AccentPurpleLight,
                                label = if (selectedLapNumber != null) "LAP DIST" else "DIST",
                                value = distKm,
                                unit = "km",
                                valueColor = AccentPurpleLight,
                                modifier = Modifier.weight(1f)
                            )

                            // Time Chip (Relative to isolated lap if active)
                            val elapsedSec = if (selectedLapNumber != null && activeLapPoints.isNotEmpty()) {
                                ((currentPoint?.elapsedSeconds ?: 0L) - activeLapPoints.first().elapsedSeconds).coerceAtLeast(0L)
                            } else {
                                currentPoint?.elapsedSeconds ?: 0L
                            }
                            TelemetryBadgeChip(
                                icon = Icons.Outlined.Timer,
                                iconTint = AccentPurpleBright,
                                label = if (selectedLapNumber != null) "LAP TIME" else "TIME",
                                value = formatTime(elapsedSec),
                                unit = "min",
                                valueColor = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ROW 2: Big Telemetry Canvas with 2 DOTS following Pace and Heart lines!
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(125.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkSurface)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                        ) {
                            DualCurvesTelemetryCanvas(
                                points = activeLapPoints,
                                scrubIndex = scrubIndex,
                                workout = workout
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // ROW 3: Interactive Smooth Scrub Slider
                        Slider(
                            value = scrubIndex.toFloat().coerceIn(0f, (activeLapPoints.size - 1).coerceAtLeast(0).toFloat()),
                            onValueChange = { newVal ->
                                scrubIndex = newVal.toInt().coerceIn(0, (activeLapPoints.size - 1).coerceAtLeast(0))
                            },
                            valueRange = 0f..(activeLapPoints.size - 1).coerceAtLeast(1).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = AccentPurple,
                                activeTrackColor = AccentPurple,
                                inactiveTrackColor = AccentPurpleMuted.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth().height(32.dp)
                        )

                        // ROW 4: Lap Isolation Selector Chips
                        if (workout.splits.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LAPS:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                )

                                val isAllSelected = (selectedLapNumber == null)
                                Surface(
                                    onClick = {
                                        selectedLapNumber = null
                                        scrubIndex = 0
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isAllSelected) AccentPurple else Color.White.copy(alpha = 0.05f),
                                    border = BorderStroke(1.dp, if (isAllSelected) AccentPurpleLight else BorderSubtle)
                                ) {
                                    Text(
                                        text = "ALL",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAllSelected) Color.White else Color(0xFF94A3B8),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }

                                workout.splits.forEach { split ->
                                    val isSelected = (selectedLapNumber == split.splitNumber)
                                    Surface(
                                        onClick = {
                                            if (isSelected) {
                                                selectedLapNumber = null
                                            } else {
                                                selectedLapNumber = split.splitNumber
                                            }
                                            scrubIndex = 0
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) AccentPurple else Color.White.copy(alpha = 0.05f),
                                        border = BorderStroke(1.dp, if (isSelected) AccentPurpleLight else BorderSubtle)
                                    ) {
                                        Text(
                                            text = "Lap ${split.splitNumber}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Genuine Native OpenStreetMap MapView (Powered by OsmDroid):
 * Dynamic real tile streaming, deep zoom (zoom 4 to 20), high-res DPI scaling,
 * bold hardware-accelerated route polyline, lap isolation with ghost trail, and large markers.
 */
@Composable
fun NativeOsmMapView(
    allPoints: List<TrackPoint>,
    lapPoints: List<TrackPoint>,
    isIsolated: Boolean,
    selectedLapNumber: Int?,
    scrubIndex: Int
) {
    val context = LocalContext.current
    val allGeoPoints = remember(allPoints) {
        allPoints.map { GeoPoint(it.latitude, it.longitude) }
    }
    val lapGeoPoints = remember(lapPoints) {
        lapPoints.map { GeoPoint(it.latitude, it.longitude) }
    }

    if (allGeoPoints.isEmpty() || lapGeoPoints.isEmpty()) return

    val minLat = lapPoints.minOf { it.latitude }
    val maxLat = lapPoints.maxOf { it.latitude }
    val minLon = lapPoints.minOf { it.longitude }
    val maxLon = lapPoints.maxOf { it.longitude }

    var scrubMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Rebuild overlays when isolated lap or route selection changes
    val rebuildOverlays = remember(context, allGeoPoints, lapGeoPoints, isIsolated, selectedLapNumber) {
        { mv: MapView, animatedZoom: Boolean ->
            mv.overlays.clear()

            // Calculate ranges for pace normalization (matching web version)
            val validPaces = lapPoints.mapNotNull { it.paceSecPerKm }.filter { it in 120.0..900.0 }
            val minPace = if (validPaces.isNotEmpty()) validPaces.minOrNull() ?: 240.0 else 240.0
            val maxPace = if (validPaces.isNotEmpty()) validPaces.maxOrNull() ?: 480.0 else 480.0

            // 1. Ghost Trail: Faint full route underneath when isolating a lap
            if (isIsolated) {
                val ghostLine = Polyline().apply {
                    outlinePaint.color = AndroidColor.parseColor("#44FFFFFF")
                    outlinePaint.strokeWidth = 9f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    setPoints(allGeoPoints)
                }
                mv.overlays.add(ghostLine)
            }

            // 2. Dark casing polyline underneath active lap for crisp contrast
            val casingLine = Polyline().apply {
                outlinePaint.color = AndroidColor.parseColor("#99000000")
                outlinePaint.strokeWidth = 24f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                setPoints(lapGeoPoints)
            }
            mv.overlays.add(casingLine)

            // 3. Multi-color Pace Gradient Polyline (Red = Fast, Yellow = Moderate, Green = Slow)
            val coreLine = Polyline().apply {
                val corePaint = Paint().apply {
                    isAntiAlias = true
                    strokeWidth = 14f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    style = Paint.Style.STROKE
                }
                val colorMapping = ColorMapping { index ->
                    val pt = lapPoints.getOrNull(index.coerceIn(0, lapPoints.size - 1))
                    val p = pt?.paceSecPerKm ?: ((minPace + maxPace) / 2.0)
                    getPaceColorInt(p, minPace, maxPace)
                }
                usePath(false)
                outlinePaintLists.add(PolychromaticPaintList(corePaint, colorMapping, true))
                setPoints(lapGeoPoints)
            }
            mv.overlays.add(coreLine)

            // Start Pin (Bold Green with White Border)
            val startMarker = Marker(mv).apply {
                position = lapGeoPoints.first()
                icon = createCirclePinDrawable(context, AndroidColor.parseColor("#00E676"))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = if (isIsolated) "Lap $selectedLapNumber Start" else "Start"
            }
            mv.overlays.add(startMarker)

            // Finish Pin (Bold Red with White Border)
            val endMarker = Marker(mv).apply {
                position = lapGeoPoints.last()
                icon = createCirclePinDrawable(context, AndroidColor.parseColor("#FF1744"))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = if (isIsolated) "Lap $selectedLapNumber Finish" else "Finish"
            }
            mv.overlays.add(endMarker)

            // Active Scrub Marker (Large, high-contrast 36dp pulsing beacon)
            val curScrubGeo = lapGeoPoints.getOrNull(scrubIndex.coerceIn(0, lapGeoPoints.size - 1)) ?: lapGeoPoints.first()
            val scrubMarker = Marker(mv).apply {
                position = curScrubGeo
                icon = createScrubPinDrawable(context)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "Current Position"
            }
            mv.overlays.add(scrubMarker)
            scrubMarkerRef = scrubMarker

            // Automatically center and zoom smoothly to fit the active lap or route
            val dLat = (maxLat - minLat).coerceAtLeast(0.0005)
            val dLon = (maxLon - minLon).coerceAtLeast(0.0005)
            val paddedBox = BoundingBox(
                maxLat + dLat * 0.22,
                maxLon + dLon * 0.22,
                minLat - dLat * 0.22,
                minLon - dLon * 0.22
            )
            mv.post {
                mv.zoomToBoundingBox(paddedBox, animatedZoom)
            }
            mv.invalidate()
        }
    }

    // Trigger overlay rebuild when selected lap changes
    LaunchedEffect(rebuildOverlays) {
        mapViewRef?.let { rebuildOverlays(it, true) }
    }

    // Update live scrub marker position on map as slider drags smoothly
    LaunchedEffect(scrubIndex) {
        val curGeo = lapGeoPoints.getOrNull(scrubIndex.coerceIn(0, lapGeoPoints.size - 1))
        if (curGeo != null && scrubMarkerRef != null) {
            scrubMarkerRef?.position = curGeo
            mapViewRef?.invalidate()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true // Razor sharp high-DPI retina tiles!
                    minZoomLevel = 4.0
                    maxZoomLevel = 20.0
                    isHorizontalMapRepetitionEnabled = false
                    isVerticalMapRepetitionEnabled = false
                    mapViewRef = this
                    rebuildOverlays(this, false)
                }
            },
            update = { /* Overlays are reactively managed via LaunchedEffect */ },
            modifier = Modifier.fillMaxSize()
        )

        // Floating Re-Center Button on map
        Surface(
            onClick = {
                val dLat = (maxLat - minLat).coerceAtLeast(0.0005)
                val dLon = (maxLon - minLon).coerceAtLeast(0.0005)
                val paddedBox = BoundingBox(
                    maxLat + dLat * 0.22,
                    maxLon + dLon * 0.22,
                    minLat - dLat * 0.22,
                    minLon - dLon * 0.22
                )
                mapViewRef?.zoomToBoundingBox(paddedBox, true)
            },
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.8f),
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Fit Route",
                    tint = AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Pure High-Contrast Neon Vector Track Canvas with Per-Lap Isolation
 */
@Composable
fun PureVectorTrackCanvas(
    allPoints: List<TrackPoint>,
    lapPoints: List<TrackPoint>,
    isIsolated: Boolean,
    selectedLapNumber: Int?,
    scrubIndex: Int
) {
    val validAll = allPoints.filter { it.latitude != 0.0 && it.longitude != 0.0 }
    val validLap = lapPoints.filter { it.latitude != 0.0 && it.longitude != 0.0 }
    if (validLap.size < 2 && validAll.size < 2) return
    val activeTrack = if (validLap.size >= 2) validLap else validAll

    val minLat = activeTrack.minOf { it.latitude }
    val maxLat = activeTrack.maxOf { it.latitude }
    val minLon = activeTrack.minOf { it.longitude }
    val maxLon = activeTrack.maxOf { it.longitude }

    val dLat = Math.max(0.00001, maxLat - minLat)
    val dLon = Math.max(0.00001, maxLon - minLon)

    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var zoomScale by remember { mutableFloatStateOf(1.2f) }

    // Reset pan and zoom when lap selection changes
    LaunchedEffect(selectedLapNumber) {
        panOffsetX = 0f
        panOffsetY = 0f
        zoomScale = 1.2f
    }

    val currentPoint = activeTrack.getOrNull(scrubIndex.coerceIn(0, activeTrack.size - 1))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomDelta, _ ->
                    zoomScale = (zoomScale * zoomDelta).coerceIn(0.5f, 4.0f)
                    panOffsetX += pan.x
                    panOffsetY += pan.y
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val w = size.width
            val h = size.height

            val cx = w / 2f + panOffsetX
            val cy = h / 2f + panOffsetY

            // Radar rings
            for (r in 1..4) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.04f),
                    radius = (r * 75f * zoomScale),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1f)
                )
            }

            val meanLatRad = Math.toRadians((minLat + maxLat) / 2.0)
            val cosLat = Math.cos(meanLatRad)
            val geoWidth = (dLon * cosLat).toFloat().coerceAtLeast(0.00001f)
            val geoHeight = dLat.toFloat().coerceAtLeast(0.00001f)
            val baseScale = minOf((w * 0.78f) / geoWidth, (h * 0.78f) / geoHeight) * zoomScale

            fun pointToScreen(pt: TrackPoint): Offset {
                val dx = ((pt.longitude - (minLon + maxLon) / 2.0) * cosLat).toFloat()
                val dy = (((minLat + maxLat) / 2.0 - pt.latitude)).toFloat()
                return Offset(cx + dx * baseScale, cy + dy * baseScale)
            }

            // Pace normalization ranges matching web version
            val validPaces = activeTrack.mapNotNull { it.paceSecPerKm }.filter { it in 120.0..900.0 }
            val minPace = if (validPaces.isNotEmpty()) validPaces.minOrNull() ?: 240.0 else 240.0
            val maxPace = if (validPaces.isNotEmpty()) validPaces.maxOrNull() ?: 480.0 else 480.0

            // 0. Faint Ghost Line of entire route when lap is isolated
            if (isIsolated && validAll.size >= 2) {
                for (i in 0 until validAll.size - 1) {
                    val pos1 = pointToScreen(validAll[i])
                    val pos2 = pointToScreen(validAll[i + 1])
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = pos1,
                        end = pos2,
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 1. High-contrast dark casing underneath for crisp edge separation
            val casingWidth = (8.5.dp * zoomScale).toPx().coerceIn(6f, 18f)
            for (i in 0 until activeTrack.size - 1) {
                val pos1 = pointToScreen(activeTrack[i])
                val pos2 = pointToScreen(activeTrack[i + 1])
                drawLine(
                    color = Color(0xDD000000),
                    start = pos1,
                    end = pos2,
                    strokeWidth = casingWidth,
                    cap = StrokeCap.Round
                )
            }

            // 2. Razor-sharp solid core pace lines (Red = Fast, Green = Slow)
            val coreWidth = (4.5.dp * zoomScale).toPx().coerceIn(3f, 12f)
            for (i in 0 until activeTrack.size - 1) {
                val p1 = activeTrack[i]
                val p2 = activeTrack[i + 1]
                val pos1 = pointToScreen(p1)
                val pos2 = pointToScreen(p2)
                val pace = p1.paceSecPerKm ?: ((minPace + maxPace) / 2.0)
                val color = getPaceColor(pace, minPace, maxPace)

                drawLine(
                    color = color,
                    start = pos1,
                    end = pos2,
                    strokeWidth = coreWidth,
                    cap = StrokeCap.Round
                )
            }

            // Start Pin (Bold Green)
            val startPos = pointToScreen(activeTrack.first())
            drawCircle(color = AccentEmerald, radius = 9.dp.toPx(), center = startPos)
            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = startPos)

            // Finish Pin (Bold Red)
            val finishPos = pointToScreen(activeTrack.last())
            drawCircle(color = AccentRose, radius = 9.dp.toPx(), center = finishPos)
            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = finishPos)

            // Scrub Marker (Bold Purple Beacon)
            if (currentPoint != null) {
                val curPos = pointToScreen(currentPoint)
                drawCircle(color = AccentPurple.copy(alpha = 0.35f), radius = 22.dp.toPx(), center = curPos)
                drawCircle(color = AccentPurple, radius = 11.dp.toPx(), center = curPos)
                drawCircle(color = Color.White, radius = 5.dp.toPx(), center = curPos)
            }
        }
    }
}

/**
 * Standardized telemetry chip with an identical 22x22dp circular icon badge,
 * uniform vertical rhythm, and equalized padding so all metrics look balanced and even.
 */
@Composable
fun TelemetryBadgeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    unit: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(68.dp),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Uniform Header Row: 22x22 circular badge + 10sp label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.4.sp
                )
            }

            // Uniform Value Row: Bold number + small unit
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = valueColor,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

/**
 * Telemetry Curves Canvas with TWO dots on the vertical guideline:
 * 1. Cyan dot rides the Pace curve
 * 2. Rose dot rides the Heart Rate curve
 */
@Composable
fun DualCurvesTelemetryCanvas(
    points: List<TrackPoint>,
    scrubIndex: Int,
    workout: WorkoutSession
) {
    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
        if (points.size < 2) return@Canvas

        val w = size.width
        val h = size.height

        val validPaces = points.mapNotNull { it.paceSecPerKm }.filter { it in 180.0..900.0 }
        val minPace = validPaces.minOrNull() ?: 300.0
        val maxPace = validPaces.maxOrNull() ?: 600.0
        val dPace = Math.max(10.0, maxPace - minPace)

        val hrs = points.mapNotNull { it.heartRateBpm }
        val minHr = hrs.minOrNull() ?: 120
        val maxHr = hrs.maxOrNull() ?: 180
        val dHr = Math.max(10, maxHr - minHr)

        // Horizontal grid guide lines
        for (step in 1..3) {
            val y = h * (step / 4f)
            drawLine(
                color = Color.White.copy(alpha = 0.07f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        // Heart Rate Gradient Area & Curve (AccentRose)
        val hrPath = Path()
        val hrArea = Path()
        var hrStarted = false
        for (i in points.indices) {
            val pt = points[i]
            val x = (i.toFloat() / (points.size - 1)) * w
            val hr = pt.heartRateBpm ?: continue
            val norm = ((hr - minHr).toFloat() / dHr).coerceIn(0.05f, 0.95f)
            val y = h - (norm * (h * 0.76f) + (h * 0.12f))

            if (!hrStarted) {
                hrPath.moveTo(x, y)
                hrArea.moveTo(x, h)
                hrArea.lineTo(x, y)
                hrStarted = true
            } else {
                hrPath.lineTo(x, y)
                hrArea.lineTo(x, y)
            }
        }
        if (hrStarted) {
            hrArea.lineTo(w, h)
            hrArea.close()
            drawPath(
                path = hrArea,
                brush = Brush.verticalGradient(
                    colors = listOf(AccentRose.copy(alpha = 0.28f), AccentRose.copy(alpha = 0.0f)),
                    startY = 0f,
                    endY = h
                )
            )
            drawPath(
                path = hrPath,
                color = AccentRose,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Pace Curve (AccentCyan) - Inverted so faster pace is higher up
        val pacePath = Path()
        var paceStarted = false
        for (i in points.indices) {
            val pt = points[i]
            val x = (i.toFloat() / (points.size - 1)) * w
            val pace = pt.paceSecPerKm ?: continue
            val norm = ((maxPace - pace) / dPace).coerceIn(0.05, 0.95).toFloat()
            val y = h - (norm * (h * 0.76f) + (h * 0.12f))

            if (!paceStarted) {
                pacePath.moveTo(x, y)
                paceStarted = true
            } else {
                pacePath.lineTo(x, y)
            }
        }
        if (paceStarted) {
            drawPath(
                path = pacePath,
                color = AccentCyan,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 1. Vertical Scrubber Guideline (Orange Line)
        val scrubX = (scrubIndex.toFloat() / (points.size - 1)) * w
        drawLine(
            color = AccentOrange.copy(alpha = 0.85f),
            start = Offset(scrubX, 0f),
            end = Offset(scrubX, h),
            strokeWidth = 2.dp.toPx()
        )

        // Calculate exact Y positions of Pace & Heart Rate for the scrub position
        val curPoint = points.getOrNull(scrubIndex.coerceIn(0, points.size - 1))
        val curPace = curPoint?.paceSecPerKm ?: workout.avgPaceSecPerKm
        val normPace = ((maxPace - curPace) / dPace).coerceIn(0.05, 0.95).toFloat()
        val paceY = h - (normPace * (h * 0.76f) + (h * 0.12f))

        val curHr = curPoint?.heartRateBpm ?: workout.avgHeartRate
        val normHr = ((curHr - minHr).toFloat() / dHr).coerceIn(0.05f, 0.95f)
        val hrY = h - (normHr * (h * 0.76f) + (h * 0.12f))

        // DOT 1: Glowing Cyan Dot riding along the Pace Curve
        drawCircle(
            color = AccentCyan.copy(alpha = 0.35f),
            radius = 10.dp.toPx(),
            center = Offset(scrubX, paceY)
        )
        drawCircle(
            color = AccentCyan,
            radius = 6.dp.toPx(),
            center = Offset(scrubX, paceY)
        )
        drawCircle(
            color = Color.White,
            radius = 2.5.dp.toPx(),
            center = Offset(scrubX, paceY)
        )

        // DOT 2: Glowing Rose Dot riding along the Heart Rate Curve
        drawCircle(
            color = AccentRose.copy(alpha = 0.35f),
            radius = 10.dp.toPx(),
            center = Offset(scrubX, hrY)
        )
        drawCircle(
            color = AccentRose,
            radius = 6.dp.toPx(),
            center = Offset(scrubX, hrY)
        )
        drawCircle(
            color = Color.White,
            radius = 2.5.dp.toPx(),
            center = Offset(scrubX, hrY)
        )
    }
}

/**
 * Creates a bold, density-scaled pulsing scrub marker (36dp physical diameter)
 * with an intense orange halo, solid core, and crisp 3dp white border.
 */
private fun createScrubPinDrawable(context: Context): Drawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (36 * density).toInt().coerceAtLeast(72)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f

    // Intense outer purple halo
    val paintHalo = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.parseColor("#66A855F7")
    }
    // Solid vibrant purple core
    val paintCore = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.parseColor("#A855F7")
    }
    // High-contrast crisp white border
    val paintBorder = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    // Center white eye
    val paintWhiteEye = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.WHITE
    }

    canvas.drawCircle(center, center, center - 2f, paintHalo)
    canvas.drawCircle(center, center, center * 0.65f, paintCore)
    canvas.drawCircle(center, center, center * 0.65f, paintBorder)
    canvas.drawCircle(center, center, center * 0.25f, paintWhiteEye)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Creates a bold, density-scaled circle pin (30dp physical diameter)
 * with drop shadow and crisp white border so it pops vividly over street map tiles.
 */
private fun createCirclePinDrawable(context: Context, colorInt: Int): Drawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (30 * density).toInt().coerceAtLeast(60)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f

    // Drop shadow
    val paintShadow = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.parseColor("#44000000")
    }
    canvas.drawCircle(center, center + 2f * density, center * 0.72f, paintShadow)

    // Solid vibrant pin color
    val paintPin = Paint().apply {
        isAntiAlias = true
        color = colorInt
    }
    canvas.drawCircle(center, center, center * 0.72f, paintPin)

    // Crisp thick white border
    val paintBorder = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    canvas.drawCircle(center, center, center * 0.72f, paintBorder)

    // Center white dot
    val paintWhiteDot = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.WHITE
    }
    canvas.drawCircle(center, center, center * 0.26f, paintWhiteDot)

    return BitmapDrawable(context.resources, bitmap)
}
