package com.craxiom.networksurvey.ui.cellular.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.band.BandTapTarget
import com.craxiom.networksurvey.ui.cellular.details.model.CellularDetailsUiState
import com.craxiom.networksurvey.ui.cellular.details.model.CellularScanState
import com.craxiom.networksurvey.ui.cellular.details.model.CellularSimView
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsSegmentedToggle
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.common.SegmentedOption
import com.craxiom.networksurvey.ui.common.SegmentedStyle
import com.craxiom.networksurvey.ui.signals.SignalsStateMessage
import com.craxiom.networksurvey.ui.signals.SimLabels
import com.craxiom.networksurvey.ui.theme.NsTheme

/** The explanations the Cellular view's info buttons open. */
enum class CellularInfo { TECHNOLOGY, TERMS, NR_SECONDARY }

/** What the user can do on the Cellular view; the stateful screen wires these up. */
interface CellularDetailsActions {
    fun onSelectPage(index: Int)
    fun onAllow()
    fun onOpenAppSettings()
    fun onTurnOnLocation()
    fun onOpenAirplaneSettings()
    fun onInfo(info: CellularInfo)
    fun onBandClick(target: BandTapTarget)
    fun onCopy(label: String, value: String)
}

/**
 * The Cellular sub-tab of Signals, without its state: the SIM tabs, then what is in the way or the
 * cards (technology, serving cell, 5G NR secondary cell, neighbors). One plain scrolling column,
 * since the content is short and bounded; a reader at the bottom stays there as neighbors arrive.
 *
 * @param chart The serving cell's signal chart for a subscription id; tests pass nothing.
 */
@Composable
fun CellularDetailsContent(
    state: CellularDetailsUiState,
    permissionAccess: PermissionAccess,
    actions: CellularDetailsActions,
    modifier: Modifier = Modifier,
    chart: @Composable (subscriptionId: Int) -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        if (state.pages.size > 1) SimTabs(state, actions)
        when (state.scanState) {
            CellularScanState.PERMISSION_NEEDED -> {
                val blocked = permissionAccess == PermissionAccess.BLOCKED
                SignalsStateMessage(
                    icon = R.drawable.ic_info,
                    text = stringResource(R.string.cellular_state_permission_body),
                    action = stringResource(if (blocked) R.string.open_settings else R.string.survey_location_allow),
                    onAction = if (blocked) actions::onOpenAppSettings else actions::onAllow,
                )
            }

            CellularScanState.LOCATION_OFF -> SignalsStateMessage(
                icon = R.drawable.ic_location_off,
                text = stringResource(R.string.cellular_state_location_off_body),
                action = stringResource(R.string.survey_location_turn_on),
                onAction = actions::onTurnOnLocation,
            )

            CellularScanState.AIRPLANE_MODE -> SignalsStateMessage(
                icon = R.drawable.ic_airplanemode_active_24,
                text = stringResource(R.string.cellular_state_airplane_body),
                action = stringResource(R.string.open_settings),
                onAction = actions::onOpenAirplaneSettings,
            )

            CellularScanState.READY -> if (state.sim.scanned) {
                val subscriptionId = state.pages.getOrNull(state.selectedPage)?.recordSubscriptionId
                Cards(state.sim, actions) { if (subscriptionId != null) chart(subscriptionId) }
            } else {
                SignalsStateMessage(icon = R.drawable.ic_cell_tower, text = stringResource(R.string.signals_cellular_waiting))
            }
        }
    }
}

@Composable
private fun SimTabs(state: CellularDetailsUiState, actions: CellularDetailsActions) {
    val resources = LocalResources.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        NsSegmentedToggle(
            options = state.pages.map { SegmentedOption(label = SimLabels.label(resources, it.sim)) },
            selectedIndex = state.selectedPage,
            onSelected = actions::onSelectPage,
            style = SegmentedStyle.TABS,
            modifier = Modifier
                .widthIn(max = NsTheme.spacing.contentMaxWidth)
                .padding(horizontal = NsTheme.spacing.screenGutter),
        )
    }
}

@Composable
private fun Cards(sim: CellularSimView, actions: CellularDetailsActions, chart: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    // Keep a reader who scrolled to the bottom there as the neighbor list grows.
    LaunchedEffect(scroll) {
        var lastMax = scroll.maxValue
        snapshotFlow { scroll.maxValue }.collect { max ->
            if (max > lastMax && lastMax > 0 && scroll.value >= lastMax) scroll.scrollTo(max)
            lastMax = max
        }
    }
    Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = NsTheme.spacing.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = NsTheme.spacing.screenGutter, vertical = NsTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.panelGap),
        ) {
            CellularHeroPanel(
                carrier = sim.carrier,
                hero = sim.hero,
                pills = sim.pills,
                onInfo = { actions.onInfo(CellularInfo.TECHNOLOGY) },
                onCopy = actions::onCopy,
            )
            val serving = sim.serving
            if (serving != null) {
                CellularServingCard(
                    serving = serving,
                    carrierAggregation = sim.carrierAggregation,
                    onInfo = { actions.onInfo(CellularInfo.TERMS) },
                    onCopy = actions::onCopy,
                    onBandClick = actions::onBandClick,
                    chart = chart,
                )
            } else {
                NsPanel {
                    Text(
                        text = stringResource(R.string.cellular_no_serving_cell),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            sim.nrCard?.let {
                NrSecondaryCellCard(
                    state = it,
                    lastSeenAgeMs = sim.nrLastSeenAgeMs,
                    onInfo = { actions.onInfo(CellularInfo.NR_SECONDARY) },
                    onCopy = actions::onCopy,
                    onBandClick = actions::onBandClick,
                )
            }
            sim.neighbors.forEach { CellularNeighborList(it) }
        }
    }
}
