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
 * Guards the hand-written surveyed_point migration against the schema Room generates. A mismatch
 * would trigger the destructive fallback on every upgraded device and wipe the watchlist, so the
 * migrated table is compared column by column with a freshly created one.
 *
 * Room validates every table when a database opens and {@code exportSchema} is off, so this test
 * runs the migration on a raw v15 copy of the one table rather than through a full database.
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

    @Test
    fun migration15To16MatchesTheGeneratedSchemaAndKeepsRows() {
        val context: Context = RuntimeEnvironment.getApplication()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                    override fun onCreate(db: SupportSQLiteDatabase) = v15Ddl.forEach(db::execSQL)
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
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
}
