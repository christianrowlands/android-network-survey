package com.craxiom.networksurvey.scout.firmware

/**
 * Everything that can stop an NS Scout firmware update. The firmware update screen turns each into
 * a sentence (`FirmwareUpdateText`); [detail] is for the log only. A port of the iOS app's
 * `FirmwareUpdateError`.
 */
sealed class FirmwareUpdateError(val detail: String = "") : Exception(detail) {
    // Finding and checking the firmware
    class ManifestUnavailable(detail: String) : FirmwareUpdateError(detail)
    class ManifestInvalid(detail: String) : FirmwareUpdateError(detail)
    class ManifestSignatureInvalid : FirmwareUpdateError()
    class UnsupportedManifestFormat(val format: Int) : FirmwareUpdateError("format $format")
    class StaleRelease(val version: String, val minimum: String) : FirmwareUpdateError("$version below $minimum")
    class NoImageForBoard(val model: String) : FirmwareUpdateError(model)
    class DownloadFailed(detail: String) : FirmwareUpdateError(detail)
    class ImageCorrupt(detail: String) : FirmwareUpdateError(detail)
    class SignatureInvalid : FirmwareUpdateError()
    class WrongChip(val board: String, val image: String) : FirmwareUpdateError("board $board, image $image")
    class WrongBoard(val board: String, val image: String) : FirmwareUpdateError("board $board, image $image")

    // Talking to the Scout
    class SurveyRunning : FirmwareUpdateError()
    class BoardBusy : FirmwareUpdateError()
    class NotConnected : FirmwareUpdateError()
    class ConnectionLost : FirmwareUpdateError()
    class PairingLost : FirmwareUpdateError()
    class BoardRejected(detail: String) : FirmwareUpdateError(detail)
    class ImageTooLarge : FirmwareUpdateError()
    class FlashWriteFailed : FirmwareUpdateError()
    class VerifyFailed : FirmwareUpdateError()
    class ProtocolError(detail: String) : FirmwareUpdateError(detail)

    // After the restart
    class BoardDidNotReturn : FirmwareUpdateError()
    class RolledBack(val version: String) : FirmwareUpdateError(version)
    class NotConfirmed(val version: String) : FirmwareUpdateError(version)
    class UnexpectedVersion(val expected: String, val found: String) : FirmwareUpdateError("expected $expected, found $found")

    class Canceled : FirmwareUpdateError()

    /** True when the same image can continue: the board answers the same OtaBegin with where it stopped. */
    val isResumable: Boolean get() = this is ConnectionLost || this is BoardBusy || this is NotConnected

    override fun toString(): String = javaClass.simpleName + if (detail.isEmpty()) "" else ": $detail"
}
