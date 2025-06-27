package com.craxiom.networksurvey.ui.activesurvey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
                text = if (surveyState.isAnyActive) "Survey Running" else "Survey Stopped",
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
        List(20) {
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
                animationTime += 0.016f // ~60 FPS (16ms per frame)
                kotlinx.coroutines.delay(16)
            }
        } else {
            // When stopped, animate particles falling
            repeat(100) {
                fallTime += 0.016f
                kotlinx.coroutines.delay(16)
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

                // Draw particle with trail effect
                if (isActive && yPos < height * 0.9f) {
                    // Trail
                    for (i in 1..5) {
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.1f / i),
                            radius = particleSize / 2,
                            center = Offset(xPos, yPos + (i * particleSize))
                        )
                    }
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

                // Glow effect
                if (isActive && normalizedY < 0.5f) {
                    drawCircle(
                        color = primaryColor.copy(alpha = (0.5f - normalizedY) * 0.3f),
                        radius = particleSize,
                        center = Offset(xPos, yPos)
                    )
                }
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