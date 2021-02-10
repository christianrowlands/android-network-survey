package com.craxiom.networksurvey.dao;

import android.database.Cursor;

import com.craxiom.networksurvey.models.message.GnssModel;
import com.craxiom.networksurvey.models.tableschemas.MessageTableSchema;

import java.util.ArrayList;

import mil.nga.geopackage.GeoPackage;

public class GnssDao {
    public static ArrayList<MessageTableSchema> getGnssTableSchema(GeoPackage geoPackage)
    {
        ArrayList<MessageTableSchema> results = new ArrayList<>();
        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery("PRAGMA table_info([GNSS_MESSAGE]);", null);

        if (cursor.moveToFirst()) {
            do {
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

    public static ArrayList<GnssModel> getAllGnssRecords(GeoPackage geoPackage)
    {
        ArrayList<GnssModel> results = new ArrayList<>();
        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery("SELECT * FROM [GNSS_MESSAGE];", null);

        if (cursor.moveToFirst()) {
            do {
                GnssModel model = new GnssModel.GnssModelBuilder()
                        .setId(cursor.getInt(0))
                        .setGeom(String.valueOf(cursor.getBlob(1)))
                        .setTime(cursor.getInt(2))
                        .setRecordNumber(cursor.getInt(3))
                        .setGroupNumber(cursor.getInt(4))
                        .setConstellation(cursor.getString(5))
                        .setSpaceVehicleId(cursor.getInt(6))
                        .setCarrierFrequencyHz(cursor.getInt(7))
                        .setLatitudeStandardDeviation(cursor.getFloat(8))
                        .setLongitudeStandardDeviation(cursor.getFloat(9))
                        .setAltitudeStandardDeviation(cursor.getFloat(10))
                        .setAgcDb(cursor.getFloat(11))
                        .setCN0(cursor.getFloat(12))
                        .build();
                results.add(model);
            } while (cursor.moveToNext());
        }
        return results;
    }


    public static ArrayList<GnssModel> getAllGnssRecordsWithAllColumnsPopulated(GeoPackage geoPackage)
    {
        ArrayList<GnssModel> results = new ArrayList<>();

        String query = "SELECT *\n" +
                "FROM [GNSS_MESSAGE]\n" +
                "WHERE (geom IS NOT NULL)\n" +
                "    AND (Time IS NOT NULL)\n" +
                "    AND (Constellation IS NOT NULL)\n" +
                "    AND ([Space Vehicle Id] IS NOT NULL)\n" +
                "    AND ([Carrier Frequency Hz] IS NOT NULL)\n" +
                "    AND ([Latitude Standard Deviation (m)] IS NOT NULL)\n" +
                "    AND ([Longitude Standard Deviation (m)] IS NOT NULL)\n" +
                "    AND ([Altitude Standard Deviation (m)] IS NOT NULL)\n" +
                "    AND ([AGC dB] IS NOT NULL)\n" +
                "    AND ([C/N0 (dB-Hz)] IS NOT NULL);";

        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                GnssModel model = new GnssModel.GnssModelBuilder()
                        .setId(cursor.getInt(0))
                        .setGeom(String.valueOf(cursor.getBlob(1)))
                        .setTime(cursor.getInt(2))
                        .setRecordNumber(cursor.getInt(3))
                        .setGroupNumber(cursor.getInt(4))
                        .setConstellation(cursor.getString(5))
                        .setSpaceVehicleId(cursor.getInt(6))
                        .setCarrierFrequencyHz(cursor.getInt(7))
                        .setLatitudeStandardDeviation(cursor.getFloat(8))
                        .setLongitudeStandardDeviation(cursor.getFloat(9))
                        .setAltitudeStandardDeviation(cursor.getFloat(10))
                        .setAgcDb(cursor.getFloat(11))
                        .setCN0(cursor.getFloat(12))
                        .build();
                results.add(model);
            } while (cursor.moveToNext());
        }
        return results;
    }


    public static Boolean allNonNullColumnsArePopulated(GeoPackage geoPackage)
    {
        Boolean hit = true;
        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery("SELECT * FROM [GNSS_MESSAGE] WHERE id IS NULL OR RecordNumber IS NULL OR GroupNumber IS NULL", null);

        if (cursor.moveToFirst()) {
            hit = false;
        }
        return hit;
    }
}
