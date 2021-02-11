package com.craxiom.networksurvey.dao;

import android.database.Cursor;

import com.craxiom.networksurvey.models.message.WifiBeaconModel;
import com.craxiom.networksurvey.models.tableschemas.MessageTableSchema;

import java.util.ArrayList;

import mil.nga.geopackage.GeoPackage;

public class WifiBeaconDao
{
    public static ArrayList<WifiBeaconModel> getAllWifiBeaconRecords(GeoPackage geoPackage)
    {
        ArrayList<WifiBeaconModel> results = new ArrayList<>();

        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery("SELECT * FROM [80211_BEACON_MESSAGE];", null);

        if (cursor.moveToFirst())
        {
            do
            {
                WifiBeaconModel model = new WifiBeaconModel.WifiBeaconModelBuilder()
                        .setId(cursor.getInt(0))
                        .setGeom(String.valueOf(cursor.getBlob(1)))
                        .setTime(cursor.getInt(2))
                        .setRecordNumber(cursor.getInt(3))
                        .setBssid(cursor.getString(4))
                        .setSsid(cursor.getString(5))
                        .setChannel(cursor.getShort(6))
                        .setFrequency(cursor.getInt(7))
                        .setCipherSuites(cursor.getString(8))
                        .setAkmSuites(cursor.getString(9))
                        .setEncryptionType(cursor.getString(10))
                        .setWps(Boolean.getBoolean(cursor.getString(11)))
                        .setSignalStrength(cursor.getFloat(12))
                        .build();
                results.add(model);
            } while (cursor.moveToNext());
        }
        return results;
    }

    /**
     * Ciper Suites and AKM suites are not included as we get those values less often
     *
     * @param geoPackage GeoPackage supplied by the GeoPackage Manager
     * @return ArrayList of WifiBeaconModel results
     */
    public static ArrayList<WifiBeaconModel> getAllWifiBeaconRecordsWithAllColumnsPopulated(GeoPackage geoPackage)
    {
        ArrayList<WifiBeaconModel> results = new ArrayList<>();

        String query = "SELECT * FROM [80211_BEACON_MESSAGE]\n" +
                "WHERE (geom IS NOT NULL)\n" +
                "    AND (Time IS NOT NULL)\n" +
                "    AND (BSSID IS NOT NULL)\n" +
                "    AND (SSID IS NOT NULL)\n" +
                "    AND (Channel IS NOT NULL)\n" +
                "    AND (Frequency IS NOT NULL)\n" +
                "    AND ([Encryption_Type] IS NOT NULL)\n" +
                "    AND (WPS IS NOT NULL)\n" +
                "    AND ([Signal Strength] IS NOT NULL);";

        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery(query, null);

        if (cursor.moveToFirst())
        {
            do
            {
                WifiBeaconModel model = new WifiBeaconModel.WifiBeaconModelBuilder()
                        .setId(cursor.getInt(0))
                        .setGeom(String.valueOf(cursor.getBlob(1)))
                        .setTime(cursor.getInt(2))
                        .setRecordNumber(cursor.getInt(3))
                        .setBssid(cursor.getString(4))
                        .setSsid(cursor.getString(5))
                        .setChannel(cursor.getShort(6))
                        .setFrequency(cursor.getInt(7))
                        .setCipherSuites(cursor.getString(8))
                        .setAkmSuites(cursor.getString(9))
                        .setEncryptionType(cursor.getString(10))
                        .setWps(Boolean.getBoolean(cursor.getString(11)))
                        .setSignalStrength(cursor.getFloat(12))
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
                .rawQuery("SELECT * FROM [80211_BEACON_MESSAGE] WHERE id IS NULL OR RecordNumber IS NULL", null);

        if (cursor.moveToFirst())
        {
            hit = false;
        }
        return hit;
    }
}
