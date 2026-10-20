package com.craxiom.networksurvey.ui.cellular.details

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.band.BandTapTarget
import com.craxiom.networksurvey.ui.cellular.details.model.BandDisplay
import com.craxiom.networksurvey.ui.cellular.details.model.CellField
import com.craxiom.networksurvey.ui.cellular.details.model.CellFieldKind
import com.craxiom.networksurvey.ui.cellular.details.model.ValueUnit
import com.craxiom.networksurvey.ui.common.NsValueCell
import com.craxiom.networksurvey.ui.common.NsValueGrid
import com.craxiom.networksurvey.ui.common.copyOnLongPress
import com.craxiom.networksurvey.ui.common.signalBucketWord
import com.craxiom.networksurvey.ui.common.signalTextColor

/**
 * The cellular values as the prototype lays them out: each value over its uppercase label,
 * centered, four to a row, dropping to three or two when the width or the font scale needs it.
 * Values wrap rather than clip; a Band shows its number with its name on a line under it. Long
 * press copies a value; a Band value opens the band reference ("BAND ›").
 */
@Composable
fun CellFieldGrid(
    fields: List<CellField>,
    onCopy: (label: String, value: String) -> Unit,
    onBandClick: (BandTapTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    NsValueGrid(fields, modifier) { field, cellModifier ->
        FieldCell(field, onCopy, onBandClick, cellModifier)
    }
}

@Composable
private fun FieldCell(
    field: CellField,
    onCopy: (String, String) -> Unit,
    onBandClick: (BandTapTarget) -> Unit,
    modifier: Modifier,
) {
    val label = stringResource(fieldLabel(field.kind))
    val text = fieldText(field)
    val band = field.bandTarget?.takeUnless { it.isEmpty }
    val bandDisplay = if (field.kind == CellFieldKind.BAND) BandDisplay.of(field) else null
    val copyLabel = stringResource(R.string.cellular_copy_action)
    val copy = if (field.value != null) ({ onCopy(label, text) }) else null
    val labelColor = if (band != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    NsValueCell(
        value = bandDisplay?.band ?: text,
        label = label,
        modifier = modifier.cellGestures(band, onBandClick, stringResource(R.string.cellular_band_open), copyLabel, text, copy),
        valueColor = if (field.bucket != null && field.value != null) signalTextColor(field.bucket) else MaterialTheme.colorScheme.onSurface,
        spoken = fieldSpoken(label, field),
        detail = bandDisplay?.name,
        labelColor = labelColor,
        labelTrailing = if (band == null) null else {
            {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right_small),
                    contentDescription = null,
                    tint = labelColor,
                    // The label's line height, so the chevron grows with the label's text.
                    modifier = Modifier.size(with(LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }),
                )
            }
        },
    )
}

/**
 * A Band value opens the band reference on a tap; every reported value copies on a long press.
 * Values that do nothing on a tap get no click action, so TalkBack does not offer one.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.cellGestures(
    band: BandTapTarget?,
    onBandClick: (BandTapTarget) -> Unit,
    bandLabel: String,
    copyLabel: String,
    copyValue: String,
    copy: (() -> Unit)?,
): Modifier = when {
    band != null -> combinedClickable(
        onClickLabel = bandLabel,
        onLongClickLabel = copyLabel,
        onLongClick = copy,
        onClick = { onBandClick(band) },
    )

    copy != null -> copyOnLongPress(copyLabel, copyValue, copy)

    else -> this
}

/** @return The value with its unit ("-79 dBm"), or "--" when the phone did not report it. */
@Composable
fun fieldText(field: CellField): String {
    val value = field.value ?: return stringResource(R.string.signal_none)
    return when (field.unit) {
        ValueUnit.NONE -> value
        ValueUnit.DBM -> stringResource(R.string.dbm_value_label, value)
        ValueUnit.DB -> stringResource(R.string.db_value_label, value)
        ValueUnit.MHZ -> stringResource(R.string.mhz_value_label, value)
    }
}

/** @return What TalkBack reads for a value: "RSRP, -79 dBm, strong". */
@Composable
internal fun fieldSpoken(label: String, field: CellField): String {
    val parts = mutableListOf(label, fieldText(field))
    if (field.value != null) signalBucketWord(field.bucket)?.let { parts += stringResource(it) }
    return parts.joinToString(", ")
}

/** @return The label of a value, the same terms the old details screen used. */
@StringRes
fun fieldLabel(kind: CellFieldKind): Int = when (kind) {
    CellFieldKind.RSSI -> R.string.rssi_label
    CellFieldKind.RSCP -> R.string.rscp_label
    CellFieldKind.RSRP -> R.string.rsrp_label
    CellFieldKind.RSRQ -> R.string.rsrq_label
    CellFieldKind.SNR -> R.string.snr_label
    CellFieldKind.CQI -> R.string.cqi_label
    CellFieldKind.SS_RSRP -> R.string.ss_rsrp_label
    CellFieldKind.SS_RSRQ -> R.string.ss_rsrq_label
    CellFieldKind.SS_SINR -> R.string.ss_sinr_label
    CellFieldKind.PLMN -> R.string.mcc_mnc_label
    CellFieldKind.TAC -> R.string.tac_label
    CellFieldKind.LAC -> R.string.lac_label
    CellFieldKind.CID -> R.string.cid_label
    CellFieldKind.ENB -> R.string.enb_id_label
    CellFieldKind.SECTOR -> R.string.sector_id_label
    CellFieldKind.RNC -> R.string.rnc_label
    CellFieldKind.SHORT_CID -> R.string.short_cid_label
    CellFieldKind.ARFCN -> R.string.arfcn_label
    CellFieldKind.UARFCN -> R.string.uarfcn_label
    CellFieldKind.EARFCN -> R.string.earfcn_label
    CellFieldKind.NARFCN -> R.string.narfcn_label
    CellFieldKind.BSIC -> R.string.bsic_label
    CellFieldKind.PSC -> R.string.psc_label
    CellFieldKind.PCI -> R.string.pci_label
    CellFieldKind.BAND -> R.string.band_label
    CellFieldKind.FREQUENCY -> R.string.frequency_label
    CellFieldKind.BANDWIDTH -> R.string.bandwidth_label
    CellFieldKind.TA -> R.string.ta_label
}
