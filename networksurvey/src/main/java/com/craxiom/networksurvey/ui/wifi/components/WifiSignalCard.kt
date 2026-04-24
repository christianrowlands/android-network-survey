package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.ASignalChartViewModel
import com.craxiom.networksurvey.ui.SignalChart

/**
 * Signal history card. Reuses the existing [SignalChart] (Vico-based, 2-minute buffer) and
 * simply re-skins the enclosing card in the handoff's visual language.
 */
@Composable
fun WifiSignalCard(viewModel: ASignalChartViewModel, modifier: Modifier = Modifier) {
    DetailsCardFrame(modifier = modifier) {
        CardHeader(text = stringResource(R.string.wifi_details_signal_last_2_min))
        SignalChart(viewModel)
    }
}
