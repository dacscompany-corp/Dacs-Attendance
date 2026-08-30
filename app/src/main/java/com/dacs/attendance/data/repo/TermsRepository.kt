package com.dacs.attendance.data.repo

import com.dacs.attendance.domain.WorkerProfile
import java.time.Instant

/** The Terms gate's two questions: has this worker accepted, and record that they did. */
interface TermsRepository {

    /** Every version this worker has ever accepted. */
    suspend fun acceptedVersions(workerId: String): Result<Set<String>>

    /** Records acceptance of the CURRENT version, plus its audit evidence. */
    suspend fun accept(worker: WorkerProfile): Result<Unit>

    /**
     * WHEN this worker accepted [version], for the Profile screen.
     *
     * Null means no acceptance row exists -- which the gate would never
     * allow past the Terms screen, so on Profile it can only mean the
     * lookup came back empty. The screen shows no date rather than a
     * guessed one; an invented acceptance date is worse than none.
     */
    suspend fun acceptedAt(workerId: String, version: String): Result<Instant?>
}
