package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.models.UserProfile
import com.example.data.repository.FollowStateStore
import com.example.items.BlinkItemNotificationManager
import com.example.items.BlinkItemPreferences
import com.example.items.BlinkLiveLocationRepository
import com.example.items.BlinkLiveLocationService
import com.example.items.BlinkLocationClient
import com.example.items.BlinkMyLiveLocationSession
import com.example.items.BlinkSharedLocation
import com.example.items.BlinkStepRepository
import com.example.items.BlinkWeatherRepository
import com.example.items.BlinkWeatherSnapshot
import com.example.ui.theme.FeedBackground
import com.example.ui.theme.FeedBorder
import com.example.ui.theme.FeedElevatedSurface
import com.example.ui.theme.FeedPurple
import com.example.ui.theme.FeedTextPrimary
import com.example.ui.theme.FeedTextSecondary
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class BlinkItemsPage { HOME, STEPS, WEATHER, LOCATION }

@Composable
fun BlinkItemsScreen(
    profiles: List<UserProfile>,
    currentUserId: String,
    currentUsername: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var page by remember { mutableStateOf(BlinkItemsPage.HOME) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FeedBackground)
            .testTag("blink_items_screen")
    ) {
        BlinkItemsHeader(
            title = when (page) {
                BlinkItemsPage.HOME -> "Items"
                BlinkItemsPage.STEPS -> "Steps"
                BlinkItemsPage.WEATHER -> "Weather"
                BlinkItemsPage.LOCATION -> "Live Location"
            },
            canGoBack = page != BlinkItemsPage.HOME,
            onBack = { page = BlinkItemsPage.HOME },
            onClose = onClose
        )

        when (page) {
            BlinkItemsPage.HOME -> BlinkItemsHome(
                onSteps = { page = BlinkItemsPage.STEPS },
                onWeather = { page = BlinkItemsPage.WEATHER },
                onLocation = { page = BlinkItemsPage.LOCATION }
            )
            BlinkItemsPage.STEPS -> BlinkStepsItem()
            BlinkItemsPage.WEATHER -> BlinkWeatherItem()
            BlinkItemsPage.LOCATION -> BlinkLiveLocationItem(
                profiles = profiles,
                currentUserId = currentUserId,
                currentUsername = currentUsername
            )
        }
    }
}

@Composable
private fun BlinkItemsHeader(
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canGoBack) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = FeedTextPrimary)
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
        Text(
            text = title,
            color = FeedTextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close Items", tint = FeedTextPrimary)
        }
    }
    HorizontalDivider(color = FeedBorder.copy(alpha = 0.8f))
}

@Composable
private fun BlinkItemsHome(
    onSteps: () -> Unit,
    onWeather: () -> Unit,
    onLocation: () -> Unit
) {
    val context = LocalContext.current
    var stepsEnabled by remember { mutableStateOf(BlinkItemPreferences.stepsEnabled(context)) }
    var weatherEnabled by remember { mutableStateOf(BlinkItemPreferences.weatherEnabled(context)) }
    var steps by remember { mutableLongStateOf(BlinkItemPreferences.lastStepCount(context)) }
    var weather by remember { mutableStateOf(BlinkWeatherRepository(context).cached()) }
    var session by remember { mutableStateOf<BlinkMyLiveLocationSession?>(null) }

    LaunchedEffect(stepsEnabled) {
        if (stepsEnabled) {
            val repo = BlinkStepRepository(context)
            if (repo.isAvailable() && repo.hasReadPermission()) {
                repo.readTodaySteps().getOrNull()?.let {
                    steps = it
                    BlinkItemPreferences.saveStepSnapshot(
                        context,
                        java.time.LocalDate.now().toString(),
                        it
                    )
                }
            }
        }
    }
    LaunchedEffect(weatherEnabled) {
        if (weatherEnabled) weather = BlinkWeatherRepository(context).cached()
    }
    LaunchedEffect(Unit) {
        session = BlinkLiveLocationRepository(context).currentSession().getOrNull()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            BlinkItemsGroupLabel("TODAY (2)")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BlinkCompactItemTile(
                    icon = Icons.Default.DirectionsWalk,
                    title = "Steps",
                    subtitle = if (stepsEnabled) "%,d today".format(steps.coerceAtLeast(0)) else "Off",
                    enabled = stepsEnabled,
                    showSwitch = true,
                    onToggle = {
                        if (!it) {
                            BlinkItemPreferences.setStepsEnabled(context, false)
                            stepsEnabled = false
                        } else {
                            onSteps()
                        }
                    },
                    onClick = onSteps,
                    modifier = Modifier.weight(1f)
                )
                BlinkCompactItemTile(
                    icon = Icons.Default.WbSunny,
                    title = "Weather",
                    subtitle = when {
                        !weatherEnabled -> "Off"
                        weather != null -> "${weather!!.temperatureC.roundToInt()}°C • ${weather!!.condition}"
                        else -> "Ready"
                    },
                    enabled = weatherEnabled,
                    showSwitch = true,
                    onToggle = {
                        if (!it) {
                            BlinkItemPreferences.setWeatherEnabled(context, false)
                            weatherEnabled = false
                        } else {
                            onWeather()
                        }
                    },
                    onClick = onWeather,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            BlinkItemsGroupLabel("CONNECT (1)")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BlinkCompactItemTile(
                    icon = Icons.Default.LocationOn,
                    title = "Live Location",
                    subtitle = session?.let { "Sharing with ${it.recipientCount}" } ?: "Not sharing",
                    enabled = session != null,
                    showSwitch = false,
                    onToggle = {},
                    onClick = onLocation,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.weight(1f))
            }
        }

        item {
            Text(
                text = "Items request access only when you turn a feature on. Live Location is private, temporary, and shared only with people you choose.",
                style = MaterialTheme.typography.bodySmall,
                color = FeedTextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun BlinkItemsGroupLabel(label: String) {
    Text(
        text = "⌄  $label",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = FeedTextSecondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 7.dp)
    )
}

@Composable
private fun BlinkCompactItemTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    showSwitch: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = FeedElevatedSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) FeedPurple.copy(alpha = 0.42f) else FeedBorder
        ),
        modifier = modifier
            .heightIn(min = 132.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            if (enabled) FeedPurple.copy(alpha = 0.16f) else FeedBackground,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = FeedTextPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (showSwitch) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = onToggle,
                        modifier = Modifier.size(width = 46.dp, height = 28.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = FeedTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                color = FeedTextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = FeedTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BlinkItemTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    showSwitch: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Surface(
        color = FeedElevatedSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) FeedPurple.copy(alpha = 0.42f) else FeedBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (enabled) FeedPurple.copy(alpha = 0.16f) else FeedBackground,
                        RoundedCornerShape(13.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = FeedTextPrimary)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = FeedTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    subtitle,
                    color = FeedTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showSwitch) {
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = FeedTextSecondary)
            }
        }
    }
}

@Composable
private fun BlinkStepsItem() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { BlinkStepRepository(context) }

    var enabled by remember { mutableStateOf(BlinkItemPreferences.stepsEnabled(context)) }
    var steps by remember { mutableLongStateOf(BlinkItemPreferences.lastStepCount(context)) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var milestones by remember { mutableStateOf(BlinkItemPreferences.stepMilestonesEnabled(context)) }
    var goalAlerts by remember { mutableStateOf(BlinkItemPreferences.stepGoalEnabled(context)) }

    val stepNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun requestStepNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            stepNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun refresh() {
        if (!enabled) return
        loading = true
        message = null
        scope.launch {
            val result = repository.readTodaySteps()
            loading = false
            result.onSuccess {
                steps = it
                BlinkItemPreferences.saveStepSnapshot(
                    context,
                    java.time.LocalDate.now().toString(),
                    it
                )
                BlinkItemNotificationManager.evaluateStepMilestone(context, it)
            }.onFailure {
                message = it.message ?: "Unable to read steps."
            }
        }
    }

    val activityPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val canRead = granted || !repository.requiresRuntimePermission()
        BlinkItemPreferences.setStepsEnabled(context, canRead)
        enabled = canRead
        if (canRead) {
            requestStepNotificationsIfNeeded()
            refresh()
        } else {
            message = "Physical activity access was not granted."
        }
    }

    LaunchedEffect(Unit) {
        if (enabled && repository.isAvailable() && repository.hasReadPermission()) refresh()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = FeedElevatedSurface,
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, FeedBorder)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        tint = FeedPurple,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "%,d".format(steps),
                        color = FeedTextPrimary,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("steps today", color = FeedTextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Goal: %,d".format(BlinkItemPreferences.dailyStepGoal(context)),
                        color = FeedTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            BlinkSettingSwitch(
                title = "Steps",
                subtitle = if (enabled) "Using your phone’s step counter" else "Off until you enable it",
                checked = enabled,
                onCheckedChange = { turnOn ->
                    if (!turnOn) {
                        BlinkItemPreferences.setStepsEnabled(context, false)
                        enabled = false
                    } else if (!repository.isAvailable()) {
                        message = "This device does not provide a hardware step counter."
                    } else {
                        if (repository.hasReadPermission()) {
                            BlinkItemPreferences.setStepsEnabled(context, true)
                            enabled = true
                            requestStepNotificationsIfNeeded()
                            refresh()
                        } else {
                            activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        }
                    }
                }
            )
        }
        item {
            BlinkSettingSwitch(
                title = "Milestone encouragement",
                subtitle = "Useful progress alerts such as 1k, 2k, 3k, 5k and 7.5k.",
                checked = milestones,
                onCheckedChange = {
                    milestones = it
                    BlinkItemPreferences.setStepMilestonesEnabled(context, it)
                }
            )
        }
        item {
            BlinkSettingSwitch(
                title = "Goal reached",
                subtitle = "Notify when you reach your daily step goal.",
                checked = goalAlerts,
                onCheckedChange = {
                    goalAlerts = it
                    BlinkItemPreferences.setStepGoalEnabled(context, it)
                }
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { refresh() }, enabled = enabled && !loading) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Refresh")
                }
            }
        }
        message?.let { error ->
            item { BlinkItemsMessage(error) }
        }
    }
}

@Composable
private fun BlinkWeatherItem() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { BlinkWeatherRepository(context) }

    var enabled by remember { mutableStateOf(BlinkItemPreferences.weatherEnabled(context)) }
    var snapshot by remember { mutableStateOf(repository.cached()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var rainAlerts by remember { mutableStateOf(BlinkItemPreferences.rainAlertsEnabled(context)) }
    var severeAlerts by remember { mutableStateOf(BlinkItemPreferences.severeAlertsEnabled(context)) }

    val weatherNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun requestWeatherNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            weatherNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun refreshWeather() {
        if (!BlinkLocationClient.hasCoarsePermission(context)) {
            message = "Allow approximate location to show local weather."
            return
        }
        loading = true
        message = null
        scope.launch {
            val location = BlinkLocationClient.currentLocation(context, highAccuracy = false)
                .getOrElse {
                    loading = false
                    message = it.message ?: "Current area is unavailable."
                    return@launch
                }
            BlinkItemPreferences.saveWeatherLocation(context, location.latitude, location.longitude)
            repository.fetch(location.latitude, location.longitude)
                .onSuccess {
                    snapshot = it
                    loading = false
                    BlinkItemNotificationManager.evaluateWeather(context, it)
                }
                .onFailure {
                    loading = false
                    message = it.message ?: "Unable to refresh weather."
                }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || BlinkLocationClient.hasCoarsePermission(context)) {
            BlinkItemPreferences.setWeatherEnabled(context, true)
            enabled = true
            requestWeatherNotificationsIfNeeded()
            refreshWeather()
        } else {
            message = "Weather stays off until approximate location is allowed."
        }
    }

    LaunchedEffect(Unit) {
        if (enabled && BlinkLocationClient.hasCoarsePermission(context)) refreshWeather()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = FeedElevatedSurface,
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, FeedBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        text = snapshot?.let { "${it.temperatureC.roundToInt()}°C" } ?: "--°C",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = FeedTextPrimary
                    )
                    Text(snapshot?.condition ?: "Turn on Weather to load conditions", color = FeedTextSecondary)
                    snapshot?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Feels like ${it.feelsLikeC.roundToInt()}°C • Humidity ${it.humidityPercent}% • Wind ${it.windKph.roundToInt()} km/h",
                            style = MaterialTheme.typography.bodySmall,
                            color = FeedTextSecondary
                        )
                        Text(
                            "Rain chance ${it.precipitationProbabilityPercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = FeedTextSecondary
                        )
                        if (it.provider.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Weather source: ${it.provider}",
                                style = MaterialTheme.typography.labelSmall,
                                color = FeedTextSecondary
                            )
                        }
                    }
                }
            }
        }

        item {
            BlinkSettingSwitch(
                title = "Weather",
                subtitle = "Uses approximate location only while Weather is enabled.",
                checked = enabled,
                onCheckedChange = { turnOn ->
                    if (!turnOn) {
                        BlinkItemPreferences.setWeatherEnabled(context, false)
                        enabled = false
                    } else if (BlinkLocationClient.hasCoarsePermission(context)) {
                        BlinkItemPreferences.setWeatherEnabled(context, true)
                        enabled = true
                        requestWeatherNotificationsIfNeeded()
                        refreshWeather()
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                }
            )
        }
        item {
            BlinkSettingSwitch(
                title = "Rain approaching",
                subtitle = "Forecast-based heads-up before likely rainfall.",
                checked = rainAlerts,
                onCheckedChange = {
                    rainAlerts = it
                    BlinkItemPreferences.setRainAlertsEnabled(context, it)
                }
            )
        }
        item {
            BlinkSettingSwitch(
                title = "Official severe-weather alerts",
                subtitle = "Provider-issued warnings are clearly labeled with the issuing source.",
                checked = severeAlerts,
                onCheckedChange = {
                    severeAlerts = it
                    BlinkItemPreferences.setSevereAlertsEnabled(context, it)
                }
            )
        }

        snapshot?.alerts?.takeIf { it.isNotEmpty() }?.let { alerts ->
            item {
                Text(
                    "ACTIVE OFFICIAL ALERTS",
                    color = FeedTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            items(alerts, key = { it.id }) { alert ->
                Surface(
                    color = FeedElevatedSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FeedPurple.copy(alpha = 0.35f))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            alert.title,
                            color = FeedTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (alert.description.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                alert.description,
                                color = FeedTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Source: ${alert.source}",
                            color = FeedTextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { refreshWeather() }, enabled = enabled && !loading) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Refresh")
                }
            }
        }
        message?.let { value -> item { BlinkItemsMessage(value) } }
    }
}

@Composable
private fun BlinkLiveLocationItem(
    profiles: List<UserProfile>,
    currentUserId: String,
    currentUsername: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { BlinkLiveLocationRepository(context) }
    val followingIds by FollowStateStore.followingIds.collectAsState()

    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var durationMinutes by remember { mutableStateOf(60) }
    var currentSession by remember { mutableStateOf<BlinkMyLiveLocationSession?>(null) }
    var sharedLocations by remember { mutableStateOf<List<BlinkSharedLocation>>(emptyList()) }
    var myLocation by remember { mutableStateOf<LatLng?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingStart by remember { mutableStateOf(false) }

    val eligible = remember(profiles, followingIds, currentUserId, currentUsername, query) {
        profiles.asSequence()
            .filter { it.id.isNotBlank() && it.id != currentUserId }
            .filter { it.id in followingIds }
            .filterNot { it.username.equals(currentUsername, ignoreCase = true) }
            .filter {
                query.isBlank() ||
                    it.username.contains(query, ignoreCase = true) ||
                    it.fullName.contains(query, ignoreCase = true)
            }
            .sortedBy { it.fullName.lowercase() }
            .take(80)
            .toList()
    }

    fun refreshMap() {
        scope.launch {
            currentSession = repository.currentSession().getOrNull()
            sharedLocations = repository.activeLocationsSharedWithMe().getOrDefault(emptyList())
            if (BlinkLocationClient.hasFinePermission(context)) {
                BlinkLocationClient.currentLocation(context, highAccuracy = true)
                    .getOrNull()
                    ?.let { myLocation = LatLng(it.latitude, it.longitude) }
            }
        }
    }

    fun startShare() {
        if (selectedIds.isEmpty()) {
            message = "Choose at least one person you follow."
            return
        }
        if (!BlinkLocationClient.hasFinePermission(context)) {
            pendingStart = true
            return
        }
        loading = true
        message = null
        scope.launch {
            val session = repository.startSession(selectedIds, durationMinutes)
                .getOrElse {
                    loading = false
                    message = it.message ?: "Unable to start live location."
                    return@launch
                }

            val location = BlinkLocationClient.currentLocation(context, highAccuracy = true)
                .getOrNull()
            if (location != null) {
                repository.updatePosition(
                    session.id,
                    location.latitude,
                    location.longitude,
                    location.accuracy
                )
                myLocation = LatLng(location.latitude, location.longitude)
            }

            ContextCompat.startForegroundService(
                context,
                Intent(context, BlinkLiveLocationService::class.java).apply {
                    action = BlinkLiveLocationService.ACTION_START
                    putExtra(BlinkLiveLocationService.EXTRA_SESSION_ID, session.id)
                    putExtra(BlinkLiveLocationService.EXTRA_EXPIRES_AT, session.expiresAt)
                }
            )
            currentSession = BlinkMyLiveLocationSession(
                id = session.id,
                expiresAt = session.expiresAt,
                recipientCount = selectedIds.size
            )
            loading = false
            message = "Live Location is sharing only with the selected people."
        }
    }

    val finePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingStart) {
            pendingStart = false
            startShare()
        } else if (!granted) {
            pendingStart = false
            message = "Precise location is required only while you actively share Live Location."
        }
    }

    LaunchedEffect(Unit) {
        FollowStateStore.refresh()
        refreshMap()
        while (true) {
            delay(8_000L)
            sharedLocations = repository.activeLocationsSharedWithMe().getOrDefault(sharedLocations)
            currentSession = repository.currentSession().getOrNull()
        }
    }

    LaunchedEffect(pendingStart) {
        if (pendingStart && !BlinkLocationClient.hasFinePermission(context)) {
            finePermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val initial = myLocation ?: sharedLocations.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
        ?: LatLng(0.0, 0.0)
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initial, if (initial == LatLng(0.0, 0.0)) 2f else 14f)
    }
    val focus = myLocation ?: sharedLocations.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
    LaunchedEffect(focus) {
        focus?.let {
            runCatching { camera.animate(CameraUpdateFactory.newLatLngZoom(it, 14f), 500) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(FeedElevatedSurface)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera
            ) {
                myLocation?.let {
                    Marker(
                        state = MarkerState(position = it),
                        title = "You"
                    )
                }
                sharedLocations.forEach { shared ->
                    Marker(
                        state = MarkerState(position = LatLng(shared.latitude, shared.longitude)),
                        title = shared.fullName.ifBlank { shared.username },
                        snippet = "Shared with you"
                    )
                }
            }
            if (myLocation == null && sharedLocations.isEmpty()) {
                Text(
                    "No live positions to show yet",
                    color = FeedTextSecondary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            currentSession?.let { session ->
                item {
                    Surface(
                        color = FeedPurple.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FeedPurple.copy(alpha = 0.4f))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(
                                "Sharing Live Location",
                                color = FeedTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Visible only to ${session.recipientCount} selected ${if (session.recipientCount == 1) "person" else "people"}.",
                                color = FeedTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Ends ${formatExpiry(session.expiresAt)}",
                                color = FeedTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val id = session.id
                                    currentSession = null
                                    context.startService(
                                        Intent(context, BlinkLiveLocationService::class.java).apply {
                                            action = BlinkLiveLocationService.ACTION_STOP
                                            putExtra(BlinkLiveLocationService.EXTRA_SESSION_ID, id)
                                        }
                                    )
                                }
                            ) {
                                Text("Stop sharing")
                            }
                        }
                    }
                }
            }

            if (sharedLocations.isNotEmpty()) {
                item {
                    Text(
                        "SHARING WITH YOU",
                        color = FeedTextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(sharedLocations, key = { it.sessionId }) { shared ->
                    BlinkSharedLocationRow(shared)
                }
            }

            item {
                Text(
                    "SHARE WITH PEOPLE YOU FOLLOW",
                    color = FeedTextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    label = { Text("Search name or username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (eligible.isEmpty()) {
                item {
                    BlinkItemsMessage(
                        if (query.isBlank()) {
                            "No followed users are available. Live Location is intentionally not shareable with random accounts."
                        } else {
                            "No followed users match your search."
                        }
                    )
                }
            } else {
                items(eligible, key = { it.id }) { profile ->
                    BlinkRecipientRow(
                        profile = profile,
                        checked = profile.id in selectedIds,
                        onChecked = { checked ->
                            selectedIds = if (checked) selectedIds + profile.id else selectedIds - profile.id
                        }
                    )
                }
            }

            item {
                Text(
                    "Sharing duration",
                    color = FeedTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15 to "15 min", 60 to "1 hour", 240 to "4 hours").forEach { option ->
                        if (durationMinutes == option.first) {
                            Button(
                                onClick = { durationMinutes = option.first },
                                modifier = Modifier.weight(1f)
                            ) { Text(option.second) }
                        } else {
                            OutlinedButton(
                                onClick = { durationMinutes = option.first },
                                modifier = Modifier.weight(1f)
                            ) { Text(option.second) }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { startShare() },
                    enabled = selectedIds.isNotEmpty() && !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (currentSession == null) "Start sharing" else "Replace current share")
                }
            }

            item {
                Text(
                    "Your precise location is never placed on Feed, Profile, Search, or a public map. The current coordinate is deleted when sharing stops, and sessions expire automatically.",
                    color = FeedTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            message?.let { value -> item { BlinkItemsMessage(value) } }
        }
    }
}

@Composable
private fun BlinkRecipientRow(
    profile: UserProfile,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Surface(
        color = FeedElevatedSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FeedBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = profile.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(42.dp)
                    .background(FeedBackground, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.fullName.ifBlank { profile.username },
                    color = FeedTextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    "@${profile.username.removePrefix("@")}",
                    color = FeedTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Checkbox(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun BlinkSharedLocationRow(shared: BlinkSharedLocation) {
    Surface(
        color = FeedElevatedSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FeedBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = FeedPurple)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    shared.fullName.ifBlank { shared.username },
                    color = FeedTextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Updated ${formatUpdated(shared.updatedAt)}",
                    color = FeedTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun BlinkSettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = FeedElevatedSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FeedBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = FeedTextPrimary, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    color = FeedTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun BlinkItemsMessage(value: String) {
    Surface(
        color = FeedElevatedSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FeedBorder)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = FeedTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                value,
                color = FeedTextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatExpiry(value: String): String =
    runCatching {
        DateTimeFormatter.ofPattern("h:mm a")
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(value))
    }.getOrDefault("automatically")

private fun formatUpdated(value: String): String =
    runCatching {
        val seconds = (System.currentTimeMillis() - Instant.parse(value).toEpochMilli())
            .coerceAtLeast(0L) / 1_000L
        when {
            seconds < 20 -> "just now"
            seconds < 60 -> "${seconds}s ago"
            else -> "${seconds / 60}m ago"
        }
    }.getOrDefault("recently")
