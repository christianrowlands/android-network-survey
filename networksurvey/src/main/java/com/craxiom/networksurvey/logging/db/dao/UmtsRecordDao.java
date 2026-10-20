package com.craxiom.networksurvey.logging.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;

import com.craxiom.networksurvey.logging.db.model.UmtsRecordEntity;

import java.util.List;

@Dao
public interface UmtsRecordDao
{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecords(List<UmtsRecordEntity> records);
}
