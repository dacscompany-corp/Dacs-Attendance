package com.dacs.attendance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The device's pre-check at the shutter.
 *
 * These tests fence the rule that decides whether a worker can record
 * attendance at all, so they are written against the same cases as
 * `supabase/tests/0069_verify.sql`. If the two ever disagree, the app
 * accepts things the server refuses -- or refuses things it would have
 * accepted, which costs a worker a day and, with the reward attached,
 * ₱500.
 */
class LocationVerificationTest {

    // A site in Metro Manila, and a 150 m fence around it.
    private val fence = Geofence(latitude = 14.5995, longitude = 120.9842, radiusMetres = 150.0)

    private fun fixAt(
        lat: Double,
        lng: Double,
        accuracy: Double = 12.0
    ) = DeviceFix(latitude = lat, longitude = lng, accuracyMetres = accuracy)

    @Test
    fun `one degree of latitude is about 111 kilometres`() {
        // The distance every other test rests on, checked against a
        // figure that is true anywhere on earth. This is also the check
        // in 0068_verify.sql, so the two implementations are pinned to
        // the same number.
        val metres = distanceMetres(14.0, 121.0, 15.0, 121.0)

        assertTrue("expected ~111195 m, got $metres", metres in 110_000.0..112_000.0)
    }

    @Test
    fun `location switched off is refused, not merely treated as no fix`() {
        // The 2026-09-15 defect: a Time In was recorded at Sulit
        // Residence from outside its 500 m fence because the phone
        // reported no coordinates, and no coordinates is deliberately
        // flagged rather than refused. A switched-off phone reported the
        // same thing as one that simply could not see the sky.
        val check = checkLocation(
            DeviceFix(locationDisabled = true), fence, minAccuracyMetres = 50.0
        )

        assertEquals(LocationStatus.LocationDisabled, check.status)
        assertTrue(
            "switching location off must not be recordable",
            locationRefuses(check.status, requireGeofence = false)
        )
        assertEquals("location_disabled", check.status.wire)
    }

    @Test
    fun `a phone that is ON but cannot get a fix is still recorded`() {
        // The other half of the same rule, and the reason the split had
        // to exist at all: this worker keeps their day, and their bonus.
        val check = checkLocation(DeviceFix(), fence, minAccuracyMetres = 50.0)

        assertEquals(LocationStatus.LocationUnavailable, check.status)
        assertFalse(
            "a weak or absent fix is never an accusation",
            locationRefuses(check.status, requireGeofence = false)
        )
    }

    @Test
    fun `a denied permission and a switched-off phone stay separate`() {
        // They are fixed on two different settings screens. Collapsing
        // them sends a worker somewhere that cannot help.
        assertEquals(
            LocationStatus.PermissionDenied,
            checkLocation(DeviceFix(permissionDenied = true), fence, 50.0).status
        )
        assertEquals(
            LocationStatus.LocationDisabled,
            checkLocation(DeviceFix(locationDisabled = true), fence, 50.0).status
        )
    }

    @Test
    fun `standing on the site verifies`() {
        val check = checkLocation(fixAt(14.5995, 120.9842), fence, minAccuracyMetres = 50.0)

        assertEquals(LocationStatus.Verified, check.status)
        assertTrue(check.distanceMetres!! < 1.0)
        assertFalse(locationRefuses(check.status, requireGeofence = true))
    }

    @Test
    fun `just inside the fence still verifies`() {
        // ~100 m north of the centre: 0.0009 degrees of latitude.
        val check = checkLocation(fixAt(14.6004, 120.9842), fence, minAccuracyMetres = 50.0)

        assertEquals(LocationStatus.Verified, check.status)
        assertTrue(check.distanceMetres!! in 90.0..110.0)
    }

    @Test
    fun `a worker somewhere else is refused`() {
        // ~1.1 km north. Demonstrably not on site.
        val check = checkLocation(fixAt(14.6095, 120.9842), fence, minAccuracyMetres = 50.0)

        assertEquals(LocationStatus.OutsideRadius, check.status)
        assertTrue(check.distanceMetres!! > 1000.0)
        assertTrue(
            "being outside the fence is the one refusal a worker earns",
            locationRefuses(check.status, requireGeofence = false)
        )
    }

    @Test
    fun `a vague fix is recorded, never refused`() {
        // Standing on the site, but the phone only knows it to 300 m.
        // That is the phone's failure, not the worker's, and refusing
        // here would cost them the day and the reward with it.
        val check = checkLocation(fixAt(14.5995, 120.9842, accuracy = 300.0), fence, 50.0)

        assertEquals(LocationStatus.LowAccuracy, check.status)
        assertFalse(locationRefuses(check.status, requireGeofence = true))
    }

    @Test
    fun `no fix at all is recorded, never refused`() {
        val check = checkLocation(DeviceFix(), fence, minAccuracyMetres = 50.0)

        assertEquals(LocationStatus.LocationUnavailable, check.status)
        assertNull(check.distanceMetres)
        assertFalse(locationRefuses(check.status, requireGeofence = true))
    }

    @Test
    fun `a mock location is refused however good it looks`() {
        // Coordinates dead on the site, and a fake provider behind them.
        val check = checkLocation(
            fixAt(14.5995, 120.9842).copy(isMock = true),
            fence,
            minAccuracyMetres = 50.0
        )

        assertEquals(LocationStatus.MockLocation, check.status)
        assertTrue(locationRefuses(check.status, requireGeofence = false))
    }

    @Test
    fun `declining the permission is refused, and is not the same as no fix`() {
        val denied = checkLocation(
            DeviceFix(permissionDenied = true), fence, minAccuracyMetres = 50.0
        )
        val noFix = checkLocation(DeviceFix(), fence, minAccuracyMetres = 50.0)

        assertEquals(LocationStatus.PermissionDenied, denied.status)
        assertEquals(LocationStatus.LocationUnavailable, noFix.status)

        // Both arrive as null coordinates, and only the device can tell
        // them apart -- which is why it reports the difference rather
        // than leaving the server to guess.
        assertTrue(locationRefuses(denied.status, requireGeofence = false))
        assertFalse(locationRefuses(noFix.status, requireGeofence = false))
    }

    @Test
    fun `an unconfigured project is refused only once geofences are required`() {
        val check = checkLocation(fixAt(14.5995, 120.9842), fence = null, minAccuracyMetres = 50.0)

        assertEquals(LocationStatus.ProjectGeofenceUnavailable, check.status)
        assertNull(check.distanceMetres)

        // Off during rollout: an admin who has not set coordinates yet
        // must not lock out the whole crew.
        assertFalse(locationRefuses(check.status, requireGeofence = false))
        assertTrue(locationRefuses(check.status, requireGeofence = true))
    }

    @Test
    fun `a disabled fence behaves as no fence`() {
        val check = checkLocation(
            fixAt(14.5995, 120.9842),
            fence.copy(enabled = false),
            minAccuracyMetres = 50.0
        )

        assertEquals(LocationStatus.ProjectGeofenceUnavailable, check.status)
    }

    @Test
    fun `permission and mock beat everything, in the server's order`() {
        // Both true, and outside the fence as well. The status has to be
        // the one the server would pick, or the app shows a worker a
        // different reason than the record carries.
        val check = checkLocation(
            DeviceFix(
                latitude = 14.7000, longitude = 120.9842, accuracyMetres = 5.0,
                isMock = true, permissionDenied = true
            ),
            fence,
            minAccuracyMetres = 50.0
        )

        assertEquals(LocationStatus.PermissionDenied, check.status)
    }

    @Test
    fun `distance is kept even when the status was settled by something else`() {
        // An admin reviewing a flagged record wants to know how far off
        // it was, not merely that it was uncertain.
        val check = checkLocation(fixAt(14.6095, 120.9842, accuracy = 400.0), fence, 50.0)

        assertEquals(LocationStatus.LowAccuracy, check.status)
        assertNotNull("the distance is evidence, not a by-product", check.distanceMetres)
        assertTrue(check.distanceMetres!! > 1000.0)
    }

    @Test
    fun `the fence used is carried back for the record's snapshot`() {
        val check = checkLocation(fixAt(14.5995, 120.9842), fence, minAccuracyMetres = 50.0)

        // Snapshotted onto the record so history keeps the fence it was
        // judged against, even after the fence is edited.
        assertEquals(fence, check.geofence)
    }

    @Test
    fun `wire values match the codes the server stores`() {
        assertEquals("verified", LocationStatus.Verified.wire)
        assertEquals("outside_radius", LocationStatus.OutsideRadius.wire)
        assertEquals("low_accuracy", LocationStatus.LowAccuracy.wire)
        assertEquals("location_unavailable", LocationStatus.LocationUnavailable.wire)
        assertEquals("mock_location", LocationStatus.MockLocation.wire)
        assertEquals("permission_denied", LocationStatus.PermissionDenied.wire)
        assertEquals("project_geofence_unavailable", LocationStatus.ProjectGeofenceUnavailable.wire)
    }
}
