package com.craxiom.networksurvey.gpstest.ui.status

import android.location.Location
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.Application
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import com.craxiom.networksurvey.gpstest.ui.GnssFilterNotice
import com.craxiom.networksurvey.gpstest.ui.clearGnssFilter
import com.craxiom.networksurvey.gpstest.ui.gnss.data.FixState
import com.craxiom.networksurvey.gpstest.ui.gnss.model.SatelliteMetadata
import com.craxiom.networksurvey.gpstest.ui.gnss.model.SignalInfoViewModel
import com.craxiom.networksurvey.gpstest.ui.sky.GNSS_TWO_PANE_MIN_WIDTH
import com.craxiom.networksurvey.model.DilutionOfPrecision
import com.craxiom.networksurvey.ui.common.NsLazyPanelItem
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.util.copyWithSystemConfirmation
import com.craxiom.networksurvey.util.NsUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * The Status page of the GNSS view: reads the fix, the signals and their trends from [viewModel],
 * which has already filtered and sorted the signals, sorts by a tapped column header, and copies
 * what the user long presses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun StatusPage(viewModel: SignalInfoViewModel) {
    val location: Location by viewModel.location.observeAsState(Location(NO_LOCATION))
    val ttffSeconds: Long? by viewModel.ttff.observeAsState(null)
    val ttff = ttffSeconds?.let { stringResource(R.string.settings_seconds, it.toInt()) }.orEmpty()
    val altitudeMsl: Double by viewModel.altitudeMsl.observeAsState(Double.NaN)
    val dop: DilutionOfPrecision by viewModel.dop.observeAsState(DilutionOfPrecision(Double.NaN, Double.NaN, Double.NaN))
    val metadata: SatelliteMetadata by viewModel.filteredSatelliteMetadata.observeAsState(SatelliteMetadata())
    val fixState: FixState by viewModel.fixState.observeAsState(FixState.NotAcquired)
    val gnss: List<SatelliteStatus> by viewModel.filteredGnssStatuses.observeAsState(emptyList())
    val sbas: List<SatelliteStatus> by viewModel.filteredSbasStatuses.observeAsState(emptyList())
    val all: List<SatelliteStatus> by viewModel.allStatuses.observeAsState(emptyList())
    val trends: GnssTrends by viewModel.trends.observeAsState(GnssTrends.EMPTY)
    val sortIndex: Int by viewModel.sortIndex.observeAsState(PreferenceUtils.getSatSortIndex(Application.getPrefs()))

    val context = LocalContext.current
    val readings = FixReadings.from(location, ttff, altitudeMsl, dop, metadata, fixState)
    val gnssRows = remember(gnss) { gnss.map(SatelliteRow::from) }
    val sbasRows = remember(sbas) { sbas.map(SatelliteRow::from) }
    val locationCopied = stringResource(R.string.copied_to_clipboard)
    val valueCopied = stringResource(R.string.value_copied_to_clipboard)
    val locationLabel = stringResource(R.string.gnss_status_location_title)
    var explainTime by rememberSaveable { mutableStateOf(false) }

    StatusPageContent(
        readings = readings,
        gnssRows = gnssRows,
        sbasRows = sbasRows,
        trends = trends,
        sortIndex = sortIndex,
        shownSignals = gnss.size + sbas.size,
        totalSignals = all.size,
        onSort = { viewModel.setSort(context, it, Application.getPrefs()) },
        onShowAll = { clearGnssFilter() },
        onCopyLocation = if (readings.hasPosition) {
            { copyWithSystemConfirmation(context, locationLabel, NsUtils.createLocationShare(location, true), locationCopied) }
        } else {
            null
        },
        onCopyValue = { label, value -> copyWithSystemConfirmation(context, label, value, valueCopied) },
        onExplainTime = { explainTime = true },
    )
    if (explainTime) TimeWrongDialog(readings.timeMs, onDismiss = { explainTime = false })
}

/**
 * The Status page without its state: a filter notice when the filter hides some signals, the
 * location card, AGC and C/N0 by band, then the GNSS signals and, when there are any, the SBAS ones,
 * each as a table whose header stays at the top while its rows scroll. One list capped at a
 * readable width, or the location and trends beside the tables on a screen wider than tall from
 * 600dp.
 *
 * @param sortIndex The sort the user chose, an index into the sort_sats array.
 * @param shownSignals The signals the filter lets through, of [totalSignals].
 */
@Composable
fun StatusPageContent(
    readings: FixReadings,
    gnssRows: List<SatelliteRow>,
    sbasRows: List<SatelliteRow>,
    trends: GnssTrends,
    sortIndex: Int,
    shownSignals: Int,
    totalSignals: Int,
    onSort: (Int) -> Unit,
    onShowAll: () -> Unit,
    onCopyLocation: (() -> Unit)?,
    onCopyValue: (label: String, value: String) -> Unit,
    onExplainTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val numbers = rememberGnssNumbers()
    val filtered = shownSignals < totalSignals
    val sortLabel = stringArrayResource(R.array.sort_sats).getOrNull(sortIndex)
    val summary: @Composable () -> Unit = {
        if (filtered) GnssFilterNotice(shownSignals, totalSignals, onShowAll)
        FixPanel(readings, onCopyLocation, onCopyValue, onExplainTime)
        if (trends.bands.isNotEmpty()) BandTrendPanel(trends, filtered, numbers)
    }
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val spacing = NsTheme.spacing
        val twoPane = maxWidth >= GNSS_TWO_PANE_MIN_WIDTH && maxWidth > maxHeight
        val paneWidth = if (twoPane) (maxWidth - spacing.screenGutter) / 2 else maxWidth
        val tableWidth = paneWidth.coerceAtMost(spacing.contentMaxWidth) - spacing.screenGutter * 2 - spacing.panelPaddingHorizontal * 2
        val layout = rememberTableLayout(gnssRows + sbasRows, tableWidth, numbers)
        val scroll = rememberScrollState().takeIf { layout.scrolls }
        val tables: LazyListScope.() -> Unit = {
            val table = TableContent(layout, sortIndex, onSort, trends, numbers, scroll)
            satelliteTable(GNSS_KEY, R.string.gnss_status_satellites_title, sortLabel, showKey = true, gnssRows, table)
            satelliteTable(SBAS_KEY, R.string.gnss_status_sbas_title, sortLabel.takeIf { gnssRows.isEmpty() }, showKey = gnssRows.isEmpty(), sbasRows, table)
        }
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = spacing.screenGutter, top = spacing.s, bottom = spacing.l),
                    verticalArrangement = Arrangement.spacedBy(spacing.panelGap),
                ) { summary() }
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), content = tables)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = SUMMARY_KEY) {
                    Column(
                        modifier = Modifier
                            .nsContentWidth()
                            .padding(horizontal = spacing.screenGutter)
                            .padding(top = spacing.s),
                        verticalArrangement = Arrangement.spacedBy(spacing.panelGap),
                    ) { summary() }
                }
                tables()
            }
        }
    }
}

/** What every row and header of the tables shares. */
private class TableContent(
    val layout: TableLayout,
    val sortIndex: Int,
    val onSort: (Int) -> Unit,
    val trends: GnssTrends,
    val numbers: GnssNumbers,
    val scroll: ScrollState?,
)

/** A table's sticky header and its rows; nothing when it has no rows. */
private fun LazyListScope.satelliteTable(
    key: String,
    @StringRes title: Int,
    sortLabel: String?,
    showKey: Boolean,
    rows: List<SatelliteRow>,
    table: TableContent,
) {
    if (rows.isEmpty()) return
    stickyHeader(key = "$key-header", contentType = HEADER_CONTENT) {
        SatelliteTableHeader(
            title = stringResource(title),
            sortLabel = sortLabel,
            showKey = showKey,
            layout = table.layout,
            sortIndex = table.sortIndex,
            onSort = table.onSort,
            scroll = table.scroll,
        )
    }
    itemsIndexed(rows, key = { _, row -> "$key-${row.key}" }, contentType = { _, _ -> ROW_CONTENT }) { index, row ->
        NsLazyPanelItem(index, rows.size, Modifier.animateItem(), divider = false, continuesAbove = true) {
            SatelliteTableRow(row, table.trends.signal(row.key), table.trends.nowMs, table.layout, table.numbers, table.scroll)
        }
    }
}

/** The provider name of the empty location shown before the first fix. */
private const val NO_LOCATION = "none"

/** Lazy list keys and content types. */
private const val SUMMARY_KEY = "summary"
private const val GNSS_KEY = "gnss"
private const val SBAS_KEY = "sbas"
private const val HEADER_CONTENT = "header"
private const val ROW_CONTENT = "row"
