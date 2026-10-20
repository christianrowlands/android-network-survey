package com.craxiom.networksurvey.ui.cellular.details.model

import androidx.compose.runtime.Immutable
import com.craxiom.networksurvey.data.band.BandTapTarget
import com.craxiom.networksurvey.model.CellularProtocol

/** What a value on the Cellular view is; the screen turns it into its label. */
enum class CellFieldKind {
    RSSI, RSCP, RSRP, RSRQ, SNR, CQI, SS_RSRP, SS_RSRQ, SS_SINR,
    PLMN, TAC, LAC, CID, ENB, SECTOR, RNC, SHORT_CID,
    ARFCN, UARFCN, EARFCN, NARFCN, BSIC, PSC, PCI, BAND, FREQUENCY, BANDWIDTH, TA,
}

/** The unit the screen appends to a value. */
enum class ValueUnit { NONE, DBM, DB, MHZ }

/**
 * One value on the Cellular view, before it has a label.
 *
 * @param value      The value without its unit, locale independent ("-79", "173 (2/57)"), or null
 *                   when the phone did not report it (the screen shows "--").
 * @param bucket     A [com.craxiom.networksurvey.util.SignalBuckets] value for signal measurements
 *                   (colors the value), or null for values that are not a signal.
 * @param bandTarget The bands behind a Band value, which open the band reference when tapped.
 * @param fraction   How full a signal's bar is (0 to 1, on the same scale as [bucket]), or null when
 *                   there is no value or the measurement has no scale.
 */
@Immutable
data class CellField(
    val kind: CellFieldKind,
    val value: String?,
    val unit: ValueUnit = ValueUnit.NONE,
    val bucket: Int? = null,
    val bandTarget: BandTapTarget? = null,
    val fraction: Float? = null,
)

/** The hero line's state, from [com.craxiom.networksurvey.util.TelephonyStateUtils.deriveHero]. */
enum class HeroKind { NR_STANDALONE, NR_NON_STANDALONE, DATA_RAT, NO_SERVICE, UNKNOWN }

/** How the hero line is colored, so a bad state never looks like a good one. */
enum class HeroTone { LIVE, WARNING, MUTED }

/**
 * The large technology line at the top of the Cellular view.
 *
 * @param ratName The radio technology name for [HeroKind.DATA_RAT] ("LTE", "UMTS"), else null.
 */
@Immutable
data class CellularHero(val kind: HeroKind, val ratName: String? = null) {
    val tone: HeroTone
        get() = when (kind) {
            HeroKind.NO_SERVICE -> HeroTone.WARNING
            HeroKind.UNKNOWN -> HeroTone.MUTED
            else -> HeroTone.LIVE
        }
}

/**
 * The Voice, Data and Branding pills under the hero.
 *
 * @param data     The data technology, or null to leave the Data pill out.
 * @param branding The carrier's branding label, or null for "None" (no branding override).
 */
@Immutable
data class CellularPills(val voice: String, val data: String?, val branding: String?)

/**
 * The carrier aggregation chips: one bandwidth per component carrier in MHz ("20", "1.4"), and
 * their total.
 */
@Immutable
data class CarrierAggregation(val carriersMhz: List<String>, val totalMhz: String)

/**
 * The serving cell card.
 *
 * @param carrier  The operator name the record carries, or null or blank when unknown.
 * @param band     The band shown in the card's title ("66", "n77"), or null.
 * @param chart    What the signal chart plots for this cell.
 */
@Immutable
data class ServingCellDetails(
    val protocol: CellularProtocol,
    val carrier: String?,
    val signals: List<CellField>,
    val identity: List<CellField>,
    val band: String?,
    val chart: ChartSample,
)

/**
 * One point for the serving cell's signal chart, with the range the fragment always used.
 *
 * @param metric The plotted measurement (its label is the chart caption).
 * @param value  The value in dBm, or null when this scan did not report one.
 * @param cellId The serving cell identity, so the chart can mark a change of cell.
 */
@Immutable
data class ChartSample(
    val protocol: CellularProtocol,
    val metric: CellFieldKind,
    val minDbm: Int,
    val maxDbm: Int,
    val value: Int?,
    val cellId: Int?,
)

/**
 * One neighbor cell.
 *
 * @param title    The cell's code: PCI for LTE and NR, PSC for UMTS, BSIC for GSM.
 * @param details  The channel, band and other measurements, missing values left out.
 * @param signal   The strongest-is-best measurement the list sorts on, with its meter fraction.
 */
@Immutable
data class NeighborRow(
    val title: CellField,
    val details: List<CellField>,
    val signal: CellField,
)

/** The neighbors of one technology, strongest first. */
@Immutable
data class NeighborSection(val protocol: CellularProtocol, val rows: List<NeighborRow>)
