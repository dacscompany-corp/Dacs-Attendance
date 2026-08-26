package com.dacs.attendance.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSubmissionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(submission: PendingSubmissionEntity)

    @Query("SELECT * FROM pending_submission WHERE failedPermanently = 0 ORDER BY createdAt ASC")
    suspend fun sendable(): List<PendingSubmissionEntity>

    @Query("SELECT * FROM pending_submission WHERE eventId = :eventId")
    suspend fun byId(eventId: String): PendingSubmissionEntity?

    /** Drives the "will sync when online" marker. */
    @Query("SELECT * FROM pending_submission ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingSubmissionEntity>>

    @Query("UPDATE pending_submission SET attempts = attempts + 1, lastError = :error WHERE eventId = :eventId")
    suspend fun recordAttempt(eventId: String, error: String?)

    @Query("UPDATE pending_submission SET failedPermanently = 1, lastError = :error WHERE eventId = :eventId")
    suspend fun markFailed(eventId: String, error: String?)

    @Delete
    suspend fun delete(submission: PendingSubmissionEntity)

    @Query("DELETE FROM pending_submission WHERE eventId = :eventId")
    suspend fun deleteById(eventId: String)
}

@Dao
interface CachedRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CachedRecordEntity)

    @Query("SELECT * FROM cached_record WHERE workDate = :workDate")
    suspend fun forDate(workDate: String): CachedRecordEntity?

    @Query("SELECT * FROM cached_record WHERE workDate = :workDate")
    fun observe(workDate: String): Flow<CachedRecordEntity?>

    @Query("DELETE FROM cached_record WHERE workDate = :workDate")
    suspend fun clear(workDate: String)

    // workDate is an ISO yyyy-MM-dd string, so lexical BETWEEN is also
    // chronological. That is the reason it is stored as text rather than
    // an epoch day.
    @Query("SELECT * FROM cached_record WHERE workDate BETWEEN :from AND :to ORDER BY workDate DESC")
    suspend fun between(from: String, to: String): List<CachedRecordEntity>
}

@Dao
interface CachedProjectDao {

    @Query("SELECT * FROM cached_project ORDER BY name ASC")
    suspend fun all(): List<CachedProjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(projects: List<CachedProjectEntity>)

    @Query("DELETE FROM cached_project")
    suspend fun clear()

    /**
     * Replaces the cached list in one transaction. Done as clear+insert
     * rather than upsert so a project DEACTIVATED on the server actually
     * disappears from the picker instead of lingering forever.
     */
    @androidx.room.Transaction
    suspend fun replaceAll(projects: List<CachedProjectEntity>) {
        clear()
        upsertAll(projects)
    }
}

@Database(
    entities = [
        PendingSubmissionEntity::class,
        CachedRecordEntity::class,
        CachedProjectEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AttendanceDatabase : RoomDatabase() {
    abstract fun pendingSubmissions(): PendingSubmissionDao
    abstract fun cachedRecords(): CachedRecordDao
    abstract fun cachedProjects(): CachedProjectDao
}
