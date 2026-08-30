package com.dacs.attendance.domain

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Changing a password from the Profile screen (design screen 11).
 *
 * The worker doing this is standing on a site with one bar of signal, so
 * everything decidable without the network is decided here first.
 */
class PasswordChangeTest {

    @Test
    fun `a good password and a matching confirmation passes`() {
        assertNull(PasswordChangeFailure.validate("bagongpass1", "bagongpass1"))
    }

    @Test
    fun `too short is caught before the network`() {
        assertEquals(
            PasswordChangeFailure.TooShort,
            PasswordChangeFailure.validate("abc123", "abc123")
        )
    }

    @Test
    fun `eight spaces is not a password`() {
        // Long enough to pass a naive length check, impossible to retype.
        assertEquals(
            PasswordChangeFailure.TooShort,
            PasswordChangeFailure.validate("        ", "        ")
        )
    }

    @Test
    fun `a mistyped confirmation is caught locally`() {
        // The server only ever sees one of the two boxes, so it could
        // never detect this.
        assertEquals(
            PasswordChangeFailure.Mismatch,
            PasswordChangeFailure.validate("bagongpass1", "bagongpass2")
        )
    }

    @Test
    fun `length is reported before the mismatch`() {
        // Both are wrong; the one they must fix either way comes first.
        assertEquals(
            PasswordChangeFailure.TooShort,
            PasswordChangeFailure.validate("abc", "abcd")
        )
    }

    @Test
    fun `no signal is not a rejected password`() {
        assertEquals(
            PasswordChangeFailure.NoConnection,
            PasswordChangeFailure.of(IOException("Unable to resolve host"))
        )
    }

    @Test
    fun `the server refusing a reused password says so`() {
        assertEquals(
            PasswordChangeFailure.Reused,
            PasswordChangeFailure.of(
                RuntimeException("New password should be different from the old password.")
            )
        )
    }

    @Test
    fun `an expired session is not a password problem`() {
        assertEquals(
            PasswordChangeFailure.SessionExpired,
            PasswordChangeFailure.of(RuntimeException("401: invalid JWT"))
        )
    }

    @Test
    fun `an unrecognised refusal never guesses`() {
        assertEquals(
            PasswordChangeFailure.Unexpected,
            PasswordChangeFailure.of(RuntimeException("boom"))
        )
    }
}
