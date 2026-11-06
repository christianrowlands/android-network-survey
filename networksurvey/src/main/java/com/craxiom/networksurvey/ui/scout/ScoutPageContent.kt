package com.craxiom.networksurvey.ui.scout

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.scout.model.NearbyScout
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.ui.common.NsCtaBar
import com.craxiom.networksurvey.ui.common.NsEmptyState
import com.craxiom.networksurvey.ui.common.NsPageScaffold
import com.craxiom.networksurvey.ui.common.NsPrimaryCta
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.survey.sheets.StopManagedSurveyDialog

/** Everything the NS Scout page can ask for. */
interface ScoutPageActions : ScoutPairedActions, ScoutPairingActions {
    fun onBack() {}
    fun onShareInstallLink() {}
    fun onOpenScoutSite() {}
}

/** Which dialog or sheet the Scout page is showing. */
enum class ScoutOverlay { NONE, PAIRING, REMOVE, ERASE, STOP }

/**
 * The NS Scout page, stateless. Without a Scout it explains the Scout and offers Add a Scout; with
 * one it shows the Scout. Remove, Erase and a managed Stop ask first; a Remove or Erase while
 * recording, or an Erase without a link, goes straight to the view model, which says why not.
 */
@Composable
fun ScoutPageContent(
    state: ScoutPageUi,
    pairing: ScoutPairingUi,
    actions: ScoutPageActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    initialOverlay: ScoutOverlay = ScoutOverlay.NONE,
) {
    var overlay by rememberSaveable { mutableStateOf(initialOverlay) }
    var replacing by remember { mutableStateOf<NearbyScout?>(null) }
    val close = { overlay = ScoutOverlay.NONE }
    val paired = pagePairedActions(state, actions) { overlay = it }

    NsPageScaffold(
        title = stringResource(R.string.scout_page_title),
        onBack = actions::onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        bottomBar = {
            if (state.allowed && !state.paired) {
                NsCtaBar {
                    NsPrimaryCta(stringResource(R.string.scout_add), { overlay = ScoutOverlay.PAIRING }, icon = R.drawable.ic_add)
                }
            }
        },
    ) {
        when {
            !state.allowed -> NsRowGroup { NsEmptyState(stringResource(R.string.scout_organization_off), R.drawable.ic_domain) }
            state.paired -> ScoutPairedContent(state, paired)
            else -> ScoutIntroContent(actions::onShareInstallLink, actions::onOpenScoutSite)
        }
    }

    when (overlay) {
        ScoutOverlay.NONE -> Unit
        ScoutOverlay.PAIRING -> ScoutPairingSheet(
            state = pairing,
            pairedName = state.scoutName.takeIf { state.paired },
            actions = object : ScoutPairingActions by actions {
                override fun onDismissPairing() {
                    actions.onDismissPairing()
                    close()
                }
            },
            onReplace = { nearby ->
                if (state.recording) {
                    // Straight to the view model, which says why not; the snackbar shows once the sheet closes
                    actions.onPair(nearby)
                    actions.onDismissPairing()
                    close()
                } else {
                    replacing = nearby
                }
            },
        )

        ScoutOverlay.REMOVE -> RemoveScoutDialog(state.scoutName, state.setupsNeedingScout.size, actions::onRemove, close)
        ScoutOverlay.ERASE -> EraseScoutDialog(state.scoutName, actions::onErase, close)
        ScoutOverlay.STOP -> StopManagedSurveyDialog(state.stopRestartsAtBoot, actions::onStopSurvey, close)
    }
    replacing?.let { nearby ->
        ReplaceScoutDialog(state.scoutName, nearby.name, onConfirm = { actions.onPair(nearby) }, onDismiss = { replacing = null })
    }
}

/**
 * The paired page's actions with the confirmations in front: the dialog first, unless the view
 * model has to say why the action can't run now.
 */
private fun pagePairedActions(state: ScoutPageUi, actions: ScoutPageActions, show: (ScoutOverlay) -> Unit) =
    object : ScoutPairedActions by actions {
        override fun onSetSource(source: WifiSource) = actions.onSetSource(source)
        override fun onPairAgain() = show(ScoutOverlay.PAIRING)
        override fun onRemove() = if (state.recording) actions.onRemove() else show(ScoutOverlay.REMOVE)
        override fun onErase() = if (state.recording || !state.linkReady) actions.onErase() else show(ScoutOverlay.ERASE)
        override fun onStopSurvey() = if (state.stopNeedsConfirmation) show(ScoutOverlay.STOP) else actions.onStopSurvey()
    }
