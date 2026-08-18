package com.dacs.attendance.data.repo

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
