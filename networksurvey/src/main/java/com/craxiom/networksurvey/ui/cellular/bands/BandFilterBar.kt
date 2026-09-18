package com.craxiom.networksurvey.ui.cellular.bands

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.band.BandReading
import com.craxiom.networksurvey.data.band.BandTechnology
import com.craxiom.networksurvey.ui.cellular.model.BandSortKey

/**
 * The search field above the band list.
 *
 * The query lives in local state so typing is synchronous; routing every keystroke through the
 * view model's StateFlow makes the field feel laggy. When a reading is pinned the keyboard
 * switches to numeric, because the free-text readings are the only ones that accept letters.
 */
@Composable
fun BandSearchField(
    value: String,
    reading: BandReading?,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it; onQueryChanged(it) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(999.dp),
        placeholder = { Text(stringResource(R.string.band_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = when (reading) {
                // Auto accepts names and suffixes, so it needs letters. A pinned frequency needs
                // a decimal point, since band edges like 2483.5 MHz are real.
                null -> KeyboardType.Text
                BandReading.FREQUENCY -> KeyboardType.Decimal
                else -> KeyboardType.Number
            }
        ),
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = ""; onQueryChanged("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.band_search_clear),
                    )
                }
            }
        },
    )
}

/**
 * Technology chips, the deployed-only filter, the reading selector and the sort menu.
 *
 * The reading selector is what keeps the search honest without splitting the screen: leaving it on
 * Auto shows every valid interpretation of a number, and pinning it makes the field behave like a
 * single labelled input for someone doing the same lookup repeatedly.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BandFilterBar(
    technologies: Set<BandTechnology>,
    resolvingOnly: Boolean,
    reading: BandReading?,
    sortKey: BandSortKey,
    onToggleTechnology: (BandTechnology) -> Unit,
    onResolvingOnlyChanged: (Boolean) -> Unit,
    onReadingChanged: (BandReading?) -> Unit,
    onSortChanged: (BandSortKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilterChip(
            selected = BandTechnology.LTE in technologies,
            onClick = { onToggleTechnology(BandTechnology.LTE) },
            label = { Text(stringResource(R.string.band_filter_lte)) },
        )
        FilterChip(
            selected = BandTechnology.NR in technologies,
            onClick = { onToggleTechnology(BandTechnology.NR) },
            label = { Text(stringResource(R.string.band_filter_nr)) },
        )
        FilterChip(
            selected = resolvingOnly,
            onClick = { onResolvingOnlyChanged(!resolvingOnly) },
            label = { Text(stringResource(R.string.band_filter_resolving_only)) },
        )
        ReadingMenu(reading, onReadingChanged)
        SortMenu(sortKey, onSortChanged)
    }
}

@Composable
private fun ReadingMenu(reading: BandReading?, onChanged: (BandReading?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        null to R.string.band_reading_auto,
        BandReading.BAND_NUMBER to R.string.band_reading_band,
        BandReading.EARFCN to R.string.band_reading_earfcn,
        BandReading.NARFCN to R.string.band_reading_narfcn,
        BandReading.FREQUENCY to R.string.band_reading_frequency,
    )
    val current = options.first { it.first == reading }.second
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { expanded = true }) {
            Text(
                stringResource(R.string.band_reading_label) + ": " + stringResource(current),
                modifier = Modifier.padding(end = 2.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = { onChanged(value); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SortMenu(sortKey: BandSortKey, onChanged: (BandSortKey) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        BandSortKey.BAND to R.string.band_sort_band,
        BandSortKey.FREQUENCY to R.string.band_sort_frequency,
        BandSortKey.WIDEST_CHANNEL to R.string.band_sort_widest,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { expanded = true }) {
            Text(
                stringResource(R.string.band_sort_label) + ": " +
                        stringResource(options.first { it.first == sortKey }.second)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = { onChanged(value); expanded = false },
                )
            }
        }
    }
}
