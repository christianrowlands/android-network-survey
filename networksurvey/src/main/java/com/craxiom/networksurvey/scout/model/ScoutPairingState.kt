package com.craxiom.networksurvey.scout.model

import com.craxiom.networksurvey.board.v1.BoardInfo
import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.board.v1.ConfigureResult
import com.craxiom.networksurvey.scout.ScoutDrops
import com.craxiom.networksurvey.scout.link.ScoutLinkFailure

/** Where an "Add a Scout" pairing is, for the pairing sheet. */
sealed interface ScoutPairingState {
    /** No pairing in progress and no result to show. */
    data object Idle : ScoutPairingState

    /** "Tap Pair when Android asks. If nothing appears, check your notifications." */
    data class Pairing(val name: String) : ScoutPairingState

    /** Paired; the Scout is now the Wi-Fi source. */
    data class Paired(val scout: PairedScout) : ScoutPairingState

    /**
     * Pairing failed. [ScoutLinkFailure.PAIRING_REJECTED] and [ScoutLinkFailure.TIMEOUT] repeat the
     * BOOT tip; [ScoutLinkFailure.BOND_MISSING] asks to forget the Scout in Bluetooth settings first.
     */
    data class Failed(val name: String, val failure: ScoutLinkFailure) : ScoutPairingState
}

/** What the Scout page shows under "Now" and "Scout", beyond the status word. */
data class ScoutLinkDetails(
    /** Info from the current or last link, null before the first one. */
    val info: BoardInfo? = null,
    val firmwareVersion: String = "",
    /** The negotiated ATT MTU, 0 while not connected. */
    val mtu: Int = 0,
    /** The latest status, null while not connected. */
    val status: BoardStatus? = null,
    /** The board's answer to the last Configure: channels and role in effect. */
    val configured: ConfigureResult? = null,
    val capturing: Boolean = false,
    /** Data missed since the current capture started. */
    val drops: ScoutDrops = ScoutDrops(),
    /**
     * When the Scout last sent a status, Unix epoch milliseconds; null before the first one in this
     * process. Kept across link losses for "Last heard 2 min ago".
     */
    val lastHeardAtMs: Long? = null,
)
