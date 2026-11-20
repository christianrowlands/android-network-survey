package com.craxiom.networksurvey.ui.newtowers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import com.craxiom.networksurvey.ui.cellular.towermap.ProtocolBadge
import com.craxiom.networksurvey.ui.cellular.towermap.TowerProvider
import com.craxiom.networksurvey.ui.cellular.towermap.rememberTowerProvider
import com.craxiom.networksurvey.ui.common.NetworkTagVariant
import com.craxiom.networksurvey.ui.common.NsChevron
import com.craxiom.networksurvey.ui.common.NsNetworkTag
import com.craxiom.networksurvey.ui.common.relativeAgeText
import com.craxiom.networksurvey.ui.common.signalTextColor
import com.craxiom.networksurvey.ui.map.CELL_PART_SEPARATOR
import com.craxiom.networksurvey.ui.map.bandText
import com.craxiom.networksurvey.ui.map.cellIdentityText
import com.craxiom.networksurvey.ui.theme.NsTheme

/*
 * The New towers list row and the words it shares with the detail sheet: the provider name and
 * the band and identity line, worded like the Map's serving-cell card.
 */

/**
 * One tower on the New towers list: the technology badge and provider, the band and identifiers
 * ("Band 66 · eNB 129595 · Sector 3 · TAC 1234"), when it was found, and whether the tower
 * database has it now, with the signal at the find on the right. One TalkBack item that reads as
 * a sentence; a tap opens the detail sheet.
 */
@Composable
internal fun NewTowerRow(find: NewTowerFindEntity, nowMs: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val provider = providerName(find)
    val identityLine = identityLine(find)
    val found = stringResource(R.string.new_towers_found_ago, relativeAgeText(find.firstSeenAt, nowMs, midSentence = true))
    val signal = signalText(find)
    val inDatabase = stringResource(R.string.new_towers_in_database).takeIf { find.inDatabase }
    val spoken = listOfNotNull(find.radio, provider, identityLine, signal, found, inDatabase).joinToString(", ")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.new_towers_show_details), onClick = onClick)
            .semantics { contentDescription = spoken },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = NsTheme.spacing.touchTarget)
                .padding(horizontal = NsTheme.spacing.panelPaddingHorizontal, vertical = NsTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics {},
                verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
                    ProtocolBadge(find.radio)
                    Text(
                        text = provider,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                identityLine?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
                    verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = found, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inDatabase?.let { NsNetworkTag(text = it, variant = NetworkTagVariant.Standard) }
                }
            }
            Text(
                text = find.signalDbm?.toString() ?: stringResource(R.string.signal_none),
                style = NsTheme.textStyles.mono,
                fontWeight = FontWeight.Bold,
                color = signalTextColor(find.signalBucket),
                modifier = Modifier.clearAndSetSemantics {},
            )
            NsChevron(Modifier.clearAndSetSemantics {})
        }
    }
}

/**
 * The provider's name: from the PLMN lookup the tower sheet uses, else the carrier name on the
 * record, else the MCC-MNC.
 */
@Composable
internal fun providerName(find: NewTowerFindEntity): String {
    val provider = rememberTowerProvider(find.mcc, find.mnc)
    return (provider as? TowerProvider.Known)?.name
        ?: find.operatorName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.mcc_mnc_value, find.mcc, find.mnc)
}

/** "Band 66 · eNB 129595 · Sector 3 · TAC 1234", or null when there is nothing to say. */
@Composable
internal fun identityLine(find: NewTowerFindEntity): String? =
    listOfNotNull(
        bandText(find.lteBand, find.nrBands),
        cellIdentityText(find.enbSector, find.identity),
        stringResource(find.areaFormat, find.area),
    ).takeIf { it.isNotEmpty() }?.joinToString(CELL_PART_SEPARATOR)

/** "-95 dBm", or null when the find has no signal. */
@Composable
internal fun signalText(find: NewTowerFindEntity): String? =
    find.signalDbm?.let { stringResource(R.string.dbm_value, it.toString()) }
