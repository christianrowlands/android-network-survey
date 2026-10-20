package com.craxiom.networksurvey.ui.survey.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.SMALL_ICON
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The Mission ID with its copy button, on the Recording card and, after Stop, under "Last survey
 * ran for ..." so the ID can still be copied to look up the survey's data.
 */
@Composable
fun MissionIdRow(missionId: String, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
    ) {
        Icon(painterResource(R.drawable.ic_tag), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SMALL_ICON))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.mission_id_card_title), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = missionId, style = NsTheme.textStyles.mono, color = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = { onCopy(missionId) }) {
            Icon(
                painterResource(R.drawable.ic_content_copy),
                contentDescription = stringResource(R.string.mission_id_copy_content_description),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(SMALL_ICON),
            )
        }
    }
}
