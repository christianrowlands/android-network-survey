package com.craxiom.networksurvey.ui.activesurvey

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import kotlin.math.cos
import kotlin.math.sin

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
    val infiniteTransition = rememberInfiniteTransition(label = "survey_animations")

    // Radar sweep rotation
    val radarRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_rotation"
    )

    // Ripple animations - 3 ripples at different phases
    val ripple1Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1"
    )

    val ripple2Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, delayMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2"
    )

    val ripple3Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, delayMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple3"
    )

    // Icon pulse
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_scale"
    )

    // Colors
    val primaryColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) primaryColor else inactiveColor,
        label = "background_color"
    )

    Box(
        modifier = modifier
            .size(200.dp)
            .shadow(
                elevation = if (isActive) 8.dp else 4.dp,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        // Background with gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = if (isActive) {
                            listOf(
                                backgroundColor,
                                backgroundColor.copy(alpha = 0.8f),
                                backgroundColor.copy(alpha = 0.6f)
                            )
                        } else {
                            listOf(inactiveColor, inactiveColor.copy(alpha = 0.9f))
                        },
                        radius = 300f
                    ),
                    shape = CircleShape
                )
        )

        // Canvas for radar and ripples
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = size.center
            val radius = size.minDimension / 2

            if (isActive) {
                // Draw ripples
                drawRipple(
                    center,
                    radius * ripple1Scale,
                    1f - (ripple1Scale - 0.5f) / 1f,
                    primaryColor
                )
                drawRipple(
                    center,
                    radius * ripple2Scale,
                    1f - (ripple2Scale - 0.5f) / 1f,
                    primaryColor
                )
                drawRipple(
                    center,
                    radius * ripple3Scale,
                    1f - (ripple3Scale - 0.5f) / 1f,
                    primaryColor
                )

                // Draw radar sweep
                rotate(radarRotation) {
                    drawRadarSweep(center, radius * 0.9f, primaryColor)
                }
            }
        }

        // Central icon
        Icon(
            imageVector = if (isActive) Icons.Filled.PlayArrow else Icons.Filled.Clear,
            contentDescription = if (isActive) "Scanning active" else "Scanning stopped",
            modifier = Modifier
                .size(80.dp)
                .scale(if (isActive) iconScale else 1f),
            tint = if (isActive) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

// Extension function to draw a ripple
private fun DrawScope.drawRipple(
    center: Offset,
    radius: Float,
    alpha: Float,
    color: Color
) {
    if (radius <= size.minDimension / 2 && alpha > 0) {
        drawCircle(
            color = color.copy(alpha = alpha * 0.3f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

// Extension function to draw radar sweep
private fun DrawScope.drawRadarSweep(
    center: Offset,
    radius: Float,
    color: Color
) {
    val sweepAngle = 45f
    val endX = center.x + radius * cos(Math.toRadians(sweepAngle.toDouble())).toFloat()
    val endY = center.y + radius * sin(Math.toRadians(sweepAngle.toDouble())).toFloat()

    // Draw sweep line with gradient effect
    for (i in 0..10) {
        val alpha = 1f - (i / 10f)
        val angle = sweepAngle - (i * 4)
        val x = center.x + radius * cos(Math.toRadians(angle.toDouble())).toFloat()
        val y = center.y + radius * sin(Math.toRadians(angle.toDouble())).toFloat()

        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    color.copy(alpha = alpha * 0.8f),
                    color.copy(alpha = 0f)
                ),
                start = center,
                end = Offset(x, y)
            ),
            start = center,
            end = Offset(x, y),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
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