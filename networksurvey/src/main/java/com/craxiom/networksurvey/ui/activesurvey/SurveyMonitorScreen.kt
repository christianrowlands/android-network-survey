package com.craxiom.networksurvey.ui.activesurvey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.ui.activesurvey.components.BaseSurveyStatusCard
import com.craxiom.networksurvey.ui.activesurvey.components.ConnectionStatusIndicator
import com.craxiom.networksurvey.ui.activesurvey.components.StatRow
import com.craxiom.networksurvey.ui.activesurvey.components.StatusChip
import com.craxiom.networksurvey.ui.activesurvey.components.formatFileSize
import com.craxiom.networksurvey.ui.activesurvey.components.formatTimestamp
import com.craxiom.networksurvey.ui.activesurvey.model.ActiveSurveyState
import com.craxiom.networksurvey.ui.activesurvey.model.SurveyStatus
import com.craxiom.networksurvey.ui.cellular.MapContext
import com.craxiom.networksurvey.ui.cellular.TowerMapScreen
import com.craxiom.networksurvey.ui.cellular.towermap.CameraMode

/**
 * Main screen for monitoring active surveys with status and map views
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyMonitorScreen(
    viewModel: SurveyMonitorViewModel = viewModel(),
    onBackPressed: () -> Unit,
    onNavigateToTowerMapSettings: () -> Unit
) {
    val surveyState by viewModel.surveyState.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    // Handle service connection
    ServiceConnectionHandler(viewModel)

    // Handle screen wake lock
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose {
            view.keepScreenOn = false
        }
    }

    Scaffold(
        topBar = {
            ActiveSurveyTopBar(
                onBackPressed = onBackPressed,
                keepScreenOn = keepScreenOn,
                onKeepScreenOnToggle = { viewModel.setKeepScreenOn(!keepScreenOn) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Status") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Map") }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> SurveyStatusTab(
                    surveyState = surveyState,
                    modifier = Modifier.fillMaxSize()
                )

                1 -> SurveyMapTab(
                    surveyState = surveyState,
                    onNavigateToTowerMapSettings = onNavigateToTowerMapSettings,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveSurveyTopBar(
    onBackPressed: () -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnToggle: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "Survey Monitor",
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = onKeepScreenOnToggle) {
                Icon(
                    imageVector = if (keepScreenOn) {
                        Icons.Filled.Lock
                    } else {
                        Icons.Outlined.Lock
                    },
                    contentDescription = if (keepScreenOn) {
                        "Screen will stay on"
                    } else {
                        "Screen may turn off"
                    }
                )
            }
        }
    )
}

@Composable
private fun SurveyStatusTab(
    surveyState: ActiveSurveyState,
    modifier: Modifier = Modifier
) {
    if (!surveyState.isAnyActive) {
        // Show empty state when no surveys are active
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "No Active Surveys",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Start a survey from the Dashboard to see status here",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        // Show active survey cards
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // File Logging Card
            surveyState.fileLoggingStatus?.let { status ->
                FileLoggingStatusCard(
                    status = status,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // MQTT Streaming Card
            surveyState.mqttStreamingStatus?.let { status ->
                MqttStreamingStatusCard(
                    status = status,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Upload Survey Card
            surveyState.uploadSurveyStatus?.let { status ->
                UploadSurveyStatusCard(
                    status = status,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // TODO Add a gRPC streaming card
        }
    }
}

@Composable
private fun SurveyMapTab(
    surveyState: ActiveSurveyState,
    onNavigateToTowerMapSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Use TowerMapScreen with Survey Monitor context and specific defaults
    TowerMapScreen(
        onBackButtonPressed = { /* Tab already has back button */ },
        onNavigateToTowerMapSettings = onNavigateToTowerMapSettings,
        mapContext = MapContext.SURVEY_MONITOR,
        surveyTracks = surveyState.currentTrack?.let { listOf(it) },
        initialBeaconDbEnabled = true, // Default BeaconDB coverage to ON for survey monitoring
        initialShowTowers = true,
        initialCameraMode = CameraMode.TRACKING
    )
}

@Composable
private fun FileLoggingStatusCard(
    status: SurveyStatus,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val fileInfo = status.fileInfo

    BaseSurveyStatusCard(
        title = "File Logging",
        icon = Icons.Default.Build, // TODO replace with a more appropriate icon
        isActive = status.isActive,
        isExpanded = isExpanded,
        onExpandToggle = { isExpanded = !isExpanded },
        modifier = modifier,
        headerContent = {
            if (status.isActive && fileInfo != null) {
                val activeCount = listOfNotNull(
                    if (fileInfo.csvEnabled) "CSV" else null,
                    if (fileInfo.geoPackageEnabled) "GeoPackage" else null
                ).size
                Text(
                    text = "$activeCount Active",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        if (fileInfo != null) {
            // File types row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusChip(
                    text = "CSV",
                    isActive = fileInfo.csvEnabled
                )
                StatusChip(
                    text = "GeoPackage",
                    isActive = fileInfo.geoPackageEnabled
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // CSV stats
            if (fileInfo.csvEnabled) {
                Text(
                    text = "CSV File",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                StatRow(
                    label = "Size:",
                    value = formatFileSize(fileInfo.csvFileSize)
                )
                StatRow(
                    label = "Records:",
                    value = fileInfo.csvRecordCount.toString()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // GeoPackage stats
            if (fileInfo.geoPackageEnabled) {
                Text(
                    text = "GeoPackage",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                StatRow(
                    label = "Size:",
                    value = formatFileSize(fileInfo.geoPackageFileSize)
                )
                StatRow(
                    label = "Records:",
                    value = fileInfo.geoPackageRecordCount.toString()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Active protocols
            if (fileInfo.activeProtocols.isNotEmpty()) {
                Text(
                    text = "Active Protocols",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    fileInfo.activeProtocols.forEach { protocol ->
                        StatusChip(
                            text = protocol,
                            isActive = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MqttStreamingStatusCard(
    status: SurveyStatus,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val mqttInfo = status.mqttInfo

    BaseSurveyStatusCard(
        title = "MQTT Streaming",
        icon = Icons.Default.Build, // TODO replace with a more appropriate icon
        isActive = status.isActive,
        isExpanded = isExpanded,
        onExpandToggle = { isExpanded = !isExpanded },
        modifier = modifier,
        headerContent = {
            if (mqttInfo != null) {
                ConnectionStatusIndicator(
                    state = mqttInfo.connectionState
                )
            }
        }
    ) {
        if (mqttInfo != null) {
            // Broker info
            if (!mqttInfo.brokerAddress.isNullOrEmpty()) {
                StatRow(
                    label = "Broker:",
                    value = mqttInfo.brokerAddress
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Connection stats
            StatRow(
                label = "Messages Sent:",
                value = mqttInfo.messagesSent.toString()
            )
            StatRow(
                label = "Last Message:",
                value = formatTimestamp(mqttInfo.lastMessageTime)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Active protocols
            if (mqttInfo.activeProtocols.isNotEmpty()) {
                Text(
                    text = "Streaming Protocols",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    mqttInfo.activeProtocols.forEach { protocol ->
                        StatusChip(
                            text = protocol,
                            isActive = true
                        )
                    }
                }
            }

            // Error message if any
            if (!mqttInfo.errorMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = mqttInfo.errorMessage,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadSurveyStatusCard(
    status: SurveyStatus,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val uploadInfo = status.uploadInfo

    BaseSurveyStatusCard(
        title = "Upload Survey",
        icon = Icons.Default.Build, // TODO replace with a more appropriate icon
        isActive = status.isActive,
        isExpanded = isExpanded,
        onExpandToggle = { isExpanded = !isExpanded },
        modifier = modifier,
        headerContent = {
            if (uploadInfo != null && uploadInfo.isUploading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Uploading",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    ) {
        if (uploadInfo != null) {
            // OpenCelliD stats
            Text(
                text = "OpenCelliD",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            StatRow(
                label = "Pending:",
                value = uploadInfo.openCellidPending.toString()
            )
            StatRow(
                label = "Uploaded:",
                value = uploadInfo.openCellidUploaded.toString()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // BeaconDB stats
            Text(
                text = "BeaconDB",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            StatRow(
                label = "Pending:",
                value = uploadInfo.beaconDbPending.toString()
            )
            StatRow(
                label = "Uploaded:",
                value = uploadInfo.beaconDbUploaded.toString()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Last upload time
            StatRow(
                label = "Last Upload:",
                value = formatTimestamp(uploadInfo.lastUploadTime)
            )

            // Progress indicator if uploading
            if (uploadInfo.isUploading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}