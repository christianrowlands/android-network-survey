package com.craxiom.networksurvey.logging;

import android.os.Looper;
import android.util.Log;

import com.craxiom.networksurvey.constants.CdmaMessageConstants;
import com.craxiom.networksurvey.constants.GsmMessageConstants;
import com.craxiom.networksurvey.constants.LteMessageConstants;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.constants.UmtsMessageConstants;
import com.craxiom.networksurvey.messaging.CdmaRecord;
import com.craxiom.networksurvey.messaging.GsmRecord;
import com.craxiom.networksurvey.messaging.LteRecord;
import com.craxiom.networksurvey.messaging.UmtsRecord;
import com.craxiom.networksurvey.messaging.WifiBeaconRecord;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.sql.SQLException;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.core.srs.SpatialReferenceSystem;
import mil.nga.geopackage.db.GeoPackageDataType;
import mil.nga.geopackage.features.user.FeatureColumn;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.features.user.FeatureRow;
import mil.nga.geopackage.geom.GeoPackageGeometryData;
import mil.nga.sf.Point;

/**
 * Responsible for taking cellular survey records, and writing them to the GeoPackage log file.
 *
 * @since 0.1.2
 */
public class CellularSurveyRecordLogger extends SurveyRecordLogger
{
    private static final String LOG_TAG = CellularSurveyRecordLogger.class.getSimpleName();

    /**
     * Constructs a Logger that writes Cellular Survey records to a GeoPackage SQLite database.
     *
     * @param networkSurveyService The Service instance that is running this logger.
     * @param serviceLooper        The Looper associated with the service that can be used to do any background processing.
     */
    public CellularSurveyRecordLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper)
    {
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.CELLULAR_FILE_NAME_PREFIX);
    }

    @Override
    public void onGsmSurveyRecord(GsmRecord gsmRecord)
    {
        writeGsmRecordToLogFile(gsmRecord);
    }

    @Override
    public void onCdmaSurveyRecord(CdmaRecord cdmaRecord)
    {
        writeCdmaRecordToLogFile(cdmaRecord);
    }

    @Override
    public void onUmtsSurveyRecord(UmtsRecord umtsRecord)
    {
        writeUmtsRecordToLogFile(umtsRecord);
    }

    @Override
    public void onLteSurveyRecord(LteRecord lteRecord)
    {
        writeLteRecordToLogFile(lteRecord);
    }

    @Override
    public void onWifiBeaconSurveyRecord(WifiBeaconRecord wifiBeaconRecord)
    {
        // no-op
    }

    @Override
    void createTables(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException
    {
        createGsmRecordTable(geoPackage, srs);
        createCdmaRecordTable(geoPackage, srs);
        createUmtsRecordTable(geoPackage, srs);
        createLteRecordTable(geoPackage, srs);
    }

    /**
     * Creates an GeoPackage Table that can be populated with GSM Records.
     *
     * @param geoPackage The GeoPackage to create the table in.
     * @param srs        The SRS to use for the table coordinates.
     * @throws SQLException If there is a problem working with the GeoPackage SQLite DB.
     */
    private void createGsmRecordTable(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException
    {
        createTable(GsmMessageConstants.GSM_RECORDS_TABLE_NAME, geoPackage, srs, true, (tableColumns, columnNumber) -> {
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GsmMessageConstants.MCC_COLUMN, GeoPackageDataType.SMALLINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GsmMessageConstants.MNC_COLUMN, GeoPackageDataType.SMALLINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GsmMessageConstants.LAC_COLUMN, GeoPackageDataType.MEDIUMINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GsmMessageConstants.CID_COLUMN, GeoPackageDataType.MEDIUMINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GsmMessageConstants.ARFCN_COLUMN, GeoPackageDataType.SMALLINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GsmMessageConstants.BSIC_COLUMN, GeoPackageDataType.SMALLINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GsmMessageConstants.SIGNAL_STRENGTH_COLUMN, GeoPackageDataType.FLOAT, false, null));
            //noinspection UnusedAssignment
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GsmMessageConstants.TA_COLUMN, GeoPackageDataType.SMALLINT, false, null));
        });
    }

    /**
     * Creates an GeoPackage Table that can be populated with GSM Records.
     *
     * @param geoPackage The GeoPackage to create the table in.
     * @param srs        The SRS to use for the table coordinates.
     * @throws SQLException If there is a problem working with the GeoPackage SQLite DB.
     */
    private void createCdmaRecordTable(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException
    {
        createTable(CdmaMessageConstants.CDMA_RECORDS_TABLE_NAME, geoPackage, srs, true, (tableColumns, columnNumber) -> {
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, CdmaMessageConstants.SID_COLUMN, GeoPackageDataType.MEDIUMINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, CdmaMessageConstants.NID_COLUMN, GeoPackageDataType.MEDIUMINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, CdmaMessageConstants.BSID_COLUMN, GeoPackageDataType.MEDIUMINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, CdmaMessageConstants.CHANNEL_COLUMN, GeoPackageDataType.SMALLINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, CdmaMessageConstants.PN_OFFSET_COLUMN, GeoPackageDataType.SMALLINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, CdmaMessageConstants.SIGNAL_STRENGTH_COLUMN, GeoPackageDataType.FLOAT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, CdmaMessageConstants.ECIO_COLUMN, GeoPackageDataType.FLOAT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, CdmaMessageConstants.BASE_LATITUDE, GeoPackageDataType.DOUBLE, false, null));
            //noinspection UnusedAssignment
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, CdmaMessageConstants.BASE_LONGITUDE, GeoPackageDataType.DOUBLE, false, null));
        });
    }

    /**
     * Creates an GeoPackage Table that can be populated with GSM Records.
     *
     * @param geoPackage The GeoPackage to create the table in.
     * @param srs        The SRS to use for the table coordinates.
     * @throws SQLException If there is a problem working with the GeoPackage SQLite DB.
     */
    private void createUmtsRecordTable(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException
    {
        createTable(UmtsMessageConstants.UMTS_RECORDS_TABLE_NAME, geoPackage, srs, true, (tableColumns, columnNumber) -> {
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, UmtsMessageConstants.MCC_COLUMN, GeoPackageDataType.SMALLINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, UmtsMessageConstants.MNC_COLUMN, GeoPackageDataType.SMALLINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, UmtsMessageConstants.LAC_COLUMN, GeoPackageDataType.MEDIUMINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, UmtsMessageConstants.CELL_ID_COLUMN, GeoPackageDataType.MEDIUMINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, UmtsMessageConstants.UARFCN_COLUMN, GeoPackageDataType.SMALLINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, UmtsMessageConstants.PSC_COLUMN, GeoPackageDataType.SMALLINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, UmtsMessageConstants.SIGNAL_STRENGTH_COLUMN, GeoPackageDataType.FLOAT, false, null));
            //noinspection UnusedAssignment
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, UmtsMessageConstants.RSCP_COLUMN, GeoPackageDataType.FLOAT, false, null));
        });
    }

    /**
     * Creates an GeoPackage Table that can be populated with LTE Records.
     *
     * @param geoPackage The GeoPackage to create the table in.
     * @param srs        The SRS to use for the table coordinates.
     * @throws SQLException If there is a problem working with the GeoPackage SQLite DB.
     */
    private void createLteRecordTable(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException
    {
        createTable(LteMessageConstants.LTE_RECORDS_TABLE_NAME, geoPackage, srs, true, (tableColumns, columnNumber) -> {
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteMessageConstants.MCC_COLUMN, GeoPackageDataType.SMALLINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteMessageConstants.MNC_COLUMN, GeoPackageDataType.SMALLINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteMessageConstants.TAC_COLUMN, GeoPackageDataType.MEDIUMINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteMessageConstants.CI_COLUMN, GeoPackageDataType.MEDIUMINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteMessageConstants.EARFCN_COLUMN, GeoPackageDataType.MEDIUMINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteMessageConstants.PCI_COLUMN, GeoPackageDataType.SMALLINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteMessageConstants.RSRP_COLUMN, GeoPackageDataType.FLOAT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteMessageConstants.RSRQ_COLUMN, GeoPackageDataType.FLOAT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteMessageConstants.TA_COLUMN, GeoPackageDataType.SMALLINT, false, null));
            //noinspection UnusedAssignment
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteMessageConstants.BANDWIDTH_COLUMN, GeoPackageDataType.TEXT, false, null));
        });
    }

    /**
     * Given a GSM Record, write it to the GeoPackage log file.
     *
     * @param gsmRecord The GSM Record to write to the log file.
     */
    private void writeGsmRecordToLogFile(final GsmRecord gsmRecord)
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
                    FeatureDao featureDao = geoPackage.getFeatureDao(GsmMessageConstants.GSM_RECORDS_TABLE_NAME);
                    FeatureRow row = featureDao.newRow();

                    Point fix = new Point(gsmRecord.getLongitude(), gsmRecord.getLatitude(), (double) gsmRecord.getAltitude());

                    GeoPackageGeometryData geomData = new GeoPackageGeometryData(WGS84_SRS);
                    geomData.setGeometry(fix);

                    row.setGeometry(geomData);

                    row.setValue(GsmMessageConstants.TIME_COLUMN, gsmRecord.getDeviceTime());
                    row.setValue(GsmMessageConstants.RECORD_NUMBER_COLUMN, gsmRecord.getRecordNumber());
                    row.setValue(GsmMessageConstants.GROUP_NUMBER_COLUMN, gsmRecord.getGroupNumber());
                    if (gsmRecord.hasServingCell())
                    {
                        row.setValue(GsmMessageConstants.SERVING_CELL_COLUMN, gsmRecord.getServingCell().getValue());
                    }
                    final String provider = gsmRecord.getProvider();
                    if (!provider.isEmpty()) row.setValue(GsmMessageConstants.PROVIDER_COLUMN, provider);

                    if (gsmRecord.hasMcc())
                    {
                        setShortValue(row, GsmMessageConstants.MCC_COLUMN, gsmRecord.getMcc().getValue());
                    }
                    if (gsmRecord.hasMnc())
                    {
                        setShortValue(row, GsmMessageConstants.MNC_COLUMN, gsmRecord.getMnc().getValue());
                    }
                    if (gsmRecord.hasLac())
                    {
                        setIntValue(row, GsmMessageConstants.LAC_COLUMN, gsmRecord.getLac().getValue());
                    }
                    if (gsmRecord.hasCi())
                    {
                        setIntValue(row, GsmMessageConstants.CID_COLUMN, gsmRecord.getCi().getValue());
                    }
                    if (gsmRecord.hasArfcn())
                    {
                        setShortValue(row, GsmMessageConstants.ARFCN_COLUMN, gsmRecord.getArfcn().getValue());
                    }
                    if (gsmRecord.hasBsic())
                    {
                        setShortValue(row, GsmMessageConstants.BSIC_COLUMN, gsmRecord.getBsic().getValue());
                    }
                    if (gsmRecord.hasSignalStrength())
                    {
                        row.setValue(GsmMessageConstants.SIGNAL_STRENGTH_COLUMN, gsmRecord.getSignalStrength().getValue());
                    }
                    if (gsmRecord.hasTa())
                    {
                        setShortValue(row, GsmMessageConstants.TA_COLUMN, gsmRecord.getTa().getValue());
                    }

                    featureDao.insert(row);
                }
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Something went wrong when trying to write a GSM survey record", e);
            }
        });
    }

    /**
     * Given a CDMA Record, write it to the GeoPackage log file.
     *
     * @param cdmaRecord The CDMA Record to write to the log file.
     */
    private void writeCdmaRecordToLogFile(final CdmaRecord cdmaRecord)
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
                    FeatureDao featureDao = geoPackage.getFeatureDao(CdmaMessageConstants.CDMA_RECORDS_TABLE_NAME);
                    FeatureRow row = featureDao.newRow();

                    Point fix = new Point(cdmaRecord.getLongitude(), cdmaRecord.getLatitude(), (double) cdmaRecord.getAltitude());

                    GeoPackageGeometryData geomData = new GeoPackageGeometryData(WGS84_SRS);
                    geomData.setGeometry(fix);

                    row.setGeometry(geomData);

                    row.setValue(CdmaMessageConstants.TIME_COLUMN, cdmaRecord.getDeviceTime());
                    row.setValue(CdmaMessageConstants.RECORD_NUMBER_COLUMN, cdmaRecord.getRecordNumber());
                    row.setValue(CdmaMessageConstants.GROUP_NUMBER_COLUMN, cdmaRecord.getGroupNumber());
                    if (cdmaRecord.hasServingCell())
                    {
                        row.setValue(CdmaMessageConstants.SERVING_CELL_COLUMN, cdmaRecord.getServingCell().getValue());
                    }
                    final String provider = cdmaRecord.getProvider();
                    if (!provider.isEmpty()) row.setValue(CdmaMessageConstants.PROVIDER_COLUMN, provider);

                    if (cdmaRecord.hasSid())
                    {
                        setIntValue(row, CdmaMessageConstants.SID_COLUMN, cdmaRecord.getSid().getValue());
                    }
                    if (cdmaRecord.hasNid())
                    {
                        setIntValue(row, CdmaMessageConstants.NID_COLUMN, cdmaRecord.getNid().getValue());
                    }
                    if (cdmaRecord.hasBsid())
                    {
                        setIntValue(row, CdmaMessageConstants.BSID_COLUMN, cdmaRecord.getBsid().getValue());
                    }
                    if (cdmaRecord.hasPnOffset())
                    {
                        setShortValue(row, CdmaMessageConstants.PN_OFFSET_COLUMN, cdmaRecord.getPnOffset().getValue());
                    }
                    if (cdmaRecord.hasSignalStrength())
                    {
                        row.setValue(CdmaMessageConstants.SIGNAL_STRENGTH_COLUMN, cdmaRecord.getSignalStrength().getValue());
                    }
                    if (cdmaRecord.hasEcio())
                    {
                        row.setValue(CdmaMessageConstants.ECIO_COLUMN, cdmaRecord.getEcio().getValue());
                    }

                    featureDao.insert(row);
                }
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Something went wrong when trying to write a CDMA survey record", e);
            }
        });
    }

    /**
     * Given a UMTS Record, write it to the GeoPackage log file.
     *
     * @param umtsRecord The UMTS Record to write to the log file.
     */
    private void writeUmtsRecordToLogFile(final UmtsRecord umtsRecord)
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
                    FeatureDao featureDao = geoPackage.getFeatureDao(UmtsMessageConstants.UMTS_RECORDS_TABLE_NAME);
                    FeatureRow row = featureDao.newRow();

                    Point fix = new Point(umtsRecord.getLongitude(), umtsRecord.getLatitude(), (double) umtsRecord.getAltitude());

                    GeoPackageGeometryData geomData = new GeoPackageGeometryData(WGS84_SRS);
                    geomData.setGeometry(fix);

                    row.setGeometry(geomData);

                    row.setValue(UmtsMessageConstants.TIME_COLUMN, umtsRecord.getDeviceTime());
                    row.setValue(UmtsMessageConstants.RECORD_NUMBER_COLUMN, umtsRecord.getRecordNumber());
                    row.setValue(UmtsMessageConstants.GROUP_NUMBER_COLUMN, umtsRecord.getGroupNumber());
                    if (umtsRecord.hasServingCell())
                    {
                        row.setValue(UmtsMessageConstants.SERVING_CELL_COLUMN, umtsRecord.getServingCell().getValue());
                    }
                    final String provider = umtsRecord.getProvider();
                    if (!provider.isEmpty()) row.setValue(UmtsMessageConstants.PROVIDER_COLUMN, provider);

                    if (umtsRecord.hasMcc())
                    {
                        setShortValue(row, UmtsMessageConstants.MCC_COLUMN, umtsRecord.getMcc().getValue());
                    }
                    if (umtsRecord.hasMnc())
                    {
                        setShortValue(row, UmtsMessageConstants.MNC_COLUMN, umtsRecord.getMnc().getValue());
                    }
                    if (umtsRecord.hasLac())
                    {
                        setIntValue(row, UmtsMessageConstants.LAC_COLUMN, umtsRecord.getLac().getValue());
                    }
                    if (umtsRecord.hasCi())
                    {
                        setIntValue(row, UmtsMessageConstants.CELL_ID_COLUMN, umtsRecord.getCi().getValue());
                    }
                    if (umtsRecord.hasUarfcn())
                    {
                        setShortValue(row, UmtsMessageConstants.UARFCN_COLUMN, umtsRecord.getUarfcn().getValue());
                    }
                    if (umtsRecord.hasPsc())
                    {
                        setShortValue(row, UmtsMessageConstants.PSC_COLUMN, umtsRecord.getPsc().getValue());
                    }
                    if (umtsRecord.hasSignalStrength())
                    {
                        row.setValue(UmtsMessageConstants.SIGNAL_STRENGTH_COLUMN, umtsRecord.getSignalStrength().getValue());
                    }
                    if (umtsRecord.hasRscp())
                    {
                        row.setValue(UmtsMessageConstants.RSCP_COLUMN, umtsRecord.getRscp().getValue());
                    }

                    featureDao.insert(row);
                }
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Something went wrong when trying to write an UMTS survey record", e);
            }
        });
    }

    /**
     * Given an LTE Record, write it to the GeoPackage log file.
     *
     * @param lteRecord The LTE Record to write to the log file.
     */
    private void writeLteRecordToLogFile(final LteRecord lteRecord)
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
                    FeatureDao featureDao = geoPackage.getFeatureDao(LteMessageConstants.LTE_RECORDS_TABLE_NAME);
                    FeatureRow row = featureDao.newRow();

                    Point fix = new Point(lteRecord.getLongitude(), lteRecord.getLatitude(), (double) lteRecord.getAltitude());

                    GeoPackageGeometryData geomData = new GeoPackageGeometryData(WGS84_SRS);
                    geomData.setGeometry(fix);

                    row.setGeometry(geomData);

                    row.setValue(LteMessageConstants.TIME_COLUMN, lteRecord.getDeviceTime());
                    row.setValue(LteMessageConstants.RECORD_NUMBER_COLUMN, lteRecord.getRecordNumber());
                    row.setValue(LteMessageConstants.GROUP_NUMBER_COLUMN, lteRecord.getGroupNumber());

                    if (lteRecord.hasMcc())
                    {
                        setShortValue(row, LteMessageConstants.MCC_COLUMN, lteRecord.getMcc().getValue());
                    }
                    if (lteRecord.hasMnc())
                    {
                        setShortValue(row, LteMessageConstants.MNC_COLUMN, lteRecord.getMnc().getValue());
                    }
                    if (lteRecord.hasTac())
                    {
                        setIntValue(row, LteMessageConstants.TAC_COLUMN, lteRecord.getTac().getValue());
                    }
                    if (lteRecord.hasCi())
                    {
                        setIntValue(row, LteMessageConstants.CI_COLUMN, lteRecord.getCi().getValue());
                    }
                    if (lteRecord.hasEarfcn())
                    {
                        setIntValue(row, LteMessageConstants.EARFCN_COLUMN, lteRecord.getEarfcn().getValue());
                    }
                    if (lteRecord.hasPci())
                    {
                        setShortValue(row, LteMessageConstants.PCI_COLUMN, lteRecord.getPci().getValue());
                    }
                    if (lteRecord.hasRsrp())
                    {
                        row.setValue(LteMessageConstants.RSRP_COLUMN, lteRecord.getRsrp().getValue());
                    }
                    if (lteRecord.hasRsrq())
                    {
                        row.setValue(LteMessageConstants.RSRQ_COLUMN, lteRecord.getRsrq().getValue());
                    }
                    if (lteRecord.hasTa())
                    {
                        setShortValue(row, LteMessageConstants.TA_COLUMN, lteRecord.getTa().getValue());
                    }
                    if (lteRecord.hasServingCell())
                    {
                        row.setValue(LteMessageConstants.SERVING_CELL_COLUMN, lteRecord.getServingCell().getValue());
                    }

                    final String provider = lteRecord.getProvider();
                    if (!provider.isEmpty()) row.setValue(LteMessageConstants.PROVIDER_COLUMN, provider);

                    setLteBandwidth(row, lteRecord.getLteBandwidth());

                    featureDao.insert(row);
                }
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Something went wrong when trying to write an LTE survey record", e);
            }
        });
    }
}
