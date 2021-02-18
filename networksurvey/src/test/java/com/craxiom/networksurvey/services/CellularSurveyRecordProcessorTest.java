package com.craxiom.networksurvey.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;

import androidx.preference.PreferenceManager;

import com.craxiom.messaging.CdmaRecord;
import com.craxiom.messaging.CdmaRecordData;
import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.GsmRecordData;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.LteRecordData;
import com.craxiom.messaging.UmtsRecord;
import com.craxiom.messaging.UmtsRecordData;
import com.craxiom.networksurvey.BuildConfig;
import com.craxiom.networksurvey.GpsListener;
import com.craxiom.networksurvey.constants.LteMessageConstants;
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
public class CellularSurveyRecordProcessorTest
{
    private final int dbm = 89;
    private final int timingAdvance = 2;
    private final int mcc = 123;
    private final int mnc = 456;
    private final int lac = 12;
    private final int cid = 45;
    private final double latitude = 35.1983;
    private final double longitude = 111.6513;
    private final double altitude = 6909.7;
    private final String deviceId = "unittest-cellular-device";
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
    public void validateGenerateGsmSurveyRecord()
    {
        int arfcn = 67;
        int bsic = 89;

        CellInfoGsm cellInfoGsm = mock(CellInfoGsm.class);
        CellIdentityGsm cellIdentityGsm = mock(CellIdentityGsm.class);
        CellSignalStrengthGsm cellSignalStrengthGsm = mock(CellSignalStrengthGsm.class);
        when(cellSignalStrengthGsm.getDbm()).thenReturn(dbm);
        when(cellSignalStrengthGsm.getTimingAdvance()).thenReturn(timingAdvance);
        when(cellInfoGsm.getCellSignalStrength()).thenReturn(cellSignalStrengthGsm);
        when(cellIdentityGsm.getMcc()).thenReturn(mcc);
        when(cellIdentityGsm.getMnc()).thenReturn(mnc);
        when(cellIdentityGsm.getLac()).thenReturn(lac);
        when(cellIdentityGsm.getCid()).thenReturn(cid);

        when(cellIdentityGsm.getArfcn()).thenReturn(arfcn);
        when(cellIdentityGsm.getBsic()).thenReturn(bsic);
        when(cellInfoGsm.getCellIdentity()).thenReturn(cellIdentityGsm);
        when(cellInfoGsm.isRegistered()).thenReturn(true);

        lenient().when(sharedPreferences.getString(NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS, String.valueOf(NetworkSurveyConstants.DEFAULT_CELLULAR_SCAN_INTERVAL_SECONDS))).thenReturn(NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS);

        GsmRecord actualRecord = new SurveyRecordProcessor(gpsListener, deviceId, context).generateGsmSurveyRecord(cellInfoGsm);
        GsmRecordData actualRecordData = actualRecord.getData();

        try (AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions())
        {
            softly.assertThat(actualRecord).as("Generated GSM Record is not null").isNotNull();
            softly.assertThat(actualRecordData.getAltitude()).as("Altitude is expected value").isEqualTo((float) altitude);
            softly.assertThat(actualRecordData.getArfcn().getValue()).as("ARFCN is expected value").isEqualTo(arfcn);
            softly.assertThat(actualRecordData.getBsic().getValue()).as("BSIC is expected value").isEqualTo(bsic);
            softly.assertThat(actualRecordData.getCi().getValue()).as("CI is expected value").isEqualTo(cid);
            softly.assertThat(actualRecordData.getDeviceSerialNumber()).as("Device serial number is expected value").isEqualTo(deviceId);
            softly.assertThat(actualRecordData.getDeviceTime()).as("Device time is expected value").isBetween(IOUtils.getRfc3339String(ZonedDateTime.now().minusMinutes(timeOffset)), IOUtils.getRfc3339String(ZonedDateTime.now().plusMinutes(timeOffset)));
            softly.assertThat(actualRecordData.getLac().getValue()).as("LAC is expected value").isEqualTo(lac);
            softly.assertThat(actualRecordData.getLatitude()).as("Latitude is expected value").isEqualTo((float) latitude, within(.001));
            softly.assertThat(actualRecordData.getLongitude()).as("Longitude is expected value").isEqualTo((float) longitude, within(.001));
            softly.assertThat(actualRecordData.getMissionId()).as("MissionId is expected value").contains("NS " + deviceId);
            softly.assertThat(actualRecordData.getMcc().getValue()).as("MCC is expected value").isEqualTo(mcc);
            softly.assertThat(actualRecordData.getMnc().getValue()).as("MNC is expected value").isEqualTo(mnc);
            softly.assertThat(actualRecordData.getRecordNumber()).as("Record Number is expected value.").isEqualTo(1);
            softly.assertThat(actualRecordData.getSignalStrength().getValue()).as("Signal strength is expected value").isEqualTo((float) dbm);
            softly.assertThat(actualRecordData.getTa().getValue()).as("TA is expected value").isEqualTo(timingAdvance);
            softly.assertThat(actualRecordData.getServingCell().getValue()).as("Serving Cell is expected value").isTrue();
            softly.assertThat(actualRecord.getVersion()).as("Record version is not null").isNotNull();
            softly.assertThat(actualRecord.getMessageType()).as("Message type is expected value").isEqualTo("GsmRecord");
        }
    }

    @Test
    public void validateGenerateCdmaSurveyRecord()
    {
        int ecio = 654;
        int sid = 321;
        int nid = 432;
        int bsid = 543;

        CellInfoCdma cellInfoCdma = mock(CellInfoCdma.class);
        CellIdentityCdma cellIdentityCdma = mock(CellIdentityCdma.class);
        CellSignalStrengthCdma cellSignalStrengthCdma = mock(CellSignalStrengthCdma.class);
        when(cellInfoCdma.getCellIdentity()).thenReturn(cellIdentityCdma);
        when(cellInfoCdma.getCellSignalStrength()).thenReturn(cellSignalStrengthCdma);
        when(cellSignalStrengthCdma.getCdmaDbm()).thenReturn(dbm);
        when(cellSignalStrengthCdma.getCdmaEcio()).thenReturn(ecio);
        when(cellIdentityCdma.getSystemId()).thenReturn(sid);
        when(cellIdentityCdma.getNetworkId()).thenReturn(nid);
        when(cellIdentityCdma.getBasestationId()).thenReturn(bsid);
        when(cellInfoCdma.isRegistered()).thenReturn(true);

        lenient().when(sharedPreferences.getString(NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS, String.valueOf(NetworkSurveyConstants.DEFAULT_CELLULAR_SCAN_INTERVAL_SECONDS))).thenReturn(NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS);

        CdmaRecord actualRecord = new SurveyRecordProcessor(gpsListener, deviceId, context).generateCdmaSurveyRecord(cellInfoCdma);
        CdmaRecordData actualRecordData = actualRecord.getData();

        try (AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions())
        {
            softly.assertThat(actualRecord).as("Generated CDMA Record is not null").isNotNull();
            softly.assertThat(actualRecordData.getAltitude()).as("Altitude is expected value").isEqualTo((float) altitude);
            softly.assertThat(actualRecordData.getBsid().getValue()).as("BSID is expected value").isEqualTo(bsid);
            softly.assertThat(actualRecordData.getEcio().getValue()).as("ECIO is expected value").isEqualTo((float) ecio / 10); // Convert the Ec/Io to the actual value.  The Android Javadocs indicate:  "Get the CDMA Ec/Io value in dB*10".  So we need to divide by 10.
            softly.assertThat(actualRecordData.getDeviceSerialNumber()).as("Device serial number is expected value").isEqualTo(deviceId);
            softly.assertThat(actualRecordData.getDeviceTime()).as("Device time is expected value").isBetween(IOUtils.getRfc3339String(ZonedDateTime.now().minusMinutes(timeOffset)), IOUtils.getRfc3339String(ZonedDateTime.now().plusMinutes(timeOffset)));
            softly.assertThat(actualRecordData.getLatitude()).as("Latitude is expected value").isEqualTo((float) latitude, within(.001));
            softly.assertThat(actualRecordData.getLongitude()).as("Longitude is expected value").isEqualTo((float) longitude, within(.001));
            softly.assertThat(actualRecordData.getMissionId()).as("MissionId is expected value").contains("NS " + deviceId);
            softly.assertThat(actualRecordData.getNid().getValue()).as("NID is expected value").isEqualTo(nid);
            softly.assertThat(actualRecordData.getRecordNumber()).as("Record Number is expected value.").isEqualTo(1);
            softly.assertThat(actualRecordData.getSid().getValue()).as("SID is expected value").isEqualTo(sid);
            softly.assertThat(actualRecordData.getSignalStrength().getValue()).as("Signal strength is expected value").isEqualTo((float) dbm);
            softly.assertThat(actualRecordData.getServingCell().getValue()).as("Serving Cell is expected value").isTrue();
            softly.assertThat(actualRecord.getVersion()).as("Record version is not null").isNotNull();
            softly.assertThat(actualRecord.getMessageType()).as("Message type is expected value").isEqualTo("CdmaRecord");
        }
    }

    @Test
    public void validatedGenerateUmtsSurveyRecord()
    {
        int uarfcn = 67;
        int psc = 78;

        CellInfoWcdma cellInfoWcdma = mock(CellInfoWcdma.class);
        CellIdentityWcdma cellIdentityWcdma = mock(CellIdentityWcdma.class);
        CellSignalStrengthWcdma cellSignalStrengthWcdma = mock(CellSignalStrengthWcdma.class);
        when(cellSignalStrengthWcdma.getDbm()).thenReturn(dbm);
        when(cellInfoWcdma.getCellSignalStrength()).thenReturn(cellSignalStrengthWcdma);
        when(cellIdentityWcdma.getMcc()).thenReturn(mcc);
        when(cellIdentityWcdma.getMnc()).thenReturn(mnc);
        when(cellIdentityWcdma.getLac()).thenReturn(lac);
        when(cellIdentityWcdma.getCid()).thenReturn(cid);
        when(cellIdentityWcdma.getUarfcn()).thenReturn(uarfcn);
        when(cellIdentityWcdma.getPsc()).thenReturn(psc);
        when(cellInfoWcdma.getCellIdentity()).thenReturn(cellIdentityWcdma);
        when(cellInfoWcdma.isRegistered()).thenReturn(true);

        lenient().when(sharedPreferences.getString(NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS, String.valueOf(NetworkSurveyConstants.DEFAULT_CELLULAR_SCAN_INTERVAL_SECONDS))).thenReturn(NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS);

        UmtsRecord actualRecord = new SurveyRecordProcessor(gpsListener, deviceId, context).generateUmtsSurveyRecord(cellInfoWcdma);
        UmtsRecordData actualRecordData = actualRecord.getData();

        try (AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions())
        {
            softly.assertThat(actualRecord).as("Generated UMTS Record is not null").isNotNull();
            softly.assertThat(actualRecordData.getAltitude()).as("Altitude is expected value").isEqualTo((float) altitude);
            softly.assertThat(actualRecordData.getCid().getValue()).as("CID is expected value").isEqualTo(cid);
            softly.assertThat(actualRecordData.getDeviceSerialNumber()).as("Device serial number is expected value").isEqualTo(deviceId);
            softly.assertThat(actualRecordData.getDeviceTime()).as("Device time is expected value").isBetween(IOUtils.getRfc3339String(ZonedDateTime.now().minusMinutes(timeOffset)), IOUtils.getRfc3339String(ZonedDateTime.now().plusMinutes(timeOffset)));
            softly.assertThat(actualRecordData.getLac().getValue()).as("LAC is expected value").isEqualTo(lac);
            softly.assertThat(actualRecordData.getLatitude()).as("Latitude is expected value").isEqualTo((float) latitude, within(.001));
            softly.assertThat(actualRecordData.getLongitude()).as("Longitude is expected value").isEqualTo((float) longitude, within(.001));
            softly.assertThat(actualRecordData.getMissionId()).as("MissionId is expected value").contains("NS " + deviceId);
            softly.assertThat(actualRecordData.getMcc().getValue()).as("MCC is expected value").isEqualTo(mcc);
            softly.assertThat(actualRecordData.getMnc().getValue()).as("MNC is expected value").isEqualTo(mnc);
            softly.assertThat(actualRecordData.getPsc().getValue()).as("PSC is expected value").isEqualTo(psc);
            softly.assertThat(actualRecordData.getRecordNumber()).as("Record Number is expected value.").isEqualTo(1);
            softly.assertThat(actualRecordData.getSignalStrength().getValue()).as("Signal strength is expected value").isEqualTo((float) dbm);
            softly.assertThat(actualRecordData.getServingCell().getValue()).as("Serving Cell is expected value").isTrue();
            softly.assertThat(actualRecordData.getUarfcn().getValue()).as("UARFCN is expected value").isEqualTo(uarfcn);
            softly.assertThat(actualRecord.getVersion()).as("Record version is not null").isNotNull();
            softly.assertThat(actualRecord.getMessageType()).as("Message type is expected value").isEqualTo("UmtsRecord");
        }
    }

    @Test
    public void validateGenerateLteSurveyRecord()
    {
        int rsrp = 43;
        int rsrq = 44;
        int eci = 41;
        int earfnc = 98;
        int pci = 99;
        int tac = 42;

        CellInfoLte cellInfoLte = mock(CellInfoLte.class);
        CellIdentityLte cellIdentityLte = mock(CellIdentityLte.class);
        CellSignalStrengthLte cellSignalStrengthLte = mock(CellSignalStrengthLte.class);
        when(cellSignalStrengthLte.getRsrp()).thenReturn(rsrp);
        when(cellSignalStrengthLte.getRsrq()).thenReturn(rsrq);
        when(cellSignalStrengthLte.getTimingAdvance()).thenReturn(timingAdvance);
        when(cellInfoLte.getCellSignalStrength()).thenReturn(cellSignalStrengthLte);
        when(cellIdentityLte.getMcc()).thenReturn(mcc);
        when(cellIdentityLte.getMnc()).thenReturn(mnc);
        when(cellIdentityLte.getCi()).thenReturn(eci);
        when(cellIdentityLte.getEarfcn()).thenReturn(earfnc);
        when(cellIdentityLte.getPci()).thenReturn(pci);
        when(cellIdentityLte.getTac()).thenReturn(tac);
        when(cellInfoLte.getCellIdentity()).thenReturn(cellIdentityLte);
        when(cellInfoLte.isRegistered()).thenReturn(true);

        lenient().when(sharedPreferences.getString(NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS, String.valueOf(NetworkSurveyConstants.DEFAULT_CELLULAR_SCAN_INTERVAL_SECONDS))).thenReturn(NetworkSurveyConstants.PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS);

        LteRecord actualRecord = new SurveyRecordProcessor(gpsListener, deviceId, context).generateLteSurveyRecord(cellInfoLte);
        LteRecordData actualRecordData = actualRecord.getData();

        try (AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions())
        {
            softly.assertThat(actualRecord).as("Generated LTE Record is not null").isNotNull();
            softly.assertThat(actualRecordData.getAltitude()).as("Altitude is expected value").isEqualTo((float) altitude);
            softly.assertThat(actualRecordData.getDeviceSerialNumber()).as("Device serial number is expected value").isEqualTo(deviceId);
            softly.assertThat(actualRecordData.getDeviceTime()).as("Device time is expected value").isBetween(IOUtils.getRfc3339String(ZonedDateTime.now().minusMinutes(timeOffset)), IOUtils.getRfc3339String(ZonedDateTime.now().plusMinutes(timeOffset)));
            softly.assertThat(actualRecordData.getEarfcn().getValue()).as("EARFCN is expected value").isEqualTo(earfnc);
            softly.assertThat(actualRecordData.getEci().getValue()).as("ECI is expected value").isEqualTo(eci);
            softly.assertThat(actualRecordData.getPci().getValue()).as("PCI is expected value").isEqualTo(pci);
            softly.assertThat(actualRecordData.getLatitude()).as("Latitude is expected value").isEqualTo((float) latitude, within(.001));
            softly.assertThat(actualRecordData.getLongitude()).as("Longitude is expected value").isEqualTo((float) longitude, within(.001));
            softly.assertThat(actualRecordData.getMissionId()).as("MissionId is expected value").contains("NS " + deviceId);
            softly.assertThat(actualRecordData.getMcc().getValue()).as("MCC is expected value").isEqualTo(mcc);
            softly.assertThat(actualRecordData.getMnc().getValue()).as("MNC is expected value").isEqualTo(mnc);
            softly.assertThat(actualRecordData.getRecordNumber()).as("Record Number is expected value.").isEqualTo(1);
            softly.assertThat(actualRecordData.getRsrp().getValue()).as("RSRP is expected value").isEqualTo((float) rsrp);
            softly.assertThat(actualRecordData.getRsrq().getValue()).as("RSRQ is expected value").isEqualTo((float) rsrq);
            softly.assertThat(actualRecordData.getTa().getValue()).as("TA is expected value").isEqualTo(timingAdvance);
            softly.assertThat(actualRecordData.getTac().getValue()).as("TAC is expected value").isEqualTo(tac);
            softly.assertThat(actualRecordData.getServingCell().getValue()).as("Serving Cell is expected value").isTrue();
            softly.assertThat(actualRecord.getVersion()).as("Record version is expected value").isEqualTo(BuildConfig.MESSAGING_API_VERSION);
            softly.assertThat(actualRecord.getMessageType()).as("Message type is expected value").isEqualTo(LteMessageConstants.LTE_RECORD_MESSAGE_TYPE);
        }
    }
}