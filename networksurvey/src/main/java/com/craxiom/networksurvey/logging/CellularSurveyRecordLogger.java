package com.craxiom.networksurvey.logging;

import android.os.Looper;

import com.craxiom.messaging.CdmaRecord;
import com.craxiom.messaging.CdmaRecordData;
import com.craxiom.messaging.ConnectionStatus;
import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.GsmRecordData;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.LteRecordData;
import com.craxiom.messaging.NrRecord;
import com.craxiom.messaging.NrRecordData;
import com.craxiom.messaging.UmtsRecord;
import com.craxiom.messaging.UmtsRecordData;
import com.craxiom.networksurvey.constants.CdmaMessageConstants;
import com.craxiom.networksurvey.constants.GsmMessageConstants;
import com.craxiom.networksurvey.constants.LteMessageConstants;
import com.craxiom.networksurvey.constants.MessageConstants;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.constants.NrMessageConstants;
import com.craxiom.networksurvey.constants.UmtsMessageConstants;
import com.craxiom.networksurvey.constants.csv.CdmaCsvConstants;
import com.craxiom.networksurvey.constants.csv.CellularCsvConstants;
import com.craxiom.networksurvey.constants.csv.CsvConstants;
import com.craxiom.networksurvey.constants.csv.GsmCsvConstants;
import com.craxiom.networksurvey.constants.csv.LteCsvConstants;
import com.craxiom.networksurvey.constants.csv.NrCsvConstants;
import com.craxiom.networksurvey.constants.csv.UmtsCsvConstants;
import com.craxiom.networksurvey.gpstest.util.MathUtils;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.util.NsUtils;
import com.google.common.base.Strings;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.contents.Contents;
import mil.nga.geopackage.contents.ContentsDao;
import mil.nga.geopackage.contents.ContentsDataType;
import mil.nga.geopackage.db.GeoPackageDataType;
import mil.nga.geopackage.features.columns.GeometryColumns;
import mil.nga.geopackage.features.columns.GeometryColumnsDao;
import mil.nga.geopackage.features.user.FeatureColumn;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.features.user.FeatureRow;
import mil.nga.geopackage.features.user.FeatureTable;
import mil.nga.geopackage.geom.GeoPackageGeometryData;
import mil.nga.geopackage.srs.SpatialReferenceSystem;
import mil.nga.sf.GeometryType;
import mil.nga.sf.Point;
import timber.log.Timber;

/**
 * Responsible for taking cellular survey records, and writing them to the GeoPackage log file.
 *
 * @since 0.1.2
 */
public class CellularSurveyRecordLogger extends SurveyRecordLogger implements ICellularSurveyRecordListener
{
    /**
     * Constructs a Logger that writes Cellular Survey records to a GeoPackage SQLite database.
     *
     * @param networkSurveyService The Service instance that is running this logger.
     * @param serviceLooper        The Looper associated with the service that can be used to do any background processing.
     */
    public CellularSurveyRecordLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper)
    {
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.LOG_DIRECTORY_NAME, NetworkSurveyConstants.CELLULAR_FILE_NAME_PREFIX);
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
    public void onNrSurveyRecord(NrRecord nrRecord)
    {
        writeNrRecordToLogFile(nrRecord);
    }

    @Override
    void createTables(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException
    {
        createGsmRecordTable(geoPackage, srs);
        createCdmaRecordTable(geoPackage, srs);
        createUmtsRecordTable(geoPackage, srs);
        createLteRecordTable(geoPackage, srs);
        createNrRecordTable(geoPackage, srs);
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
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GsmMessageConstants.TA_COLUMN, GeoPackageDataType.SMALLINT, false, null));
            //noinspection UnusedAssignment
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GsmMessageConstants.PLMN_COLUMN, GeoPackageDataType.TEXT, false, null));
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
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, UmtsMessageConstants.RSCP_COLUMN, GeoPackageDataType.FLOAT, false, null));
            //noinspection UnusedAssignment
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, UmtsMessageConstants.PLMN_COLUMN, GeoPackageDataType.TEXT, false, null));
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
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteMessageConstants.BANDWIDTH_COLUMN, GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteCsvConstants.SIGNAL_STRENGTH, GeoPackageDataType.FLOAT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteCsvConstants.CQI, GeoPackageDataType.SMALLINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteCsvConstants.SNR, GeoPackageDataType.FLOAT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteMessageConstants.PLMN_COLUMN, GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteMessageConstants.CONNECTION_STATUS_COLUMN, GeoPackageDataType.TEXT, false, null));
            //noinspection UnusedAssignment
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, LteMessageConstants.CELL_BANDWIDTHS_KHZ_COLUMN, GeoPackageDataType.TEXT, false, null));
        });
    }

    /**
     * Creates an GeoPackage Table that can be populated with NR(New Radio 5G) Records.
     *
     * @param geoPackage The GeoPackage to create the table in.
     * @param srs        The SRS to use for the table coordinates.
     * @throws SQLException If there is a problem working with the GeoPackage SQLite DB.
     * @since 1.5.0
     */
    private void createNrRecordTable(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException
    {
        ContentsDao contentsDao = geoPackage.getContentsDao();

        // Note: We are not using the createTable method here because we want to match the MQTT message
        // schema. It was decided to start matching the Network Survey Messaging API schema with the
        // GeoPackage schema starting with the 5G NR records.

        Contents contents = new Contents();
        contents.setTableName(NrMessageConstants.NR_RECORDS_TABLE_NAME);
        contents.setDataType(ContentsDataType.FEATURES);
        contents.setIdentifier(NrMessageConstants.NR_RECORDS_TABLE_NAME);
        contents.setDescription(NrMessageConstants.NR_RECORDS_TABLE_NAME);
        contents.setSrs(srs);

        int columnNumber = 0;
        List<FeatureColumn> tableColumns = new LinkedList<>();
        tableColumns.add(FeatureColumn.createPrimaryKeyColumn(columnNumber++, MessageConstants.ID_COLUMN));
        tableColumns.add(FeatureColumn.createGeometryColumn(columnNumber++, MessageConstants.GEOMETRY_COLUMN, GeometryType.POINT, false, null));

        tableColumns.add(FeatureColumn.createColumn(columnNumber++, CsvConstants.DEVICE_SERIAL_NUMBER, GeoPackageDataType.TEXT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.DEVICE_TIME_COLUMN, GeoPackageDataType.INT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.LATITUDE_COLUMN, GeoPackageDataType.DOUBLE, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.LONGITUDE_COLUMN, GeoPackageDataType.DOUBLE, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.ALTITUDE_COLUMN, GeoPackageDataType.FLOAT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.MISSION_ID_COLUMN, GeoPackageDataType.TEXT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.RECORD_NUMBER_COLUMN, GeoPackageDataType.MEDIUMINT, true, -1));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.GROUP_NUMBER_COLUMN, GeoPackageDataType.MEDIUMINT, true, -1));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, CsvConstants.SPEED, GeoPackageDataType.FLOAT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.ACCURACY, GeoPackageDataType.INT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, CsvConstants.LOCATION_AGE, GeoPackageDataType.MEDIUMINT, false, null));

        // nr record specific
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.MCC_COLUMN, GeoPackageDataType.SMALLINT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.MNC_COLUMN, GeoPackageDataType.SMALLINT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.TAC_COLUMN, GeoPackageDataType.MEDIUMINT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.NCI_COLUMN, GeoPackageDataType.INT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.NARFCN_COLUMN, GeoPackageDataType.MEDIUMINT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.PCI_COLUMN, GeoPackageDataType.SMALLINT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.SS_RSRP_COLUMN, GeoPackageDataType.FLOAT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.SS_RSRQ_COLUMN, GeoPackageDataType.FLOAT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.SS_SINR_COLUMN, GeoPackageDataType.FLOAT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.CSI_RSRP_COLUMN, GeoPackageDataType.FLOAT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.CSI_RSRQ_COLUMN, GeoPackageDataType.FLOAT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.CSI_SINR_COLUMN, GeoPackageDataType.FLOAT, false, null));

        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.SERVING_CELL_COLUMN, GeoPackageDataType.BOOLEAN, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.PROVIDER_COLUMN, GeoPackageDataType.TEXT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, CellularCsvConstants.SLOT, GeoPackageDataType.SMALLINT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.PLMN_COLUMN, GeoPackageDataType.TEXT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.CONNECTION_STATUS_COLUMN, GeoPackageDataType.TEXT, false, null));
        //noinspection UnusedAssignment
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, NrMessageConstants.CELL_BANDWIDTHS_KHZ_COLUMN, GeoPackageDataType.TEXT, false, null));

        FeatureTable table = new FeatureTable(NrMessageConstants.NR_RECORDS_TABLE_NAME, tableColumns);
        geoPackage.createFeatureTable(table);

        contentsDao.create(contents);

        GeometryColumnsDao geometryColumnsDao = geoPackage.getGeometryColumnsDao();

        GeometryColumns geometryColumns = new GeometryColumns();
        geometryColumns.setContents(contents);
        geometryColumns.setColumnName(MessageConstants.GEOMETRY_COLUMN);
        geometryColumns.setGeometryType(GeometryType.POINT);
        geometryColumns.setSrs(srs);
        geometryColumns.setZ((byte) 0);
        geometryColumns.setM((byte) 0);
        geometryColumnsDao.create(geometryColumns);
    }

    /**
     * Given a GSM Record, write it to the GeoPackage log file.
     *
     * @param gsmRecord The GSM Record to write to the log file.
     */
    private void writeGsmRecordToLogFile(final GsmRecord gsmRecord)
    {
        if (!loggingEnabled) return;

        handler.post(() -> {
            synchronized (geoPackageLock)
            {
                try
                {
                    if (geoPackage != null)
                    {
                        FeatureDao featureDao = getCachedFeatureDao(GsmMessageConstants.GSM_RECORDS_TABLE_NAME);
                        FeatureRow row = featureDao.newRow();

                        final GsmRecordData data = gsmRecord.getData();

                        Point fix = new Point(data.getLongitude(), data.getLatitude(), (double) data.getAltitude());

                        GeoPackageGeometryData geomData = new GeoPackageGeometryData(WGS84_SRS);
                        geomData.setGeometry(fix);

                        row.setGeometry(geomData);

                        row.setValue(GsmCsvConstants.DEVICE_SERIAL_NUMBER, data.getDeviceSerialNumber());
                        row.setValue(GsmMessageConstants.TIME_COLUMN, NsUtils.getEpochFromRfc3339(data.getDeviceTime()));
                        row.setValue(GsmMessageConstants.MISSION_ID_COLUMN, data.getMissionId());
                        row.setValue(GsmMessageConstants.RECORD_NUMBER_COLUMN, data.getRecordNumber());
                        row.setValue(GsmMessageConstants.GROUP_NUMBER_COLUMN, data.getGroupNumber());
                        row.setValue(GsmCsvConstants.SPEED, data.getSpeed());
                        row.setValue(GsmMessageConstants.ACCURACY, MathUtils.roundAccuracy(data.getAccuracy()));
                        row.setValue(CsvConstants.LOCATION_AGE, data.getLocationAge());

                        if (data.hasServingCell())
                        {
                            row.setValue(GsmMessageConstants.SERVING_CELL_COLUMN, data.getServingCell().getValue());
                        }
                        final String provider = data.getProvider();
                        if (!provider.isEmpty())
                        {
                            row.setValue(GsmMessageConstants.PROVIDER_COLUMN, provider);
                        }

                        if (data.hasMcc())
                        {
                            setShortValue(row, GsmMessageConstants.MCC_COLUMN, data.getMcc().getValue());
                        }
                        if (data.hasMnc())
                        {
                            setShortValue(row, GsmMessageConstants.MNC_COLUMN, data.getMnc().getValue());
                        }
                        if (data.hasPlmn())
                        {
                            row.setValue(GsmMessageConstants.PLMN_COLUMN, data.getPlmn().getValue());
                        }
                        if (data.hasLac())
                        {
                            setIntValue(row, GsmMessageConstants.LAC_COLUMN, data.getLac().getValue());
                        }
                        if (data.hasCi())
                        {
                            setIntValue(row, GsmMessageConstants.CID_COLUMN, data.getCi().getValue());
                        }
                        if (data.hasArfcn())
                        {
                            setShortValue(row, GsmMessageConstants.ARFCN_COLUMN, data.getArfcn().getValue());
                        }
                        if (data.hasBsic())
                        {
                            setShortValue(row, GsmMessageConstants.BSIC_COLUMN, data.getBsic().getValue());
                        }
                        if (data.hasSignalStrength())
                        {
                            row.setValue(GsmMessageConstants.SIGNAL_STRENGTH_COLUMN, data.getSignalStrength().getValue());
                        }
                        if (data.hasTa())
                        {
                            setShortValue(row, GsmMessageConstants.TA_COLUMN, data.getTa().getValue());
                        }
                        if (data.hasSlot())
                        {
                            setShortValue(row, CellularCsvConstants.SLOT, data.getSlot().getValue());
                        }

                        featureDao.insert(row);

                        checkIfRolloverNeeded();
                    }
                } catch (Exception e)
                {
                    Timber.e(e, "Something went wrong when trying to write a GSM survey record");
                }
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
        if (!loggingEnabled) return;

        handler.post(() -> {
            synchronized (geoPackageLock)
            {
                try
                {
                    if (geoPackage != null)
                    {
                        FeatureDao featureDao = getCachedFeatureDao(CdmaMessageConstants.CDMA_RECORDS_TABLE_NAME);
                        FeatureRow row = featureDao.newRow();

                        final CdmaRecordData data = cdmaRecord.getData();

                        Point fix = new Point(data.getLongitude(), data.getLatitude(), (double) data.getAltitude());

                        GeoPackageGeometryData geomData = new GeoPackageGeometryData(WGS84_SRS);
                        geomData.setGeometry(fix);

                        row.setGeometry(geomData);

                        row.setValue(CdmaCsvConstants.DEVICE_SERIAL_NUMBER, data.getDeviceSerialNumber());
                        row.setValue(CdmaMessageConstants.TIME_COLUMN, NsUtils.getEpochFromRfc3339(data.getDeviceTime()));
                        row.setValue(CdmaMessageConstants.MISSION_ID_COLUMN, data.getMissionId());
                        row.setValue(CdmaMessageConstants.RECORD_NUMBER_COLUMN, data.getRecordNumber());
                        row.setValue(CdmaMessageConstants.GROUP_NUMBER_COLUMN, data.getGroupNumber());
                        row.setValue(CdmaCsvConstants.SPEED, data.getSpeed());
                        row.setValue(CdmaMessageConstants.ACCURACY, MathUtils.roundAccuracy(data.getAccuracy()));
                        row.setValue(CsvConstants.LOCATION_AGE, data.getLocationAge());

                        if (data.hasServingCell())
                        {
                            row.setValue(CdmaMessageConstants.SERVING_CELL_COLUMN, data.getServingCell().getValue());
                        }
                        final String provider = data.getProvider();
                        if (!provider.isEmpty())
                        {
                            row.setValue(CdmaMessageConstants.PROVIDER_COLUMN, provider);
                        }

                        if (data.hasSid())
                        {
                            setIntValue(row, CdmaMessageConstants.SID_COLUMN, data.getSid().getValue());
                        }
                        if (data.hasNid())
                        {
                            setIntValue(row, CdmaMessageConstants.NID_COLUMN, data.getNid().getValue());
                        }
                        if (data.hasBsid())
                        {
                            setIntValue(row, CdmaMessageConstants.BSID_COLUMN, data.getBsid().getValue());
                        }
                        if (data.hasPnOffset())
                        {
                            setShortValue(row, CdmaMessageConstants.PN_OFFSET_COLUMN, data.getPnOffset().getValue());
                        }
                        if (data.hasSignalStrength())
                        {
                            row.setValue(CdmaMessageConstants.SIGNAL_STRENGTH_COLUMN, data.getSignalStrength().getValue());
                        }
                        if (data.hasEcio())
                        {
                            row.setValue(CdmaMessageConstants.ECIO_COLUMN, data.getEcio().getValue());
                        }
                        if (data.hasSlot())
                        {
                            setShortValue(row, CellularCsvConstants.SLOT, data.getSlot().getValue());
                        }

                        featureDao.insert(row);

                        checkIfRolloverNeeded();
                    }
                } catch (Exception e)
                {
                    Timber.e(e, "Something went wrong when trying to write a CDMA survey record");
                }
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
        if (!loggingEnabled) return;

        handler.post(() -> {
            synchronized (geoPackageLock)
            {
                try
                {
                    if (geoPackage != null)
                    {
                        FeatureDao featureDao = getCachedFeatureDao(UmtsMessageConstants.UMTS_RECORDS_TABLE_NAME);
                        FeatureRow row = featureDao.newRow();

                        final UmtsRecordData data = umtsRecord.getData();

                        Point fix = new Point(data.getLongitude(), data.getLatitude(), (double) data.getAltitude());

                        GeoPackageGeometryData geomData = new GeoPackageGeometryData(WGS84_SRS);
                        geomData.setGeometry(fix);

                        row.setGeometry(geomData);

                        row.setValue(UmtsCsvConstants.DEVICE_SERIAL_NUMBER, data.getDeviceSerialNumber());
                        row.setValue(UmtsMessageConstants.TIME_COLUMN, NsUtils.getEpochFromRfc3339(data.getDeviceTime()));
                        row.setValue(UmtsMessageConstants.MISSION_ID_COLUMN, data.getMissionId());
                        row.setValue(UmtsMessageConstants.RECORD_NUMBER_COLUMN, data.getRecordNumber());
                        row.setValue(UmtsMessageConstants.GROUP_NUMBER_COLUMN, data.getGroupNumber());
                        row.setValue(UmtsCsvConstants.SPEED, data.getSpeed());
                        row.setValue(UmtsMessageConstants.ACCURACY, MathUtils.roundAccuracy(data.getAccuracy()));
                        row.setValue(CsvConstants.LOCATION_AGE, data.getLocationAge());

                        if (data.hasServingCell())
                        {
                            row.setValue(UmtsMessageConstants.SERVING_CELL_COLUMN, data.getServingCell().getValue());
                        }
                        final String provider = data.getProvider();
                        if (!provider.isEmpty())
                        {
                            row.setValue(UmtsMessageConstants.PROVIDER_COLUMN, provider);
                        }

                        if (data.hasMcc())
                        {
                            setShortValue(row, UmtsMessageConstants.MCC_COLUMN, data.getMcc().getValue());
                        }
                        if (data.hasMnc())
                        {
                            setShortValue(row, UmtsMessageConstants.MNC_COLUMN, data.getMnc().getValue());
                        }
                        if (data.hasPlmn())
                        {
                            row.setValue(UmtsMessageConstants.PLMN_COLUMN, data.getPlmn().getValue());
                        }
                        if (data.hasLac())
                        {
                            setIntValue(row, UmtsMessageConstants.LAC_COLUMN, data.getLac().getValue());
                        }
                        if (data.hasCid())
                        {
                            setIntValue(row, UmtsMessageConstants.CELL_ID_COLUMN, data.getCid().getValue());
                        }
                        if (data.hasUarfcn())
                        {
                            setShortValue(row, UmtsMessageConstants.UARFCN_COLUMN, data.getUarfcn().getValue());
                        }
                        if (data.hasPsc())
                        {
                            setShortValue(row, UmtsMessageConstants.PSC_COLUMN, data.getPsc().getValue());
                        }
                        if (data.hasSignalStrength())
                        {
                            row.setValue(UmtsMessageConstants.SIGNAL_STRENGTH_COLUMN, data.getSignalStrength().getValue());
                        }
                        if (data.hasRscp())
                        {
                            row.setValue(UmtsMessageConstants.RSCP_COLUMN, data.getRscp().getValue());
                        }
                        if (data.hasSlot())
                        {
                            setShortValue(row, CellularCsvConstants.SLOT, data.getSlot().getValue());
                        }

                        featureDao.insert(row);

                        checkIfRolloverNeeded();
                    }
                } catch (Exception e)
                {
                    Timber.e(e, "Something went wrong when trying to write an UMTS survey record");
                }
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
        if (!loggingEnabled) return;

        handler.post(() -> {
            synchronized (geoPackageLock)
            {
                try
                {
                    if (geoPackage != null)
                    {
                        FeatureDao featureDao = getCachedFeatureDao(LteMessageConstants.LTE_RECORDS_TABLE_NAME);
                        FeatureRow row = featureDao.newRow();

                        final LteRecordData data = lteRecord.getData();

                        Point fix = new Point(data.getLongitude(), data.getLatitude(), (double) data.getAltitude());

                        GeoPackageGeometryData geomData = new GeoPackageGeometryData(WGS84_SRS);
                        geomData.setGeometry(fix);

                        row.setGeometry(geomData);

                        row.setValue(LteCsvConstants.DEVICE_SERIAL_NUMBER, data.getDeviceSerialNumber());
                        row.setValue(LteMessageConstants.TIME_COLUMN, NsUtils.getEpochFromRfc3339(data.getDeviceTime()));
                        row.setValue(LteMessageConstants.MISSION_ID_COLUMN, data.getMissionId());
                        row.setValue(LteMessageConstants.RECORD_NUMBER_COLUMN, data.getRecordNumber());
                        row.setValue(LteMessageConstants.GROUP_NUMBER_COLUMN, data.getGroupNumber());
                        row.setValue(LteCsvConstants.SPEED, data.getSpeed());
                        row.setValue(LteMessageConstants.ACCURACY, MathUtils.roundAccuracy(data.getAccuracy()));
                        row.setValue(CsvConstants.LOCATION_AGE, data.getLocationAge());

                        if (data.hasMcc())
                        {
                            setShortValue(row, LteMessageConstants.MCC_COLUMN, data.getMcc().getValue());
                        }
                        if (data.hasMnc())
                        {
                            setShortValue(row, LteMessageConstants.MNC_COLUMN, data.getMnc().getValue());
                        }
                        if (data.hasPlmn())
                        {
                            row.setValue(LteMessageConstants.PLMN_COLUMN, data.getPlmn().getValue());
                        }
                        if (data.hasTac())
                        {
                            setIntValue(row, LteMessageConstants.TAC_COLUMN, data.getTac().getValue());
                        }
                        if (data.hasEci())
                        {
                            setIntValue(row, LteMessageConstants.CI_COLUMN, data.getEci().getValue());
                        }
                        if (data.hasEarfcn())
                        {
                            setIntValue(row, LteMessageConstants.EARFCN_COLUMN, data.getEarfcn().getValue());
                        }
                        if (data.hasPci())
                        {
                            setShortValue(row, LteMessageConstants.PCI_COLUMN, data.getPci().getValue());
                        }
                        if (data.hasRsrp())
                        {
                            row.setValue(LteMessageConstants.RSRP_COLUMN, data.getRsrp().getValue());
                        }
                        if (data.hasRsrq())
                        {
                            row.setValue(LteMessageConstants.RSRQ_COLUMN, data.getRsrq().getValue());
                        }
                        if (data.hasTa())
                        {
                            setShortValue(row, LteMessageConstants.TA_COLUMN, data.getTa().getValue());
                        }
                        if (data.hasServingCell())
                        {
                            row.setValue(LteMessageConstants.SERVING_CELL_COLUMN, data.getServingCell().getValue());
                        }

                        final String provider = data.getProvider();
                        if (!provider.isEmpty())
                        {
                            row.setValue(LteMessageConstants.PROVIDER_COLUMN, provider);
                        }

                        if (data.hasSignalStrength())
                        {
                            row.setValue(LteCsvConstants.SIGNAL_STRENGTH, data.getSignalStrength().getValue());
                        }
                        if (data.hasCqi())
                        {
                            row.setValue(LteCsvConstants.CQI, data.getCqi().getValue());
                        }
                        if (data.hasSlot())
                        {
                            setShortValue(row, CellularCsvConstants.SLOT, data.getSlot().getValue());
                        }
                        if (data.hasSnr())
                        {
                            row.setValue(LteCsvConstants.SNR, data.getSnr().getValue());
                        }

                        setLteBandwidth(row, data.getLteBandwidth());
                        setConnectionStatus(row, LteMessageConstants.CONNECTION_STATUS_COLUMN, data.getConnectionStatus());
                        setCellBandwidths(row, LteMessageConstants.CELL_BANDWIDTHS_KHZ_COLUMN, data.getCellBandwidthsKhzList());

                        featureDao.insert(row);

                        checkIfRolloverNeeded();
                    }
                } catch (Exception e)
                {
                    Timber.e(e, "Something went wrong when trying to write an LTE survey record");
                }
            }
        });
    }

    /**
     * Given an NR Record, write it to the GeoPackage log file.
     *
     * @param nrRecord The NR Record to write to the log file.
     * @since 1.5.0
     */
    private void writeNrRecordToLogFile(final NrRecord nrRecord)
    {
        if (!loggingEnabled) return;

        handler.post(() -> {
            synchronized (geoPackageLock)
            {
                try
                {
                    if (geoPackage != null)
                    {
                        FeatureDao featureDao = getCachedFeatureDao(NrMessageConstants.NR_RECORDS_TABLE_NAME);
                        FeatureRow row = featureDao.newRow();

                        final NrRecordData data = nrRecord.getData();

                        Point fix = new Point(data.getLongitude(), data.getLatitude(), (double) data.getAltitude());

                        GeoPackageGeometryData geomData = new GeoPackageGeometryData(WGS84_SRS);
                        geomData.setGeometry(fix);

                        row.setGeometry(geomData);

                        row.setValue(NrCsvConstants.DEVICE_SERIAL_NUMBER, data.getDeviceSerialNumber());
                        row.setValue(NrMessageConstants.DEVICE_TIME_COLUMN, NsUtils.getEpochFromRfc3339(data.getDeviceTime()));
                        row.setValue(NrMessageConstants.MISSION_ID_COLUMN, data.getMissionId());
                        row.setValue(NrMessageConstants.RECORD_NUMBER_COLUMN, data.getRecordNumber());
                        row.setValue(NrMessageConstants.GROUP_NUMBER_COLUMN, data.getGroupNumber());
                        row.setValue(NrCsvConstants.SPEED, data.getSpeed());
                        row.setValue(NrMessageConstants.ACCURACY, MathUtils.roundAccuracy(data.getAccuracy()));
                        row.setValue(CsvConstants.LOCATION_AGE, data.getLocationAge());

                        if (data.hasMcc())
                        {
                            setShortValue(row, NrMessageConstants.MCC_COLUMN, data.getMcc().getValue());
                        }
                        if (data.hasMnc())
                        {
                            setShortValue(row, NrMessageConstants.MNC_COLUMN, data.getMnc().getValue());
                        }
                        if (data.hasPlmn())
                        {
                            row.setValue(NrMessageConstants.PLMN_COLUMN, data.getPlmn().getValue());
                        }
                        if (data.hasTac())
                        {
                            setIntValue(row, NrMessageConstants.TAC_COLUMN, data.getTac().getValue());
                        }
                        if (data.hasNci())
                        {
                            row.setValue(NrMessageConstants.NCI_COLUMN, data.getNci().getValue());
                        }
                        if (data.hasNarfcn())
                        {
                            setIntValue(row, NrMessageConstants.NARFCN_COLUMN, data.getNarfcn().getValue());
                        }
                        if (data.hasPci())
                        {
                            setShortValue(row, NrMessageConstants.PCI_COLUMN, data.getPci().getValue());
                        }

                        if (data.hasSsRsrp())
                        {
                            row.setValue(NrMessageConstants.SS_RSRP_COLUMN, data.getSsRsrp().getValue());
                        }
                        if (data.hasSsRsrq())
                        {
                            row.setValue(NrMessageConstants.SS_RSRQ_COLUMN, data.getSsRsrq().getValue());
                        }
                        if (data.hasSsSinr())
                        {
                            row.setValue(NrMessageConstants.SS_SINR_COLUMN, data.getSsSinr().getValue());
                        }

                        if (data.hasCsiRsrp())
                        {
                            row.setValue(NrMessageConstants.CSI_RSRP_COLUMN, data.getCsiRsrp().getValue());
                        }
                        if (data.hasCsiRsrq())
                        {
                            row.setValue(NrMessageConstants.CSI_RSRQ_COLUMN, data.getCsiRsrq().getValue());
                        }
                        if (data.hasCsiSinr())
                        {
                            row.setValue(NrMessageConstants.CSI_SINR_COLUMN, data.getCsiSinr().getValue());
                        }
                        if (data.hasServingCell())
                        {
                            row.setValue(NrMessageConstants.SERVING_CELL_COLUMN, data.getServingCell().getValue());
                        }

                        final String provider = data.getProvider();
                        if (!Strings.isNullOrEmpty(provider))
                        {
                            row.setValue(NrMessageConstants.PROVIDER_COLUMN, provider);
                        }
                        if (data.hasSlot())
                        {
                            setShortValue(row, CellularCsvConstants.SLOT, data.getSlot().getValue());
                        }

                        setConnectionStatus(row, NrMessageConstants.CONNECTION_STATUS_COLUMN, data.getConnectionStatus());
                        setCellBandwidths(row, NrMessageConstants.CELL_BANDWIDTHS_KHZ_COLUMN, data.getCellBandwidthsKhzList());

                        featureDao.insert(row);

                        checkIfRolloverNeeded();
                    }
                } catch (Exception e)
                {
                    Timber.e(e, "Something went wrong when trying to write an NR survey record");
                }
            }
        });
    }

    /**
     * Sets the connection status column on the row. An UNKNOWN status is left unset because an
     * absent value means the device did not report a status (matching the JSON serialization,
     * which omits the default UNKNOWN value).
     *
     * @param row              The GeoPackage row to update.
     * @param columnName       The connection status column name for the record's table.
     * @param connectionStatus The connection status from the record.
     */
    private void setConnectionStatus(FeatureRow row, String columnName, ConnectionStatus connectionStatus)
    {
        if (connectionStatus == ConnectionStatus.UNKNOWN || connectionStatus == ConnectionStatus.UNRECOGNIZED)
        {
            return;
        }

        row.setValue(columnName, connectionStatus.name());
    }

    /**
     * Sets the per-carrier cell bandwidths column on the row as semicolon-joined kHz values
     * (e.g. "20000;10000"). An empty list leaves the column unset because an absent value means
     * the device did not report bandwidths.
     *
     * @param row               The GeoPackage row to update.
     * @param columnName        The cell bandwidths column name for the record's table.
     * @param cellBandwidthsKhz The per-carrier bandwidths in kHz from the record.
     */
    private void setCellBandwidths(FeatureRow row, String columnName, List<Integer> cellBandwidthsKhz)
    {
        if (cellBandwidthsKhz == null || cellBandwidthsKhz.isEmpty()) return;

        row.setValue(columnName, cellBandwidthsKhz.stream().map(String::valueOf).collect(Collectors.joining(";")));
    }
}
