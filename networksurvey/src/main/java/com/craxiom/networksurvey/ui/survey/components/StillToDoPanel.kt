package com.craxiom.networksurvey.ui.survey.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.status.StatusAction
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.common.ROW_ICON
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.survey.DestinationStatusText
import com.craxiom.networksurvey.ui.survey.model.StillToDoItemUi
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * "Still to do" at the top of the Ready screen: records a finished survey left on the phone for
 * NS Analytics or the community databases, why they wait, and Upload now. It stays until the
 * queues are empty, so waiting records never go unmentioned after Stop.
 */
@Composable
fun StillToDoPanel(
    items: List<StillToDoItemUi>,
    nowMs: Long,
    onUploadNow: (Destination) -> Unit,
    onOpen: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeader(stringResource(R.string.survey_still_to_do))
        NsPanel {
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StillToDoRow(item, nowMs, onUploadNow, onOpen)
            }
        }
    }
}

@Composable
private fun StillToDoRow(item: StillToDoItemUi, nowMs: Long, onUploadNow: (Destination) -> Unit, onOpen: (Destination) -> Unit) {
    val name = stringResource(item.destination.labelRes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NsTheme.spacing.rowMinHeight)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.survey_dest_open)) { onOpen(item.destination) }
            .padding(vertical = NsTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        Icon(
            painter = painterResource(item.destination.iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ROW_ICON),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pluralStringResource(R.plurals.survey_still_to_do_records, item.pending, item.pending, name),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = DestinationStatusText.waitingDetail(LocalResources.current, item.status.detail, nowMs),
                style = MaterialTheme.typography.bodySmall,
                color = statusLevelColor(item.status.level),
            )
        }
        if (item.status.action == StatusAction.UPLOAD_NOW) {
            NsTextAction(stringResource(R.string.survey_upload_now), onClick = { onUploadNow(item.destination) })
        }
    }
}
