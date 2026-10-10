package com.privacyguard.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ForegroundSession::class,
        AccessEvent::class,
        PermissionBaselineRow::class,
        RuntimeScore::class,
        LocalBlockState::class,
        AuditCursor::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foregroundSessionDao(): ForegroundSessionDao
    abstract fun accessEventDao(): AccessEventDao
    abstract fun permissionBaselineDao(): PermissionBaselineDao
    abstract fun runtimeScoreDao(): RuntimeScoreDao
    abstract fun localBlockStateDao(): LocalBlockStateDao
    abstract fun auditCursorDao(): AuditCursorDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "privacyguard.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
