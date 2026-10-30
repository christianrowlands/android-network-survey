package com.craxiom.networksurvey.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsPrimaryCta
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The first run cards, in order. [next] and [previous] stop at the ends, because a second Continue
 * or Back (two key presses in one frame) can land before the footer and Back handler catch up.
 */
enum class OnboardingStep {
    INTRO, PERMISSIONS, DESTINATION;

    fun next(): OnboardingStep = entries.getOrElse(ordinal + 1) { this }

    fun previous(): OnboardingStep = entries.getOrElse(ordinal - 1) { this }
}

/** Everything the first run cards can ask for. */
interface OnboardingActions {
    fun onContinue()
    fun onAllowPermissions()
    fun onSkip()
    fun onTurnOnLocation()
    fun onChoose(choice: OnboardingChoice)
}

/**
 * First run, stateless: the current card scrolling above the step dots and, on the first two
 * cards, a bar with Skip and the card's main button. The content is capped in width and centered
 * on a wide screen.
 *
 * @param permissionsMissing True while any launch permission is missing; the permission card's
 *                           button then asks for them ("Allow and continue").
 */
@Composable
fun OnboardingContent(
    step: OnboardingStep,
    permissions: List<OnboardingPermission>,
    permissionsMissing: Boolean,
    locationOff: Boolean,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(TRANSITION_MS)) togetherWith fadeOut(tween(TRANSITION_MS)) },
                label = "onboardingStep",
                modifier = Modifier.weight(1f),
            ) { shown ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = NsTheme.spacing.xxl, vertical = NsTheme.spacing.l),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val cardModifier = Modifier
                        .widthIn(max = MAX_CONTENT_WIDTH)
                        .fillMaxWidth()
                    when (shown) {
                        OnboardingStep.INTRO -> IntroCard(cardModifier)
                        OnboardingStep.PERMISSIONS -> PermissionsCard(permissions, locationOff, actions::onTurnOnLocation, cardModifier)
                        OnboardingStep.DESTINATION -> DestinationCard(actions::onChoose, cardModifier)
                    }
                }
            }
            StepDots(step)
            if (step != OnboardingStep.DESTINATION) {
                Footer(step, permissionsMissing, actions)
            } else {
                Box(Modifier.height(NsTheme.spacing.l))
            }
        }
    }
}

@Composable
private fun Footer(step: OnboardingStep, permissionsMissing: Boolean, actions: OnboardingActions) {
    val asks = step == OnboardingStep.PERMISSIONS && permissionsMissing
    Row(
        modifier = Modifier
            .widthIn(max = MAX_CONTENT_WIDTH)
            .fillMaxWidth()
            .padding(horizontal = NsTheme.spacing.screenGutter, vertical = NsTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
    ) {
        TextButton(onClick = actions::onSkip) {
            Text(stringResource(R.string.onboarding_skip), style = MaterialTheme.typography.titleMedium)
        }
        NsPrimaryCta(
            text = stringResource(if (asks) R.string.onboarding_allow_continue else R.string.onboarding_continue),
            onClick = if (asks) actions::onAllowPermissions else actions::onContinue,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The step dots; the current one is a wider bar. TalkBack reads "Step 2 of 3". */
@Composable
private fun StepDots(step: OnboardingStep) {
    val description = stringResource(R.string.onboarding_step, step.ordinal + 1, OnboardingStep.entries.size)
    Row(
        modifier = Modifier
            .padding(top = NsTheme.spacing.s)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs + NsTheme.spacing.xxs),
    ) {
        OnboardingStep.entries.forEach {
            val current = it == step
            Box(
                Modifier
                    .width(if (current) DOT_CURRENT_WIDTH else DOT_SIZE)
                    .height(DOT_SIZE)
                    .clip(CircleShape)
                    .background(if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            )
        }
    }
}

private const val TRANSITION_MS = 200
internal val MAX_CONTENT_WIDTH = 560.dp
private val DOT_SIZE = 8.dp
private val DOT_CURRENT_WIDTH = 22.dp
