package com.craxiom.networksurvey.ui.cellular.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.CalculatorError
import com.craxiom.networksurvey.ui.cellular.CalculatorField
import com.craxiom.networksurvey.ui.cellular.CalculatorResults
import com.craxiom.networksurvey.ui.cellular.bands.BandFormatting
import com.craxiom.networksurvey.ui.cellular.model.ChannelLookup
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * Turns a channel number into the band or bands that contain it, plus the frequency.
 *
 * This is one card rather than one per technology because the question is the same either side:
 * only the label on the field and the shape of the answer differ. For NR the answer is a list,
 * because a quarter of the NARFCN space falls in more than one band, and the frequency leads
 * because it is always correct even where the band cannot be pinned down.
 *
 * @param onBrowseBands hands the typed value to the band browser, so the card funnels into the
 *   fuller reference rather than competing with it.
 */
@Composable
fun ChannelToBandCard(
    label: String,
    value: String,
    lookup: ChannelLookup?,
    maxChannel: Int,
    bandPrefix: String,
    onValueChange: (String) -> Unit,
    onBrowseBands: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.m)) {
        CalculatorField(label = label, value = value, isError = lookup?.outOfRange == true, onValueChange = onValueChange)

        if (lookup?.outOfRange == true) {
            CalculatorError(label, maxChannel.toLong())
        } else if (lookup != null) {
            val frequency = lookup.frequencyMhz?.let { stringResource(R.string.mhz_value_label, BandFormatting.mhz(it)) }.orEmpty()
            val bands = if (lookup.hasBand) lookup.bandNumbers.joinToString(" / ") { "$bandPrefix$it" } else ""
            CalculatorResults(
                stringResource(R.string.calculator_frequency) to frequency,
                stringResource(R.string.band_group_number) to bands,
            )
            if (lookup.hasBand && lookup.isAmbiguous) {
                Text(
                    text = stringResource(R.string.calculator_ambiguous_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!lookup.hasBand) {
                Text(
                    text = stringResource(R.string.calculator_no_band),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NsTextAction(stringResource(R.string.band_browse_all), { onBrowseBands(value) })
        }
    }
}
