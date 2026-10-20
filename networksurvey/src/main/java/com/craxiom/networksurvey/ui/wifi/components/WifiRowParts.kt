package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.WifiBand

/** A copy action a Wi-Fi row offers on long press and to TalkBack ("Copy SSID", "Copy BSSID"). */
data class WifiCopyAction(val label: String, val onCopy: () -> Unit)

/**
 * The network name at the start of a Wi-Fi row: the SSID in the accent color, or a muted italic
 * "<Hidden SSID>"; a long press copies a visible SSID.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WifiSsidText(
    ssid: String,
    hidden: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (hidden) LocalResources.current.getString(R.string.wifi_hidden_ssid_placeholder) else ssid,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        fontStyle = if (hidden) FontStyle.Italic else FontStyle.Normal,
        color = if (hidden) NsTheme.colors.tag.hiddenSsid else MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress),
    )
}

/**
 * Makes a Wi-Fi row one TalkBack item: a tap with [clickLabel], the spoken summary, and the copy
 * actions as custom actions (the visible long presses on the SSID and BSSID stay for touch). The
 * row's content must clear its own semantics so it is not read twice.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.wifiRowSemantics(
    spoken: String,
    clickLabel: String,
    onClick: () -> Unit,
    copyActions: List<WifiCopyAction>,
): Modifier = combinedClickable(onClickLabel = clickLabel, onClick = onClick)
    .semantics {
        contentDescription = spoken
        customActions = copyActions.map { action ->
            CustomAccessibilityAction(action.label) {
                action.onCopy()
                true
            }
        }
    }

/** @return "5 GHz", or "5 GHz, channel 36", as TalkBack should read a band chip. */
@Composable
internal fun spokenBand(band: WifiBand?, channel: Int?): String? {
    if (band == null) return null
    val resources = LocalResources.current
    val label = resources.getString(band.labelRes)
    return if (channel != null) resources.getString(R.string.wifi_row_band_channel, label, channel) else label
}

/** @return "-52 dBm", or "No signal". */
@Composable
internal fun spokenSignal(rssi: Int?): String {
    val resources = LocalResources.current
    return rssi?.let { resources.getString(R.string.dbm_value, it.toString()) }
        ?: resources.getString(R.string.wifi_no_signal_description)
}
