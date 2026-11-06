package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.ScoutWifiDetails
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.model.WifiNetworkScout
import com.craxiom.networksurvey.ui.common.NsCardHeader
import com.craxiom.networksurvey.ui.common.NsDetailsCardFrame
import com.craxiom.networksurvey.ui.common.NsValueCell
import com.craxiom.networksurvey.ui.common.NsValueGrid
import com.craxiom.networksurvey.ui.common.SMALL_ICON
import com.craxiom.networksurvey.ui.common.securityLabelText
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.wifi.model.ScoutApText
import kotlin.math.roundToInt

/*
 * The Wi-Fi details parts only an NS Scout can fill: the "Heard by" chip, the Security and Load
 * cards and the extra Radio rows. Each shows only what the Scout actually heard, so a value it did
 * not hear never reads as an empty row.
 */

/** One value and its label in a Scout card. */
private data class ScoutValue(val value: String, val label: String)

/** "Heard by NS-ABCD", with the Scout's name in monospace like every other identifier. */
@Composable
fun WifiHeardByChip(scoutName: String, modifier: Modifier = Modifier) {
    val text = stringResource(R.string.wifi_details_heard_by, scoutName)
    val start = text.indexOf(scoutName)
    Surface(modifier = modifier, shape = NsTheme.shapes.chip, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.padding(horizontal = NsTheme.spacing.s, vertical = NsTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
        ) {
            Icon(
                painterResource(R.drawable.ic_sensors),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SMALL_ICON),
            )
            Text(
                text = buildAnnotatedString {
                    append(text)
                    if (start >= 0) addStyle(SpanStyle(fontFamily = NsTheme.textStyles.mono.fontFamily), start, start + scoutName.length)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * The Security card for an access point a Scout heard, in place of Android's raw capabilities
 * string: encryption (Personal or Enterprise), key management, the ciphers and WPS.
 */
@Composable
fun WifiSecurityCard(network: WifiNetwork, scout: WifiNetworkScout, modifier: Modifier = Modifier) {
    val enterprise = ScoutApText.isEnterprise(scout.akmSuites)
    val values = buildList {
        if (network.encryptionType.isNotBlank()) {
            val kind = when (enterprise) {
                true -> R.string.wifi_details_security_enterprise
                false -> R.string.wifi_details_security_personal
                null -> R.string.wifi_details_security_encryption
            }
            add(ScoutValue(securityLabelText(network.encryptionType), stringResource(kind)))
        }
        ScoutApText.akmNames(scout.akmSuites).takeIf { it.isNotEmpty() }?.let {
            add(ScoutValue(it.joinToString(SUITE_SEPARATOR), stringResource(R.string.wifi_details_key_management)))
        }
        addAll(cipherValues(scout))
        scout.wps?.let {
            add(ScoutValue(stringResource(if (it) R.string.wifi_details_wps_on else R.string.wifi_details_wps_off), stringResource(R.string.wifi_details_wps)))
        }
    }
    if (values.isEmpty()) return
    ScoutCard(stringResource(R.string.wifi_details_security), null, values, modifier)
}

/** The Load card, from the AP's BSS Load element; nothing when the AP sends none. */
@Composable
fun WifiLoadCard(details: ScoutWifiDetails, modifier: Modifier = Modifier) {
    val values = listOfNotNull(
        details.stationCount?.let { ScoutValue(it.toString(), stringResource(R.string.wifi_details_stations)) },
        details.channelUtilization?.let {
            ScoutValue(stringResource(R.string.wifi_details_percent, ScoutApText.channelUsePercent(it)), stringResource(R.string.wifi_details_channel_use))
        },
    )
    if (values.isEmpty()) return
    ScoutCard(stringResource(R.string.wifi_details_load), stringResource(R.string.wifi_details_load_reported), values, modifier)
}

/** The Radio card's extra rows for a Scout record: beacon interval and SNR, then the Wi-Fi generations it supports. */
@Composable
internal fun WifiScoutRadioRows(scout: WifiNetworkScout) {
    val interval = scout.beaconIntervalTu?.let { tu ->
        RadioValue(
            label = stringResource(R.string.wifi_details_beacon_interval),
            primary = ScoutApText.beaconIntervalMs(tu).toString(),
            suffix = stringResource(R.string.wifi_details_unit_ms),
        )
    }
    val snr = scout.snrDb?.let { snrDb ->
        RadioValue(
            label = stringResource(R.string.wifi_details_snr),
            primary = snrDb.roundToInt().toString(),
            suffix = stringResource(R.string.wifi_details_unit_db),
            below = scout.details.noiseFloorDbm?.let { stringResource(R.string.wifi_details_noise, it) },
        )
    }
    RadioRow(listOfNotNull(interval, snr))
    val generations = ScoutApText.generations(scout.details.phyFlags)
    if (generations.isNotEmpty()) {
        RadioRow(
            listOf(
                RadioValue(
                    label = stringResource(R.string.wifi_details_generations),
                    primary = generations.joinToString(SUITE_SEPARATOR),
                    prefix = stringResource(R.string.wifi_details_generation_prefix),
                ),
            ),
        )
    }
}

/** One radio cell's words; [below] is a muted line under the value (the noise floor under SNR). */
private data class RadioValue(
    val label: String,
    val primary: String,
    val suffix: String? = null,
    val prefix: String? = null,
    val below: String? = null,
)

/** Up to two radio cells side by side; a missing one leaves its half empty so the columns line up with the grid above. */
@Composable
private fun RadioRow(values: List<RadioValue>) {
    if (values.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.l)) {
        values.forEach { value ->
            RadioStatCell(
                label = value.label,
                primary = value.primary,
                suffix = value.suffix,
                prefix = value.prefix,
                modifier = Modifier.weight(1f),
                belowContent = value.below?.let { line ->
                    { Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                },
            )
        }
        if (values.size == 1) Spacer(Modifier.weight(1f))
    }
}

/** Pairwise and group ciphers when the Scout told them apart, otherwise the record's merged list. */
@Composable
private fun cipherValues(scout: WifiNetworkScout): List<ScoutValue> {
    val pairwise = ScoutApText.cipherNames(scout.details.pairwiseCiphers)
    val group = ScoutApText.cipherNames(listOfNotNull(scout.details.groupCipher))
    if (pairwise.isNotEmpty() || group.isNotEmpty()) {
        return listOfNotNull(
            pairwise.takeIf { it.isNotEmpty() }?.let { ScoutValue(it.joinToString(SUITE_SEPARATOR), stringResource(R.string.wifi_details_pairwise_cipher)) },
            group.firstOrNull()?.let { ScoutValue(it, stringResource(R.string.wifi_details_group_cipher)) },
        )
    }
    val all = ScoutApText.cipherNames(scout.cipherSuites)
    return if (all.isEmpty()) emptyList() else listOf(ScoutValue(all.joinToString(SUITE_SEPARATOR), stringResource(R.string.wifi_details_ciphers)))
}

@Composable
private fun ScoutCard(title: String, note: String?, values: List<ScoutValue>, modifier: Modifier) {
    NsDetailsCardFrame(modifier = modifier) {
        NsCardHeader(text = title) {
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        NsValueGrid(values, maxColumns = 2) { item, cellModifier -> NsValueCell(item.value, item.label, cellModifier) }
    }
}

private const val SUITE_SEPARATOR = ", "
