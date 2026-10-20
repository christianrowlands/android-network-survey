package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.wifi.model.ThrottleWarning

/**
 * Says that Android throttles Wi-Fi scans, so the list updates less often than the scan interval
 * asks, with a way to the setting (or to About phone, to turn developer options on first) and
 * Dismiss. It sits above the rows instead of covering them.
 */
@Composable
fun WifiThrottleNotice(
    warning: ThrottleWarning,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = buildString {
        append(stringResource(if (warning.android9) R.string.android_9_throttling_information else R.string.android_10_throttling_information))
        if (!warning.android9 && !warning.developerOptionsOn) {
            append("\n\n")
            append(stringResource(R.string.enable_developer_options))
        }
    }
    NsPanel(
        color = NsTheme.colors.status.warning.container,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NsTheme.spacing.screenGutter, vertical = NsTheme.spacing.s),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s, Alignment.End),
        ) {
            NsTextAction(text = stringResource(R.string.wifi_throttle_dismiss), onClick = onDismiss)
            if (!warning.android9) {
                NsTextAction(text = stringResource(R.string.open_settings), onClick = onOpenSettings)
            }
        }
    }
}
