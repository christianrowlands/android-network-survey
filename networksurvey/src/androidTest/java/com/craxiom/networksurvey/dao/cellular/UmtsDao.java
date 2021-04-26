package com.craxiom.networksurvey.dao.cellular;

import com.craxiom.networksurvey.constants.UmtsMessageConstants;
import com.craxiom.networksurvey.dao.CommonDao;
import mil.nga.geopackage.GeoPackage;

public class UmtsDao
{
    public static boolean allNonNullColumnsArePopulated(GeoPackage geoPackage)
    {
        String query = String.format("SELECT * FROM [%s] WHERE id IS NULL OR RecordNumber IS NULL OR GroupNumber IS NULL", UmtsMessageConstants.UMTS_RECORDS_TABLE_NAME);
        return CommonDao.allNonNullColumnsArePopulated(geoPackage, query);
    }
}
