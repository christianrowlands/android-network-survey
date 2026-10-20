package com.craxiom.networksurvey.ui.nsanalytics

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.common.NsRowDivider
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.NsStatusPanel
import com.craxiom.networksurvey.ui.common.NsSummaryText
import com.craxiom.networksurvey.ui.common.NsSwitchRow
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.NsAnalyticsUtils

/** Everything the connected NS Analytics page can ask for. */
interface NsAnalyticsConnectedActions {
    fun onToggleAutoUpload(enabled: Boolean) {}
    fun onUploadNow() {}
    fun onOpenDashboard() {}
    fun onOpenUrl(url: String) {}
    fun onClearQueue() {}
    fun onDisconnect() {}
}

/**
 * The NS Analytics page once this phone is connected: the workspace, its uploads, what is sent,
 * and the way to disconnect. Starting a survey that sends here is the Survey screen's job.
 */
@Composable
fun NsAnalyticsConnectedContent(state: NsAnalyticsConnectionUiState, actions: NsAnalyticsConnectedActions) {
    StatusPanel(state)
    if (state.uploadPausedForQuota) QuotaPausedPanel(state, actions)

    NsRowGroup {
        NsSwitchRow(
            title = stringResource(R.string.ns_analytics_upload_auto),
            subtitle = pluralStringResource(
                R.plurals.ns_analytics_upload_auto_detail,
                state.uploadFrequencyMinutes,
                state.uploadFrequencyMinutes,
            ),
            checked = state.autoUploadEnabled,
            onCheckedChange = actions::onToggleAutoUpload,
            icon = R.drawable.ic_schedule,
        )
        NsRowDivider()
        UploadNowRow(state, actions)
        if (state.quotaMaxRecords > 0 && !state.uploadPausedForQuota) {
            NsRowDivider()
            NsRow(
                title = stringResource(R.string.ns_analytics_usage),
                subtitle = usageText(state),
                icon = R.drawable.ic_bar_chart,
            )
        }
        NsRowDivider()
        NsRow(
            title = stringResource(R.string.ns_analytics_open_dashboard),
            icon = R.drawable.ic_open_in_new,
            onClick = actions::onOpenDashboard,
        )
    }

    SectionHeader(stringResource(R.string.destination_what_is_sent_title))
    NsSummaryText(stringResource(R.string.ns_analytics_what_is_sent))

    NsRowGroup {
        if (state.queuedRecords > 0) {
            NsRow(title = stringResource(R.string.ns_analytics_clear_queue), icon = R.drawable.ic_delete, onClick = actions::onClearQueue)
            NsRowDivider()
        }
        NsRow(
            title = stringResource(R.string.ns_analytics_disconnect),
            subtitle = if (state.isSurveyActive) stringResource(R.string.ns_analytics_disconnect_stop_first) else null,
            icon = R.drawable.ic_logout,
            titleColor = MaterialTheme.colorScheme.error,
            iconTint = MaterialTheme.colorScheme.error,
            onClick = actions::onDisconnect,
        )
    }
}

@Composable
private fun StatusPanel(state: NsAnalyticsConnectionUiState) {
    NsStatusPanel(
        icon = R.drawable.ic_cloud_done,
        iconTint = NsTheme.colors.status.success.content,
        title = stringResource(
            R.string.ns_analytics_connected_status,
            state.workspaceName ?: stringResource(R.string.ns_analytics_unknown_workspace),
        ),
        subtitle = if (state.lastUploadTime > 0) {
            stringResource(R.string.ns_analytics_last_upload, DateUtils.getRelativeTimeSpanString(state.lastUploadTime).toString())
        } else {
            stringResource(R.string.ns_analytics_no_upload_yet)
        },
    )
}

@Composable
private fun UploadNowRow(state: NsAnalyticsConnectionUiState, actions: NsAnalyticsConnectedActions) {
    val uploading = state.uploadState == UploadState.UPLOADING
    val failed = state.uploadState == UploadState.UNAVAILABLE && state.uploadStatusMessage.isNotBlank()
    val subtitle = when {
        uploading || failed -> state.uploadStatusMessage
        else -> listOfNotNull(
            pluralStringResource(R.plurals.ns_analytics_records_waiting, state.queuedRecords, state.queuedRecords),
            state.lastUploadResult,
        ).joinToString(SEPARATOR)
    }
    val canUpload = !uploading && state.queuedRecords > 0
    // Records waiting is the moment to act, so the row takes the accent like the Survey screen's Upload now
    val colors = MaterialTheme.colorScheme
    Column {
        NsRow(
            title = stringResource(R.string.ns_analytics_upload_now),
            subtitle = subtitle,
            titleColor = if (canUpload) colors.primary else colors.onSurface,
            subtitleColor = if (failed) NsTheme.colors.status.warningText else MaterialTheme.colorScheme.onSurfaceVariant,
            icon = R.drawable.ic_upload_24,
            iconTint = if (canUpload) colors.primary else colors.onSurfaceVariant,
            onClick = if (canUpload) actions::onUploadNow else null,
        )
        if (uploading) {
            LinearProgressIndicator(progress = { state.uploadProgress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun QuotaPausedPanel(state: NsAnalyticsConnectionUiState, actions: NsAnalyticsConnectedActions) {
    NsPanel(color = NsTheme.colors.status.warning.container) {
        Text(
            text = stringResource(R.string.ns_analytics_upload_paused_banner_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = state.quotaMessage?.takeIf { it.isNotBlank() } ?: stringResource(R.string.ns_analytics_upload_paused_banner_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (state.quotaMaxRecords > 0) {
            Text(usageText(state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.quotaWebUrl?.takeIf { it.isNotBlank() }?.let { url ->
            NsTextAction(stringResource(R.string.ns_analytics_manage_subscription), onClick = { actions.onOpenUrl(url) })
        }
    }
}

@Composable
private fun usageText(state: NsAnalyticsConnectionUiState): String = stringResource(
    R.string.ns_analytics_quota_usage,
    NsAnalyticsUtils.formatNumberWithThousandsSeparator(state.quotaCurrentUsage),
    NsAnalyticsUtils.formatNumberWithThousandsSeparator(state.quotaMaxRecords),
)

private const val SEPARATOR = " · "
