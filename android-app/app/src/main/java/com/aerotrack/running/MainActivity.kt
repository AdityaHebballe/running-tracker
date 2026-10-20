package com.aerotrack.running

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.aerotrack.running.data.TcxParser
import com.aerotrack.running.data.WorkoutSession
import com.aerotrack.running.health.HealthConnectManager
import com.aerotrack.running.ui.AeroTrackTheme
import com.aerotrack.running.ui.HealthConnectNotAvailableScreen
import com.aerotrack.running.ui.HealthConnectPermissionScreen
import com.aerotrack.running.ui.NoWorkoutsScreen
import com.aerotrack.running.ui.RouteStudioScreen
import com.aerotrack.running.ui.WorkoutDetailScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

sealed interface UiState {
    data object Checking : UiState
    data object NotAvailable : UiState
    data object PermissionRequired : UiState
    data object Loading : UiState
    data class Empty(val filterDate: LocalDate?) : UiState
    data class Ready(
        val workouts: List<WorkoutSession>,
        val selectedIndex: Int = 0,
        val filterDate: LocalDate? = null
    ) : UiState
    data class Error(val message: String) : UiState
}

class MainActivity : ComponentActivity() {

    private val TAG = "AeroTrack"
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var permissionLauncher: ActivityResultLauncher<Set<String>>

    private var uiState by mutableStateOf<UiState>(UiState.Checking)
    private var activeDateFilter by mutableStateOf<LocalDate?>(null)
    private var showStudio by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        healthConnectManager = HealthConnectManager(this)

        permissionLauncher = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) {
            checkStatusAndLoad(activeDateFilter)
        }

        checkStatusAndLoad(activeDateFilter)

        setContent {
            AeroTrackTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (val state = uiState) {
                        is UiState.Checking, is UiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        is UiState.NotAvailable -> {
                            HealthConnectNotAvailableScreen(
                                onInstallHealthConnect = { openPlayStoreForHealthConnect() }
                            )
                        }

                        is UiState.PermissionRequired -> {
                            HealthConnectPermissionScreen(
                                onGrantPermissions = {
                                    permissionLauncher.launch(healthConnectManager.permissions)
                                }
                            )
                        }

                        is UiState.Empty -> {
                            NoWorkoutsScreen(
                                onSyncNow = { checkStatusAndLoad(activeDateFilter) }
                            )
                        }

                        is UiState.Ready -> {
                            val selectedWorkout = state.workouts[state.selectedIndex.coerceIn(0, state.workouts.size - 1)]
                            if (showStudio) {
                                BackHandler {
                                    showStudio = false
                                }
                                RouteStudioScreen(
                                    workout = selectedWorkout,
                                    onBack = { showStudio = false }
                                )
                            } else {
                                WorkoutDetailScreen(
                                    workout = selectedWorkout,
                                    allWorkouts = state.workouts,
                                    selectedIndex = state.selectedIndex,
                                    selectedDate = state.filterDate,
                                    onSelectDate = { newDate ->
                                        activeDateFilter = newDate
                                        loadHealthConnectData(newDate)
                                    },
                                    onSelectWorkout = { index ->
                                        uiState = state.copy(selectedIndex = index)
                                    },
                                    onOpenStudio = { showStudio = true },
                                    onRefreshHealthConnect = { loadHealthConnectData(state.filterDate) }
                                )
                            }
                        }

                        is UiState.Error -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { checkStatusAndLoad(activeDateFilter) }) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (uiState is UiState.PermissionRequired || uiState is UiState.NotAvailable) {
            checkStatusAndLoad(activeDateFilter)
        }
    }

    private fun tryLoadPreloadedTcx(): WorkoutSession? {
        val candidates = listOf(
            File(getExternalFilesDir(null), "exercise_tcx_file.tcx"),
            File("/sdcard/Download/Telegram/exercise_tcx_file.tcx"),
            File("/sdcard/Download/exercise_tcx_file.tcx")
        )
        for (f in candidates) {
            if (f.exists() && f.canRead()) {
                try {
                    f.inputStream().use { stream ->
                        val session = TcxParser.parse(stream)
                        Log.i(TAG, "Successfully loaded TCX from ${f.absolutePath} with ${session.trackPoints.size} points")
                        return session
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not parse candidate file ${f.absolutePath}: ${e.message}")
                }
            }
        }
        return null
    }

    private fun checkStatusAndLoad(date: LocalDate?) {
        val status = HealthConnectClient.getSdkStatus(this)
        if (status == HealthConnectClient.SDK_UNAVAILABLE || status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            val localTcx = tryLoadPreloadedTcx()
            if (localTcx != null) {
                uiState = UiState.Ready(listOf(localTcx), 0, null)
            } else {
                uiState = UiState.NotAvailable
            }
            return
        }

        lifecycleScope.launch {
            if (healthConnectManager.hasRequiredPermissions()) {
                loadHealthConnectData(date)
            } else {
                val localTcx = tryLoadPreloadedTcx()
                if (localTcx != null) {
                    uiState = UiState.Ready(listOf(localTcx), 0, null)
                } else {
                    uiState = UiState.PermissionRequired
                }
            }
        }
    }

    private fun loadHealthConnectData(date: LocalDate?) {
        lifecycleScope.launch {
            uiState = UiState.Loading
            try {
                var sessions = healthConnectManager.readWorkouts(filterDate = date)
                if (sessions.isEmpty() && date != null) {
                    val allRecent = healthConnectManager.readWorkouts(filterDate = null, daysBack = 90)
                    if (allRecent.isNotEmpty()) {
                        sessions = allRecent
                        activeDateFilter = null
                    }
                }

                val localTcx = tryLoadPreloadedTcx()
                val combinedSessions = mutableListOf<WorkoutSession>()

                if (localTcx != null) {
                    val matchedHc = sessions.find { 
                        Math.abs(it.startTime.epochSecond - localTcx.startTime.epochSecond) < 600 
                    }
                    if (matchedHc != null) {
                        // Enrich Health Connect session with TCX GPS route points and loop laps!
                        val enriched = matchedHc.copy(
                            trackPoints = localTcx.trackPoints,
                            splits = localTcx.splits,
                            totalDistanceMeters = if (localTcx.totalDistanceMeters > 0) localTcx.totalDistanceMeters else matchedHc.totalDistanceMeters,
                            avgPaceSecPerKm = if (localTcx.avgPaceSecPerKm > 0) localTcx.avgPaceSecPerKm else matchedHc.avgPaceSecPerKm,
                            bestPaceSecPerKm = if (localTcx.bestPaceSecPerKm > 0) localTcx.bestPaceSecPerKm else matchedHc.bestPaceSecPerKm,
                            hrZones = if (matchedHc.hrZones.isNotEmpty()) matchedHc.hrZones else localTcx.hrZones
                        )
                        combinedSessions.add(enriched)
                        combinedSessions.addAll(sessions.filter { it.id != matchedHc.id })
                    } else {
                        combinedSessions.add(localTcx)
                        combinedSessions.addAll(sessions)
                    }
                } else {
                    combinedSessions.addAll(sessions)
                }

                if (combinedSessions.isEmpty()) {
                    uiState = UiState.Empty(filterDate = date)
                } else {
                    uiState = UiState.Ready(
                        workouts = combinedSessions,
                        selectedIndex = 0,
                        filterDate = activeDateFilter
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading workouts", e)
                val localTcx = tryLoadPreloadedTcx()
                if (localTcx != null) {
                    uiState = UiState.Ready(listOf(localTcx), 0, null)
                } else {
                    uiState = UiState.Error(e.localizedMessage ?: "Failed to read Health Connect data")
                }
            }
        }
    }

    private fun openPlayStoreForHealthConnect() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
            setPackage("com.android.vending")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
        }
    }
}
