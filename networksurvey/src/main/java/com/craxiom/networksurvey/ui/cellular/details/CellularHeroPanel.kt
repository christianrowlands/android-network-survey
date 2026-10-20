package com.craxiom.networksurvey.ui.cellular.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.ui.cellular.details.model.CellularHero
import com.craxiom.networksurvey.ui.cellular.details.model.CellularPills
import com.craxiom.networksurvey.ui.cellular.details.model.HeroKind
import com.craxiom.networksurvey.ui.cellular.details.model.HeroTone
import com.craxiom.networksurvey.ui.common.NsLabeledPill
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.copyOnLongPress
import com.craxiom.networksurvey.ui.theme.NsTheme

/** Joins a technology and its qualifier ("LTE + NR · NSA"). */
private const val QUALIFIER_SEPARATOR = " · "

/**
 * The top of the Cellular view: the carrier, the technology the phone is using in large type, and
 * the Voice, Data and Branding pills. A bad state never looks like a good one: No service is in
 * the warning color and Unknown is muted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CellularHeroPanel(
    carrier: String?,
    hero: CellularHero?,
    pills: CellularPills?,
    onInfo: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NsPanel(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = carrier?.takeIf { it.isNotBlank() } ?: stringResource(R.string.carrier_unknown),
                    style = NsTheme.textStyles.compact,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hero != null) HeroLine(hero, onCopy)
            }
            InfoButton(stringResource(R.string.network_technology_icon_description), onInfo)
        }
        if (pills != null) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = NsTheme.spacing.s),
                horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
                verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
            ) {
                NsLabeledPill(stringResource(R.string.pill_label_voice), pillValueText(pills.voice))
                pills.data?.let { NsLabeledPill(stringResource(R.string.pill_label_data), pillValueText(it)) }
                NsLabeledPill(stringResource(R.string.pill_label_branding), pills.branding ?: stringResource(R.string.branding_none))
            }
        }
    }
}

@Composable
private fun HeroLine(hero: CellularHero, onCopy: (String, String) -> Unit) {
    val (technology, qualifier) = heroParts(hero)
    val color = when (hero.tone) {
        HeroTone.LIVE -> MaterialTheme.colorScheme.onSurface
        HeroTone.WARNING -> NsTheme.colors.status.warningText
        HeroTone.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = buildAnnotatedString {
        append(technology)
        if (qualifier != null) {
            withStyle(MaterialTheme.typography.titleLarge.toSpanStyle().merge(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant))) {
                append(QUALIFIER_SEPARATOR)
                append(qualifier)
            }
        }
    }
    val label = stringResource(R.string.network_technology_title)
    Text(
        text = text,
        style = NsTheme.textStyles.stateHeadline,
        color = color,
        modifier = Modifier
            .padding(top = NsTheme.spacing.xxs)
            .copyOnLongPress(stringResource(R.string.cellular_copy_action), text.text) { onCopy(label, text.text) },
    )
}

/** @return The technology and its smaller qualifier: ("NR", "Standalone"), ("LTE + NR", "NSA"). */
@Composable
private fun heroParts(hero: CellularHero): Pair<String, String?> = when (hero.kind) {
    HeroKind.NR_STANDALONE -> NetworkSurveyConstants.NR to stringResource(R.string.nr_mode_standalone)
    HeroKind.NR_NON_STANDALONE -> "${NetworkSurveyConstants.LTE} + ${NetworkSurveyConstants.NR}" to NetworkSurveyConstants.NSA
    HeroKind.DATA_RAT -> (hero.ratName ?: stringResource(R.string.unknown)) to null
    HeroKind.NO_SERVICE -> stringResource(R.string.hero_no_service) to null
    HeroKind.UNKNOWN -> stringResource(R.string.unknown) to null
}

/** A panel's title row: the title as a heading, an optional muted value, and an info button. */
@Composable
fun CellularPanelHeader(
    title: String,
    trailing: String?,
    infoDescription: String,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        InfoButton(infoDescription, onInfo)
    }
}

@Composable
private fun InfoButton(description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A pill value; the controller's English "Unknown" is shown in the phone's language. */
@Composable
private fun pillValueText(value: String): String =
    if (value == NetworkSurveyConstants.UNKNOWN) stringResource(R.string.unknown) else value
