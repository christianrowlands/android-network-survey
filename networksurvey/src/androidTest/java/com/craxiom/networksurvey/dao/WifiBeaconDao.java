package com.craxiom.networksurvey.dao;

import android.database.Cursor;
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants;
import com.craxiom.networksurvey.models.message.WifiBeaconModel;
import mil.nga.geopackage.GeoPackage;

import java.util.ArrayList;

public class WifiBeaconDao
{

    public static ArrayList<WifiBeaconModel> baseQuery(GeoPackage geoPackage, String query)
    {
        ArrayList<WifiBeaconModel> results = new ArrayList<>();

        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery(query, null);

        if (cursor.moveToFirst())
        {
            do
            {
                WifiBeaconModel model = new WifiBeaconModel.WifiBeaconModelBuilder()
                        .setId(cursor.getInt(cursor.getColumnIndex(WifiBeaconMessageConstants.ID_COLUMN)))
                        .setGeom(String.valueOf(cursor.getBlob(cursor.getColumnIndex(WifiBeaconMessageConstants.GEOMETRY_COLUMN))))
                        .setTime(cursor.getInt(cursor.getColumnIndex(WifiBeaconMessageConstants.TIME_COLUMN)))
                        .setRecordNumber(cursor.getInt(cursor.getColumnIndex(WifiBeaconMessageConstants.RECORD_NUMBER_COLUMN)))
                        .setBssid(cursor.getString(cursor.getColumnIndex(WifiBeaconMessageConstants.BSSID_COLUMN)))
                        .setSsid(cursor.getString(cursor.getColumnIndex(WifiBeaconMessageConstants.SSID_COLUMN)))
                        .setChannel(cursor.getShort(cursor.getColumnIndex(WifiBeaconMessageConstants.CHANNEL_COLUMN)))
                        .setFrequency(cursor.getInt(cursor.getColumnIndex(WifiBeaconMessageConstants.FREQUENCY_MHZ_COLUMN)))
                        .setCipherSuites(cursor.getString(cursor.getColumnIndex(WifiBeaconMessageConstants.CIPHER_SUITES_COLUMN)))
                        .setAkmSuites(cursor.getString(cursor.getColumnIndex(WifiBeaconMessageConstants.AKM_SUITES_COLUMN)))
                        .setEncryptionType(cursor.getString(cursor.getColumnIndex(WifiBeaconMessageConstants.ENCRYPTION_TYPE_COLUMN)))
                        .setWps(Boolean.getBoolean(cursor.getString(cursor.getColumnIndex(WifiBeaconMessageConstants.WPS_COLUMN))))
                        .setSignalStrength(cursor.getFloat(cursor.getColumnIndex(WifiBeaconMessageConstants.SIGNAL_STRENGTH_COLUMN)))
                        .build();
                results.add(model);
            } while (cursor.moveToNext());
        }
        return results;
    }

    public static ArrayList<WifiBeaconModel> getAllRecords(GeoPackage geoPackage)
    {
        String query = String.format("SELECT * FROM [%s];", WifiBeaconMessageConstants.WIFI_BEACON_RECORDS_TABLE_NAME);
        return baseQuery(geoPackage, query);
    }

    /**
     * Ciper Suites and AKM suites are not included as we get those values less often
     *
     * @param geoPackage GeoPackage supplied by the GeoPackage Manager
     * @return ArrayList of WifiBeaconModel results
     */
    public static ArrayList<WifiBeaconModel> getRecordsWithAllColumnsPopulated(GeoPackage geoPackage)
    {
        String query = String.format("SELECT * FROM [%s]\n" +
                "WHERE (geom IS NOT NULL)\n" +
                "    AND (Time IS NOT NULL)\n" +
                "    AND (BSSID IS NOT NULL)\n" +
                "    AND (SSID IS NOT NULL)\n" +
                "    AND (Channel IS NOT NULL)\n" +
                "    AND (Frequency IS NOT NULL)\n" +
                "    AND ([Encryption_Type] IS NOT NULL)\n" +
                "    AND (WPS IS NOT NULL)\n" +
                "    AND ([Signal Strength] IS NOT NULL);", WifiBeaconMessageConstants.WIFI_BEACON_RECORDS_TABLE_NAME);

        return baseQuery(geoPackage, query);
    }

    public static boolean allNonNullColumnsArePopulated(GeoPackage geoPackage)
    {
        String query = String.format("SELECT * FROM [%s] WHERE id IS NULL OR RecordNumber IS NULL", WifiBeaconMessageConstants.WIFI_BEACON_RECORDS_TABLE_NAME);
        return CommonDao.allNonNullColumnsArePopulated(geoPackage, query);
    }
}
