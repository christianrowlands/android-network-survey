package com.craxiom.networksurvey.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.ui.common.NsPrimaryCta
import com.craxiom.networksurvey.ui.survey.components.boldArguments
import com.craxiom.networksurvey.ui.survey.components.destinationNames
import com.craxiom.networksurvey.ui.survey.components.labelRes
import com.craxiom.networksurvey.ui.survey.components.recordTypeNames
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The one-time welcome for a phone that ran an earlier version, shown in place of first run: what
 * changed, and the survey it is set up for, in the same sentence the Survey tab shows.
 * It closes onto the Survey tab, where that setup can be reviewed. Back leaves the app, and the
 * welcome shows again next time, as first run does.
 */
@Composable
fun UpgradeWelcomeScreen(viewModel: OnboardingViewModel) {
    val setup = remember(viewModel) { viewModel.welcomeSetup() }
    UpgradeWelcomeContent(setup, viewModel::finishWelcome)
}

/** The welcome, stateless. */
@Composable
fun UpgradeWelcomeContent(setup: UpgradeWelcomeSetup, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = NsTheme.spacing.xxl, vertical = NsTheme.spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = MAX_CONTENT_WIDTH)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(CARD_GAP),
                ) {
                    OnboardingArt()
                    CardTitle(stringResource(R.string.upgrade_welcome_title))
                    CardBody(stringResource(R.string.upgrade_welcome_body))
                    YourSetup(setup)
                    CardBody(stringResource(R.string.upgrade_welcome_tabs))
                }
            }
            NsPrimaryCta(
                text = stringResource(R.string.upgrade_welcome_done),
                onClick = onDone,
                modifier = Modifier
                    .widthIn(max = MAX_CONTENT_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = NsTheme.spacing.screenGutter, vertical = NsTheme.spacing.m),
            )
        }
    }
}

/**
 * "Your survey": the setup as one sentence, whether it starts on its own, and how to use a saved
 * MQTT broker the survey does not send to. An MQTT stream without record types says it sends only
 * device status.
 */
@Composable
private fun YourSetup(setup: UpgradeWelcomeSetup) {
    if (setup.destinations.isEmpty() || (setup.types.isEmpty() && !setup.deviceStatusOnly)) return
    Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
        Text(
            text = stringResource(R.string.upgrade_welcome_setup_heading),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        val sentence = if (setup.deviceStatusOnly) {
            boldArguments(
                stringResource(R.string.upgrade_welcome_device_status_sentence),
                listOf(destinationNames(setup.destinations)),
            )
        } else {
            boldArguments(
                stringResource(R.string.survey_sentence),
                listOf(recordTypeNames(setup.types), destinationNames(setup.destinations)),
            )
        }
        Text(
            text = sentence,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (setup.autoStart) CardBody(stringResource(R.string.upgrade_welcome_auto_start))
        if (setup.mqttBrokerSaved) {
            CardBody(stringResource(R.string.upgrade_welcome_mqtt_saved, stringResource(Destination.MQTT.labelRes)))
        }
    }
}
