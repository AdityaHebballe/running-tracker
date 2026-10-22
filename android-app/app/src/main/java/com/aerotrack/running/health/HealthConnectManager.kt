package com.aerotrack.running.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.aerotrack.running.data.HeartRateZoneStats
import com.aerotrack.running.data.SplitInfo
import com.aerotrack.running.data.TrackPoint
import com.aerotrack.running.data.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectManager(private val context: Context) {

    private val TAG = "AeroTrack"

    val healthConnectClient: HealthConnectClient? by lazy {
        try {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing HealthConnectClient", e)
            null
        }
    }

    val permissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ElevationGainedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        "android.permission.health.READ_EXERCISE_ROUTE",
        "android.permission.health.READ_EXERCISE_ROUTES"
    )

    suspend fun hasRequiredPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        // Essential permission: ExerciseSessionRecord
        return granted.contains(HealthPermission.getReadPermission(ExerciseSessionRecord::class))
    }

    /**
     * Reads all exercise sessions from Health Connect, optionally filtered by a specific date.
     */
    suspend fun readWorkouts(
        filterDate: LocalDate? = null,
        daysBack: Long = 90
    ): List<WorkoutSession> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: run {
            Log.w(TAG, "HealthConnectClient is null")
            return@withContext emptyList()
        }

        val sessions = mutableListOf<WorkoutSession>()
        val zoneId = ZoneId.systemDefault()

        try {
            val timeRangeFilter = if (filterDate != null) {
                val startOfDay = filterDate.atStartOfDay(zoneId).toInstant()
                val endOfDay = filterDate.plusDays(1).atStartOfDay(zoneId).toInstant()
                TimeRangeFilter.between(startOfDay, endOfDay)
            } else {
                TimeRangeFilter.after(Instant.now().minus(Duration.ofDays(daysBack)))
            }

            Log.d(TAG, "Querying Health Connect with filter: $timeRangeFilter")

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = timeRangeFilter
                )
            )

            Log.d(TAG, "Health Connect returned ${response.records.size} raw exercise sessions")

            for (record in response.records) {
                Log.i(
                    TAG,
                    "RAW SESSION: id=${record.metadata.id}, origin=${record.metadata.dataOrigin.packageName}, type=${record.exerciseType}, title='${record.title}', start=${record.startTime}, end=${record.endTime}, route=${record.exerciseRouteResult}"
                )

                // Strictly filter for activities explicitly marked as Running
                val isRunning = record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING ||
                                record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL ||
                                record.title?.contains("run", ignoreCase = true) == true

                if (!isRunning) {
                    Log.d(TAG, "Filtering out non-running activity: type=${record.exerciseType}, title=${record.title}")
                    continue
                }

                Log.d(
                    TAG,
                    "Found running record: id=${record.metadata.id}, type=${record.exerciseType}, title=${record.title}, start=${record.startTime}, end=${record.endTime}"
                )
                try {
                    val session = buildWorkoutSession(client, record)
                    sessions.add(session)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing record ${record.metadata.id}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying Health Connect records", e)
        }

        // Return newest first
        sessions.sortedByDescending { it.startTime }
    }

    private suspend fun buildWorkoutSession(
        client: HealthConnectClient,
        sessionRecord: ExerciseSessionRecord
    ): WorkoutSession {
        // Expand time filter by +/- 1 minute to catch bucketed records starting on the minute
        val timeFilterWithBuffer = TimeRangeFilter.between(
            sessionRecord.startTime.minus(Duration.ofMinutes(1)),
            sessionRecord.endTime.plus(Duration.ofMinutes(1))
        )
        val timeFilterStrict = TimeRangeFilter.between(sessionRecord.startTime, sessionRecord.endTime)

        // 1. Read Heart Rate series
        val hrSamples = try {
            val hrRecords = client.readRecords(
                ReadRecordsRequest(HeartRateRecord::class, timeFilterWithBuffer)
            ).records
            hrRecords.flatMap { it.samples }.sortedBy { it.time }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read HeartRateRecords: ${e.message}")
            emptyList()
        }

        // 2. Read GPS Route if available
        // Note: Batch readRecords() omits exercise route data to avoid IPC payload limits.
        // Calling client.readRecord() for the individual record fetches the route or returns ConsentRequired.
        val individualRecord = try {
            client.readRecord(ExerciseSessionRecord::class, sessionRecord.metadata.id).record
        } catch (e: Exception) {
            Log.w(TAG, "Failed single readRecord for ${sessionRecord.metadata.id}: ${e.message}")
            sessionRecord
        }

        val routeResult = individualRecord.exerciseRouteResult
        Log.i(TAG, "individual exerciseRouteResult for ${sessionRecord.metadata.id} = $routeResult")
        var isConsentRequired = false
        val routeLocations: List<ExerciseRoute.Location> = try {
            when (routeResult) {
                is ExerciseRouteResult.Data -> {
                    Log.i(TAG, "Found route with ${routeResult.exerciseRoute.route.size} locations")
                    routeResult.exerciseRoute.route
                }
                is ExerciseRouteResult.ConsentRequired -> {
                    Log.i(TAG, "Route consent required for session ${sessionRecord.metadata.id}")
                    isConsentRequired = true
                    emptyList()
                }
                is ExerciseRouteResult.NoData -> {
                    Log.i(TAG, "No route data in Health Connect for session ${sessionRecord.metadata.id}")
                    emptyList()
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read ExerciseRoute: ${e.message}")
            emptyList()
        }

        // 3. Read Distance Records - FILTER BY DATA ORIGIN to prevent double-counting!
        val distRecordsAll = try {
            client.readRecords(
                ReadRecordsRequest(DistanceRecord::class, timeFilterWithBuffer)
            ).records
        } catch (e: Exception) {
            emptyList()
        }

        for (d in distRecordsAll) {
            Log.i(TAG, "DistanceRecord from ${d.metadata.dataOrigin.packageName}: ${d.distance.inMeters}m (start=${d.startTime}, end=${d.endTime})")
        }

        // Filter distance specifically from the session's data origin (e.g. Fitbit)
        val matchingDistRecords = distRecordsAll.filter {
            it.metadata.dataOrigin.packageName == sessionRecord.metadata.dataOrigin.packageName
        }
        val totalDistanceDirect = if (matchingDistRecords.isNotEmpty()) {
            matchingDistRecords.sumOf { it.distance.inMeters }
        } else if (distRecordsAll.isNotEmpty()) {
            distRecordsAll.groupBy { it.metadata.dataOrigin.packageName }
                .values.maxOf { originList -> originList.sumOf { it.distance.inMeters } }
        } else {
            0.0
        }

        // 4. Read Calories - filter by session origin to prevent double counting
        val calRecordsAll = try {
            client.readRecords(
                ReadRecordsRequest(TotalCaloriesBurnedRecord::class, timeFilterWithBuffer)
            ).records
        } catch (e: Exception) {
            emptyList()
        }

        val matchingCalRecords = calRecordsAll.filter {
            it.metadata.dataOrigin.packageName == sessionRecord.metadata.dataOrigin.packageName
        }
        val totalCaloriesDirect = if (matchingCalRecords.isNotEmpty()) {
            matchingCalRecords.sumOf { it.energy.inKilocalories }.toInt()
        } else if (calRecordsAll.isNotEmpty()) {
            calRecordsAll.groupBy { it.metadata.dataOrigin.packageName }
                .values.maxOf { it.sumOf { c -> c.energy.inKilocalories } }.toInt()
        } else {
            0
        }

        // 5. Read Elevation Gained
        val elevationDirect = try {
            val elevRecords = client.readRecords(
                ReadRecordsRequest(ElevationGainedRecord::class, timeFilterWithBuffer)
            ).records
            elevRecords.sumOf { it.elevation.inMeters }
        } catch (e: Exception) {
            0.0
        }

        // Assemble TrackPoints
        val trackPoints = mutableListOf<TrackPoint>()
        var cumDist = 0.0
        val t0 = sessionRecord.startTime

        if (routeLocations.isNotEmpty()) {
            for (i in routeLocations.indices) {
                val loc = routeLocations[i]
                if (i > 0) {
                    val prev = routeLocations[i - 1]
                    cumDist += haversineDistanceMeters(prev.latitude, prev.longitude, loc.latitude, loc.longitude)
                }

                val matchingHr = hrSamples.minByOrNull {
                    Math.abs(Duration.between(it.time, loc.time).toMillis())
                }?.beatsPerMinute?.toInt()

                val elapsedSec = Duration.between(t0, loc.time).seconds

                trackPoints.add(
                    TrackPoint(
                        time = loc.time,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        altitudeMeters = loc.altitude?.inMeters ?: 0.0,
                        distanceMeters = cumDist,
                        heartRateBpm = matchingHr,
                        elapsedSeconds = elapsedSec
                    )
                )
            }

            // Smooth speed & pace
            for (i in trackPoints.indices) {
                val wStart = Math.max(0, i - 4)
                val wEnd = Math.min(trackPoints.size - 1, i + 4)
                val dDist = trackPoints[wEnd].distanceMeters - trackPoints[wStart].distanceMeters
                val dTime = trackPoints[wEnd].elapsedSeconds - trackPoints[wStart].elapsedSeconds
                val speedMs = if (dTime > 0) dDist / dTime else 0.0
                trackPoints[i] = trackPoints[i].copy(
                    speedKmh = speedMs * 3.6,
                    paceSecPerKm = if (speedMs >= 0.4) 1000.0 / speedMs else null
                )
            }
        } else if (hrSamples.isNotEmpty()) {
            // No GPS, but HR samples exist -> synthesize trackpoints from HR
            val intervalDist = if (totalDistanceDirect > 0) totalDistanceDirect / hrSamples.size else 0.0
            for (i in hrSamples.indices) {
                val s = hrSamples[i]
                trackPoints.add(
                    TrackPoint(
                        time = s.time,
                        latitude = 0.0,
                        longitude = 0.0,
                        distanceMeters = intervalDist * (i + 1),
                        heartRateBpm = s.beatsPerMinute.toInt(),
                        elapsedSeconds = Duration.between(t0, s.time).seconds
                    )
                )
            }
        }

        val totalDist = if (totalDistanceDirect > 0) {
            totalDistanceDirect
        } else if (trackPoints.isNotEmpty()) {
            trackPoints.last().distanceMeters
        } else {
            0.0
        }
        val totalSec = Math.max(1L, Duration.between(sessionRecord.startTime, sessionRecord.endTime).seconds)

        Log.i(TAG, "=== WORKOUT DATA AUDIT ===")
        Log.i(TAG, "routeLocations count = ${routeLocations.size}")
        Log.i(TAG, "cumDist from GPS = ${if (trackPoints.isNotEmpty()) trackPoints.last().distanceMeters else 0.0}")
        Log.i(TAG, "totalDistanceDirect from DistanceRecord = $totalDistanceDirect")
        Log.i(TAG, "totalDist chosen = $totalDist")
        Log.i(TAG, "totalSec = $totalSec")

        val avgPace = if (totalDist > 0) totalSec / (totalDist / 1000.0) else 0.0
        val bestPace = trackPoints.mapNotNull { it.paceSecPerKm }.filter { it in 120.0..900.0 }.minOrNull() ?: avgPace

        Log.i(TAG, "avgPace in seconds/km = $avgPace -> ${formatPace(avgPace)}")
        Log.i(TAG, "avgSpeed km/h = ${(totalDist / 1000.0) / (totalSec / 3600.0)}")
        Log.i(TAG, "==========================")
        val titleText = sessionRecord.title?.trim() ?: ""
        val sportLabel = when {
            sessionRecord.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
            sessionRecord.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Treadmill Run"
            sessionRecord.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
            sessionRecord.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
            titleText.contains("run", ignoreCase = true) -> "Running"
            titleText.contains("walk", ignoreCase = true) -> "Walking"
            titleText.isNotEmpty() -> titleText
            else -> "Workout"
        }

        val splits = calculateSplits(trackPoints, intervalMeters = 1000.0)
        val hrZones = calculateHeartRateZones(
            hrs = if (hrSamples.isNotEmpty()) hrSamples.map { it.beatsPerMinute.toInt() } else trackPoints.mapNotNull { it.heartRateBpm },
            totalSec = totalSec,
            maxHr = 185
        )

        val allHrs = if (hrSamples.isNotEmpty()) hrSamples.map { it.beatsPerMinute.toInt() } else trackPoints.mapNotNull { it.heartRateBpm }
        val avgHr = if (allHrs.isNotEmpty()) allHrs.average().toInt() else 0
        val maxHr = if (allHrs.isNotEmpty()) allHrs.maxOrNull() ?: 0 else 0
        val minHr = if (allHrs.isNotEmpty()) allHrs.minOrNull() ?: 0 else 0

        val alts = trackPoints.map { it.altitudeMeters }.filter { it != 0.0 }
        val minAlt = if (alts.isNotEmpty()) alts.minOrNull() ?: 0.0 else 0.0
        val maxAlt = if (alts.isNotEmpty()) alts.maxOrNull() ?: 0.0 else 0.0

        var gain = elevationDirect
        var loss = 0.0
        if (gain == 0.0 && trackPoints.size > 1) {
            for (i in 1 until trackPoints.size) {
                val d = trackPoints[i].altitudeMeters - trackPoints[i - 1].altitudeMeters
                if (d > 0.4) gain += d else if (d < -0.4) loss += Math.abs(d)
            }
        }

        val calculatedCalories = if (totalCaloriesDirect > 0) {
            totalCaloriesDirect
        } else {
            Math.max(10, ((totalDist / 1000.0) * 75 * 1.036).toInt())
        }

        return WorkoutSession(
            id = sessionRecord.metadata.id,
            title = if (titleText.isNotEmpty()) titleText else "$sportLabel Session",
            sport = sportLabel,
            startTime = sessionRecord.startTime,
            endTime = sessionRecord.endTime,
            totalDistanceMeters = totalDist,
            movingSeconds = totalSec,
            totalElapsedSeconds = totalSec,
            avgPaceSecPerKm = avgPace,
            bestPaceSecPerKm = bestPace,
            avgHeartRate = avgHr,
            maxHeartRate = maxHr,
            minHeartRate = minHr,
            elevationGainMeters = gain,
            elevationLossMeters = loss,
            minAltitudeMeters = minAlt,
            maxAltitudeMeters = maxAlt,
            caloriesKcal = calculatedCalories,
            avgSpeedKmh = if (totalSec > 0) (totalDist / 1000.0) / (totalSec / 3600.0) else 0.0,
            maxSpeedKmh = trackPoints.map { it.speedKmh }.maxOrNull() ?: 0.0,
            trackPoints = trackPoints,
            splits = splits,
            hrZones = hrZones,
            isRouteConsentRequired = isConsentRequired
        )
    }

    fun enrichSessionWithRoute(session: WorkoutSession, exerciseRoute: ExerciseRoute): WorkoutSession {
        val routeLocations = exerciseRoute.route
        if (routeLocations.isEmpty()) return session

        val trackPoints = mutableListOf<TrackPoint>()
        var cumDist = 0.0
        val t0 = session.startTime

        for (i in routeLocations.indices) {
            val loc = routeLocations[i]
            if (i > 0) {
                val prev = routeLocations[i - 1]
                cumDist += haversineDistanceMeters(prev.latitude, prev.longitude, loc.latitude, loc.longitude)
            }
            val elapsedSec = Duration.between(t0, loc.time).seconds
            val matchingHr = session.trackPoints.minByOrNull {
                Math.abs(Duration.between(it.time, loc.time).toMillis())
            }?.heartRateBpm

            trackPoints.add(
                TrackPoint(
                    time = loc.time,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitudeMeters = loc.altitude?.inMeters ?: 0.0,
                    distanceMeters = cumDist,
                    heartRateBpm = matchingHr,
                    elapsedSeconds = elapsedSec
                )
            )
        }

        // Smooth speed & pace
        for (i in trackPoints.indices) {
            val wStart = Math.max(0, i - 4)
            val wEnd = Math.min(trackPoints.size - 1, i + 4)
            val dDist = trackPoints[wEnd].distanceMeters - trackPoints[wStart].distanceMeters
            val dTime = trackPoints[wEnd].elapsedSeconds - trackPoints[wStart].elapsedSeconds
            val speedMs = if (dTime > 0) dDist / dTime else 0.0
            trackPoints[i] = trackPoints[i].copy(
                speedKmh = speedMs * 3.6,
                paceSecPerKm = if (speedMs >= 0.4) 1000.0 / speedMs else null
            )
        }

        val splits = com.aerotrack.running.data.TcxParser.detectLoopLaps(trackPoints).ifEmpty {
            calculateSplits(trackPoints, intervalMeters = 1000.0)
        }

        val totalDist = if (cumDist > 0) cumDist else session.totalDistanceMeters
        val totalSec = session.totalElapsedSeconds
        val avgPace = if (totalDist > 0 && totalSec > 0) totalSec / (totalDist / 1000.0) else session.avgPaceSecPerKm
        val bestPace = trackPoints.mapNotNull { it.paceSecPerKm }.filter { it in 120.0..900.0 }.minOrNull() ?: avgPace

        return session.copy(
            trackPoints = trackPoints,
            splits = splits,
            totalDistanceMeters = totalDist,
            avgPaceSecPerKm = avgPace,
            bestPaceSecPerKm = bestPace,
            isRouteConsentRequired = false
        )
    }

    private fun calculateSplits(pts: List<TrackPoint>, intervalMeters: Double): List<SplitInfo> {
        if (pts.isEmpty()) return emptyList()
        val splits = mutableListOf<SplitInfo>()
        var startIndex = 0
        var splitNum = 1
        var nextThreshold = intervalMeters

        for (i in pts.indices) {
            val isLast = i == pts.size - 1
            if (pts[i].distanceMeters >= nextThreshold || (isLast && i > startIndex)) {
                val startP = pts[startIndex]
                val endP = pts[i]
                val dDist = endP.distanceMeters - startP.distanceMeters
                val dTime = Math.max(1L, endP.elapsedSeconds - startP.elapsedSeconds)
                val pace = if (dDist > 0) dTime / (dDist / 1000.0) else 0.0

                val slice = pts.subList(startIndex, i + 1)
                val hrs = slice.mapNotNull { it.heartRateBpm }.filter { it > 40 }
                val avgHr = if (hrs.isNotEmpty()) hrs.average().toInt() else 0
                val maxHr = if (hrs.isNotEmpty()) hrs.maxOrNull() ?: 0 else 0

                var gain = 0.0
                var loss = 0.0
                for (k in 1 until slice.size) {
                    val d = slice[k].altitudeMeters - slice[k - 1].altitudeMeters
                    if (d > 0) gain += d else loss += Math.abs(d)
                }

                splits.add(
                    SplitInfo(
                        splitNumber = splitNum,
                        distanceMeters = dDist,
                        durationSeconds = dTime,
                        avgPaceSecPerKm = pace,
                        bestPaceSecPerKm = slice.mapNotNull { it.paceSecPerKm }.minOrNull() ?: pace,
                        avgHeartRate = avgHr,
                        maxHeartRate = maxHr,
                        elevationGainMeters = gain,
                        elevationLossMeters = loss
                    )
                )

                startIndex = i
                splitNum++
                nextThreshold += intervalMeters
            }
        }
        return splits
    }

    private fun calculateHeartRateZones(hrs: List<Int>, totalSec: Long, maxHr: Int): List<HeartRateZoneStats> {
        val zoneCounts = IntArray(6)
        val validHrs = hrs.filter { it > 40 }
        for (hr in validHrs) {
            val pct = hr.toDouble() / maxHr
            val zone = when {
                pct >= 0.90 -> 5
                pct >= 0.80 -> 4
                pct >= 0.70 -> 3
                pct >= 0.60 -> 2
                else -> 1
            }
            zoneCounts[zone]++
        }

        val totalSamples = Math.max(1, validHrs.size)
        val results = mutableListOf<HeartRateZoneStats>()
        val zoneNames = arrayOf("", "Warm Up", "Fat Burn", "Aerobic", "Threshold", "Peak")
        val zoneRanges = arrayOf(
            Pair(0, 0),
            Pair(0, (maxHr * 0.60).toInt()),
            Pair((maxHr * 0.60).toInt(), (maxHr * 0.70).toInt()),
            Pair((maxHr * 0.70).toInt(), (maxHr * 0.80).toInt()),
            Pair((maxHr * 0.80).toInt(), (maxHr * 0.90).toInt()),
            Pair((maxHr * 0.90).toInt(), maxHr)
        )

        for (z in 5 downTo 1) {
            val pct = ((zoneCounts[z].toDouble() / totalSamples) * 100).toInt()
            val sec = ((totalSec * zoneCounts[z]) / totalSamples)
            results.add(
                HeartRateZoneStats(
                    zoneNumber = z,
                    name = zoneNames[z],
                    minBpm = zoneRanges[z].first,
                    maxBpm = zoneRanges[z].second,
                    durationSeconds = sec,
                    percentage = pct
                )
            )
        }

        return results
    }

    private fun formatPace(secPerKm: Double): String {
        val m = (secPerKm / 60).toInt()
        val s = (secPerKm % 60).toInt()
        return "$m:${if (s < 10) "0" else ""}$s /km"
    }

    private fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
