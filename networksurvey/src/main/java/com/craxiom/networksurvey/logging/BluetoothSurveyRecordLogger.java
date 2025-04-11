package com.craxiom.networksurvey.logging;

import android.os.Looper;

import com.craxiom.messaging.BluetoothRecord;
import com.craxiom.messaging.BluetoothRecordData;
import com.craxiom.messaging.bluetooth.AddressType;
import com.craxiom.messaging.bluetooth.SupportedTechnologies;
import com.craxiom.messaging.bluetooth.Technology;
import com.craxiom.networksurvey.constants.BluetoothMessageConstants;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants;
import com.craxiom.networksurvey.constants.csv.CsvConstants;
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.util.MathUtils;
import com.craxiom.networksurvey.util.NsUtils;

import java.sql.SQLException;
import java.util.List;

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
 * Responsible for taking Bluetooth survey records, and writing them to the GeoPackage log file.
 *
 * @since 1.0.0
 */
public class BluetoothSurveyRecordLogger extends SurveyRecordLogger implements IBluetoothSurveyRecordListener
{
    /**
     * Constructs a Logger that writes Bluetooth Survey records to a GeoPackage SQLite database.
     *
     * @param networkSurveyService The Service instance that is running this logger.
     * @param serviceLooper        The Looper associated with the service that can be used to do any background processing.
     */
    public BluetoothSurveyRecordLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper)
    {
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.LOG_DIRECTORY_NAME, NetworkSurveyConstants.BLUETOOTH_FILE_NAME_PREFIX);
    }

    @Override
    public void onBluetoothSurveyRecord(BluetoothRecord bluetoothRecord)
    {
        writeBluetoothRecordToLogFile(bluetoothRecord);
    }

    @Override
    public void onBluetoothSurveyRecords(List<BluetoothRecord> bluetoothRecords)
    {
        bluetoothRecords.forEach(this::writeBluetoothRecordToLogFile);
    }

    @Override
    void createTables(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException
    {
        createBluetoothRecordTable(geoPackage, srs);
    }

    /**
     * Creates an GeoPackage Table that can be populated with 802.11 Beacon Records.
     *
     * @param geoPackage The GeoPackage to create the table in.
     * @param srs        The SRS to use for the table coordinates.
     * @throws SQLException If there is a problem working with the GeoPackage SQLite DB.
     */
    private void createBluetoothRecordTable(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException
    {
        createTable(BluetoothMessageConstants.BLUETOOTH_RECORDS_TABLE_NAME, geoPackage, srs, false, (tableColumns, columnNumber) -> {
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, BluetoothMessageConstants.SOURCE_ADDRESS_COLUMN, GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, BluetoothMessageConstants.OTA_DEVICE_NAME_COLUMN, GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, BluetoothMessageConstants.TECHNOLOGY_COLUMN, GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, BluetoothMessageConstants.SUPPORTED_TECHNOLOGIES_COLUMN, GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, BluetoothMessageConstants.TX_POWER_COLUMN, GeoPackageDataType.FLOAT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, BluetoothMessageConstants.SIGNAL_STRENGTH_COLUMN, GeoPackageDataType.FLOAT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, BluetoothCsvConstants.CHANNEL, GeoPackageDataType.SMALLINT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, BluetoothCsvConstants.ADDRESS_TYPE, GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, BluetoothCsvConstants.DEVICE_CLASS, GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, BluetoothCsvConstants.SERVICE_UUIDS, GeoPackageDataType.TEXT, false, null));
            //noinspection UnusedAssignment
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, BluetoothCsvConstants.COMPANY_ID, GeoPackageDataType.TEXT, false, null));
        });
    }

    /**
     * Given a Bluetooth Record, write it to the GeoPackage log file.
     *
     * @param bluetoothRecord The Bluetooth Record to write to the log file.
     */
    private void writeBluetoothRecordToLogFile(final BluetoothRecord bluetoothRecord)
    {
        if (!loggingEnabled) return;

        handler.post(() -> {
            synchronized (geoPackageLock)
            {
                try
                {
                    if (geoPackage != null)
                    {
                        final BluetoothRecordData data = bluetoothRecord.getData();
                        FeatureDao featureDao = geoPackage.getFeatureDao(BluetoothMessageConstants.BLUETOOTH_RECORDS_TABLE_NAME);
                        FeatureRow row = featureDao.newRow();

                        Point fix = new Point(data.getLongitude(), data.getLatitude(), (double) data.getAltitude());

                        GeoPackageGeometryData geomData = new GeoPackageGeometryData(WGS84_SRS);
                        geomData.setGeometry(fix);

                        row.setGeometry(geomData);

                        row.setValue(BluetoothCsvConstants.DEVICE_SERIAL_NUMBER, data.getDeviceSerialNumber());
                        row.setValue(BluetoothMessageConstants.TIME_COLUMN, NsUtils.getEpochFromRfc3339(data.getDeviceTime()));
                        row.setValue(BluetoothMessageConstants.MISSION_ID_COLUMN, data.getMissionId());
                        row.setValue(BluetoothMessageConstants.RECORD_NUMBER_COLUMN, data.getRecordNumber());
                        row.setValue(BluetoothCsvConstants.SPEED, data.getSpeed());
                        row.setValue(BluetoothMessageConstants.ACCURACY, MathUtils.roundAccuracy(data.getAccuracy()));
                        row.setValue(CsvConstants.LOCATION_AGE, data.getLocationAge());

                        final String sourceAddress = data.getSourceAddress();
                        if (!sourceAddress.isEmpty())
                        {
                            row.setValue(BluetoothMessageConstants.SOURCE_ADDRESS_COLUMN, sourceAddress);
                        }

                        if (data.hasSignalStrength())
                        {
                            row.setValue(BluetoothMessageConstants.SIGNAL_STRENGTH_COLUMN, data.getSignalStrength().getValue());
                        }

                        if (data.hasTxPower())
                        {
                            row.setValue(BluetoothMessageConstants.TX_POWER_COLUMN, data.getTxPower().getValue());
                        }

                        final Technology technology = data.getTechnology();
                        if (technology != Technology.UNKNOWN)
                        {
                            row.setValue(BluetoothMessageConstants.TECHNOLOGY_COLUMN, BluetoothMessageConstants.getTechnologyString(technology));
                        }

                        final SupportedTechnologies supportedTech = data.getSupportedTechnologies();
                        if (supportedTech != SupportedTechnologies.UNKNOWN)
                        {
                            row.setValue(BluetoothMessageConstants.SUPPORTED_TECHNOLOGIES_COLUMN, BluetoothMessageConstants.getSupportedTechString(supportedTech));
                        }

                        final String otaDeviceName = data.getOtaDeviceName();
                        if (!otaDeviceName.isEmpty())
                        {
                            row.setValue(BluetoothMessageConstants.OTA_DEVICE_NAME_COLUMN, otaDeviceName);
                        }

                        if (data.hasChannel())
                        {
                            row.setValue(BluetoothCsvConstants.CHANNEL, data.getChannel());
                        }

                        final AddressType addressType = data.getAddressType();
                        if (addressType != AddressType.UNRECOGNIZED)
                        {
                            row.setValue(BluetoothCsvConstants.ADDRESS_TYPE, data.getAddressType().name());
                        }

                        final String deviceClass = data.getDeviceClass();
                        if (!deviceClass.isEmpty())
                        {
                            row.setValue(BluetoothCsvConstants.DEVICE_CLASS, deviceClass);
                        }

                        final List<String> serviceUuids = data.getServiceUuidsList();
                        if (!serviceUuids.isEmpty())
                        {
                            row.setValue(BluetoothCsvConstants.SERVICE_UUIDS, String.join(";", data.getServiceUuidsList()));
                        }

                        String companyId = data.getCompanyId();
                        if (!companyId.isEmpty())
                        {
                            row.setValue(BluetoothCsvConstants.COMPANY_ID, companyId);
                        }

                        featureDao.insert(row);

                        checkIfRolloverNeeded();
                    }
                } catch (Exception e)
                {
                    Timber.e(e, "Something went wrong when trying to write a Bluetooth survey record");
                }
            }
        });
    }
}
