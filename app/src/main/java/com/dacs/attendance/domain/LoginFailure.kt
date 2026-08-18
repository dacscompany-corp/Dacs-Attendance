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
    TooManyAttempts,
    NoConnection,
    ServerProblem;

    companion object {

        /**
         * The stable codes `attendance-signin` returns. The decision is
         * made server-side, where the profile row is readable without
         * handing a session to an account that is not allowed one.
         */
        fun forCode(code: String?): LoginFailure = when (code) {
            "INVALID_CREDENTIALS" -> WrongCredentials
            "NOT_A_WORKER" -> NotAWorker
            "ACCOUNT_INACTIVE" -> AccountInactive
            "TOO_MANY_ATTEMPTS" -> TooManyAttempts
            // An unknown code means the function and the app have drifted
            // apart. That is our problem, not the worker's password.
            else -> ServerProblem
        }

        /** For transport failures, which never carry one of the codes above. */
        fun of(error: Throwable): LoginFailure = when {
            // No signal is not a wrong password. On site the two mean
            // completely different things: "try again in a minute" versus
            // "walk to the office".
            error is IOException -> NoConnection
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
