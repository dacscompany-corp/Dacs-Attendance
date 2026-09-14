package com.dacs.attendance.data.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.dacs.attendance.domain.DeviceFix
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One location reading, taken at the shutter.
 *
 * ── WHY FUSED AND NOT getLastKnownLocation(). The framework's
 *    last-known fix is whatever the phone happened to record last, which
 *    may be hours old and kilometres away. That is the DANGEROUS failure
 *    for this feature: a fix captured at home this morning would verify
 *    a worker onto a site they are not standing on, and it would look
 *    exactly like a good reading. getCurrentLocation() asks for a fresh
 *    one.
 *
 * ── EVERY FAILURE PATH RETURNS A [DeviceFix], never an exception.
 *    A missing fix is not an error the flow should abort on -- it is
 *    recorded and flagged (migration 0069). Throwing here would turn
 *    "the phone could not tell" into "the worker cannot record today",
 *    which is the outcome the whole design is arranged to avoid.
 */
/**
 * The seam between the flow and the GPS hardware.
 *
 * An interface because [TimeFlowViewModel] decides whether a worker may
 * record attendance at all, and that decision has to be testable without
 * an Android Context, a Play Services install, or a satellite. The
 * repositories in this app are split the same way and for the same
 * reason.
 */
interface LocationSource {
    fun hasPermission(): Boolean
    suspend fun currentFix(): DeviceFix
}

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationSource {

    /**
     * Waits at most this long for a fix.
     *
     * A worker is standing at the gate holding a phone. Fifteen seconds
     * is already a long time to stare at a spinner, and the cost of
     * giving up is small: no fix flags the record, it does not refuse
     * it. Waiting longer would trade a worse experience for a better
     * chance at a reading that is optional anyway.
     */
    private val timeoutMs = 15_000L

    /**
     * A fix older than this is treated as no fix.
     *
     * getCurrentLocation() should always return something fresh, so this
     * is a belt-and-braces check rather than an expected path. It exists
     * because the consequence of trusting a stale reading is silently
     * verifying attendance at the wrong place.
     */
    private val maxAgeMs = 2 * 60 * 1000L

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun currentFix(): DeviceFix {
        // Refusing the permission and failing to get a fix arrive at the
        // server identically -- as null coordinates -- and only the
        // device can tell them apart. So the device says which, and the
        // server trusts it for exactly this.
        if (!hasPermission()) return DeviceFix(permissionDenied = true)

        val location = try {
            withTimeoutOrNull(timeoutMs) { awaitCurrentLocation() }
        } catch (e: SecurityException) {
            // The permission was revoked between the check above and the
            // request. Report it as a denial, which is what it is.
            return DeviceFix(permissionDenied = true)
        } catch (e: Exception) {
            // Play Services missing or broken. Common enough on the
            // cheapest handsets to be worth handling rather than
            // crashing: it becomes an unverified record, not a lockout.
            null
        }

        if (location == null) return DeviceFix()

        if (ageMillis(location) > maxAgeMs) return DeviceFix()

        return DeviceFix(
            latitude = location.latitude,
            longitude = location.longitude,
            // A location with no accuracy is a location we cannot place.
            // Left null so the check reads it as LowAccuracy rather than
            // treating an unknown radius as a perfect one.
            accuracyMetres = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            isMock = location.isMockCompat()
        )
    }

    private suspend fun awaitCurrentLocation(): Location? =
        suspendCancellableCoroutine { cont ->
            val cancel = CancellationTokenSource()
            try {
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            } catch (e: SecurityException) {
                // Caught here as well as in currentFix(): the permission
                // can be revoked between that check and this call, and the
                // throw lands HERE, synchronously. Named rather than left
                // to the branch below so the guarantee is visible at the
                // call site -- and so lint can see it, which it cannot do
                // through a catch of Exception in a different method.
                cont.resume(null)
            } catch (e: Exception) {
                cont.resume(null)
            }
            cont.invokeOnCancellation { cancel.cancel() }
        }

    /** Elapsed-realtime based, so a user changing the clock cannot fake freshness. */
    private fun ageMillis(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000

    /**
     * Client-reported, and defeatable on a rooted device. It catches
     * casual spoofing and not determined spoofing, which is why the
     * record stores what was observed rather than claiming certainty.
     */
    private fun Location.isMockCompat(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isMock
        } else {
            @Suppress("DEPRECATION")
            isFromMockProvider
        }
}
