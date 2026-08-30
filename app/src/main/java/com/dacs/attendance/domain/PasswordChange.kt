package com.dacs.attendance.domain

import java.io.IOException

/** Supabase's own floor is 6; 8 is the project's, and the stricter wins. */
const val MIN_PASSWORD_LENGTH = 8

/**
 * Every way changing a password can fail, local checks and server
 * refusals in ONE type -- so the dialog renders one thing regardless of
 * which side said no.
 */
enum class PasswordChangeFailure {
    TooShort,
    Mismatch,

    /** The server refused it as identical to the current password. */
    Reused,

    /** The session died while the dialog was open; they must log in again. */
    SessionExpired,

    NoConnection,
    Unexpected;

    companion object {

        /**
         * Checked BEFORE the network call. A password too short to be
         * accepted should not cost a round trip on site signal, and a
         * mistyped confirmation is not something the server can see at
         * all -- it only ever receives one of the two boxes.
         */
        fun validate(password: String, confirmation: String): PasswordChangeFailure? = when {
            // isBlank first: eight spaces passes a length check and is
            // not a password anyone can retype tomorrow.
            password.isBlank() || password.length < MIN_PASSWORD_LENGTH -> TooShort
            password != confirmation -> Mismatch
            else -> null
        }

        fun of(error: Throwable): PasswordChangeFailure {
            if (error is IOException) return NoConnection

            val text = (error.message ?: "") + " " + (error.cause?.message ?: "")
            return when {
                text.contains("should be different", ignoreCase = true) ||
                    text.contains("same_password", ignoreCase = true) -> Reused

                text.contains("at least", ignoreCase = true) ||
                    text.contains("weak_password", ignoreCase = true) -> TooShort

                text.contains("401") ||
                    text.contains("JWT", ignoreCase = true) ||
                    text.contains("session", ignoreCase = true) -> SessionExpired

                else -> Unexpected
            }
        }
    }
}
