package com.craxiom.networksurvey.ui.wifi.model

import com.craxiom.messaging.wifi.AkmSuite
import com.craxiom.messaging.wifi.CipherSuite
import com.craxiom.networksurvey.board.v1.PhyFlag
import kotlin.math.roundToInt

/**
 * The values the Wi-Fi details page shows for an access point an NS Scout heard, worked out from
 * the record's numbers. Pure, so it is tested on the JVM. Suite names are the standard identifiers
 * people look up (802.1X, SAE, CCMP-128), so they are not translated.
 */
object ScoutApText {

    private const val TU_MS = 1.024
    private const val PERCENT = 100
    private const val CHANNEL_UTILIZATION_MAX = 255

    private val AKM_NAMES = mapOf(
        AkmSuite.IEEE_8021X to "802.1X",
        AkmSuite.FT_IEEE_8021X to "FT-802.1X",
        AkmSuite.IEEE_8021X_SHA256 to "802.1X-SHA256",
        AkmSuite.PSK to "PSK",
        AkmSuite.FT_PSK to "FT-PSK",
        AkmSuite.PSK_SHA256 to "PSK-SHA256",
        AkmSuite.SAE to "SAE",
        AkmSuite.FT_SAE to "FT-SAE",
        AkmSuite.TDLS to "TDLS",
    )

    private val ENTERPRISE_AKMS = setOf(AkmSuite.IEEE_8021X, AkmSuite.FT_IEEE_8021X, AkmSuite.IEEE_8021X_SHA256)

    private val CIPHER_NAMES = mapOf(
        CipherSuite.WEP_40 to "WEP-40",
        CipherSuite.WEP_104 to "WEP-104",
        CipherSuite.WEP to "WEP",
        CipherSuite.TKIP to "TKIP",
        CipherSuite.CCMP to "CCMP-128",
        CipherSuite.CCMP_256 to "CCMP-256",
        CipherSuite.GCMP_128 to "GCMP-128",
        CipherSuite.GCMP_256 to "GCMP-256",
        CipherSuite.BIP_GMAC_128 to "BIP-GMAC-128",
        CipherSuite.BIP_GMAC_256 to "BIP-GMAC-256",
        CipherSuite.BIP_CMAC_256 to "BIP-CMAC-256",
    )

    /** The Wi-Fi generation each PHY capability element stands for: HT is Wi-Fi 4, up to EHT, Wi-Fi 7. */
    private val GENERATIONS = linkedMapOf(
        PhyFlag.PHY_FLAG_HT to 4,
        PhyFlag.PHY_FLAG_VHT to 5,
        PhyFlag.PHY_FLAG_HE to 6,
        PhyFlag.PHY_FLAG_EHT to 7,
    )

    /** @return The AKM suite names, without open or unknown ones (they name no key management). */
    @JvmStatic
    fun akmNames(akmNumbers: List<Int>): List<String> = akmNumbers.mapNotNull { AKM_NAMES[AkmSuite.forNumber(it)] }.distinct()

    /** @return The cipher names, without open or unknown ones. */
    @JvmStatic
    fun cipherNames(cipherNumbers: List<Int>): List<String> = cipherNumbers.mapNotNull { CIPHER_NAMES[CipherSuite.forNumber(it)] }.distinct()

    /** @return True when the AP authenticates with 802.1X (Enterprise), false for a passphrase (Personal), null when neither. */
    @JvmStatic
    fun isEnterprise(akmNumbers: List<Int>): Boolean? {
        val suites = akmNumbers.mapNotNull { AkmSuite.forNumber(it) }
        return when {
            suites.any { it in ENTERPRISE_AKMS } -> true
            suites.any { it in AKM_NAMES } -> false
            else -> null
        }
    }

    /** @return The beacon interval in whole milliseconds (one time unit is 1.024 ms). */
    @JvmStatic
    fun beaconIntervalMs(tu: Int): Int = (tu * TU_MS).roundToInt()

    /** @return The BSS Load channel utilization (0 to 255) as a percentage. */
    @JvmStatic
    fun channelUsePercent(utilization: Int): Int =
        (utilization.coerceIn(0, CHANNEL_UTILIZATION_MAX) * PERCENT.toDouble() / CHANNEL_UTILIZATION_MAX).roundToInt()

    /** @return The Wi-Fi generations whose capability element was in the beacon, oldest first. */
    @JvmStatic
    fun generations(phyFlags: Int): List<Int> = GENERATIONS.filterKeys { phyFlags and it.number != 0 }.values.toList()
}
