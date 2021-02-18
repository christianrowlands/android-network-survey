package com.craxiom.networksurvey.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.GnssMeasurement;
import android.location.GnssStatus;
import android.location.Location;

import androidx.preference.PreferenceManager;

import com.craxiom.messaging.GnssRecord;
import com.craxiom.messaging.GnssRecordData;
import com.craxiom.messaging.gnss.Constellation;
import com.craxiom.networksurvey.GpsListener;
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
public class GnssSurveyRecordGenerationTest
{
    private final double latitude = 35.1983;
    private final double longitude = 111.6513;
    private final double altitude = 6909.7;
    private final String deviceId = "unittest-gnss-device";
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
    public void validateGenerateGnssSurveyRecord()
    {
        float accuracy = 1.1f;
        float verticalAccuracy = 0.2f;
        int svid = 999;
        float carrierFreqHz = 123.4f;
        double agcl = 4.5;
        double cn0 = 5.6;

        when(fakeLocation.hasAccuracy()).thenReturn(true);
        when(fakeLocation.getAccuracy()).thenReturn(accuracy);
        when(fakeLocation.hasVerticalAccuracy()).thenReturn(true);
        when(fakeLocation.getVerticalAccuracyMeters()).thenReturn(verticalAccuracy);
        when(gpsListener.getLatestLocation()).thenReturn(fakeLocation);
        GnssMeasurement gnssMeasurement = mock(GnssMeasurement.class);
        when(gnssMeasurement.getConstellationType()).thenReturn(GnssStatus.CONSTELLATION_GPS);
        when(gnssMeasurement.getSvid()).thenReturn(svid);
        when(gnssMeasurement.hasCarrierFrequencyHz()).thenReturn(true);
        when(gnssMeasurement.getCarrierFrequencyHz()).thenReturn(carrierFreqHz);
        when(gnssMeasurement.hasAutomaticGainControlLevelDb()).thenReturn(true);
        when(gnssMeasurement.getAutomaticGainControlLevelDb()).thenReturn(agcl);
        when(gnssMeasurement.getCn0DbHz()).thenReturn(cn0);

        lenient().when(sharedPreferences.getString(NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS, String.valueOf(NetworkSurveyConstants.DEFAULT_GNSS_SCAN_INTERVAL_SECONDS))).thenReturn(NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS);

        GnssRecord actualRecord = new SurveyRecordProcessor(gpsListener, deviceId, context).generateGnssSurveyRecord(gnssMeasurement);
        GnssRecordData actualRecordData = actualRecord.getData();

        try (AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions())
        {
            softly.assertThat(actualRecord).as("Generated GNSS record is not null").isNotNull();
            softly.assertThat(actualRecordData.getConstellation()).as("Constellation is expected value").isEqualTo(Constellation.GPS);
            softly.assertThat(actualRecordData.getAltitude()).as("Altitude is expected value").isEqualTo((float) altitude);
            softly.assertThat(actualRecordData.getAltitudeStdDevM().getValue()).as("Altitude std. deviation is expected value").isEqualTo(verticalAccuracy);
            softly.assertThat(actualRecordData.getAgcDb().getValue()).as("AGCDB is expected value").isEqualTo((float) agcl);
            softly.assertThat(actualRecordData.getCarrierFreqHz().getValue()).as("Carrier Frequency number is expected value").isEqualTo((long) carrierFreqHz);
            softly.assertThat(actualRecordData.getCn0DbHz().getValue()).as("Cn0DBhz is expected value").isEqualTo((float) cn0);
            softly.assertThat(actualRecordData.getDeviceSerialNumber()).as("Device serial number is expected value").isEqualTo(deviceId);
            softly.assertThat(actualRecordData.getDeviceTime()).as("Device time is expected value").isBetween(IOUtils.getRfc3339String(ZonedDateTime.now().minusMinutes(timeOffset)), IOUtils.getRfc3339String(ZonedDateTime.now().plusMinutes(timeOffset)));
            softly.assertThat(actualRecordData.getLatitude()).as("Latitude is expected value").isEqualTo((float) latitude, within(.001));
            softly.assertThat(actualRecordData.getLatitudeStdDevM().getValue()).as("Latitude std. deviation is expected value").isEqualTo(accuracy);
            softly.assertThat(actualRecordData.getLongitude()).as("Longitude is expected value").isEqualTo((float) longitude, within(.001));
            softly.assertThat(actualRecordData.getLongitudeStdDevM().getValue()).as("Longitude std. deviation is expected value").isEqualTo(accuracy);
            softly.assertThat(actualRecordData.getMissionId()).as("MissionId is expected value").contains("NS " + deviceId);
            softly.assertThat(actualRecordData.getRecordNumber()).as("Record Number is expected value.").isEqualTo(1);
            softly.assertThat(actualRecordData.getSpaceVehicleId().getValue()).as("Signal strength is expected value").isEqualTo(svid);
            softly.assertThat(actualRecord.getMessageType()).as("Message type is expected value").isEqualTo("GnssRecord");
        }
    }
}
