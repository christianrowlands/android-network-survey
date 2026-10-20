package com.craxiom.networksurvey.logging.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;

import com.craxiom.networksurvey.logging.db.model.CdmaRecordEntity;

import java.util.List;

@Dao
public interface CdmaRecordDao
{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecords(List<CdmaRecordEntity> records);
}
