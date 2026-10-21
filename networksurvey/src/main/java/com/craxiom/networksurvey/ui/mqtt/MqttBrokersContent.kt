package com.craxiom.networksurvey.ui.mqtt

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.status.DestinationStatus
import com.craxiom.networksurvey.ui.common.NsEmptyState
import com.craxiom.networksurvey.ui.common.NsIconAction
import com.craxiom.networksurvey.ui.common.NsManagedLockSheet
import com.craxiom.networksurvey.ui.common.NsPageScaffold
import com.craxiom.networksurvey.ui.common.NsRadioRow
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.common.NsRowDivider
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.NsSummaryText
import com.craxiom.networksurvey.ui.common.NsSwitchRow
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.common.StatusLabel
import com.craxiom.networksurvey.ui.common.dialogs.NsInputDialog
import com.craxiom.networksurvey.ui.survey.DestinationPageStatus
import com.craxiom.networksurvey.ui.survey.components.DestinationStatusPanel
import com.craxiom.networksurvey.ui.survey.components.destinationStatusLabel
import com.craxiom.networksurvey.ui.survey.sheets.UseOwnSettingsDialog
import com.craxiom.networksurvey.ui.theme.NsTheme

/** Everything the MQTT page can ask for. The stateful screen wires these up. */
interface MqttBrokersActions {
    fun onBack() {}
    fun onSelect(id: String) {}
    fun onEdit(id: String) {}
    fun onAdd() {}
    fun onScan() {}
    fun onSetClientId(clientId: String) {}
    fun onSetDeviceStatus(enabled: Boolean) {}
    fun onSeeOrganizationSettings() {}
    fun onUseOwnSettings() {}
}

/**
 * The MQTT page, stateless: the brokers with the selected one marked, how to add another, this
 * phone's own MQTT settings, and what is sent.
 *
 * @param cameraNotice Shown under "Scan a QR code" after the camera was refused.
 */
@Composable
fun MqttBrokersContent(
    state: MqttBrokersUiState,
    actions: MqttBrokersActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    status: DestinationPageStatus? = null,
    cameraNotice: @Composable () -> Unit = {},
) {
    var showLockSheet by rememberSaveable { mutableStateOf(false) }
    var confirmOwnSettings by rememberSaveable { mutableStateOf(false) }
    var editingClientId by rememberSaveable { mutableStateOf(false) }

    NsPageScaffold(
        title = stringResource(R.string.survey_dest_mqtt),
        onBack = actions::onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        // While a survey streams here: the same status and detail as the Survey screen's row
        status?.let { DestinationStatusPanel(it) }
        SectionHeader(stringResource(R.string.mqtt_brokers_group))
        when {
            state.loading -> Unit
            state.organizationBroker != null -> OrganizationBroker(state, status?.status, onLocked = { showLockSheet = true })
            state.brokers.isEmpty() -> NsRowGroup { NsEmptyState(stringResource(R.string.mqtt_brokers_empty), R.drawable.ic_cloud_connection) }
            else -> BrokerList(state, status?.status, actions)
        }

        if (!state.organizationInForce && !state.loading) {
            if (state.noneSelected) {
                NsSummaryText(stringResource(R.string.mqtt_brokers_none_selected), color = NsTheme.colors.status.warningText)
            } else if (state.brokers.isNotEmpty()) {
                NsSummaryText(stringResource(R.string.mqtt_brokers_selected_note))
            }
            NsRowGroup {
                NsRow(title = stringResource(R.string.mqtt_broker_add), icon = R.drawable.ic_add, onClick = actions::onAdd)
                NsRowDivider()
                NsRow(
                    title = stringResource(R.string.mqtt_broker_scan),
                    subtitle = stringResource(R.string.mqtt_broker_scan_detail),
                    icon = R.drawable.ic_qr_code_scanner,
                    onClick = actions::onScan,
                )
            }
            cameraNotice()

            SectionHeader(stringResource(R.string.mqtt_this_phone))
            NsRowGroup {
                NsRow(
                    title = stringResource(R.string.mqtt_client_id),
                    subtitle = state.clientId.ifBlank { stringResource(R.string.mqtt_client_id_detail) },
                    onClick = { editingClientId = true },
                )
                NsRowDivider()
                NsSwitchRow(
                    title = stringResource(R.string.mqtt_device_status),
                    subtitle = stringResource(R.string.mqtt_device_status_detail),
                    checked = state.deviceStatusEnabled,
                    onCheckedChange = actions::onSetDeviceStatus,
                )
            }
        }

        SectionHeader(stringResource(R.string.destination_what_is_sent_title))
        NsSummaryText(stringResource(R.string.destination_what_is_sent))
    }

    if (showLockSheet) {
        NsManagedLockSheet(
            onSeeAll = actions::onSeeOrganizationSettings,
            onUseOwnSettings = {
                showLockSheet = false
                confirmOwnSettings = true
            },
            onDismiss = { showLockSheet = false },
        )
    }
    if (confirmOwnSettings) {
        UseOwnSettingsDialog(onConfirm = actions::onUseOwnSettings, onDismiss = { confirmOwnSettings = false })
    }
    if (editingClientId) {
        NsInputDialog(
            title = stringResource(R.string.mqtt_client_id),
            initialValue = state.clientId,
            label = stringResource(R.string.mqtt_client_id_detail),
            onConfirm = {
                editingClientId = false
                actions.onSetClientId(it)
            },
            onDismiss = { editingClientId = false },
        )
    }
}

@Composable
private fun BrokerList(state: MqttBrokersUiState, live: DestinationStatus?, actions: MqttBrokersActions) {
    NsRowGroup(selectable = true) {
        state.brokers.forEachIndexed { index, broker ->
            if (index > 0) NsRowDivider()
            val selected = broker.id == state.activeId
            NsRadioRow(
                title = broker.name,
                subtitle = brokerDetail(broker.host, broker.port, broker.tlsEnabled, broker.username),
                selected = selected,
                onSelect = { actions.onSelect(broker.id) },
            ) {
                if (selected && live != null) {
                    val (text, color) = destinationStatusLabel(live)
                    StatusLabel(text, color)
                }
                NsIconAction(R.drawable.ic_edit, stringResource(R.string.mqtt_broker_edit, broker.name), onClick = { actions.onEdit(broker.id) })
            }
        }
    }
}

@Composable
private fun OrganizationBroker(state: MqttBrokersUiState, live: DestinationStatus?, onLocked: () -> Unit) {
    val broker = state.organizationBroker ?: return
    val announcement = stringResource(R.string.mqtt_broker_organization_announcement, broker.host)
    Column {
        NsRowGroup {
            NsRow(
                title = stringResource(R.string.survey_dest_mqtt_organization),
                subtitle = brokerDetail(broker.host, broker.port, broker.tlsEnabled, username = ""),
                icon = R.drawable.ic_domain,
                locked = true,
                onClickLabel = stringResource(R.string.survey_lock_learn_why),
                onClick = onLocked,
                modifier = Modifier.semantics { contentDescription = announcement },
            ) {
                if (live != null) {
                    val (text, color) = destinationStatusLabel(live)
                    StatusLabel(text, color)
                }
            }
        }
    }
    NsSummaryText(stringResource(R.string.mqtt_broker_organization_note))
    if (state.hasOwnBrokers) NsSummaryText(stringResource(R.string.mqtt_broker_own_kept))
}

/** "host:port · TLS · user", the line under a broker's name. */
@Composable
private fun brokerDetail(host: String, port: Int, tlsEnabled: Boolean, username: String): String =
    listOfNotNull(
        "$host:$port",
        stringResource(R.string.mqtt_broker_tls).takeIf { tlsEnabled },
        username.takeIf { it.isNotBlank() },
    ).joinToString(DETAIL_SEPARATOR)

private const val DETAIL_SEPARATOR = " · "
