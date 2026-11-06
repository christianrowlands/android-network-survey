package com.craxiom.networksurvey.scout

import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.survey.status.ScoutOutage
import com.craxiom.networksurvey.survey.status.ScoutWifiStatus

/**
 * Turns the Scout's state into what a running survey says about Wi-Fi from it: the same answer for
 * the Survey screen (`SurveyViewModel`) and the survey notification (the service), so both list
 * the same left-out item and the stall watchdog knows an outage is not a stall.
 *
 * A survey gets the 30 second connect grace after it starts (from the Survey screen, a boot or
 * the Intent API) before anything about the Scout is called left out, so a Scout still waking up
 * or Bluetooth still coming on after a boot doesn't flash a problem.
 */
object ScoutWifiStatuses {
    /** The grace after a survey starts, the same 30 s the Scout state uses before Out of range. */
    const val START_GRACE_MS = ScoutStateMapper.CONNECT_GRACE_MS

    /**
     * @param state The Scout's state.
     * @param source Where Wi-Fi comes from.
     * @param surveyStartMs When the survey session started, null when none runs.
     * @param nowMs The current wall clock time.
     * @return The Scout as the Wi-Fi source, or null when the phone is the source.
     */
    @JvmStatic
    fun of(state: ScoutState, source: WifiSource, surveyStartMs: Long?, nowMs: Long): ScoutWifiStatus? {
        if (source != WifiSource.SCOUT) return null
        val scout = state.scout ?: return null
        val inGrace = surveyStartMs != null && nowMs - surveyStartMs < START_GRACE_MS
        return ScoutWifiStatus(scout.name, outage(state).takeUnless { inGrace })
    }

    /** @return When the start grace ends, so the status can be refreshed then; null when it already has. */
    @JvmStatic
    fun graceEndsAtMs(surveyStartMs: Long?, nowMs: Long): Long? {
        val end = (surveyStartMs ?: return null) + START_GRACE_MS
        return end.takeIf { it > nowMs }
    }

    /** @return Why Wi-Fi from the Scout isn't recorded in [state], null when it is or will be shortly. */
    @JvmStatic
    fun outage(state: ScoutState): ScoutOutage? = when (state) {
        is ScoutState.OutOfRange -> ScoutOutage.OUT_OF_RANGE
        is ScoutState.CantConnect -> ScoutOutage.CANT_CONNECT
        is ScoutState.BluetoothOff -> ScoutOutage.BLUETOOTH_OFF
        is ScoutState.PermissionMissing -> ScoutOutage.PERMISSION_MISSING
        is ScoutState.NeedsFirmwareUpdate -> ScoutOutage.NEEDS_FIRMWARE_UPDATE
        is ScoutState.AppUpdateNeeded -> ScoutOutage.APP_UPDATE_NEEDED
        is ScoutState.PairingLost, is ScoutState.PairingRefused -> ScoutOutage.PAIRING_LOST
        // A survey can't be refused when a boot or the Intent API starts it during an update
        is ScoutState.UpdatingFirmware -> ScoutOutage.UPDATING_FIRMWARE
        // Connecting is the grace itself; Not connected only flashes while a capture starts.
        is ScoutState.Connecting, is ScoutState.NotConnected,
        is ScoutState.Connected, is ScoutState.CatchingUp, is ScoutState.NotInUse, ScoutState.NotPaired -> null
    }
}
