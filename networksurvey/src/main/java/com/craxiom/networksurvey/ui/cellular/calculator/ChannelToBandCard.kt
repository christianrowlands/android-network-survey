package com.craxiom.networksurvey.ui.cellular.calculator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.bands.BandFormatting
import com.craxiom.networksurvey.ui.cellular.model.ChannelLookup

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
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = lookup?.outOfRange == true,
        )
        Spacer(Modifier.height(8.dp))

        lookup?.frequencyMhz?.let {
            Text(
                text = stringResource(R.string.calculator_frequency) + ": " +
                        BandFormatting.mhz(it) + " " + stringResource(R.string.band_unit_mhz),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (lookup?.outOfRange == true) {
            Text(
                text = stringResource(R.string.calculator_channel_out_of_range, "%,d".format(maxChannel)),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp),
            )
        } else if (lookup != null) {
            if (lookup.hasBand) {
                Text(
                    text = stringResource(R.string.band_group_number) + ": " +
                            lookup.bandNumbers.joinToString(" / ") { "$bandPrefix$it" },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
                if (lookup.isAmbiguous) {
                    Text(
                        text = stringResource(R.string.calculator_ambiguous_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.calculator_no_band),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            TextButton(onClick = { onBrowseBands(value) }) {
                Text(stringResource(R.string.band_browse_all))
            }
        }
    }
}
