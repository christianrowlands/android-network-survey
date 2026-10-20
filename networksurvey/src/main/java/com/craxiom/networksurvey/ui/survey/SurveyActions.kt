package com.craxiom.networksurvey.ui.survey

import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.survey.model.AttentionItemUi

/**
 * Everything the Survey screen can ask for. The stateless [SurveyScreenContent] calls these; the
 * stateful screen wires them to the view model, navigation and system settings. Every method has
 * a no-op default so tests only override what they check.
 */
interface SurveyActions {
    fun onStart() {}
    fun onStop() {}
    fun onToggleRecordType(type: RecordType) {}
    fun onToggleDestination(destination: Destination) {}
    fun onOpenDestination(destination: Destination) {}
    fun onExplainPhoneState() {}
    fun onUseLogFiles() {}
    fun onConnectNsAnalytics() {}
    fun onTurnOnLocation() {}
    fun onAllowLocation() {}
    fun onUseOwnSettings() {}
    fun onRestoreOrganizationSettings() {}
    fun onCopyMissionId(missionId: String) {}
    fun onApplyDraft(types: Set<RecordType>, destinations: Set<Destination>) {}
    fun onSetDestinationShown(destination: Destination, shown: Boolean) {}
    fun onCancelCommunityUpload() {}
    fun onChangeBatteryThreshold() {}
    fun onAdjustQueueLimit() {}
    fun onDisableQueueLimit() {}
    fun onFixItem(item: AttentionItemUi) {}
    fun onOpenOrganizationSettings() {}
    fun onPickSetup(id: String) {}
    fun onSaveSetup(name: String) {}
    fun onRenameSetup(id: String, name: String) {}
    fun onStarSetup(id: String, starred: Boolean) {}
    fun onDeleteSetup(id: String) {}
    fun onUploadNow(destination: Destination) {}
    fun onOpenSettings() {}
}
