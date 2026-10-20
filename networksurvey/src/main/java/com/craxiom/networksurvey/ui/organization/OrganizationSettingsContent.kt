package com.craxiom.networksurvey.ui.organization

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsEmptyState
import com.craxiom.networksurvey.ui.common.NsPageScaffold
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.common.NsRowDivider
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.NsSummaryText
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.survey.sheets.RestoreOrganizationDialog
import com.craxiom.networksurvey.ui.survey.sheets.UseOwnSettingsDialog

/** Everything the Organization settings page can ask for. */
interface OrganizationSettingsActions {
    fun onBack() {}
    fun onUseOwnSettings() {}
    fun onRestore() {}
}

/**
 * The Organization settings page, stateless: every value the organization sets on this phone, read
 * only, with notes for the cases people ask about, and the way to use your own settings (or restore
 * the organization's).
 *
 * @param state Null while loading.
 * @param recording True while a survey runs, for the Restore dialog's wording.
 */
@Composable
fun OrganizationSettingsContent(
    state: OrganizationSettingsUi?,
    recording: Boolean,
    actions: OrganizationSettingsActions,
    modifier: Modifier = Modifier,
) {
    var dialog by rememberSaveable { mutableStateOf<OverrideDialog?>(null) }

    NsPageScaffold(title = stringResource(R.string.organization_settings_title), onBack = actions::onBack, modifier = modifier) {
        when {
            state == null -> Unit
            !state.managed -> NsRowGroup { NsEmptyState(stringResource(R.string.org_unmanaged), R.drawable.ic_domain) }
            else -> ManagedSettings(state) { dialog = it }
        }
    }

    when (dialog) {
        OverrideDialog.USE_OWN -> UseOwnSettingsDialog(onConfirm = actions::onUseOwnSettings, onDismiss = { dialog = null })
        OverrideDialog.RESTORE -> RestoreOrganizationDialog(recording, onConfirm = actions::onRestore, onDismiss = { dialog = null })
        null -> Unit
    }
}

private enum class OverrideDialog { USE_OWN, RESTORE }

@Composable
private fun ManagedSettings(state: OrganizationSettingsUi, onDialog: (OverrideDialog) -> Unit) {
    if (state.overrideOn) NsSummaryText(stringResource(R.string.org_note_overridden))
    if (state.brokerOnly) NsSummaryText(stringResource(R.string.org_note_broker_only))
    if (state.communityOffBecauseManaged && state.externalUploadTitleRes != 0) {
        NsSummaryText(stringResource(R.string.org_note_community_managed, stringResource(state.externalUploadTitleRes)))
    }
    state.groups.forEach { group ->
        SectionHeader(stringResource(group.group.titleRes))
        NsRowGroup {
            group.rows.forEachIndexed { index, row ->
                if (index > 0) NsRowDivider()
                NsRow(title = if (row.titleRes != 0) stringResource(row.titleRes) else row.key, role = null) {
                    Text(
                        text = valueText(row.value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    NsRowGroup {
        if (state.overrideOn) {
            NsRow(title = stringResource(R.string.survey_org_restore), icon = R.drawable.ic_domain, onClick = { onDialog(OverrideDialog.RESTORE) })
        } else {
            NsRow(title = stringResource(R.string.survey_lock_use_own), icon = R.drawable.ic_settings, onClick = { onDialog(OverrideDialog.USE_OWN) })
        }
    }
}

@Composable
private fun valueText(value: OrgValue): String = when (value) {
    OrgValue.On -> stringResource(R.string.survey_lock_state_on)
    OrgValue.Off -> stringResource(R.string.survey_lock_state_off)
    OrgValue.UserChooses -> stringResource(R.string.org_value_user)
    OrgValue.Allowed -> stringResource(R.string.org_value_allowed)
    OrgValue.NotAllowed -> stringResource(R.string.survey_lock_not_allowed)
    OrgValue.Secret -> stringResource(R.string.org_value_set)
    is OrgValue.Text -> value.text
}

private val OrgGroup.titleRes: Int
    get() = when (this) {
        OrgGroup.SURVEY -> R.string.org_group_survey
        OrgGroup.PERMISSIONS -> R.string.org_group_permissions
        OrgGroup.MQTT -> R.string.org_group_mqtt
        OrgGroup.OTHER -> R.string.org_group_other
    }
