package com.craxiom.networksurvey.gpstest.ui.sky

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.NsTheme

private val LEGEND_ROW_HEIGHT = 40.dp

/**
 * What the sky plot's marks mean, built from the same tables the plot draws with: the C/N0 color
 * scale, how heard and used satellites are marked, and each constellation's and augmentation
 * system's mark and flag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyLegendSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SkyLegendContent()
    }
}

/** The legend's content, scrolling inside its sheet. */
@Composable
fun SkyLegendContent(modifier: Modifier = Modifier) {
    val colors = skyColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NsTheme.spacing.l)
            .padding(bottom = NsTheme.spacing.l)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.sky_legend_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.sky_legend_heading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle(stringResource(R.string.sky_legend_cn0_title))
        Cn0Scale()
        Text(
            text = stringResource(R.string.sky_legend_cn0_units),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.End),
        )

        SectionTitle(stringResource(R.string.sky_legend_shape_availability_title))
        val heardFill = colors.cn0Color(SkyProjection.CN0_STOPS.last())
        LegendRow(SatelliteShape.CIRCLE, MarkStatus.NOT_IN_VIEW, heardFill, stringResource(R.string.sky_legend_shape_not_in_view))
        LegendRow(SatelliteShape.CIRCLE, MarkStatus.IN_VIEW, heardFill, stringResource(R.string.sky_legend_shape_in_view))
        LegendRow(SatelliteShape.CIRCLE, MarkStatus.USED_IN_FIX, heardFill, stringResource(R.string.sky_legend_shape_used_in_fix))

        SectionTitle(stringResource(R.string.sky_legend_shape_gnss_title))
        SkyLegend.constellations.forEach { (_, row) ->
            LegendRow(row.shape, MarkStatus.NOT_IN_VIEW, heardFill, stringResource(row.name), row.flag)
        }

        SectionTitle(stringResource(R.string.sky_legend_shape_sbas_title))
        SkyLegend.augmentations.forEach { (_, row) ->
            LegendRow(row.shape, MarkStatus.NOT_IN_VIEW, heardFill, stringResource(row.name), row.flag)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(top = NsTheme.spacing.m)
            .semantics { heading() },
    )
}

/** One legend line: the mark, the flag when there is one, and the name TalkBack reads. */
@Composable
private fun LegendRow(
    shape: SatelliteShape,
    status: MarkStatus,
    fill: Color,
    name: String,
    @DrawableRes flag: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LEGEND_ROW_HEIGHT)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        SatelliteMarkIcon(shape, status, fill)
        if (flag != null) {
            Image(
                painter = painterResource(flag),
                contentDescription = null,
                modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
