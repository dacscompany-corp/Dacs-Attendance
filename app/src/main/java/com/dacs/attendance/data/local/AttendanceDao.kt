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

    // Scoped to ONE worker. An unscoped read is how another worker's
    // queued day would end up uploaded under this session.
    @Query(
        "SELECT * FROM pending_submission " +
            "WHERE failedPermanently = 0 AND workerId = :workerId ORDER BY createdAt ASC"
    )
    suspend fun sendable(workerId: String): List<PendingSubmissionEntity>

    @Query("SELECT * FROM pending_submission WHERE eventId = :eventId")
    suspend fun byId(eventId: String): PendingSubmissionEntity?

    /** Drives the "will sync when online" marker. */
    @Query("SELECT * FROM pending_submission ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingSubmissionEntity>>

    /**
     * Drives the widget's "Not sent yet". Scoped to ONE worker, for the
     * same reason [sendable] is. Permanently failed rows are left out: they
     * will never be sent, and "not sent YET" would promise that they will.
     */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM pending_submission " +
            "WHERE workerId = :workerId AND failedPermanently = 0)"
    )
    suspend fun hasPendingFor(workerId: String): Boolean

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

    @Query("SELECT * FROM cached_record WHERE workerId = :workerId AND workDate = :workDate")
    suspend fun forDate(workerId: String, workDate: String): CachedRecordEntity?

    @Query("SELECT * FROM cached_record WHERE workerId = :workerId AND workDate = :workDate")
    fun observe(workerId: String, workDate: String): Flow<CachedRecordEntity?>

    @Query("DELETE FROM cached_record WHERE workerId = :workerId AND workDate = :workDate")
    suspend fun clear(workerId: String, workDate: String)

    // workDate is an ISO yyyy-MM-dd string, so lexical BETWEEN is also
    // chronological. That is the reason it is stored as text rather than
    // an epoch day.
    @Query(
        "SELECT * FROM cached_record WHERE workerId = :workerId " +
            "AND workDate BETWEEN :from AND :to ORDER BY workDate DESC"
    )
    suspend fun between(workerId: String, from: String, to: String): List<CachedRecordEntity>
}

@Dao
interface CachedProjectDao {

    @Query("SELECT * FROM cached_project WHERE workerId = :workerId ORDER BY name ASC")
    suspend fun all(workerId: String): List<CachedProjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(projects: List<CachedProjectEntity>)

    @Query("DELETE FROM cached_project WHERE workerId = :workerId")
    suspend fun clear(workerId: String)

    /**
     * Replaces the cached list in one transaction. Done as clear+insert
     * rather than upsert so a project DEACTIVATED on the server actually
     * disappears from the picker instead of lingering forever.
     */
    @androidx.room.Transaction
    suspend fun replaceAll(workerId: String, projects: List<CachedProjectEntity>) {
        clear(workerId)
        upsertAll(projects)
    }
}

@Database(
    entities = [
        PendingSubmissionEntity::class,
        CachedRecordEntity::class,
        CachedProjectEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AttendanceDatabase : RoomDatabase() {
    abstract fun pendingSubmissions(): PendingSubmissionDao
    abstract fun cachedRecords(): CachedRecordDao
    abstract fun cachedProjects(): CachedProjectDao
}
