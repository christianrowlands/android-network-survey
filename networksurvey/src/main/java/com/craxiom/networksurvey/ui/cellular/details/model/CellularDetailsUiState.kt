package com.craxiom.networksurvey.ui.cellular.details.model

import androidx.compose.runtime.Immutable
import com.craxiom.networksurvey.fragments.model.NrSecondaryCellViewState

/** What stops the Cellular view from showing cells, in the order it is checked. */
enum class CellularScanState {
    /** Location or phone permission is missing. */
    PERMISSION_NEEDED,

    /** Location is off; Android reports no cells to apps then. */
    LOCATION_OFF,

    /** The radios are off. */
    AIRPLANE_MODE,

    /** Nothing is in the way. */
    READY,
}

/**
 * The selected SIM's tab of the Cellular view.
 *
 * @param scanned         False until the first scan for this SIM arrives.
 * @param scanCount       How many scans this SIM has had, so the strip can mark each new one.
 * @param hero            The technology line, or null before the first technology report.
 * @param pills           The Voice, Data and Branding pills, or null to hide them.
 * @param carrier         The last serving cell's operator name (blank or null shows "Unknown carrier").
 * @param nrLastSeenAgeMs How long ago the idle NR cell was seen, or null when it is not idle.
 */
@Immutable
data class CellularSimView(
    val scanned: Boolean = false,
    val scanCount: Int = 0,
    val hero: CellularHero? = null,
    val pills: CellularPills? = null,
    val carrier: String? = null,
    val serving: ServingCellDetails? = null,
    val carrierAggregation: CarrierAggregation? = null,
    val nrCard: NrSecondaryCellViewState? = null,
    val nrLastSeenAgeMs: Long? = null,
    val neighbors: List<NeighborSection> = emptyList(),
) {
    companion object {
        /**
         * @param voiceNone The localized "None" the controller uses for no voice bearer; it is
         *                  noise the pills hide behind a degraded hero.
         */
        fun from(data: CellularSimData, voiceNone: String): CellularSimView {
            val technology = data.technology
            val serving = data.serving
            return CellularSimView(
                scanned = data.scanned,
                scanCount = data.scanCount,
                hero = technology?.let(CellularHeroMapper::hero),
                pills = technology?.let { CellularHeroMapper.pills(it, voiceNone) },
                carrier = data.carrier,
                serving = serving,
                // Carrier aggregation belongs to the serving cell, so it goes when the cell does.
                carrierAggregation = if (serving == null) null else CellularHeroMapper.carrierAggregation(technology?.cellBandwidthsKhz()),
                nrCard = data.nrCard,
                nrLastSeenAgeMs = data.nrLastSeenAgeMs,
                neighbors = data.neighbors,
            )
        }
    }
}

/**
 * The Cellular view.
 *
 * @param pages        The SIM tabs; one tab hides the tab row.
 * @param pagesKnown   False until the SIMs have been read from the survey service.
 * @param selectedPage The index of the tab showing.
 * @param bound        True while the view is connected to the survey service and listening.
 * @param sim          The selected tab's content.
 */
@Immutable
data class CellularDetailsUiState(
    val pages: List<CellularPage> = emptyList(),
    val pagesKnown: Boolean = false,
    val selectedPage: Int = 0,
    val scanState: CellularScanState = CellularScanState.READY,
    val bound: Boolean = false,
    val sim: CellularSimView = CellularSimView(),
)
