package com.craxiom.networksurvey.ui.survey.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.DestinationCapabilities
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.survey.model.DestinationControl
import com.craxiom.networksurvey.ui.survey.model.DestinationDetail
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import com.craxiom.networksurvey.ui.survey.model.DestinationRowUi
import com.craxiom.networksurvey.ui.survey.model.ItemLock
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsActionRow
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.common.ROW_ICON
import com.craxiom.networksurvey.ui.common.SMALL_ICON

/**
 * The "Where should the data go?" rows. Tapping a row opens the destination's page. A destination
 * that is ready also has a switch, set apart by a short divider as its own target, that turns it
 * on or off (or explains the organization's lock); one that is not ready offers "Set up" or
 * "Connect" instead of a switch that would silently do nothing.
 */
@Composable
fun DestinationRows(
    rows: List<DestinationRowUi>,
    info: DestinationInfo,
    userHiddenCount: Int,
    onToggle: (Destination) -> Unit,
    onOpen: (Destination) -> Unit,
    onLocked: (DestinationRowUi) -> Unit,
    onEditList: () -> Unit,
    modifier: Modifier = Modifier,
    showEditList: Boolean = true,
    onWhatGoesWhere: (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        NsPanel {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DestinationRow(row, info, onToggle, onOpen, onLocked)
            }
        }
        NsActionRow(
            modifier = Modifier.fillMaxWidth(),
            end = {
                if (showEditList) NsTextAction(
                    text = if (userHiddenCount > 0) {
                        pluralStringResource(R.plurals.survey_edit_list_hidden, userHiddenCount, userHiddenCount)
                    } else {
                        stringResource(R.string.survey_edit_list)
                    },
                    onClick = onEditList,
                )
            },
        ) {
            onWhatGoesWhere?.let { NsTextAction(stringResource(R.string.survey_what_goes_where), it) }
        }
    }
}

/** The divider between a row's page target and its switch, short enough to read as a separator. */
private val SWITCH_DIVIDER_HEIGHT = 32.dp

@Composable
private fun DestinationRow(
    row: DestinationRowUi,
    info: DestinationInfo,
    onToggle: (Destination) -> Unit,
    onOpen: (Destination) -> Unit,
    onLocked: (DestinationRowUi) -> Unit,
) {
    val name = stringResource(row.destination.labelRes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NsTheme.spacing.rowMinHeight)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.survey_dest_open)) {
                onOpen(row.destination)
            }
            .padding(vertical = NsTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        Icon(
            painter = painterResource(row.destination.iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ROW_ICON),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            DetailText(row, info)
        }
        RowControl(row, name, onToggle, onOpen, onLocked)
    }
}

@Composable
private fun RowControl(
    row: DestinationRowUi,
    name: String,
    onToggle: (Destination) -> Unit,
    onOpen: (Destination) -> Unit,
    onLocked: (DestinationRowUi) -> Unit,
) {
    when (row.control) {
        DestinationControl.SET_UP, DestinationControl.CONNECT, DestinationControl.CHOOSE -> OutlinedButton(
            onClick = { onOpen(row.destination) },
            modifier = Modifier.heightIn(min = NsTheme.spacing.touchTarget),
        ) {
            Text(
                stringResource(
                    when (row.control) {
                        DestinationControl.CONNECT -> R.string.survey_action_connect
                        DestinationControl.CHOOSE -> R.string.survey_action_choose
                        else -> R.string.survey_action_set_up
                    }
                )
            )
        }

        DestinationControl.SWITCH -> Row(verticalAlignment = Alignment.CenterVertically) {
            val switchDescription = if (row.lock == ItemLock.ORGANIZATION) {
                stringResource(R.string.survey_lock_announcement, name, onOffWord(row.on))
            } else {
                name
            }
            if (row.lock == ItemLock.ORGANIZATION) {
                Icon(
                    painter = painterResource(R.drawable.ic_domain),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SMALL_ICON),
                )
            }
            VerticalDivider(
                modifier = Modifier
                    .padding(horizontal = NsTheme.spacing.m)
                    .height(SWITCH_DIVIDER_HEIGHT),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Switch(
                checked = row.on,
                onCheckedChange = {
                    if (row.lock == ItemLock.ORGANIZATION) onLocked(row) else onToggle(row.destination)
                },
                modifier = Modifier.semantics { contentDescription = switchDescription },
            )
        }
    }
}

@Composable
private fun DetailText(row: DestinationRowUi, info: DestinationInfo) {
    val (text, color) = destinationDetail(row.destination, row.detail, info)
    if (text.isNotBlank()) Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

/** @return The second line of a destination row and its color, on the Survey screen and in More. */
@Composable
fun destinationDetail(destination: Destination, detail: DestinationDetail, info: DestinationInfo): Pair<String, Color> {
    val normal = MaterialTheme.colorScheme.onSurfaceVariant
    return when (detail) {
        DestinationDetail.Default -> defaultDetail(destination, info) to normal
        DestinationDetail.NotSetUp -> stringResource(R.string.survey_dest_not_set_up) to normal
        DestinationDetail.NotConnected -> stringResource(R.string.survey_dest_not_connected) to normal
        DestinationDetail.ChooseBroker -> stringResource(R.string.survey_dest_mqtt_choose) to normal
        DestinationDetail.TurnedOnByOrganization -> stringResource(R.string.survey_set_by_organization) to normal
        is DestinationDetail.CommunityRoutes -> communityRoutes(detail.openCelliD, detail.beaconDb) to normal
        DestinationDetail.ReceivesNothing -> stringResource(R.string.survey_dest_receives_nothing) to NsTheme.colors.status.warningText
    }
}

/** The destination's own description: workspace, broker, address or targets. */
@Composable
fun defaultDetail(destination: Destination, info: DestinationInfo): String = when (destination) {
    Destination.LOG_FILES -> stringResource(R.string.survey_dest_log_files_detail)

    Destination.NS_ANALYTICS -> info.workspaceName ?: stringResource(R.string.survey_dest_ns_analytics_connected)
    Destination.MQTT -> if (info.mqttFromOrganization) {
        stringResource(R.string.survey_dest_mqtt_organization)
    } else {
        info.mqttBrokerLabel.orEmpty()
    }

    Destination.GRPC -> info.grpcAddress.orEmpty()
    Destination.COMMUNITY -> communityRoutes(
        openCelliD = if (info.openCelliDSelected) DestinationCapabilities.OPENCELLID_TYPES else emptySet(),
        beaconDb = if (info.beaconDbSelected) DestinationCapabilities.BEACONDB_TYPES else emptySet(),
    )
}

/**
 * What goes to which community database, grouping record types that go to the same targets:
 * "Cellular to OpenCelliD and BeaconDB · Wi-Fi to BeaconDB".
 */
@Composable
fun communityRoutes(openCelliD: Set<RecordType>, beaconDb: Set<RecordType>): String {
    val openCelliDName = stringResource(R.string.survey_dest_community_opencellid)
    val beaconDbName = stringResource(R.string.survey_dest_community_beacondb)
    val routes = RecordType.entries
        .filter { it in openCelliD || it in beaconDb }
        .groupBy { (it in openCelliD) to (it in beaconDb) }
    val separator = stringResource(R.string.survey_dot_separator)
    return routes.map { (targets, types) ->
        val (toOpenCelliD, toBeaconDb) = targets
        val targetNames = when {
            toOpenCelliD && toBeaconDb -> stringResource(R.string.survey_dest_community_both)
            toOpenCelliD -> openCelliDName
            else -> beaconDbName
        }
        stringResource(R.string.survey_dest_route, recordTypeNames(types), targetNames)
    }.joinToString(separator)
}
