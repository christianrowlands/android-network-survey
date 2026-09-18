package com.craxiom.networksurvey.ui.cellular

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.bands.BandBrowserScreen
import com.craxiom.networksurvey.ui.cellular.bands.BandHelpSheet
import com.craxiom.networksurvey.ui.cellular.model.BandBrowserViewModel
import com.craxiom.networksurvey.ui.cellular.model.CalculatorViewModel
import com.craxiom.networksurvey.ui.common.NsSegmentedToggle
import com.craxiom.networksurvey.ui.common.SegmentedOption

/** Which pane of the Cellular Tools destination is showing. */
private const val PANE_BANDS = 0
private const val PANE_CALCULATORS = 1

/**
 * Host for the two cellular reference tools: a browsable band reference and the calculators.
 *
 * They share one destination because the nav drawer is full and because they answer the same kind
 * of question offline. Bands is the default pane, since it is the one a user is most likely to be
 * looking for and it describes itself on sight, which the destination's own name no longer does.
 *
 * The panes are peers rather than a master and a detail, so there is no nested NavHost: system
 * back leaves the destination from either one. The selected pane is remembered across
 * configuration changes.
 */
@Composable
fun CellularToolsScreen(
    bandViewModel: BandBrowserViewModel = viewModel(),
    calculatorViewModel: CalculatorViewModel = viewModel(),
    showHelp: Boolean = false,
    onHelpDismissed: () -> Unit = {},
) {
    var pane by rememberSaveable { mutableIntStateOf(PANE_BANDS) }

    Column(modifier = Modifier.fillMaxSize()) {
        NsSegmentedToggle(
            options = listOf(
                SegmentedOption(stringResource(R.string.cellular_tools_pane_bands)),
                SegmentedOption(stringResource(R.string.cellular_tools_pane_calculators)),
            ),
            selectedIndex = pane,
            onSelected = { pane = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )

        if (pane == PANE_BANDS) {
            BandBrowserScreen(
                viewModel = bandViewModel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            CalculatorScreen(
                viewModel = calculatorViewModel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onBrowseBands = { query ->
                    bandViewModel.setQuery(query)
                    pane = PANE_BANDS
                },
            )
        }
    }

    // Hosted here rather than inside the band browser so the app bar's help action works from
    // either pane. It was previously scoped to the browser, which left the icon visible but inert
    // on the Calculators pane.
    if (showHelp) BandHelpSheet(onDismiss = onHelpDismissed)
}
