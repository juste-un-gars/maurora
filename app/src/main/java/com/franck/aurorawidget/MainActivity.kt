/**
 * @file MainActivity.kt
 * @description Main activity with Dashboard / Settings navigation using Jetpack Compose.
 */
package com.franck.aurorawidget

import android.Manifest
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.franck.aurorawidget.data.model.GeocodingResult
import com.franck.aurorawidget.data.preferences.DashboardData
import com.franck.aurorawidget.data.preferences.UserPreferences
import com.franck.aurorawidget.data.remote.GeocodingRepository
import com.franck.aurorawidget.data.remote.HttpClientFactory
import com.franck.aurorawidget.location.LocationHelper
import com.franck.aurorawidget.ui.DashboardScreen
import com.franck.aurorawidget.ui.theme.AuroraTheme
import com.franck.aurorawidget.worker.AuroraUpdateWorker
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Simple two-screen navigation without a library. */
sealed class Screen {
    data object Dashboard : Screen()
    data object Settings : Screen()
}

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = UserPreferences(applicationContext)

        // Apply saved language before setContent
        val savedSettings = runBlocking { prefs.settingsFlow.first() }
        applyLanguage(savedSettings.language)

        setContent {
            val settings by prefs.settingsFlow.collectAsState(initial = savedSettings)

            val darkTheme = when (settings.theme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            AuroraTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

                    val dashboardData by prefs.dashboardFlow.collectAsState(initial = DashboardData())

                    when (currentScreen) {
                        Screen.Dashboard -> {
                            DashboardScreen(
                                data = dashboardData,
                                locationName = settings.locationName,
                                onNavigateToSettings = { currentScreen = Screen.Settings },
                                onRefresh = {
                                    AuroraUpdateWorker.schedule(
                                        applicationContext,
                                        settings.refreshMinutes.toLong()
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
                                onLanguageChange = { lang -> applyLanguage(lang) },
                                onNavigateBack = { currentScreen = Screen.Dashboard }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyLanguage(lang: String) {
        val locales = if (lang == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(lang)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    prefs: UserPreferences,
    onScheduleWorker: (Int) -> Unit,
    onLanguageChange: (String) -> Unit,
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
    var selectedLanguage by remember { mutableStateOf("system") }
    var selectedTheme by remember { mutableStateOf("system") }

    // City search state
    var cityQuery by remember { mutableStateOf("") }
    var cityResults by remember { mutableStateOf<List<GeocodingResult>>(emptyList()) }
    var citySearching by remember { mutableStateOf(false) }
    var citySearchJob by remember { mutableStateOf<Job?>(null) }
    val geocodingRepo = remember { GeocodingRepository(HttpClientFactory.create()) }

    // Load saved values once available
    LaunchedEffect(settings) {
        settings?.let {
            latText = it.latitude.toString()
            lonText = it.longitude.toString()
            nameText = it.locationName
            selectedRefresh = it.refreshMinutes
            notifEnabled = it.notificationsEnabled
            notifThreshold = it.notificationThreshold.toFloat()
            selectedLanguage = it.language
            selectedTheme = it.theme
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
                    snackbarHostState.showSnackbar(context.getString(R.string.snack_gps, loc.name))
                }
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.snack_no_gps))
                }
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.snack_permission_denied))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
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
            Text(stringResource(R.string.settings_location), style = MaterialTheme.typography.titleSmall)

            // City search field
            OutlinedTextField(
                value = cityQuery,
                onValueChange = { query ->
                    cityQuery = query
                    citySearchJob?.cancel()
                    if (query.length >= 2) {
                        citySearching = true
                        citySearchJob = scope.launch {
                            delay(500) // debounce
                            geocodingRepo.searchCities(query).onSuccess { results ->
                                cityResults = results
                            }.onFailure {
                                cityResults = emptyList()
                            }
                            citySearching = false
                        }
                    } else {
                        cityResults = emptyList()
                        citySearching = false
                    }
                },
                label = { Text(stringResource(R.string.settings_search_city)) },
                placeholder = { Text(stringResource(R.string.settings_search_placeholder)) },
                trailingIcon = {
                    if (citySearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(4.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Search results
            if (cityQuery.length >= 2) {
                if (cityResults.isEmpty() && !citySearching) {
                    Text(
                        stringResource(R.string.settings_no_results),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                cityResults.forEach { result ->
                    val label = buildString {
                        append(result.name)
                        if (result.country.isNotBlank()) append(", ${result.country}")
                        if (result.admin1.isNotBlank()) append(" (${result.admin1})")
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                nameText = result.name
                                latText = result.latitude.toString()
                                lonText = result.longitude.toString()
                                cityQuery = ""
                                cityResults = emptyList()
                                scope.launch {
                                    prefs.saveLocation(
                                        result.latitude,
                                        result.longitude,
                                        result.name
                                    )
                                    onScheduleWorker(selectedRefresh)
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.snack_location_saved)
                                    )
                                }
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                    HorizontalDivider()
                }
            }

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
                                snackbarHostState.showSnackbar(context.getString(R.string.snack_gps, loc.name))
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.snack_no_gps))
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
                Text(stringResource(R.string.settings_use_gps))
            }

            Text(stringResource(R.string.settings_or_manual),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text(stringResource(R.string.settings_location_name)) },
                placeholder = { Text(stringResource(R.string.settings_location_placeholder)) },
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
                    label = { Text(stringResource(R.string.settings_latitude)) },
                    placeholder = { Text("48.86") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { lonText = it },
                    label = { Text(stringResource(R.string.settings_longitude)) },
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
                            snackbarHostState.showSnackbar(context.getString(R.string.snack_location_saved))
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.snack_invalid_coords))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_save_location))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Refresh interval section ---
            Text(stringResource(R.string.settings_refresh_interval), style = MaterialTheme.typography.titleSmall)

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
                                snackbarHostState.showSnackbar(context.getString(R.string.snack_refresh, minutes))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size)
                    ) {
                        Text(stringResource(R.string.settings_refresh_format, minutes))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Notifications section ---
            Text(stringResource(R.string.settings_notifications), style = MaterialTheme.typography.titleSmall)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_aurora_alerts), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = notifEnabled,
                    onCheckedChange = { enabled ->
                        notifEnabled = enabled
                        scope.launch {
                            prefs.saveNotificationSettings(enabled, notifThreshold.roundToInt())
                            snackbarHostState.showSnackbar(
                                context.getString(if (enabled) R.string.snack_notif_enabled else R.string.snack_notif_disabled)
                            )
                        }
                    }
                )
            }

            if (notifEnabled) {
                Text(
                    stringResource(R.string.settings_alert_threshold, notifThreshold.roundToInt()),
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

            Spacer(modifier = Modifier.height(8.dp))

            // --- Language section ---
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)

            val languageOptions = listOf("system", "fr", "en")
            val languageLabels = listOf(
                stringResource(R.string.settings_language_system),
                stringResource(R.string.settings_language_fr),
                stringResource(R.string.settings_language_en)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                languageOptions.forEachIndexed { index, lang ->
                    SegmentedButton(
                        selected = selectedLanguage == lang,
                        onClick = {
                            selectedLanguage = lang
                            scope.launch {
                                prefs.saveLanguage(lang)
                                onLanguageChange(lang)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, languageOptions.size)
                    ) {
                        Text(languageLabels[index])
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Theme section ---
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)

            val themeOptions = listOf("system", "light", "dark")
            val themeLabels = listOf(
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_light),
                stringResource(R.string.settings_theme_dark)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                themeOptions.forEachIndexed { index, theme ->
                    SegmentedButton(
                        selected = selectedTheme == theme,
                        onClick = {
                            selectedTheme = theme
                            scope.launch { prefs.saveTheme(theme) }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, themeOptions.size)
                    ) {
                        Text(themeLabels[index])
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Current status ---
            settings?.let {
                Text(stringResource(R.string.settings_current), style = MaterialTheme.typography.titleSmall)
                val notifStatus = if (it.notificationsEnabled)
                    stringResource(R.string.settings_notif_on, it.notificationThreshold)
                else
                    stringResource(R.string.settings_notif_off)
                Text(
                    stringResource(
                        R.string.settings_summary,
                        it.locationName,
                        it.latitude.toString(),
                        it.longitude.toString(),
                        it.refreshMinutes,
                        notifStatus
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
