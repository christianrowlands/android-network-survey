package com.craxiom.networksurvey.dao;

import android.database.Cursor;

import com.craxiom.networksurvey.constants.DeviceStatusMessageConstants;
import com.craxiom.networksurvey.models.message.PhoneStateModel;

import java.util.ArrayList;
import java.util.Arrays;

import mil.nga.geopackage.GeoPackage;

public class PhoneStateDao
{
    public static Boolean allNonNullColumnsArePopulated(GeoPackage geoPackage)
    {
        String query = String.format("SELECT * FROM [%s] WHERE id IS NULL OR RecordNumber IS NULL", DeviceStatusMessageConstants.PHONE_STATE_TABLE_NAME);
        return CommonDao.allNonNullColumnsArePopulated(geoPackage, query);
    }


    public static ArrayList<PhoneStateModel> getRecordsWithAllColumnsPopulated(GeoPackage geoPackage)
    {
        String query = String.format("SELECT *\n" +
                "FROM [%s]\n" +
                "WHERE (geom IS NOT NULL)\n" +
                "    AND (Time IS NOT NULL)\n" +
                "    AND (RecordNumber IS NOT NULL)\n" +
                "    AND (MissionId IS NOT NULL)\n" +
                "    AND (latitude IS NOT NULL)\n" +
                "    AND (longitude IS NOT NULL)\n" +
                "    AND (altitude IS NOT NULL)\n" +
                "    AND (simState IS NOT NULL)\n" +
                "    AND (simOperator IS NOT NULL)\n" +
                "    AND (networkRegistrationInfo IS NOT NULL);", DeviceStatusMessageConstants.PHONE_STATE_TABLE_NAME);

        return baseQuery(geoPackage, query);
    }

    public static ArrayList<PhoneStateModel> baseQuery(GeoPackage geoPackage, String query)
    {
        ArrayList<PhoneStateModel> results = new ArrayList<>();
        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery(query, null);

        if (cursor.moveToFirst())
        {
            do
            {
                PhoneStateModel model = new PhoneStateModel.PhoneStateModelBuilder()
                        .setId(cursor.getInt(cursor.getColumnIndex(DeviceStatusMessageConstants.ID_COLUMN)))
                        .setGeom(Arrays.toString(cursor.getBlob(cursor.getColumnIndex(DeviceStatusMessageConstants.GEOMETRY_COLUMN))))
                        .setTime(cursor.getInt(cursor.getColumnIndex(DeviceStatusMessageConstants.TIME_COLUMN)))
                        .setRecordNumber(cursor.getInt(cursor.getColumnIndex(DeviceStatusMessageConstants.RECORD_NUMBER_COLUMN)))
                        .setLatitude(cursor.getDouble(cursor.getColumnIndex(DeviceStatusMessageConstants.LATITUDE_COLUMN)))
                        .setLongitude(cursor.getDouble(cursor.getColumnIndex(DeviceStatusMessageConstants.LONGITUDE_COLUMN)))
                        .setAltitude(cursor.getFloat(cursor.getColumnIndex(DeviceStatusMessageConstants.ALTITUDE_COLUMN)))
                        .setMissionId(cursor.getString(cursor.getColumnIndex(DeviceStatusMessageConstants.MISSION_ID_COLUMN)))
                        .setSimState(cursor.getString(cursor.getColumnIndex(DeviceStatusMessageConstants.SIM_STATE_COLUMN)))
                        .setSimOperator(cursor.getString(cursor.getColumnIndex(DeviceStatusMessageConstants.SIM_OPERATOR_COLUMN)))
                        .setNetworkRegistration(cursor.getString(cursor.getColumnIndex(DeviceStatusMessageConstants.NETWORK_REGISTRATION_COLUMN)))
                        .build();
                results.add(model);
            } while (cursor.moveToNext());
        }

        return results;
    }
}
