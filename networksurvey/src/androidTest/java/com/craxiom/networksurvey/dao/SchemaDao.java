package com.craxiom.networksurvey.dao;

import android.database.Cursor;
import com.craxiom.networksurvey.models.tableschemas.MessageTableSchema;
import mil.nga.geopackage.GeoPackage;

import java.util.ArrayList;

public class SchemaDao
{

    public static ArrayList<MessageTableSchema> getTableSchema(GeoPackage geoPackage, String table)
    {
        ArrayList<MessageTableSchema> results = new ArrayList<>();
        String query = String.format("PRAGMA table_info([%s]);", table);
        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery(query, null);

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
