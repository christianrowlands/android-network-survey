package com.craxiom.networksurvey.ui.bluetooth.list

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothDeviceDisplay
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothTech
import com.craxiom.networksurvey.ui.bluetooth.list.model.FindMyDevice
import com.craxiom.networksurvey.ui.common.NetworkTagVariant
import com.craxiom.networksurvey.ui.common.NsNetworkTag
import com.craxiom.networksurvey.ui.common.NsRssiColumn
import com.craxiom.networksurvey.ui.theme.NsTheme

private const val SEPARATOR = " · "

/**
 * One device in the Bluetooth list, as the prototype draws it: the name (or a muted "No name") and
 * a tracker tag ("AirTag" or "Find My"), then the address · company · technology, with the signal on the right.
 *
 * The whole row is one TalkBack item that reads the name, company, technology and signal before
 * the address (read character by character, so it goes last). A tap opens the details; a long
 * press copies the address.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BluetoothDeviceRow(
    device: BluetoothDeviceDisplay,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spoken = spokenDescription(device)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClickLabel = stringResource(R.string.bluetooth_row_open_details),
                onLongClickLabel = stringResource(R.string.bluetooth_row_copy_address),
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics { contentDescription = spoken }
            .heightIn(min = NsTheme.spacing.rowMinHeight)
            .padding(horizontal = NsTheme.spacing.screenGutter, vertical = NsTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics {},
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
                Text(
                    text = device.name.ifBlank { stringResource(R.string.bluetooth_no_name) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (device.hasName) FontWeight.Medium else FontWeight.Normal,
                    fontStyle = if (device.hasName) FontStyle.Normal else FontStyle.Italic,
                    color = if (device.hasName) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    // Two lines, so a long name still reads whole inside the list panel at large text
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                device.tracker?.let {
                    NsNetworkTag(text = stringResource(trackerTagLabel(it)), variant = NetworkTagVariant.Tracker)
                }
            }
            DetailLine(device)
        }
        NsRssiColumn(rssi = device.rssi, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** "02:00:F4:12:00:9C · Apple, Inc. · LE", the address in monospace. */
@Composable
private fun DetailLine(device: BluetoothDeviceDisplay) {
    val tech = techLabel(device.tech)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = NsTheme.textStyles.mono.fontFamily)) { append(device.address) }
        if (device.companyName.isNotBlank()) append(SEPARATOR + device.companyName)
        if (tech != null) append(SEPARATOR + tech)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** @return "LE", "BR/EDR" or "Dual mode", or null when the records did not say. */
@Composable
internal fun techLabel(tech: BluetoothTech): String? = when (tech) {
    BluetoothTech.LE -> stringResource(R.string.bluetooth_tech_le)
    BluetoothTech.CLASSIC -> stringResource(R.string.bluetooth_tech_classic)
    BluetoothTech.DUAL -> stringResource(R.string.bluetooth_tech_dual)
    BluetoothTech.UNKNOWN -> null
}

/** @return The list tag for a Find My tracker: "AirTag", or "Find My" when the kind cannot be told. */
@StringRes
private fun trackerTagLabel(tracker: FindMyDevice): Int =
    if (tracker == FindMyDevice.AIRTAG) R.string.airtag else R.string.bluetooth_tag_find_my

/** @return What TalkBack reads for the row, the address last. */
@Composable
private fun spokenDescription(device: BluetoothDeviceDisplay): String {
    val resources = LocalResources.current
    val signal = device.rssi?.let { resources.getString(R.string.dbm_value, it.toString()) }
        ?: resources.getString(R.string.wifi_no_signal_description)
    return listOfNotNull(
        device.name.ifBlank { resources.getString(R.string.bluetooth_no_name) },
        device.tracker?.let { resources.getString(trackerTagLabel(it)) },
        device.companyName.takeIf { it.isNotBlank() },
        techLabel(device.tech),
        signal,
        device.address,
    ).joinToString(", ")
}
