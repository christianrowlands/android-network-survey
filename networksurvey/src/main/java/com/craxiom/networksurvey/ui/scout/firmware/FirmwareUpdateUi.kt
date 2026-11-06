package com.craxiom.networksurvey.ui.scout.firmware

import com.craxiom.networksurvey.scout.firmware.FirmwareComparison
import com.craxiom.networksurvey.scout.firmware.FirmwareConsent
import com.craxiom.networksurvey.scout.firmware.FirmwareInstallPhase
import com.craxiom.networksurvey.scout.firmware.FirmwareInstallResult
import com.craxiom.networksurvey.scout.firmware.FirmwareOffer
import com.craxiom.networksurvey.scout.firmware.FirmwarePackage
import com.craxiom.networksurvey.scout.firmware.FirmwareUpdateError
import com.craxiom.networksurvey.ui.common.PermissionAccess

/** What stops an update from starting now, in the order the screen reports them. Each has one fix. */
enum class FirmwareBlocker {
    /** A survey records: updates are blocked so a Mission never loses the Scout halfway. */
    RECORDING,

    /** The Nearby devices permission is missing. */
    PERMISSION,

    BLUETOOTH_OFF,

    /** The Scout lost this phone's pairing; fixed on the Scout page. */
    PAIRING_LOST,

    /** Not connected yet; the screen keeps the Scout connected, so this clears on its own. */
    WAITING,
}

/** Where the firmware update is, as the screen shows it. */
sealed interface FirmwareStage {
    /** Fetching and checking the release manifest. */
    data object Checking : FirmwareStage

    /** The release for this Scout, waiting for Update. */
    data class Offer(val offer: FirmwareOffer) : FirmwareStage

    data class Downloading(val offer: FirmwareOffer) : FirmwareStage

    data class Installing(val pkg: FirmwarePackage, val phase: FirmwareInstallPhase, val cancelable: Boolean) : FirmwareStage

    data class Finished(val result: FirmwareInstallResult) : FirmwareStage

    /**
     * Something failed. [resumable] when the same package can continue where the transfer stopped.
     */
    data class Failed(val error: FirmwareUpdateError, val resumable: Boolean) : FirmwareStage
}

/**
 * What the firmware update screen draws.
 *
 * @property installedVersion The firmware the Scout runs, from its last link.
 * @property consents What the shown offer needs the user to accept explicitly.
 * @property accepted What the user accepted so far.
 */
data class FirmwareUpdateUi(
    val scoutName: String = "",
    val modelName: String = "",
    val installedVersion: String = "",
    val stage: FirmwareStage = FirmwareStage.Checking,
    val blocker: FirmwareBlocker? = null,
    /** Whether the Nearby devices permission can still be asked for, or only granted in Settings. */
    val nearbyAccess: PermissionAccess = PermissionAccess.AVAILABLE,
    val consents: List<FirmwareConsent> = emptyList(),
    val accepted: Set<FirmwareConsent> = emptySet(),
) {
    /** True once every consent the shown offer needs was accepted. */
    val allAccepted: Boolean get() = accepted.containsAll(consents)

    /** True while the update runs: the blocker is not shown and Back is held. */
    val installing: Boolean get() = stage is FirmwareStage.Installing || stage is FirmwareStage.Downloading

    /** True while the update can still stop: Back asks first. After OtaEnd nothing can stop it. */
    val cancelable: Boolean get() = stage is FirmwareStage.Downloading || (stage as? FirmwareStage.Installing)?.cancelable == true

    /** The offered or installed version, for the header's "0.1.0 → 0.2.0". */
    val targetVersion: String?
        get() = when (val current = stage) {
            is FirmwareStage.Offer -> current.offer.version
            is FirmwareStage.Downloading -> current.offer.version
            is FirmwareStage.Installing -> current.pkg.version
            is FirmwareStage.Finished -> current.result.newVersion
            else -> null
        }
}

/** What the firmware update screen can ask for. */
interface FirmwareUpdateActions {
    fun onBack() {}
    fun onUpdate() {}
    fun onAccept(consent: FirmwareConsent, accepted: Boolean) {}
    fun onCancel() {}
    fun onResume() {}
    fun onStartOver() {}
    fun onDone() {}
    fun onFixBlocker(blocker: FirmwareBlocker) {}
}

/** The consents a release offer needs before it downloads: a version that is not newer, a stale release. */
internal fun offerConsents(offer: FirmwareOffer): List<FirmwareConsent> = buildList {
    if (offer.comparison == FirmwareComparison.SAME || offer.comparison == FirmwareComparison.OLDER) {
        add(FirmwareConsent.NotNewer(offer.comparison))
    }
    offer.release.newerVersionSeen?.let { add(FirmwareConsent.StaleRelease(it)) }
}
