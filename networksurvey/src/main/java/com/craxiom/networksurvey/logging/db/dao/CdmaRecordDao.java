package com.craxiom.networksurvey.logging.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.craxiom.networksurvey.logging.db.model.CdmaRecordEntity;

import java.util.List;

@Dao
public interface CdmaRecordDao
{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecord(CdmaRecordEntity record);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecords(List<CdmaRecordEntity> records);

    @Query("SELECT * FROM cdma_survey_records")
    List<CdmaRecordEntity> getAllRecords();
}
