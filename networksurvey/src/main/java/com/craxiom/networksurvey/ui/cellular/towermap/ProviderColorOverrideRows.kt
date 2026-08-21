package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsHeroStatusCard
import com.craxiom.networksurvey.util.PlmnColorMapper

/**
 * Row composables for the provider color override screen, extracted to keep
 * [ProviderColorOverrideScreen] focused: the capacity hero card and an override row with its
 * color swatch, edit affordance, and delete action.
 */

/**
 * The capacity hero card: shows how many of the available override slots are used, switching to
 * the error styling with a "limit reached" title when the limit is hit.
 */
@Composable
internal fun ProviderColorHero(count: Int, max: Int) {
    val isFull = count >= max
    NsHeroStatusCard(
        title = stringResource(
            if (isFull) R.string.provider_color_status_full_title else R.string.provider_color_status_title
        ),
        subtitle = stringResource(R.string.count_of_max_format, count, max),
        icon = painterResource(R.drawable.ic_palette),
        isError = isFull
    )
}

/**
 * A single override row: the color swatch, the MCC-MNC in monospace, the override and default
 * color names, and a trailing delete action. Tapping the row opens the color picker to edit; the
 * delete action only reports the tap and the screen confirms before removing.
 */
@Composable
internal fun ProviderColorOverrideRow(
    entry: ProviderColorEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val overrideColor = PlmnColorMapper.getColorByIndex(entry.paletteIndex)
    val overrideName = PlmnColorMapper.PALETTE_NAMES[entry.paletteIndex]
    val defaultName = PlmnColorMapper.PALETTE_NAMES[entry.defaultPaletteIndex]

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val mccMnc = stringResource(R.string.mcc_mnc_value, entry.mcc, entry.mnc)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(overrideColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mccMnc,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                )
                Text(
                    text = "$overrideName  ·  ${
                        stringResource(R.string.provider_color_default_label, defaultName)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove_format, mccMnc),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
