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
import androidx.health.connect.client.contracts.ExerciseRouteRequestContract
import androidx.health.connect.client.records.ExerciseRoute
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
import java.time.ZoneId

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
    private lateinit var routeConsentLauncher: ActivityResultLauncher<String>
    private lateinit var tcxPickerLauncher: ActivityResultLauncher<Array<String>>

    private var pendingRouteSessionId by mutableStateOf<String?>(null)
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

        routeConsentLauncher = registerForActivityResult(
            ExerciseRouteRequestContract()
        ) { route: ExerciseRoute? ->
            if (route != null) {
                val currentReady = uiState as? UiState.Ready ?: return@registerForActivityResult
                val updatedWorkouts = currentReady.workouts.map { w ->
                    if (w.id == pendingRouteSessionId) {
                        healthConnectManager.enrichSessionWithRoute(w, route)
                    } else {
                        w
                    }
                }
                uiState = currentReady.copy(workouts = updatedWorkouts)
            }
        }

        tcxPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val parsed = TcxParser.parse(stream)
                            val dest = File(getExternalFilesDir(null), "imported_${System.currentTimeMillis()}.tcx")
                            try {
                                contentResolver.openInputStream(uri)?.use { inStr ->
                                    dest.outputStream().use { outStr -> inStr.copyTo(outStr) }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Could not save imported tcx: ${e.message}")
                            }
                            withContext(Dispatchers.Main) {
                                loadHealthConnectData(activeDateFilter)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error importing TCX file", e)
                    }
                }
            }
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
                                    onRefreshHealthConnect = { loadHealthConnectData(state.filterDate) },
                                    onRequestRouteConsent = { sessionId ->
                                        pendingRouteSessionId = sessionId
                                        routeConsentLauncher.launch(sessionId)
                                    },
                                    onImportTcx = {
                                        tcxPickerLauncher.launch(arrayOf("*/*"))
                                    }
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

    private fun tryLoadAllTcx(): List<WorkoutSession> {
        val searchDirs = listOfNotNull(
            getExternalFilesDir(null),
            File("/sdcard/Download"),
            File("/sdcard/Download/Telegram"),
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0/Download/Telegram")
        )
        val foundFiles = mutableSetOf<String>()
        val sessions = mutableListOf<WorkoutSession>()

        for (dir in searchDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles { f ->
                f.isFile && (f.name.endsWith(".tcx", ignoreCase = true) || f.name.endsWith(".gpx", ignoreCase = true))
            } ?: continue

            for (f in files) {
                if (f.absolutePath in foundFiles) continue
                foundFiles.add(f.absolutePath)
                try {
                    f.inputStream().use { stream ->
                        val s = TcxParser.parse(stream)
                        Log.i(TAG, "Loaded TCX from ${f.absolutePath} with ${s.trackPoints.size} points (start=${s.startTime})")
                        sessions.add(s)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not parse candidate file ${f.absolutePath}: ${e.message}")
                }
            }
        }
        return sessions
    }

    private fun checkStatusAndLoad(date: LocalDate?) {
        val status = HealthConnectClient.getSdkStatus(this)
        if (status == HealthConnectClient.SDK_UNAVAILABLE || status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            val localTcx = tryLoadAllTcx()
            if (localTcx.isNotEmpty()) {
                val zoneId = ZoneId.systemDefault()
                val filtered = if (date != null) localTcx.filter { it.startTime.atZone(zoneId).toLocalDate() == date } else localTcx
                val sorted = filtered.sortedByDescending { it.startTime }
                uiState = if (sorted.isNotEmpty()) UiState.Ready(sorted, 0, date) else UiState.Empty(date)
            } else {
                uiState = UiState.NotAvailable
            }
            return
        }

        lifecycleScope.launch {
            if (healthConnectManager.hasRequiredPermissions()) {
                loadHealthConnectData(date)
            } else {
                val localTcx = tryLoadAllTcx()
                if (localTcx.isNotEmpty()) {
                    val zoneId = ZoneId.systemDefault()
                    val filtered = if (date != null) localTcx.filter { it.startTime.atZone(zoneId).toLocalDate() == date } else localTcx
                    val sorted = filtered.sortedByDescending { it.startTime }
                    uiState = if (sorted.isNotEmpty()) UiState.Ready(sorted, 0, date) else UiState.Empty(date)
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
                // If date is selected, read that date; if null, read 90 days back
                val hcSessions = healthConnectManager.readWorkouts(filterDate = date, daysBack = 90)
                val tcxSessions = withContext(Dispatchers.IO) { tryLoadAllTcx() }

                val combined = mutableListOf<WorkoutSession>()
                val matchedTcxIds = mutableSetOf<String>()

                // 1. Enrich Health Connect workouts with matching TCX files if available
                for (hc in hcSessions) {
                    val matchedTcx = tcxSessions.find { tcx ->
                        Math.abs(tcx.startTime.epochSecond - hc.startTime.epochSecond) < 1800
                    }
                    if (matchedTcx != null) {
                        matchedTcxIds.add(matchedTcx.id)
                        val enriched = hc.copy(
                            trackPoints = if (matchedTcx.trackPoints.isNotEmpty()) matchedTcx.trackPoints else hc.trackPoints,
                            splits = if (matchedTcx.splits.isNotEmpty()) matchedTcx.splits else hc.splits,
                            totalDistanceMeters = if (matchedTcx.totalDistanceMeters > 0) matchedTcx.totalDistanceMeters else hc.totalDistanceMeters,
                            avgPaceSecPerKm = if (matchedTcx.avgPaceSecPerKm > 0) matchedTcx.avgPaceSecPerKm else hc.avgPaceSecPerKm,
                            bestPaceSecPerKm = if (matchedTcx.bestPaceSecPerKm > 0) matchedTcx.bestPaceSecPerKm else hc.bestPaceSecPerKm,
                            hrZones = if (hc.hrZones.isNotEmpty()) hc.hrZones else matchedTcx.hrZones
                        )
                        combined.add(enriched)
                    } else {
                        combined.add(hc)
                    }
                }

                // 2. Add any standalone TCX sessions that did not match a Health Connect workout
                for (tcx in tcxSessions) {
                    if (tcx.id !in matchedTcxIds) {
                        combined.add(tcx)
                    }
                }

                // 3. Strict Date Filtering if date filter is active
                val zoneId = ZoneId.systemDefault()
                val filtered = if (date != null) {
                    combined.filter {
                        it.startTime.atZone(zoneId).toLocalDate() == date
                    }
                } else {
                    combined
                }

                // 4. Always sort newest first so recent workout is index 0
                val sorted = filtered.sortedByDescending { it.startTime }

                if (sorted.isEmpty()) {
                    uiState = UiState.Empty(filterDate = date)
                } else {
                    uiState = UiState.Ready(
                        workouts = sorted,
                        selectedIndex = 0,
                        filterDate = date
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading workouts", e)
                val tcxSessions = withContext(Dispatchers.IO) { tryLoadAllTcx() }
                val zoneId = ZoneId.systemDefault()
                val filtered = if (date != null) {
                    tcxSessions.filter { it.startTime.atZone(zoneId).toLocalDate() == date }
                } else {
                    tcxSessions
                }
                val sorted = filtered.sortedByDescending { it.startTime }
                if (sorted.isNotEmpty()) {
                    uiState = UiState.Ready(sorted, 0, date)
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
