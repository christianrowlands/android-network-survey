package com.craxiom.networksurvey.logging.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.craxiom.networksurvey.logging.db.model.GsmRecordEntity;

import java.util.List;

@Dao
public interface GsmRecordDao
{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecord(GsmRecordEntity record);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecords(List<GsmRecordEntity> records);

    @Query("SELECT * FROM gsm_survey_records")
    List<GsmRecordEntity> getAllRecords();
}
