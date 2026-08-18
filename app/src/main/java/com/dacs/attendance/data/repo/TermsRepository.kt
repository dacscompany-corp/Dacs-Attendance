package com.dacs.attendance.data.repo

import com.dacs.attendance.domain.WorkerProfile

/** The Terms gate's two questions: has this worker accepted, and record that they did. */
interface TermsRepository {

    /** Every version this worker has ever accepted. */
    suspend fun acceptedVersions(workerId: String): Result<Set<String>>

    /** Records acceptance of the CURRENT version, plus its audit evidence. */
    suspend fun accept(worker: WorkerProfile): Result<Unit>
}
