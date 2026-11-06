package com.craxiom.networksurvey.ui.scout

import android.content.res.Resources
import androidx.annotation.StringRes
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.board.v1.Band
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.ui.common.CountFormat
import com.craxiom.networksurvey.ui.common.RelativeAge
import com.craxiom.networksurvey.ui.survey.SurveyEventMessages
import java.text.NumberFormat
import java.util.Locale

/** How a Scout status reads at a glance; the word always says it too, so color is never the only signal. */
enum class ScoutLevel {
    /** Connected or catching up: Wi-Fi from the Scout is flowing. */
    OK,

    /** Connecting, out of range, updating: likely to fix itself or needs the Scout brought close. */
    WARN,

    /** Needs the user: Bluetooth, the permission, a firmware or app update, pairing, a Scout that can't connect. */
    BAD,

    /** Not in use, or nothing needs the Scout right now. */
    OFF,
}

/**
 * The words for an NS Scout's state, shared by the Survey row, the Scout page and the More row so
 * they never disagree (the plan's one state vocabulary). Plain functions over [Resources], like
 * [SurveyEventMessages], so they are tested without composition.
 *
 * [recording] picks the Recording wording: a Scout that drops while a survey runs is "Out of
 * range"; before Start the same state is "Not connected".
 */
object ScoutStatusText {

    /** Within this long of the last status the board is still surveying into its buffer (protocol.md 5). */
    const val STILL_LISTENING_MS = 5 * 60_000L

    private val BAND_GHZ = mapOf(Band.BAND_GHZ_2_4 to 2.4, Band.BAND_GHZ_5 to 5.0, Band.BAND_GHZ_6 to 6.0)

    /** @return How [state] reads at a glance. */
    @JvmStatic
    fun level(state: ScoutState): ScoutLevel = when (state) {
        is ScoutState.Connected, is ScoutState.CatchingUp -> ScoutLevel.OK
        is ScoutState.Connecting, is ScoutState.OutOfRange, is ScoutState.UpdatingFirmware -> ScoutLevel.WARN
        is ScoutState.PermissionMissing, is ScoutState.BluetoothOff, is ScoutState.NeedsFirmwareUpdate,
        is ScoutState.AppUpdateNeeded, is ScoutState.PairingLost, is ScoutState.PairingRefused,
        is ScoutState.CantConnect -> ScoutLevel.BAD

        is ScoutState.NotConnected, is ScoutState.NotInUse, ScoutState.NotPaired -> ScoutLevel.OFF
    }

    /** @return The status word for [state]. */
    @StringRes
    @JvmStatic
    fun wordRes(state: ScoutState, recording: Boolean): Int = when (state) {
        is ScoutState.Connected -> R.string.scout_word_connected
        is ScoutState.CatchingUp -> R.string.scout_word_catching_up
        is ScoutState.Connecting -> R.string.scout_word_connecting
        is ScoutState.OutOfRange -> if (recording) R.string.scout_word_out_of_range else R.string.scout_word_not_connected
        is ScoutState.CantConnect -> R.string.scout_word_cant_connect
        is ScoutState.UpdatingFirmware -> R.string.scout_word_updating
        is ScoutState.PermissionMissing -> R.string.scout_word_permission
        is ScoutState.BluetoothOff -> R.string.scout_word_bluetooth_off
        is ScoutState.NeedsFirmwareUpdate -> R.string.scout_word_firmware
        is ScoutState.AppUpdateNeeded -> R.string.scout_word_app_update
        is ScoutState.PairingLost -> R.string.scout_word_pairing_lost
        is ScoutState.PairingRefused -> R.string.scout_word_pairing_refused
        is ScoutState.NotInUse -> R.string.scout_word_not_in_use
        is ScoutState.NotConnected, ScoutState.NotPaired -> R.string.scout_word_not_connected
    }

    /** @return The status word for [state]. */
    @JvmStatic
    fun word(resources: Resources, state: ScoutState, recording: Boolean): String =
        resources.getString(wordRes(state, recording))

    /**
     * @return The line under the word, or null when there is nothing to add. [lastHeardAtMs] (wall
     * clock) gives "Last heard 2 minutes ago" for a Scout that went quiet.
     */
    @JvmStatic
    fun detail(
        resources: Resources,
        state: ScoutState,
        recording: Boolean,
        lastHeardAtMs: Long?,
        nowMs: Long,
        locale: Locale = Locale.getDefault(),
    ): String? = when (state) {
        is ScoutState.Connected -> when {
            recording && state.capturing && state.channel > 0 -> resources.getString(
                R.string.scout_detail_capture, state.channel, CountFormat.format(state.framesPerSecond),
            )

            recording && state.capturing -> resources.getString(R.string.scout_detail_capture_starting)
            else -> summary(resources, state.scout, state.batteryPercent, connected = true)
        }

        is ScoutState.CatchingUp ->
            CountFormat.plural(resources, R.plurals.scout_detail_catching_up, state.backlogRecords.toLong())

        is ScoutState.Connecting -> resources.getString(R.string.scout_detail_hold_near)
        is ScoutState.OutOfRange -> outOfRange(resources, state, recording, lastHeardAtMs, nowMs, locale)
        is ScoutState.CantConnect -> resources.getString(R.string.scout_detail_cant_connect, state.scout.name)
        is ScoutState.UpdatingFirmware -> resources.getString(R.string.scout_detail_updating)
        is ScoutState.PermissionMissing -> resources.getString(R.string.scout_detail_permission)
        is ScoutState.BluetoothOff -> resources.getString(R.string.scout_detail_bluetooth_off)
        is ScoutState.NeedsFirmwareUpdate -> resources.getString(R.string.scout_detail_firmware, state.firmwareVersion)
        is ScoutState.AppUpdateNeeded -> resources.getString(R.string.scout_detail_app_update, state.scout.name)
        is ScoutState.PairingLost -> resources.getString(R.string.scout_detail_pairing_lost)
        is ScoutState.PairingRefused -> resources.getString(R.string.scout_detail_pairing_refused)
        is ScoutState.NotConnected -> summary(resources, state.scout, batteryPercent = null, connected = false)
        is ScoutState.NotInUse, ScoutState.NotPaired -> null
    }

    /** "2.4 and 5 GHz · USB powered", or the bands alone when the power is not known yet. */
    @JvmStatic
    fun summary(resources: Resources, scout: PairedScout, batteryPercent: Int?, connected: Boolean): String? {
        val parts = listOfNotNull(bands(resources, scout.bands), power(resources, batteryPercent).takeIf { connected })
        return parts.takeIf { it.isNotEmpty() }?.joinToString(resources.getString(R.string.survey_dot_separator))
    }

    /** "2.4 and 5 GHz", or null when the Scout reported no bands. */
    @JvmStatic
    fun bands(resources: Resources, bands: List<Int>, locale: Locale = Locale.getDefault()): String? {
        val format = NumberFormat.getInstance(locale)
        val numbers = bands.mapNotNull { Band.forNumber(it) }.distinct().sorted().mapNotNull { BAND_GHZ[it] }.map(format::format)
        if (numbers.isEmpty()) return null
        return resources.getString(R.string.scout_bands, SurveyEventMessages.join(resources, numbers))
    }

    /** "82% battery", or "USB powered" when the board reports no battery. */
    @JvmStatic
    fun power(resources: Resources, batteryPercent: Int?): String =
        batteryPercent?.let { resources.getString(R.string.scout_battery_percent, it) } ?: resources.getString(R.string.scout_usb_powered)

    private fun outOfRange(
        resources: Resources,
        state: ScoutState.OutOfRange,
        recording: Boolean,
        lastHeardAtMs: Long?,
        nowMs: Long,
        locale: Locale,
    ): String {
        if (state.batteryMayBeEmpty) return resources.getString(R.string.scout_detail_battery_empty)
        val age = lastHeardAtMs?.let { RelativeAge.of(it, nowMs) }?.takeIf { it.unit != RelativeAge.AgeUnit.NOW }
        if (age == null) {
            // Before Start the plate above already says to plug it in, so the row describes the Scout
            val summary = if (recording) null else summary(resources, state.scout, batteryPercent = null, connected = false)
            return summary ?: resources.getString(R.string.scout_detail_plug_in)
        }
        val ago = age.format(locale, midSentence = true)
        val stillListening = recording && lastHeardAtMs != null && nowMs - lastHeardAtMs < STILL_LISTENING_MS
        return resources.getString(if (stillListening) R.string.scout_detail_still_listening else R.string.scout_detail_last_heard, ago)
    }
}
