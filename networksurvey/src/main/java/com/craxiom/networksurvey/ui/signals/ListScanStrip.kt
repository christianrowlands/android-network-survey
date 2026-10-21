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
import com.craxiom.networksurvey.ui.common.NsScanSweep
import com.craxiom.networksurvey.ui.common.NsSortPill
import com.craxiom.networksurvey.ui.common.findComponentActivity
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.main.SurveyStatusViewModel
import com.craxiom.networksurvey.ui.main.appbar.AppBarAction
import com.craxiom.networksurvey.ui.theme.NsTheme

private val PAUSE_BUTTON_SIZE = 40.dp
private const val COUNT_SEPARATOR = " · "

/**
 * The strip at the top of a Signals list (Wi-Fi, Bluetooth): the counts, a warning when something
 * stops the scan or pauses the survey, the sort pill, the filter button and the pause button. The
 * counts sweep once on each update, a quiet sign the list is live; the pause button shows when
 * the user has frozen it.
 *
 * The counts and warning sit beside the controls when they fit and above them when they do not (a
 * narrow phone at a large font size), so the warning is never squeezed out. TalkBack reads the
 * counts and the whole state as one sentence; the sort pill and the pause button are their own
 * controls.
 *
 * @param counts Joined on one line, for example "Scan #12" and "21 APs", or "12 devices".
 * @param warning What stops the scan or pauses the survey ("Bluetooth is off", "Survey paused"),
 *                or null when all is well.
 * @param spokenStatus The state as TalkBack reads it ("Live view", "Recording", "Updates paused").
 * @param sweepKey Changes with each update shown, which plays the sweep.
 * @param sortLabel The current sort option.
 * @param actions Icon buttons after the sort pill: the filter (badged while it is on), and on Wi-Fi
 *                in a short window the spectrum.
 * @param leadingControls Controls before the sort pill: the Wi-Fi mode pills in a short window,
 *                        where they join the strip rather than taking a row of their own.
 */
@Composable
fun ListScanStrip(
    counts: List<String>,
    warning: String?,
    spokenStatus: String,
    sortLabel: String,
    onSortClick: () -> Unit,
    paused: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
    sweepKey: Any? = null,
    actions: List<AppBarAction> = emptyList(),
    leadingControls: (@Composable () -> Unit)? = null,
) {
    val spoken = (counts + spokenStatus).joinToString(". ")
    StripLayout(
        gap = NsTheme.spacing.m,
        modifier = modifier
            .nsContentWidth()
            .padding(horizontal = NsTheme.spacing.screenGutter, vertical = NsTheme.spacing.s),
        info = {
            // The warning drops under the counts rather than being squeezed beside them
            FlowRow(
                modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
                horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
                verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                // One line, like the Cellular strip: "Scan #12 · 21 APs"
                if (counts.isNotEmpty()) {
                    NsScanSweep(sweepKey) {
                        StripText(counts.joinToString(COUNT_SEPARATOR), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (warning != null) StripText(warning, NsTheme.colors.status.warningText, FontWeight.Medium)
            }
        },
        controls = {
            // Icon buttons carry their own padding, so a narrow gap keeps the row short enough to
            // share the line with the counts
            Row(
                horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A wider gap after the mode pills sets them apart from the sort and the buttons
                leadingControls?.let { Box(Modifier.padding(end = NsTheme.spacing.s)) { it() } }
                NsSortPill(
                    text = stringResource(R.string.wifi_sort_pill_label, sortLabel),
                    onClick = onSortClick,
                    contentDescription = stringResource(R.string.wifi_sort_pill_content_description, sortLabel),
                )
                actions.forEach { AppBarAction(it) }
                PauseButton(paused = paused, onClick = onTogglePause)
            }
        },
    )
}

/**
 * The Signals state of a list: the warning its strip shows (only a paused survey) and the whole
 * state as TalkBack reads it, so the lists answer "is this saved?" for TalkBack the same way as
 * the Cellular strip.
 */
data class SignalsStripWords(val warning: String?, val spoken: String)

/** @return The [SignalsStripWords] for [subTab] from the survey's current state. */
@Composable
fun signalsStripWords(subTab: SignalsSubTab): SignalsStripWords {
    val resources = LocalResources.current
    val activity = LocalContext.current.findComponentActivity()
        ?: return SignalsStripWords(null, stateWord(resources, StripState.LIVE))
    val status by viewModel<SurveyStatusViewModel>(viewModelStoreOwner = activity).status.collectAsStateWithLifecycle()
    val strip = scanStripState(subTab, status, subTab.defaultIntervalSeconds, null).copy(intervalSeconds = null)
    return SignalsStripWords(warningWord(resources, strip.state), stripSpoken(resources, strip))
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
private fun StripText(text: String, color: Color, fontWeight: FontWeight? = null) {
    Text(
        text = text,
        style = NsTheme.textStyles.compact,
        color = color,
        fontWeight = fontWeight,
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
