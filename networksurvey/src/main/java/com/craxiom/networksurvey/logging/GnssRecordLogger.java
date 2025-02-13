package com.craxiom.networksurvey.logging;

import android.os.Looper;

import com.craxiom.messaging.GnssRecord;
import com.craxiom.messaging.GnssRecordData;
import com.craxiom.messaging.gnss.Constellation;
import com.craxiom.networksurvey.constants.GnssMessageConstants;
import com.craxiom.networksurvey.constants.MessageConstants;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.constants.csv.CsvConstants;
import com.craxiom.networksurvey.constants.csv.GnssCsvConstants;
import com.craxiom.networksurvey.listeners.IGnssSurveyRecordListener;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.util.MathUtils;
import com.craxiom.networksurvey.util.NsUtils;

import java.sql.SQLException;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.db.GeoPackageDataType;
import mil.nga.geopackage.features.user.FeatureColumn;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.features.user.FeatureRow;
import mil.nga.geopackage.geom.GeoPackageGeometryData;
import mil.nga.geopackage.srs.SpatialReferenceSystem;
import mil.nga.sf.Point;
import timber.log.Timber;

/**
 * Responsible for taking GNSS survey records, and writing them to the GeoPackage log file.
 *
 * @since 0.3.0
 */
public class GnssRecordLogger extends SurveyRecordLogger implements IGnssSurveyRecordListener
{
    /**
     * Constructs a Logger that writes GNSS Survey records to a GeoPackage SQLite database.
     *
     * @param networkSurveyService The Service instance that is running this logger.
     * @param serviceLooper        The Looper associated with the service that can be used to do any background processing.
     */
    public GnssRecordLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper)
    {
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.LOG_DIRECTORY_NAME, NetworkSurveyConstants.GNSS_FILE_NAME_PREFIX);
    }

    @Override
    public void onGnssSurveyRecord(GnssRecord gnssRecord)
    {
        writeGnssRecordToLogFile(gnssRecord);
    }

    @Override
    void createTables(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException
    {
        createGnssRecordTable(geoPackage, srs);
    }

    /**
     * Creates an GeoPackage Table that can be populated with GNSS Records.
     *
     * @param geoPackage The GeoPackage to create the table in.
     * @param srs        The SRS to use for the table coordinates.
     * @throws SQLException If there is a problem working with the GeoPackage SQLite DB.
     */
    private void createGnssRecordTable(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException
    {
        createTable(GnssMessageConstants.GNSS_RECORDS_TABLE_NAME, geoPackage, srs, false, (tableColumns, columnNumber) -> {
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GnssMessageConstants.GROUP_NUMBER_COLUMN, GeoPackageDataType.MEDIUMINT, true, -1));

            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GnssMessageConstants.CONSTELLATION, GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GnssMessageConstants.SPACE_VEHICLE_ID, GeoPackageDataType.MEDIUMINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GnssMessageConstants.CARRIER_FREQUENCY_HZ, GeoPackageDataType.INT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GnssMessageConstants.LATITUDE_STD_DEV_M, GeoPackageDataType.FLOAT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GnssMessageConstants.LONGITUDE_STD_DEV_M, GeoPackageDataType.FLOAT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GnssMessageConstants.ALTITUDE_STD_DEV_M, GeoPackageDataType.FLOAT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GnssMessageConstants.AGC_DB, GeoPackageDataType.FLOAT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GnssMessageConstants.CARRIER_TO_NOISE_DENSITY_DB_HZ, GeoPackageDataType.FLOAT, false, null));
            //noinspection UnusedAssignment
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, GnssMessageConstants.DEVICE_MODEL_COLUMN, GeoPackageDataType.TEXT, false, null));
        });
    }

    /**
     * Given a GNSS Record, write it to the GeoPackage log file.
     *
     * @param gnssRecord The GNSS Record to write to the log file.
     */
    private void writeGnssRecordToLogFile(final GnssRecord gnssRecord)
    {
        if (!loggingEnabled) return;

        handler.post(() -> {
            synchronized (geoPackageLock)
            {
                try
                {
                    if (geoPackage != null)
                    {
                        final GnssRecordData data = gnssRecord.getData();
                        FeatureDao featureDao = geoPackage.getFeatureDao(GnssMessageConstants.GNSS_RECORDS_TABLE_NAME);
                        FeatureRow row = featureDao.newRow();

                        Point fix = new Point(data.getLongitude(), data.getLatitude(), (double) data.getAltitude());

                        GeoPackageGeometryData geomData = new GeoPackageGeometryData(WGS84_SRS);
                        geomData.setGeometry(fix);

                        row.setGeometry(geomData);

                        row.setValue(GnssCsvConstants.DEVICE_SERIAL_NUMBER, data.getDeviceSerialNumber());
                        row.setValue(GnssMessageConstants.TIME_COLUMN, NsUtils.getEpochFromRfc3339(data.getDeviceTime()));
                        row.setValue(GnssMessageConstants.MISSION_ID_COLUMN, data.getMissionId());
                        row.setValue(GnssMessageConstants.RECORD_NUMBER_COLUMN, data.getRecordNumber());
                        row.setValue(GnssMessageConstants.GROUP_NUMBER_COLUMN, data.getGroupNumber());
                        row.setValue(GnssMessageConstants.DEVICE_MODEL_COLUMN, data.getDeviceModel());
                        row.setValue(GnssCsvConstants.SPEED, data.getSpeed());
                        row.setValue(MessageConstants.ACCURACY, MathUtils.roundAccuracy(data.getAccuracy()));
                        row.setValue(CsvConstants.LOCATION_AGE, data.getLocationAge());

                        final Constellation constellation = data.getConstellation();
                        if (constellation != Constellation.UNKNOWN)
                        {
                            row.setValue(GnssMessageConstants.CONSTELLATION, GnssMessageConstants.getConstellationString(constellation));
                        }

                        if (data.hasSpaceVehicleId())
                        {
                            row.setValue(GnssMessageConstants.SPACE_VEHICLE_ID, data.getSpaceVehicleId().getValue());
                        }

                        if (data.hasCarrierFreqHz())
                        {
                            row.setValue(GnssMessageConstants.CARRIER_FREQUENCY_HZ, data.getCarrierFreqHz().getValue());
                        }

                        if (data.hasLatitudeStdDevM())
                        {
                            row.setValue(GnssMessageConstants.LATITUDE_STD_DEV_M, data.getLatitudeStdDevM().getValue());
                        }

                        if (data.hasLongitudeStdDevM())
                        {
                            row.setValue(GnssMessageConstants.LONGITUDE_STD_DEV_M, data.getLongitudeStdDevM().getValue());
                        }

                        if (data.hasAltitudeStdDevM())
                        {
                            row.setValue(GnssMessageConstants.ALTITUDE_STD_DEV_M, data.getAltitudeStdDevM().getValue());
                        }

                        if (data.hasAgcDb())
                        {
                            row.setValue(GnssMessageConstants.AGC_DB, data.getAgcDb().getValue());
                        }

                        if (data.hasCn0DbHz())
                        {
                            row.setValue(GnssMessageConstants.CARRIER_TO_NOISE_DENSITY_DB_HZ, data.getCn0DbHz().getValue());
                        }

                        featureDao.insert(row);

                        checkIfRolloverNeeded();
                    }
                } catch (Exception e)
                {
                    Timber.e(e, "Something went wrong when trying to write a GNSS survey record");
                }
            }
        });
    }
}
