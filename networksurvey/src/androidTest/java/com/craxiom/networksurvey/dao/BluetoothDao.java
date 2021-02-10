package com.craxiom.networksurvey.dao;

import android.database.Cursor;
import com.craxiom.networksurvey.models.message.BluetoothModel;
import com.craxiom.networksurvey.models.tableschemas.MessageTableSchema;
import mil.nga.geopackage.GeoPackage;

import java.util.ArrayList;

public class BluetoothDao
{
    public static ArrayList<BluetoothModel> getAllBluetoothRecords(GeoPackage geoPackage)
    {
        ArrayList<BluetoothModel> results = new ArrayList<>();

        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery("SELECT * FROM [BLUETOOTH_MESSAGE];", null);

        if (cursor.moveToFirst())
        {
            do
            {
                BluetoothModel model = new BluetoothModel.BluetoothTableSchemaModelBuilder()
                        .setId(cursor.getInt(0))
                        .setGeom(String.valueOf(cursor.getBlob(1)))
                        .setTime(cursor.getInt(2))
                        .setRecordNumber(cursor.getInt(3))
                        .setSourceAddress(cursor.getString(4))
                        .setOtaDeviceName(cursor.getString(5))
                        .setTechnology(cursor.getString(6))
                        .setSupportedTechnologies(cursor.getString(7))
                        .setTxPower(cursor.getFloat(8))
                        .setSignalStrength(cursor.getFloat(9))
                        .build();
                results.add(model);
            } while (cursor.moveToNext());
        }
        return results;
    }

    /**
     * Tx Power and Technology are omitted since I could not gather any data with those values included.
     *
     * @param geoPackage GeoPackage supplied by the GeoPackage Manager
     * @return ArrayList of BluetoothModel results
     */
    public static ArrayList<BluetoothModel> getAllBluetoothRecordsWithAllColumnsPopulated(GeoPackage geoPackage)
    {
        ArrayList<BluetoothModel> results = new ArrayList<>();

        String query = "SELECT * FROM [BLUETOOTH_MESSAGE]\n" +
                "WHERE (geom IS NOT NULL)\n" +
                "    AND (Time IS NOT NULL)\n" +
                "    AND ([Source Address] IS NOT NULL)\n" +
                "    AND ([OTA Device Name] IS NOT NULL)\n" +
                "    AND ([Supported Technologies] IS NOT NULL)\n" +
                "    AND ([Signal Strength] IS NOT NULL);";

        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery(query, null);

        if (cursor.moveToFirst())
        {
            do
            {
                BluetoothModel model = new BluetoothModel.BluetoothTableSchemaModelBuilder()
                        .setId(cursor.getInt(0))
                        .setGeom(String.valueOf(cursor.getBlob(1)))
                        .setTime(cursor.getInt(2))
                        .setRecordNumber(cursor.getInt(3))
                        .setSourceAddress(cursor.getString(4))
                        .setOtaDeviceName(cursor.getString(5))
                        .setTechnology(cursor.getString(6))
                        .setSupportedTechnologies(cursor.getString(7))
                        .setTxPower(cursor.getFloat(8))
                        .setSignalStrength(cursor.getFloat(9))
                        .build();
                results.add(model);
            } while (cursor.moveToNext());
        }
        return results;
    }

    public static boolean allNonNullColumnsArePopulated(GeoPackage geoPackage)
    {
        Boolean hit = true;
        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery("SELECT * FROM [BLUETOOTH_MESSAGE] WHERE id IS NULL OR RecordNumber IS NULL", null);

        if (cursor.moveToFirst())
        {
            hit = false;
        }
        return hit;
    }

    public static ArrayList<MessageTableSchema> getBluetoothTableSchema(GeoPackage geoPackage)
    {
        ArrayList<MessageTableSchema> results = new ArrayList<>();
        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery("PRAGMA table_info([BLUETOOTH_MESSAGE]);", null);

        if (cursor.moveToFirst())
        {
            do
            {
                MessageTableSchema model = new MessageTableSchema.MessageTableSchemaModelBuilder()
                        .setCid(cursor.getInt(0))
                        .setName(cursor.getString(1))
                        .setType(String.valueOf(cursor.getType(2)))
                        .setNotNull(cursor.getInt(3))
                        .setDefaultValue(cursor.getInt(4))
                        .setPrimaryKey(cursor.getInt(5))
                        .build();
                results.add(model);
            } while (cursor.moveToNext());
        }
        return results;
    }
}
