package com.aerotrack.running.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Duration
import java.time.Instant

object TcxParser {

    fun parse(inputStream: InputStream): WorkoutSession {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        val points = mutableListOf<TrackPoint>()
        var sport = "Running"
        var startTime: Instant? = null
        var endTime: Instant? = null
        var totalCalories = 0

        var eventType = parser.eventType
        var currentTag = ""

        var curTime: Instant? = null
        var curLat: Double? = null
        var curLon: Double? = null
        var curDist: Double? = null
        var curHr: Int? = null

        var insideTrackpoint = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag.equals("Activity", ignoreCase = true)) {
                        sport = parser.getAttributeValue(null, "Sport") ?: "Running"
                    } else if (currentTag.equals("Trackpoint", ignoreCase = true)) {
                        insideTrackpoint = true
                        curTime = null
                        curLat = null
                        curLon = null
                        curDist = null
                        curHr = null
                    }
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (text.isNotEmpty()) {
                        when (currentTag) {
                            "Time" -> if (insideTrackpoint && curTime == null) {
                                try {
                                    curTime = Instant.parse(text)
                                    if (startTime == null) startTime = curTime
                                    endTime = curTime
                                } catch (_: Exception) {}
                            }
                            "LatitudeDegrees" -> if (insideTrackpoint) curLat = text.toDoubleOrNull()
                            "LongitudeDegrees" -> if (insideTrackpoint) curLon = text.toDoubleOrNull()
                            "DistanceMeters" -> if (insideTrackpoint) curDist = text.toDoubleOrNull()
                            "Value" -> if (insideTrackpoint && curHr == null) curHr = text.toIntOrNull()
                            "Calories" -> if (totalCalories == 0) totalCalories = text.toIntOrNull() ?: 0
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("Trackpoint", ignoreCase = true)) {
                        insideTrackpoint = false
                        if (curLat != null && curLon != null) {
                            val t0 = startTime ?: curTime ?: Instant.now()
                            val elap = if (curTime != null) Duration.between(t0, curTime).seconds else points.size.toLong()
                            points.add(
                                TrackPoint(
                                    time = curTime ?: Instant.now(),
                                    latitude = curLat,
                                    longitude = curLon,
                                    altitudeMeters = 0.0, // Elevation removed as requested
                                    distanceMeters = curDist ?: 0.0,
                                    heartRateBpm = curHr,
                                    elapsedSeconds = elap
                                )
                            )
                        }
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        val tStart = startTime ?: (if (points.isNotEmpty()) points.first().time else Instant.now())
        val tEnd = endTime ?: (if (points.isNotEmpty()) points.last().time else tStart)
        val totalSec = Math.max(1L, Duration.between(tStart, tEnd).seconds)
        val totalDist = if (points.isNotEmpty()) points.last().distanceMeters else 0.0

        // Rolling speed & pace
        for (i in points.indices) {
            val wStart = Math.max(0, i - 4)
            val wEnd = Math.min(points.size - 1, i + 4)
            val dDist = points[wEnd].distanceMeters - points[wStart].distanceMeters
            val dTime = points[wEnd].elapsedSeconds - points[wStart].elapsedSeconds
            val speedMs = if (dTime > 0) dDist / dTime.toDouble() else 0.0
            points[i] = points[i].copy(
                speedKmh = speedMs * 3.6,
                paceSecPerKm = if (speedMs >= 0.4) 1000.0 / speedMs else null
            )
        }

        // Detect Laps by Loop Closure (Running in circles)
        val loopLaps = detectLoopLaps(points)

        // Heart Rate Zones
        val validHrs = points.mapNotNull { it.heartRateBpm }.filter { it > 40 }
        val avgHr = if (validHrs.isNotEmpty()) validHrs.average().toInt() else 0
        val maxHr = if (validHrs.isNotEmpty()) validHrs.maxOrNull() ?: 0 else 0
        val minHr = if (validHrs.isNotEmpty()) validHrs.minOrNull() ?: 0 else 0

        val maxExpectedHr = 185
        val zoneCounts = IntArray(6)
        for (hr in validHrs) {
            val pct = hr.toDouble() / maxExpectedHr
            val z = when {
                pct >= 0.90 -> 5
                pct >= 0.80 -> 4
                pct >= 0.70 -> 3
                pct >= 0.60 -> 2
                else -> 1
            }
            zoneCounts[z]++
        }

        val totalSamples = Math.max(1, validHrs.size)
        val hrZones = mutableListOf<HeartRateZoneStats>()
        val zoneNames = arrayOf("", "Warm Up", "Fat Burn", "Aerobic", "Threshold", "Peak")
        val zoneRanges = arrayOf(
            Pair(0, 0),
            Pair(0, (maxExpectedHr * 0.60).toInt()),
            Pair((maxExpectedHr * 0.60).toInt(), (maxExpectedHr * 0.70).toInt()),
            Pair((maxExpectedHr * 0.70).toInt(), (maxExpectedHr * 0.80).toInt()),
            Pair((maxExpectedHr * 0.80).toInt(), (maxExpectedHr * 0.90).toInt()),
            Pair((maxExpectedHr * 0.90).toInt(), maxExpectedHr)
        )
        for (z in 5 downTo 1) {
            val pct = ((zoneCounts[z].toDouble() / totalSamples) * 100).toInt()
            val sec = ((totalSec * zoneCounts[z]) / totalSamples)
            hrZones.add(
                HeartRateZoneStats(z, zoneNames[z], zoneRanges[z].first, zoneRanges[z].second, sec, pct)
            )
        }

        val avgPace = if (totalDist > 0) totalSec / (totalDist / 1000.0) else 0.0
        val bestPace = points.mapNotNull { it.paceSecPerKm }.filter { it in 120.0..900.0 }.minOrNull() ?: avgPace

        return WorkoutSession(
            id = "tcx_${tStart.toEpochMilli()}",
            title = "Morning Run",
            sport = sport,
            startTime = tStart,
            endTime = tEnd,
            totalDistanceMeters = totalDist,
            movingSeconds = totalSec,
            totalElapsedSeconds = totalSec,
            avgPaceSecPerKm = avgPace,
            bestPaceSecPerKm = bestPace,
            avgHeartRate = avgHr,
            maxHeartRate = maxHr,
            minHeartRate = minHr,
            elevationGainMeters = 0.0,
            elevationLossMeters = 0.0,
            minAltitudeMeters = 0.0,
            maxAltitudeMeters = 0.0,
            caloriesKcal = if (totalCalories > 0) totalCalories else ((totalDist / 1000.0) * 75 * 1.036).toInt(),
            avgSpeedKmh = if (totalSec > 0) (totalDist / 1000.0) / (totalSec / 3600.0) else 0.0,
            maxSpeedKmh = points.map { it.speedKmh }.maxOrNull() ?: 0.0,
            trackPoints = points,
            splits = loopLaps,
            hrZones = hrZones
        )
    }

    /**
     * Cleanly detects laps when running in loops/circles using GPS start proximity
     */
    fun detectLoopLaps(points: List<TrackPoint>): List<SplitInfo> {
        val validPoints = points.filter { it.latitude != 0.0 && it.longitude != 0.0 }
        if (validPoints.size < 10) return emptyList()

        val startPt = validPoints.first()
        val distsFromStart = validPoints.map {
            haversineDistance(startPt.latitude, startPt.longitude, it.latitude, it.longitude)
        }

        val laps = mutableListOf<SplitInfo>()
        var lapStartIdx = 0
        var lapNum = 1
        val minLoopDistanceMeters = 160.0 // Must run at least 160m around the circle before completing a lap

        for (i in 1 until validPoints.size) {
            val distInCurrentLap = validPoints[i].distanceMeters - validPoints[lapStartIdx].distanceMeters
            val distToStart = distsFromStart[i]

            // Returned back to the start coordinate (< 25m) and is at a local minimum
            if (distInCurrentLap >= minLoopDistanceMeters && distToStart < 25.0) {
                val wStart = Math.max(0, i - 4)
                val wEnd = Math.min(validPoints.size, i + 5)
                val localMin = distsFromStart.subList(wStart, wEnd).minOrNull() ?: distToStart

                if (distToStart == localMin) {
                    val pStart = validPoints[lapStartIdx]
                    val pEnd = validPoints[i]
                    val lapDist = pEnd.distanceMeters - pStart.distanceMeters
                    val lapTime = Math.max(1L, pEnd.elapsedSeconds - pStart.elapsedSeconds)
                    val lapPace = if (lapDist > 0) lapTime / (lapDist / 1000.0) else 0.0

                    val slice = validPoints.subList(lapStartIdx, i + 1)
                    val hrs = slice.mapNotNull { it.heartRateBpm }.filter { it > 40 }
                    val avgHr = if (hrs.isNotEmpty()) hrs.average().toInt() else 0
                    val maxHr = if (hrs.isNotEmpty()) hrs.maxOrNull() ?: 0 else 0

                    laps.add(
                        SplitInfo(
                            splitNumber = lapNum,
                            distanceMeters = lapDist,
                            durationSeconds = lapTime,
                            avgPaceSecPerKm = lapPace,
                            bestPaceSecPerKm = slice.mapNotNull { it.paceSecPerKm }.minOrNull() ?: lapPace,
                            avgHeartRate = avgHr,
                            maxHeartRate = maxHr,
                            elevationGainMeters = 0.0,
                            elevationLossMeters = 0.0,
                            startIndex = lapStartIdx,
                            endIndex = i
                        )
                    )

                    lapStartIdx = i
                    lapNum++
                }
            }
        }

        // Final remaining lap/portion
        if (lapStartIdx < validPoints.size - 1) {
            val pStart = validPoints[lapStartIdx]
            val pEnd = validPoints.last()
            val lapDist = pEnd.distanceMeters - pStart.distanceMeters
            val lapTime = Math.max(1L, pEnd.elapsedSeconds - pStart.elapsedSeconds)
            if (lapDist > 40.0) {
                val lapPace = if (lapDist > 0) lapTime / (lapDist / 1000.0) else 0.0
                val slice = validPoints.subList(lapStartIdx, validPoints.size)
                val hrs = slice.mapNotNull { it.heartRateBpm }.filter { it > 40 }

                laps.add(
                    SplitInfo(
                        splitNumber = lapNum,
                        distanceMeters = lapDist,
                        durationSeconds = lapTime,
                        avgPaceSecPerKm = lapPace,
                        bestPaceSecPerKm = slice.mapNotNull { it.paceSecPerKm }.minOrNull() ?: lapPace,
                        avgHeartRate = if (hrs.isNotEmpty()) hrs.average().toInt() else 0,
                        maxHeartRate = if (hrs.isNotEmpty()) hrs.maxOrNull() ?: 0 else 0,
                        elevationGainMeters = 0.0,
                        elevationLossMeters = 0.0,
                        startIndex = lapStartIdx,
                        endIndex = validPoints.size - 1
                    )
                )
            }
        }

        return laps
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
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
