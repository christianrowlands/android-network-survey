package com.craxiom.networksurvey.ui.cellular.details.model

import com.craxiom.messaging.NrRecord
import com.craxiom.networksurvey.fragments.model.NrSecondaryCellSighting
import com.craxiom.networksurvey.fragments.model.NrSecondaryCellViewState
import com.craxiom.networksurvey.model.NrRecordWrapper
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.TelephonyStateUtils

/**
 * Decides, once per scan, what the 5G NR secondary cell card shows: the live cell when the scan
 * has one, the dimmed last-seen cell while the phone stays on 5G NSA with the NR leg detached (on
 * NSA the leg only attaches during active data), or nothing.
 *
 * Pure: the caller passes in the time, so tests run it with a fake clock.
 */
object NrSecondaryCellResolver {

    /**
     * How long the card keeps showing the last-seen cell after the NR leg detaches. The idle state
     * stops the card flickering while the user reads the screen; it is short because a held cell is
     * a claim about the recent past. A change of serving cell drops the sighting regardless.
     */
    const val IDLE_MAX_AGE_MS = 2 * 60 * 1000L

    private const val MS_PER_SECOND = 1000L
    private const val SECONDS_PER_MINUTE = 60L

    /**
     * The card for one scan.
     *
     * @param state         The card, or null to hide it.
     * @param sighting      The sighting to keep for the next scan.
     * @param lastSeenAgeMs How long ago the idle cell was seen, or null when the card is not idle.
     */
    data class Result(
        val state: NrSecondaryCellViewState?,
        val sighting: NrSecondaryCellSighting,
        val lastSeenAgeMs: Long?,
    )

    /**
     * @param secondary     The SECONDARY_SERVING NR cell in this scan, or null.
     * @param nrMode        The NR mode reported just before this scan's records.
     * @param servingCellId The serving cell of this scan ([CellularUtils.getTowerId]); a sighting
     *                      from another serving cell describes a place the user has left.
     * @param lastSighting  The sighting kept from earlier scans.
     * @param nowMs         The current time on the same clock as the sightings
     *                      ([android.os.SystemClock.elapsedRealtime] in the app).
     */
    fun resolve(
        secondary: NrRecordWrapper?,
        nrMode: TelephonyStateUtils.NrMode,
        servingCellId: String,
        lastSighting: NrSecondaryCellSighting,
        nowMs: Long,
    ): Result {
        val usable = lastSighting.isUsableFor(servingCellId)
        val lastSeenAgeMs = if (usable) nowMs - lastSighting.realtimeMs() else -1L

        return when (TelephonyStateUtils.resolveNrCardState(secondary != null, nrMode, lastSeenAgeMs, IDLE_MAX_AGE_MS)) {
            TelephonyStateUtils.NrCardState.ACTIVE -> {
                val state = viewState(requireNotNull(secondary))
                Result(state, NrSecondaryCellSighting(state, nowMs, servingCellId), null)
            }

            TelephonyStateUtils.NrCardState.IDLE -> Result(lastSighting.state().asIdle(), lastSighting, lastSeenAgeMs)
            else -> Result(null, lastSighting, null)
        }
    }

    /** @return The live card for the NR cell the phone reported as SECONDARY_SERVING. */
    fun viewState(wrapper: NrRecordWrapper): NrSecondaryCellViewState {
        val data = (wrapper.cellularRecord as NrRecord).data
        val narfcn = if (data.hasNarfcn()) data.narfcn.value else -1
        return NrSecondaryCellViewState(
            CellularUtils.formatNrBands(wrapper.bands, narfcn),
            ServingCellMapper.nrBandTarget(CellularUtils.resolveNrBands(wrapper.bands, narfcn)),
            if (data.hasNarfcn()) ServingCellMapper.formatNrFrequency(narfcn).orEmpty() else "",
            if (data.hasPci()) ServingCellMapper.formatPci(data.pci.value) else "",
            if (data.hasNarfcn()) narfcn.toString() else "",
            if (data.hasSsRsrp()) data.ssRsrp.value.toInt() else null,
            if (data.hasSsRsrq()) data.ssRsrq.value.toInt() else null,
            if (data.hasSsSinr()) data.ssSinr.value.toInt() else null,
            false,
        )
    }

    /** @return The idle card's last-seen age: whole seconds under a minute, then whole minutes. */
    fun lastSeenAge(ageMs: Long): LastSeenAge {
        val seconds = ageMs / MS_PER_SECOND
        return if (seconds < SECONDS_PER_MINUTE) LastSeenAge(seconds, false) else LastSeenAge(seconds / SECONDS_PER_MINUTE, true)
    }

    /** How long ago the idle cell was seen: [amount] seconds, or minutes when [inMinutes]. */
    data class LastSeenAge(val amount: Long, val inMinutes: Boolean)
}
