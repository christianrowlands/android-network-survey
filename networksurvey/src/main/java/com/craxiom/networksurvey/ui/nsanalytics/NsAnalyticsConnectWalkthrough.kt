package com.craxiom.networksurvey.ui.nsanalytics

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.NsSummaryText
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.common.annotateLink
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The NS Analytics page while this phone is not connected: what NS Analytics is, the three steps to
 * connect this phone to a workspace, and a link to the NS Analytics page on the website. The Scan
 * button, and the note about a refused camera, sit in the page's bottom bar. [onOpenUrl] opens the
 * web addresses on the page in the browser.
 */
@Composable
fun NsAnalyticsConnectWalkthrough(uploadFrequencyMinutes: Int, onOpenUrl: (String) -> Unit) {
    Intro()
    SectionHeader(stringResource(R.string.ns_analytics_how_to_connect))
    NsPanel {
        Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.l)) {
            Step(
                1,
                stringResource(R.string.ns_analytics_step1_title),
                step1Body(onOpenUrl),
            )
            Step(2, stringResource(R.string.ns_analytics_step2_title), AnnotatedString(stringResource(R.string.ns_analytics_step2_body)))
            Step(
                3,
                stringResource(R.string.ns_analytics_step3_title),
                AnnotatedString(pluralStringResource(R.plurals.ns_analytics_step3_body, uploadFrequencyMinutes, uploadFrequencyMinutes)),
            )
        }
    }
    NsSummaryText(stringResource(R.string.ns_analytics_connect_privacy))
    NsRowGroup {
        NsRow(
            title = stringResource(R.string.ns_analytics_learn_more),
            icon = R.drawable.ic_open_in_new,
            onClick = { onOpenUrl(NsAnalyticsConstants.NS_ANALYTICS_LEARN_MORE_URL) },
        )
    }
}

/** Step 1's text, with the web app's address in it as a link. */
@Composable
private fun step1Body(onOpenUrl: (String) -> Unit): AnnotatedString {
    val text = stringResource(R.string.ns_analytics_step1_body, NsAnalyticsConstants.NS_ANALYTICS_WEB_HOST)
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(text, linkColor, onOpenUrl) {
        annotateLink(text, NsAnalyticsConstants.NS_ANALYTICS_WEB_HOST, NsAnalyticsConstants.NS_ANALYTICS_WEB_URL, linkColor, onOpenUrl)
    }
}

/** What NS Analytics is and what a workspace gives a team, in a sentence and three short lines. */
@Composable
private fun Intro() {
    Text(
        text = stringResource(R.string.ns_analytics_intro),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    NsPanel {
        Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.m)) {
            Benefit(R.drawable.ic_map, stringResource(R.string.ns_analytics_benefit_map))
            Benefit(R.drawable.ic_bar_chart, stringResource(R.string.ns_analytics_benefit_analysis))
            Benefit(R.drawable.ic_cloud_connection, stringResource(R.string.ns_analytics_benefit_uploads))
        }
    }
}

@Composable
private fun Benefit(@DrawableRes icon: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m), verticalAlignment = Alignment.CenterVertically) {
        // As wide as a step's number badge, so the benefit text and the step text line up
        Box(Modifier.size(STEP_BADGE), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(BENEFIT_ICON),
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Step(number: Int, title: String, body: AnnotatedString) {
    Row(horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m), verticalAlignment = Alignment.Top) {
        Surface(
            shape = NsTheme.shapes.pill,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(STEP_BADGE),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(number.toString(), style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val STEP_BADGE = 26.dp
private val BENEFIT_ICON = 22.dp
