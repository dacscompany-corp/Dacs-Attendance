package com.dacs.attendance.domain

import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A worker on a site sees ONE of a small set of sentences, in both
 * languages. Never a raw Postgres or ktor string.
 */
class LoginFailureTest {

    @Test
    fun `no signal is not a wrong password`() {
        // The distinction matters on site: one means "try again later",
        // the other means "call the office".
        assertEquals(LoginFailure.NoConnection, LoginFailure.of(UnknownHostException("dns")))
        assertEquals(LoginFailure.NoConnection, LoginFailure.of(IOException("socket closed")))
    }

    @Test
    fun `supabase invalid_credentials becomes the wrong-password message`() {
        assertEquals(
            LoginFailure.WrongCredentials,
            LoginFailure.of(RuntimeException("""{"error_code":"invalid_credentials"}"""))
        )
        assertEquals(
            LoginFailure.WrongCredentials,
            LoginFailure.of(RuntimeException("Invalid login credentials"))
        )
    }

    @Test
    fun `the sign-in function's error codes map to their own sentences`() {
        // attendance-signin returns a stable code, decided server-side
        // where the profile row is readable. These are the only codes it
        // emits; anything else means the function and the app disagree,
        // which is a server problem, not a worker mistake.
        assertEquals(LoginFailure.WrongCredentials, LoginFailure.forCode("INVALID_CREDENTIALS"))
        assertEquals(LoginFailure.NotAWorker, LoginFailure.forCode("NOT_A_WORKER"))
        assertEquals(LoginFailure.AccountInactive, LoginFailure.forCode("ACCOUNT_INACTIVE"))
        assertEquals(LoginFailure.TooManyAttempts, LoginFailure.forCode("TOO_MANY_ATTEMPTS"))
    }

    @Test
    fun `an unknown or missing code is a server problem, not a wrong password`() {
        assertEquals(LoginFailure.ServerProblem, LoginFailure.forCode("SOMETHING_NEW"))
        assertEquals(LoginFailure.ServerProblem, LoginFailure.forCode(null))
    }

    @Test
    fun `anything else is reported as a server problem, not as bad credentials`() {
        // Telling a worker "wrong password" when the server is down sends
        // them to the office for nothing.
        assertEquals(LoginFailure.ServerProblem, LoginFailure.of(RuntimeException("500")))
    }
}
