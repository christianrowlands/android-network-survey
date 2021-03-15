package com.craxiom.networksurvey.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.wifi.ScanResult;

import androidx.preference.PreferenceManager;

import com.craxiom.messaging.WifiBeaconRecord;
import com.craxiom.messaging.WifiBeaconRecordData;
import com.craxiom.messaging.wifi.EncryptionType;
import com.craxiom.networksurvey.GpsListener;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.model.WifiRecordWrapper;
import com.craxiom.networksurvey.util.IOUtils;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class WifiBeaconSurveyRecordGenerationTest
{
    private final double latitude = 35.1983;
    private final double longitude = 111.6513;
    private final double altitude = 6909.7;
    private final String deviceId = "unittest-wifi-device";
    private final int timeOffset = 1;

    Context context;
    GpsListener gpsListener;
    Location fakeLocation;
    SharedPreferences sharedPreferences;

    @Before
    public void setUp()
    {
        context = mock(Context.class);
        gpsListener = mock(GpsListener.class);
        fakeLocation = mock(Location.class);
        sharedPreferences = mock(SharedPreferences.class);
        when(fakeLocation.getLatitude()).thenReturn(latitude);
        when(fakeLocation.getLongitude()).thenReturn(longitude);
        when(fakeLocation.getAltitude()).thenReturn(altitude);
        when(gpsListener.getLatestLocation()).thenReturn(fakeLocation);
        when(PreferenceManager.getDefaultSharedPreferences(context)).thenReturn(sharedPreferences);
    }

    @Test
    public void validateGenerateWifiBeaconSurveyRecord()
    {
        String bssid = "48:d8:10:5f:0b:99";
        int level = 11;
        String ssid = "mynetwork";
        int frequency = 2417;
        String capabilities = "WPA2][RSN][ESS][WPS]";
        int expectedChannel = 2;

        ScanResult scanResult = new ScanResult();
        scanResult.BSSID = bssid;
        scanResult.level = level;
        scanResult.SSID = ssid;
        scanResult.frequency = frequency;
        scanResult.capabilities = capabilities;

        lenient().when(sharedPreferences.getString(NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS, String.valueOf(NetworkSurveyConstants.DEFAULT_WIFI_SCAN_INTERVAL_SECONDS))).thenReturn(NetworkSurveyConstants.PROPERTY_WIFI_SCAN_INTERVAL_SECONDS);

        WifiRecordWrapper actualWrapperRecord = new SurveyRecordProcessor(gpsListener, deviceId, context).generateWiFiBeaconSurveyRecord(scanResult);
        WifiBeaconRecord actualRecord = actualWrapperRecord.getWifiBeaconRecord();
        WifiBeaconRecordData actualRecordData = actualRecord.getData();

        try (AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions())
        {
            softly.assertThat(actualWrapperRecord).as("Generated WifiWrapper record is not null").isNotNull();
            softly.assertThat(actualRecord).as("Generated WifiRecord is not null").isNotNull();
            softly.assertThat(actualRecordData.getBssid()).as("BSSID is expected value").isEqualTo(bssid);
            softly.assertThat(actualRecordData.getChannel().getValue()).as("Channel is expected value").isEqualTo(expectedChannel);
            softly.assertThat(actualRecordData.getDeviceTime()).as("Device time is expected value").isBetween(IOUtils.getRfc3339String(ZonedDateTime.now().minusMinutes(timeOffset)), IOUtils.getRfc3339String(ZonedDateTime.now().plusMinutes(timeOffset)));
            softly.assertThat(actualRecordData.getEncryptionType()).as("Encryption type is expected value").isEqualTo(EncryptionType.WPA2);
            softly.assertThat(actualRecordData.getFrequencyMhz().getValue()).as("Frequency is expected value").isEqualTo(frequency);
            softly.assertThat(actualRecordData.getLatitude()).as("Latitude is expected value").isEqualTo((float) latitude, within(.001));
            softly.assertThat(actualRecordData.getLongitude()).as("Longitude is expected value").isEqualTo((float) longitude, within(.001));
            softly.assertThat(actualRecordData.getMissionId()).as("MissionId is expected value").contains("NS " + deviceId);
            softly.assertThat(actualRecordData.getRecordNumber()).as("Record Number is expected value.").isEqualTo(1);
            softly.assertThat(actualRecordData.getSignalStrength().getValue()).as("Signal strength is expected value.").isEqualTo((float) level);
            softly.assertThat(actualRecordData.getSsid()).as("SSID is expected value.").isEqualTo(ssid);
            softly.assertThat(actualRecordData.getWps().getValue()).as("WPS is expected value.").isTrue();
        }
    }
}
