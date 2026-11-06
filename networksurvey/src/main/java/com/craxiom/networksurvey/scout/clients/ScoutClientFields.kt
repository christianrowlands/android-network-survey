package com.craxiom.networksurvey.scout.clients

import com.craxiom.messaging.wifi.ClientFrameType
import com.craxiom.networksurvey.board.v1.ClientObservation
import com.craxiom.networksurvey.board.v1.ClientSource
import com.craxiom.networksurvey.board.v1.ProbeObservation
import com.craxiom.networksurvey.scout.wifi.ScoutWifiFields

/**
 * NS Messaging field values for Wi-Fi clients, ported from the iOS `WifiFields.frameTypes` so both
 * apps write the same values for the same frames.
 */
object ScoutClientFields {
    private val FRAME_TYPE_BITS: List<Pair<ClientSource, ClientFrameType>> = listOf(
        ClientSource.CLIENT_SOURCE_DATA to ClientFrameType.DATA,
        ClientSource.CLIENT_SOURCE_NULL_DATA to ClientFrameType.NULL_DATA,
        ClientSource.CLIENT_SOURCE_PROBE_REQ to ClientFrameType.PROBE_REQUEST,
        ClientSource.CLIENT_SOURCE_ASSOC_REQ to ClientFrameType.ASSOCIATION_REQUEST,
        ClientSource.CLIENT_SOURCE_REASSOC_REQ to ClientFrameType.REASSOCIATION_REQUEST,
        ClientSource.CLIENT_SOURCE_AUTH to ClientFrameType.AUTHENTICATION,
        ClientSource.CLIENT_SOURCE_ACTION to ClientFrameType.ACTION,
    )

    /**
     * Folds a weaker observation of the same client and window into the one a record is built
     * from: the frame types of [other] are added to its `source_flags`, so the record lists every
     * kind of frame the client sent, and its `associated_bssid` is taken from [other] when [kept]
     * has none, so the record names the access point whenever any dwell knew it.
     *
     * @return [kept] itself when [other] adds nothing, otherwise a copy with the additions.
     */
    @JvmStatic
    fun withWindowOf(kept: ClientObservation, other: ClientObservation): ClientObservation {
        val flags = kept.sourceFlags or other.sourceFlags
        val takeBssid = !ScoutWifiFields.isMac(kept.associatedBssid) && ScoutWifiFields.isMac(other.associatedBssid)
        if (flags == kept.sourceFlags && !takeBssid) return kept
        return kept.toBuilder().setSourceFlags(flags).apply { if (takeBssid) associatedBssid = other.associatedBssid }.build()
    }

    /** @return The frame types set in a `ClientObservation.source_flags` bit field, in protocol order. */
    @JvmStatic
    fun frameTypes(sourceFlags: Int): List<ClientFrameType> =
        FRAME_TYPE_BITS.filter { (bit, _) -> sourceFlags and bit.number != 0 }.map { it.second }
}
