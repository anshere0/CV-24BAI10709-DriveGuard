package com.driveguard.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LocalDriveSessionEntity::class,
        LocalClipRecordEntity::class,
        LocalRiskEventEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class DriveGuardLocalDatabase : RoomDatabase() {
    abstract fun localCaptureDao(): LocalCaptureDao

    companion object {
        private const val DATABASE_NAME = "driveguard_android.db"

        @Volatile
        private var instance: DriveGuardLocalDatabase? = null

        fun getInstance(context: Context): DriveGuardLocalDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DriveGuardLocalDatabase::class.java,
                    DATABASE_NAME,
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
