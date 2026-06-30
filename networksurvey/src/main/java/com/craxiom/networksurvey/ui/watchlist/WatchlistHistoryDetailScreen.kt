package com.craxiom.networksurvey.ui.watchlist

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.theme.WifiTokens
import com.craxiom.networksurvey.ui.wifi.components.SignalMeter
import com.craxiom.networksurvey.util.toWifiSignalCategory

/** SavedStateHandle key for handing the watched-network id to the sighting-details screen. */
const val WATCHLIST_ENTRY_ID_KEY = "watchlist_entry_id"

/**
 * The sighting-details screen for a single watched network: a map of every located sighting on top and
 * a synchronized list below. Tapping a list row selects (and recenters) its map pin; tapping a pin
 * selects and scrolls to its list row. The trash action clears this network's history (the watched
 * entry is kept), after which the screen pops back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistHistoryDetailScreen(
    viewModel: WatchlistHistoryDetailViewModel,
    entryId: Long,
    onNavigateUp: () -> Unit
) {
    LaunchedEffect(entryId) { viewModel.start(entryId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Once this network's history is emptied (e.g. cleared here), there is nothing to show: go back.
    LaunchedEffect(uiState.loaded, uiState.sightings.isEmpty()) {
        if (uiState.loaded && uiState.sightings.isEmpty()) onNavigateUp()
    }

    // Keep the list in sync when a pin selects a sighting.
    LaunchedEffect(uiState.selectedHitId) {
        val id = uiState.selectedHitId ?: return@LaunchedEffect
        val index = uiState.sightings.indexOfFirst { it.id == id }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    val matchedBy = if (uiState.matchedByName) {
        stringResource(R.string.watchlist_matched_by_name_short)
    } else {
        stringResource(R.string.watchlist_matched_by_ap_short)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = WifiTokens.SsidAccent
                        )
                        Text(
                            text = stringResource(
                                R.string.watchlist_detail_subtitle,
                                matchedBy,
                                uiState.sightingCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.watchlist_navigate_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.watchlist_history_clear)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            DetailMap(uiState, viewModel)
            HorizontalDivider()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(items = uiState.sightings, key = { it.id }) { hit ->
                    SightingRow(
                        hit = hit,
                        selected = hit.id == uiState.selectedHitId,
                        onClick = { viewModel.selectHit(hit.id) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }

    if (showClearDialog) {
        NsConfirmationDialog(
            title = stringResource(R.string.watchlist_detail_clear_title),
            message = stringResource(R.string.watchlist_detail_clear_message, uiState.title),
            confirmText = stringResource(R.string.watchlist_history_clear),
            onConfirm = { viewModel.clearHistory() },
            onDismiss = { showClearDialog = false },
            destructive = true
        )
    }
}

@Composable
private fun DetailMap(
    uiState: WatchlistDetailUiState,
    viewModel: WatchlistHistoryDetailViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(208.dp)
    ) {
        if (uiState.mapPoints.isEmpty()) {
            Text(
                text = stringResource(R.string.watchlist_history_map_no_locations),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        } else {
            WatchlistMap(
                points = uiState.mapPoints,
                selectedId = uiState.selectedHitId?.toString(),
                onPointClick = { id -> viewModel.selectHit(id.toLongOrNull()) },
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = stringResource(R.string.watchlist_detail_strongest_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SightingRow(
    hit: WatchlistHitEntity,
    selected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val whenText = DateUtils.formatDateTime(
        context,
        hit.timestamp,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME
    )
    val background =
        if (selected) WifiTokens.SsidSoft else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(background)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.watchlist_signal_dbm, hit.rssi),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.SemiBold,
                color = hit.rssi.toWifiSignalCategory().color
            )
            SignalMeter(rssi = hit.rssi, width = 80.dp)
            Text(
                text = whenText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
        val latitude = hit.latitude
        val longitude = hit.longitude
        val locationText = if (latitude != null && longitude != null) {
            stringResource(
                R.string.watchlist_detail_location_line,
                stringResource(
                    R.string.watchlist_detail_latlng,
                    "%.5f".format(latitude),
                    "%.5f".format(longitude)
                ),
                hit.bssid ?: ""
            )
        } else {
            hit.bssid ?: ""
        }
        Text(
            text = locationText,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
