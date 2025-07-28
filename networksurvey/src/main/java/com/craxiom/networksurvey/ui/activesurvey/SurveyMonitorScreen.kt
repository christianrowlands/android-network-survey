package com.craxiom.networksurvey.ui.activesurvey

import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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

    // Sound playback
    val context = LocalContext.current
    val notificationSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

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

    var selectedTab by remember { mutableIntStateOf(0) }

    // Handle service connection
    ServiceConnectionHandler(viewModel)

    // Initialize TowerDetectionManager with context
    LaunchedEffect(Unit) {
        viewModel.initializeTowerDetectionManager(context)
    }

    // Check for new towers when serving cell changes and upload scanning is active
    LaunchedEffect(servingCellInfo, isNewTowerAlertsEnabled, surveyState.isUploadActive) {
        // Only check if upload scanning is active AND user has enabled alerts
        val shouldCheckForNewTowers = surveyState.isUploadActive && isNewTowerAlertsEnabled

        viewModel.checkServingCellForNewTower(
            servingCellInfo = servingCellInfo,
            isNewTowerAlertsEnabled = shouldCheckForNewTowers,
            onNewTowerDetected = {
                // Play notification sound when new tower is detected
                // Only play if the Survey Monitor screen is visible
                try {
                    val ringtone = RingtoneManager.getRingtone(context, notificationSound)
                    ringtone?.play()
                } catch (_: Exception) {
                    // Handle error silently - don't crash if sound fails
                }

                // Show notification with tower details
                servingCellInfo?.servingCell?.let { wrapper ->
                    val protocol = wrapper.cellularProtocol
                    val record = wrapper.cellularRecord

                    val (technology, mcc, mnc, area, cellId) = when (protocol) {
                        com.craxiom.networksurvey.model.CellularProtocol.LTE -> {
                            val lte = record as com.craxiom.messaging.LteRecord
                            val data = lte.data
                            listOf(
                                "LTE", data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                                data.tac?.value ?: 0, data.eci?.value ?: 0L
                            )
                        }

                        com.craxiom.networksurvey.model.CellularProtocol.NR -> {
                            val nr = record as com.craxiom.messaging.NrRecord
                            val data = nr.data
                            listOf(
                                "5G NR", data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                                data.tac?.value ?: 0, data.nci?.value ?: 0L
                            )
                        }

                        com.craxiom.networksurvey.model.CellularProtocol.GSM -> {
                            val gsm = record as com.craxiom.messaging.GsmRecord
                            val data = gsm.data
                            listOf(
                                "GSM", data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                                data.lac?.value ?: 0, data.ci?.value ?: 0L
                            )
                        }

                        com.craxiom.networksurvey.model.CellularProtocol.UMTS -> {
                            val umts = record as com.craxiom.messaging.UmtsRecord
                            val data = umts.data
                            listOf(
                                "UMTS", data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                                data.lac?.value ?: 0, data.cid?.value ?: 0L
                            )
                        }

                        else -> return@let
                    }

                    NewTowerNotificationHelper.showNewTowerNotification(
                        context = context,
                        mcc = mcc as Int,
                        mnc = mnc as Int,
                        area = area as Int,
                        cellId = cellId as Long,
                        technology = technology as String
                    )
                }
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
                            isNewTowerAlertsEnabled = enabled
                            // Save to SharedPreferences
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Compact Status Indicator and Text
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 24.dp)
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

        // Serving Cell Details (always show card space)
            ServingCellCard(
                servingCellInfo = servingCellInfo,
                isNewTowerDetected = isNewTowerDetected,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // New Tower Alerts Toggle
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "New Tower Alerts",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Switch(
                            checked = isNewTowerAlertsEnabled,
                            onCheckedChange = onNewTowerAlertsToggle,
                            enabled = surveyState.isUploadActive
                        )
                    }

                    // Show explanatory text when disabled
                    if (!surveyState.isUploadActive) {
                        Text(
                            text = "Enable upload scanning to detect new towers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

        // Statistics when active
        if (surveyState.isAnyActive) {
            SurveyStatistics(
                surveyState = surveyState,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
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

    // Get total records from session
    val totalRecords = viewModel.getSurveySessionRecordCount()
    val uploadRecords = viewModel.getSurveySessionUploadRecordCount()
    val isUploadActive = surveyState.isUploadActive
    val hasNonUploadSurvey = surveyState.hasNonUploadSurvey
    val activeSurveyTypes = surveyState.activeSurveyTypes

    // State for help dialog
    var showHelpDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with help icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.survey_statistics_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { showHelpDialog = true }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_help),
                    contentDescription = "Help",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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

        // Total Records (only show if non-upload surveys are active)
        if (hasNonUploadSurvey) {
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
                        text = stringResource(R.string.records_captured_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Show active survey types as subtitle
                    if (activeSurveyTypes.isNotEmpty()) {
                        Text(
                            text = activeSurveyTypes.joinToString(" & "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = String.format("%,d", totalRecords),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Upload Records (only show if upload is active)
        if (isUploadActive) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.ready_for_upload_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = String.format("%,d", uploadRecords),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
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
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Color animation for new tower detected
    val indicatorColor by animateColorAsState(
        targetValue = when {
            isNewTowerDetected -> MaterialTheme.colorScheme.tertiary
            isActive -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.error
        },
        animationSpec = tween(500),
        label = "color"
    )

    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring when active
        if (isActive) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawCircle(
                    color = indicatorColor,
                    radius = size.minDimension / 2,
                    alpha = pulseAlpha * 0.3f
                )
            }
        }

        // Inner dot
        Canvas(
            modifier = Modifier.size(12.dp)
        ) {
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
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(500),
        label = "border"
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .then(
                if (isNewTowerDetected) {
                    Modifier.border(
                        width = 2.dp,
                        color = borderColor,
                        shape = MaterialTheme.shapes.medium
                    )
                } else Modifier
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isNewTowerDetected) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Serving Cell",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

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
                            data.tac?.value ?: 0, data.eci?.value ?: 0L,
                            data.rsrp?.value?.let { "$it" } ?: "---"
                        )
                    }

                    com.craxiom.networksurvey.model.CellularProtocol.NR -> {
                        val nr = record as com.craxiom.messaging.NrRecord
                        val data = nr.data
                        listOf(
                            "5G NR", data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                            data.tac?.value ?: 0, data.nci?.value ?: 0L,
                            data.ssRsrp?.value?.let { "$it" } ?: "---"
                        )
                    }

                    com.craxiom.networksurvey.model.CellularProtocol.GSM -> {
                        val gsm = record as com.craxiom.messaging.GsmRecord
                        val data = gsm.data
                        listOf(
                            "GSM", data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                            data.lac?.value ?: 0, data.ci?.value ?: 0L,
                            data.signalStrength?.value?.let { "$it" } ?: "---"
                        )
                    }

                    com.craxiom.networksurvey.model.CellularProtocol.UMTS -> {
                        val umts = record as com.craxiom.messaging.UmtsRecord
                        val data = umts.data
                        listOf(
                            "UMTS", data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                            data.lac?.value ?: 0, data.cid?.value ?: 0L,
                            data.rscp?.value?.let { "$it" } ?: "---"
                        )
                    }

                    else -> listOf("Unknown", 0, 0, 0, 0L, "---")
                }

                val technology = cellInfo[0] as String
                val mcc = cellInfo[1] as Int
                val mnc = cellInfo[2] as Int
                val area = cellInfo[3] as Int
                val cellId = cellInfo[4] as Long
                val signalStrength = cellInfo[5] as String

                // Technology and Signal Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TECHNOLOGY",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = technology,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SIGNAL",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = "$signalStrength dBm",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Network and Area Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NETWORK",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = "$mcc-$mnc",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (protocol == com.craxiom.networksurvey.model.CellularProtocol.GSM || 
                                       protocol == com.craxiom.networksurvey.model.CellularProtocol.UMTS) "LAC" else "TAC",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = area.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Cell ID
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "CELL ID",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = cellId.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TECHNOLOGY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .fillMaxWidth(0.6f)
                        .background(shimmerColor, RoundedCornerShape(4.dp))
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SIGNAL",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .fillMaxWidth(0.7f)
                        .background(shimmerColor, RoundedCornerShape(4.dp))
                )
            }
        }

        // Network and Area Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "NETWORK",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .fillMaxWidth(0.5f)
                        .background(shimmerColor, RoundedCornerShape(4.dp))
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TAC",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .fillMaxWidth(0.4f)
                        .background(shimmerColor, RoundedCornerShape(4.dp))
                )
            }
        }

        // Cell ID
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "CELL ID",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .fillMaxWidth(0.8f)
                    .background(shimmerColor, RoundedCornerShape(4.dp))
            )
        }
    }
}