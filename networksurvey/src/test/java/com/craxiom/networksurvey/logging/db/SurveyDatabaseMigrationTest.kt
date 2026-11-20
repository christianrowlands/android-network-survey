package com.craxiom.networksurvey.logging.db

import android.content.Context
import androidx.room.Room
import androidx.room.util.TableInfo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Guards the hand-written migrations against the schema Room generates. A mismatch would trigger
 * the destructive fallback on every upgraded device and wipe the watchlist and un-uploaded survey
 * data, so each migrated table is compared column by column with a freshly created one.
 *
 * Room validates every table when a database opens and {@code exportSchema} is off, so these tests
 * run each migration on a raw copy of the tables it touches rather than through a full database.
 */
@RunWith(RobolectricTestRunner::class)
class SurveyDatabaseMigrationTest {

    private val v15Ddl = listOf(
        "CREATE TABLE surveyed_point (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "latitude REAL NOT NULL, longitude REAL NOT NULL, time INTEGER NOT NULL, " +
                "source INTEGER NOT NULL, observedMask INTEGER NOT NULL, uploadedMask INTEGER NOT NULL)",
        "CREATE INDEX index_surveyed_point_latitude_longitude ON surveyed_point (latitude, longitude)",
        "CREATE INDEX index_surveyed_point_source_time ON surveyed_point (source, time)",
    )

    private val v16TowerCacheDdl = listOf(
        "CREATE TABLE tower_cache (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, mcc TEXT, mnc TEXT, " +
                "area INTEGER NOT NULL, cid INTEGER NOT NULL, timestamp INTEGER NOT NULL, radio TEXT)",
        "CREATE UNIQUE INDEX index_tower_cache_mcc_mnc_area_cid ON tower_cache (mcc, mnc, area, cid)",
    )

    @Test
    fun migration15To16MatchesTheGeneratedSchemaAndKeepsRows() {
        val context: Context = RuntimeEnvironment.getApplication()
        val helper = rawDatabase(context, 15, v15Ddl)
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO surveyed_point (latitude, longitude, time, source, observedMask, uploadedMask) " +
                    "VALUES (38.0, -77.0, 123, 1, 1, 0)"
        )

        SurveyDatabase.MIGRATION_15_16.migrate(db)

        val room = Room.inMemoryDatabaseBuilder(context, SurveyDatabase::class.java)
            .allowMainThreadQueries().build()
        val expected = TableInfo.read(room.openHelper.writableDatabase, "surveyed_point")
        val migrated = TableInfo.read(db, "surveyed_point")
        assertEquals(expected, migrated)

        db.query("SELECT protocol, plmn, cid, signalBucket, missionId, time FROM surveyed_point")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertTrue(cursor.isNull(1))
                assertEquals(0L, cursor.getLong(2))
                assertEquals(0, cursor.getInt(3))
                assertTrue(cursor.isNull(4))
                assertEquals(123L, cursor.getLong(5))
            }

        room.close()
        helper.close()
    }

    @Test
    fun migration16To17AddsTheNewTowerFindTableRoomExpects_andKeepsExistingTables() {
        val context: Context = RuntimeEnvironment.getApplication()
        val helper = rawDatabase(context, 16, v16TowerCacheDdl)
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO tower_cache (mcc, mnc, area, cid, timestamp, radio) VALUES ('310', '260', 1, 2, 3, 'LTE')"
        )

        SurveyDatabase.MIGRATION_16_17.migrate(db)

        val room = Room.inMemoryDatabaseBuilder(context, SurveyDatabase::class.java)
            .allowMainThreadQueries().build()
        val roomDb = room.openHelper.writableDatabase
        assertEquals(TableInfo.read(roomDb, "new_tower_find"), TableInfo.read(db, "new_tower_find"))
        assertEquals(TableInfo.read(roomDb, "tower_cache"), TableInfo.read(db, "tower_cache"))

        db.query("SELECT COUNT(*) FROM tower_cache").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.execSQL(
            "INSERT INTO new_tower_find (mcc, mnc, area, cellId, radio, firstSeenAt, lastSeenAt, " +
                    "sessionStartedAt, inDatabase, lastCheckedAt) VALUES ('310', '260', 1, 2, 'LTE', 4, 4, 3, 0, 4)"
        )

        room.close()
        helper.close()
    }

    /** An unmanaged database at [version] holding only the tables [ddl] creates. */
    private fun rawDatabase(context: Context, version: Int, ddl: List<String>): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = ddl.forEach(db::execSQL)
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
}
