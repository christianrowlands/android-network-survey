package com.craxiom.networksurvey.ui.watchlist

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog

/**
 * The Watchlist history screen: a chronological log of past "Seen" detections, with a link to view
 * a sighting on a map and an action to clear the log.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistHistoryScreen(
    viewModel: WatchlistHistoryViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.watchlist_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.watchlist_navigate_back)
                        )
                    }
                },
                actions = {
                    if (uiState.hits.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.watchlist_history_clear)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.hits.isEmpty()) {
                Text(
                    text = stringResource(R.string.watchlist_history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = uiState.hits, key = { it.id }) { hit ->
                        WatchlistHitRow(hit)
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        NsConfirmationDialog(
            title = stringResource(R.string.watchlist_history_clear_title),
            message = stringResource(R.string.watchlist_history_clear_message),
            confirmText = stringResource(R.string.watchlist_history_clear),
            onConfirm = { viewModel.clearHistory() },
            onDismiss = { showClearDialog = false },
            destructive = true
        )
    }
}

@Composable
private fun WatchlistHitRow(hit: WatchlistHitEntity) {
    val context = LocalContext.current

    val matchedText = when (hit.matchedField) {
        WatchlistHitEntity.MATCHED_FIELD_BSSID -> stringResource(R.string.watchlist_matched_bssid)
        else -> stringResource(R.string.watchlist_matched_ssid)
    }
    val whenText = DateUtils.getRelativeTimeSpanString(
        hit.timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hit.label ?: hit.ssid ?: hit.bssid ?: "",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "$matchedText  -  ${
                        stringResource(
                            R.string.watchlist_seen_relative,
                            whenText
                        )
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.watchlist_signal_dbm, hit.rssi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val latitude = hit.latitude
            val longitude = hit.longitude
            if (latitude != null && longitude != null) {
                IconButton(onClick = {
                    val label = hit.label ?: hit.ssid ?: hit.bssid ?: ""
                    val uri =
                        "geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})".toUri()
                    val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                    // Launch directly and catch the failure: package-visibility filtering on modern
                    // Android makes resolveActivity() return null for implicit intents, so guarding
                    // with it would make this button silently do nothing.
                    try {
                        context.startActivity(mapIntent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.watchlist_no_map_app),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = stringResource(R.string.watchlist_view_on_map),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
