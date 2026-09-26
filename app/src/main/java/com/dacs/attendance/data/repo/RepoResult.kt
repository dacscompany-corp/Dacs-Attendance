package com.dacs.attendance.data.repo

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException

/**
 * runCatching swallows CancellationException, which turns a cancelled
 * coroutine into a fake failure and quietly breaks structured
 * concurrency. Every suspend call in the data layer goes through this
 * instead.
 */
internal inline fun <T> runCatchingExceptCancellation(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Throwable) {
    Result.failure(error)
}

/**
 * Refuses to go on when there is no session to make the request with.
 *
 * ── WHY A READ WITHOUT A SESSION IS WORSE THAN A FAILED ONE.
 *
 *    The Supabase client does not refuse a request it has no token for.
 *    It falls back to the anon key (`resolveAccessToken`), so the request
 *    goes out and SUCCEEDS -- as the anon role, with `auth.uid()` null.
 *    Every one of our row-level policies is written against `auth.uid()`,
 *    and a SELECT that matches no policy is not an error in PostgREST: it
 *    is an empty result with a 200.
 *
 *    So an unauthenticated read is indistinguishable from a true negative.
 *    "This worker has no profile." "This worker has never accepted the
 *    Terms." Both were believed, and both were wrong -- one signed workers
 *    out of an app they never left, the other sent them back to a Terms
 *    screen they could not complete.
 *
 *    A thrown failure, in contrast, is something every caller here already
 *    handles correctly, because it is what being offline looks like.
 *
 * Named AUTH_REQUIRED to match the guards already on the reads that were
 * written this way from the start (RewardRepository, SupabaseAttendance-
 * Repository).
 */
internal fun SupabaseClient.requireSession() {
    checkNotNull(auth.currentAccessTokenOrNull()) { "AUTH_REQUIRED" }
}
