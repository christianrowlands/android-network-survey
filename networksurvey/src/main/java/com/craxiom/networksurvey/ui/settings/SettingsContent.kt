package com.craxiom.networksurvey.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.ui.common.NsGroupLabel
import com.craxiom.networksurvey.ui.common.NsPageScaffold
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.common.NsRowDivider
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.NsSwitchRow

/** What the Settings rows ask the page to do. */
interface SettingsActions {
    fun onBack()
    fun onSwitch(key: String, on: Boolean)
    fun onAutoStart(on: Boolean)
    fun onOverride(on: Boolean)
    fun onOpen(picker: SettingsPicker)
    fun onLocked()

    /** A destination the organization blocks; the override does not lift it, so it is not offered. */
    fun onBlocked()
    fun onOpenBatteryOptimization()
    fun onOpenNsAnalytics()
    fun onOpenUploads()
    fun onOpenMapSettings()
    fun onOpenSsidExclusions()
    fun onOpenOrganization()
    fun onCopy(label: String, value: String)
}

/** The dialogs and sheets a row opens. */
enum class SettingsPicker { SCAN_RATES, LOCATION_SOURCE, BATTERY_THRESHOLD, THEME, ACCENT, UNITS, QUEUE_LIMIT }

/**
 * The Settings page: grouped panels under small labels, as in the prototype (Survey, Phone,
 * Destinations, Watchlist, Map and lookups, Fleet, Device IDs). A setting the organization set
 * keeps its value, shows "Set by your organization" and explains itself on a tap.
 */
@Composable
fun SettingsContent(state: SettingsUiState, actions: SettingsActions, snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier) {
    NsPageScaffold(
        title = stringResource(R.string.settings),
        onBack = actions::onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        SurveyGroup(state, actions)
        PhoneGroup(state, actions)
        DestinationsGroup(state, actions)
        WatchlistGroup(state, actions)
        MapGroup(state, actions)
        FleetGroup(state, actions)
        DeviceIdsGroup(state, actions)
    }
}

@Composable
private fun SurveyGroup(state: SettingsUiState, actions: SettingsActions) {
    NsGroupLabel(stringResource(R.string.settings_group_survey))
    NsRowGroup {
        LockableSwitch(R.string.auto_start_survey_title, state.autoStart, actions, R.drawable.ic_play, R.string.auto_start_survey_summary) {
            actions.onAutoStart(it)
        }
        NsRowDivider()
        NsRow(
            title = stringResource(R.string.settings_scan_rates),
            subtitle = scanRatesSummary(state.scanRates),
            icon = R.drawable.ic_tune,
            onClick = { actions.onOpen(SettingsPicker.SCAN_RATES) },
        )
        NsRowDivider()
        LockableSwitch(
            R.string.include_neighbor_cells_title, state.neighborCells, actions, R.drawable.ic_cell_tower,
            if (state.neighborCells.value) R.string.include_neighbor_cells_summary_on else R.string.include_neighbor_cells_summary_off,
        ) { actions.onSwitch(NetworkSurveyConstants.PROPERTY_INCLUDE_NEIGHBOR_CELLS, it) }
        NsRowDivider()
        LockableSwitch(
            R.string.auto_include_phone_state_title, state.phoneStateWithCellular, actions, R.drawable.ic_phone_state,
            if (state.phoneStateWithCellular.value) R.string.auto_include_phone_state_summary_on else R.string.auto_include_phone_state_summary_off,
        ) { actions.onSwitch(NetworkSurveyConstants.PROPERTY_AUTO_INCLUDE_PHONE_STATE_WITH_CELLULAR, it) }
    }
}

@Composable
private fun PhoneGroup(state: SettingsUiState, actions: SettingsActions) {
    NsGroupLabel(stringResource(R.string.settings_group_phone))
    NsRowGroup {
        PickerRow(
            R.string.location_provider_title, R.drawable.ic_my_location, state.locationProvider.locked, actions, SettingsPicker.LOCATION_SOURCE,
            subtitle = stringArrayResource(R.array.location_provider_option_labels).getOrNull(state.locationProvider.value),
        )
        NsRowDivider()
        PickerRow(
            R.string.battery_pause_title, R.drawable.ic_battery_alert, state.batteryThreshold.locked, actions, SettingsPicker.BATTERY_THRESHOLD,
            subtitle = batteryThresholdLabel(state.batteryThreshold.value),
        )
        NsRowDivider()
        NsRow(
            title = stringResource(R.string.battery_optimization_settings_title),
            subtitle = stringResource(
                if (state.batteryOptimizationOff) R.string.battery_optimization_settings_summary_disabled
                else R.string.battery_optimization_settings_summary_enabled
            ),
            icon = R.drawable.ic_battery_alert,
            onClick = actions::onOpenBatteryOptimization,
        )
        NsRowDivider()
        NsSwitchRow(
            title = stringResource(R.string.keep_screen_on_while_recording_title),
            subtitle = stringResource(R.string.keep_screen_on_while_recording_summary),
            checked = state.keepScreenOn,
            icon = R.drawable.ic_display_settings,
            onCheckedChange = { actions.onSwitch(NetworkSurveyConstants.PROPERTY_KEEP_SCREEN_ON_WHILE_RECORDING, it) },
        )
        NsRowDivider()
        PickerRow(
            R.string.app_theme_title, R.drawable.ic_contrast, locked = false, actions, SettingsPicker.THEME,
            subtitle = arrayLabel(R.array.app_theme_values, R.array.app_theme_entries, state.theme),
        )
        NsRowDivider()
        // The accent's own color, on its icon and as a swatch, makes this row a focal point in the list
        NsRow(
            title = stringResource(R.string.app_accent_title),
            subtitle = stringResource(state.accent.label),
            icon = R.drawable.ic_palette,
            iconTint = MaterialTheme.colorScheme.primary,
            onClick = { actions.onOpen(SettingsPicker.ACCENT) },
        ) { AccentSwatch(MaterialTheme.colorScheme.primary, ACCENT_ROW_SWATCH) }
        NsRowDivider()
        PickerRow(
            R.string.measurement_units_title, R.drawable.ic_tune, locked = false, actions, SettingsPicker.UNITS,
            subtitle = arrayLabel(R.array.measurement_unit_values, R.array.measurement_unit_entries, state.units),
        )
    }
}

@Composable
private fun DestinationsGroup(state: SettingsUiState, actions: SettingsActions) {
    NsGroupLabel(stringResource(R.string.settings_group_destinations))
    NsRowGroup {
        NsRow(
            title = stringResource(R.string.survey_dest_ns_analytics),
            subtitle = stringResource(if (state.nsAnalyticsBlocked) R.string.ns_analytics_disabled_via_mdm else R.string.settings_ns_analytics_summary),
            icon = R.drawable.ic_ns_analytics,
            locked = state.nsAnalyticsBlocked,
            onClick = if (state.nsAnalyticsBlocked) actions::onBlocked else actions::onOpenNsAnalytics,
        )
        NsRowDivider()
        NsRow(
            title = stringResource(R.string.settings_upload_title),
            subtitle = stringResource(if (state.uploadsBlocked) R.string.upload_disabled_via_mdm else R.string.settings_upload_summary),
            icon = R.drawable.ic_public,
            locked = state.uploadsBlocked,
            onClick = if (state.uploadsBlocked) actions::onBlocked else actions::onOpenUploads,
        )
        NsRowDivider()
        PickerRow(
            R.string.streaming_queue_limit_title, R.drawable.ic_cloud_connection, state.queueLimit.locked, actions, SettingsPicker.QUEUE_LIMIT,
            subtitle = state.queueLimit.value.toString(),
        )
    }
}

@Composable
private fun WatchlistGroup(state: SettingsUiState, actions: SettingsActions) {
    NsGroupLabel(stringResource(R.string.watchlist_settings_title))
    NsRowGroup {
        NsSwitchRow(
            title = stringResource(R.string.watchlist_settings_enable_title),
            subtitle = stringResource(
                if (state.watchlistEnabled) R.string.watchlist_settings_enable_summary_on else R.string.watchlist_settings_enable_summary_off
            ),
            checked = state.watchlistEnabled,
            icon = R.drawable.ic_watchlist,
            onCheckedChange = { actions.onSwitch(NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED, it) },
        )
        NsRowDivider()
        LockableSwitch(R.string.watchlist_stream_setting_title, state.watchlistStream, actions, R.drawable.ic_cloud_connection, R.string.watchlist_stream_setting_summary) {
            actions.onSwitch(NetworkSurveyConstants.PROPERTY_MQTT_WATCHLIST_STREAM_ENABLED, it)
        }
    }
}

@Composable
private fun MapGroup(state: SettingsUiState, actions: SettingsActions) {
    NsGroupLabel(stringResource(R.string.settings_group_map))
    NsRowGroup {
        NsRow(
            title = stringResource(R.string.map_settings_title),
            subtitle = stringResource(R.string.map_settings_summary),
            icon = R.drawable.ic_map,
            onClick = actions::onOpenMapSettings,
        )
        NsRowDivider()
        LockableSwitch(R.string.settings_oui_title, state.ouiLookup, actions, R.drawable.ic_search_24, R.string.settings_oui_summary) {
            actions.onSwitch(NetworkSurveyConstants.PROPERTY_OUI_LOOKUP_ENABLED, it)
        }
        NsRowDivider()
        NsRow(
            title = stringResource(R.string.ssid_exclusion_list_title),
            subtitle = stringResource(R.string.ssid_exclusion_list_description),
            icon = R.drawable.ic_wifi,
            onClick = actions::onOpenSsidExclusions,
        )
    }
}

@Composable
private fun FleetGroup(state: SettingsUiState, actions: SettingsActions) {
    NsGroupLabel(stringResource(R.string.settings_group_fleet))
    NsRowGroup {
        LockableSwitch(
            R.string.allow_intent_control_title, state.intentControl, actions, R.drawable.ic_security_24,
            if (state.intentControl.value) R.string.allow_intent_control_summary_on else R.string.allow_intent_control_summary_off,
        ) { actions.onSwitch(NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL, it) }
        if (state.managed) {
            NsRowDivider()
            NsRow(
                title = stringResource(R.string.organization_settings_title),
                subtitle = stringResource(R.string.organization_settings_summary),
                icon = R.drawable.ic_domain,
                onClick = actions::onOpenOrganization,
            )
            NsRowDivider()
            NsSwitchRow(
                title = stringResource(R.string.mdm_override_title),
                subtitle = stringResource(if (state.overrideOn) R.string.mdm_override_summary_on else R.string.mdm_override_summary_off),
                checked = state.overrideOn,
                icon = R.drawable.ic_domain,
                onCheckedChange = actions::onOverride,
            )
        }
    }
}

@Composable
private fun DeviceIdsGroup(state: SettingsUiState, actions: SettingsActions) {
    NsGroupLabel(stringResource(R.string.settings_device_ids))
    NsRowGroup {
        val serialLabel = stringResource(R.string.settings_device_serial_number)
        val serial = state.deviceSerial ?: stringResource(R.string.unknown)
        NsRow(title = serialLabel, subtitle = serial, icon = R.drawable.ic_info, onClick = { actions.onCopy(serialLabel, serial) })
        state.appInstanceId?.let { id ->
            NsRowDivider()
            val idLabel = stringResource(R.string.settings_app_instance_id)
            NsRow(title = idLabel, subtitle = id, icon = R.drawable.ic_info, onClick = { actions.onCopy(idLabel, id) })
        }
    }
}

/** A switch the organization may set: locked, it keeps its value and a tap explains instead. */
@Composable
private fun LockableSwitch(title: Int, setting: SettingValue<Boolean>, actions: SettingsActions, icon: Int, summary: Int, onChange: (Boolean) -> Unit) {
    NsSwitchRow(
        title = stringResource(title),
        subtitle = stringResource(summary),
        checked = setting.value,
        icon = icon,
        locked = setting.locked,
        onLocked = actions::onLocked,
        onCheckedChange = onChange,
    )
}

/** A row whose value is chosen in a dialog or sheet; locked, a tap explains instead. */
@Composable
private fun PickerRow(title: Int, icon: Int, locked: Boolean, actions: SettingsActions, picker: SettingsPicker, subtitle: String?) {
    NsRow(
        title = stringResource(title),
        subtitle = subtitle,
        icon = icon,
        locked = locked,
        onClick = { if (locked) actions.onLocked() else actions.onOpen(picker) },
    )
}

/** "Cellular 5 s · Wi-Fi 15 s · Bluetooth 30 s · GNSS 45 s" (device status is left out). */
@Composable
private fun scanRatesSummary(rates: Map<ScanRate, SettingValue<Int>>): String =
    ScanRate.entries.mapNotNull { rate ->
        rate.shortName?.let { stringResource(R.string.settings_scan_rate_part, stringResource(it), rates.getValue(rate).value) }
    }.joinToString(SUMMARY_SEPARATOR)

/** "Disabled" or "Pause when battery is at 20% or below". */
@Composable
fun batteryThresholdLabel(percent: Int): String =
    if (percent <= 0) stringResource(R.string.battery_management_disabled) else stringResource(R.string.battery_threshold_summary, percent)

/** The label of [value] in a list preference's values and labels arrays. */
@Composable
private fun arrayLabel(values: Int, labels: Int, value: String): String? {
    val index = stringArrayResource(values).indexOf(value)
    return stringArrayResource(labels).getOrNull(index)
}

private const val SUMMARY_SEPARATOR = " · "
