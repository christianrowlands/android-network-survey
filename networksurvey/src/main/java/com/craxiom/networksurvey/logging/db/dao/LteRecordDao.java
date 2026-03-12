package com.craxiom.networksurvey.logging.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.craxiom.networksurvey.logging.db.model.LteRecordEntity;

import java.util.List;

@Dao
public interface LteRecordDao
{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecord(LteRecordEntity record);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecords(List<LteRecordEntity> records);

    @Query("SELECT * FROM lte_survey_records")
    List<LteRecordEntity> getAllRecords();
}
