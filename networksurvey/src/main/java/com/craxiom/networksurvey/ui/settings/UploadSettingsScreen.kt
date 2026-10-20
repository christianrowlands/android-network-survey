package com.craxiom.networksurvey.ui.settings

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.survey.mdm.AndroidRestrictionsSource
import com.craxiom.networksurvey.survey.mdm.OrganizationOverride
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.ui.common.NsGroupLabel
import com.craxiom.networksurvey.ui.common.NsManagedLockSheet
import com.craxiom.networksurvey.ui.common.NsPageScaffold
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.common.NsRowDivider
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.NsSwitchRow
import com.craxiom.networksurvey.ui.common.dialogs.NsInputDialog
import com.craxiom.networksurvey.ui.survey.sheets.UseOwnSettingsDialog
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.CredentialSecureStorage
import com.craxiom.networksurvey.util.OpenCelliDSigning
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * OpenCelliD and BeaconDB upload settings. The OpenCelliD key is kept in encrypted storage, never
 * in the plain preferences; the auto upload switches can be set by the organization.
 */
@Composable
fun UploadSettingsScreen(onBack: () -> Unit, onOpenOrganization: () -> Unit) {
    val preferences = rememberAppPreferences()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val restrictions = remember { AndroidRestrictionsSource(context).snapshot() }
    var editingKey by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var lockSheet by rememberSaveable { mutableStateOf(false) }
    var confirmOwnSettings by rememberSaveable { mutableStateOf(false) }
    var personalKey by remember { mutableStateOf(CredentialSecureStorage.getOcidApiKey(context)?.trim().orEmpty()) }
    val sharedToken = remember { OpenCelliDSigning.hasSharedToken(context) }

    LaunchedEffect(Unit) {
        ManagedSettingsSync.write(preferences, restrictions)
        // A key typed before it moved to encrypted storage must not stay readable
        preferences.edit { remove(NetworkSurveyConstants.PROPERTY_OCID_API_KEY) }
    }

    val autoUpload by preferences.observe { managedOr(it, restrictions, NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_ENABLED, false) }
    val wifiOnly by preferences.observe { managedOr(it, restrictions, NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_WIFI_ONLY, true) }

    NsPageScaffold(title = stringResource(R.string.community_upload_settings), onBack = onBack, snackbarHostState = snackbarHostState) {
        NsGroupLabel(stringResource(R.string.settings))
        NsRowGroup {
            PreferenceSwitch(preferences, NetworkSurveyConstants.PROPERTY_SHOW_CONFIG_UPLOAD_DIALOG, true, R.string.show_upload_dialog_pref_title,
                R.string.show_upload_dialog_summary_on, R.string.show_upload_dialog_summary_off)
            NsRowDivider()
            PreferenceSwitch(preferences, NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED, NetworkSurveyConstants.DEFAULT_UPLOAD_RETRY_ENABLED,
                R.string.upload_retry_title, R.string.upload_retry_summary, R.string.upload_retry_summary)
            NsRowDivider()
            PreferenceSwitch(preferences, NetworkSurveyConstants.PROPERTY_NEW_TOWER_ALERTS_ENABLED, false, R.string.new_tower_alerts_title,
                R.string.new_tower_alerts_summary_on, R.string.new_tower_alerts_summary_off, icon = R.drawable.ic_cell_tower)
            NsRowDivider()
            NsSwitchRow(
                title = stringResource(R.string.auto_upload_title),
                subtitle = stringResource(if (autoUpload.value) R.string.auto_upload_summary_on else R.string.auto_upload_summary_off),
                checked = autoUpload.value,
                locked = autoUpload.locked,
                onLocked = { lockSheet = true },
                onCheckedChange = { on -> preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_ENABLED, on) } },
            )
            // Wi-Fi only belongs to auto upload, so it shows only while auto upload is on
            if (autoUpload.value) {
                NsRowDivider()
                NsSwitchRow(
                    title = stringResource(R.string.auto_upload_wifi_only_title),
                    subtitle = stringResource(if (wifiOnly.value) R.string.auto_upload_wifi_only_summary_on else R.string.auto_upload_wifi_only_summary_off),
                    checked = wifiOnly.value,
                    locked = wifiOnly.locked,
                    onLocked = { lockSheet = true },
                    onCheckedChange = { on -> preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_WIFI_ONLY, on) } },
                )
            }
        }

        NsGroupLabel(stringResource(R.string.upload_ocid_category))
        NsRowGroup {
            PreferenceSwitch(preferences, NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, NetworkSurveyConstants.DEFAULT_UPLOAD_TO_OPENCELLID,
                R.string.upload_to_ocid_title, R.string.upload_to_ocid_summary, R.string.upload_to_ocid_summary)
            NsRowDivider()
            val keyStatus = ocidKeyStatus(personalKey, sharedToken)
            NsRow(
                title = stringResource(R.string.upload_ocid_key_title),
                subtitle = stringResource(keyStatus.text),
                subtitleColor = if (keyStatus.warning) NsTheme.colors.status.warningText else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { editingKey = true },
            )
            // Anonymous or your key is only a choice with a valid key and the shared token
            if (PreferenceUtils.isApiKeyValid(personalKey) && sharedToken) {
                NsRowDivider()
                PreferenceSwitch(preferences, NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, false, R.string.upload_anonymous_title,
                    R.string.upload_anonymous_summary_on, R.string.upload_anonymous_summary_off)
            }
        }

        NsGroupLabel(stringResource(R.string.upload_beacondb_category))
        NsRowGroup {
            PreferenceSwitch(preferences, NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, NetworkSurveyConstants.DEFAULT_UPLOAD_TO_BEACONDB,
                R.string.upload_to_beacondb_title, R.string.upload_to_beacondb_summary, R.string.upload_to_beacondb_summary)
        }

        NsGroupLabel(stringResource(R.string.upload_data_category))
        NsRowGroup {
            NsRow(
                title = stringResource(R.string.upload_delete_all_title),
                subtitle = stringResource(R.string.upload_delete_all_summary),
                onClick = { confirmingDelete = true },
            )
        }
    }

    if (editingKey) {
        NsInputDialog(
            title = stringResource(R.string.upload_ocid_key_dialog_title),
            // A blank key only means anonymous when the build has the shared token
            message = if (sharedToken) stringResource(R.string.upload_ocid_key_dialog_message) else null,
            initialValue = personalKey,
            allowBlankConfirm = true,
            onConfirm = { text ->
                val key = text.trim()
                // Kept even when it does not look right, as before; the warning says so
                if (key.isNotEmpty() && !PreferenceUtils.isApiKeyValid(key)) {
                    scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.upload_ocid_key_invalid)) }
                }
                OpenCelliDSigning.savePersonalKey(context, key)
                personalKey = key
            },
            onDismiss = { editingKey = false },
        )
    }
    if (confirmingDelete) {
        DeleteUploadDataDialog(
            onDelete = {
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) {
                        runCatching { SurveyDatabase.getInstance(context).surveyRecordDao().deleteAllRecords() }
                            .onFailure { Timber.e(it, "Could not delete the upload data") }
                            .isSuccess
                    }
                    snackbarHostState.showSnackbar(
                        resources.getString(if (deleted) R.string.delete_upload_data_success else R.string.delete_upload_data_failed)
                    )
                }
            },
            onDismiss = { confirmingDelete = false },
        )
    }
    if (lockSheet) {
        NsManagedLockSheet(
            onSeeAll = onOpenOrganization,
            onUseOwnSettings = {
                lockSheet = false
                confirmOwnSettings = true
            },
            onDismiss = { lockSheet = false },
        )
    }
    if (confirmOwnSettings) {
        UseOwnSettingsDialog(
            onConfirm = { scope.launch(Dispatchers.IO) { OrganizationOverride.set(preferences, true) } },
            onDismiss = { confirmOwnSettings = false },
        )
    }
}

/** A plain switch setting with its on and off summaries. */
@Composable
private fun PreferenceSwitch(
    preferences: SharedPreferences,
    key: String,
    default: Boolean,
    title: Int,
    summaryOn: Int,
    summaryOff: Int,
    icon: Int? = null,
) {
    val on by preferences.observe { it.getBoolean(key, default) }
    NsSwitchRow(
        title = stringResource(title),
        subtitle = stringResource(if (on) summaryOn else summaryOff),
        checked = on,
        icon = icon,
        onCheckedChange = { checked -> preferences.edit { putBoolean(key, checked) } },
    )
}

/** The organization's value for [key] (locked) on a managed phone without the override, else the stored one. */
private fun managedOr(
    preferences: SharedPreferences,
    restrictions: RestrictionsSnapshot,
    key: String,
    default: Boolean,
): SettingValue<Boolean> {
    val override = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false)
    val managed = if (restrictions.isManaged && !override) restrictions.getBoolean(key) else null
    return managed?.let { SettingValue(it, locked = true) } ?: SettingValue(preferences.getBoolean(key, default))
}

/** What the OpenCelliD key row says, and whether it is a warning. */
private data class OcidKeyStatus(@StringRes val text: Int, val warning: Boolean)

/** @return What happens with [key]: uploads use it, or go anonymously, or can't run at all. */
private fun ocidKeyStatus(key: String, sharedToken: Boolean): OcidKeyStatus = when {
    key.isEmpty() && sharedToken -> OcidKeyStatus(R.string.upload_ocid_key_not_set_anonymous, warning = false)
    key.isEmpty() -> OcidKeyStatus(R.string.upload_ocid_key_not_set_needed, warning = true)
    !PreferenceUtils.isApiKeyValid(key) && sharedToken -> OcidKeyStatus(R.string.upload_ocid_key_invalid_anonymous, warning = true)
    !PreferenceUtils.isApiKeyValid(key) -> OcidKeyStatus(R.string.upload_ocid_key_invalid_saved, warning = true)
    else -> OcidKeyStatus(R.string.upload_ocid_key_saved, warning = false)
}
