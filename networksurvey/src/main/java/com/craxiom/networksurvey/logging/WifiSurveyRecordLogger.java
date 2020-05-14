package com.craxiom.networksurvey.logging;

import android.os.Looper;
import android.util.Log;

import com.craxiom.networksurvey.constants.LteMessageConstants;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants;
import com.craxiom.networksurvey.messaging.CdmaRecord;
import com.craxiom.networksurvey.messaging.CipherSuite;
import com.craxiom.networksurvey.messaging.GsmRecord;
import com.craxiom.networksurvey.messaging.LteRecord;
import com.craxiom.networksurvey.messaging.UmtsRecord;
import com.craxiom.networksurvey.messaging.WifiBeaconRecord;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.core.srs.SpatialReferenceSystem;
import mil.nga.geopackage.db.GeoPackageDataType;
import mil.nga.geopackage.features.user.FeatureColumn;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.features.user.FeatureRow;
import mil.nga.geopackage.geom.GeoPackageGeometryData;
import mil.nga.sf.Point;

/**
 * Responsible for taking 802.11 survey records, and writing them to the GeoPackage log file.
 *
 * @since 0.1.2
 */
public class WifiSurveyRecordLogger extends SurveyRecordLogger
{
    private static final String LOG_TAG = WifiSurveyRecordLogger.class.getSimpleName();

    /**
     * Constructs a Logger that writes 802.11 Survey records to a GeoPackage SQLite database.
     *
     * @param networkSurveyService The Service instance that is running this logger.
     * @param serviceLooper        The Looper associated with the service that can be used to do any background processing.
     */
    public WifiSurveyRecordLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper)
    {
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.WIFI_FILE_NAME_PREFIX);
    }

    @Override
    public void onGsmSurveyRecord(GsmRecord gsmRecord)
    {
    }

    @Override
    public void onCdmaSurveyRecord(CdmaRecord cdmaRecord)
    {
    }

    @Override
    public void onUmtsSurveyRecord(UmtsRecord umtsRecord)
    {
    }

    @Override
    public void onLteSurveyRecord(LteRecord lteRecord)
    {
    }

    @Override
    public void onWifiBeaconSurveyRecord(WifiBeaconRecord wifiBeaconRecord)
    {
        writeWifiBeaconRecordToLogFile(wifiBeaconRecord);
    }

    @Override
    void createTables(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException
    {
        createWifiBeaconRecordTable(geoPackage, srs);
    }

    /**
     * Creates an GeoPackage Table that can be populated with 802.11 Beacon Records.
     *
     * @param geoPackage The GeoPackage to create the table in.
     * @param srs        The SRS to use for the table coordinates.
     * @throws SQLException If there is a problem working with the GeoPackage SQLite DB.
     */
    private void createWifiBeaconRecordTable(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException
    {
        createTable(LteMessageConstants.LTE_RECORDS_TABLE_NAME, geoPackage, srs, false, (tableColumns, columnNumber) -> {
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, WifiBeaconMessageConstants.BSSID_COLUMN, GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, WifiBeaconMessageConstants.SSID_COLUMN, GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, WifiBeaconMessageConstants.CHANNEL_COLUMN, GeoPackageDataType.SMALLINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, WifiBeaconMessageConstants.FREQUENCY_MHZ_COLUMN, GeoPackageDataType.MEDIUMINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, WifiBeaconMessageConstants.CIPHER_SUITES_COLUMN, GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, WifiBeaconMessageConstants.AKM_SUITES_COLUMN, GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, WifiBeaconMessageConstants.ENCRYPTION_TYPE_COLUMN, GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, WifiBeaconMessageConstants.WPS_COLUMN, GeoPackageDataType.BOOLEAN, false, null));
            //noinspection UnusedAssignment
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, WifiBeaconMessageConstants.SIGNAL_STRENGTH_COLUMN, GeoPackageDataType.FLOAT, false, null));
        });
    }

    /**
     * Given an 802.11 Beacon Record, write it to the GeoPackage log file.
     *
     * @param wifiBeaconRecord The 802.11 Beacon Record to write to the log file.
     */
    private void writeWifiBeaconRecordToLogFile(final WifiBeaconRecord wifiBeaconRecord)
    {
        if (!loggingEnabled)
        {
            Log.v(LOG_TAG, "Not writing the log file because logging is turned off");
            return;
        }

        handler.post(() -> {
            try
            {
                if (geoPackage != null)
                {
                    FeatureDao featureDao = geoPackage.getFeatureDao(WifiBeaconMessageConstants.WIFI_BEACON_RECORDS_TABLE_NAME);
                    FeatureRow row = featureDao.newRow();

                    Point fix = new Point(wifiBeaconRecord.getLongitude(), wifiBeaconRecord.getLatitude(), (double) wifiBeaconRecord.getAltitude());

                    GeoPackageGeometryData geomData = new GeoPackageGeometryData(WGS84_SRS);
                    geomData.setGeometry(fix);

                    row.setGeometry(geomData);

                    row.setValue(WifiBeaconMessageConstants.TIME_COLUMN, wifiBeaconRecord.getDeviceTime());
                    row.setValue(WifiBeaconMessageConstants.RECORD_NUMBER_COLUMN, wifiBeaconRecord.getRecordNumber());

                    final String sourceAddress = wifiBeaconRecord.getSourceAddress();
                    if (!sourceAddress.isEmpty())
                    {
                        row.setValue(WifiBeaconMessageConstants.SOURCE_ADDRESS_COLUMN, sourceAddress);
                    }

                    final String bssid = wifiBeaconRecord.getBssid();
                    if (!bssid.isEmpty()) row.setValue(WifiBeaconMessageConstants.BSSID_COLUMN, bssid);

                    final String ssid = wifiBeaconRecord.getSsid();
                    if (!ssid.isEmpty()) row.setValue(WifiBeaconMessageConstants.SSID_COLUMN, ssid);

                    if (wifiBeaconRecord.hasChannel())
                    {
                        setShortValue(row, WifiBeaconMessageConstants.CHANNEL_COLUMN, wifiBeaconRecord.getChannel().getValue());
                    }

                    if (wifiBeaconRecord.hasFrequency())
                    {
                        setIntValue(row, WifiBeaconMessageConstants.FREQUENCY_MHZ_COLUMN, wifiBeaconRecord.getFrequency().getValue());
                    }

                    final List<CipherSuite> cipherSuitesList = wifiBeaconRecord.getCipherSuitesList();
                    if (!cipherSuitesList.isEmpty())
                    {
                        row.setValue(WifiBeaconMessageConstants.CIPHER_SUITES_COLUMN,
                                cipherSuitesList.stream().map(WifiBeaconMessageConstants::getCipherSuiteString)
                                        .collect(Collectors.joining(";")));
                    }

                    featureDao.insert(row);
                }
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Something went wrong when trying to write an LTE survey record", e);
            }
        });
    }
}
