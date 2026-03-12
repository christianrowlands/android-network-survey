package com.craxiom.networksurvey.logging.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.craxiom.networksurvey.logging.db.model.WifiBeaconRecordEntity;

import java.util.List;

@Dao
public interface WifiRecordDao
{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecords(List<WifiBeaconRecordEntity> record);

    @Query("SELECT * FROM wifi_survey_records")
    List<WifiBeaconRecordEntity> getAllRecords();
}
