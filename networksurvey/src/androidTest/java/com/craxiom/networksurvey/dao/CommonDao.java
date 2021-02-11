package com.craxiom.networksurvey.dao;

import android.database.Cursor;
import mil.nga.geopackage.GeoPackage;

public class CommonDao
{
    public static boolean allNonNullColumnsArePopulated(GeoPackage geoPackage, String query)
    {
        Boolean hit = true;
        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery(query, null);

        if (cursor.moveToFirst())
        {
            hit = false;
        }
        return hit;
    }
}
