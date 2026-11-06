package com.craxiom.networksurvey.ui.scout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.scout.link.ScoutLinkFailure
import com.craxiom.networksurvey.scout.model.NearbyScout
import com.craxiom.networksurvey.scout.model.ScoutPairingState
import com.craxiom.networksurvey.ui.common.NsIconAction
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.main.more.ExternalLinks
import com.craxiom.networksurvey.ui.scout.components.NearbyScoutList
import com.craxiom.networksurvey.ui.theme.NsTheme
import kotlinx.coroutines.delay

/** What the Add a Scout sheet can ask for. */
interface ScoutPairingActions : ScoutFixActions {
    fun onStartScan() {}
    fun onPair(nearby: NearbyScout) {}
    fun onResultShown(result: ScoutPairingState) {}
    fun onDismissPairing() {}
}

/**
 * Add a Scout: Scouts heard nearby with what each row can do and how close it is, the bonding
 * step (Android's own prompt), what to check when nothing shows up after 10 seconds, and why a
 * pairing failed. A success closes the sheet; the page then says the Scout is paired.
 *
 * @param pairedName The Scout already paired, so pairing another asks "Replace NS-ABCD?" first.
 * @param onReplace Asks to replace [pairedName] with a nearby Scout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoutPairingSheet(
    state: ScoutPairingUi,
    pairedName: String?,
    actions: ScoutPairingActions,
    onReplace: (NearbyScout) -> Unit,
) {
    val pairing = state.pairing
    ScanWhileResumed(pairing, actions)
    LaunchedEffect(pairing) {
        if (pairing is ScoutPairingState.Paired) {
            actions.onResultShown(pairing)
            actions.onDismissPairing()
        }
    }
    ModalBottomSheet(
        onDismissRequest = actions::onDismissPairing,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        ScoutPairingSheetBody(state, pairedName, actions, onReplace)
    }
}

/**
 * Starts the scan when the sheet opens. The page stops the scan when it pauses, so each later resume
 * starts it again, except while a pairing step shows: Android's pair prompt pauses the page, and a
 * failure stays up until the user acts on it. A scan never starts during a pairing.
 */
@Composable
private fun ScanWhileResumed(pairing: ScoutPairingState, actions: ScoutPairingActions) {
    val currentActions by rememberUpdatedState(actions)
    val currentPairing by rememberUpdatedState(pairing)
    var opened by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        val start = when (currentPairing) {
            ScoutPairingState.Idle -> true
            is ScoutPairingState.Pairing -> false
            else -> !opened
        }
        if (start) currentActions.onStartScan()
        opened = true
        onPauseOrDispose {}
    }
}

/** The sheet's title and its current step. */
@Composable
private fun ScoutPairingSheetBody(
    state: ScoutPairingUi,
    pairedName: String?,
    actions: ScoutPairingActions,
    onReplace: (NearbyScout) -> Unit,
) {
    val pairing = state.pairing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NsTheme.spacing.xl)
            .padding(bottom = NsTheme.spacing.xl)
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.scout_add),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            NsIconAction(R.drawable.ic_cancel, stringResource(R.string.scout_pair_close), actions::onDismissPairing)
        }
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = NsTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
        ) {
            when {
                pairing is ScoutPairingState.Pairing -> Bonding(pairing.name)
                pairing is ScoutPairingState.Failed -> Failed(pairing, actions)
                state.block != null -> Blocked(state.block, state.nearbyAccess, actions)
                else -> Nearby(state, pairedName, actions, onReplace)
            }
        }
    }
}

@Composable
private fun Nearby(state: ScoutPairingUi, pairedName: String?, actions: ScoutPairingActions, onReplace: (NearbyScout) -> Unit) {
    var checklist by rememberSaveable { mutableStateOf(false) }
    var waited by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(checklist) {
        if (!checklist) {
            waited = false
            delay(EMPTY_TIMEOUT_MS)
            waited = true
        }
    }
    if (checklist || (waited && state.nearby.isEmpty())) {
        Checklist {
            checklist = false
            actions.onStartScan()
        }
        return
    }
    if (state.scanning) {
        Row(horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(SPINNER), strokeWidth = SPINNER_STROKE)
            Text(stringResource(R.string.scout_pair_looking), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (state.nearby.isNotEmpty()) {
        NearbyScoutList(state.nearby, onPair = { nearby: NearbyScout ->
            if (pairedName != null && !nearby.pairedHere) onReplace(nearby) else actions.onPair(nearby)
        })
    }
    Text(stringResource(R.string.scout_pair_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    NsTextAction(stringResource(R.string.scout_pair_didnt_see), { checklist = true })
}

/** "Didn't see your Scout?": what to check, then Look again. */
@Composable
private fun Checklist(onLookAgain: () -> Unit) {
    Text(stringResource(R.string.scout_pair_didnt_see), style = MaterialTheme.typography.titleMedium)
    listOf(
        stringResource(R.string.scout_pair_check_software, ExternalLinks.NS_SCOUT_INSTALLER_HOST),
        stringResource(R.string.scout_pair_check_power),
        stringResource(R.string.scout_pair_check_close),
        stringResource(R.string.scout_pair_check_name),
        stringResource(R.string.scout_pair_check_boot),
    ).forEach { item ->
        Text(
            stringResource(R.string.scout_bullet, item),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SheetButton(stringResource(R.string.scout_pair_look_again), onLookAgain)
}

/** Android shows its own pair prompt now; say where to find it and what to leave off. */
@Composable
private fun Bonding(name: String) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(SPINNER), strokeWidth = SPINNER_STROKE)
        Text(stringResource(R.string.scout_pair_bonding, name), style = MaterialTheme.typography.bodyLarge)
    }
    Text(stringResource(R.string.scout_pair_contacts_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Failed(result: ScoutPairingState.Failed, actions: ScoutPairingActions) {
    val refused = result.failure == ScoutLinkFailure.PAIRING_REJECTED || result.failure == ScoutLinkFailure.TIMEOUT
    val bondMissing = result.failure == ScoutLinkFailure.BOND_MISSING
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
    ) {
        Text(
            stringResource(if (refused) R.string.scout_pair_refused_title else R.string.scout_pair_failed_title, result.name),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            when {
                refused -> stringResource(R.string.scout_pair_refused_body)
                bondMissing -> stringResource(R.string.scout_pair_bond_missing_body, result.name)
                else -> stringResource(R.string.scout_pair_failed_body)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
        SheetButton(stringResource(R.string.survey_action_try_again), {
            actions.onResultShown(result)
            actions.onStartScan()
        })
        if (bondMissing) NsTextAction(stringResource(R.string.scout_action_bluetooth_settings), actions::onOpenBluetoothSettings)
    }
}

/** Bluetooth off or the permission missing: say so, with the one fix. */
@Composable
private fun Blocked(block: ScoutScanBlock, nearbyAccess: PermissionAccess, actions: ScoutPairingActions) {
    val (text, action, onClick) = when (block) {
        ScoutScanBlock.PERMISSION -> Triple(scanPermissionText(), nearbyFixLabel(nearbyAccess), actions::onAllowNearby)
        ScoutScanBlock.BLUETOOTH_OFF -> Triple(R.string.scout_pair_needs_bluetooth, R.string.wifi_scout_action_turn_on_bluetooth, actions::onTurnOnBluetooth)
    }
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    SheetButton(stringResource(action), onClick)
}

@Composable
private fun SheetButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = NsTheme.spacing.touchTarget)) { Text(text) }
}

private const val EMPTY_TIMEOUT_MS = 10_000L
private val SPINNER = 20.dp
private val SPINNER_STROKE = 2.dp
