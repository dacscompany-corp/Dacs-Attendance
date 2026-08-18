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
    fun `a captcha challenge is its own failure, not a generic server error`() {
        // This project enforces Cloudflare Turnstile on auth: a password
        // grant with no captcha token is refused outright. Verified against
        // the live endpoint on 2026-08-18. Reporting it as "something went
        // wrong" would send whoever debugs it looking at the password.
        assertEquals(
            LoginFailure.CaptchaRequired,
            LoginFailure.of(
                RuntimeException(
                    """{"code":400,"error_code":"captcha_failed",""" +
                        """"msg":"captcha protection: request disallowed"}"""
                )
            )
        )
    }

    @Test
    fun `anything else is reported as a server problem, not as bad credentials`() {
        // Telling a worker "wrong password" when the server is down sends
        // them to the office for nothing.
        assertEquals(LoginFailure.ServerProblem, LoginFailure.of(RuntimeException("500")))
    }
}
