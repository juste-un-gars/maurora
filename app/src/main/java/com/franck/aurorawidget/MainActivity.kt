/**
 * @file MainActivity.kt
 * @description Main activity with Dashboard / Settings navigation using Jetpack Compose.
 */
package com.franck.aurorawidget

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.franck.aurorawidget.data.preferences.DashboardData
import com.franck.aurorawidget.data.preferences.UserPreferences
import com.franck.aurorawidget.location.LocationHelper
import com.franck.aurorawidget.ui.DashboardScreen
import com.franck.aurorawidget.ui.theme.AuroraTheme
import com.franck.aurorawidget.worker.AuroraUpdateWorker
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Simple two-screen navigation without a library. */
sealed class Screen {
    data object Dashboard : Screen()
    data object Settings : Screen()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = UserPreferences(applicationContext)

        setContent {
            AuroraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

                    val dashboardData by prefs.dashboardFlow.collectAsState(initial = DashboardData())
                    val settings by prefs.settingsFlow.collectAsState(initial = null)

                    when (currentScreen) {
                        Screen.Dashboard -> {
                            DashboardScreen(
                                data = dashboardData,
                                locationName = settings?.locationName ?: "",
                                onNavigateToSettings = { currentScreen = Screen.Settings },
                                onRefresh = {
                                    AuroraUpdateWorker.schedule(
                                        applicationContext,
                                        (settings?.refreshMinutes ?: 30).toLong()
                                    )
                                }
                            )
                        }
                        Screen.Settings -> {
                            BackHandler { currentScreen = Screen.Dashboard }
                            ConfigScreen(
                                prefs = prefs,
                                onScheduleWorker = { minutes ->
                                    AuroraUpdateWorker.schedule(applicationContext, minutes.toLong())
                                },
                                onNavigateBack = { currentScreen = Screen.Dashboard }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    prefs: UserPreferences,
    onScheduleWorker: (Int) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by prefs.settingsFlow.collectAsState(initial = null)

    // Local state for text fields
    var latText by remember { mutableStateOf("") }
    var lonText by remember { mutableStateOf("") }
    var nameText by remember { mutableStateOf("") }
    var selectedRefresh by remember { mutableIntStateOf(30) }
    var notifEnabled by remember { mutableStateOf(false) }
    var notifThreshold by remember { mutableFloatStateOf(50f) }

    // Load saved values once available
    LaunchedEffect(settings) {
        settings?.let {
            latText = it.latitude.toString()
            lonText = it.longitude.toString()
            nameText = it.locationName
            selectedRefresh = it.refreshMinutes
            notifEnabled = it.notificationsEnabled
            notifThreshold = it.notificationThreshold.toFloat()
        }
    }

    // GPS permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            val loc = LocationHelper.getLastKnownLocation(context)
            if (loc != null) {
                latText = loc.latitude.toString()
                lonText = loc.longitude.toString()
                nameText = loc.name
                scope.launch {
                    prefs.saveLocation(loc.latitude, loc.longitude, loc.name)
                    onScheduleWorker(selectedRefresh)
                    snackbarHostState.showSnackbar("GPS: ${loc.name}")
                }
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("No GPS location available")
                }
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Location permission denied")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Location section ---
            Text("Location", style = MaterialTheme.typography.titleSmall)

            // GPS button
            OutlinedButton(
                onClick = {
                    if (LocationHelper.hasPermission(context)) {
                        val loc = LocationHelper.getLastKnownLocation(context)
                        if (loc != null) {
                            latText = loc.latitude.toString()
                            lonText = loc.longitude.toString()
                            nameText = loc.name
                            scope.launch {
                                prefs.saveLocation(loc.latitude, loc.longitude, loc.name)
                                onScheduleWorker(selectedRefresh)
                                snackbarHostState.showSnackbar("GPS: ${loc.name}")
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("No GPS location available")
                            }
                        }
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Use GPS location")
            }

            Text("— or enter manually —",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text("Location name") },
                placeholder = { Text("e.g. Paris, France") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("Latitude") },
                    placeholder = { Text("48.86") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { lonText = it },
                    label = { Text("Longitude") },
                    placeholder = { Text("2.35") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }

            Button(
                onClick = {
                    val lat = latText.toDoubleOrNull()
                    val lon = lonText.toDoubleOrNull()
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                        scope.launch {
                            prefs.saveLocation(lat, lon, nameText.ifBlank { "Custom" })
                            onScheduleWorker(selectedRefresh)
                            snackbarHostState.showSnackbar("Location saved")
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("Invalid coordinates")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save location")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Refresh interval section ---
            Text("Refresh interval", style = MaterialTheme.typography.titleSmall)

            val options = listOf(15, 30, 60)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, minutes ->
                    SegmentedButton(
                        selected = selectedRefresh == minutes,
                        onClick = {
                            selectedRefresh = minutes
                            scope.launch {
                                prefs.saveRefreshMinutes(minutes)
                                onScheduleWorker(minutes)
                                snackbarHostState.showSnackbar("Refresh: every $minutes min")
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size)
                    ) {
                        Text("${minutes}min")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Notifications section ---
            Text("Notifications", style = MaterialTheme.typography.titleSmall)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Aurora alerts", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = notifEnabled,
                    onCheckedChange = { enabled ->
                        notifEnabled = enabled
                        scope.launch {
                            prefs.saveNotificationSettings(enabled, notifThreshold.roundToInt())
                            snackbarHostState.showSnackbar(
                                if (enabled) "Notifications enabled" else "Notifications disabled"
                            )
                        }
                    }
                )
            }

            if (notifEnabled) {
                Text(
                    "Alert when visibility exceeds ${notifThreshold.roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = notifThreshold,
                    onValueChange = { notifThreshold = it },
                    onValueChangeFinished = {
                        scope.launch {
                            prefs.saveNotificationSettings(notifEnabled, notifThreshold.roundToInt())
                        }
                    },
                    valueRange = 20f..80f,
                    steps = 5
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Current status ---
            settings?.let {
                Text("Current settings", style = MaterialTheme.typography.titleSmall)
                val notifStatus = if (it.notificationsEnabled)
                    "Notifications: ON (threshold ${it.notificationThreshold}%)"
                else
                    "Notifications: OFF"
                Text(
                    "${it.locationName} (${it.latitude}, ${it.longitude})\n" +
                        "Refresh: every ${it.refreshMinutes} min\n" +
                        notifStatus,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
