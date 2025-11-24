package com.craxiom.networksurvey.ui.nsanalytics

import android.Manifest
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.theme.onPrimaryDark
import com.craxiom.networksurvey.util.NsAnalyticsUtils
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen for managing NS Analytics connection and settings.
 */
@Composable
fun NsAnalyticsConnectionScreen(
    viewModel: NsAnalyticsConnectionViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToQrScanner: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showClearQueueDialog by remember { mutableStateOf(false) }
    var showDeregistrationDialog by remember { mutableStateOf(false) }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onNavigateToQrScanner()
        } else {
            Toast.makeText(
                context,
                "Camera permission is required to scan QR codes",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    // Check for QR data when returning from scanner
    LaunchedEffect(Unit) {
        viewModel.checkAndProcessQrData()
    }

    // Handle service connection with proper lifecycle management
    NsAnalyticsServiceConnectionHandler(viewModel)

    // Check camera permission and navigate to QR scanner
    val handleQrScanClick = {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                // Permission already granted, navigate to scanner
                onNavigateToQrScanner()
            }

            else -> {
                // Request camera permission
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    NsAnalyticsConnectionContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onQrScanClick = handleQrScanClick,
        onToggleAutoUpload = { viewModel.toggleAutoUpload(it) },
        onUploadNowClick = { viewModel.uploadNow() },
        onDisconnectClick = { showDisconnectDialog = true },
        onClearQueueClick = { showClearQueueDialog = true },
        onToggleCellular = { viewModel.toggleCellularProtocol(it) },
        onToggleWifi = { viewModel.toggleWifiProtocol(it) },
        onToggleBluetooth = { viewModel.toggleBluetoothProtocol(it) },
        onToggleGnss = { viewModel.toggleGnssProtocol(it) },
        onToggleSurvey = { viewModel.toggleSurvey() },
    )

    // Show deregistration dialog when device is detected as deregistered
    LaunchedEffect(uiState.deregistrationInfo) {
        if (uiState.deregistrationInfo != null) {
            showDeregistrationDialog = true
        }
    }

    if (showDisconnectDialog) {
        DisconnectConfirmationDialog(
            workspaceName = uiState.workspaceName ?: "Unknown Workspace",
            onDismiss = { showDisconnectDialog = false },
            onConfirm = {
                viewModel.unregisterDevice()
                showDisconnectDialog = false
            }
        )
    }

    if (showDeregistrationDialog && uiState.deregistrationInfo != null) {
        DeviceDeregisteredDialog(
            deregistrationInfo = uiState.deregistrationInfo!!,
            workspaceName = uiState.workspaceName ?: "the workspace",
            onDismiss = {
                showDeregistrationDialog = false
                viewModel.clearDeregistrationInfo()
            },
            onRegisterAgain = {
                showDeregistrationDialog = false
                viewModel.clearDeregistrationInfo()
                onNavigateToQrScanner()
            }
        )
    }

    if (uiState.showQuotaExceededDialog) {
        QuotaExceededDialog(
            currentUsage = uiState.quotaCurrentUsage,
            maxRecords = uiState.quotaMaxRecords,
            quotaMessage = uiState.quotaMessage,
            nsAnalyticsUrl = uiState.quotaWebUrl,
            onDismiss = {
                viewModel.dismissQuotaDialog()
            },
            onOpenNsAnalytics = {
                viewModel.dismissQuotaDialog()
                uiState.quotaWebUrl?.let { url ->
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    )
                }
            }
        )
    }

    if (showClearQueueDialog) {
        ClearQueueConfirmationDialog(
            queueSize = uiState.queuedRecords,
            onDismiss = { showClearQueueDialog = false },
            onConfirm = {
                viewModel.clearQueue()
                showClearQueueDialog = false
            }
        )
    }

    // Registration confirmation dialog
    uiState.pendingQrData?.let { qrData ->
        if (uiState.showRegistrationConfirmDialog) {
            RegistrationConfirmationDialog(
                qrData = qrData,
                onDismiss = { viewModel.cancelRegistration() },
                onConfirm = { viewModel.confirmRegistration() }
            )
        }
    }

    // Registration success dialog
    if (uiState.showRegistrationSuccessDialog) {
        RegistrationSuccessDialog(
            workspaceName = uiState.workspaceName ?: "Unknown Workspace",
            onDismiss = { viewModel.dismissSuccessDialog() }
        )
    }

    // Registration error dialog
    uiState.registrationError?.let { errorMessage ->
        RegistrationErrorDialog(
            errorMessage = errorMessage,
            onDismiss = { viewModel.dismissRegistrationError() },
            onRetry = { viewModel.retryRegistration() }
        )
    }

    // Already registered dialog - when user scans QR while registered
    if (uiState.showAlreadyRegisteredDialog && uiState.pendingQrData != null) {
        AlreadyRegisteredDialog(
            currentWorkspaceName = uiState.workspaceName ?: "Unknown Workspace",
            newWorkspaceName = uiState.pendingQrData?.workspaceName,
            onDismiss = { viewModel.dismissAlreadyRegisteredDialog() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NsAnalyticsConnectionContent(
    uiState: NsAnalyticsConnectionUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onQrScanClick: () -> Unit,
    onToggleAutoUpload: (Boolean) -> Unit,
    onUploadNowClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onClearQueueClick: () -> Unit,
    onToggleCellular: (Boolean) -> Unit,
    onToggleWifi: (Boolean) -> Unit,
    onToggleBluetooth: (Boolean) -> Unit,
    onToggleGnss: (Boolean) -> Unit,
    onToggleSurvey: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NS Analytics Details", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1B1F)
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF121316)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF121316))
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF4CAF50))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!uiState.isRegistered) {
                        // Not registered - show connection setup
                        NotConnectedCard(onQrScanClick = onQrScanClick)
                    } else {
                        // Registered - show new design
                        WorkspaceStatusCard(
                            workspaceName = uiState.workspaceName,
                            workspaceId = uiState.workspace
                        )

                        NsAnalyticsStatusCard(
                            isSurveyActive = uiState.isSurveyActive,
                            surveyStartTime = uiState.surveyStartTime,
                            cellularCount = uiState.cellularRecordCount,
                            wifiCount = uiState.wifiRecordCount,
                            bluetoothCount = uiState.bluetoothRecordCount,
                            gnssCount = uiState.gnssRecordCount,
                            onToggleSurvey = onToggleSurvey,
                            showDetailedInfo = true
                        )

                        UploadSettingsCard(
                            autoUploadEnabled = uiState.autoUploadEnabled,
                            onToggleAutoUpload = onToggleAutoUpload,
                            uploadFrequencyMinutes = uiState.uploadFrequencyMinutes,
                            lastUploadTime = uiState.lastUploadTime,
                            isUploading = uiState.isUploading,
                            uploadProgress = uiState.uploadProgress,
                            uploadedRecords = uiState.uploadedRecords,
                            totalRecordsToUpload = uiState.totalRecordsToUpload,
                            onUploadNowClick = onUploadNowClick
                        )

                        ActiveProtocolsCard(
                            cellularEnabled = uiState.cellularEnabled,
                            wifiEnabled = uiState.wifiEnabled,
                            bluetoothEnabled = uiState.bluetoothEnabled,
                            gnssEnabled = uiState.gnssEnabled,
                            isSurveyActive = uiState.isSurveyActive,
                            onToggleCellular = onToggleCellular,
                            onToggleWifi = onToggleWifi,
                            onToggleBluetooth = onToggleBluetooth,
                            onToggleGnss = onToggleGnss
                        )

                        DangerZoneCard(
                            queuedRecords = uiState.queuedRecords,
                            onClearQueueClick = onClearQueueClick,
                            onDisconnectClick = onDisconnectClick
                        )
                    }
                }
            }

            // Upload progress overlay
            if (uiState.isUploading) {
                UploadProgressOverlay(
                    progress = uiState.uploadProgress,
                    uploadedRecords = uiState.uploadedRecords,
                    totalRecords = uiState.totalRecordsToUpload
                )
            }
        }
    }
}

@Composable
private fun NotConnectedCard(
    onQrScanClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        NsAnalyticsHeroSection()

        SetupGuideSection()

        SetupTipBanner()

        CallToActionButtons(
            onGetStartedClick = {
                openUrlInBrowser(context, "https://analytics.networksurvey.app")
            },
            onScanQrClick = onQrScanClick,
            onLearnMoreClick = {
                openUrlInBrowser(context, "https://www.networksurvey.app/analytics")
            },
            onQrCodeUserManualClick = {
                openUrlInBrowser(
                    context,
                    "https://www.networksurvey.app/manual/ns-analytics/qr-code-registration/"
                )
            }
        )

        WhatHappensNextSection()
    }
}

/**
 * Hero section with NS Analytics branding and tagline
 */
@Composable
private fun NsAnalyticsHeroSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ns_analytics),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(R.string.ns_analytics),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(
            text = stringResource(R.string.ns_analytics_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/**
 * Step-by-step setup guide section
 */
@Composable
private fun SetupGuideSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1F24)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.ns_analytics_setup_guide_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            SetupStepItem(
                stepNumber = 1,
                title = stringResource(R.string.ns_analytics_step_1_title),
                description = stringResource(R.string.ns_analytics_step_1_desc)
            )

            SetupStepItem(
                stepNumber = 2,
                title = stringResource(R.string.ns_analytics_step_2_title),
                description = stringResource(R.string.ns_analytics_step_2_desc)
            )

            SetupStepItem(
                stepNumber = 3,
                title = stringResource(R.string.ns_analytics_step_3_title),
                description = stringResource(R.string.ns_analytics_step_3_desc)
            )

            SetupStepItem(
                stepNumber = 4,
                title = stringResource(R.string.ns_analytics_step_4_title),
                description = stringResource(R.string.ns_analytics_step_4_desc)
            )
        }
    }
}

/**
 * Individual setup step with number, title, and description
 */
@Composable
private fun SetupStepItem(
    stepNumber: Int,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Step number circle
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = Color(0xFF2A2B30),
                    shape = MaterialTheme.shapes.small
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

/**
 * Setup tip banner recommending desktop setup
 */
@Composable
private fun SetupTipBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.ns_analytics_setup_tip_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Text(
                    text = stringResource(R.string.ns_analytics_setup_tip_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/**
 * Call-to-action buttons section
 */
@Composable
private fun CallToActionButtons(
    onGetStartedClick: () -> Unit,
    onScanQrClick: () -> Unit,
    onLearnMoreClick: () -> Unit,
    onQrCodeUserManualClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Primary button - Get Started
        Button(
            onClick = onGetStartedClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(stringResource(R.string.ns_analytics_get_started))
            Icon(
                painter = painterResource(R.drawable.ic_open_details),
                tint = onPrimaryDark,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Secondary button - Scan QR Code
        Button(
            onClick = onScanQrClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(stringResource(R.string.ns_analytics_already_have_qr))
        }

        // Text link - Learn More
        TextButton(
            onClick = onLearnMoreClick,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = stringResource(R.string.ns_analytics_learn_more),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Icon(
                painter = painterResource(R.drawable.ic_open_details),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Text link - User Manual QR Code Registration
        TextButton(
            onClick = onQrCodeUserManualClick,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = stringResource(R.string.ns_analytics_qr_user_manual),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Icon(
                painter = painterResource(R.drawable.ic_open_details),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * What happens next explanation section
 */
@Composable
private fun WhatHappensNextSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1F24)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.ns_analytics_what_happens_next_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = stringResource(R.string.ns_analytics_what_happens_next_desc),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

/**
 * Opens a URL in the default browser
 */
private fun openUrlInBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    } catch (e: Exception) {
        Timber.e(e, "Failed to open URL: $url")
        Toast.makeText(
            context,
            "Unable to open web browser",
            Toast.LENGTH_SHORT
        ).show()
    }
}

@Composable
private fun WorkspaceStatusCard(
    workspaceName: String?,
    workspaceId: String?
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1F24)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Workspace Registered",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = workspaceName ?: "Unknown Workspace",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                workspaceId?.let { id ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            clipboard.nativeClipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    "Workspace ID",
                                    id
                                )
                            )
                            Toast.makeText(context, "Workspace ID copied", Toast.LENGTH_SHORT)
                                .show()
                        }
                    ) {
                        Text(
                            text = "ID: ${id.take(8)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_copy),
                            contentDescription = "Copy ID",
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Ready to collect and upload survey data",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}


@Composable
private fun UploadSettingsCard(
    autoUploadEnabled: Boolean,
    onToggleAutoUpload: (Boolean) -> Unit,
    uploadFrequencyMinutes: Int,
    lastUploadTime: Long,
    isUploading: Boolean,
    uploadProgress: Float,
    uploadedRecords: Int,
    totalRecordsToUpload: Int,
    onUploadNowClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1F24)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Upload Settings",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Auto Upload",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Text(
                        text = if (autoUploadEnabled) "Every $uploadFrequencyMinutes min" else "Disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = autoUploadEnabled,
                    onCheckedChange = onToggleAutoUpload
                )
            }

            if (lastUploadTime > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Last Upload",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    val dateFormat =
                        SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
                    Text(
                        text = dateFormat.format(Date(lastUploadTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Text(
                    text = getTimeAgo(lastUploadTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onUploadNowClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUploading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Upload Now")
            }
        }
    }
}

@Composable
private fun ActiveProtocolsCard(
    cellularEnabled: Boolean,
    wifiEnabled: Boolean,
    bluetoothEnabled: Boolean,
    gnssEnabled: Boolean,
    isSurveyActive: Boolean,
    onToggleCellular: (Boolean) -> Unit,
    onToggleWifi: (Boolean) -> Unit,
    onToggleBluetooth: (Boolean) -> Unit,
    onToggleGnss: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1F24)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Active Protocols",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            ProtocolToggleRow(
                label = "Cellular",
                description = "GSM, CDMA, UMTS, LTE, NR",
                isEnabled = cellularEnabled,
                enabled = !isSurveyActive,
                onToggle = onToggleCellular
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProtocolToggleRow(
                label = "Wi-Fi",
                description = "802.11 networks and access points",
                isEnabled = wifiEnabled,
                enabled = !isSurveyActive,
                onToggle = onToggleWifi
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProtocolToggleRow(
                label = "Bluetooth",
                description = "BLE and classic devices",
                isEnabled = bluetoothEnabled,
                enabled = !isSurveyActive,
                onToggle = onToggleBluetooth
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProtocolToggleRow(
                label = "GPS/GNSS",
                description = "Satellite positioning data",
                isEnabled = gnssEnabled,
                enabled = !isSurveyActive,
                onToggle = onToggleGnss
            )
        }
    }
}

@Composable
private fun ProtocolToggleRow(
    label: String,
    description: String,
    isEnabled: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            enabled = enabled
        )
    }
}

@Composable
private fun DangerZoneCard(
    queuedRecords: Int,
    onClearQueueClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onClearQueueClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = queuedRecords > 0,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFE57373),
                disabledContentColor = Color(0xFFE57373).copy(alpha = 0.5f)
            ),
            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE57373))
            )
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text("Clear Queue ($queuedRecords records)")
        }

        OutlinedButton(
            onClick = onDisconnectClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFE57373)
            ),
            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE57373))
            )
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(stringResource(R.string.unregister_device))
        }
    }
}

@Composable
private fun UploadProgressOverlay(
    progress: Float,
    uploadedRecords: Int,
    totalRecords: Int
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1F24)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (progress > 0f && progress < 1f) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp,
                        trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
                        strokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Uploading Records",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$uploadedRecords / $totalRecords",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0xFF2A2B30),
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    drawStopIndicator = {}
                )
            }
        }
    }
}


private fun getTimeAgo(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val elapsed = System.currentTimeMillis() - timestamp
    val minutes = (elapsed / 1000 / 60).toInt()
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "$days days ago"
        hours > 0 -> "$hours hours ago"
        minutes > 0 -> "$minutes minutes ago"
        else -> "Just now"
    }
}

@Composable
private fun DisconnectConfirmationDialog(
    workspaceName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unregister_device_title)) },
        text = {
            Text(stringResource(R.string.unregister_device_message, workspaceName))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.unregister))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun DeviceDeregisteredDialog(
    deregistrationInfo: DeregistrationInfo,
    workspaceName: String,
    onDismiss: () -> Unit,
    onRegisterAgain: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_deregistered_title)) },
        text = {
            Column {
                // Handle different levels of detail in deregistration info
                when {
                    deregistrationInfo.source == "web" &&
                            (deregistrationInfo.deregisteredBy != null || deregistrationInfo.deregisteredAt != null) -> {
                        // We have details from web deregistration
                        val byPart = deregistrationInfo.deregisteredBy?.let {
                            stringResource(R.string.device_deregistered_by, it)
                        } ?: ""
                        val onPart = deregistrationInfo.deregisteredAt?.let { " on $it" } ?: ""
                        Text(stringResource(R.string.device_deregistered_from_web, byPart, onPart))
                    }

                    deregistrationInfo.source == "web" -> {
                        // Web deregistration detected but no details available (403 response)
                        Text(stringResource(R.string.device_deregistered_from_web_no_details))
                    }

                    else -> {
                        // Generic deregistration message
                        Text(stringResource(R.string.device_deregistered_generic, workspaceName))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.scan_qr_to_reregister))
            }
        },
        confirmButton = {
            TextButton(onClick = onRegisterAgain) {
                Text(stringResource(R.string.register_again))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

@Composable
private fun QuotaExceededDialog(
    currentUsage: Int,
    maxRecords: Int,
    quotaMessage: String?,
    nsAnalyticsUrl: String?,
    onDismiss: () -> Unit,
    onOpenNsAnalytics: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Quota Exceeded") },
        text = {
            Column {
                Text(
                    quotaMessage ?: "Your workspace has reached its record limit."
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Current usage: ${
                        NsAnalyticsUtils.formatNumberWithThousandsSeparator(currentUsage)
                    } / ${
                        NsAnalyticsUtils.formatNumberWithThousandsSeparator(maxRecords)
                    } records",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "To continue uploading records, please:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• Delete existing records in NS Analytics, or",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• Upgrade to a higher subscription tier",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            if (nsAnalyticsUrl != null) {
                TextButton(onClick = onOpenNsAnalytics) {
                    Text("Open NS Analytics")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
private fun ClearQueueConfirmationDialog(
    queueSize: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear Upload Queue?") },
        text = {
            Text("This will permanently delete $queueSize queued records that haven't been uploaded yet. This action cannot be undone.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Clear Queue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Handles service connection lifecycle for the NS Analytics screen.
 * Binds to NetworkSurveyService when the composable enters composition
 * and unbinds when it leaves composition.
 */
@Composable
private fun NsAnalyticsServiceConnectionHandler(
    viewModel: NsAnalyticsConnectionViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isServiceBound by remember { mutableStateOf(false) }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val serviceBinder = binder as? NetworkSurveyService.SurveyServiceBinder
                val service = serviceBinder?.service as? NetworkSurveyService
                viewModel.setNetworkSurveyService(service)
                viewModel.onStart() // Start polling when service is connected
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                viewModel.onStop() // Stop polling when service is disconnected
                viewModel.setNetworkSurveyService(null)
            }
        }
    }

    // Get application context to avoid leaking activity context
    val applicationContext = remember { context.applicationContext }

    // Handle lifecycle events to unbind/rebind service when app goes to/from background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // App is going to background - unbind from service
                    if (isServiceBound) {
                        try {
                            // Stop polling
                            viewModel.onStop()
                            // Clear service reference in ViewModel
                            viewModel.setNetworkSurveyService(null)
                            // Unbind from the service
                            applicationContext.unbindService(serviceConnection)
                            isServiceBound = false
                        } catch (e: Exception) {
                            Timber.e(e, "Error unbinding service on background")
                        }
                    }
                }

                Lifecycle.Event.ON_START -> {
                    // App is returning to foreground - rebind to service
                    if (!isServiceBound) {
                        try {
                            val serviceIntent =
                                Intent(applicationContext, NetworkSurveyService::class.java)
                            val bound = applicationContext.bindService(
                                serviceIntent,
                                serviceConnection,
                                Context.BIND_AUTO_CREATE
                            )
                            if (bound) {
                                isServiceBound = true
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error rebinding service on foreground")
                        }
                    }
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(context) {
        // Initial binding to the service when the screen is first displayed
        if (!isServiceBound) {
            val serviceIntent = Intent(applicationContext, NetworkSurveyService::class.java)
            val bound = try {
                applicationContext.bindService(
                    serviceIntent,
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to bind to NetworkSurveyService")
                false
            }
            isServiceBound = bound
        }

        onDispose {
            // Stop polling and clean up service reference
            viewModel.onStop()
            viewModel.setNetworkSurveyService(null)

            // Unbind from service if still bound
            if (isServiceBound) {
                try {
                    applicationContext.unbindService(serviceConnection)
                    isServiceBound = false
                } catch (e: Exception) {
                    Timber.e(e, "Error unbinding service on disposal")
                }
            }
        }
    }
}

/**
 * Preview for the NS Analytics unregistered/not connected screen.
 * Shows the complete onboarding UI with hero section, setup guide, and CTAs.
 */
@Preview(
    name = "NS Analytics Not Connected - Dark",
    showBackground = true,
    backgroundColor = 0xFF121316,
    widthDp = 360,
    heightDp = 2000
)

/**
 * Dialog shown to confirm workspace registration before proceeding.
 *
 * UX Design:
 * - Workspace name is prominently displayed as the focal point
 * - Technical details (API URL, Workspace ID) are in a collapsible section
 * - Fallback display when workspace_name is not provided in deep link
 */
@Composable
private fun RegistrationConfirmationDialog(
    qrData: com.craxiom.networksurvey.data.api.NsAnalyticsQrData,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var showTechnicalDetails by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Join Workspace?",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Prompt text
                Text(
                    text = "You're about to join:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Workspace icon
                Icon(
                    painter = painterResource(R.drawable.ic_ns_analytics),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                // Workspace Name - Hero Element
                if (!qrData.workspaceName.isNullOrBlank()) {
                    Text(
                        text = qrData.workspaceName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    // Fallback when workspace_name is missing
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Workspace",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = qrData.workspaceId.take(12) +
                                    if (qrData.workspaceId.length > 12) "..." else "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Workspace name not provided in link",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontStyle = FontStyle.Italic
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Collapsible Technical Details Section
                TechnicalDetailsSection(
                    isExpanded = showTechnicalDetails,
                    onToggle = { showTechnicalDetails = !showTechnicalDetails },
                    workspaceId = qrData.workspaceId,
                    apiUrl = qrData.apiUrl
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF4CAF50)
                )
            ) {
                Text("Join Workspace")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Collapsible section showing technical details (API URL and Workspace ID).
 * Hidden by default to reduce visual clutter.
 */
@Composable
private fun TechnicalDetailsSection(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    workspaceId: String,
    apiUrl: String
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Toggle header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isExpanded)
                    Icons.Default.KeyboardArrowUp
                else
                    Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Technical Details",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Expandable content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TechnicalDetailItem(
                        label = "API URL",
                        value = apiUrl
                    )
                    TechnicalDetailItem(
                        label = "Workspace ID",
                        value = workspaceId
                    )
                }
            }
        }
    }
}

/**
 * Single technical detail item with label and monospace value.
 */
@Composable
private fun TechnicalDetailItem(
    label: String,
    value: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Dialog shown after successful workspace registration
 */
@Composable
private fun RegistrationSuccessDialog(
    workspaceName: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Registration Successful!")
            }
        },
        text = {
            Column {
                Text(
                    text = "You've successfully joined:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = workspaceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Start a survey to begin collecting and uploading data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF4CAF50)
                )
            ) {
                Text("Done")
            }
        }
    )
}

/**
 * Dialog shown when registration fails
 */
@Composable
private fun RegistrationErrorDialog(
    errorMessage: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Registration Failed")
            }
        },
        text = {
            Column {
                Text(
                    text = "Unable to register device:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF4CAF50)
                )
            ) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog shown when user scans QR code while already registered to a workspace.
 * Informs the user they need to unregister first before registering with a new workspace.
 */
@Composable
private fun AlreadyRegisteredDialog(
    currentWorkspaceName: String,
    newWorkspaceName: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.ns_analytics_qr_detected_title))
            }
        },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.ns_analytics_already_registered_message,
                        currentWorkspaceName
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (!newWorkspaceName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.ns_analytics_new_workspace_detected,
                            newWorkspaceName
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.ns_analytics_unregister_first_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

@Composable
private fun NotConnectedCardPreview() {
    NsTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121316))
                .padding(16.dp)
        ) {
            NotConnectedCard(
                onQrScanClick = { /* Preview - no action */ }
            )
        }
    }
}
