package com.craxiom.networksurvey.ui.signals

import androidx.annotation.StringRes
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.main.SurveyStatus

/** The four live views under the Signals tab. */
enum class SignalsSubTab(
    @StringRes val label: Int,
    val recordType: RecordType,
    val intervalKey: String,
    val defaultIntervalSeconds: Int,
) {
    CELLULAR(
        R.string.cellular_title, RecordType.CELLULAR,
        NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS,
        NetworkSurveyConstants.DEFAULT_CELLULAR_SCAN_INTERVAL_SECONDS,
    ),
    WIFI(
        R.string.wifi_title, RecordType.WIFI,
        NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS,
        NetworkSurveyConstants.DEFAULT_WIFI_SCAN_INTERVAL_SECONDS,
    ),
    BLUETOOTH(
        R.string.bluetooth_title, RecordType.BLUETOOTH,
        NetworkSurveyConstants.PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS,
        NetworkSurveyConstants.DEFAULT_BLUETOOTH_SCAN_INTERVAL_SECONDS,
    ),
    GNSS(
        R.string.gnss_title, RecordType.GNSS,
        NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS,
        NetworkSurveyConstants.DEFAULT_GNSS_SCAN_INTERVAL_SECONDS,
    ),
}

/** The two pages of the GNSS view, in the order of its toggle. */
enum class GnssPage(@StringRes val label: Int) {
    STATUS(R.string.gnss_page_status),
    SKY(R.string.gnss_page_sky),
}

/**
 * The SIM the Cellular view is showing.
 *
 * @property slot The 1-based SIM slot, or 0 when the phone has no SIM.
 * @property subscriptionId The subscription the page shows, or -1 when there is no SIM.
 * @property carrier The carrier's display name, when known.
 */
data class ViewedSim(val slot: Int, val subscriptionId: Int, val carrier: String?) {
    /** True when the phone has no active SIM. */
    val isNoSim: Boolean get() = slot <= 0

    companion object {
        /** The phone has no active SIM. */
        @JvmField
        val NO_SIM = ViewedSim(slot = 0, subscriptionId = -1, carrier = null)
    }
}

/** What the scan strip says about the sub-tab's records. */
enum class StripState {
    /** No survey runs: the view is live and nothing is saved. */
    LIVE,

    /** The running survey records this sub-tab's record type. */
    RECORDING,

    /** A survey runs, but without this record type. */
    NOT_IN_SURVEY,

    /** The survey that records this type is paused. */
    PAUSED,
}

/**
 * The scan strip above a Signals sub-tab.
 *
 * @property intervalSeconds The scan interval to show, or null when none is shown.
 * @property sim The SIM label on the left (Cellular only), or null for none.
 */
data class ScanStripState(val state: StripState, val intervalSeconds: Int?, val sim: ViewedSim?)

/**
 * @return The strip for [subTab]: whether its records are saved, its scan interval (not for GNSS,
 * whose view updates at the receiver's rate, nor when nothing scans for it), and the SIM.
 */
fun scanStripState(subTab: SignalsSubTab, status: SurveyStatus, intervalSeconds: Int, sim: ViewedSim?): ScanStripState {
    val inSurvey = status.recording && subTab.recordType in status.recordTypes
    val state = when {
        inSurvey && status.paused -> StripState.PAUSED
        inSurvey -> StripState.RECORDING
        status.recording -> StripState.NOT_IN_SURVEY
        else -> StripState.LIVE
    }
    val showsInterval = subTab != SignalsSubTab.GNSS && (state == StripState.LIVE || state == StripState.RECORDING)
    return ScanStripState(
        state = state,
        intervalSeconds = intervalSeconds.takeIf { showsInterval },
        sim = sim.takeIf { subTab == SignalsSubTab.CELLULAR },
    )
}

/** An action on the Signals top bar. */
enum class SignalsAction { OPEN_MAP, WIFI_SPECTRUM, WIFI_FILTER, BLUETOOTH_FILTER, GNSS_SORT, GNSS_FILTER }

/**
 * @return The top bar actions for [subTab]; the GNSS Sky page has no sort, and the Wi-Fi and
 * Bluetooth lists sort from the pill on their own strip.
 */
fun signalsActions(subTab: SignalsSubTab, gnssPage: GnssPage): List<SignalsAction> = when (subTab) {
    SignalsSubTab.CELLULAR -> listOf(SignalsAction.OPEN_MAP)
    SignalsSubTab.WIFI -> listOf(SignalsAction.WIFI_SPECTRUM, SignalsAction.WIFI_FILTER)
    SignalsSubTab.BLUETOOTH -> listOf(SignalsAction.BLUETOOTH_FILTER)
    SignalsSubTab.GNSS -> when (gnssPage) {
        GnssPage.STATUS -> listOf(SignalsAction.GNSS_SORT, SignalsAction.GNSS_FILTER)
        GnssPage.SKY -> listOf(SignalsAction.GNSS_FILTER)
    }
}
