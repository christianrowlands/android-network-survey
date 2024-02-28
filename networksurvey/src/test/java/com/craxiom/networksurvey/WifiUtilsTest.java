package com.craxiom.networksurvey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.craxiom.messaging.wifi.CipherSuite;
import com.craxiom.messaging.wifi.EncryptionType;
import com.craxiom.messaging.wifi.WifiBandwidth;
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants;
import com.craxiom.networksurvey.util.WifiUtils;

import org.junit.Test;

/**
 * Tests the various conversions for Wi-Fi scanning and logging.
 *
 * @since 0.1.2
 */
public class WifiUtilsTest
{
    @Test
    public void validateWpa2Wps()
    {
        final String capabilities = "[WPA2][RSN][ESS][WPS]";

        assertEquals(EncryptionType.WPA2, WifiUtils.getEncryptionType(capabilities));
        assertTrue(WifiUtils.supportsWps(capabilities));
    }

    @Test
    public void validateWpa2WpaWps()
    {
        final String capabilities = "[WPA2][RSN][WPA][ESS][WPS]";

        assertEquals(EncryptionType.WPA_WPA2, WifiUtils.getEncryptionType(capabilities));
        assertTrue(WifiUtils.supportsWps(capabilities));
    }

    @Test
    public void validateWpa2Wpa()
    {
        final String capabilities = "[WPA][WPA2][RSN][ESS]";

        assertEquals(EncryptionType.WPA_WPA2, WifiUtils.getEncryptionType(capabilities));
        assertFalse(WifiUtils.supportsWps(capabilities));
    }

    @Test
    public void validateOpenEncryptionType()
    {
        final String capabilities = "[ESS]";

        assertEquals(EncryptionType.OPEN, WifiUtils.getEncryptionType(capabilities));
        assertFalse(WifiUtils.supportsWps(capabilities));
    }

    @Test
    public void validateLongFormWpa2Wps()
    {
        final String capabilities = "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS][WPS]";

        assertEquals(EncryptionType.WPA2, WifiUtils.getEncryptionType(capabilities));
        assertTrue(WifiUtils.supportsWps(capabilities));
    }

    @Test
    public void validateLongFormWpa2WpaWps()
    {
        final String capabilities = "[WPA2-PSK-CCMP][RSN-PSK-CCMP][WPA-PSK-CCMP][ESS][WPS]";

        assertEquals(EncryptionType.WPA_WPA2, WifiUtils.getEncryptionType(capabilities));
        assertTrue(WifiUtils.supportsWps(capabilities));
    }

    @Test
    public void validateLongFormWpa2WpaCcmpAndTkip()
    {
        final String capabilities = "[WPA-PSK-CCMP+TKIP][WPA2-PSK-CCMP+TKIP][RSN-PSK-CCMP+TKIP][ESS]";

        assertEquals(EncryptionType.WPA_WPA2, WifiUtils.getEncryptionType(capabilities));
        assertFalse(WifiUtils.supportsWps(capabilities));
    }

    @Test
    public void validateWpa3()
    {
        final String capabilities = "[RSN-SAE-CCMP][ESS][MFPR][MFPC]";

        assertEquals(EncryptionType.WPA3, WifiUtils.getEncryptionType(capabilities));
        assertFalse(WifiUtils.supportsWps(capabilities));
    }

    @Test
    public void validateWpa2Wpa3()
    {
        final String capabilities = "[WPA2-PSK-CCMP][RSN-PSK+PSK-SHA256+SAE-CCMP][ESS][MFPC]";

        assertEquals(EncryptionType.WPA2_WPA3, WifiUtils.getEncryptionType(capabilities));
        assertFalse(WifiUtils.supportsWps(capabilities));
    }

    @Test
    public void validateEncryptionTypeString()
    {
        assertEquals("Unknown", WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.UNKNOWN));
        assertEquals("Open", WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.OPEN));
        assertEquals("WEP", WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.WEP));
        assertEquals("WPA", WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.WPA));
        assertEquals("WPA/WPA2", WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.WPA_WPA2));
        assertEquals("WPA2", WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.WPA2));
        assertEquals("WPA3", WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.WPA3));
        assertEquals("", WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.UNRECOGNIZED));
    }

    @Test
    public void validateCipherSuiteString()
    {
        assertEquals("Unknown", WifiBeaconMessageConstants.getCipherSuiteString(CipherSuite.UNKNOWN));
        assertEquals("WEP-40", WifiBeaconMessageConstants.getCipherSuiteString(CipherSuite.WEP_40));
        assertEquals("TKIP", WifiBeaconMessageConstants.getCipherSuiteString(CipherSuite.TKIP));
        assertEquals("CCMP", WifiBeaconMessageConstants.getCipherSuiteString(CipherSuite.CCMP));
        assertEquals("WEP-104", WifiBeaconMessageConstants.getCipherSuiteString(CipherSuite.WEP_104));
        assertEquals("Open", WifiBeaconMessageConstants.getCipherSuiteString(CipherSuite.OPEN));
        assertEquals("WEP", WifiBeaconMessageConstants.getCipherSuiteString(CipherSuite.WEP));
        assertEquals("", WifiBeaconMessageConstants.getCipherSuiteString(CipherSuite.UNRECOGNIZED));
    }

    @Test
    public void validateChannelConversion()
    {
        assertEquals(1, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(2412));
        assertEquals(6, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(2437));
        assertEquals(11, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(2462));
        assertEquals(12, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(2467));
        assertEquals(13, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(2472));
        assertEquals(14, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(2484));

        assertEquals(34, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(5170));
        assertEquals(40, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(5200));
        assertEquals(42, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(5210));
        assertEquals(52, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(5260));
        assertEquals(64, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(5320));
        assertEquals(100, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(5500));
        assertEquals(116, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(5580));
        assertEquals(140, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(5700));
        assertEquals(149, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(5745));
        assertEquals(157, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(5785));
        assertEquals(165, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(5825));

        assertEquals(1, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(5955));
        assertEquals(3, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(5965));
        assertEquals(5, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(5975));
        assertEquals(191, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(6905));
        assertEquals(233, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(7115));
        assertEquals(-1, WifiBeaconMessageConstants.convertFrequencyToChannelNumber(7120));
    }

    @Test
    public void testGetCenterChannel()
    {
        assertEquals(1, WifiUtils.getCenterChannel(1, WifiBandwidth.MHZ_20, 2412));
        assertEquals(11, WifiUtils.getCenterChannel(11, WifiBandwidth.MHZ_20, 2412));
        assertEquals(1, WifiUtils.getCenterChannel(1, WifiBandwidth.MHZ_40, 2462));
        assertEquals(7, WifiUtils.getCenterChannel(5, WifiBandwidth.MHZ_40, 2432));

        assertEquals(36, WifiUtils.getCenterChannel(36, WifiBandwidth.MHZ_20, 0));
        assertEquals(54, WifiUtils.getCenterChannel(52, WifiBandwidth.MHZ_40, 0));
        assertEquals(54, WifiUtils.getCenterChannel(56, WifiBandwidth.MHZ_40, 0));
        assertEquals(151, WifiUtils.getCenterChannel(149, WifiBandwidth.MHZ_40, 0));
        assertEquals(159, WifiUtils.getCenterChannel(161, WifiBandwidth.MHZ_40, 0));
        assertEquals(58, WifiUtils.getCenterChannel(60, WifiBandwidth.MHZ_80, 0));
        assertEquals(171, WifiUtils.getCenterChannel(169, WifiBandwidth.MHZ_80, 0));
        assertEquals(50, WifiUtils.getCenterChannel(36, WifiBandwidth.MHZ_160, 0));
        assertEquals(82, WifiUtils.getCenterChannel(80, WifiBandwidth.MHZ_160, 0));
        assertEquals(163, WifiUtils.getCenterChannel(157, WifiBandwidth.MHZ_160, 0));

        assertEquals(19, WifiUtils.getCenterChannel(19, WifiBandwidth.MHZ_40, 6045));
        assertEquals(207, WifiUtils.getCenterChannel(207, WifiBandwidth.MHZ_160, 6985));
        assertEquals(127, WifiUtils.getCenterChannel(127, WifiBandwidth.MHZ_320, 6585));
    }
}
