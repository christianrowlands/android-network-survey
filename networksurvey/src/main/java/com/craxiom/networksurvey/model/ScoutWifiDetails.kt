package com.craxiom.networksurvey.model

import java.io.Serializable

/**
 * What an NS Scout heard about an access point beyond the NS Messaging beacon record, for the
 * Wi-Fi screens only: BSS Load and the PHY capability elements. Never logged or sent.
 *
 * @property scoutName The Scout that heard it, "NS-ABCD".
 * @property stationCount Stations the AP reports in its BSS Load element, null without one.
 * @property channelUtilization Channel use from the BSS Load element, 0 to 255, null without one.
 * @property phyFlags `PhyFlag` bits: which of the HT, VHT, HE and EHT capability elements were present.
 * @property noiseFloorDbm The receiver's noise floor, null when the board gave none.
 * @property pairwiseCiphers The pairwise `CipherSuite` numbers, apart from the group cipher (the
 *   record's `cipherSuites` merges the two); empty when not known.
 * @property groupCipher The group `CipherSuite` number, null when not known.
 */
data class ScoutWifiDetails(
    val scoutName: String,
    val stationCount: Int?,
    val channelUtilization: Int?,
    val phyFlags: Int,
    val noiseFloorDbm: Int?,
    val pairwiseCiphers: List<Int> = emptyList(),
    val groupCipher: Int? = null,
) : Serializable
