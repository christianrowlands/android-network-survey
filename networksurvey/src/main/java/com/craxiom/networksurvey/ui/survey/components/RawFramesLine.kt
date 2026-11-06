package com.craxiom.networksurvey.ui.survey.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.RawFramesProgress
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.common.CountFormat
import com.craxiom.networksurvey.ui.common.SMALL_ICON
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The Records panel's own line for raw frames, under the bar and legend (in the bar they would
 * dwarf every other type): "Raw frames · 12,480 saved · 38 MB". When saving stopped (storage full,
 * or a write failed) the line says so in the warning color, with a word and an explanation, so the
 * state never depends on color alone. Frames that never reached a file get a line of their own.
 */
@Composable
fun RawFramesLine(progress: RawFramesProgress, showDivider: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val saved = stringResource(
        R.string.survey_records_raw_saved,
        CountFormat.format(progress.framesSaved),
        Formatter.formatShortFileSize(context, progress.bytesSaved),
    )
    val stopped = progress.state != RawFramesProgress.State.SAVING
    val word = when (progress.state) {
        RawFramesProgress.State.SAVING -> null
        RawFramesProgress.State.STORAGE_FULL -> stringResource(R.string.survey_records_raw_storage_full_word)
        RawFramesProgress.State.FAILED -> stringResource(R.string.survey_records_raw_failed_word)
    }
    Column(modifier = modifier.fillMaxWidth().padding(top = NsTheme.spacing.m)) {
        if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (showDivider) Modifier.padding(top = NsTheme.spacing.s) else Modifier)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        ) {
            Icon(
                painter = painterResource(RecordType.WIFI_RAW.iconRes),
                contentDescription = null,
                tint = RecordType.WIFI_RAW.color,
                modifier = Modifier.size(SMALL_ICON),
            )
            Text(
                text = stringResource(RecordType.WIFI_RAW.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (word != null) {
                Text(
                    text = word,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = NsTheme.colors.status.warningText,
                )
            }
            Text(text = saved, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        if (stopped) {
            Text(
                text = stringResource(
                    if (progress.state == RawFramesProgress.State.STORAGE_FULL) {
                        R.string.survey_records_raw_storage_full
                    } else {
                        R.string.survey_records_raw_failed
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = NsTheme.colors.status.warningText,
                modifier = Modifier.padding(top = NsTheme.spacing.xs),
            )
        }
        if (progress.framesDropped > 0) {
            Text(
                text = CountFormat.plural(LocalResources.current, R.plurals.scout_missed_raw, progress.framesDropped),
                style = MaterialTheme.typography.bodySmall,
                color = NsTheme.colors.status.warningText,
                modifier = Modifier.padding(top = NsTheme.spacing.xs),
            )
        }
    }
}
