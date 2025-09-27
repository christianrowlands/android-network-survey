package com.craxiom.networksurvey.ui.nsanalytics

import android.Manifest
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.services.NetworkSurveyService
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
    onNavigateToQrScanner: () -> Unit,
    mainNavController: NavController? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showClearQueueDialog by remember { mutableStateOf(false) }

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

    if (showDisconnectDialog) {
        DisconnectConfirmationDialog(
            onDismiss = { showDisconnectDialog = false },
            onConfirm = {
                viewModel.disconnect()
                showDisconnectDialog = false
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
                title = { Text("NS Analytics Connection", color = Color.White) },
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1F24)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFFE53935)
            )

            Text(
                text = "Not Connected",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Connect to NS Analytics to start uploading survey data",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onQrScanClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4285F4)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Scan QR Code")
            }
        }
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
                onToggle = onToggleCellular
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProtocolToggleRow(
                label = "Wi-Fi",
                description = "802.11 networks and access points",
                isEnabled = wifiEnabled,
                onToggle = onToggleWifi
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProtocolToggleRow(
                label = "Bluetooth",
                description = "BLE and classic devices",
                isEnabled = bluetoothEnabled,
                onToggle = onToggleBluetooth
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProtocolToggleRow(
                label = "GPS/GNSS",
                description = "Satellite positioning data",
                isEnabled = gnssEnabled,
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
            onCheckedChange = onToggle
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
            Text("Unregister from Workspace")
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
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disconnect from NS Analytics?") },
        text = {
            Text("This will stop data collection and remove your connection to the NS Analytics backend. Queued data will be lost.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Disconnect")
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
