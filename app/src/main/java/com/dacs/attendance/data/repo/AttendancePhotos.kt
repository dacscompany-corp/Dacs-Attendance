package com.dacs.attendance.data.repo

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How long a minted URL is asked to live. */
private val SIGNED_FOR = 1.hours

/**
 * Re-sign a little before the URL actually dies, so a slow scroll never
 * hands Coil a link that expires between request and response.
 */
private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L

/**
 * Signed URLs for attendance photos, cached per path.
 *
 * The `attendance` bucket is private (0050), and RLS lets a worker read
 * only their own objects, so every fetch needs a freshly signed link.
 * Signing is a cheap POST that moves no image bytes -- but it is still a
 * round trip, and History can show a month of days.
 *
 * So: sign ONCE per path per hour and keep it. Combined with Coil's disk
 * cache, a given photo crosses the network once per device rather than
 * once per glance. That distinction is the whole reason this class
 * exists: this project has already been over its egress quota once, and
 * a thumbnail grid is exactly the shape of thing that does it.
 */
@Singleton
class AttendancePhotos @Inject constructor(
    private val client: SupabaseClient
) {
    private data class Signed(val url: String, val expiresAtMs: Long)

    private val cache = mutableMapOf<String, Signed>()
    private val mutex = Mutex()

    /**
     * A URL Coil can load, or null when the path is unknown or signing
     * failed. Null is not an error worth showing: the caller falls back
     * to the plain colour bar, which carries the same IN/OUT reading.
     */
    suspend fun signedUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null

        val now = System.currentTimeMillis()
        mutex.withLock { cache[path] }
            ?.takeIf { it.expiresAtMs > now }
            ?.let { return it.url }

        val url = runCatchingExceptCancellation {
            client.storage.from(PHOTO_BUCKET).createSignedUrl(path, SIGNED_FOR)
        }.getOrNull() ?: return null

        mutex.withLock {
            cache[path] = Signed(
                url = url,
                expiresAtMs = now + SIGNED_FOR.inWholeMilliseconds - REFRESH_MARGIN_MS
            )
        }
        return url
    }

    /** Dropped on sign-out: the next worker must not reuse these links. */
    suspend fun clear() {
        mutex.withLock { cache.clear() }
    }
}
