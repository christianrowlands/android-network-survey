package com.craxiom.networksurvey.ui.cellular.bands

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.band.SpectrumGroup
import com.craxiom.networksurvey.ui.theme.WifiTokens

/**
 * Explains the two things about this screen that are not self evident: what the colours mean, and
 * why a channel number sometimes maps to several bands or to none.
 *
 * The legend ships with the feature rather than arriving later, because a coloured list with no
 * key is decoration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BandHelpSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.band_help_title),
                style = MaterialTheme.typography.titleLarge,
                color = WifiTokens.Ink,
            )

            SpectrumGroup.entries.forEach { group ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(BandFormatting.spectrumColor(group))
                    )
                    Text(
                        text = BandFormatting.spectrumLabel(group),
                        style = TextStyle(fontSize = 13.sp),
                        color = WifiTokens.Ink,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }

            Paragraph(stringResource(R.string.band_help_ambiguity))
            Paragraph(stringResource(R.string.band_help_reference_only))
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
        color = WifiTokens.InkMuted,
    )
}
