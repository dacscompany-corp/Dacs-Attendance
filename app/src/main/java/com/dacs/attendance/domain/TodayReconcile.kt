package com.dacs.attendance.domain

/** What the dashboard should do with today, after weighing three sources. */
sealed interface TodayDecision {
    /** Show this record. */
    data class Use(val record: AttendanceRecord) : TodayDecision

    /** There is genuinely no record today; drop any stale mirror. */
    data object Clear : TodayDecision

    /** We could not find out. Not the same as "no record". */
    data object Unknown : TodayDecision
}

/**
 * Reconciles the server, the local mirror and the queue.
 *
 * The trap this exists to avoid: a nullable result collapses "the server
 * has no record" and "the server could not be reached" into the same
 * value, and they demand opposite behaviour. One should clear the
 * mirror; the other must never touch it.
 *
 * And when a submission is still queued, the server having no row is the
 * EXPECTED state, not a contradiction -- the upload has not happened
 * yet. Clearing the mirror there would erase a Time In the worker was
 * already told was saved, which is the worst thing this app could do.
 */
fun reconcileToday(
    server: Result<AttendanceRecord?>,
    cached: AttendanceRecord?,
    hasPending: Boolean
): TodayDecision = server.fold(
    onSuccess = { record ->
        when {
            record != null -> TodayDecision.Use(record)
            hasPending && cached != null -> TodayDecision.Use(cached)
            else -> TodayDecision.Clear
        }
    },
    onFailure = {
        if (cached != null) TodayDecision.Use(cached) else TodayDecision.Unknown
    }
)
