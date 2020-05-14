package com.craxiom.networksurvey;

import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants;
import com.craxiom.networksurvey.messaging.CipherSuite;
import com.craxiom.networksurvey.messaging.EncryptionType;
import com.craxiom.networksurvey.util.WifiCapabilitiesUtils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

        assertEquals(EncryptionType.ENC_WPA2, WifiCapabilitiesUtils.getEncryptionType(capabilities));
        assertTrue(WifiCapabilitiesUtils.supportsWps(capabilities));
    }

    @Test
    public void validateWpa2WpaWps()
    {
        final String capabilities = "[WPA2][RSN][WPA][ESS][WPS]";

        assertEquals(EncryptionType.ENC_WPA_WPA2, WifiCapabilitiesUtils.getEncryptionType(capabilities));
        assertTrue(WifiCapabilitiesUtils.supportsWps(capabilities));
    }

    @Test
    public void validateWpa2Wpa()
    {
        final String capabilities = "[WPA][WPA2][RSN][ESS]";

        assertEquals(EncryptionType.ENC_WPA_WPA2, WifiCapabilitiesUtils.getEncryptionType(capabilities));
        assertFalse(WifiCapabilitiesUtils.supportsWps(capabilities));
    }

    @Test
    public void validateOpenEncryptionType()
    {
        final String capabilities = "[ESS]";

        assertEquals(EncryptionType.ENC_OPEN, WifiCapabilitiesUtils.getEncryptionType(capabilities));
        assertFalse(WifiCapabilitiesUtils.supportsWps(capabilities));
    }

    @Test
    public void validateEncryptionTypeString()
    {
        assertEquals("Unknown", WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.ENC_UNKNOWN));
        assertEquals("Open", WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.ENC_OPEN));
        assertEquals("WEP", WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.ENC_WEP));
        assertEquals("WPA", WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.ENC_WPA));
        assertEquals("WPA/WPA2", WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.ENC_WPA_WPA2));
        assertEquals("WPA2", WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.ENC_WPA2));
        assertEquals("WPA3", WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.ENC_WPA3));
        assertEquals("", WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.UNRECOGNIZED));
    }

    @Test
    public void validateCipherSuiteString()
    {
        assertEquals("Unknown", WifiBeaconMessageConstants.getCipherSuiteString(CipherSuite.CIPHER_UNKNOWN));
        assertEquals("WEP-40", WifiBeaconMessageConstants.getCipherSuiteString(CipherSuite.CIPHER_WEP_40));
        assertEquals("TKIP", WifiBeaconMessageConstants.getCipherSuiteString(CipherSuite.CIPHER_TKIP));
        assertEquals("CCMP", WifiBeaconMessageConstants.getCipherSuiteString(CipherSuite.CIPHER_CCMP));
        assertEquals("WEP-104", WifiBeaconMessageConstants.getCipherSuiteString(CipherSuite.CIPHER_WEP_104));
        assertEquals("Open", WifiBeaconMessageConstants.getCipherSuiteString(CipherSuite.CIPHER_OPEN));
        assertEquals("WEP", WifiBeaconMessageConstants.getCipherSuiteString(CipherSuite.CIPHER_WEP));
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
