package com.craxiom.networksurvey.ui.scout

import android.content.res.Resources
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.scout.ScoutDrops
import com.craxiom.networksurvey.scout.firmware.FirmwareReleaseStatus
import com.craxiom.networksurvey.scout.model.ScoutBattery
import com.craxiom.networksurvey.scout.model.ScoutLinkDetails
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.ui.common.CountFormat

/**
 * The values on the Scout page's Now and Scout rows: what the Scout is doing, what it hears, what
 * it missed, its firmware and its power. Plain functions over [Resources] so they are tested.
 */
object ScoutPageText {

    /** "All 41 channels · now 36", "Waiting for a survey or a Wi-Fi screen" or "Nothing". */
    @JvmStatic
    fun doing(resources: Resources, state: ScoutState, details: ScoutLinkDetails): String {
        val capturing = when (state) {
            is ScoutState.Connected -> state.capturing
            is ScoutState.CatchingUp -> true
            else -> return resources.getString(R.string.scout_now_doing_nothing)
        }
        if (!capturing) return resources.getString(R.string.scout_now_doing_waiting)
        val channels = details.configured?.channelsCount ?: 0
        val channel = details.status?.currentChannel ?: 0
        return if (channels > 0 && channel > 0) {
            resources.getString(R.string.scout_now_doing_capture, channels, channel)
        } else {
            resources.getString(R.string.scout_detail_capture_starting)
        }
    }

    /** "74 frames/s" while the Scout captures, else "Not listening". */
    @JvmStatic
    fun hearing(resources: Resources, state: ScoutState, details: ScoutLinkDetails): String {
        val status = details.status
        val listening = (state is ScoutState.Connected && state.capturing) || state is ScoutState.CatchingUp
        if (!listening || status == null) return resources.getString(R.string.scout_now_hearing_none)
        return resources.getString(R.string.scout_now_hearing_value, CountFormat.format(status.rxFramesPerSec))
    }

    /** "None", or what was missed: "3 records dropped on the Scout · 2 Bluetooth packets lost". */
    @JvmStatic
    fun missed(resources: Resources, drops: ScoutDrops): String {
        val parts = listOfNotNull(
            drops.records.takeIf { it > 0 }?.let { CountFormat.plural(resources, R.plurals.scout_missed_records, it) },
            drops.rawFrames.takeIf { it > 0 }?.let { CountFormat.plural(resources, R.plurals.scout_missed_raw, it) },
            drops.missedNotifications.takeIf { it > 0 }?.let { CountFormat.plural(resources, R.plurals.scout_missed_link, it) },
        )
        if (parts.isEmpty()) return resources.getString(R.string.scout_now_missed_none)
        return parts.joinToString(resources.getString(R.string.survey_dot_separator))
    }

    /**
     * The firmware version, with "Update needed" when the Scout is too old for this app, else how
     * it compares with the latest verified release ("Up to date", "Update available").
     */
    @JvmStatic
    fun firmware(
        resources: Resources,
        state: ScoutState,
        details: ScoutLinkDetails,
        status: FirmwareReleaseStatus = FirmwareReleaseStatus.Unknown,
    ): String {
        val version = (state as? ScoutState.NeedsFirmwareUpdate)?.firmwareVersion
            ?: details.firmwareVersion.ifEmpty { state.scout?.firmwareVersion.orEmpty() }
        if (version.isEmpty()) return resources.getString(R.string.scout_info_unknown)
        if (state is ScoutState.NeedsFirmwareUpdate) return resources.getString(R.string.scout_info_firmware_update, version)
        return when (status) {
            FirmwareReleaseStatus.UpToDate -> resources.getString(R.string.scout_info_firmware_up_to_date, version)
            is FirmwareReleaseStatus.UpdateAvailable -> resources.getString(R.string.scout_info_firmware_available, version)
            FirmwareReleaseStatus.NewerThanRelease -> resources.getString(R.string.scout_info_firmware_newer, version)
            FirmwareReleaseStatus.Unknown -> version
        }
    }

    /** The Update firmware row's subtitle: blocked while recording, running, or what it will find. */
    @JvmStatic
    fun updateRow(resources: Resources, state: ScoutPageUi): String = when {
        state.firmwareUpdating -> resources.getString(R.string.scout_fw_row_updating)
        state.recording -> resources.getString(R.string.scout_fw_row_recording)
        state.firmwareStatus is FirmwareReleaseStatus.UpdateAvailable ->
            resources.getString(R.string.scout_fw_row_available, state.firmwareStatus.version)

        else -> resources.getString(R.string.scout_fw_row_checks)
    }

    /** "82% battery" or "USB powered" while connected; before that, that it shows once it connects. */
    @JvmStatic
    fun battery(resources: Resources, details: ScoutLinkDetails): String {
        val status = details.status ?: return resources.getString(R.string.scout_info_battery_unknown)
        return ScoutStatusText.power(resources, ScoutBattery.percent(status))
    }
}
