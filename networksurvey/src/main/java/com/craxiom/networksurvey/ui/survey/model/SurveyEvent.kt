package com.craxiom.networksurvey.ui.survey.model

import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType

/** One-shot messages the Survey screen shows in its snackbar. */
sealed interface SurveyEvent {
    /** The survey started; [skipped] destinations or record types could not. */
    data class Started(val missionId: String?, val skipped: Int) : SurveyEvent

    /** Nothing could start; [problems] says why for each item. */
    data class CouldNotStart(val problems: List<AttentionItemUi>) : SurveyEvent

    data class Stopped(val records: Long) : SurveyEvent

    data object Applied : SurveyEvent

    /** The organization changed the running survey. */
    data class OrganizationChanged(
        val addedTypes: List<RecordType>,
        val removedTypes: List<RecordType>,
        val addedDestinations: List<Destination>,
        val removedDestinations: List<Destination>,
    ) : SurveyEvent

    /** The organization's change left nothing valid, so the survey stopped. */
    data object OrganizationStopped : SurveyEvent

    /** Phone state can't be turned off on its own while Cellular is on. */
    data object PhoneStateFollowsCellular : SurveyEvent

    data object MissionIdCopied : SurveyEvent

    /** "Save current" saved the selection as [name]. */
    data class SetupSaved(val name: String) : SurveyEvent

    /** Upload now was tapped while offline; the upload runs once the phone is back online. */
    data object UploadWaitsForNetwork : SurveyEvent

    /** Upload now was tapped while offline, and this upload can't wait for the network. */
    data object UploadNeedsNetwork : SurveyEvent

    /** The organization doesn't allow NS Analytics uploads on this phone. */
    data object UploadBlockedByOrganization : SurveyEvent
}
