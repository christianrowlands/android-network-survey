package com.craxiom.networksurvey.logging.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.craxiom.networksurvey.logging.db.model.UmtsRecordEntity;

import java.util.List;

@Dao
public interface UmtsRecordDao
{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecord(UmtsRecordEntity record);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecords(List<UmtsRecordEntity> records);

    @Query("SELECT * FROM umts_survey_records")
    List<UmtsRecordEntity> getAllRecords();
}
