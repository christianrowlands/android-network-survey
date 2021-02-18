package com.craxiom.networksurvey.services;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import androidx.preference.PreferenceManager;

import com.craxiom.messaging.BluetoothRecord;
import com.craxiom.messaging.BluetoothRecordData;
import com.craxiom.networksurvey.GpsListener;
import com.craxiom.networksurvey.constants.BluetoothMessageConstants;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
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
public class BluetoothSurveyRecordGenerationTest
{
    private final double latitude = 35.1983;
    private final double longitude = 111.6513;
    private final double altitude = 6909.7;
    private final String deviceId = "unittest-bluetooth-device";
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
    public void validateGenerateBluetoothSurveyRecord()
    {
        int rssi = 12;
        int txPowerLevel = 13;
        String sourceAddress = "00:11:22:33:FF:EE";

        BluetoothDevice bluetoothDevice = mock(BluetoothDevice.class);
        when(bluetoothDevice.getAddress()).thenReturn(sourceAddress);
        when(bluetoothDevice.getName()).thenReturn(deviceId);
        when(bluetoothDevice.getType()).thenReturn(BluetoothDevice.DEVICE_TYPE_LE);

        lenient().when(sharedPreferences.getString(NetworkSurveyConstants.PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS, String.valueOf(NetworkSurveyConstants.DEFAULT_BLUETOOTH_SCAN_INTERVAL_SECONDS))).thenReturn(NetworkSurveyConstants.PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS);

        BluetoothRecord actualRecord = new SurveyRecordProcessor(gpsListener, deviceId, context).generateBluetoothSurveyRecord(bluetoothDevice, rssi, txPowerLevel);
        BluetoothRecordData actualRecordData = actualRecord.getData();

        try (AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions())
        {
            softly.assertThat(actualRecord).as("Generated Bluetooth record is not null").isNotNull();
            softly.assertThat(actualRecordData.getAltitude()).as("Altitude is expected value").isEqualTo((float) altitude);
            softly.assertThat(actualRecordData.getDeviceSerialNumber()).as("Device serial number is expected value").isEqualTo(deviceId);
            softly.assertThat(actualRecordData.getDeviceTime()).as("Device time is expected value").isBetween(IOUtils.getRfc3339String(ZonedDateTime.now().minusMinutes(timeOffset)), IOUtils.getRfc3339String(ZonedDateTime.now().plusMinutes(timeOffset)));
            softly.assertThat(actualRecordData.getLatitude()).as("Latitude is expected value").isEqualTo((float) latitude, within(.001));
            softly.assertThat(actualRecordData.getLongitude()).as("Longitude is expected value").isEqualTo((float) longitude, within(.001));
            softly.assertThat(actualRecordData.getMissionId()).as("MissionId is expected value").contains("NS " + deviceId);
            softly.assertThat(actualRecordData.getOtaDeviceName()).as("OTA Device Name is expected value").isEqualTo(deviceId);
            softly.assertThat(actualRecordData.getRecordNumber()).as("Record Number is expected value.").isEqualTo(1);
            softly.assertThat(actualRecordData.getSignalStrength().getValue()).as("Signal strength is expected value").isEqualTo((float) rssi);
            softly.assertThat(actualRecordData.getSupportedTechnologies()).as("Supported Technologies is expected value").isEqualTo(BluetoothMessageConstants.getSupportedTechnologies(BluetoothDevice.DEVICE_TYPE_LE));
            softly.assertThat(actualRecordData.getTxPower().getValue()).as("Tx power is expected value").isEqualTo((float) txPowerLevel);
            softly.assertThat(actualRecordData.getSourceAddress()).as("Source Address is expected value.").isEqualTo(sourceAddress);
            softly.assertThat(actualRecord.getMessageType()).as("Message type is expected value").isEqualTo("BluetoothRecord");
        }
    }
}
