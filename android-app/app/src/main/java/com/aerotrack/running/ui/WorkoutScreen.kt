package com.aerotrack.running.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aerotrack.running.data.SplitInfo
import com.aerotrack.running.data.TrackPoint
import com.aerotrack.running.data.WorkoutSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    workout: WorkoutSession,
    allWorkouts: List<WorkoutSession>,
    selectedIndex: Int,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate?) -> Unit,
    onSelectWorkout: (Int) -> Unit,
    onOpenStudio: () -> Unit,
    onRefreshHealthConnect: () -> Unit,
    onRequestRouteConsent: ((String) -> Unit)? = null,
    onImportTcx: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceCard)
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = "Pick Date",
                            tint = AccentPurpleLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val dateLabel = if (selectedDate != null) {
                            selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                        } else {
                            "All Workouts"
                        }
                        Text(
                            text = dateLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8)
                        )
                    }
                },
                actions = {
                    // Import TCX / GPX button
                    if (onImportTcx != null) {
                        IconButton(
                            onClick = onImportTcx,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SurfaceCard)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = "Import TCX File",
                                tint = AccentCyan
                            )
                        }
                    }
                    // Sync Health Connect button
                    IconButton(
                        onClick = onRefreshHealthConnect,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync Health Connect",
                            tint = AccentPurpleLight
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick Date Filter Pills
            item {
                QuickDateFilterRow(
                    selectedDate = selectedDate,
                    onSelectDate = onSelectDate
                )
            }

            // Exercise Session Carousel (if multiple on this day or in filter)
            if (allWorkouts.size > 1) {
                item {
                    Text(
                        text = "RUNNING WORKOUTS (${allWorkouts.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(allWorkouts) { idx, w ->
                            val isSelected = idx == selectedIndex
                            ExerciseChip(
                                workout = w,
                                isSelected = isSelected,
                                onClick = { onSelectWorkout(idx) }
                            )
                        }
                    }
                }
            }

            // 1. Hero Editorial Display (Distance, Pace, Duration, Heart Rate - Calories Removed)
            item {
                HeroEditorialCard(workout)
            }

            // Route Consent Required Action Card
            if (workout.isRouteConsentRequired) {
                item {
                    RouteConsentCard(onClick = { onRequestRouteConsent?.invoke(workout.id) })
                }
            }

            // 2. Dedicated Route & Telemetry Studio Launcher Card
            if (workout.trackPoints.any { it.latitude != 0.0 }) {
                item {
                    RouteStudioLauncherCard(
                        workout = workout,
                        onOpenStudio = onOpenStudio
                    )
                }
            } else if (!workout.isRouteConsentRequired) {
                // Helpful card when Health Connect didn't receive GPS data
                item {
                    NoGpsRouteCard(
                        onRequestRouteConsent = { onRequestRouteConsent?.invoke(workout.id) },
                        onImportTcx = onImportTcx
                    )
                }
            }

            // 3. Loop Laps Breakdown (Running in circles)
            if (workout.splits.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Loop Laps Breakdown",
                        badge = "${workout.splits.size} Laps Detected"
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Auto-detected by loop return to start coordinate (~250m per lap)",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                items(workout.splits) { split ->
                    LoopLapCard(split = split, overallAvgPace = workout.avgPaceSecPerKm)
                }
            }

            // 4. Heart Rate Zones
            if (workout.hrZones.isNotEmpty()) {
                item {
                    SectionHeader(title = "Heart Rate Intensity Zones", badge = "${workout.avgHeartRate} BPM Avg")
                    Spacer(modifier = Modifier.height(8.dp))
                    HrZonesExpressiveCard(workout)
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Material 3 Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        onSelectDate(picked)
                    }
                }) {
                    Text("Select Date", color = AccentPurpleLight, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    onSelectDate(null)
                }) {
                    Text("Show All", color = Color(0xFF94A3B8))
                }
            },
            colors = DatePickerDefaults.colors(containerColor = SurfaceCard)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    titleContentColor = Color.White,
                    headlineContentColor = Color.White,
                    weekdayContentColor = AccentPurpleLight,
                    subheadContentColor = Color.White,
                    yearContentColor = Color.White,
                    currentYearContentColor = AccentPurpleLight,
                    selectedYearContentColor = Color.White,
                    selectedYearContainerColor = AccentPurple,
                    dayContentColor = Color.White,
                    selectedDayContentColor = Color.White,
                    selectedDayContainerColor = AccentPurple,
                    todayContentColor = AccentPurpleLight,
                    todayDateBorderColor = AccentPurpleLight
                )
            )
        }
    }
}

@Composable
fun QuickDateFilterRow(
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate?) -> Unit
) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedDate == null,
            onClick = { onSelectDate(null) },
            label = { Text("All Recent", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentOrange,
                selectedLabelColor = Color.White
            )
        )
        FilterChip(
            selected = selectedDate == today,
            onClick = { onSelectDate(today) },
            label = { Text("Today", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentOrange,
                selectedLabelColor = Color.White
            )
        )
        FilterChip(
            selected = selectedDate == yesterday,
            onClick = { onSelectDate(yesterday) },
            label = { Text("Yesterday", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentOrange,
                selectedLabelColor = Color.White
            )
        )
        if (selectedDate != null && selectedDate != today && selectedDate != yesterday) {
            FilterChip(
                selected = true,
                onClick = { /* selected */ },
                label = {
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("MMM d")),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentOrange,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
fun ExerciseChip(
    workout: WorkoutSession,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val timeLabel = workout.startTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
    val km = String.format("%.2f km", workout.totalDistanceMeters / 1000.0)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) AccentPurple.copy(alpha = 0.2f) else SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isSelected) AccentPurple else BorderSubtle
        ),
        modifier = Modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsRun,
                contentDescription = null,
                tint = if (isSelected) AccentPurpleLight else Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "$timeLabel • $km",
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
            )
        }
    }
}

@Composable
fun HeroEditorialCard(workout: WorkoutSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = AccentPurple.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = workout.sport.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentPurpleLight,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = DateTimeFormatter.ofPattern("MMM d • h:mm a").format(
                        workout.startTime.atZone(ZoneId.systemDefault())
                    ),
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Giant Distance Typography
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format("%.2f", workout.totalDistanceMeters / 1000.0),
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    color = Color.White,
                    letterSpacing = (-1.5).sp,
                    lineHeight = 54.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "KM",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentPurpleLight
                )
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "MOVING TIME",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = formatTime(workout.movingSeconds),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Pace & Heart Rate Stats Ribbon (Elevation & Calories removed - 100% measured real stats)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.04f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "AVG PACE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        val m = (workout.avgPaceSecPerKm / 60).toInt()
                        val s = (workout.avgPaceSecPerKm % 60).toInt()
                        Text(text = "$m:${if (s < 10) "0" else ""}$s /km", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = AccentCyan)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderSubtle))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "HEART RATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        Text(text = "${workout.avgHeartRate} bpm", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AccentRose)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderSubtle))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "BEST PACE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        val bm = (workout.bestPaceSecPerKm / 60).toInt()
                        val bs = (workout.bestPaceSecPerKm % 60).toInt()
                        Text(text = "$bm:${if (bs < 10) "0" else ""}$bs /km", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = AccentEmerald)
                    }
                }
            }
        }
    }
}

@Composable
fun RouteStudioLauncherCard(
    workout: WorkoutSession,
    onOpenStudio: () -> Unit
) {
    val validPoints = workout.trackPoints.filter { it.latitude != 0.0 && it.longitude != 0.0 }

    Card(
        onClick = onOpenStudio,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentPurple.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AccentPurple.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = AccentPurpleLight,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "ROUTE & TELEMETRY STUDIO",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = AccentPurpleLight
                    )
                    Text(
                        text = "OpenStreetMap • Live Scrubber",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stylized Route Preview Canvas with Launch CTA Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                MiniRouteCanvas(points = validPoints)

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xDD000000),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurpleLight)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = null,
                            tint = AccentPurpleLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Full-Screen Studio ➔",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniRouteCanvas(points: List<TrackPoint>) {
    val valid = points.filter { it.latitude != 0.0 && it.longitude != 0.0 }
    if (valid.size < 2) return

    val minLat = valid.minOf { it.latitude }
    val maxLat = valid.maxOf { it.latitude }
    val minLon = valid.minOf { it.longitude }
    val maxLon = valid.maxOf { it.longitude }

    val dLat = Math.max(0.00001, maxLat - minLat)
    val dLon = Math.max(0.00001, maxLon - minLon)

    val validPaces = valid.mapNotNull { it.paceSecPerKm }.filter { it in 120.0..900.0 }
    val minPace = if (validPaces.isNotEmpty()) validPaces.minOrNull() ?: 240.0 else 240.0
    val maxPace = if (validPaces.isNotEmpty()) validPaces.maxOrNull() ?: 480.0 else 480.0

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val w = size.width
        val h = size.height

        for (i in 0 until valid.size - 1) {
            val pt1 = valid[i]
            val pt2 = valid[i + 1]
            val x1 = (((pt1.longitude - minLon) / dLon) * w).toFloat()
            val y1 = (h - (((pt1.latitude - minLat) / dLat) * h)).toFloat()
            val x2 = (((pt2.longitude - minLon) / dLon) * w).toFloat()
            val y2 = (h - (((pt2.latitude - minLat) / dLat) * h)).toFloat()
            val color = getPaceColor(pt1.paceSecPerKm ?: ((minPace + maxPace) / 2.0), minPace, maxPace)

            // Glow line
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Core colored line
            drawLine(
                color = color,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun GpsInteractiveMapCard(
    points: List<TrackPoint>,
    scrubIndex: Int
) {
    val validPoints = points.filter { it.latitude != 0.0 && it.longitude != 0.0 }
    if (validPoints.size < 2) return

    val minLat = validPoints.minOf { it.latitude }
    val maxLat = validPoints.maxOf { it.latitude }
    val minLon = validPoints.minOf { it.longitude }
    val maxLon = validPoints.maxOf { it.longitude }

    val dLat = Math.max(0.00001, maxLat - minLat)
    val dLon = Math.max(0.00001, maxLon - minLon)

    val validPaces = validPoints.mapNotNull { it.paceSecPerKm }.filter { it in 120.0..900.0 }
    val minPace = if (validPaces.isNotEmpty()) validPaces.minOrNull() ?: 240.0 else 240.0
    val maxPace = if (validPaces.isNotEmpty()) validPaces.maxOrNull() ?: 480.0 else 480.0

    val currentPoint = validPoints[scrubIndex.coerceIn(0, validPoints.size - 1)]

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Real GPS Track Canvas with Pace-Colored Segments
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                val w = size.width
                val h = size.height

                for (i in 0 until validPoints.size - 1) {
                    val pt1 = validPoints[i]
                    val pt2 = validPoints[i + 1]
                    val x1 = ((pt1.longitude - minLon) / dLon * w).toFloat()
                    val y1 = ((maxLat - pt1.latitude) / dLat * h).toFloat()
                    val x2 = ((pt2.longitude - minLon) / dLon * w).toFloat()
                    val y2 = ((maxLat - pt2.latitude) / dLat * h).toFloat()
                    val color = getPaceColor(pt1.paceSecPerKm ?: ((minPace + maxPace) / 2.0), minPace, maxPace)

                    // Ambient glow stroke
                    drawLine(
                        color = color.copy(alpha = 0.35f),
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 12f,
                        cap = StrokeCap.Round
                    )
                    // Neon pace-colored route line
                    drawLine(
                        color = color,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )
                }

                // Start Coordinate (Green)
                val pStart = validPoints.first()
                val sx = ((pStart.longitude - minLon) / dLon * w).toFloat()
                val sy = ((maxLat - pStart.latitude) / dLat * h).toFloat()
                drawCircle(color = AccentEmerald, radius = 9f, center = Offset(sx, sy))
                drawCircle(color = Color.White, radius = 4f, center = Offset(sx, sy))

                // Finish Coordinate (Red)
                val pEnd = validPoints.last()
                val ex = ((pEnd.longitude - minLon) / dLon * w).toFloat()
                val ey = ((maxLat - pEnd.latitude) / dLat * h).toFloat()
                drawCircle(color = AccentRose, radius = 9f, center = Offset(ex, ey))
                drawCircle(color = Color.White, radius = 4f, center = Offset(ex, ey))

                // Active Scrubber Marker on Map
                val cx = ((currentPoint.longitude - minLon) / dLon * w).toFloat()
                val cy = ((maxLat - currentPoint.latitude) / dLat * h).toFloat()

                // Pulsing outer glow
                drawCircle(color = AccentPurple.copy(alpha = 0.4f), radius = 18f, center = Offset(cx, cy))
                // Marker core
                drawCircle(color = AccentPurple, radius = 10f, center = Offset(cx, cy))
                drawCircle(color = Color.White, radius = 5f, center = Offset(cx, cy))
            }

            // Live Floating HUD Overlay on the Map
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
                shape = RoundedCornerShape(16.dp),
                color = DarkBg.copy(alpha = 0.88f),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = "Time: ${formatTime(currentPoint.elapsedSeconds)} • ${(currentPoint.distanceMeters / 1000.0).let { String.format("%.2f", it) }} km",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val paceSec = currentPoint.paceSecPerKm ?: 470.0
                        val pm = (paceSec / 60.0).toInt()
                        val ps = (paceSec % 60.0).toInt()
                        Text(
                            text = "$pm:${if (ps < 10) "0" else ""}$ps /km",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AccentCyan
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${currentPoint.heartRateBpm ?: "--"} bpm",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentRose
                        )
                    }
                }
            }

            // Map legend at bottom with Pace gradient
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AccentEmerald))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Start", fontSize = 10.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AccentRose))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Finish", fontSize = 10.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.width(10.dp))
                // Pace Heat gradient bar (Red = Fast, Green = Slow)
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(Color(0xFFEF4444), Color(0xFFFBBF24), Color(0xFF10B981))
                            )
                        )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Pace", fontSize = 10.sp, color = Color(0xFFCBD5E1), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SlidingTelemetryCard(
    points: List<TrackPoint>,
    scrubIndex: Int,
    onScrub: (Int) -> Unit
) {
    if (points.isEmpty()) return
    val currentPoint = points[scrubIndex.coerceIn(0, points.size - 1)]

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Live Scrub Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "PACE AT MARKER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    val paceSec = currentPoint.paceSecPerKm ?: 470.0
                    val pm = (paceSec / 60).toInt()
                    val ps = (paceSec % 60).toInt()
                    Text(text = "$pm:${if (ps < 10) "0" else ""}$ps /km", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = AccentCyan)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "DISTANCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    Text(text = "${String.format("%.2f", currentPoint.distanceMeters / 1000.0)} km", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "HEART RATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    Text(text = "${currentPoint.heartRateBpm ?: "--"} bpm", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = AccentRose)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Synchronized Dual-Curve Canvas (Pace Top, Heart Rate Bottom)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                            val idx = (ratio * (points.size - 1)).toInt()
                            onScrub(idx)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                            val idx = (ratio * (points.size - 1)).toInt()
                            onScrub(idx)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    val validPaces = points.mapNotNull { it.paceSecPerKm }.filter { it in 150.0..800.0 }
                    val minP = if (validPaces.isNotEmpty()) validPaces.minOrNull() ?: 300.0 else 300.0
                    val maxP = if (validPaces.isNotEmpty()) validPaces.maxOrNull() ?: 600.0 else 600.0
                    val dP = Math.max(1.0, maxP - minP)

                    val validHrs = points.mapNotNull { it.heartRateBpm }.filter { it > 50 }
                    val minH = if (validHrs.isNotEmpty()) validHrs.minOrNull() ?: 110 else 110
                    val maxH = if (validHrs.isNotEmpty()) validHrs.maxOrNull() ?: 185 else 185
                    val dH = Math.max(1, maxH - minH)

                    // 1. Draw Pace Line (Inverted: faster/smaller pace is higher up!)
                    val pacePath = Path()
                    points.forEachIndexed { i, p ->
                        val x = (i.toFloat() / (points.size - 1)) * w
                        val pace = (p.paceSecPerKm ?: ((minP + maxP) / 2)).coerceIn(minP, maxP)
                        // Inverted Y: 0 is top (fast), h*0.5 is bottom
                        val y = (((pace - minP) / dP) * (h * 0.45f)).toFloat()
                        if (i == 0) pacePath.moveTo(x, y) else pacePath.lineTo(x, y)
                    }

                    drawPath(
                        path = pacePath,
                        color = AccentCyan,
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // 2. Draw Heart Rate Line (Bottom half)
                    val hrPath = Path()
                    points.forEachIndexed { i, p ->
                        val x = (i.toFloat() / (points.size - 1)) * w
                        val hr = (p.heartRateBpm ?: minH).coerceIn(minH, maxH)
                        // HR Y: from h*0.55 to h
                        val y = (h - ((hr - minH).toFloat() / dH * (h * 0.40f)))
                        if (i == 0) hrPath.moveTo(x, y) else hrPath.lineTo(x, y)
                    }

                    drawPath(
                        path = hrPath,
                        color = AccentRose,
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // 3. Sliding Scrubber Vertical Cursor
                    val cursorX = (scrubIndex.toFloat() / (points.size - 1)) * w
                    drawLine(
                        color = AccentPurple,
                        start = Offset(cursorX, 0f),
                        end = Offset(cursorX, h),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )

                    // Scrubber point knobs
                    drawCircle(color = AccentPurple, radius = 6f, center = Offset(cursorX, 0f))
                    drawCircle(color = AccentPurple, radius = 6f, center = Offset(cursorX, h))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Slider track to drag smoothly with finger
            Slider(
                value = scrubIndex.toFloat(),
                onValueChange = { onScrub(it.toInt()) },
                valueRange = 0f..(points.size - 1).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = AccentPurple,
                    activeTrackColor = AccentPurple,
                    inactiveTrackColor = AccentPurpleMuted.copy(alpha = 0.4f)
                )
            )
        }
    }
}

@Composable
fun LoopLapCard(split: SplitInfo, overallAvgPace: Double) {
    val isFaster = split.avgPaceSecPerKm <= overallAvgPace

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isFaster) AccentPurple.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "L${split.splitNumber}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = if (isFaster) AccentPurpleLight else Color.White
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "Lap ${split.splitNumber} • ${split.distanceMeters.toInt()}m loop",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Text(
                    text = "${formatTime(split.durationSeconds)} duration",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.End) {
                val m = (split.avgPaceSecPerKm / 60).toInt()
                val s = (split.avgPaceSecPerKm % 60).toInt()
                Text(
                    text = "$m:${if (s < 10) "0" else ""}$s /km",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isFaster) AccentPurpleLight else AccentPurple
                )
                Text(
                    text = "${split.avgHeartRate} bpm",
                    fontSize = 11.sp,
                    color = AccentRose,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun HrZonesExpressiveCard(workout: WorkoutSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Segmented Multi-zone Color Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                workout.hrZones.reversed().forEach { z ->
                    if (z.percentage > 0) {
                        val color = getZoneColor(z.zoneNumber)
                        Box(
                            modifier = Modifier
                                .weight(Math.max(1f, z.percentage.toFloat()))
                                .fillMaxHeight()
                                .background(color)
                        )
                    }
                }
            }

            workout.hrZones.forEach { zone ->
                val color = getZoneColor(zone.zoneNumber)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Zone ${zone.zoneNumber} • ${zone.name}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${formatTime(zone.durationSeconds)}  (${zone.percentage}%)",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, badge: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.08f)
        ) {
            Text(
                text = badge,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }
    }
}

fun getZoneColor(zoneNumber: Int): Color {
    return when (zoneNumber) {
        5 -> ZonePeak
        4 -> ZoneThreshold
        3 -> ZoneAerobic
        2 -> ZoneFatBurn
        else -> ZoneWarmup
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatGptBottomSheet(
    workout: WorkoutSession,
    onDismiss: () -> Unit,
    onLaunch: (String) -> Unit
) {
    val prompt = remember(workout) {
        val m = (workout.avgPaceSecPerKm / 60).toInt()
        val s = (workout.avgPaceSecPerKm % 60).toInt()
        val bm = (workout.bestPaceSecPerKm / 60).toInt()
        val bs = (workout.bestPaceSecPerKm % 60).toInt()

        """
        You are an elite exercise physiologist and Olympic running coach. Here is my running workout telemetry:
        
        • Date: ${workout.startTime}
        • Activity: ${workout.title} (${workout.sport})
        • Total Distance: ${String.format("%.2f", workout.totalDistanceMeters / 1000.0)} km
        • Moving Time: ${formatTime(workout.movingSeconds)} (Elapsed: ${formatTime(workout.totalElapsedSeconds)})
        • Average Pace: $m:${if (s < 10) "0" else ""}$s /km (Best: $bm:${if (bs < 10) "0" else ""}$bs /km)
        • Average Heart Rate: ${workout.avgHeartRate} bpm (Max: ${workout.maxHeartRate} bpm, Min: ${workout.minHeartRate} bpm)
        • Active Calories: ${workout.caloriesKcal} kcal
        
        Loop Laps (~250m circle laps):
        ${workout.splits.joinToString("\n") { "• Lap ${it.splitNumber}: ${it.distanceMeters.toInt()}m in ${formatTime(it.durationSeconds)}, pace ${(it.avgPaceSecPerKm / 60).toInt()}:${((it.avgPaceSecPerKm % 60).toInt()).toString().padStart(2, '0')}/km, HR ${it.avgHeartRate} bpm" }}
        
        Heart Rate Zones:
        ${workout.hrZones.joinToString("\n") { "- Zone ${it.zoneNumber} (${it.name}): ${formatTime(it.durationSeconds)} (${it.percentage}%)" }}
        
        COACHING ANALYSIS QUESTIONS:
        1. Lap Rhythm: Analyze my pacing degradation across Laps 1 to 5.
        2. Cardiac Drift: Assess how my heart rate ramped up from Lap 1 to Lap 5.
        3. Interval Strategy: What should my target loop time be for interval training?
        """.trimIndent()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AccentPurple.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = AccentPurpleLight, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "Ask AI Running Coach", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(text = "Instant ChatGPT analysis of your laps, rhythm, and cardiac drift", fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.05f)
            ) {
                Text(
                    text = prompt.take(300) + "\n...",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Button(
                onClick = { onLaunch(prompt) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
            ) {
                Text(
                    text = "Copy & Open ChatGPT ➔",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }
    }
}

fun launchChatGpt(context: Context, prompt: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Workout Telemetry", prompt)
    clipboard.setPrimaryClip(clip)

    Toast.makeText(context, "Workout data copied! Launching ChatGPT...", Toast.LENGTH_SHORT).show()

    val encoded = Uri.encode(prompt.take(1500))
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/?q=$encoded"))
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/")))
    }
}

fun formatTime(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return "$m:${if (s < 10) "0" else ""}$s"
}

@Composable
fun RouteConsentCard(
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = AccentPurple.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, AccentPurpleLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccentPurple.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Grant Route Access",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Health Connect requires your permission to view the GPS route for this run. Tap to grant.",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = AccentPurpleLight
            )
        }
    }
}

@Composable
fun NoGpsRouteCard(
    onRequestRouteConsent: (() -> Unit)?,
    onImportTcx: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "No GPS Track Available",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Fitbit / Google Health synced distance and heart rate, but did not export the GPS route into Health Connect. Tap 'Import TCX' to load your workout route.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onRequestRouteConsent != null) {
                    OutlinedButton(
                        onClick = onRequestRouteConsent,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AccentPurpleLight),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Check Route", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                if (onImportTcx != null) {
                    Button(
                        onClick = onImportTcx,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import TCX", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
