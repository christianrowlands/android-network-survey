package com.craxiom.networksurvey.dao;

import android.database.Cursor;
import com.craxiom.networksurvey.constants.GnssMessageConstants;
import com.craxiom.networksurvey.models.message.GnssModel;
import mil.nga.geopackage.GeoPackage;

import java.util.ArrayList;

public class GnssDao
{

    public static ArrayList<GnssModel> baseQuery(GeoPackage geoPackage, String query)
    {
        ArrayList<GnssModel> results = new ArrayList<>();
        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery(query, null);

        if (cursor.moveToFirst())
        {
            do
            {
                GnssModel model = new GnssModel.GnssModelBuilder()
                        .setId(cursor.getInt(cursor.getColumnIndex(GnssMessageConstants.ID_COLUMN)))
                        .setGeom(String.valueOf(cursor.getBlob(cursor.getColumnIndex(GnssMessageConstants.GEOMETRY_COLUMN))))
                        .setTime(cursor.getInt(cursor.getColumnIndex(GnssMessageConstants.TIME_COLUMN)))
                        .setRecordNumber(cursor.getInt(cursor.getColumnIndex(GnssMessageConstants.RECORD_NUMBER_COLUMN)))
                        .setGroupNumber(cursor.getInt(cursor.getColumnIndex(GnssMessageConstants.GROUP_NUMBER_COLUMN)))
                        .setConstellation(cursor.getString(cursor.getColumnIndex(GnssMessageConstants.CONSTELLATION)))
                        .setSpaceVehicleId(cursor.getInt(cursor.getColumnIndex(GnssMessageConstants.SPACE_VEHICLE_ID)))
                        .setCarrierFrequencyHz(cursor.getInt(cursor.getColumnIndex(GnssMessageConstants.CARRIER_FREQUENCY_HZ)))
                        .setLatitudeStandardDeviation(cursor.getFloat(cursor.getColumnIndex(GnssMessageConstants.LATITUDE_STD_DEV_M)))
                        .setLongitudeStandardDeviation(cursor.getFloat(cursor.getColumnIndex(GnssMessageConstants.LONGITUDE_STD_DEV_M)))
                        .setAltitudeStandardDeviation(cursor.getFloat(cursor.getColumnIndex(GnssMessageConstants.ALTITUDE_STD_DEV_M)))
                        .setAgcDb(cursor.getFloat(cursor.getColumnIndex(GnssMessageConstants.AGC_DB)))
                        .setCN0(cursor.getFloat(cursor.getColumnIndex(GnssMessageConstants.CARRIER_TO_NOISE_DENSITY_DB_HZ)))
                        .build();
                results.add(model);
            } while (cursor.moveToNext());
        }
        return results;
    }

    public static ArrayList<GnssModel> getAllRecords(GeoPackage geoPackage)
    {
        String query = String.format("SELECT * FROM [%s];", GnssMessageConstants.GNSS_RECORDS_TABLE_NAME);
        return baseQuery(geoPackage, query);
    }

    public static ArrayList<GnssModel> getRecordsWithAllColumnsPopulated(GeoPackage geoPackage)
    {
        String query = String.format("SELECT *\n" +
                "FROM [%s]\n" +
                "WHERE (geom IS NOT NULL)\n" +
                "    AND (Time IS NOT NULL)\n" +
                "    AND (Constellation IS NOT NULL)\n" +
                "    AND ([Space Vehicle Id] IS NOT NULL)\n" +
                "    AND ([Carrier Frequency Hz] IS NOT NULL)\n" +
                "    AND ([Latitude Standard Deviation (m)] IS NOT NULL)\n" +
                "    AND ([Longitude Standard Deviation (m)] IS NOT NULL)\n" +
                "    AND ([Altitude Standard Deviation (m)] IS NOT NULL)\n" +
                "    AND ([AGC dB] IS NOT NULL)\n" +
                "    AND ([C/N0 (dB-Hz)] IS NOT NULL);", GnssMessageConstants.GNSS_RECORDS_TABLE_NAME);

        return baseQuery(geoPackage, query);
    }

    public static Boolean allNonNullColumnsArePopulated(GeoPackage geoPackage)
    {
        String query = String.format("SELECT * FROM [%s] WHERE id IS NULL OR RecordNumber IS NULL OR GroupNumber IS NULL", GnssMessageConstants.GNSS_RECORDS_TABLE_NAME);
        return CommonDao.allNonNullColumnsArePopulated(geoPackage, query);
    }
}
