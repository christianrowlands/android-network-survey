package com.craxiom.networksurvey.ui.scout

import android.os.Build
import androidx.annotation.StringRes
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.scout.firmware.FirmwareReleaseStatus
import com.craxiom.networksurvey.scout.model.NearbyScout
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutLinkDetails
import com.craxiom.networksurvey.scout.model.ScoutPairingState
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.ui.common.PermissionAccess

/**
 * What the NS Scout page draws. With no Scout paired it explains the Scout and offers Add a Scout;
 * with one it shows its status, the Wi-Fi source, what it is doing and its details.
 *
 * @property allowed False when the organization doesn't allow NS Scout on this phone.
 * @property recording True while a survey runs: the source is locked and Remove is blocked.
 * @property stopNeedsConfirmation True when Stop needs the managed-survey confirmation, as on Survey.
 * @property setupsNeedingScout The saved setups that record a type only a Scout can record.
 * @property firmwareStatus How the Scout's firmware compares with the latest verified release.
 * @property firmwareUpdating True while a firmware update runs.
 * @property nearbyAccess Whether the Nearby devices permission can still be asked for.
 */
data class ScoutPageUi(
    val allowed: Boolean = true,
    val state: ScoutState = ScoutState.NotPaired,
    val details: ScoutLinkDetails = ScoutLinkDetails(),
    val source: WifiSource = WifiSource.PHONE,
    val recording: Boolean = false,
    val stopNeedsConfirmation: Boolean = false,
    val stopRestartsAtBoot: Boolean = false,
    val nowMs: Long = 0L,
    val setupsNeedingScout: List<SurveySetup> = emptyList(),
    val firmwareStatus: FirmwareReleaseStatus = FirmwareReleaseStatus.Unknown,
    val firmwareUpdating: Boolean = false,
    val nearbyAccess: PermissionAccess = PermissionAccess.AVAILABLE,
) {
    /** True once a Scout is paired: the page shows its status instead of the introduction. */
    val paired: Boolean get() = state.scout != null

    /** The paired Scout's name, or empty. */
    val scoutName: String get() = state.scout?.name.orEmpty()

    /** True while the Scout has a link that can take commands (Erase pairings). */
    val linkReady: Boolean get() = details.mtu > 0
}

/** What stops the pairing scan from running, in the order to fix them. */
enum class ScoutScanBlock {
    /** BLUETOOTH_SCAN (or location before Android 12) is missing. */
    PERMISSION,

    /** Bluetooth is off. */
    BLUETOOTH_OFF,
}

/**
 * The Add a Scout sheet: the Scouts heard nearby and the pairing in progress.
 *
 * @property block Why the scan can't run, null when it can.
 * @property nearbyAccess Whether the scan's permission can still be asked for.
 */
data class ScoutPairingUi(
    val nearby: List<NearbyScout> = emptyList(),
    val scanning: Boolean = false,
    val pairing: ScoutPairingState = ScoutPairingState.Idle,
    val block: ScoutScanBlock? = null,
    val nearbyAccess: PermissionAccess = PermissionAccess.AVAILABLE,
)

/** @return The label of the button that fixes a missing permission: Open settings once it is denied for good. */
@StringRes
internal fun nearbyFixLabel(access: PermissionAccess): Int =
    if (access == PermissionAccess.BLOCKED) R.string.open_settings else R.string.survey_action_allow

/** @return What the pairing sheet asks for: Android 12 added Nearby devices; before it the scan needs Location. */
@StringRes
internal fun scanPermissionText(sdk: Int = Build.VERSION.SDK_INT): Int =
    if (sdk >= Build.VERSION_CODES.S) R.string.scout_pair_needs_permission else R.string.scout_pair_needs_location

/** @return True when pairing [nearby] would replace [paired] during a survey, which never changes Scout. */
internal fun replacesWhileRecording(recording: Boolean, paired: PairedScout?, nearby: NearbyScout): Boolean =
    recording && paired != null && !nearby.pairedHere

/** One-shot messages the Scout page shows in its snackbar. */
sealed interface ScoutPageEvent {
    /** The Scout was removed; the snackbar offers Bluetooth settings so it can be forgotten there too. */
    data class Removed(val scoutName: String) : ScoutPageEvent

    /** The Scout erased its pairings and was removed. */
    data class Erased(val scoutName: String) : ScoutPageEvent

    /** The Scout didn't confirm the erase. */
    data class EraseFailed(val scoutName: String) : ScoutPageEvent

    /**
     * A Scout was paired. It is now the Wi-Fi source, unless a survey was recording
     * ([duringSurvey]): then the phone stays the source until the user switches after the survey.
     */
    data class Paired(val scoutName: String, val duringSurvey: Boolean = false) : ScoutPageEvent

    /** Wi-Fi now comes from [source]. */
    data class SourceChanged(val source: WifiSource, val scoutName: String) : ScoutPageEvent

    /** The Wi-Fi source can't change while a survey records. */
    data object LockedWhileRecording : ScoutPageEvent

    /** The Scout can't be removed while a survey records. */
    data object RemoveLockedWhileRecording : ScoutPageEvent

    /** The Scout's pairings can't be erased while a survey records. */
    data object EraseLockedWhileRecording : ScoutPageEvent

    /** The paired Scout can't be replaced by another while a survey records. */
    data object ReplaceLockedWhileRecording : ScoutPageEvent

    /** Erase pairings needs the link up. */
    data object NeedsLink : ScoutPageEvent
}

/**
 * The saved setups that record a type only an NS Scout can record ([RecordType.needsScout]), for
 * Remove Scout ("2 saved setups record Wi-Fi clients or Raw frames"). Empty until such types exist.
 */
object ScoutSetups {
    /** The record types only a Scout can record. */
    val SCOUT_ONLY_TYPES: List<RecordType> get() = RecordType.entries.filter { it.needsScout }

    /** @return The setups in [setups] that record at least one Scout-only type. */
    @JvmStatic
    fun needingScout(setups: List<SurveySetup>): List<SurveySetup> =
        setups.filter { setup -> setup.recordTypes.any { it.needsScout } }
}
