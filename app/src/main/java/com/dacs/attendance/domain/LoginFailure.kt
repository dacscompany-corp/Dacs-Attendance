package com.dacs.attendance.domain

import java.io.IOException

/**
 * Every way logging in can fail, reduced to ONE of a handful of sentences
 * the worker actually sees. A raw ktor or Postgres string never reaches
 * the screen.
 */
enum class LoginFailure {
    MissingFields,
    WrongCredentials,
    AccountInactive,
    NotAWorker,

    /**
     * The Supabase project enforces Cloudflare Turnstile on auth, and the
     * app has no token to send. Its own case on purpose: this is a
     * configuration problem, not something the worker typed wrong, and
     * whoever debugs it should not start at the password.
     */
    CaptchaRequired,
    NoConnection,
    ServerProblem;

    companion object {
        fun of(error: Throwable): LoginFailure = when {
            // No signal is not a wrong password. On site the two mean
            // completely different things: "try again in a minute" versus
            // "walk to the office".
            error is IOException -> NoConnection
            error.mentions("captcha") -> CaptchaRequired
            error.looksLikeBadCredentials() -> WrongCredentials
            else -> ServerProblem
        }

        private fun Throwable.looksLikeBadCredentials(): Boolean =
            mentions("invalid_credentials") || mentions("Invalid login credentials")

        private fun Throwable.mentions(needle: String): Boolean {
            val text = (message ?: "") + " " + (cause?.message ?: "")
            return text.contains(needle, ignoreCase = true)
        }
    }
}
