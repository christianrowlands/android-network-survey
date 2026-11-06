package com.craxiom.networksurvey.scout.wifi

import com.craxiom.messaging.wifi.AkmSuite
import com.craxiom.messaging.wifi.CipherSuite
import com.craxiom.messaging.wifi.EncryptionType
import com.craxiom.messaging.wifi.ServiceSetType
import com.craxiom.messaging.wifi.Standard
import com.craxiom.messaging.wifi.WifiBandwidth
import com.craxiom.networksurvey.board.v1.ApObservation
import com.craxiom.networksurvey.board.v1.Band
import com.craxiom.networksurvey.board.v1.ChannelWidth
import com.craxiom.networksurvey.board.v1.PhyFlag
import com.craxiom.networksurvey.board.v1.SecurityType
import com.google.protobuf.ByteString
import java.util.Locale

/**
 * NS Messaging field values derived from a Scout's observations, ported from the iOS
 * `WifiFields` so both apps write the same values for the same frames.
 */
object ScoutWifiFields {
    private const val RSN_OUI = 0x000FAC
    private const val WPA_OUI = 0x0050F2
    private const val MAC_BYTES = 6
    private const val BYTE_MASK = 0xFF
    private const val SELECTOR_TYPE_MASK = 0xFF
    private const val OUI_SHIFT = 8
    private const val RATE_VALUE_MASK = 0x7F
    private const val RATE_BASIC_BIT = 0x80
    private const val FIRST_MEMBERSHIP_SELECTOR = 121
    private const val CAPABILITY_ESS = 0x0001
    private const val CAPABILITY_IBSS = 0x0002
    private const val MAC_FORMAT = "%02x"
    private const val MAC_SEPARATOR = ":"
    private const val RATES_SEPARATOR = "; "
    private const val HALF_RATE_SUFFIX = ".5"

    private val CCK_RATES = setOf(2, 4, 11, 22)
    private val OFDM_RATES = setOf(12, 18, 24, 36, 48, 72, 96, 108)

    /** @return `aa:bb:cc:dd:ee:ff` in lowercase (as Android's ScanResult), or empty for anything but 6 bytes. */
    @JvmStatic
    fun mac(bytes: ByteString): String {
        if (!isMac(bytes)) return ""
        return bytes.toByteArray().joinToString(MAC_SEPARATOR) { String.format(Locale.US, MAC_FORMAT, it.toInt() and BYTE_MASK) }
    }

    /** @return True when [bytes] is a MAC address (6 bytes); records without one are never built. */
    @JvmStatic
    fun isMac(bytes: ByteString): Boolean = bytes.size() == MAC_BYTES

    /** @return The SSID as text: empty when hidden or all zero, invalid UTF-8 becomes U+FFFD. */
    @JvmStatic
    fun ssid(bytes: ByteString): String {
        if (bytes.toByteArray().all { it.toInt() == 0 }) return ""
        return bytes.toStringUtf8()
    }

    /**
     * @return Supported Rates octets as NS text, "1; 2; 5.5; 11". Membership selectors (the basic
     * bit plus values 121 to 127) are not rates and are skipped.
     */
    @JvmStatic
    fun rates(octets: ByteString): String = octets.toByteArray().toList().mapNotNull { byte: Byte ->
        val octet = byte.toInt() and BYTE_MASK
        val value = octet and RATE_VALUE_MASK
        if (octet and RATE_BASIC_BIT != 0 && value >= FIRST_MEMBERSHIP_SELECTOR) {
            null
        } else if (value % 2 == 0) {
            (value / 2).toString()
        } else {
            (value / 2).toString() + HALF_RATE_SUFFIX
        }
    }.joinToString(RATES_SEPARATOR)

    /** @return The cipher suite for an `(OUI << 8) | type` selector, null for "use group cipher". */
    @JvmStatic
    fun cipherSuite(selector: Int): CipherSuite? {
        val oui = selector ushr OUI_SHIFT
        val type = selector and SELECTOR_TYPE_MASK
        val known = oui == RSN_OUI || oui == WPA_OUI
        return when {
            known && type == 0 -> null
            known && type == 1 -> CipherSuite.WEP_40
            known && type == 2 -> CipherSuite.TKIP
            known && type == 4 -> CipherSuite.CCMP
            known && type == 5 -> CipherSuite.WEP_104
            oui == RSN_OUI && type == 8 -> CipherSuite.GCMP_128
            oui == RSN_OUI && type == 9 -> CipherSuite.GCMP_256
            oui == RSN_OUI && type == 10 -> CipherSuite.CCMP_256
            oui == RSN_OUI && type == 11 -> CipherSuite.BIP_GMAC_128
            oui == RSN_OUI && type == 12 -> CipherSuite.BIP_GMAC_256
            oui == RSN_OUI && type == 13 -> CipherSuite.BIP_CMAC_256
            else -> CipherSuite.UNKNOWN
        }
    }

    /** @return The AKM suite for an `(OUI << 8) | type` selector. */
    @JvmStatic
    fun akmSuite(selector: Int): AkmSuite {
        val oui = selector ushr OUI_SHIFT
        val type = selector and SELECTOR_TYPE_MASK
        val known = oui == RSN_OUI || oui == WPA_OUI
        return when {
            known && type == 1 -> AkmSuite.IEEE_8021X
            known && type == 2 -> AkmSuite.PSK
            oui == RSN_OUI && type == 3 -> AkmSuite.FT_IEEE_8021X
            oui == RSN_OUI && type == 4 -> AkmSuite.FT_PSK
            oui == RSN_OUI && type == 5 -> AkmSuite.IEEE_8021X_SHA256
            oui == RSN_OUI && type == 6 -> AkmSuite.PSK_SHA256
            oui == RSN_OUI && type == 7 -> AkmSuite.TDLS
            oui == RSN_OUI && type == 8 -> AkmSuite.SAE
            oui == RSN_OUI && type == 9 -> AkmSuite.FT_SAE
            else -> AkmSuite.UNKNOWN
        }
    }

    /**
     * @return The board's security summary as an NS encryption type. OWE has no NS value and maps
     * to UNKNOWN; enterprise networks are in the WPA2 or WPA3 bucket (the AKM list tells them apart).
     */
    @JvmStatic
    fun encryptionType(security: SecurityType): EncryptionType = when (security) {
        SecurityType.SECURITY_TYPE_OPEN -> EncryptionType.OPEN
        SecurityType.SECURITY_TYPE_WEP -> EncryptionType.WEP
        SecurityType.SECURITY_TYPE_WPA -> EncryptionType.WPA
        SecurityType.SECURITY_TYPE_WPA_WPA2 -> EncryptionType.WPA_WPA2
        SecurityType.SECURITY_TYPE_WPA2 -> EncryptionType.WPA2
        SecurityType.SECURITY_TYPE_WPA3 -> EncryptionType.WPA3
        SecurityType.SECURITY_TYPE_WPA2_WPA3 -> EncryptionType.WPA2_WPA3
        else -> EncryptionType.UNKNOWN
    }

    /** @return Pairwise ciphers then the group cipher, without repeats; OPEN or WEP without RSN and WPA elements. */
    @JvmStatic
    fun cipherSuites(ap: ApObservation): List<CipherSuite> = when (ap.security) {
        SecurityType.SECURITY_TYPE_OPEN -> listOf(CipherSuite.OPEN)
        SecurityType.SECURITY_TYPE_WEP -> listOf(CipherSuite.WEP)
        else -> (ap.pairwiseCiphersList + ap.groupCipher)
            .filter { it != 0 }
            .mapNotNull(::cipherSuite)
            .distinct()
    }

    /** @return The AKM suites without repeats, OPEN for an open network. */
    @JvmStatic
    fun akmSuites(ap: ApObservation): List<AkmSuite> =
        if (ap.security == SecurityType.SECURITY_TYPE_OPEN) listOf(AkmSuite.OPEN) else ap.akmSuitesList.map(::akmSuite).distinct()

    /** @return ESS or IBSS from the Capability Information field, UNKNOWN otherwise. */
    @JvmStatic
    fun serviceSetType(capabilityInfo: Int): ServiceSetType {
        val ess = capabilityInfo and CAPABILITY_ESS != 0
        val ibss = capabilityInfo and CAPABILITY_IBSS != 0
        return when {
            ess && !ibss -> ServiceSetType.ESS
            ibss && !ess -> ServiceSetType.IBSS
            else -> ServiceSetType.UNKNOWN
        }
    }

    /** @return The channel width; without HT or later capabilities an AP can only use 20 MHz. */
    @JvmStatic
    fun bandwidth(width: ChannelWidth, phyFlags: Int): WifiBandwidth = when (width) {
        ChannelWidth.CHANNEL_WIDTH_MHZ_20 -> WifiBandwidth.MHZ_20
        ChannelWidth.CHANNEL_WIDTH_MHZ_40 -> WifiBandwidth.MHZ_40
        ChannelWidth.CHANNEL_WIDTH_MHZ_80 -> WifiBandwidth.MHZ_80
        ChannelWidth.CHANNEL_WIDTH_MHZ_160 -> WifiBandwidth.MHZ_160
        ChannelWidth.CHANNEL_WIDTH_MHZ_80P80 -> WifiBandwidth.MHZ_80_PLUS
        ChannelWidth.CHANNEL_WIDTH_MHZ_320 -> WifiBandwidth.MHZ_320
        else -> if (phyFlags == 0) WifiBandwidth.MHZ_20 else WifiBandwidth.UNKNOWN
    }

    /**
     * @return The newest standard whose capability element was present. Legacy 2.4 GHz networks
     * are told apart by their rates (CCK only b, OFDM only g, both bg).
     */
    @JvmStatic
    fun standard(phyFlags: Int, band: Band, rates: ByteString = ByteString.EMPTY): Standard {
        val has = { flag: PhyFlag -> phyFlags and flag.number != 0 }
        return when {
            has(PhyFlag.PHY_FLAG_EHT) -> Standard.IEEE80211BE
            has(PhyFlag.PHY_FLAG_HE) -> Standard.IEEE80211AX
            has(PhyFlag.PHY_FLAG_VHT) && band == Band.BAND_GHZ_5 -> Standard.IEEE80211AC
            has(PhyFlag.PHY_FLAG_HT) || has(PhyFlag.PHY_FLAG_VHT) -> Standard.IEEE80211N
            band == Band.BAND_GHZ_5 -> Standard.IEEE80211A
            band == Band.BAND_GHZ_2_4 -> legacy24(rates)
            else -> Standard.UNKNOWN
        }
    }

    private fun legacy24(rates: ByteString): Standard {
        val values = rates.toByteArray().map { it.toInt() and RATE_VALUE_MASK }.toSet()
        val cck = values.any { it in CCK_RATES }
        val ofdm = values.any { it in OFDM_RATES }
        return when {
            cck && ofdm -> Standard.IEEE80211BG
            cck -> Standard.IEEE80211B
            ofdm -> Standard.IEEE80211G
            else -> Standard.UNKNOWN
        }
    }
}
