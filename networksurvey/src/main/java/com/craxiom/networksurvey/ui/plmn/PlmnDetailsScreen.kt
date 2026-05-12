package com.craxiom.networksurvey.ui.plmn

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.api.PlmnRecord
import com.craxiom.networksurvey.data.plmn.displayName
import com.craxiom.networksurvey.ui.theme.WifiTokens
import com.craxiom.networksurvey.ui.util.copyTextToClipboard
import com.craxiom.networksurvey.ui.wifi.components.CardHeader
import com.craxiom.networksurvey.ui.wifi.components.DetailsCardFrame

private val SectionGap = 14.dp
private val CardContentGap = 6.dp

/**
 * Details surface for a single [PlmnRecord]. Mirrors the card-stack layout of
 * [com.craxiom.networksurvey.ui.wifi.WifiDetailsScreen] and
 * [com.craxiom.networksurvey.ui.bluetooth.BluetoothDetailsScreen]: a hero card with the
 * primary identity (flag + brand-or-operator name + country) and three field cards
 * (Identifiers, Operator, Location). Inline subtitle hints explain short field meanings;
 * the top app bar's info button surfaces longer definitions in [PlmnInfoSheet].
 *
 * Container Scaffold + top app bar live in `AppNavigation.kt` to match the pattern used by
 * the other details screens.
 */
@Composable
fun PlmnDetailsScreen(
    record: PlmnRecord,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        HeroCard(record)
        IdentifiersCard(record)
        if (!record.brand.isNullOrBlank() || !record.operator.isNullOrBlank()) {
            OperatorCard(record)
        }
        LocationCard(record)
    }
}

@Composable
private fun HeroCard(record: PlmnRecord) {
    val country = record.country?.takeIf { it.isNotBlank() }
        ?: record.region?.takeIf { it.isNotBlank() }
    DetailsCardFrame {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = isoToFlag(record.iso),
                style = TextStyle(fontSize = 40.sp),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = record.displayName(),
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = WifiTokens.SsidAccent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (country != null) {
                    Text(
                        text = country,
                        style = TextStyle(fontSize = 14.sp),
                        color = WifiTokens.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            text = "${record.mcc}-${record.mnc} · ${
                stringResource(
                    R.string.plmn_lookup_plmn_label,
                    record.plmn
                )
            }",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
            color = WifiTokens.InkDim,
        )
    }
}

@Composable
private fun IdentifiersCard(record: PlmnRecord) {
    DetailsCardFrame {
        CardHeader(text = stringResource(R.string.plmn_details_section_identifiers))
        Column(verticalArrangement = Arrangement.spacedBy(CardContentGap)) {
            PlmnLabeledRow(
                label = stringResource(R.string.plmn_field_plmn),
                value = record.plmn,
                subtitle = stringResource(R.string.plmn_subtitle_plmn),
                monospace = true,
            )
            PlmnLabeledRow(
                label = stringResource(R.string.plmn_field_mcc),
                value = record.mcc,
                subtitle = stringResource(R.string.plmn_subtitle_mcc),
                monospace = true,
            )
            PlmnLabeledRow(
                label = stringResource(R.string.plmn_field_mnc),
                value = record.mnc,
                subtitle = stringResource(R.string.plmn_subtitle_mnc),
                monospace = true,
            )
            PlmnLabeledRow(
                label = stringResource(R.string.plmn_field_tadig),
                value = record.tadig,
                subtitle = stringResource(R.string.plmn_subtitle_tadig),
                monospace = true,
            )
        }
    }
}

@Composable
private fun OperatorCard(record: PlmnRecord) {
    val brand = record.brand?.takeIf { it.isNotBlank() }
    val operator = record.operator?.takeIf { it.isNotBlank() }
    val merged = brand != null && operator != null &&
            brand.trim().equals(operator.trim(), ignoreCase = true)

    DetailsCardFrame {
        CardHeader(text = stringResource(R.string.plmn_details_section_provider))
        Column(verticalArrangement = Arrangement.spacedBy(CardContentGap)) {
            when {
                merged -> PlmnLabeledRow(
                    label = stringResource(R.string.plmn_field_brand_operator),
                    value = brand,
                    subtitle = stringResource(R.string.plmn_subtitle_brand_operator),
                )

                else -> {
                    PlmnLabeledRow(
                        label = stringResource(R.string.plmn_field_brand),
                        value = brand,
                        subtitle = stringResource(R.string.plmn_subtitle_brand),
                    )
                    PlmnLabeledRow(
                        label = stringResource(R.string.plmn_field_operator),
                        value = operator,
                        subtitle = stringResource(R.string.plmn_subtitle_operator),
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationCard(record: PlmnRecord) {
    val region = record.region?.takeIf { it.isNotBlank() }
    DetailsCardFrame {
        CardHeader(text = stringResource(R.string.plmn_details_section_location))
        Column(verticalArrangement = Arrangement.spacedBy(CardContentGap)) {
            PlmnLabeledRow(
                label = stringResource(R.string.plmn_field_country),
                value = record.country,
                valueSecondary = record.iso?.takeIf { it.isNotBlank() },
            )
            if (region != null) {
                PlmnLabeledRow(
                    label = stringResource(R.string.plmn_field_region),
                    value = region,
                )
            }
        }
    }
}

/**
 * Two-line field row: label (with optional inline [subtitle]) on the left, value on the right.
 * Long-press on a populated value copies it to the clipboard, mirroring the row copy behavior
 * in the lookup list. Empty values render as the no-value placeholder in muted color.
 *
 * [valueSecondary] adds a faint monospace second line beneath the main value (e.g. the ISO
 * code under a country name). Suppressed when the main value is blank to avoid an orphan
 * tag floating below the "-" placeholder.
 *
 * Tap is intentionally not wired: a `combinedClickable` would add a ripple with no resolving
 * action, which reads as a broken affordance. Long-press is the documented copy gesture.
 */
@Composable
private fun PlmnLabeledRow(
    label: String,
    value: String?,
    subtitle: String? = null,
    valueSecondary: String? = null,
    monospace: Boolean = false,
) {
    val context = LocalContext.current
    val emptyPlaceholder = stringResource(R.string.plmn_details_no_value)
    val displayValue = value?.takeIf { it.isNotBlank() } ?: emptyPlaceholder
    val isEmpty = value.isNullOrBlank()
    val copyToast = stringResource(R.string.plmn_details_copied_toast)
    val secondaryToShow = if (isEmpty) null else valueSecondary?.takeIf { it.isNotBlank() }

    val valueModifier = if (!value.isNullOrBlank()) {
        Modifier.pointerInput(value) {
            detectTapGestures(
                onLongPress = { copyTextToClipboard(context, label, value, copyToast) }
            )
        }
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = TextStyle(fontSize = 11.sp),
                    color = WifiTokens.InkFaint,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1.4f)
                .padding(start = 12.dp)
                .semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = displayValue,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                    fontWeight = if (isEmpty) FontWeight.Normal else FontWeight.SemiBold,
                ),
                color = if (isEmpty) WifiTokens.InkFaint else MaterialTheme.colorScheme.onSurface,
                modifier = valueModifier,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondaryToShow != null) {
                Text(
                    text = secondaryToShow,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = WifiTokens.InkFaint,
                )
            }
        }
    }
}
