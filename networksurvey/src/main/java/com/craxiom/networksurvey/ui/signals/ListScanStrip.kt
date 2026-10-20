package com.craxiom.networksurvey.ui.signals

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsPulsingDot
import com.craxiom.networksurvey.ui.common.NsSortPill
import com.craxiom.networksurvey.ui.common.findComponentActivity
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.main.SurveyStatusViewModel
import com.craxiom.networksurvey.ui.theme.NsTheme

private val PAUSE_BUTTON_SIZE = 40.dp
private const val COUNT_SEPARATOR = " · "

/**
 * The strip at the top of a Signals list (Wi-Fi, Bluetooth): the counts, the status with a pulsing
 * dot while the list is live, the sort pill and the pause button.
 *
 * The counts and status sit beside the controls when they fit and above them when they do not (a
 * narrow phone at a large font size), so the status is never squeezed out. TalkBack reads the
 * counts and status as one sentence; the sort pill and the pause button are their own controls.
 *
 * @param counts Joined on one line, for example "Scan #12" and "APs: 30", or "12 devices".
 * @param status The state word ("Live view", "Recording", "Bluetooth is off").
 * @param live True while the list is updating, which shows the pulsing dot.
 * @param sortLabel The current sort option.
 */
@Composable
fun ListScanStrip(
    counts: List<String>,
    status: String,
    live: Boolean,
    sortLabel: String,
    onSortClick: () -> Unit,
    paused: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
    statusSpoken: String = status,
) {
    val spoken = (counts + statusSpoken).joinToString(". ")
    StripLayout(
        gap = NsTheme.spacing.m,
        modifier = modifier
            .nsContentWidth()
            .padding(horizontal = NsTheme.spacing.screenGutter, vertical = NsTheme.spacing.s),
        info = {
            // The status drops under the counts rather than being squeezed beside them
            FlowRow(
                modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
                horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
                verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                // One line, like the Cellular and GNSS strips: "Scan #12 · APs: 30"
                StripText(counts.joinToString(COUNT_SEPARATOR), MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s - NsTheme.spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (live) NsPulsingDot()
                    StripText(status, MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        controls = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NsSortPill(
                    text = stringResource(R.string.wifi_sort_pill_label, sortLabel),
                    onClick = onSortClick,
                    contentDescription = stringResource(R.string.wifi_sort_pill_content_description, sortLabel),
                )
                PauseButton(paused = paused, onClick = onTogglePause)
            }
        },
    )
}

/**
 * @return The Signals state word for [subTab] (Live view, Recording, not in this survey, Paused)
 * and its spoken form, so the lists answer "is this saved?" the same way as the other sub-tabs.
 */
@Composable
fun signalsStateWords(subTab: SignalsSubTab): Pair<String, String> {
    val resources = LocalResources.current
    val activity = LocalContext.current.findComponentActivity()
        ?: return stateWord(resources, StripState.LIVE).let { it to it }
    val status by viewModel<SurveyStatusViewModel>(viewModelStoreOwner = activity).status.collectAsStateWithLifecycle()
    val strip = scanStripState(subTab, status, subTab.defaultIntervalSeconds, null).copy(intervalSeconds = null)
    return stateWord(resources, strip.state) to stripSpoken(resources, strip)
}

/** Places [info] beside [controls] when it fits at its full width, otherwise above them, start-aligned. */
@Composable
private fun StripLayout(
    gap: Dp,
    info: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(info, controls), modifier = modifier) { (infoMeasurables, controlMeasurables), constraints ->
        val gapPx = gap.roundToPx()
        val infoMeasurable = infoMeasurables.first()
        val controlsPlaceable = controlMeasurables.first().measure(Constraints(maxWidth = constraints.maxWidth))
        val infoWidth = infoMeasurable.maxIntrinsicWidth(constraints.maxHeight)
        // In a horizontally scrolling parent there is no width to run out of, so it all fits beside.
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else infoWidth + gapPx + controlsPlaceable.width
        val roomBeside = width - controlsPlaceable.width - gapPx
        if (roomBeside > 0 && infoWidth <= roomBeside) {
            val infoPlaceable = infoMeasurable.measure(Constraints(maxWidth = roomBeside))
            val height = constraints.constrainHeight(maxOf(infoPlaceable.height, controlsPlaceable.height))
            layout(width, height) {
                infoPlaceable.place(0, (height - infoPlaceable.height) / 2)
                controlsPlaceable.place(width - controlsPlaceable.width, (height - controlsPlaceable.height) / 2)
            }
        } else {
            val infoPlaceable = infoMeasurable.measure(Constraints(maxWidth = width))
            val height = constraints.constrainHeight(infoPlaceable.height + gapPx + controlsPlaceable.height)
            layout(width, height) {
                infoPlaceable.place(0, 0)
                // Under the counts, from the start, so they read as the next line rather than float
                controlsPlaceable.place(0, infoPlaceable.height + gapPx)
            }
        }
    }
}

@Composable
private fun StripText(text: String, color: Color) {
    Text(
        text = text,
        style = NsTheme.textStyles.compact,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PauseButton(paused: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (paused) R.string.list_updates_resume else R.string.list_updates_pause)
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(PAUSE_BUTTON_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
