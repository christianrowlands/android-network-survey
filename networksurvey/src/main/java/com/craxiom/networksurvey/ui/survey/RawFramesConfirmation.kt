package com.craxiom.networksurvey.ui.survey

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.survey.model.ItemLock
import com.craxiom.networksurvey.ui.survey.model.RecordTypeTileUi
import com.craxiom.networksurvey.ui.survey.model.SurveyUiState

/**
 * The privacy question before Raw frames is turned on: raw frames hold other people's addresses
 * and network names (protocol.md section 9 asks apps to warn first). It is asked wherever Raw
 * frames gets turned on: its tile, the Change sheet, and picking a saved setup that records it.
 */
object RawFramesConfirmation {

    /** The pending confirmation that came from the Raw frames tile (any other value is a setup ID). */
    const val FROM_TILE = ":raw-frames-tile"

    /**
     * @param before The record types before the change.
     * @param after  The record types the change asks for.
     * @param tiles  The tiles the screen offers; Raw frames must be offered and not set by the
     *               organization, so a phone without a Scout, or a managed one, is never asked.
     * @return True when the change turns Raw frames on, so the user must confirm first.
     */
    fun isNeeded(before: Set<RecordType>, after: Set<RecordType>, tiles: List<RecordTypeTileUi>): Boolean {
        val tile = tiles.firstOrNull { it.type == RecordType.WIFI_RAW } ?: return false
        return tile.lock == ItemLock.NONE && RecordType.WIFI_RAW in after && RecordType.WIFI_RAW !in before
    }
}

/**
 * The Survey screen's actions with the raw frames question in front of the Raw frames tile and of
 * picking a saved setup that records raw frames; [ask] shows the dialog with what is pending
 * ([RawFramesConfirmation.FROM_TILE] or the setup's ID), and [confirm] then does it.
 */
internal class RawFramesConfirmingActions(
    private val actions: SurveyActions,
    private val state: () -> SurveyUiState,
    private val ask: (String) -> Unit,
) : SurveyActions by actions {

    override fun onToggleRecordType(type: RecordType) {
        val current = state()
        val before = current.userSetup.recordTypes
        if (type == RecordType.WIFI_RAW && RawFramesConfirmation.isNeeded(before, before + type, current.tiles)) {
            ask(RawFramesConfirmation.FROM_TILE)
        } else {
            actions.onToggleRecordType(type)
        }
    }

    override fun onPickSetup(id: String) {
        val current = state()
        val setup = current.savedSetups?.setups?.firstOrNull { it.id == id }
        if (setup != null && RawFramesConfirmation.isNeeded(current.userSetup.recordTypes, setup.recordTypes, current.tiles)) {
            ask(id)
        } else {
            actions.onPickSetup(id)
        }
    }

    /** The user chose "Save raw frames" for [pending]. */
    fun confirm(pending: String) {
        if (pending == RawFramesConfirmation.FROM_TILE) {
            actions.onToggleRecordType(RecordType.WIFI_RAW)
        } else {
            actions.onPickSetup(pending)
        }
    }
}

/** "Save raw Wi-Fi frames?" with Save raw frames and Cancel. */
@Composable
fun RawFramesDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NsConfirmationDialog(
        title = stringResource(R.string.survey_raw_confirm_title),
        message = stringResource(R.string.survey_raw_confirm_body),
        confirmText = stringResource(R.string.survey_raw_confirm_action),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
