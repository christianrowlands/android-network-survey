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
import com.craxiom.networksurvey.logging.db.dao.NsAnalyticsDao;
import com.craxiom.networksurvey.logging.db.dao.SurveyRecordDao;
import com.craxiom.networksurvey.logging.db.dao.TowerCacheDao;
import com.craxiom.networksurvey.logging.db.dao.UmtsRecordDao;
import com.craxiom.networksurvey.logging.db.dao.WatchlistDao;
import com.craxiom.networksurvey.logging.db.dao.WatchlistHitDao;
import com.craxiom.networksurvey.logging.db.dao.WifiRecordDao;
import com.craxiom.networksurvey.logging.db.model.CdmaRecordEntity;
import com.craxiom.networksurvey.logging.db.model.GsmRecordEntity;
import com.craxiom.networksurvey.logging.db.model.LteRecordEntity;
import com.craxiom.networksurvey.logging.db.model.NrRecordEntity;
import com.craxiom.networksurvey.logging.db.model.NsAnalyticsConnectionEntity;
import com.craxiom.networksurvey.logging.db.model.NsAnalyticsQueueEntity;
import com.craxiom.networksurvey.logging.db.model.TowerCacheEntity;
import com.craxiom.networksurvey.logging.db.model.UmtsRecordEntity;
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity;
import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity;
import com.craxiom.networksurvey.logging.db.model.WifiBeaconRecordEntity;

@Database(entities = {GsmRecordEntity.class, CdmaRecordEntity.class, UmtsRecordEntity.class,
        LteRecordEntity.class, NrRecordEntity.class, WifiBeaconRecordEntity.class, TowerCacheEntity.class,
        NsAnalyticsQueueEntity.class, NsAnalyticsConnectionEntity.class, WatchlistEntryEntity.class,
        WatchlistHitEntity.class}, version = 14, exportSchema = false)
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

    public abstract NsAnalyticsDao nsAnalyticsDao();

    public abstract WatchlistDao watchlistDao();

    public abstract WatchlistHitDao watchlistHitDao();

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

    /**
     * Migration from version 9 to 10: Add NS Analytics tables
     */
    private static final Migration MIGRATION_9_10 = new Migration(9, 10)
    {
        @Override
        public void migrate(SupportSQLiteDatabase database)
        {
            database.execSQL("CREATE TABLE ns_analytics_queue (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "recordType TEXT, " +
                    "protobufJson TEXT, " +
                    "timestamp INTEGER NOT NULL, " +
                    "batchId TEXT, " +
                    "uploaded INTEGER NOT NULL, " +
                    "retryCount INTEGER NOT NULL, " +
                    "lastUploadAttempt INTEGER NOT NULL, " +
                    "payloadSize INTEGER NOT NULL)");

            database.execSQL("CREATE INDEX index_ns_analytics_queue_uploaded ON ns_analytics_queue (uploaded)");
            database.execSQL("CREATE INDEX index_ns_analytics_queue_timestamp ON ns_analytics_queue (timestamp)");

            database.execSQL("CREATE TABLE ns_analytics_connection (" +
                    "workspaceId TEXT PRIMARY KEY NOT NULL, " +
                    "deviceToken TEXT, " +
                    "apiUrl TEXT, " +
                    "deviceId TEXT, " +
                    "registeredAt INTEGER NOT NULL, " +
                    "lastUploadAt INTEGER NOT NULL, " +
                    "totalRecordsUploaded INTEGER NOT NULL, " +
                    "isActive INTEGER NOT NULL, " +
                    "uploadFrequencyMinutes INTEGER NOT NULL)");
        }
    };

    /**
     * Migration from version 10 to 11: Fix NS Analytics tables schema
     * This migration fixes the schema for users who upgraded to v1.44 (database version 10)
     * which had DEFAULT values and missing indices that caused Room validation failures.
     * Also handles edge cases where tables might already have correct schema (from MIGRATION_9_10).
     */
    private static final Migration MIGRATION_10_11 = new Migration(10, 11)
    {
        @Override
        public void migrate(SupportSQLiteDatabase database)
        {
            // Check if ns_analytics_queue table exists before attempting migration
            android.database.Cursor queueCursor = database.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='ns_analytics_queue'");
            boolean queueTableExists = queueCursor.getCount() > 0;
            queueCursor.close();

            if (queueTableExists)
            {
                // Recreate ns_analytics_queue table with correct schema (no DEFAULT values)
                database.execSQL("CREATE TABLE ns_analytics_queue_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "recordType TEXT, " +
                        "protobufJson TEXT, " +
                        "timestamp INTEGER NOT NULL, " +
                        "batchId TEXT, " +
                        "uploaded INTEGER NOT NULL, " +
                        "retryCount INTEGER NOT NULL, " +
                        "lastUploadAttempt INTEGER NOT NULL, " +
                        "payloadSize INTEGER NOT NULL)");

                // Copy existing data (safe even if table is empty)
                database.execSQL("INSERT INTO ns_analytics_queue_new " +
                        "(id, recordType, protobufJson, timestamp, batchId, uploaded, retryCount, lastUploadAttempt, payloadSize) " +
                        "SELECT id, recordType, protobufJson, timestamp, batchId, uploaded, retryCount, lastUploadAttempt, payloadSize " +
                        "FROM ns_analytics_queue");

                database.execSQL("DROP TABLE ns_analytics_queue");

                database.execSQL("ALTER TABLE ns_analytics_queue_new RENAME TO ns_analytics_queue");
            }

            // Create indices (using IF NOT EXISTS for idempotency in case they already exist from MIGRATION_9_10)
            database.execSQL("CREATE INDEX IF NOT EXISTS index_ns_analytics_queue_uploaded ON ns_analytics_queue (uploaded)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_ns_analytics_queue_timestamp ON ns_analytics_queue (timestamp)");

            // Check if ns_analytics_connection table exists before attempting migration
            android.database.Cursor connectionCursor = database.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='ns_analytics_connection'");
            boolean connectionTableExists = connectionCursor.getCount() > 0;
            connectionCursor.close();

            if (connectionTableExists)
            {
                // Recreate ns_analytics_connection table with correct schema (no DEFAULT values)
                database.execSQL("CREATE TABLE ns_analytics_connection_new (" +
                        "workspaceId TEXT PRIMARY KEY NOT NULL, " +
                        "deviceToken TEXT, " +
                        "apiUrl TEXT, " +
                        "deviceId TEXT, " +
                        "registeredAt INTEGER NOT NULL, " +
                        "lastUploadAt INTEGER NOT NULL, " +
                        "totalRecordsUploaded INTEGER NOT NULL, " +
                        "isActive INTEGER NOT NULL, " +
                        "uploadFrequencyMinutes INTEGER NOT NULL)");

                // Copy existing data (safe even if table is empty)
                database.execSQL("INSERT INTO ns_analytics_connection_new " +
                        "(workspaceId, deviceToken, apiUrl, deviceId, registeredAt, lastUploadAt, totalRecordsUploaded, isActive, uploadFrequencyMinutes) " +
                        "SELECT workspaceId, deviceToken, apiUrl, deviceId, registeredAt, lastUploadAt, totalRecordsUploaded, isActive, uploadFrequencyMinutes " +
                        "FROM ns_analytics_connection");

                database.execSQL("DROP TABLE ns_analytics_connection");

                database.execSQL("ALTER TABLE ns_analytics_connection_new RENAME TO ns_analytics_connection");
            }
        }
    };

    /**
     * Migration from version 11 to 12: Change tower_cache mcc/mnc columns from INTEGER to TEXT.
     * This preserves MNC leading zeros (e.g., "01" instead of "1") for correct API lookups
     * and tower identification.
     */
    private static final Migration MIGRATION_11_12 = new Migration(11, 12)
    {
        @Override
        public void migrate(SupportSQLiteDatabase database)
        {
            database.execSQL("CREATE TABLE tower_cache_new ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "mcc TEXT, "
                    + "mnc TEXT, "
                    + "area INTEGER NOT NULL, "
                    + "cid INTEGER NOT NULL, "
                    + "timestamp INTEGER NOT NULL, "
                    + "radio TEXT"
                    + ")");

            database.execSQL("INSERT INTO tower_cache_new (id, mcc, mnc, area, cid, timestamp, radio) "
                    + "SELECT id, CAST(mcc AS TEXT), CAST(mnc AS TEXT), area, cid, timestamp, radio "
                    + "FROM tower_cache");

            database.execSQL("DROP TABLE tower_cache");

            database.execSQL("ALTER TABLE tower_cache_new RENAME TO tower_cache");

            database.execSQL("CREATE UNIQUE INDEX index_tower_cache_mcc_mnc_area_cid "
                    + "ON tower_cache (mcc, mnc, area, cid)");
        }
    };

    /**
     * Migration from version 12 to 13: Add the Watchlist tables.
     * <p>
     * Adds {@code watchlist_entry} (the networks the user has chosen to watch for) and
     * {@code watchlist_hit} (the history of "Seen" detections). The DDL must match the schema Room
     * generates from {@link WatchlistEntryEntity} and {@link WatchlistHitEntity}, including the
     * cascading foreign key and the auto-named composite index, or Room's schema validation fails on
     * upgrade. No new columns are added to existing tables, so existing survey data is untouched.
     */
    private static final Migration MIGRATION_12_13 = new Migration(12, 13)
    {
        @Override
        public void migrate(SupportSQLiteDatabase database)
        {
            database.execSQL("CREATE TABLE watchlist_entry ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "label TEXT, "
                    + "ssid TEXT, "
                    + "bssid TEXT, "
                    + "matchType TEXT, "
                    + "enabled INTEGER NOT NULL, "
                    + "createdAt INTEGER NOT NULL, "
                    + "cooldownSeconds INTEGER NOT NULL"
                    + ")");

            database.execSQL("CREATE TABLE watchlist_hit ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "entryId INTEGER NOT NULL, "
                    + "label TEXT, "
                    + "matchedField TEXT, "
                    + "ssid TEXT, "
                    + "bssid TEXT, "
                    + "rssi INTEGER NOT NULL, "
                    + "latitude REAL, "
                    + "longitude REAL, "
                    + "timestamp INTEGER NOT NULL, "
                    + "FOREIGN KEY(entryId) REFERENCES watchlist_entry(id) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE"
                    + ")");

            database.execSQL("CREATE INDEX index_watchlist_hit_entryId_timestamp "
                    + "ON watchlist_hit (entryId, timestamp)");
        }
    };

    /**
     * Adds the {@code uuid} and {@code updatedAt} columns to {@code watchlist_entry} so that watchlist
     * changes can be published over MQTT with a stable, correlatable identity. Existing rows are
     * backfilled with a random opaque {@code uuid} and an {@code updatedAt} equal to the row's
     * {@code createdAt}.
     * <p>
     * The {@code NOT NULL DEFAULT 0} DDL must match the {@code @ColumnInfo(defaultValue = "0")} default
     * on {@link WatchlistEntryEntity#updatedAt}, or Room's schema validation fails on upgrade.
     */
    private static final Migration MIGRATION_13_14 = new Migration(13, 14)
    {
        @Override
        public void migrate(SupportSQLiteDatabase database)
        {
            database.execSQL("ALTER TABLE watchlist_entry ADD COLUMN uuid TEXT");
            database.execSQL("ALTER TABLE watchlist_entry ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0");

            // Backfill existing rows. randomblob() is evaluated per-row, so each entry gets a distinct id.
            database.execSQL("UPDATE watchlist_entry SET uuid = lower(hex(randomblob(16))) WHERE uuid IS NULL");
            database.execSQL("UPDATE watchlist_entry SET updatedAt = createdAt WHERE updatedAt = 0");
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
                            .addMigrations(MIGRATION_7_9, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
