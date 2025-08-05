package com.craxiom.networksurvey.ui.activesurvey

import android.content.SharedPreferences
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.ui.activesurvey.model.ActiveSurveyState
import com.craxiom.networksurvey.ui.cellular.MapContext
import com.craxiom.networksurvey.ui.cellular.TowerMapScreen
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
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
    val servingCellInfo by viewModel.servingCellInfo.collectAsStateWithLifecycle()
    val isNewTowerDetected by viewModel.isNewTowerDetected.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // New tower alert states - load from SharedPreferences
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var isNewTowerAlertsEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                NetworkSurveyConstants.PROPERTY_NEW_TOWER_ALERTS_ENABLED,
                false
            )
        )
    }

    // Observe preference changes to keep UI in sync with global preference
    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == NetworkSurveyConstants.PROPERTY_NEW_TOWER_ALERTS_ENABLED) {
                isNewTowerAlertsEnabled = prefs.getBoolean(key, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    // Handle service connection
    ServiceConnectionHandler(viewModel)

    // Initialize TowerDetectionManager with context
    LaunchedEffect(Unit) {
        viewModel.initializeTowerDetectionManager(context)
    }

    // Check for new towers for UI visual feedback only (NEW badge)
    // Notifications are handled by NetworkSurveyService
    LaunchedEffect(servingCellInfo, surveyState.isUploadActive) {
        // Only check for UI feedback when upload scanning is active
        val shouldCheckForNewTowers = surveyState.isUploadActive

        viewModel.checkServingCellForNewTower(
            servingCellInfo = servingCellInfo,
            isNewTowerAlertsEnabled = shouldCheckForNewTowers,
            onNewTowerDetected = {
                // UI feedback is handled by the isNewTowerDetected state
                // Notifications are now handled by the service
            }
        )
    }

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
            Box(modifier = Modifier.fillMaxSize()) {
                // Status tab with animated visibility
                androidx.compose.animation.AnimatedVisibility(
                    visible = selectedTab == 0,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SurveyStatusTab(
                        surveyState = surveyState,
                        servingCellInfo = servingCellInfo,
                        isNewTowerAlertsEnabled = isNewTowerAlertsEnabled,
                        onNewTowerAlertsToggle = { enabled ->
                            // Update the preference (UI will update via the listener)
                            prefs.edit {
                                putBoolean(
                                    NetworkSurveyConstants.PROPERTY_NEW_TOWER_ALERTS_ENABLED,
                                    enabled
                                )
                            }
                        },
                        isNewTowerDetected = isNewTowerDetected,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Map tab with animated visibility
                androidx.compose.animation.AnimatedVisibility(
                    visible = selectedTab == 1,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SurveyMapTab(
                        surveyState = surveyState,
                        onNavigateToTowerMapSettings = onNavigateToTowerMapSettings
                    )
                }
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
    servingCellInfo: ServingCellInfo?,
    isNewTowerAlertsEnabled: Boolean,
    onNewTowerAlertsToggle: (Boolean) -> Unit,
    isNewTowerDetected: Boolean,
    modifier: Modifier = Modifier
) {
    // State for New Tower Alerts help dialog
    var showNewTowerAlertsHelpDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Section - Status and Statistics
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Compact Status Indicator and Text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                CompactSurveyStatusIndicator(
                    isActive = surveyState.isAnyActive,
                    isNewTowerDetected = isNewTowerDetected
                )

                Text(
                    text = if (surveyState.isAnyActive) "Survey Running" else "Survey Stopped",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (surveyState.isAnyActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            // Statistics at the top
            if (surveyState.isAnyActive) {
                SurveyStatistics(
                    surveyState = surveyState,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }

        // Spacer to push bottom content down
        Spacer(modifier = Modifier.weight(1f))

        // Bottom Section - Serving Cell and Alerts (only show when survey is active)
        if (surveyState.isAnyActive) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Serving Cell Card with enhanced styling
                ServingCellCard(
                    servingCellInfo = servingCellInfo,
                    isNewTowerDetected = isNewTowerDetected,
                    modifier = Modifier
                )

                // New Tower Alerts integrated with bottom section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 0.dp
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_cell_tower),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isNewTowerAlertsEnabled && surveyState.isUploadActive) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = "New Tower Alerts",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (surveyState.isUploadActive) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                IconButton(
                                    onClick = { showNewTowerAlertsHelpDialog = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_info),
                                        contentDescription = "Help",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!surveyState.isUploadActive) {
                                Text(
                                    text = "Requires upload scanning",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 4.dp, start = 28.dp)
                                )
                            } else if (isNewTowerAlertsEnabled) {
                                Text(
                                    text = "Alerts when new towers are detected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp, start = 28.dp)
                                )
                            }
                        }
                        Switch(
                            checked = isNewTowerAlertsEnabled,
                            onCheckedChange = onNewTowerAlertsToggle,
                            enabled = surveyState.isUploadActive,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }

    // New Tower Alerts Help Dialog
    if (showNewTowerAlertsHelpDialog) {
        AlertDialog(
            onDismissRequest = { showNewTowerAlertsHelpDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.new_tower_alerts_help_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // What are New Tower Alerts?
                    Column {
                        Text(
                            text = stringResource(R.string.new_tower_alerts_what),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.new_tower_alerts_what_desc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // How it works
                    Column {
                        Text(
                            text = stringResource(R.string.new_tower_alerts_how),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.new_tower_alerts_how_desc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Requirements
                    Column {
                        Text(
                            text = stringResource(R.string.new_tower_alerts_requirements),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.new_tower_alerts_requirements_desc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Benefits
                    Column {
                        Text(
                            text = stringResource(R.string.new_tower_alerts_benefits),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.new_tower_alerts_benefits_desc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNewTowerAlertsHelpDialog = false }) {
                    Text(stringResource(R.string.help_dialog_got_it))
                }
            }
        )
    }
}

@Composable
private fun SurveyMapTab(
    surveyState: ActiveSurveyState,
    onNavigateToTowerMapSettings: () -> Unit
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

// Data class for particles
private data class Particle(
    val initialY: Float,
    val x: Float,
    val size: Float,
    val velocity: Float
)

@Composable
private fun SurveyStatusIndicator(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    // Particle system state
    val particles = remember {
        List(12) {  // Reduced from 20 to 12 for better performance
            Particle(
                initialY = (0..100).random() / 100f,
                x = (10..90).random() / 100f,  // Wider distribution
                size = (4..8).random() / 100f,
                velocity = (12..24).random() / 100f  // 8x faster than original
            )
        }
    }

    // Animation time state - this drives the animation
    var animationTime by remember { mutableFloatStateOf(0f) }
    var fallTime by remember { mutableFloatStateOf(0f) }

    // Animate based on active state
    LaunchedEffect(isActive) {
        if (isActive) {
            // Reset fall time when becoming active
            fallTime = 0f
            // Continue from current animation time
            while (isActive) {
                animationTime += 0.033f // ~30 FPS (33ms per frame) for better performance
                kotlinx.coroutines.delay(33)
            }
        } else {
            // When stopped, animate particles falling
            repeat(50) {  // Reduced iterations since we have longer delays
                fallTime += 0.033f
                kotlinx.coroutines.delay(33)
            }
        }
    }

    // Colors
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error

    Box(
        modifier = modifier
            .size(width = 180.dp, height = 240.dp),
        contentAlignment = Alignment.BottomCenter
    ) {

        // Canvas for particles
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val width = size.width
            val height = size.height

            particles.forEach { particle ->
                val xPos = width * particle.x
                val particleSize = width * particle.size

                // Calculate Y position based on animation state
                val yPos = if (isActive) {
                    // Moving upward: calculate position based on time and velocity
                    val distance = animationTime * particle.velocity
                    val currentY = (particle.initialY - distance) % 1f
                    val wrappedY = if (currentY < 0) currentY + 1f else currentY
                    height * wrappedY
                } else {
                    // Falling down: particles settle at bottom
                    val fallY = particle.initialY + (fallTime * particle.velocity * 4)
                    height * minOf(fallY, 0.95f)
                }

                // Draw particle with simplified trail effect
                if (isActive && yPos < height * 0.9f) {
                    // Simplified trail - only 2 circles instead of 5
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.1f),
                        radius = particleSize / 2,
                        center = Offset(xPos, yPos + particleSize)
                    )
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.05f),
                        radius = particleSize / 2,
                        center = Offset(xPos, yPos + (2 * particleSize))
                    )
                }

                // Main particle
                val normalizedY = yPos / height
                drawCircle(
                    color = if (isActive) {
                        primaryColor.copy(alpha = 1f - normalizedY)
                    } else {
                        errorColor.copy(alpha = 0.3f)
                    },
                    radius = particleSize / 2,
                    center = Offset(xPos, yPos)
                )

                // Glow effect removed for better performance
            }
        }

    }
}


@Composable
private fun SurveyStatistics(
    surveyState: ActiveSurveyState,
    modifier: Modifier = Modifier
) {
    // Pass viewModel to access session data
    val viewModel: SurveyMonitorViewModel = viewModel()

    // Calculate elapsed time from session start
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(surveyState.isAnyActive) {
        if (surveyState.isAnyActive) {
            while (surveyState.isAnyActive) {
                val sessionStartTime = viewModel.getSurveySessionStartTime()
                if (sessionStartTime != null) {
                    elapsedSeconds =
                        ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
                }
                kotlinx.coroutines.delay(1000)
            }
        } else {
            elapsedSeconds = 0
        }
    }

    // Get total records from session with animation
    val totalRecords = viewModel.getSurveySessionRecordCount()
    val uploadRecords = viewModel.getSurveySessionUploadRecordCount()
    val isUploadActive = surveyState.isUploadActive
    val hasNonUploadSurvey = surveyState.hasNonUploadSurvey
    val activeSurveyTypes = surveyState.activeSurveyTypes

    // Animated values for smooth transitions
    val animatedTotalRecords by animateIntAsState(
        targetValue = totalRecords,
        animationSpec = tween(300),
        label = "total_records"
    )
    val animatedUploadRecords by animateIntAsState(
        targetValue = uploadRecords,
        animationSpec = tween(300),
        label = "upload_records"
    )

    // State for help dialogs
    var showHelpDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with help icon
        Row(
            modifier = Modifier.padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.survey_statistics_header),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(
                onClick = { showHelpDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_help),
                    contentDescription = "Help",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Statistics Cards Grid
        // Show different layouts based on active survey types
        if (hasNonUploadSurvey && isUploadActive) {
            // Both survey types active - show only Time Elapsed card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 1.dp
                ),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Time Elapsed",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AnimatedContent(
                        targetState = formatElapsedTime(elapsedSeconds),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith
                                    fadeOut(animationSpec = tween(300))
                        },
                        label = "elapsed_time"
                    ) { time ->
                        Text(
                            text = time,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            // Single survey type active - show Time Elapsed and Records side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Elapsed Time Card
                ElevatedCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 1.dp
                    ),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Time Elapsed",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AnimatedContent(
                            targetState = formatElapsedTime(elapsedSeconds),
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300)) togetherWith
                                        fadeOut(animationSpec = tween(300))
                            },
                            label = "elapsed_time"
                        ) { time ->
                            Text(
                                text = time,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Records Card (show either total or upload based on survey type)
                if (hasNonUploadSurvey || isUploadActive) {
                    ElevatedCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = 1.dp
                        ),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (isUploadActive && !hasNonUploadSurvey) {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Removed icon to match Time Elapsed card height
                            Text(
                                text = if (isUploadActive && !hasNonUploadSurvey) {
                                    stringResource(R.string.ready_for_upload_label)
                                } else {
                                    stringResource(R.string.records_captured_label)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isUploadActive && !hasNonUploadSurvey) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = String.format(
                                    "%,d",
                                    if (isUploadActive && !hasNonUploadSurvey) {
                                        animatedUploadRecords
                                    } else {
                                        animatedTotalRecords
                                    }
                                ),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isUploadActive && !hasNonUploadSurvey) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }

        // Show both counters if both survey types are active
        if (hasNonUploadSurvey && isUploadActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Regular Records Mini Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 0.dp
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_schema),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column {
                            Text(
                                text = "Logged",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format("%,d", animatedTotalRecords),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Upload Records Mini Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 0.dp
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_upload_24),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Column {
                            Text(
                                text = "To Upload",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = String.format("%,d", animatedUploadRecords),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }

        // Active survey types indicator
        if (activeSurveyTypes.isNotEmpty()) {
            Text(
                text = "Active: ${activeSurveyTypes.joinToString(", ")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(text = stringResource(R.string.help_dialog_title))
            },
            text = {
                val helpText = if (hasNonUploadSurvey && isUploadActive) {
                    // Both counts are shown
                    stringResource(R.string.help_dialog_both_counts)
                } else {
                    // Only upload count is shown
                    stringResource(R.string.help_dialog_upload_only)
                }
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(text = helpText)
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(stringResource(R.string.help_dialog_got_it))
                }
            }
        )
    }
}

private fun formatElapsedTime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}

@Composable
private fun CompactSurveyStatusIndicator(
    isActive: Boolean,
    isNewTowerDetected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status")

    // Pulsing animation for active state
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Ripple scale animation
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ripple"
    )

    // Color animation for new tower detected
    val indicatorColor by animateColorAsState(
        targetValue = when {
            isNewTowerDetected -> MaterialTheme.colorScheme.tertiary
            isActive -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.error
        },
        animationSpec = tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "color"
    )

    Box(
        modifier = modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing rings when active
        if (isActive) {
            // First ripple
            Canvas(
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer(
                        scaleX = rippleScale,
                        scaleY = rippleScale
                    )
            ) {
                drawCircle(
                    color = indicatorColor,
                    radius = size.minDimension / 2,
                    alpha = pulseAlpha * 0.2f
                )
            }

            // Second ripple
            Canvas(
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer(
                        scaleX = rippleScale * 0.8f,
                        scaleY = rippleScale * 0.8f
                    )
            ) {
                drawCircle(
                    color = indicatorColor,
                    radius = size.minDimension / 2,
                    alpha = pulseAlpha * 0.3f
                )
            }
        }

        // Inner dot with glow
        Canvas(
            modifier = Modifier.size(14.dp)
        ) {
            // Glow effect
            drawCircle(
                color = indicatorColor,
                radius = size.minDimension / 2 + 2.dp.toPx(),
                alpha = 0.3f
            )

            // Core dot
            drawCircle(
                color = indicatorColor,
                radius = size.minDimension / 2
            )
        }
    }
}

@Composable
private fun ServingCellCard(
    servingCellInfo: ServingCellInfo?,
    isNewTowerDetected: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isNewTowerDetected) {
            MaterialTheme.colorScheme.tertiary
        } else {
            Color.Transparent
        },
        animationSpec = tween(600),
        label = "border"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isNewTowerDetected) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(600),
        label = "container"
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(
                if (isNewTowerDetected) {
                    Modifier.border(
                        width = 2.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(16.dp)
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_cell_tower),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isNewTowerDetected) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Text(
                    text = "Serving Cell",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
                if (isNewTowerDetected) {
                    Spacer(modifier = Modifier.weight(1f))

                    // Animated NEW badge with pulse effect
                    val infiniteTransition = rememberInfiniteTransition(label = "new_badge")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                2000,
                                easing = androidx.compose.animation.core.EaseInOutCubic
                            ),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "new_badge_scale"
                    )
                    val glowAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 0.7f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "new_badge_glow"
                    )

                    Box(contentAlignment = Alignment.Center) {
                        // Glow effect
                        Card(
                            modifier = Modifier
                                .graphicsLayer(
                                    scaleX = scale * 1.2f,
                                    scaleY = scale * 1.2f,
                                    alpha = glowAlpha * 0.5f
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(
                                    text = "NEW",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Transparent
                                )
                            }
                        }

                        // Main badge
                        Card(
                            modifier = Modifier.graphicsLayer(
                                scaleX = scale,
                                scaleY = scale
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 2.dp
                            )
                        ) {
                            Text(
                                text = "NEW",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (servingCellInfo?.servingCell != null) {
                val wrapper = servingCellInfo.servingCell
                val protocol = wrapper.cellularProtocol
                val record = wrapper.cellularRecord

                // Extract common fields based on protocol
                val cellInfo = when (protocol) {
                    com.craxiom.networksurvey.model.CellularProtocol.LTE -> {
                        val lte = record as com.craxiom.messaging.LteRecord
                        val data = lte.data
                        listOf(
                            "LTE", data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                            data.tac?.value ?: 0, data.eci?.value?.toLong() ?: 0L,
                            data.rsrp?.value?.toInt()?.toString() ?: "---"
                        )
                    }

                    com.craxiom.networksurvey.model.CellularProtocol.NR -> {
                        val nr = record as com.craxiom.messaging.NrRecord
                        val data = nr.data
                        listOf(
                            "5G NR", data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                            data.tac?.value ?: 0, data.nci?.value ?: 0L,
                            data.ssRsrp?.value?.toInt()?.toString() ?: "---"
                        )
                    }

                    com.craxiom.networksurvey.model.CellularProtocol.GSM -> {
                        val gsm = record as com.craxiom.messaging.GsmRecord
                        val data = gsm.data
                        listOf(
                            "GSM", data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                            data.lac?.value ?: 0, data.ci?.value?.toLong() ?: 0L,
                            data.signalStrength?.value?.toInt()?.toString() ?: "---"
                        )
                    }

                    com.craxiom.networksurvey.model.CellularProtocol.UMTS -> {
                        val umts = record as com.craxiom.messaging.UmtsRecord
                        val data = umts.data
                        listOf(
                            "UMTS", data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                            data.lac?.value ?: 0, data.cid?.value?.toLong() ?: 0L,
                            data.rscp?.value?.toInt()?.toString() ?: "---"
                        )
                    }

                    else -> listOf("Unknown", 0, 0, 0, 0L, "---")
                }

                val technology = cellInfo[0] as String
                val mcc = (cellInfo[1] as Number).toInt()
                val mnc = (cellInfo[2] as Number).toInt()
                val area = (cellInfo[3] as Number).toInt()
                val cellId = (cellInfo[4] as Number).toLong()
                val signalStrength = cellInfo[5] as String

                // Technology and Signal Row with enhanced styling
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TECHNOLOGY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Technology badge
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = technology,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = when (technology) {
                                                "5G NR", "LTE" -> MaterialTheme.colorScheme.onPrimary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(
                                                horizontal = 8.dp,
                                                vertical = 4.dp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SIGNAL",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Column(horizontalAlignment = Alignment.Start) {
                                // Signal strength bars visualization
                                val signalValue = signalStrength.toIntOrNull() ?: -999
                                val signalBars = when {
                                    signalValue > -70 -> 4 // Excellent
                                    signalValue > -85 -> 3 // Good
                                    signalValue > -100 -> 2 // Fair
                                    signalValue > -110 -> 1 // Poor
                                    else -> 0 // No signal
                                }
                                val signalColor = when (signalBars) {
                                    4 -> Color(0xFF4CAF50) // Green
                                    3 -> Color(0xFF8BC34A) // Light Green
                                    2 -> Color(0xFFFFC107) // Yellow
                                    1 -> Color(0xFFFF9800) // Orange
                                    else -> Color(0xFFF44336) // Red
                                }

                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

                                    for (i in 1..4) {
                                        val barHeight = (i * 5 + 2).dp
                                        val isActive = i <= signalBars
                                        val barAlpha by animateFloatAsState(
                                            targetValue = if (isActive) 1f else 0.3f,
                                            animationSpec = tween(200),
                                            label = "bar_alpha_$i"
                                        )

                                        Canvas(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(barHeight)
                                        ) {
                                            drawRoundRect(
                                                color = if (isActive) signalColor else inactiveColor,
                                                alpha = barAlpha,
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                                    2.dp.toPx()
                                                ),
                                                size = androidx.compose.ui.geometry.Size(
                                                    size.width,
                                                    size.height
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Text(
                                        text = "$signalStrength dBm",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Network and Area Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp, start = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NETWORK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Text(
                            text = "$mcc-$mnc",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (protocol == com.craxiom.networksurvey.model.CellularProtocol.GSM ||
                                protocol == com.craxiom.networksurvey.model.CellularProtocol.UMTS
                            ) "LAC" else "TAC",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Text(
                            text = area.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Cell ID with enhanced styling
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "CELL ID",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Text(
                            text = cellId.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                // Loading state
                ServingCellCardSkeleton()
            }
        }
    }
}

@Composable
private fun ServingCellCardSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    Column {
        // Technology and Signal Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TECHNOLOGY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .fillMaxWidth(0.6f)
                        .background(shimmerColor, RoundedCornerShape(4.dp))
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SIGNAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .fillMaxWidth(0.7f)
                        .background(shimmerColor, RoundedCornerShape(4.dp))
                )
            }
        }

        // Network and Area Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "NETWORK",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .fillMaxWidth(0.5f)
                        .background(shimmerColor, RoundedCornerShape(4.dp))
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TAC",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .fillMaxWidth(0.4f)
                        .background(shimmerColor, RoundedCornerShape(4.dp))
                )
            }
        }

        // Cell ID
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "CELL ID",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Box(
                modifier = Modifier
                    .height(16.dp)
                    .fillMaxWidth(0.8f)
                    .background(shimmerColor, RoundedCornerShape(4.dp))
            )
        }
    }
}