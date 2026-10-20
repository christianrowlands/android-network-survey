package com.craxiom.networksurvey.ui.bluetooth.details

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.messaging.bluetooth.AddressType
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.ASignalChartViewModel
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothScanState
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothTech
import com.craxiom.networksurvey.ui.bluetooth.list.model.FindMy
import com.craxiom.networksurvey.ui.bluetooth.list.model.FindMyDevice
import com.craxiom.networksurvey.ui.bluetooth.list.techLabel
import com.craxiom.networksurvey.ui.common.NsCardHeader
import com.craxiom.networksurvey.ui.common.NsDetailsCardFrame
import com.craxiom.networksurvey.ui.common.NsScanRateCard
import com.craxiom.networksurvey.ui.common.NsSignalHistoryCard
import com.craxiom.networksurvey.ui.common.NsSignalMeterWide
import com.craxiom.networksurvey.ui.common.NsValueCell
import com.craxiom.networksurvey.ui.common.NsValueGrid
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.common.bluetoothScanRateHelp
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.main.appbar.TitleBar
import com.craxiom.networksurvey.ui.manufacturer.BluetoothManufacturerCard
import com.craxiom.networksurvey.ui.signals.SignalsBlockedNotice
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.textColor
import com.craxiom.networksurvey.util.toWifiSignalCategory

/** What the user can do on the Bluetooth details page; the route wires these up. */
interface BluetoothDetailsActions {
    fun onBack()
    fun onCopyAddress()
    fun onOpenSettings()
    fun onAllow()
    fun onOpenAppSettings()
    fun onTurnOnLocation()
    fun onTurnOnBluetooth()
}

/**
 * The Bluetooth details page: when the phone cannot scan, a notice with the one fix; then the
 * device (name, address, signal), its fields, its manufacturer, the signal history and how often
 * Bluetooth scans.
 */
@Composable
fun BluetoothDetailsScreen(
    device: BluetoothRecordData,
    currentRssi: Int?,
    scanRateSeconds: Int,
    scanState: BluetoothScanState,
    permissionAccess: PermissionAccess,
    chart: ASignalChartViewModel?,
    actions: BluetoothDetailsActions,
) {
    Scaffold(
        topBar = { TitleBar(stringResource(R.string.bluetooth_details_title), onBackClick = actions::onBack) },
    ) { insetPadding ->
        Column(Modifier.padding(insetPadding)) {
            // Pinned above the cards: an item inserted before the first visible one would scroll
            // in out of view.
            if (scanState != BluetoothScanState.READY) {
                Box(Modifier.padding(horizontal = NsTheme.spacing.screenGutter)) { BlockedNotice(scanState, permissionAccess, actions) }
            }
            DeviceCards(device, currentRssi, scanRateSeconds, chart, actions)
        }
    }
}

/** The scrolling cards under the notice. */
@Composable
private fun DeviceCards(
    device: BluetoothRecordData,
    currentRssi: Int?,
    scanRateSeconds: Int,
    chart: ASignalChartViewModel?,
    actions: BluetoothDetailsActions,
) {
    LazyColumn(
        contentPadding = PaddingValues(NsTheme.spacing.panelPaddingVertical),
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.panelPaddingVertical),
        modifier = Modifier.fillMaxSize().nsContentWidth(),
    ) {
        item(key = "hero") { HeroCard(device, currentRssi, actions::onCopyAddress) }
        item(key = "fields") { FieldsCard(device) }
        item(key = "manufacturer") {
            BluetoothManufacturerCard(mac = device.sourceAddress, serviceUuids = device.serviceUuidsList, companyId = device.companyId)
        }
        item(key = "signal") { NsSignalHistoryCard(title = stringResource(R.string.bluetooth_details_signal), chart = chart) }
        item(key = "rate") {
            NsScanRateCard(seconds = scanRateSeconds, help = bluetoothScanRateHelp(), onOpenSettings = actions::onOpenSettings)
        }
    }
}

/** The name (or "No name"), the address (long press copies it) and the signal with its meter. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCard(device: BluetoothRecordData, currentRssi: Int?, onCopyAddress: () -> Unit) {
    val named = device.otaDeviceName.isNotBlank()
    val signal = currentRssi?.let { stringResource(R.string.dbm_value, it.toString()) } ?: stringResource(R.string.signal_none)
    NsDetailsCardFrame {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m)) {
            Text(
                text = if (named) device.otaDeviceName else stringResource(R.string.bluetooth_no_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (named) FontWeight.SemiBold else FontWeight.Normal,
                fontStyle = if (named) FontStyle.Normal else FontStyle.Italic,
                color = if (named) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = signal,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = currentRssi?.toWifiSignalCategory()?.textColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = device.sourceAddress,
            style = NsTheme.textStyles.mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.combinedClickable(
                onClickLabel = null,
                onLongClickLabel = stringResource(R.string.bluetooth_row_copy_address),
                onLongClick = onCopyAddress,
                onClick = {},
            ),
        )
        NsSignalMeterWide(rssi = currentRssi, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Technology, address type, class, company ID, what sent a Find My advertisement and the Tx power
 * in the value grid the Wi-Fi Radio card and Cellular use, then the service UUIDs and the
 * manufacturer data (too long for a grid cell) as full-width values under their labels.
 */
@Composable
private fun FieldsCard(device: BluetoothRecordData) {
    val fields = listOfNotNull(
        techLabel(BluetoothTech.from(device.supportedTechnologies))?.let { R.string.bluetooth_details_technology to it },
        device.addressType.takeIf { it != AddressType.UNKNOWN && it != AddressType.UNRECOGNIZED }
            ?.let { R.string.bluetooth_details_address_type to formatAddressType(it) },
        device.deviceClass.ifBlank { null }?.let { R.string.bluetooth_details_device_class to it },
        device.companyId.ifBlank { null }?.let { R.string.bluetooth_details_company_id to it },
        FindMy.device(device.companyId, device.mfgData)
            ?.let { R.string.bluetooth_details_find_my to stringResource(findMyLabel(it)) },
        device.takeIf { it.hasTxPower() }
            ?.let { R.string.bluetooth_details_tx_power to stringResource(R.string.dbm_value, it.txPower.value.toInt().toString()) },
    )
    val uuids = device.serviceUuidsList.ifEmpty { null }?.joinToString("\n")
    val manufacturerData = device.mfgData.ifBlank { null }?.let(::formatHexBytes)
    NsDetailsCardFrame {
        NsCardHeader(text = stringResource(R.string.bluetooth_details_device))
        if (fields.isNotEmpty()) {
            NsValueGrid(fields, cellMinWidth = FIELD_CELL_MIN_WIDTH, maxColumns = 2) { (label, value), cellModifier ->
                NsValueCell(value = value, label = stringResource(label), modifier = cellModifier)
            }
        }
        if (uuids != null) Field(stringResource(R.string.bluetooth_details_service_uuids), uuids)
        if (manufacturerData != null) {
            Field(
                stringResource(R.string.bluetooth_details_manufacturer_data),
                manufacturerData,
                valueStyle = NsTheme.textStyles.mono,
                selectable = true,
            )
        }
    }
}

/**
 * A value under its label, read by TalkBack as "label, value".
 *
 * @param selectable True to let a long press select the value for copying (raw data to paste elsewhere).
 */
@Composable
private fun Field(
    label: String,
    value: String,
    valueStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    selectable: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val valueText = @Composable {
            Text(
                text = value,
                style = valueStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (selectable) SelectionContainer { valueText() } else valueText()
    }
}

/** What stops new scans, with the one button that fixes it; nothing on a phone without Bluetooth. */
@Composable
private fun BlockedNotice(scanState: BluetoothScanState, permissionAccess: PermissionAccess, actions: BluetoothDetailsActions) {
    when (scanState) {
        BluetoothScanState.PERMISSION_NEEDED -> {
            val blocked = permissionAccess == PermissionAccess.BLOCKED
            SignalsBlockedNotice(
                icon = R.drawable.ic_info,
                text = stringResource(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.string.bluetooth_blocked_permission_notice else R.string.bluetooth_blocked_permission_notice_location
                ),
                action = stringResource(if (blocked) R.string.open_settings else R.string.survey_location_allow),
                onAction = if (blocked) actions::onOpenAppSettings else actions::onAllow,
            )
        }

        BluetoothScanState.LOCATION_OFF -> SignalsBlockedNotice(
            icon = R.drawable.ic_location_off,
            text = stringResource(R.string.signals_blocked_location_notice),
            action = stringResource(R.string.survey_location_turn_on),
            onAction = actions::onTurnOnLocation,
        )

        BluetoothScanState.BLUETOOTH_OFF -> SignalsBlockedNotice(
            icon = R.drawable.ic_bluetooth,
            text = stringResource(R.string.bluetooth_blocked_off_notice),
            action = stringResource(R.string.survey_location_turn_on),
            onAction = actions::onTurnOnBluetooth,
        )

        BluetoothScanState.NOT_SUPPORTED, BluetoothScanState.READY -> Unit
    }
}

/** The name of what sent a Find My advertisement. */
private fun findMyLabel(device: FindMyDevice): Int = when (device) {
    FindMyDevice.APPLE_DEVICE -> R.string.bluetooth_find_my_apple_device
    FindMyDevice.AIRTAG -> R.string.airtag
    FindMyDevice.FIND_MY_ACCESSORY -> R.string.bluetooth_find_my_accessory
    FindMyDevice.AIRPODS -> R.string.bluetooth_find_my_airpods
}

/** "4C 00 12 19 ..." from "4c001219...": spaced bytes wrap between bytes and are easier to read. */
private fun formatHexBytes(hex: String): String = hex.uppercase().chunked(2).joinToString(" ")

/** "Public", "Random" and the like, from the record's address type. */
private fun formatAddressType(addressType: AddressType): String =
    addressType.name.lowercase().replaceFirstChar { it.uppercase() }

/** Wide enough for "Classic + BLE" and a device class at font scale 1. */
private val FIELD_CELL_MIN_WIDTH = 140.dp
