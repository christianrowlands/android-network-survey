package com.craxiom.networksurvey.ui.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.AppPermissions
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import com.craxiom.networksurvey.ui.common.NsManagedLockSheet
import com.craxiom.networksurvey.ui.common.annotatedStringResource
import com.craxiom.networksurvey.ui.common.dialogs.NsSingleChoiceDialog
import com.craxiom.networksurvey.ui.survey.rememberSurveyPermissionFlow
import com.craxiom.networksurvey.ui.survey.sheets.UseOwnSettingsDialog
import com.craxiom.networksurvey.ui.util.copyWithSystemConfirmation
import com.craxiom.networksurvey.util.BatteryOptimizationHelper

/** Where the Settings page leads. */
class SettingsNavigation(
    val onBack: () -> Unit,
    val onOpenNsAnalytics: () -> Unit,
    val onOpenUploads: () -> Unit,
    val onOpenMapSettings: () -> Unit,
    val onOpenSsidExclusions: () -> Unit,
    val onOpenOrganization: () -> Unit,
    /** Explains background location, which starting at boot needs (shown by the main screen). */
    val onBackgroundLocationRationale: () -> Unit,
)

/**
 * The Settings page: [SettingsContent] on [SettingsViewModel], with the pickers, the queue limit
 * questions, the organization lock sheet, and the call records permissions that starting
 * automatically needs when the survey records them.
 */
@Composable
fun SettingsScreen(navigation: SettingsNavigation, viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionFlow = rememberSurveyPermissionFlow()
    var picker by rememberSaveable { mutableStateOf<SettingsPicker?>(null) }
    var lockSheet by rememberSaveable { mutableStateOf(false) }
    var blockedSheet by rememberSaveable { mutableStateOf(false) }
    var confirmOwnSettings by rememberSaveable { mutableStateOf(false) }

    // Battery optimization changes in Android's settings, so read it again on every return
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    LaunchedEffect(notice) {
        when (val current = notice) {
            is SettingsNotice.BluetoothMinimum -> {
                viewModel.consumeNotice()
                snackbarHostState.showSnackbar(
                    resources.getQuantityString(R.plurals.bluetooth_scan_interval_below_minimum, current.seconds, current.seconds)
                )
            }

            null -> Unit
        }
    }

    val openPicker: (SettingsPicker) -> Unit = { picker = it }
    val showLockSheet: () -> Unit = { lockSheet = true }
    val showBlockedSheet: () -> Unit = { blockedSheet = true }
    val actions = remember(viewModel, navigation, permissionFlow) {
        object : SettingsActions {
            override fun onBack() = navigation.onBack()
            override fun onSwitch(key: String, on: Boolean) = viewModel.setBoolean(key, on)
            override fun onAutoStart(on: Boolean) {
                when {
                    !on -> viewModel.setBoolean(SurveyMdmKeys.AUTO_START_SURVEY, false)

                    // Starting on its own records call records, so their permissions come first
                    RecordType.CDR in SurveySetupStore(context).getCurrentSetup().recordTypes ->
                        permissionFlow.ensure(RecordType.CDR) { viewModel.setBoolean(SurveyMdmKeys.AUTO_START_SURVEY, true) }

                    else -> {
                        viewModel.setBoolean(SurveyMdmKeys.AUTO_START_SURVEY, true)
                        if (AppPermissions.canAskForBackgroundLocation(context)) navigation.onBackgroundLocationRationale()
                    }
                }
            }

            override fun onOverride(on: Boolean) = viewModel.setOverride(on)
            override fun onOpen(picker: SettingsPicker) = openPicker(picker)
            override fun onLocked() = showLockSheet()
            override fun onBlocked() = showBlockedSheet()
            override fun onOpenBatteryOptimization() = BatteryOptimizationHelper(context).openBatteryOptimizationSettings()
            override fun onOpenNsAnalytics() = navigation.onOpenNsAnalytics()
            override fun onOpenUploads() = navigation.onOpenUploads()
            override fun onOpenMapSettings() = navigation.onOpenMapSettings()
            override fun onOpenSsidExclusions() = navigation.onOpenSsidExclusions()
            override fun onOpenOrganization() = navigation.onOpenOrganization()
            override fun onCopy(label: String, value: String) =
                copyWithSystemConfirmation(context, label, value, resources.getString(R.string.value_copied_to_clipboard))
        }
    }

    SettingsContent(state = state, actions = actions, snackbarHostState = snackbarHostState)

    SettingsPickerHost(picker, state, viewModel, onLocked = { lockSheet = true }) { picker = null }
    prompt?.let { QueueLimitPromptDialog(it, viewModel) }
    if (lockSheet) {
        NsManagedLockSheet(
            onSeeAll = navigation.onOpenOrganization,
            onUseOwnSettings = {
                lockSheet = false
                confirmOwnSettings = true
            },
            onDismiss = { lockSheet = false },
        )
    }
    if (blockedSheet) {
        NsManagedLockSheet(onSeeAll = navigation.onOpenOrganization, onUseOwnSettings = null, onDismiss = { blockedSheet = false })
    }
    if (confirmOwnSettings) {
        UseOwnSettingsDialog(onConfirm = { viewModel.setOverride(true) }, onDismiss = { confirmOwnSettings = false })
    }
}

/** The dialog or sheet [picker] names, or nothing. */
@Composable
private fun SettingsPickerHost(
    picker: SettingsPicker?,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onLocked: () -> Unit,
    onClose: () -> Unit,
) {
    when (picker) {
        SettingsPicker.SCAN_RATES -> ScanRatesSheet(
            rates = state.scanRates,
            onSave = { changed -> changed.forEach { (rate, seconds) -> viewModel.setScanRate(rate, seconds) } },
            onLocked = {
                onClose()
                onLocked()
            },
            onDismiss = onClose,
        )

        SettingsPicker.LOCATION_SOURCE -> NsSingleChoiceDialog(
            title = stringResource(R.string.location_provider_title),
            options = stringArrayResource(R.array.location_provider_option_labels).toList(),
            selectedIndex = state.locationProvider.value,
            onConfirm = viewModel::setLocationProvider,
            onDismiss = onClose,
            message = annotatedStringResource(R.string.location_provider_description),
        )

        SettingsPicker.BATTERY_THRESHOLD -> BatteryThresholdDialog(
            title = stringResource(R.string.battery_pause_title),
            initialValue = state.batteryThreshold.value,
            onConfirm = viewModel::setBatteryThreshold,
            onDismiss = onClose,
        )

        SettingsPicker.THEME -> ValueChoiceDialog(R.string.app_theme_title, R.array.app_theme_values, R.array.app_theme_entries, state.theme, viewModel::setTheme, onClose)
        SettingsPicker.UNITS -> ValueChoiceDialog(
            R.string.measurement_units_title, R.array.measurement_unit_values, R.array.measurement_unit_entries, state.units, viewModel::setUnits, onClose,
        )

        SettingsPicker.QUEUE_LIMIT -> SettingsNumberDialog(
            title = stringResource(R.string.streaming_queue_limit_title),
            message = stringResource(R.string.streaming_queue_limit_description),
            initialValue = state.queueLimit.value,
            onConfirm = viewModel::setQueueLimit,
            onDismiss = onClose,
        )

        null -> Unit
    }
}

/** A single choice among a list preference's labels, storing the matching value. */
@Composable
private fun ValueChoiceDialog(title: Int, values: Int, labels: Int, current: String, onChoose: (String) -> Unit, onClose: () -> Unit) {
    val valueList = stringArrayResource(values)
    NsSingleChoiceDialog(
        title = stringResource(title),
        options = stringArrayResource(labels).toList(),
        selectedIndex = valueList.indexOf(current).coerceAtLeast(0),
        onConfirm = { index -> valueList.getOrNull(index)?.let(onChoose) },
        onDismiss = onClose,
    )
}
