package com.craxiom.networksurvey.ui.wifi.model

import com.craxiom.messaging.wifi.AkmSuite
import com.craxiom.messaging.wifi.CipherSuite
import com.craxiom.networksurvey.board.v1.PhyFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Wi-Fi details values worked out from what an NS Scout heard. */
class ScoutApTextTest {

    @Test
    fun keyManagement_namesTheSuites_withoutOpenOrUnknown() {
        val akms = listOf(AkmSuite.SAE, AkmSuite.PSK, AkmSuite.OPEN, AkmSuite.UNKNOWN, AkmSuite.SAE).map { it.number }
        assertEquals(listOf("SAE", "PSK"), ScoutApText.akmNames(akms))
    }

    @Test
    fun enterprise_isAny8021x_personalIsAPassphrase_openIsNeither() {
        assertEquals(true, ScoutApText.isEnterprise(listOf(AkmSuite.PSK.number, AkmSuite.IEEE_8021X.number)))
        assertEquals(false, ScoutApText.isEnterprise(listOf(AkmSuite.SAE.number)))
        assertNull(ScoutApText.isEnterprise(listOf(AkmSuite.OPEN.number)))
        assertNull(ScoutApText.isEnterprise(emptyList()))
    }

    @Test
    fun ciphers_useTheirStandardNames() {
        val ciphers = listOf(CipherSuite.CCMP, CipherSuite.GCMP_256, CipherSuite.OPEN, CipherSuite.CCMP).map { it.number }
        assertEquals(listOf("CCMP-128", "GCMP-256"), ScoutApText.cipherNames(ciphers))
    }

    @Test
    fun theBeaconInterval_isInMilliseconds_andChannelUseAPercent() {
        assertEquals(102, ScoutApText.beaconIntervalMs(100))
        assertEquals(0, ScoutApText.channelUsePercent(0))
        assertEquals(23, ScoutApText.channelUsePercent(59))
        assertEquals(100, ScoutApText.channelUsePercent(255))
    }

    @Test
    fun generations_comeFromTheCapabilityElements() {
        assertEquals(listOf(4, 5, 6), ScoutApText.generations(PhyFlag.PHY_FLAG_HT.number or PhyFlag.PHY_FLAG_VHT.number or PhyFlag.PHY_FLAG_HE.number))
        assertEquals(listOf(7), ScoutApText.generations(PhyFlag.PHY_FLAG_EHT.number))
        assertEquals(emptyList<Int>(), ScoutApText.generations(0))
    }
}
