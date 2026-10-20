package com.craxiom.networksurvey.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.InlineNotice
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.common.NsRowDivider
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.common.SMALL_ICON
import com.craxiom.networksurvey.ui.theme.NsTheme
import androidx.compose.foundation.layout.size

/** Card 1: what the app does, in one sentence, under the picture. */
@Composable
fun IntroCard(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
        OnboardingArt()
        CardTitle(stringResource(R.string.onboarding_intro_title))
        CardBody(stringResource(R.string.onboarding_intro_body))
    }
}

/**
 * Card 2: each launch permission with its reason in plain words, plus a line with a way to turn
 * location on when it is off on the phone.
 */
@Composable
fun PermissionsCard(
    permissions: List<OnboardingPermission>,
    locationOff: Boolean,
    onTurnOnLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
        CardTitle(stringResource(R.string.onboarding_permissions_title))
        NsRowGroup {
            permissions.forEachIndexed { index, permission ->
                if (index > 0) NsRowDivider()
                NsRow(
                    title = stringResource(permission.title),
                    subtitle = stringResource(permission.reason),
                    icon = permission.icon,
                    role = null,
                )
            }
        }
        if (locationOff) {
            InlineNotice(
                text = stringResource(R.string.onboarding_location_off),
                icon = R.drawable.ic_location_off,
                color = NsTheme.colors.status.warningText,
            ) { NsTextAction(stringResource(R.string.survey_location_turn_on), onTurnOnLocation) }
        }
        Text(
            text = stringResource(R.string.onboarding_permissions_footnote),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Card 3: where the data goes, which sets the starting setup and ends first run. */
@Composable
fun DestinationCard(onChoose: (OnboardingChoice) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
        CardTitle(stringResource(R.string.survey_question_destinations))
        CardBody(stringResource(R.string.onboarding_destination_body))
        NsRowGroup {
            CHOICES.forEachIndexed { index, choice ->
                if (index > 0) NsRowDivider()
                NsRow(
                    title = stringResource(choice.title),
                    subtitle = stringResource(choice.summary),
                    icon = choice.icon,
                    onClick = { onChoose(choice.choice) },
                ) {
                    Icon(
                        painterResource(R.drawable.ic_chevron_right_small),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(SMALL_ICON),
                    )
                }
            }
        }
    }
}

@Composable
internal fun CardTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
    )
}

@Composable
internal fun CardBody(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private class ChoiceRow(val choice: OnboardingChoice, val title: Int, val summary: Int, val icon: Int)

private val CHOICES = listOf(
    ChoiceRow(
        OnboardingChoice.NS_ANALYTICS,
        R.string.onboarding_choice_ns_analytics,
        R.string.onboarding_choice_ns_analytics_summary,
        R.drawable.ic_cloud_done,
    ),
    ChoiceRow(
        OnboardingChoice.LOG_FILES,
        R.string.onboarding_choice_files,
        R.string.onboarding_choice_files_summary,
        R.drawable.ic_folder,
    ),
    ChoiceRow(
        OnboardingChoice.DECIDE_LATER,
        R.string.onboarding_choice_later,
        R.string.onboarding_choice_later_summary,
        R.drawable.ic_tune,
    ),
)

internal val CARD_GAP = NsTheme.spacing.l
