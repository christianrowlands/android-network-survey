package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R

/**
 * Card wrapper for the logging toggle controls. Wraps the existing
 * [LoggingControlContent] composable with a card header and consistent styling.
 */
@Composable
fun LoggingControlsCard(
    state: LoggingUiState,
    onCellularToggle: (Boolean) -> Unit,
    onPhoneStateToggle: (Boolean) -> Unit,
    onWifiToggle: (Boolean) -> Unit,
    onBluetoothToggle: (Boolean) -> Unit,
    onGnssToggle: (Boolean) -> Unit,
    onCdrToggle: (Boolean) -> Unit,
    onPhoneStateHelpClick: () -> Unit,
    onCdrHelpClick: () -> Unit,
    onFileHelpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DashboardCardDefaults.margin, vertical = 4.dp),
        shape = DashboardCardDefaults.shape,
        elevation = DashboardCardDefaults.elevation(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        DashboardCardHeader(
            icon = R.drawable.ic_action_bar_logging,
            title = stringResource(R.string.card_title_logging_details),
            onHelpClick = onFileHelpClick,
        )

        LoggingControlContent(
            cellularEnabled = state.cellularEnabled,
            phoneStateEnabled = state.phoneStateEnabled,
            phoneStateAutoStarted = state.phoneStateAutoStarted,
            wifiEnabled = state.wifiEnabled,
            bluetoothEnabled = state.bluetoothEnabled,
            gnssEnabled = state.gnssEnabled,
            cdrEnabled = state.cdrEnabled,
            onCellularToggle = onCellularToggle,
            onPhoneStateToggle = onPhoneStateToggle,
            onWifiToggle = onWifiToggle,
            onBluetoothToggle = onBluetoothToggle,
            onGnssToggle = onGnssToggle,
            onCdrToggle = onCdrToggle,
            onPhoneStateHelpClick = onPhoneStateHelpClick,
            onCdrHelpClick = onCdrHelpClick,
        )
    }
}
