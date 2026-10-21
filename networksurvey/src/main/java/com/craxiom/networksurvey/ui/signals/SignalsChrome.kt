package com.craxiom.networksurvey.ui.signals

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.telephony.SubscriptionManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsScanSweep
import com.craxiom.networksurvey.ui.common.NsSegmentedToggle
import com.craxiom.networksurvey.ui.common.SegmentedOption
import com.craxiom.networksurvey.ui.common.SegmentedStyle
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.theme.NsTheme
import timber.log.Timber

/**
 * The Cellular · Wi-Fi · Bluetooth · GNSS tab row at the top of the Signals tab. The tab has no top
 * bar, so the row takes the status bar inset itself and carries the "Signals" pane title for TalkBack.
 */
@Composable
fun SignalsSubTabRow(selected: SignalsSubTab, onSelect: (SignalsSubTab) -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val title = stringResource(R.string.nav_signals)
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .semantics { paneTitle = title },
        contentAlignment = Alignment.TopCenter,
    ) {
        NsSegmentedToggle(
            options = SignalsSubTab.entries.map { SegmentedOption(label = resources.getString(it.label)) },
            selectedIndex = selected.ordinal,
            onSelected = { onSelect(SignalsSubTab.entries[it]) },
            style = SegmentedStyle.TABS,
            modifier = Modifier
                .widthIn(max = NsTheme.spacing.contentMaxWidth)
                .padding(horizontal = NsTheme.spacing.screenGutter),
        )
    }
}

/**
 * The line under the sub-tabs: the SIM on the left (Cellular), and how often it scans on the right,
 * replaced by a warning when something stops the scan or pauses the survey. Whether the view is
 * recorded is left to the Survey tab and the notification, so the strip takes one line; TalkBack
 * still reads the whole state as one sentence. It is not a live region, since the Survey tab
 * announces state changes. The SIM label sweeps once on each new scan.
 *
 * @param blocked What stops the view from showing anything ("Location is off"), which replaces the
 *                interval, or null.
 * @param sweepKey Changes with each scan, which plays the sweep.
 */
@Composable
fun ScanStrip(state: ScanStripState, modifier: Modifier = Modifier, blocked: String? = null, sweepKey: Any? = null) {
    val resources = LocalResources.current
    val left = state.sim?.let { SimLabels.label(resources, it) }
    val spoken = listOfNotNull(state.sim?.let { SimLabels.spoken(resources, it) }, blocked ?: stripSpoken(resources, state))
        .joinToString(". ")
    val warning = blocked ?: warningWord(resources, state.state)
    FlowRow(
        modifier = modifier
            .nsContentWidth()
            .padding(horizontal = NsTheme.spacing.screenGutter + NsTheme.spacing.xs, vertical = NsTheme.spacing.s)
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = if (left == null) {
            Arrangement.End
        } else {
            Arrangement.spacedBy(NsTheme.spacing.s, Alignment.Start)
        },
    ) {
        if (left != null) {
            NsScanSweep(sweepKey, Modifier.weight(1f)) {
                Text(
                    text = left,
                    style = NsTheme.textStyles.compact,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val right = warning ?: state.intervalSeconds?.let { resources.getString(R.string.signals_scan_interval, it) }
        if (right != null) {
            Text(
                text = right,
                style = NsTheme.textStyles.compact,
                color = if (warning != null) NsTheme.colors.status.warningText else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (warning != null) FontWeight.Medium else null,
            )
        }
    }
}

/** @return The state word for [state], as TalkBack reads it: Live view, Recording, Survey paused, or not in this survey. */
fun stateWord(resources: Resources, state: StripState): String = resources.getString(
    when (state) {
        StripState.LIVE -> R.string.signals_state_live
        StripState.RECORDING -> R.string.signals_state_recording
        StripState.NOT_IN_SURVEY -> R.string.signals_state_not_in_survey_spoken
        StripState.PAUSED -> R.string.signals_state_paused
    }
)

/**
 * @return The word the strip shows for [state], or null for none: only a paused survey is shown,
 * since its scans stop and the view would look stuck. Live, recording and not in this survey are
 * for the Survey tab and the notification to say.
 */
fun warningWord(resources: Resources, state: StripState): String? =
    if (state == StripState.PAUSED) resources.getString(R.string.signals_state_paused) else null

/** @return What TalkBack says for the right side: "Recording, scans every 5 seconds". */
fun stripSpoken(resources: Resources, state: ScanStripState): String {
    val word = stateWord(resources, state.state)
    return state.intervalSeconds?.let {
        resources.getQuantityString(R.plurals.signals_state_interval_spoken, it, word, it)
    } ?: word
}

/** How a SIM is named on the Signals tab, the Cellular SIM tabs and the map's SIM menu. */
object SimLabels {
    /** @return "SIM 1 · Mint", "SIM 2" when the carrier is unknown, or "No SIM". */
    @JvmStatic
    fun label(resources: Resources, sim: ViewedSim): String = when {
        sim.isNoSim -> resources.getString(R.string.signals_no_sim)
        sim.carrier.isNullOrBlank() -> resources.getString(R.string.signals_sim_no_carrier, sim.slot)
        else -> resources.getString(R.string.signals_sim, sim.slot, sim.carrier)
    }

    /**
     * @return The label of the SIM with [subscriptionId], looked up in the active subscriptions;
     * "SIM n" with the id when the phone does not say.
     */
    @JvmStatic
    @SuppressLint("MissingPermission") // READ_PHONE_STATE is required to run; a denial is caught
    fun forSubscription(context: Context, subscriptionId: Int): String {
        val info = try {
            context.getSystemService(SubscriptionManager::class.java)?.getActiveSubscriptionInfo(subscriptionId)
        } catch (e: SecurityException) {
            Timber.w(e, "Could not read the subscription for the SIM label")
            null
        }
        val sim = info?.let { ViewedSim(it.simSlotIndex + 1, subscriptionId, it.displayName?.toString()) }
            ?: ViewedSim(slot = subscriptionId, subscriptionId = subscriptionId, carrier = null)
        return label(context.resources, sim)
    }

    /** @return [label] without the separator, for TalkBack. */
    fun spoken(resources: Resources, sim: ViewedSim): String =
        if (sim.isNoSim || sim.carrier.isNullOrBlank()) {
            label(resources, sim)
        } else {
            resources.getString(R.string.signals_sim_spoken, sim.slot, sim.carrier)
        }
}
