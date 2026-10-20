package com.aerotrack.running.data

import java.time.Instant

data class TrackPoint(
    val time: Instant,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val speedKmh: Double = 0.0,
    val paceSecPerKm: Double? = null,
    val heartRateBpm: Int? = null,
    val elapsedSeconds: Long = 0
)

data class SplitInfo(
    val splitNumber: Int,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val avgPaceSecPerKm: Double,
    val bestPaceSecPerKm: Double,
    val avgHeartRate: Int,
    val maxHeartRate: Int,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double
)

data class HeartRateZoneStats(
    val zoneNumber: Int,
    val name: String,
    val minBpm: Int,
    val maxBpm: Int,
    val durationSeconds: Long,
    val percentage: Int
)

data class WorkoutSession(
    val id: String,
    val title: String = "Morning Run",
    val sport: String = "Running",
    val startTime: Instant,
    val endTime: Instant,
    val totalDistanceMeters: Double,
    val movingSeconds: Long,
    val totalElapsedSeconds: Long,
    val avgPaceSecPerKm: Double,
    val bestPaceSecPerKm: Double,
    val avgHeartRate: Int,
    val maxHeartRate: Int,
    val minHeartRate: Int,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val minAltitudeMeters: Double,
    val maxAltitudeMeters: Double,
    val caloriesKcal: Int,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val trackPoints: List<TrackPoint> = emptyList(),
    val splits: List<SplitInfo> = emptyList(),
    val hrZones: List<HeartRateZoneStats> = emptyList()
)
