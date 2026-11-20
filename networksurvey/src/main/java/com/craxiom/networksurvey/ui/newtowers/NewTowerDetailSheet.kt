package com.craxiom.networksurvey.ui.newtowers

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import com.craxiom.networksurvey.ui.cellular.towermap.DetailRow
import com.craxiom.networksurvey.ui.cellular.towermap.MILLIS_PER_SECOND
import com.craxiom.networksurvey.ui.cellular.towermap.ProtocolBadge
import com.craxiom.networksurvey.ui.cellular.towermap.TimestampRow
import com.craxiom.networksurvey.ui.cellular.towermap.metricName
import com.craxiom.networksurvey.ui.common.NsIconAction
import com.craxiom.networksurvey.ui.common.NsSeeOnMapButton
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.map.bandText
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.util.copyWithSystemConfirmation
import com.craxiom.networksurvey.util.MeasurementFormatter
import java.util.Locale

/** Decimal places of a coordinate shown as text, as on the tower sheet (about 0.1 m). */
private const val COORDINATE_FORMAT = "%.6f"

/**
 * The detail sheet of one find: the provider, whether the tower database has the tower now and
 * when that was last checked, every identifier (selectable, with Copy), the signal, first and last
 * seen, and where the phone was at the find, with "Show on map" when there is a location.
 *
 * @param onShowOnMap Switches the page to its Map view centered on this find.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewTowerDetailSheet(find: NewTowerFindEntity, onShowOnMap: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NsTheme.spacing.l)
                .padding(bottom = NsTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProtocolBadge(find.radio)
                Text(
                    text = providerName(find),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = NsTheme.spacing.s)
                        .semantics { heading() },
                )
            }
            StatusSection(find)
            IdentifiersSection(find)
            WhereSection(find, onShowOnMap)
        }
    }
}

/** Whether the tower database has the tower, as of the last check, and the find's times. */
@Composable
private fun StatusSection(find: NewTowerFindEntity) {
    Text(
        text = stringResource(if (find.inDatabase) R.string.new_towers_in_database else R.string.new_towers_not_in_database),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = if (find.inDatabase) NsTheme.colors.status.success.content else MaterialTheme.colorScheme.onSurface,
    )
    TimestampRow(stringResource(R.string.new_towers_last_checked), find.lastCheckedAt / MILLIS_PER_SECOND)
    TimestampRow(stringResource(R.string.tower_detail_first_seen), find.firstSeenAt / MILLIS_PER_SECOND)
    TimestampRow(stringResource(R.string.new_towers_last_seen), find.lastSeenAt / MILLIS_PER_SECOND)
}

/** Every identifier, selectable, with a Copy action that copies them all as "label: value" lines. */
@Composable
private fun IdentifiersSection(find: NewTowerFindEntity) {
    val context = LocalContext.current
    val identifiers = identifiers(find)
    val copyLabel = stringResource(R.string.new_towers_copy_identifiers)
    val copiedMessage = stringResource(R.string.value_copied_to_clipboard)
    val clipLabel = stringResource(R.string.new_towers_identifiers)
    SectionHeader(
        title = stringResource(R.string.new_towers_identifiers),
        action = {
            NsIconAction(R.drawable.ic_content_copy, copyLabel, onClick = {
                val text = identifiers.joinToString("\n") { (label, value) -> "$label: $value" }
                copyWithSystemConfirmation(context, clipLabel, text, copiedMessage)
            })
        },
    )
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
            identifiers.forEach { (label, value) -> DetailRow(label, value) }
        }
    }
}

/** Where the phone was at the find (never the tower), the tower's own location once known, and "Show on map". */
@Composable
private fun WhereSection(find: NewTowerFindEntity, onShowOnMap: () -> Unit) {
    val context = LocalContext.current
    SectionHeader(stringResource(R.string.new_towers_where_found))
    val latitude = find.latitude
    val longitude = find.longitude
    if (latitude == null || longitude == null) {
        Text(
            text = stringResource(R.string.new_towers_no_location),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
            DetailRow(
                label = stringResource(R.string.tower_detail_location),
                value = coordinatesText(latitude, longitude),
                secondary = find.accuracy?.let { accuracyText(context, it) },
            )
            val towerLatitude = find.towerLatitude
            val towerLongitude = find.towerLongitude
            if (find.inDatabase && towerLatitude != null && towerLongitude != null) {
                DetailRow(stringResource(R.string.new_towers_tower_location), coordinatesText(towerLatitude, towerLongitude))
            }
        }
    }
    Text(
        text = stringResource(R.string.new_towers_where_found_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NsSeeOnMapButton(onClick = onShowOnMap)
}

/**
 * Every identifier of the find as label and value, in the order the sheet lists and copies them.
 * The labels are 3GPP terms, the same in every language.
 */
@Composable
private fun identifiers(find: NewTowerFindEntity): List<Pair<String, String>> = buildList {
    add(stringResource(R.string.mcc_mnc_label) to stringResource(R.string.mcc_mnc_value, find.mcc, find.mnc))
    add(stringResource(R.string.survey_point_area) to stringResource(find.areaFormat, find.area))
    add(stringResource(R.string.calculator_cell_id) to find.cellId.toString())
    find.enbSector?.let { (enb, sector) ->
        add(stringResource(R.string.enb_id_label) to enb.toString())
        add(stringResource(R.string.sector_id_label) to sector.toString())
    }
    find.pci?.let { add(stringResource(find.physicalIdLabel) to it.toString()) }
    find.channel?.let { add(stringResource(find.channelLabel) to it.toString()) }
    bandText(find.lteBand, find.nrBands)?.let { add(stringResource(R.string.band_label) to it) }
    find.signalDbm?.let {
        add(stringResource(R.string.survey_point_signal) to stringResource(R.string.survey_point_dbm_metric, it, metricName(find.surveyProtocol)))
    }
}

@Composable
private fun coordinatesText(latitude: Double, longitude: Double): String = stringResource(
    R.string.new_towers_coordinates,
    String.format(Locale.ROOT, COORDINATE_FORMAT, latitude),
    String.format(Locale.ROOT, COORDINATE_FORMAT, longitude),
)

@Composable
private fun accuracyText(context: Context, meters: Float): String =
    stringResource(R.string.gnss_accuracy_short, MeasurementFormatter.formatAccuracy(context, meters))
