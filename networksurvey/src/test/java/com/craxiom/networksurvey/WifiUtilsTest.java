package com.craxiom.networksurvey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.craxiom.messaging.wifi.CipherSuite;
import com.craxiom.messaging.wifi.EncryptionType;
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants;
import com.craxiom.networksurvey.util.WifiCapabilitiesUtils;

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

        assertEquals(EncryptionType.WPA2, WifiCapabilitiesUtils.getEncryptionType(capabilities));
        assertTrue(WifiCapabilitiesUtils.supportsWps(capabilities));
    }

    @Test
    public void validateWpa2WpaWps()
    {
        final String capabilities = "[WPA2][RSN][WPA][ESS][WPS]";

        assertEquals(EncryptionType.WPA_WPA2, WifiCapabilitiesUtils.getEncryptionType(capabilities));
        assertTrue(WifiCapabilitiesUtils.supportsWps(capabilities));
    }

    @Test
    public void validateWpa2Wpa()
    {
        final String capabilities = "[WPA][WPA2][RSN][ESS]";

        assertEquals(EncryptionType.WPA_WPA2, WifiCapabilitiesUtils.getEncryptionType(capabilities));
        assertFalse(WifiCapabilitiesUtils.supportsWps(capabilities));
    }

    @Test
    public void validateOpenEncryptionType()
    {
        final String capabilities = "[ESS]";

        assertEquals(EncryptionType.OPEN, WifiCapabilitiesUtils.getEncryptionType(capabilities));
        assertFalse(WifiCapabilitiesUtils.supportsWps(capabilities));
    }

    @Test
    public void validateLongFormWpa2Wps()
    {
        final String capabilities = "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS][WPS]";

        assertEquals(EncryptionType.WPA2, WifiCapabilitiesUtils.getEncryptionType(capabilities));
        assertTrue(WifiCapabilitiesUtils.supportsWps(capabilities));
    }

    @Test
    public void validateLongFormWpa2WpaWps()
    {
        final String capabilities = "[WPA2-PSK-CCMP][RSN-PSK-CCMP][WPA-PSK-CCMP][ESS][WPS]";

        assertEquals(EncryptionType.WPA_WPA2, WifiCapabilitiesUtils.getEncryptionType(capabilities));
        assertTrue(WifiCapabilitiesUtils.supportsWps(capabilities));
    }

    @Test
    public void validateLongFormWpa2WpaCcmpAndTkip()
    {
        final String capabilities = "[WPA-PSK-CCMP+TKIP][WPA2-PSK-CCMP+TKIP][RSN-PSK-CCMP+TKIP][ESS]";

        assertEquals(EncryptionType.WPA_WPA2, WifiCapabilitiesUtils.getEncryptionType(capabilities));
        assertFalse(WifiCapabilitiesUtils.supportsWps(capabilities));
    }

    @Test
    public void validateWpa3()
    {
        final String capabilities = "[RSN-SAE-CCMP][ESS][MFPR][MFPC]";

        assertEquals(EncryptionType.WPA3, WifiCapabilitiesUtils.getEncryptionType(capabilities));
        assertFalse(WifiCapabilitiesUtils.supportsWps(capabilities));
    }

    @Test
    public void validateWpa2Wpa3()
    {
        final String capabilities = "[WPA2-PSK-CCMP][RSN-PSK+PSK-SHA256+SAE-CCMP][ESS][MFPC]";

        assertEquals(EncryptionType.WPA2_WPA3, WifiCapabilitiesUtils.getEncryptionType(capabilities));
        assertFalse(WifiCapabilitiesUtils.supportsWps(capabilities));
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
    }
}
