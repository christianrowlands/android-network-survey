package com.craxiom.networksurvey.ui.scout.firmware

import android.content.res.Resources
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.scout.firmware.FirmwareComparison
import com.craxiom.networksurvey.scout.firmware.FirmwareConsent
import com.craxiom.networksurvey.scout.firmware.FirmwareUpdateError
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/**
 * The words of the firmware update screen that depend on what happened: error messages, consents
 * and the release date. Plain functions over [Resources] so they are tested.
 */
object FirmwareUpdateText {

    /** @return The sentence for [error] about the Scout [name]. */
    @JvmStatic
    fun error(resources: Resources, error: FirmwareUpdateError, name: String): String = when (error) {
        is FirmwareUpdateError.ManifestUnavailable -> resources.getString(R.string.scout_fw_error_manifest_unavailable)
        is FirmwareUpdateError.ManifestInvalid -> resources.getString(R.string.scout_fw_error_manifest_invalid)
        is FirmwareUpdateError.ManifestSignatureInvalid -> resources.getString(R.string.scout_fw_error_manifest_signature)
        is FirmwareUpdateError.UnsupportedManifestFormat -> resources.getString(R.string.scout_fw_error_format)
        is FirmwareUpdateError.StaleRelease -> resources.getString(R.string.scout_fw_error_stale, error.version, error.minimum)
        is FirmwareUpdateError.NoImageForBoard -> resources.getString(R.string.scout_fw_error_no_image, error.model)
        is FirmwareUpdateError.DownloadFailed -> resources.getString(R.string.scout_fw_error_download)
        is FirmwareUpdateError.ImageCorrupt -> resources.getString(R.string.scout_fw_error_corrupt)
        is FirmwareUpdateError.SignatureInvalid -> resources.getString(R.string.scout_fw_error_signature)
        is FirmwareUpdateError.WrongChip -> resources.getString(R.string.scout_fw_error_wrong_chip, error.image, name, error.board)
        is FirmwareUpdateError.WrongBoard -> resources.getString(R.string.scout_fw_error_wrong_board, name)
        is FirmwareUpdateError.SurveyRunning -> resources.getString(R.string.scout_fw_blocked_recording, name)
        is FirmwareUpdateError.BoardBusy -> resources.getString(R.string.scout_fw_error_busy, name)
        is FirmwareUpdateError.NotConnected -> resources.getString(R.string.scout_fw_error_not_connected, name)
        is FirmwareUpdateError.ConnectionLost -> resources.getString(R.string.scout_fw_error_connection_lost, name)
        is FirmwareUpdateError.PairingLost -> resources.getString(R.string.scout_fw_blocked_pairing, name)
        is FirmwareUpdateError.BoardRejected -> resources.getString(R.string.scout_fw_error_rejected, name)
        is FirmwareUpdateError.ImageTooLarge -> resources.getString(R.string.scout_fw_error_too_large, name)
        is FirmwareUpdateError.FlashWriteFailed -> resources.getString(R.string.scout_fw_error_flash, name)
        is FirmwareUpdateError.VerifyFailed -> resources.getString(R.string.scout_fw_error_verify, name)
        is FirmwareUpdateError.ProtocolError -> resources.getString(R.string.scout_fw_error_protocol, name)
        is FirmwareUpdateError.BoardDidNotReturn -> resources.getString(R.string.scout_fw_error_did_not_return, name)
        is FirmwareUpdateError.RolledBack -> resources.getString(R.string.scout_fw_error_rolled_back, name, error.version)
        is FirmwareUpdateError.NotConfirmed -> resources.getString(R.string.scout_fw_error_not_confirmed, name, error.version)
        is FirmwareUpdateError.UnexpectedVersion -> resources.getString(R.string.scout_fw_error_unexpected, name, error.found, error.expected)
        is FirmwareUpdateError.Canceled -> resources.getString(R.string.scout_fw_error_canceled, name)
    }

    /** @return The switch label for [consent]. */
    @JvmStatic
    fun consent(resources: Resources, consent: FirmwareConsent): String = when (consent) {
        is FirmwareConsent.NotNewer -> resources.getString(
            if (consent.comparison == FirmwareComparison.SAME) R.string.scout_fw_consent_same else R.string.scout_fw_consent_older,
        )

        is FirmwareConsent.StaleRelease -> resources.getString(R.string.scout_fw_consent_stale, consent.newer)
    }

    /** @return The main action for a release that compares as [comparison] to the Scout's firmware. */
    @JvmStatic
    fun releaseAction(resources: Resources, comparison: FirmwareComparison, version: String): String = when (comparison) {
        FirmwareComparison.NEWER, FirmwareComparison.UNKNOWN -> resources.getString(R.string.scout_fw_action_update)
        FirmwareComparison.SAME -> resources.getString(R.string.scout_fw_action_reinstall, version)
        FirmwareComparison.OLDER -> resources.getString(R.string.scout_fw_action_install_older, version)
    }

    /** @return [released] (ISO 8601, UTC) as a local date, like "Oct 24, 2026", or null when it doesn't parse. */
    @JvmStatic
    fun releaseDate(released: String?, zone: ZoneId = ZoneId.systemDefault()): String? {
        if (released.isNullOrEmpty()) return null
        return try {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(zone).format(Instant.parse(released))
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
