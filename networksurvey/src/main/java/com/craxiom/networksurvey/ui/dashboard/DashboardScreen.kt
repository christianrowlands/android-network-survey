package com.craxiom.networksurvey.ui.dashboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.uploader.NsUploaderWorker
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.main.CdrHelpDialog
import com.craxiom.networksurvey.ui.main.FileMqttHelpDialog
import com.craxiom.networksurvey.ui.main.NsAnalyticsHelpDialog
import com.craxiom.networksurvey.ui.main.PhoneStateHelpDialog
import com.craxiom.networksurvey.ui.main.SharedViewModel
import com.craxiom.networksurvey.ui.main.UploadHelpDialog
import com.craxiom.networksurvey.util.BatteryOptimizationHelper
import com.craxiom.networksurvey.util.PreferenceUtils
import timber.log.Timber

/**
 * The main Dashboard screen composable. A fully Compose-native implementation that
 * manages service binding, composes all cards in a scrollable column, and handles
 * dialog state.
 */
@Composable
fun DashboardScreen(
    sharedViewModel: SharedViewModel = viewModel(),
    dashboardViewModel: DashboardViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Collect state from separate flows
    val batteryState by dashboardViewModel.batteryState.collectAsStateWithLifecycle()
    val queueState by dashboardViewModel.queueState.collectAsStateWithLifecycle()
    val locationState by dashboardViewModel.locationState.collectAsStateWithLifecycle()
    val loggingState by dashboardViewModel.loggingState.collectAsStateWithLifecycle()
    val mqttState by dashboardViewModel.mqttState.collectAsStateWithLifecycle()
    val uploadState by dashboardViewModel.uploadState.collectAsStateWithLifecycle()
    val nsAnalyticsState by dashboardViewModel.nsAnalyticsState.collectAsStateWithLifecycle()

    // Dialog visibility states
    var showUploadHelpDialog by remember { mutableStateOf(false) }
    var showFileMqttHelpDialog by remember { mutableStateOf(false) }
    var showPhoneStateHelpDialog by remember { mutableStateOf(false) }
    var showCdrHelpDialog by remember { mutableStateOf(false) }
    var showNsAnalyticsHelpDialog by remember { mutableStateOf(false) }
    var showDisableQueueLimitDialog by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var showBluetoothPermissionDialog by remember { mutableStateOf(false) }
    var showCdrRequiredPermissionDialog by remember { mutableStateOf(false) }
    var showCdrOptionalPermissionDialog by remember { mutableStateOf(false) }
    var showNoInternetDialog by remember { mutableStateOf(false) }

    // Service binding via lifecycle
    ServiceBindingEffect(
        dashboardViewModel = dashboardViewModel,
    )

    // Initialize location on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dashboardViewModel.initializeLocationState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Observe WorkManager uploads
    UploadWorkObserver(dashboardViewModel = dashboardViewModel)

    // Handle one-shot events
    LaunchedEffect(Unit) {
        dashboardViewModel.events.collect { event ->
            when (event) {
                is DashboardEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }

                is DashboardEvent.ShowSnackbar -> {
                    // For MQTT "Open" snackbar - navigate to MQTT settings
                    if (event.actionLabel == "Open") {
                        sharedViewModel.triggerNavigationToMqttConnection()
                    }
                }

                is DashboardEvent.ShowBatteryOptimizationDialog -> {
                    sharedViewModel.triggerBatteryOptimizationDialog()
                }

                is DashboardEvent.ShowBluetoothPermissionRationale -> {
                    showBluetoothPermissionDialog = true
                }

                is DashboardEvent.ShowCdrPermissionRationale -> {
                    if (event.isRequired) {
                        showCdrRequiredPermissionDialog = true
                    } else {
                        showCdrOptionalPermissionDialog = true
                    }
                }

                is DashboardEvent.ShowUploadDialog -> {
                    showUploadDialog = true
                }

                is DashboardEvent.ShowDisableQueueLimitDialog -> {
                    showDisableQueueLimitDialog = true
                }
            }
        }
    }

    // Main content
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Battery card (animated visibility handled internally)
        BatteryCard(state = batteryState)

        // Queue status card (animated visibility handled internally)
        QueueStatusCard(
            state = queueState,
            onAdjustLimit = { sharedViewModel.triggerNavigationToSettings() },
            onDisableLimit = { showDisableQueueLimitDialog = true },
        )

        // Location card
        LocationCard(
            state = locationState,
            onNavigateToSettings = { sharedViewModel.triggerNavigationToSettings() },
        )

        // NS Analytics card (animated visibility handled internally)
        NsAnalyticsCard(
            state = nsAnalyticsState,
            onToggleSurvey = { dashboardViewModel.toggleNsAnalyticsSurvey() },
            onOpenDetails = { sharedViewModel.triggerNavigationToNsAnalyticsConnection() },
            onHelpClick = { showNsAnalyticsHelpDialog = true },
        )

        // Upload scanning card
        UploadScanningCard(
            state = uploadState,
            onStartScanning = {
                val batteryHelper = BatteryOptimizationHelper(context)
                if (batteryHelper.shouldPromptForBatteryOptimization()) {
                    sharedViewModel.triggerBatteryOptimizationDialog()
                } else {
                    dashboardViewModel.toggleUploadScanning(true)
                }
            },
            onStopScanning = { dashboardViewModel.toggleUploadScanning(false) },
            onUpload = {
                if (dashboardViewModel.shouldShowUploadConfigDialog()) {
                    showUploadDialog = true
                } else {
                    // Skip dialog, use saved preferences directly
                    val started = dashboardViewModel.startUpload(
                        uploadToOpenCellId = PreferenceUtils.getBoolean(
                            NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID,
                            NetworkSurveyConstants.DEFAULT_UPLOAD_TO_OPENCELLID,
                        ),
                        anonymously = PreferenceUtils.getBoolean(
                            NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD,
                            NetworkSurveyConstants.DEFAULT_UPLOAD_TO_OPENCELLID,
                        ),
                        uploadToBeaconDb = PreferenceUtils.getBoolean(
                            NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB,
                            NetworkSurveyConstants.DEFAULT_UPLOAD_TO_BEACONDB,
                        ),
                        retry = PreferenceUtils.getBoolean(
                            NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED,
                            NetworkSurveyConstants.DEFAULT_UPLOAD_RETRY_ENABLED,
                        ),
                    )
                    if (!started) showNoInternetDialog = true
                }
            },
            onCancelUpload = {
                WorkManager.getInstance(context)
                    .cancelAllWorkByTag(NsUploaderWorker.WORKER_TAG)
            },
            onNavigateToUploadSettings = { sharedViewModel.triggerNavigationToUploadSettings() },
            onHelpClick = { showUploadHelpDialog = true },
            shouldStartCellular = PreferenceUtils.shouldStartCellularForUpload(context),
            shouldStartWifi = PreferenceUtils.shouldStartWifiForUpload(context),
        )

        // Logging controls card
        LoggingControlsCard(
            state = loggingState,
            onCellularToggle = { enable ->
                if (enable) {
                    val batteryHelper = BatteryOptimizationHelper(context)
                    if (batteryHelper.shouldPromptForBatteryOptimization()) {
                        sharedViewModel.triggerBatteryOptimizationDialog()
                        return@LoggingControlsCard
                    }
                }
                dashboardViewModel.toggleCellularLogging(enable)
            },
            onPhoneStateToggle = { enable ->
                if (enable) {
                    val batteryHelper = BatteryOptimizationHelper(context)
                    if (batteryHelper.shouldPromptForBatteryOptimization()) {
                        sharedViewModel.triggerBatteryOptimizationDialog()
                        return@LoggingControlsCard
                    }
                }
                dashboardViewModel.togglePhoneStateLogging(enable)
            },
            onWifiToggle = { enable ->
                if (enable) {
                    val batteryHelper = BatteryOptimizationHelper(context)
                    if (batteryHelper.shouldPromptForBatteryOptimization()) {
                        sharedViewModel.triggerBatteryOptimizationDialog()
                        return@LoggingControlsCard
                    }
                }
                dashboardViewModel.toggleWifiLogging(enable)
            },
            onBluetoothToggle = { enable ->
                if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val activity = context as? NetworkSurveyActivity
                    if (activity != null) {
                        val missing = NetworkSurveyActivity.BLUETOOTH_PERMISSIONS.any {
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                it,
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                        if (missing) {
                            showBluetoothPermissionDialog = true
                            return@LoggingControlsCard
                        }
                    }
                }
                if (enable) {
                    val batteryHelper = BatteryOptimizationHelper(context)
                    if (batteryHelper.shouldPromptForBatteryOptimization()) {
                        sharedViewModel.triggerBatteryOptimizationDialog()
                        return@LoggingControlsCard
                    }
                }
                dashboardViewModel.toggleBluetoothLogging(enable)
            },
            onGnssToggle = { enable ->
                if (enable) {
                    val batteryHelper = BatteryOptimizationHelper(context)
                    if (batteryHelper.shouldPromptForBatteryOptimization()) {
                        sharedViewModel.triggerBatteryOptimizationDialog()
                        return@LoggingControlsCard
                    }
                }
                dashboardViewModel.toggleGnssLogging(enable)
            },
            onCdrToggle = { enable ->
                if (enable) {
                    val cdrRequiredMissing =
                        com.craxiom.networksurvey.constants.CdrPermissions.CDR_REQUIRED_PERMISSIONS.any {
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                it,
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                    if (cdrRequiredMissing) {
                        showCdrRequiredPermissionDialog = true
                        return@LoggingControlsCard
                    }
                    val cdrOptionalMissing =
                        com.craxiom.networksurvey.constants.CdrPermissions.CDR_OPTIONAL_PERMISSIONS.any {
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                it,
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                    if (cdrOptionalMissing) {
                        showCdrOptionalPermissionDialog = true
                        return@LoggingControlsCard
                    }
                    val batteryHelper = BatteryOptimizationHelper(context)
                    if (batteryHelper.shouldPromptForBatteryOptimization()) {
                        sharedViewModel.triggerBatteryOptimizationDialog()
                        return@LoggingControlsCard
                    }
                }
                dashboardViewModel.toggleCdrLogging(enable)
            },
            onPhoneStateHelpClick = { showPhoneStateHelpDialog = true },
            onCdrHelpClick = { showCdrHelpDialog = true },
            onFileHelpClick = { showFileMqttHelpDialog = true },
        )

        // MQTT status card
        MqttStatusCard(
            state = mqttState,
            onToggleMqtt = { connect -> dashboardViewModel.toggleMqttConnection(connect) },
            onNavigateToMqttSettings = { sharedViewModel.triggerNavigationToMqttConnection() },
            onHelpClick = { showFileMqttHelpDialog = true },
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Dialogs
    if (showUploadHelpDialog) {
        UploadHelpDialog(onDismissRequest = { showUploadHelpDialog = false })
    }
    if (showFileMqttHelpDialog) {
        FileMqttHelpDialog(onDismissRequest = { showFileMqttHelpDialog = false })
    }
    if (showPhoneStateHelpDialog) {
        PhoneStateHelpDialog(onDismissRequest = { showPhoneStateHelpDialog = false })
    }
    if (showCdrHelpDialog) {
        CdrHelpDialog(onDismissRequest = { showCdrHelpDialog = false })
    }
    if (showNsAnalyticsHelpDialog) {
        NsAnalyticsHelpDialog(onDismissRequest = { showNsAnalyticsHelpDialog = false })
    }
    if (showDisableQueueLimitDialog) {
        DisableQueueLimitDialog(
            onConfirm = { dashboardViewModel.disableQueueLimit() },
            onDismiss = { showDisableQueueLimitDialog = false },
        )
    }
    if (showBluetoothPermissionDialog) {
        BluetoothPermissionRationaleDialog(
            onRequestPermissions = {
                val activity = context as? NetworkSurveyActivity
                activity?.let {
                    androidx.core.app.ActivityCompat.requestPermissions(
                        it,
                        NetworkSurveyActivity.BLUETOOTH_PERMISSIONS,
                        NetworkSurveyConstants.ACCESS_BLUETOOTH_PERMISSION_REQUEST_ID,
                    )
                }
            },
            onDismiss = { showBluetoothPermissionDialog = false },
        )
    }
    if (showCdrRequiredPermissionDialog) {
        CdrRequiredPermissionDialog(
            onRequestPermissions = {
                val activity = context as? NetworkSurveyActivity
                activity?.let {
                    androidx.core.app.ActivityCompat.requestPermissions(
                        it,
                        com.craxiom.networksurvey.constants.CdrPermissions.CDR_REQUIRED_PERMISSIONS,
                        NetworkSurveyConstants.ACCESS_REQUIRED_PERMISSION_REQUEST_ID,
                    )
                }
            },
            onDismiss = { showCdrRequiredPermissionDialog = false },
        )
    }
    if (showCdrOptionalPermissionDialog) {
        CdrOptionalPermissionDialog(
            onRequestPermissions = {
                val activity = context as? NetworkSurveyActivity
                activity?.let {
                    androidx.core.app.ActivityCompat.requestPermissions(
                        it,
                        com.craxiom.networksurvey.constants.CdrPermissions.CDR_OPTIONAL_PERMISSIONS,
                        NetworkSurveyConstants.ACCESS_OPTIONAL_PERMISSION_REQUEST_ID,
                    )
                }
            },
            onIgnore = {
                dashboardViewModel.toggleCdrLogging(true)
            },
            onDismiss = { showCdrOptionalPermissionDialog = false },
        )
    }
    if (showUploadDialog) {
        UploadConfirmationDialog(
            onUpload = { uploadToOcid, anonymously, uploadToBeaconDb, retry, dontShowAgain ->
                if (dontShowAgain) {
                    dashboardViewModel.setShowUploadDialog(dontShowAgain)
                }
                val started = dashboardViewModel.startUpload(
                    uploadToOcid, anonymously, uploadToBeaconDb, retry,
                )
                if (!started) showNoInternetDialog = true
            },
            onNavigateToUploadSettings = { sharedViewModel.triggerNavigationToUploadSettings() },
            onDismiss = { showUploadDialog = false },
        )
    }
    if (showNoInternetDialog) {
        NoInternetDialog(onDismiss = { showNoInternetDialog = false })
    }
}

/**
 * Manages service binding/unbinding tied to the lifecycle.
 * Binds in ON_RESUME, unbinds in ON_PAUSE, matching the original Fragment behavior.
 */
@Composable
private fun ServiceBindingEffect(
    dashboardViewModel: DashboardViewModel,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val serviceBinder = binder as NetworkSurveyService.SurveyServiceBinder
                val service = serviceBinder.service as NetworkSurveyService
                dashboardViewModel.onServiceConnected(service)
                Timber.d("Dashboard connected to NetworkSurveyService")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Timber.d("Dashboard disconnected from NetworkSurveyService")
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val appContext = context.applicationContext
                    val startIntent =
                        Intent(appContext, NetworkSurveyService::class.java)
                    appContext.startService(startIntent)

                    val bindIntent =
                        Intent(appContext, NetworkSurveyService::class.java)
                    appContext.bindService(
                        bindIntent,
                        serviceConnection,
                        Context.BIND_ABOVE_CLIENT,
                    )
                    Timber.i("NetworkSurveyService bound in DashboardScreen")
                }

                Lifecycle.Event.ON_PAUSE -> {
                    dashboardViewModel.service?.let {
                        dashboardViewModel.onServiceDisconnecting(it)
                    }
                    try {
                        context.applicationContext.unbindService(serviceConnection)
                        Timber.i("NetworkSurveyService unbound in DashboardScreen")
                    } catch (e: IllegalArgumentException) {
                        Timber.e(e, "Could not unbind service")
                    }
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            dashboardViewModel.service?.let {
                dashboardViewModel.onServiceDisconnecting(it)
            }
            try {
                context.applicationContext.unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                // Already unbound
            }
        }
    }
}

/**
 * Observes WorkManager upload tasks and updates the ViewModel's upload progress state.
 */
@Composable
private fun UploadWorkObserver(
    dashboardViewModel: DashboardViewModel,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        val workManager = WorkManager.getInstance(context)
        val liveData = workManager.getWorkInfosByTagLiveData(NsUploaderWorker.WORKER_TAG)

        val observer = androidx.lifecycle.Observer<List<WorkInfo>> { workInfos ->
            val activeWork = workInfos?.firstOrNull { info ->
                info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING
            }

            if (activeWork != null) {
                val progress = activeWork.progress
                val currentPercent =
                    progress.getInt(NsUploaderWorker.PROGRESS, NsUploaderWorker.PROGRESS_MIN_VALUE)
                val maxPercent = progress.getInt(
                    NsUploaderWorker.PROGRESS_MAX,
                    NsUploaderWorker.PROGRESS_MAX_VALUE,
                )
                val statusMessage = progress.getString(NsUploaderWorker.PROGRESS_STATUS_MESSAGE)
                    ?: if (activeWork.state == WorkInfo.State.ENQUEUED) {
                        context.getString(com.craxiom.networksurvey.R.string.uploader_enqueued)
                    } else ""

                dashboardViewModel.updateUploadProgress(
                    UploadProgressState.InProgress(
                        progress = currentPercent.coerceAtMost(maxPercent),
                        maxProgress = maxPercent,
                        statusMessage = statusMessage,
                    )
                )
            } else {
                // Check if any finished work
                val finishedWork = workInfos?.firstOrNull { it.state.isFinished }
                if (finishedWork != null) {
                    val cancelled = finishedWork.state == WorkInfo.State.CANCELLED
                    dashboardViewModel.updateUploadProgress(
                        UploadProgressState.Finished(
                            cancelled = cancelled,
                            ocidResult = finishedWork.outputData.getString(NsUploaderWorker.OCID_RESULT)
                                ?: "",
                            beaconDbResult = finishedWork.outputData.getString(NsUploaderWorker.BEACONDB_RESULT)
                                ?: "",
                            ocidResultMessage = finishedWork.outputData.getString(NsUploaderWorker.OCID_RESULT_MESSAGE)
                                ?: "",
                            beaconDbResultMessage = finishedWork.outputData.getString(
                                NsUploaderWorker.BEACONDB_RESULT_MESSAGE
                            )
                                ?: "",
                        )
                    )
                    workManager.pruneWork()
                } else {
                    dashboardViewModel.updateUploadProgress(UploadProgressState.Hidden)
                }
            }
        }

        liveData.observe(lifecycleOwner, observer)

        onDispose {
            liveData.removeObserver(observer)
        }
    }
}
