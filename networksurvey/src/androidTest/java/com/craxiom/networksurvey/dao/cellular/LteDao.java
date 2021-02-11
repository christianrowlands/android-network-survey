package com.craxiom.networksurvey.dao.cellular;

import android.database.Cursor;
import com.craxiom.networksurvey.constants.LteMessageConstants;
import com.craxiom.networksurvey.dao.CommonDao;
import com.craxiom.networksurvey.models.message.cellular.LteModel;
import mil.nga.geopackage.GeoPackage;

import java.util.ArrayList;

public class LteDao
{
    public static boolean allNonNullColumnsArePopulated(GeoPackage geoPackage)
    {
        String query = String.format("SELECT * FROM [%s] WHERE id IS NULL OR RecordNumber IS NULL OR GroupNumber IS NULL", LteMessageConstants.LTE_RECORDS_TABLE_NAME);
        return CommonDao.allNonNullColumnsArePopulated(geoPackage, query);
    }

    public static ArrayList<LteModel> baseQuery(GeoPackage geoPackage, String query)
    {
        ArrayList<LteModel> results = new ArrayList<>();

        Cursor cursor = geoPackage
                .getConnection()
                .rawQuery(query, null);

        if (cursor.moveToFirst())
        {
            do
            {
                LteModel model = new LteModel.LteModelBuilder()
                        .setId(cursor.getInt(cursor.getColumnIndex(LteMessageConstants.ID_COLUMN)))
                        .setGeom(String.valueOf(cursor.getBlob(cursor.getColumnIndex(LteMessageConstants.GEOMETRY_COLUMN))))
                        .setTime(cursor.getLong(cursor.getColumnIndex(LteMessageConstants.TIME_COLUMN)))
                        .setRecordNumber(cursor.getInt(cursor.getColumnIndex(LteMessageConstants.RECORD_NUMBER_COLUMN)))
                        .setGroupNumber(cursor.getInt(cursor.getColumnIndex(LteMessageConstants.GROUP_NUMBER_COLUMN)))
                        .setServingCell(cursor.getInt(cursor.getColumnIndex(LteMessageConstants.SERVING_CELL_COLUMN)))
                        .setProvider(cursor.getString(cursor.getColumnIndex(LteMessageConstants.PROVIDER_COLUMN)))
                        .setMcc(cursor.getInt(cursor.getColumnIndex(LteMessageConstants.MCC_COLUMN)))
                        .setMnc(cursor.getInt(cursor.getColumnIndex(LteMessageConstants.MNC_COLUMN)))
                        .setTac(cursor.getInt(cursor.getColumnIndex(LteMessageConstants.TAC_COLUMN)))
                        .setEci(cursor.getInt(cursor.getColumnIndex(LteMessageConstants.CI_COLUMN)))
                        .setDlEarfcn(cursor.getInt(cursor.getColumnIndex(LteMessageConstants.EARFCN_COLUMN)))
                        .setPhysCellId(cursor.getInt(cursor.getColumnIndex(LteMessageConstants.PCI_COLUMN)))
                        .setRsrp(cursor.getFloat(cursor.getColumnIndex(LteMessageConstants.RSRP_COLUMN)))
                        .setRsrq(cursor.getFloat(cursor.getColumnIndex(LteMessageConstants.RSRQ_COLUMN)))
                        .setTa(cursor.getInt(cursor.getColumnIndex(LteMessageConstants.TA_COLUMN)))
                        .setDlBandwidth(cursor.getString(cursor.getColumnIndex(LteMessageConstants.BANDWIDTH_COLUMN)))
                        .build();
                results.add(model);
            } while (cursor.moveToNext());
        }
        return results;
    }

    public static ArrayList<LteModel> getAllRecords(GeoPackage geoPackage)
    {
        String query = String.format("SELECT * FROM [%s]", LteMessageConstants.LTE_RECORDS_TABLE_NAME);
        return baseQuery(geoPackage, query);
    }
}
