package com.craxiom.networksurvey.ui.nsanalytics

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
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
        onRefreshStatus = { viewModel.refreshStatus() },
        onToggleCellular = { viewModel.toggleCellularProtocol(it) },
        onToggleWifi = { viewModel.toggleWifiProtocol(it) },
        onToggleBluetooth = { viewModel.toggleBluetoothProtocol(it) },
        onToggleGnss = { viewModel.toggleGnssProtocol(it) }
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
    onRefreshStatus: () -> Unit,
    onToggleCellular: (Boolean) -> Unit,
    onToggleWifi: (Boolean) -> Unit,
    onToggleBluetooth: (Boolean) -> Unit,
    onToggleGnss: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NS Analytics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                actions = {
                    if (uiState.isRegistered) {
                        IconButton(onClick = onRefreshStatus) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh status"
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!uiState.isRegistered) {
                        // Not registered - show connection setup
                        NotConnectedCard(onQrScanClick = onQrScanClick)
                    } else {
                        // Registered - show status and controls
                        ConnectionStatusCard(
                            workspace = uiState.workspace,
                            apiUrl = uiState.apiUrl,
                            isConnected = uiState.isConnected,
                            lastUploadTime = uiState.lastUploadTime
                        )

                        DataCollectionCard(
                            queuedRecords = uiState.queuedRecords
                        )

                        ProtocolSelectionCard(
                            cellularEnabled = uiState.cellularEnabled,
                            wifiEnabled = uiState.wifiEnabled,
                            bluetoothEnabled = uiState.bluetoothEnabled,
                            gnssEnabled = uiState.gnssEnabled,
                            onToggleCellular = onToggleCellular,
                            onToggleWifi = onToggleWifi,
                            onToggleBluetooth = onToggleBluetooth,
                            onToggleGnss = onToggleGnss
                        )

                        UploadSettingsCard(
                            autoUploadEnabled = uiState.autoUploadEnabled,
                            onToggleAutoUpload = onToggleAutoUpload,
                            uploadFrequencyMinutes = uiState.uploadFrequencyMinutes,
                            isUploading = uiState.isUploading,
                            onUploadNowClick = onUploadNowClick
                        )

                        ActionsCard(
                            queuedRecords = uiState.queuedRecords,
                            onClearQueueClick = onClearQueueClick,
                            onDisconnectClick = onDisconnectClick
                        )
                    }
                }
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
            containerColor = MaterialTheme.colorScheme.primaryContainer
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
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                text = "Not Connected",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Connect to NS Analytics to start uploading survey data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )

            Button(
                onClick = onQrScanClick,
                modifier = Modifier.fillMaxWidth()
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
private fun ConnectionStatusCard(
    workspace: String?,
    apiUrl: String?,
    isConnected: Boolean,
    lastUploadTime: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth()
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
                    text = "Connection Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isConnected) Color(0xFF4CAF50) else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            workspace?.let {
                InfoRow(label = "Workspace", value = it)
            }

            apiUrl?.let {
                InfoRow(label = "Server", value = it.replace("https://", "").replace("http://", ""))
            }

            if (lastUploadTime > 0) {
                val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                InfoRow(label = "Last Upload", value = dateFormat.format(Date(lastUploadTime)))
            }
        }
    }
}

@Composable
private fun DataCollectionCard(
    queuedRecords: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth()
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Data Collection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (queuedRecords > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Queued Records",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = queuedRecords.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolSelectionCard(
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Protocol Selection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Choose which protocols to collect data for",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Cellular protocol toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cellular (GSM, LTE, 5G)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = cellularEnabled,
                    onCheckedChange = onToggleCellular
                )
            }

            // Wi-Fi protocol toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Wi-Fi",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = wifiEnabled,
                    onCheckedChange = onToggleWifi
                )
            }

            // Bluetooth protocol toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bluetooth",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = bluetoothEnabled,
                    onCheckedChange = onToggleBluetooth
                )
            }

            // GNSS protocol toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GNSS (GPS)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = gnssEnabled,
                    onCheckedChange = onToggleGnss
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
    isUploading: Boolean,
    onUploadNowClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto Upload",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (autoUploadEnabled)
                            "Every $uploadFrequencyMinutes minutes"
                        else
                            "Manual upload only",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoUploadEnabled,
                    onCheckedChange = onToggleAutoUpload
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onUploadNowClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUploading
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Uploading...",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Upload Now")
                }
            }
        }
    }
}

@Composable
private fun ActionsCard(
    queuedRecords: Int,
    onClearQueueClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Danger Zone",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )

            if (queuedRecords > 0) {
                OutlinedButton(
                    onClick = onClearQueueClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Clear Queue ($queuedRecords records)")
                }
            }

            OutlinedButton(
                onClick = onDisconnectClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Disconnect from NS Analytics")
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
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