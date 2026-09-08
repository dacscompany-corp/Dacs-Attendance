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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

    /**
     * v2 -> v3: a project is a (system, id) PAIR, and the id is a uuid.
     *
     * Migration 0059 retired `attendance_projects` and pointed attendance
     * at folders ('pc') and construction_projects ('pm'). Their ids are
     * uuids in two separate spaces, so the integer id this app used to
     * store identifies nothing any more.
     *
     * ── The queue is the careful part.
     *    A row queued before this points at an integer id from a table the
     *    server has DROPPED. There is no mapping to recover -- the project
     *    it named is gone -- so it can never be submitted. It is flagged
     *    failed rather than deleted: the app's rule throughout is that a
     *    worker who was told their day was recorded must be told when it
     *    turns out it was not. Deleting the row would take the evidence of
     *    the problem with it.
     *
     * ── The project cache is just dropped.
     *    It is a cache. It refills from attendance_projects_for_worker()
     *    on the next connection, and keeping stale rows would show the
     *    worker three projects that no longer exist -- which is exactly
     *    the bug this migration is fixing.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ── The queue: projectId INTEGER -> TEXT, plus the system.
            // Rebuilt rather than ALTERed because the column changes type.
            db.execSQL(
                """CREATE TABLE pending_submission_new (
                       eventId TEXT NOT NULL PRIMARY KEY,
                       workerId TEXT NOT NULL DEFAULT '',
                       direction TEXT NOT NULL,
                       projectSystem TEXT NOT NULL DEFAULT '',
                       projectId TEXT NOT NULL,
                       projectName TEXT NOT NULL,
                       capturedAt INTEGER NOT NULL,
                       photoLocalPath TEXT NOT NULL,
                       description TEXT,
                       latitude REAL,
                       longitude REAL,
                       accuracyMetres REAL,
                       wasOffline INTEGER NOT NULL,
                       attempts INTEGER NOT NULL,
                       lastError TEXT,
                       failedPermanently INTEGER NOT NULL,
                       createdAt INTEGER NOT NULL)"""
            )
            db.execSQL(
                """INSERT INTO pending_submission_new
                       (eventId, workerId, direction, projectSystem, projectId,
                        projectName, capturedAt, photoLocalPath, description,
                        latitude, longitude, accuracyMetres, wasOffline,
                        attempts, lastError, failedPermanently, createdAt)
                     SELECT eventId, workerId, direction, '', CAST(projectId AS TEXT),
                            projectName, capturedAt, photoLocalPath, description,
                            latitude, longitude, accuracyMetres, wasOffline,
                            attempts, 'PROJECT_RETIRED', 1, createdAt
                       FROM pending_submission"""
            )
            db.execSQL("DROP TABLE pending_submission")
            db.execSQL("ALTER TABLE pending_submission_new RENAME TO pending_submission")

            // ── The cache: rebuilt empty on the new key.
            db.execSQL("DROP TABLE cached_project")
            db.execSQL(
                """CREATE TABLE cached_project (
                       workerId TEXT NOT NULL, system TEXT NOT NULL,
                       id TEXT NOT NULL, name TEXT NOT NULL,
                       PRIMARY KEY(workerId, system, id))"""
            )
        }
    }

    /**
     * v3 -> v4: the device learns where it is.
     *
     * Both tables only GAIN nullable columns, so these are plain ALTERs
     * -- no rebuild, and nothing in the queue is touched. A row queued
     * before this migration carries no location at all, which is exactly
     * what an older build recorded and remains truthful: it is sent with
     * null coordinates and the server files it as
     * `location_unavailable`, flagged rather than refused.
     *
     * That is the whole reason uncertainty is never a refusal. If a
     * missing fix were refusable, this migration would silently destroy
     * every day already sitting in the queue at upgrade time.
     *
     * The cached projects gain their fence so the pre-check works with no
     * signal. They are left null here rather than backfilled: the cache
     * refills from the server on the next connection, and inventing a
     * fence locally would pre-check against a circle nobody drew.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE pending_submission ADD COLUMN isMock INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE pending_submission " +
                    "ADD COLUMN permissionDenied INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL("ALTER TABLE pending_submission ADD COLUMN locationStatus TEXT")

            db.execSQL("ALTER TABLE cached_project ADD COLUMN geofenceLat REAL")
            db.execSQL("ALTER TABLE cached_project ADD COLUMN geofenceLng REAL")
            db.execSQL("ALTER TABLE cached_project ADD COLUMN geofenceRadiusM REAL")
            db.execSQL(
                "ALTER TABLE cached_project ADD COLUMN geofenceEnabled INTEGER NOT NULL DEFAULT 1"
            )
        }
    }
}
