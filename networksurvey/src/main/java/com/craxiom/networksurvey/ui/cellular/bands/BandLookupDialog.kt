package com.craxiom.networksurvey.ui.cellular.bands

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.band.BandReferenceRepository
import com.craxiom.networksurvey.data.band.BandTapTarget
import com.craxiom.networksurvey.data.band.CellularBand
import com.craxiom.networksurvey.ui.theme.WifiTokens

/**
 * Opens the band reference for the band a cell is currently reporting.
 *
 * Reached by tapping the Band field on the cellular details screen, which is where a user actually
 * meets a band designator and wonders what it is, rather than from a drawer entry they have to
 * remember exists.
 *
 * A cell often reports several candidate bands, so this resolves to a chooser first in that case
 * and to the detail directly when there is only one. It takes band numbers rather than any shared
 * browser state, because this entry point lives outside the Cellular Tools back stack entirely.
 */
@Composable
fun BandLookupDialog(target: BandTapTarget, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var bands by remember { mutableStateOf<List<CellularBand>?>(null) }
    var selected by remember { mutableStateOf<CellularBand?>(null) }

    LaunchedEffect(target) {
        val all = BandReferenceRepository.bands(context)
        val matches = target.bandNumbers.mapNotNull { number ->
            all.firstOrNull { it.technology == target.technology && it.number == number }
        }
        bands = matches
        // Only skip the chooser when every reported band was found. A modem can report a band the
        // reference does not carry (an FR2 or vendor number), and auto-selecting the single
        // survivor would hide the fact that something was dropped.
        if (matches.size == 1 && matches.size == target.bandNumbers.size) {
            selected = matches.first()
        }
    }

    val band = selected

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
        text = {
            val loaded = bands
            when {
                // The asset parse is quick but not instant, and returning nothing here left the
                // first ever open showing a dimmed empty dialog.
                loaded == null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                loaded.isEmpty() -> Text(stringResource(R.string.band_lookup_unknown))
                // The AlertDialog text slot already pads horizontally, so the content adds none.
                band != null -> BandDetailContent(
                    band = band,
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalPadding = 0.dp,
                )
                else -> Chooser(loaded) { selected = it }
            }
        },
    )
}

/**
 * Shown when the cell's channel number matches more than one band. Listing them is the honest
 * answer: the channel genuinely does not identify one, and picking silently would be a guess.
 */
@Composable
private fun Chooser(bands: List<CellularBand>, onPick: (CellularBand) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.band_lookup_choose),
            style = TextStyle(fontSize = 13.sp),
            color = WifiTokens.InkMuted,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        bands.forEach { band ->
            BandRow(band, onClick = { onPick(band) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
