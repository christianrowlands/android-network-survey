package com.craxiom.networksurvey.data.oui

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Standalone Room database for the OUI cache. Deliberately separate from `SurveyDatabase` so
 * that schema changes here cannot trigger destructive migration on the user's survey data.
 *
 * Destructive fallback is safe here because the cache can always be rebuilt from the server.
 */
@Database(
    entities = [OuiCacheEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OuiCacheDatabase : RoomDatabase() {

    abstract fun ouiCacheDao(): OuiCacheDao

    companion object {
        const val DB_NAME = "oui_cache.db"

        @Volatile
        private var INSTANCE: OuiCacheDatabase? = null

        fun getInstance(context: Context): OuiCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OuiCacheDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
