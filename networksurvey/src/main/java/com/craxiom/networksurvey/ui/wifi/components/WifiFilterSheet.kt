package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.WifiTokens
import com.craxiom.networksurvey.ui.wifi.model.WifiDisplayFilter
import com.craxiom.networksurvey.util.WifiBand

/**
 * Bottom sheet for filtering the visible Wi-Fi network list: SSID/BSSID text search plus
 * multi-select band pills. Changes apply live as the user types or toggles a band. This is a
 * display-only filter and is unrelated to the SSID Exclusion List, which controls what is
 * saved and uploaded; [onManageExclusionList] links there since the funnel icon used to be
 * its entry point on this screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WifiFilterSheet(
    filter: WifiDisplayFilter,
    visibleApCount: Int,
    totalApCount: Int,
    onQueryChanged: (String) -> Unit,
    onBandToggled: (WifiBand) -> Unit,
    onClear: () -> Unit,
    onManageExclusionList: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // The query lives in local state so typing is synchronous; binding the text field directly
    // to the view model's StateFlow makes every keystroke an async round trip, which can drop
    // characters or jump the cursor during fast input. The only external mutations while the
    // sheet is open are the clear buttons below, which reset this state explicitly.
    var query by remember { mutableStateOf(filter.searchQuery) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.wifi_filter_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (filter.isActive) {
                    TextButton(onClick = {
                        query = ""
                        onClear()
                    }) {
                        Text(text = stringResource(R.string.wifi_filter_clear))
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onQueryChanged(it)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(text = stringResource(R.string.wifi_filter_search_hint)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            onQueryChanged("")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.wifi_filter_clear_search),
                            )
                        }
                    }
                },
            )

            // FlowRow so the pills wrap instead of clipping at large font scales.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                WifiBand.entries.forEach { band ->
                    BandFilterPill(
                        band = band,
                        selected = band in filter.selectedBands,
                        onClick = { onBandToggled(band) },
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.wifi_filter_showing_count,
                    visibleApCount,
                    totalApCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(
                onClick = onManageExclusionList,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Text(text = stringResource(R.string.wifi_filter_manage_exclusions))
            }
        }
    }
}

/**
 * Multi-select band toggle pill, visually consistent with the mode tabs on the Wi-Fi list and
 * carrying the band's dot color for continuity with [BandChip].
 */
@Composable
private fun BandFilterPill(
    band: WifiBand,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bandLabel = stringResource(
        when (band) {
            WifiBand.GHZ_24 -> R.string.wifi_band_24_ghz
            WifiBand.GHZ_5 -> R.string.wifi_band_5_ghz
            WifiBand.GHZ_6 -> R.string.wifi_band_6_ghz
        }
    )
    val bg = if (selected) WifiTokens.SsidSoft else Color.Transparent
    val border = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
    val textColor =
        if (selected) WifiTokens.SsidAccent else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(999.dp))
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BandDot(color = band.dotColor)
        Text(
            text = bandLabel,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = textColor,
        )
    }
}
