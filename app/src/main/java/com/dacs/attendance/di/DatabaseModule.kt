package com.dacs.attendance.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dacs.attendance.data.local.AttendanceDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * No fallbackToDestructiveMigration, deliberately.
     *
     * This database holds submissions that have not reached the server.
     * Wiping it on a schema change would silently delete a worker's
     * recorded day. Schema changes here need real migrations, which is
     * why the schema is exported to app/schemas and committed.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AttendanceDatabase =
        Room.databaseBuilder(context, AttendanceDatabase::class.java, "attendance.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    /**
     * v1 -> v2: every local row learns WHOSE it is.
     *
     * Before this, the mirror and the queue were shared by whoever
     * happened to be signed in on the phone. Rows that already exist
     * cannot be attributed to anyone -- so they get an empty owner, and
     * the queue refuses to send a row it cannot attribute. Losing an
     * upload is bad; recording one worker's attendance under another
     * worker's name is worse and cannot be spotted afterwards.
     *
     * The tables are REBUILT rather than ALTERed because both mirrors
     * gain a composite primary key, which SQLite cannot add in place.
     * pending_submission only gains a column, so it is altered.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pending_submission ADD COLUMN workerId TEXT NOT NULL DEFAULT ''")

            db.execSQL(
                """CREATE TABLE cached_record_new (
                       workerId TEXT NOT NULL, workDate TEXT NOT NULL, id TEXT,
                       status TEXT NOT NULL, timeInAt INTEGER, timeOutAt INTEGER,
                       timeInProjectName TEXT, timeOutProjectName TEXT,
                       totalMinutes INTEGER, pending INTEGER NOT NULL,
                       PRIMARY KEY(workerId, workDate))"""
            )
            db.execSQL(
                """INSERT INTO cached_record_new
                       (workerId, workDate, id, status, timeInAt, timeOutAt,
                        timeInProjectName, timeOutProjectName, totalMinutes, pending)
                     SELECT '', workDate, id, status, timeInAt, timeOutAt,
                            timeInProjectName, timeOutProjectName, totalMinutes, pending
                       FROM cached_record"""
            )
            db.execSQL("DROP TABLE cached_record")
            db.execSQL("ALTER TABLE cached_record_new RENAME TO cached_record")

            db.execSQL(
                """CREATE TABLE cached_project_new (
                       workerId TEXT NOT NULL, id INTEGER NOT NULL, name TEXT NOT NULL,
                       PRIMARY KEY(workerId, id))"""
            )
            db.execSQL(
                "INSERT INTO cached_project_new (workerId, id, name) " +
                    "SELECT '', id, name FROM cached_project"
            )
            db.execSQL("DROP TABLE cached_project")
            db.execSQL("ALTER TABLE cached_project_new RENAME TO cached_project")
        }
    }
}
