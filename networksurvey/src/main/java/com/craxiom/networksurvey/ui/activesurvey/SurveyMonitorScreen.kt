package com.craxiom.networksurvey.ui.activesurvey

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.ui.activesurvey.model.ActiveSurveyState
import com.craxiom.networksurvey.ui.cellular.MapContext
import com.craxiom.networksurvey.ui.cellular.TowerMapScreen
import com.craxiom.networksurvey.ui.cellular.towermap.CameraMode
import kotlinx.coroutines.delay

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

    var selectedTab by remember { mutableIntStateOf(0) }

    // Handle service connection
    ServiceConnectionHandler(viewModel)

    // Keep screen always on when viewing Survey Monitor
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    Scaffold(
        topBar = {
            ActiveSurveyTopBar(
                onBackPressed = onBackPressed
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
    onBackPressed: () -> Unit
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
        }
    )
}

@Composable
private fun SurveyStatusTab(
    surveyState: ActiveSurveyState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Status Indicator
            SurveyStatusIndicator(
                isActive = surveyState.isAnyActive
            )
            
            // Status Text
            Text(
                text = if (surveyState.isAnyActive) "SCANNING" else "STOPPED",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (surveyState.isAnyActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                textAlign = TextAlign.Center
            )
            
            // Statistics when active
            if (surveyState.isAnyActive) {
                SurveyStatistics(
                    surveyState = surveyState
                )
            }
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
        // Don't override the saved preferences - let TowerMapScreen load them based on context
        initialCameraMode = CameraMode.TRACKING
    )
}

@Composable
private fun SurveyStatusIndicator(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "background_color"
    )
    
    Box(
        modifier = modifier
            .size(200.dp)
            .scale(if (isActive) scale else 1f)
            .background(
                color = backgroundColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        // Inner circle for visual depth
        Box(
            modifier = Modifier
                .size(160.dp)
                .background(
                    color = backgroundColor.copy(alpha = 0.7f),
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun SurveyStatistics(
    surveyState: ActiveSurveyState,
    modifier: Modifier = Modifier
) {
    // Calculate elapsed time
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(surveyState.isAnyActive) {
        if (surveyState.isAnyActive) {
            val startTime = System.currentTimeMillis()
            while (surveyState.isAnyActive) {
                elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                kotlinx.coroutines.delay(1000)
            }
        } else {
            elapsedSeconds = 0
        }
    }
    
    // Calculate total records
    val totalRecords = calculateTotalRecords(surveyState)
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Elapsed Time
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Time Elapsed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatElapsedTime(elapsedSeconds),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Total Records
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total Records",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format("%,d", totalRecords),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatElapsedTime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}

private fun calculateTotalRecords(surveyState: ActiveSurveyState): Int {
    var total = 0
    
    // Add file logging records
    surveyState.fileLoggingStatus?.fileInfo?.let { fileInfo ->
        total += (fileInfo.csvRecordCount + fileInfo.geoPackageRecordCount).toInt()
    }
    
    // Add MQTT messages sent
    surveyState.mqttStreamingStatus?.mqttInfo?.let { mqttInfo ->
        total += mqttInfo.messagesSent.toInt()
    }
    
    // Add upload records
    surveyState.uploadSurveyStatus?.uploadInfo?.let { uploadInfo ->
        total += (uploadInfo.openCellidUploaded + uploadInfo.beaconDbUploaded).toInt()
    }
    
    return total
}