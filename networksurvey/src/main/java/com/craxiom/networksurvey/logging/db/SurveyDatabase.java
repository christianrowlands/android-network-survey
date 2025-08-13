package com.craxiom.networksurvey.logging.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.craxiom.networksurvey.logging.db.dao.CdmaRecordDao;
import com.craxiom.networksurvey.logging.db.dao.GsmRecordDao;
import com.craxiom.networksurvey.logging.db.dao.LteRecordDao;
import com.craxiom.networksurvey.logging.db.dao.NrRecordDao;
import com.craxiom.networksurvey.logging.db.dao.SurveyRecordDao;
import com.craxiom.networksurvey.logging.db.dao.TowerCacheDao;
import com.craxiom.networksurvey.logging.db.dao.UmtsRecordDao;
import com.craxiom.networksurvey.logging.db.dao.WifiRecordDao;
import com.craxiom.networksurvey.logging.db.model.CdmaRecordEntity;
import com.craxiom.networksurvey.logging.db.model.GsmRecordEntity;
import com.craxiom.networksurvey.logging.db.model.LteRecordEntity;
import com.craxiom.networksurvey.logging.db.model.NrRecordEntity;
import com.craxiom.networksurvey.logging.db.model.TowerCacheEntity;
import com.craxiom.networksurvey.logging.db.model.UmtsRecordEntity;
import com.craxiom.networksurvey.logging.db.model.WifiBeaconRecordEntity;

@Database(entities = {GsmRecordEntity.class, CdmaRecordEntity.class, UmtsRecordEntity.class,
        LteRecordEntity.class, NrRecordEntity.class, WifiBeaconRecordEntity.class, TowerCacheEntity.class}, version = 9)
public abstract class SurveyDatabase extends RoomDatabase
{
    public abstract GsmRecordDao gsmRecordDao();

    public abstract CdmaRecordDao cdmaRecordDao();

    public abstract UmtsRecordDao umtsRecordDao();

    public abstract LteRecordDao lteRecordDao();

    public abstract NrRecordDao nrRecordDao();

    public abstract WifiRecordDao wifiRecordDao();

    public abstract SurveyRecordDao surveyRecordDao();

    public abstract TowerCacheDao towerCacheDao();

    private static volatile SurveyDatabase INSTANCE;

    /**
     * Migration from version 7 to 9: Simplify tower_cache table structure
     * Remove isKnown and alerted columns as they are no longer needed
     */
    private static final Migration MIGRATION_7_9 = new Migration(7, 9)
    {
        @Override
        public void migrate(SupportSQLiteDatabase database)
        {
            // Create new table with simplified schema
            database.execSQL("CREATE TABLE tower_cache_new ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "mcc INTEGER NOT NULL, "
                    + "mnc INTEGER NOT NULL, "
                    + "area INTEGER NOT NULL, "
                    + "cid INTEGER NOT NULL, "
                    + "timestamp INTEGER NOT NULL, "
                    + "radio TEXT"
                    + ")");
            
            // Copy data from old table to new table (excluding isKnown and alerted columns)
            database.execSQL("INSERT INTO tower_cache_new (id, mcc, mnc, area, cid, timestamp, radio) "
                    + "SELECT id, mcc, mnc, area, cid, timestamp, radio FROM tower_cache");
            
            // Drop old table
            database.execSQL("DROP TABLE tower_cache");
            
            // Rename new table to original name
            database.execSQL("ALTER TABLE tower_cache_new RENAME TO tower_cache");
            
            // Re-create the unique index
            database.execSQL("CREATE UNIQUE INDEX index_tower_cache_mcc_mnc_area_cid "
                    + "ON tower_cache (mcc, mnc, area, cid)");
        }
    };

    /**
     * Migration from version 8 to 9: Simplify tower_cache table structure
     * Remove alerted column that was briefly added in version 8
     */
    private static final Migration MIGRATION_8_9 = new Migration(8, 9)
    {
        @Override
        public void migrate(SupportSQLiteDatabase database)
        {
            // Create new table with simplified schema
            database.execSQL("CREATE TABLE tower_cache_new ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "mcc INTEGER NOT NULL, "
                    + "mnc INTEGER NOT NULL, "
                    + "area INTEGER NOT NULL, "
                    + "cid INTEGER NOT NULL, "
                    + "timestamp INTEGER NOT NULL, "
                    + "radio TEXT"
                    + ")");
            
            // Copy data from old table to new table (excluding alerted column)
            database.execSQL("INSERT INTO tower_cache_new (id, mcc, mnc, area, cid, timestamp, radio) "
                    + "SELECT id, mcc, mnc, area, cid, timestamp, radio FROM tower_cache");
            
            // Drop old table
            database.execSQL("DROP TABLE tower_cache");
            
            // Rename new table to original name
            database.execSQL("ALTER TABLE tower_cache_new RENAME TO tower_cache");
            
            // Re-create the unique index
            database.execSQL("CREATE UNIQUE INDEX index_tower_cache_mcc_mnc_area_cid "
                    + "ON tower_cache (mcc, mnc, area, cid)");
        }
    };

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
                            .addMigrations(MIGRATION_7_9, MIGRATION_8_9)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
