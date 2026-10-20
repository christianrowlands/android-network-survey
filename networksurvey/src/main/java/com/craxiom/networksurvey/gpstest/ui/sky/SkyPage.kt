package com.craxiom.networksurvey.gpstest.ui.sky

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import com.craxiom.networksurvey.gpstest.ui.GnssFilterNotice
import com.craxiom.networksurvey.gpstest.ui.clearGnssFilter
import com.craxiom.networksurvey.gpstest.ui.gnss.data.FixState
import com.craxiom.networksurvey.gpstest.ui.gnss.model.SignalInfoViewModel
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.common.StatusLabel
import com.craxiom.networksurvey.ui.theme.NsTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.math.abs

/** The widest the plot grows in a single column, so a tablet's plot stays a readable size. */
private val PLOT_MAX_SIZE = 440.dp

/**
 * From this width, when the screen is also wider than tall, the GNSS pages sit in two panes (the
 * plot beside the cards, the fix beside the satellites).
 */
internal val GNSS_TWO_PANE_MIN_WIDTH = 600.dp

/** Beside the cards, the plot is never smaller than this; the plot's pane scrolls when it must. */
private val TWO_PANE_PLOT_MIN_SIZE = 280.dp

/** What the sky panel adds around the plot: its header, the counts, its padding and the gutter. */
private val SKY_PANEL_CHROME = 120.dp

/** The smallest heading change that redraws the plot. */
private const val HEADING_STEP_DEGREES = 1f

/**
 * The Sky page of the GNSS view: reads the satellites and the fix from [viewModel] and turns the
 * plot with the phone's heading while the page is started.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun SkyPage(viewModel: SignalInfoViewModel) {
    val filtered: List<SatelliteStatus> by viewModel.filteredStatuses.observeAsState(emptyList())
    val all: List<SatelliteStatus> by viewModel.allStatuses.observeAsState(emptyList())
    val fixState: FixState by viewModel.fixState.observeAsState(FixState.NotAcquired)
    val sky = remember(filtered) { SkyModel.from(filtered) }

    val heading = remember { mutableFloatStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.headingUpdates().collect {
                // The sensor reports up to 100 times a second; a change under a degree is not visible.
                val degrees = it.values[0].toFloat()
                if (abs(degrees - heading.floatValue) >= HEADING_STEP_DEGREES) heading.floatValue = degrees
            }
        }
    }

    SkyPageContent(
        sky = sky,
        fix = fixState is FixState.Acquired,
        shownSignals = filtered.size,
        totalSignals = all.size,
        heading = { heading.floatValue },
        onShowAll = { clearGnssFilter() },
    )
}

/**
 * The Sky page without its state: a filter notice when the filter hides some signals, the sky panel
 * (fix, Legend, the plot, the counts) and the average C/N0 panel. One scrolling column capped at a
 * readable width, or the plot beside the cards on a screen wide and tall enough. It owns the legend
 * sheet's open state.
 *
 * @param shownSignals The signals the filter lets through, of [totalSignals].
 * @param heading Where the phone points, read only while drawing the plot.
 */
@Composable
fun SkyPageContent(
    sky: SkyState,
    fix: Boolean,
    shownSignals: Int,
    totalSignals: Int,
    heading: () -> Float,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var legendOpen by remember { mutableStateOf(false) }
    val notice: @Composable () -> Unit = {
        if (shownSignals < totalSignals) GnssFilterNotice(shownSignals, totalSignals, onShowAll)
    }
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val gutter = Modifier.padding(horizontal = NsTheme.spacing.screenGutter, vertical = NsTheme.spacing.s)
        if (maxWidth >= GNSS_TWO_PANE_MIN_WIDTH && maxWidth > maxHeight) {
            // As tall as the screen allows, so a landscape phone shows the whole plot or nearly.
            val plotCap = maxOf(maxHeight - SKY_PANEL_CHROME, TWO_PANE_PLOT_MIN_SIZE)
            Row(gutter.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.panelGap)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    SkyPanel(sky, fix, { legendOpen = true }) {
                        GnssSkyPlot(
                            sky = sky,
                            heading = heading,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .widthIn(max = plotCap)
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        )
                    }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.panelGap),
                ) {
                    notice()
                    SkyAveragesPanel(sky.summary)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = NsTheme.spacing.contentMaxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .then(gutter),
                verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.panelGap),
            ) {
                notice()
                SkyPanel(sky, fix, { legendOpen = true }) {
                    GnssSkyPlot(
                        sky = sky,
                        heading = heading,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .widthIn(max = PLOT_MAX_SIZE)
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                }
                SkyAveragesPanel(sky.summary)
            }
        }
    }
    if (legendOpen) SkyLegendSheet(onDismiss = { legendOpen = false })
}

/** The sky panel: "Sky", the fix word and Legend over the plot, then the counts. */
@Composable
private fun SkyPanel(
    sky: SkyState,
    fix: Boolean,
    onLegend: () -> Unit,
    modifier: Modifier = Modifier,
    plot: @Composable ColumnScope.() -> Unit,
) {
    NsPanel(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PanelTitle(stringResource(R.string.gnss_page_sky), Modifier.weight(1f))
            StatusLabel(
                text = stringResource(if (fix) R.string.gnss_fix else R.string.gnss_no_fix),
                color = if (fix) NsTheme.colors.status.success.content else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NsTextAction(text = stringResource(R.string.sky_legend_title), onClick = onLegend)
        }
        plot()
        Text(
            text = pluralStringResource(
                R.plurals.sky_satellites_summary, sky.summary.satellites, sky.summary.satellites, sky.summary.used,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // The plot already says this to TalkBack.
            modifier = Modifier.fillMaxWidth().semantics { hideFromAccessibility() },
        )
    }
}

/** A panel's title as a heading. */
@Composable
internal fun PanelTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { heading() },
    )
}
