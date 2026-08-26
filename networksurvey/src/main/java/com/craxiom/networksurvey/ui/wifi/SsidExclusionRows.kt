package com.craxiom.networksurvey.ui.wifi

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsHeroStatusCard
import com.craxiom.networksurvey.ui.theme.WifiTokens

/**
 * Row composables for the SSID Exclusion List screen, extracted to keep [SsidExclusionListScreen]
 * focused: the capacity hero card and an excluded SSID row with its delete action.
 */

/**
 * The capacity hero card: shows how many of the available exclusion slots are used, switching to
 * the error styling with a "list is full" title when the limit is reached.
 */
@Composable
internal fun SsidExclusionHero(count: Int, max: Int) {
    val isFull = count >= max
    NsHeroStatusCard(
        title = stringResource(
            if (isFull) R.string.ssid_exclusion_status_full_title else R.string.ssid_exclusion_status_title
        ),
        subtitle = pluralStringResource(R.plurals.count_of_max_format, count, count, max),
        icon = painterResource(R.drawable.ic_block),
        isError = isFull
    )
}

/**
 * A single excluded SSID row: the SSID in the Wi-Fi accent color with a trailing delete action.
 * The delete action only reports the tap; the screen confirms before removing.
 */
@Composable
internal fun SsidExclusionRow(ssid: String, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ssid,
                style = MaterialTheme.typography.titleMedium,
                color = WifiTokens.SsidAccent,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove_format, ssid),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
