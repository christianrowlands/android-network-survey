package com.craxiom.networksurvey.dao.cellular;

import com.craxiom.networksurvey.constants.CdmaMessageConstants;
import com.craxiom.networksurvey.dao.CommonDao;
import mil.nga.geopackage.GeoPackage;

public class CdmaDao
{
    public static boolean allNonNullColumnsArePopulated(GeoPackage geoPackage)
    {
        String query = String.format("SELECT * FROM [%s] WHERE id IS NULL OR RecordNumber IS NULL OR GroupNumber IS NULL", CdmaMessageConstants.CDMA_RECORDS_TABLE_NAME);
        return CommonDao.allNonNullColumnsArePopulated(geoPackage, query);
    }
}
