package com.craxiom.networksurvey.ui.cellular.bands

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.band.BandMatchGroup
import com.craxiom.networksurvey.data.band.BandReading
import com.craxiom.networksurvey.ui.cellular.model.BandBrowserViewModel
import com.craxiom.networksurvey.ui.theme.WifiTokens

/**
 * The browsable reference of every LTE and 5G NR operating band.
 *
 * Search does not replace the list. Interpretation groups are drawn above a catalogue that is
 * always present, so a query that matches nothing reads as "that value means nothing" rather than
 * as an empty table, and browsing never looks like a failed search. The groups sit on a tinted
 * surface and the catalogue on the plain one, so it is obvious at a glance which region is an
 * answer and which is the reference.
 */
@Composable
fun BandBrowserScreen(viewModel: BandBrowserViewModel, modifier: Modifier = Modifier) {
    val loading by viewModel.loading.collectAsState()
    val query by viewModel.query.collectAsState()
    val reading by viewModel.reading.collectAsState()
    val technologies by viewModel.technologies.collectAsState()
    val resolvingOnly by viewModel.resolvingOnly.collectAsState()
    val sortKey by viewModel.sortKey.collectAsState()
    val matches by viewModel.matches.collectAsState()
    val catalogue by viewModel.catalogue.collectAsState()
    val selected by viewModel.selectedBand.collectAsState()
    val listState = rememberLazyListState()

    // Interpretation groups and the no-match notice are prepended above the catalogue. A keyed
    // LazyColumn preserves the scroll anchor on whichever item was already visible, so without
    // this those items are inserted above the viewport and the screen looks as though the search
    // did nothing. Keyed on the results as well as the query: the query changes one keystroke
    // before the list does, so keying on the query alone scrolls the old list and the newly
    // inserted item still lands off screen.
    LaunchedEffect(query, matches) { listState.scrollToItem(0) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BandSearchField(query, reading, viewModel::setQuery)
            BandFilterBar(
                technologies = technologies,
                resolvingOnly = resolvingOnly,
                reading = reading,
                sortKey = sortKey,
                onToggleTechnology = viewModel::toggleTechnology,
                onResolvingOnlyChanged = viewModel::setResolvingOnly,
                onReadingChanged = viewModel::setReading,
                onSortChanged = viewModel::setSortKey,
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            // Without this, a query that matches nothing renders exactly like an empty field:
            // the plain catalogue, with no hint that the value meant anything at all.
            if (query.isNotBlank() && matches.isEmpty()) {
                item(key = "no-match") { NoMatch(query) }
            }

            matches.forEach { group ->
                item(key = "group-${group.reading}-${group.value}") {
                    MatchGroupHeader(group)
                }
                items(group.bands, key = { "m-${group.reading}-${it.technology}-${it.number}" }) {
                    BandRow(it, onClick = { viewModel.selectBand(it) })
                }
            }

            item(key = "catalogue-header") {
                SectionLabel(
                    stringResource(R.string.band_catalogue_header) + "  ·  " +
                            pluralStringResource(
                                R.plurals.band_count,
                                catalogue.size,
                                catalogue.size,
                            )
                )
            }
            if (catalogue.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.band_empty),
                        style = TextStyle(fontSize = 13.sp),
                        color = WifiTokens.InkFaint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    )
                }
            }
            items(catalogue, key = { "c-${it.technology}-${it.number}" }) { band ->
                BandRow(band, onClick = { viewModel.selectBand(band) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    selected?.let { BandDetailSheet(it) { viewModel.selectBand(null) } }
}

/**
 * The heading above one interpretation of the query. For a channel reading it also states the
 * frequency, which is always correct even where the band is not, and says plainly when a channel
 * belongs to no band at all.
 */
@Composable
private fun MatchGroupHeader(group: BandMatchGroup) {
    val label = stringResource(
        when (group.reading) {
            BandReading.NAME -> R.string.band_group_name
            BandReading.BAND_NUMBER -> R.string.band_group_number
            BandReading.EARFCN -> R.string.band_group_earfcn
            BandReading.NARFCN -> R.string.band_group_narfcn
            BandReading.FREQUENCY -> R.string.band_group_frequency
        }
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = WifiTokens.SsidAccent,
        )
        group.frequencyMhz?.let {
            Text(
                text = BandFormatting.mhz(it) + " " + stringResource(R.string.band_unit_mhz),
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                color = WifiTokens.Ink,
            )
        }
        if (group.bands.isEmpty()) {
            Text(
                text = stringResource(
                    if (group.reading == BandReading.FREQUENCY) R.string.band_group_no_band_frequency
                    else R.string.band_group_no_band
                ),
                style = TextStyle(fontSize = 12.sp),
                color = WifiTokens.InkFaint,
            )
        }
    }
}

@Composable
private fun NoMatch(query: String) {
    Text(
        text = stringResource(R.string.band_no_match, query),
        style = TextStyle(fontSize = 13.sp),
        color = WifiTokens.InkFaint,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
        color = WifiTokens.InkFaint,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}
