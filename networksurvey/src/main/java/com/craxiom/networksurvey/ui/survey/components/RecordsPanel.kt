package com.craxiom.networksurvey.ui.survey.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.DbUploadStore
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.theme.NsTheme
import java.text.NumberFormat
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.SectionHeader

/**
 * Records by type: one stacked bar in the record type colors, and a two-column legend with the
 * counts. The bar is decoration for sighted users; the legend carries the numbers for TalkBack.
 *
 * With [savedForUpload] the counts are of records saved for the community databases, and a note
 * under the legend says why they hold still while the phone does.
 */
@Composable
fun RecordsPanel(
    types: List<RecordType>,
    counts: Map<RecordType, Long>,
    savedForUpload: Boolean,
    modifier: Modifier = Modifier,
) {
    NsPanel(modifier = modifier) {
        SectionHeader(
            title = stringResource(R.string.survey_records_heading),
            trailing = stringResource(if (savedForUpload) R.string.survey_records_saved_for_upload else R.string.survey_records_by_type),
            topPadding = 0.dp,
        )
        StackedBar(types, counts)
        types.chunked(LEGEND_COLUMNS).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = NsTheme.spacing.s), horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.l)) {
                row.forEach { LegendItem(it, counts[it] ?: 0L, Modifier.weight(1f)) }
                if (row.size < LEGEND_COLUMNS) Spacer(Modifier.weight(1f))
            }
        }
        if (savedForUpload) {
            Text(
                text = stringResource(
                    R.string.survey_records_upload_rules,
                    DbUploadStore.DISTANCE_MOVED_THRESHOLD_METERS,
                    DbUploadStore.ACCURACY_THRESHOLD_METERS,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = NsTheme.spacing.m),
            )
        }
    }
}

@Composable
private fun StackedBar(types: List<RecordType>, counts: Map<RecordType, Long>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(RoundedCornerShape(BAR_HEIGHT / 2))
            .background(MaterialTheme.colorScheme.outlineVariant)
            .clearAndSetSemantics { },
    ) {
        types.forEach { type ->
            val count = counts[type] ?: 0L
            if (count > 0) {
                Box(
                    Modifier
                        .weight(count.toFloat())
                        .fillMaxHeight()
                        .background(type.color)
                )
            }
        }
    }
}

@Composable
private fun LegendItem(type: RecordType, count: Long, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
        Box(Modifier.size(SWATCH).clip(RoundedCornerShape(2.dp)).background(type.color))
        Text(stringResource(type.labelRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            NumberFormat.getIntegerInstance().format(count),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private const val LEGEND_COLUMNS = 2
private val BAR_HEIGHT = 10.dp
private val SWATCH = 10.dp
