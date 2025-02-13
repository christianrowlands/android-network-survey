package com.craxiom.networksurvey.logging.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.craxiom.networksurvey.logging.db.dao.CdmaRecordDao;
import com.craxiom.networksurvey.logging.db.dao.GsmRecordDao;
import com.craxiom.networksurvey.logging.db.dao.LteRecordDao;
import com.craxiom.networksurvey.logging.db.dao.NrRecordDao;
import com.craxiom.networksurvey.logging.db.dao.SurveyRecordDao;
import com.craxiom.networksurvey.logging.db.dao.UmtsRecordDao;
import com.craxiom.networksurvey.logging.db.dao.WifiRecordDao;
import com.craxiom.networksurvey.logging.db.model.CdmaRecordEntity;
import com.craxiom.networksurvey.logging.db.model.GsmRecordEntity;
import com.craxiom.networksurvey.logging.db.model.LteRecordEntity;
import com.craxiom.networksurvey.logging.db.model.NrRecordEntity;
import com.craxiom.networksurvey.logging.db.model.UmtsRecordEntity;
import com.craxiom.networksurvey.logging.db.model.WifiBeaconRecordEntity;

@Database(entities = {GsmRecordEntity.class, CdmaRecordEntity.class, UmtsRecordEntity.class,
        LteRecordEntity.class, NrRecordEntity.class, WifiBeaconRecordEntity.class}, version = 6)
public abstract class SurveyDatabase extends RoomDatabase
{
    public abstract GsmRecordDao gsmRecordDao();

    public abstract CdmaRecordDao cdmaRecordDao();

    public abstract UmtsRecordDao umtsRecordDao();

    public abstract LteRecordDao lteRecordDao();

    public abstract NrRecordDao nrRecordDao();

    public abstract WifiRecordDao wifiRecordDao();

    public abstract SurveyRecordDao surveyRecordDao();

    private static volatile SurveyDatabase INSTANCE;

    public static SurveyDatabase getInstance(Context context)
    {
        if (INSTANCE == null)
        {
            synchronized (SurveyDatabase.class)
            {
                if (INSTANCE == null)
                {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    SurveyDatabase.class, "survey_db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
