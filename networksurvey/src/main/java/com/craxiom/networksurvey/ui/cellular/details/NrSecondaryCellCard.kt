package com.craxiom.networksurvey.ui.cellular.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.band.BandTapTarget
import com.craxiom.networksurvey.fragments.model.NrSecondaryCellViewState
import com.craxiom.networksurvey.ui.cellular.details.model.CellField
import com.craxiom.networksurvey.ui.cellular.details.model.CellFieldKind
import com.craxiom.networksurvey.ui.cellular.details.model.NrSecondaryCellResolver
import com.craxiom.networksurvey.ui.cellular.details.model.ServingCellMapper
import com.craxiom.networksurvey.ui.cellular.details.model.ValueUnit
import com.craxiom.networksurvey.ui.common.NetworkTagVariant
import com.craxiom.networksurvey.ui.common.NsNetworkTag
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * Opacity of the idle card's values: dim enough to read as not live next to the Idle tag, still
 * legible in both themes.
 */
private const val IDLE_BODY_ALPHA = 0.65f

/**
 * The 5G NR cell the phone uses alongside its primary cell (the NSA data leg, or an NR carrier
 * under SA). While idle it shows the last-seen cell dimmed, with an Idle tag and its age at full
 * opacity, so held values never look live.
 */
@Composable
fun NrSecondaryCellCard(
    state: NrSecondaryCellViewState,
    lastSeenAgeMs: Long?,
    onInfo: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    onBandClick: (BandTapTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    NsPanel(modifier) {
        CellularPanelHeader(
            title = stringResource(R.string.card_title_nr_details),
            trailing = null,
            infoDescription = stringResource(R.string.nr_secondary_info_description),
            onInfo = onInfo,
        )
        Text(
            text = stringResource(R.string.nr_secondary_cell_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.idle() && lastSeenAgeMs != null) IdleLine(lastSeenAgeMs)
        Column(
            modifier = Modifier.alpha(if (state.idle()) IDLE_BODY_ALPHA else 1f),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        ) {
            ServingSignalBars(signals(state), onCopy, compact = true)
            CellFieldGrid(identity(state), onCopy, onBandClick)
        }
    }
}

/** The Idle tag and "Last seen 42 s ago", read by TalkBack as one phrase. */
@Composable
private fun IdleLine(lastSeenAgeMs: Long) {
    val idle = stringResource(R.string.nr_idle_badge)
    val age = NrSecondaryCellResolver.lastSeenAge(lastSeenAgeMs)
    val amount = age.amount.toInt()
    val ageText = if (age.inMinutes) {
        pluralStringResource(R.plurals.survey_minutes_short, amount, amount)
    } else {
        stringResource(R.string.settings_seconds, amount)
    }
    val lastSeen = stringResource(R.string.nr_last_seen_format, ageText)
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$idle, $lastSeen" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
    ) {
        NsNetworkTag(idle, NetworkTagVariant.Standard)
        Text(
            text = lastSeen,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun signals(state: NrSecondaryCellViewState): List<CellField> = listOf(
    ServingCellMapper.signal(CellFieldKind.SS_RSRP, state.ssRsrp(), ValueUnit.DBM),
    ServingCellMapper.signal(CellFieldKind.SS_RSRQ, state.ssRsrq(), ValueUnit.DB),
    ServingCellMapper.signal(CellFieldKind.SS_SINR, state.ssSinr(), ValueUnit.DB),
)

private fun identity(state: NrSecondaryCellViewState): List<CellField> = listOf(
    CellField(CellFieldKind.PCI, state.pci().ifEmpty { null }),
    CellField(CellFieldKind.BAND, state.band().ifEmpty { null }, bandTarget = state.bandTapTarget()),
    CellField(CellFieldKind.NARFCN, state.narfcn().ifEmpty { null }),
    CellField(CellFieldKind.FREQUENCY, state.frequency().ifEmpty { null }, ValueUnit.MHZ),
)
