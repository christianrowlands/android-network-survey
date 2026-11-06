package com.craxiom.networksurvey.ui.survey.model

import com.craxiom.networksurvey.scout.ScoutWifiStatuses
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.survey.model.RecordType

/**
 * The Survey tab's NS Scout row, present only once a Scout is paired: in Ready and in Recording,
 * directly above the record types (never inside the setup panel, so Change never looks like it
 * edits the Scout).
 *
 * @property state The Scout's state, never [ScoutState.NotPaired].
 * @property recording True while a survey runs, for the Recording wording.
 * @property lastHeardAtMs When the Scout last sent a status, for "Last heard 2 minutes ago".
 */
data class ScoutRowUi(val state: ScoutState, val recording: Boolean, val lastHeardAtMs: Long?, val nowMs: Long) {
    /** The Scout's name, "NS-ABCD". */
    val scoutName: String get() = state.scout?.name.orEmpty()

    /** True when this phone is the Wi-Fi source: the row then offers "Use NS-ABCD". */
    val phoneIsSource: Boolean get() = state is ScoutState.NotInUse
}

/**
 * The NS Scout parts of [SurveyUiStateMapper]: the row, the Wi-Fi source name for the tiles,
 * chips and sentences, and the Scout plates. A Scout problem gets a plate of its own (with the
 * Scout's fixes) rather than a line in the "things need attention" plate; the notification keeps
 * listing it with the other left-out items.
 */
internal object ScoutSurveyMapper {

    /** @return The row, or null when no Scout is paired (or the organization doesn't allow one). */
    fun row(inputs: SurveyInputs, recording: Boolean): ScoutRowUi? =
        inputs.scout.takeIf { it.scout != null }?.let { ScoutRowUi(it, recording, inputs.scoutLastHeardAtMs, inputs.nowMs) }

    /** @return The paired Scout's name while this phone is the Wi-Fi source, for "Needs NS-ABCD", else null. */
    fun needsScoutName(inputs: SurveyInputs): String? =
        inputs.scout.takeIf { it is ScoutState.NotInUse }?.scout?.name

    /** @return The Scout's name while it is the Wi-Fi source, else null. */
    fun wifiSourceName(inputs: SurveyInputs): String? =
        inputs.scout.takeIf { it !is ScoutState.NotInUse }?.scout?.name

    /**
     * Ready: the Scout is the Wi-Fi source, Wi-Fi is selected and the Scout can't deliver it (after
     * its 30 s connect grace). Start is never blocked by it.
     */
    fun readyPlate(inputs: SurveyInputs, effectiveTypes: Set<RecordType>): AttentionPlateUi.Scout? {
        val name = wifiSourceName(inputs) ?: return null
        if (effectiveTypes.none { it == RecordType.WIFI || it.needsScout }) return null
        val outage = ScoutWifiStatuses.outage(inputs.scout) ?: return null
        val item = AttentionItemUi.Scout(
            name, outage,
            alsoBluetooth = RecordType.BLUETOOTH in effectiveTypes,
            types = SurveyUiStateMapper.ordered(effectiveTypes.filter { it == RecordType.WIFI || it.needsScout }),
        )
        return AttentionPlateUi.Scout(item, recording = false)
    }

    /**
     * Recording: splits what the running survey left out into the Scout's own plates and the rest.
     *
     * @return The Scout plates and the remaining items.
     */
    fun splitRecording(items: List<AttentionItemUi>): Pair<List<AttentionPlateUi>, List<AttentionItemUi>> {
        val (scout, others) = items.partition { it is AttentionItemUi.Scout }
        return scout.map { AttentionPlateUi.Scout(it as AttentionItemUi.Scout, recording = true) } to others
    }
}
