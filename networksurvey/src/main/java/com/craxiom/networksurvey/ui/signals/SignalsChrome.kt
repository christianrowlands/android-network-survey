package com.craxiom.networksurvey.ui.signals

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.telephony.SubscriptionManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsSegmentedToggle
import com.craxiom.networksurvey.ui.common.SegmentedOption
import com.craxiom.networksurvey.ui.common.SegmentedStyle
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.theme.NsTheme
import timber.log.Timber

/** The Cellular · Wi-Fi · Bluetooth · GNSS tab row under the Signals title. */
@Composable
fun SignalsSubTabRow(selected: SignalsSubTab, onSelect: (SignalsSubTab) -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
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
 * The line under the sub-tabs: the SIM on the left (Cellular), and on the right whether what is on
 * screen is being recorded and how often it scans. TalkBack reads it as one sentence; it is not a
 * live region, since the Survey tab announces state changes.
 *
 * @param blocked What stops the view from showing anything ("Location is off"), which replaces the
 *                state word, or null.
 */
@Composable
fun ScanStrip(state: ScanStripState, modifier: Modifier = Modifier, blocked: String? = null) {
    val resources = LocalResources.current
    val left = state.sim?.let { SimLabels.label(resources, it) }
    val spoken = listOfNotNull(state.sim?.let { SimLabels.spoken(resources, it) }, blocked ?: stripSpoken(resources, state))
        .joinToString(". ")
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
            Text(
                text = left,
                style = NsTheme.textStyles.compact,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        val emphasis = MaterialTheme.colorScheme.onSurface
        Text(
            text = if (blocked != null) {
                buildAnnotatedString { withStyle(SpanStyle(color = emphasis, fontWeight = FontWeight.Medium)) { append(blocked) } }
            } else {
                stripText(resources, state, emphasis)
            },
            style = NsTheme.textStyles.compact,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** @return The state word for [state]: Live view, Recording, Paused, or not in this survey. */
fun stateWord(resources: Resources, state: StripState): String = resources.getString(
    when (state) {
        StripState.LIVE -> R.string.signals_state_live
        StripState.RECORDING -> R.string.signals_state_recording
        StripState.NOT_IN_SURVEY -> R.string.signals_state_not_in_survey
        StripState.PAUSED -> R.string.signals_state_paused
    }
)

/** @return "Recording · scan every 5 s" with the state word emphasized. */
fun stripText(resources: Resources, state: ScanStripState, emphasis: Color): AnnotatedString {
    val word = stateWord(resources, state.state)
    val text = state.intervalSeconds?.let { resources.getString(R.string.signals_state_interval, word, it) } ?: word
    return buildAnnotatedString {
        val start = text.indexOf(word)
        if (start < 0) {
            append(text)
        } else {
            append(text.substring(0, start))
            withStyle(SpanStyle(color = emphasis, fontWeight = FontWeight.Medium)) { append(word) }
            append(text.substring(start + word.length))
        }
    }
}

/** @return What TalkBack says for the right side: "Recording, scans every 5 seconds". */
fun stripSpoken(resources: Resources, state: ScanStripState): String {
    val word = if (state.state == StripState.NOT_IN_SURVEY) {
        resources.getString(R.string.signals_state_not_in_survey_spoken)
    } else {
        stateWord(resources, state.state)
    }
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
