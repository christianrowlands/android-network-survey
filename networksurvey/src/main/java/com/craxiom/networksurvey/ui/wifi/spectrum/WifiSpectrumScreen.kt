package com.craxiom.networksurvey.ui.wifi.spectrum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.ui.common.NsCardHeader
import com.craxiom.networksurvey.ui.common.NsDetailsCardFrame
import com.craxiom.networksurvey.ui.common.NsScanRateCard
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.common.wifiScanRateHelp
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.wifi.components.WifiBlockedNotice
import com.craxiom.networksurvey.ui.wifi.components.WifiScanFixes
import com.craxiom.networksurvey.ui.wifi.model.WifiScanState

/** What the user can do on the spectrum page; the route wires these up. */
interface WifiSpectrumActions : WifiScanFixes {
    fun onOpenSettings()
}

/**
 * The Wi-Fi spectrum page without its plumbing: when the phone cannot scan, a notice with the one
 * fix; then how often it scans, and one card per band with [chart] drawing that band.
 */
@Composable
fun WifiSpectrumScreen(
    scanState: WifiScanState,
    scanRateSeconds: Int,
    permissionAccess: PermissionAccess,
    actions: WifiSpectrumActions,
    modifier: Modifier = Modifier,
    chart: @Composable (SpectrumBand) -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        // Pinned above the cards: an item inserted before the first visible one would scroll in
        // out of view.
        if (scanState != WifiScanState.READY) {
            WifiBlockedNotice(scanState, permissionAccess, actions, Modifier.padding(horizontal = NsTheme.spacing.screenGutter))
        }
        SpectrumCards(scanRateSeconds, actions, chart)
    }
}

/** The scan interval card and one card per band, under the notice. */
@Composable
private fun SpectrumCards(scanRateSeconds: Int, actions: WifiSpectrumActions, chart: @Composable (SpectrumBand) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(NsTheme.spacing.panelPaddingVertical),
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.panelPaddingVertical),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "rate") {
            NsScanRateCard(seconds = scanRateSeconds, help = wifiScanRateHelp(), onOpenSettings = actions::onOpenSettings)
        }
        items(SpectrumBand.entries, key = { it.name }) { band ->
            NsDetailsCardFrame {
                NsCardHeader(text = stringResource(band.title))
                chart(band)
            }
        }
    }
}
