package com.craxiom.networksurvey.dao;

import android.database.Cursor;
import com.craxiom.networksurvey.constants.BluetoothMessageConstants;
import com.craxiom.networksurvey.models.message.BluetoothModel;
import mil.nga.geopackage.GeoPackage;

import java.util.ArrayList;

public class BluetoothDao
{

    public static ArrayList<BluetoothModel> baseQuery(GeoPackage geoPackage, String query)
    {
        ArrayList<BluetoothModel> results = new ArrayList<>();

        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery(query, null);

        if (cursor.moveToFirst())
        {
            do
            {
                BluetoothModel model = new BluetoothModel.BluetoothTableSchemaModelBuilder()
                        .setId(cursor.getInt(cursor.getColumnIndex(BluetoothMessageConstants.ID_COLUMN)))
                        .setGeom(String.valueOf(cursor.getBlob(cursor.getColumnIndex(BluetoothMessageConstants.GEOMETRY_COLUMN))))
                        .setTime(cursor.getLong(cursor.getColumnIndex(BluetoothMessageConstants.TIME_COLUMN)))
                        .setRecordNumber(cursor.getInt(cursor.getColumnIndex(BluetoothMessageConstants.RECORD_NUMBER_COLUMN)))
                        .setSourceAddress(cursor.getString(cursor.getColumnIndex(BluetoothMessageConstants.SOURCE_ADDRESS_COLUMN)))
                        .setOtaDeviceName(cursor.getString(cursor.getColumnIndex(BluetoothMessageConstants.OTA_DEVICE_NAME_COLUMN)))
                        .setTechnology(cursor.getString(cursor.getColumnIndex(BluetoothMessageConstants.TECHNOLOGY_COLUMN)))
                        .setSupportedTechnologies(cursor.getString(cursor.getColumnIndex(BluetoothMessageConstants.SUPPORTED_TECHNOLOGIES_COLUMN)))
                        .setTxPower(cursor.getFloat(cursor.getColumnIndex(BluetoothMessageConstants.TX_POWER_COLUMN)))
                        .setSignalStrength(cursor.getFloat(cursor.getColumnIndex(BluetoothMessageConstants.SIGNAL_STRENGTH_COLUMN)))
                        .build();
                results.add(model);
            } while (cursor.moveToNext());
        }
        return results;
    }

    public static ArrayList<BluetoothModel> getAllRecords(GeoPackage geoPackage)
    {
        String query = String.format("SELECT * FROM [%s]", BluetoothMessageConstants.BLUETOOTH_RECORDS_TABLE_NAME);
        return baseQuery(geoPackage, query);
    }

    /**
     * Tx Power and Technology are omitted since I could not gather any data with those values included.
     *
     * @param geoPackage GeoPackage supplied by the GeoPackage Manager
     * @return ArrayList of BluetoothModel results
     */
    public static ArrayList<BluetoothModel> getRecordsWithAllColumnsPopulated(GeoPackage geoPackage)
    {

        String query = String.format("SELECT * FROM [%s]\n" +
                "WHERE (geom IS NOT NULL)\n" +
                "    AND (Time IS NOT NULL)\n" +
                "    AND ([Source Address] IS NOT NULL)\n" +
                "    AND ([OTA Device Name] IS NOT NULL)\n" +
                "    AND ([Supported Technologies] IS NOT NULL)\n" +
                "    AND ([Signal Strength] IS NOT NULL);", BluetoothMessageConstants.BLUETOOTH_RECORDS_TABLE_NAME);

        return baseQuery(geoPackage, query);
    }

    public static boolean allNonNullColumnsArePopulated(GeoPackage geoPackage)
    {
        String query = String.format("SELECT * FROM [%s] WHERE id IS NULL OR RecordNumber IS NULL", BluetoothMessageConstants.BLUETOOTH_RECORDS_TABLE_NAME);
        return CommonDao.allNonNullColumnsArePopulated(geoPackage, query);
    }
}
