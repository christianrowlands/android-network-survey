package com.craxiom.networksurvey.ui.watchlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.common.dialogs.NsMessageDialog

/**
 * The confirmation prompt shown when a watchlist import deep link is opened. It previews every network
 * the link carries (so a semi-trusted sender's payload is never opaque), summarizes how many are new
 * versus already saved, and adds only the new ones on confirm. When every network is already saved it
 * degrades to a simple informational dialog with nothing to add.
 *
 * The preview rows are rendered as plain composables rather than a LazyColumn because the shared dialog
 * scaffold already wraps its body in a vertical scroll; the entry-count cap in the parser bounds how
 * many rows render.
 */
@Composable
fun WatchlistImportDialog(
    pending: PendingWatchlistImport,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (pending.allDuplicates) {
        NsMessageDialog(
            title = stringResource(R.string.watchlist_import_all_saved_title),
            message = pluralStringResource(
                R.plurals.watchlist_import_all_saved_message,
                pending.totalCount,
                pending.totalCount
            ),
            onDismiss = onDismiss,
            confirmText = stringResource(R.string.watchlist_import_done),
            icon = Icons.Outlined.Info,
        )
        return
    }

    val title = pending.name
        ?.let { stringResource(R.string.watchlist_import_title, it) }
        ?: stringResource(R.string.watchlist_import_title_generic)
    val summary = if (pending.duplicateCount > 0) {
        stringResource(
            R.string.watchlist_import_summary,
            pluralStringResource(
                R.plurals.watchlist_import_new_count,
                pending.addedCount,
                pending.addedCount
            ),
            pluralStringResource(
                R.plurals.watchlist_import_saved_count,
                pending.duplicateCount,
                pending.duplicateCount
            )
        )
    } else {
        stringResource(
            R.string.watchlist_import_summary_no_dupes,
            pluralStringResource(
                R.plurals.watchlist_import_new_count,
                pending.addedCount,
                pending.addedCount
            )
        )
    }

    NsConfirmationDialog(
        title = title,
        message = summary,
        confirmText = stringResource(R.string.watchlist_import_confirm, pending.addedCount),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        icon = Icons.Default.Add,
        body = {
            Spacer(Modifier.height(8.dp))
            pending.previewRows.forEach { row -> ImportPreviewRow(row) }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.watchlist_import_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** One row in the import preview: the resolved label, its SSID/BSSID, and a duplicate marker. */
@Composable
private fun ImportPreviewRow(row: WatchlistImportPreviewRow) {
    val identifier = listOfNotNull(
        row.ssid?.takeIf { it.isNotBlank() },
        row.bssid?.takeIf { it.isNotBlank() },
    ).joinToString("  -  ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(if (row.isDuplicate) 0.5f else 1f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (row.isDuplicate) {
                Text(
                    text = stringResource(R.string.watchlist_import_already_saved_tag),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (identifier.isNotBlank()) {
            Text(
                text = identifier,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
