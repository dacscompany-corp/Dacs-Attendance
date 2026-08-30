package com.dacs.attendance.domain

import java.security.MessageDigest

/** One numbered clause, in both languages, as the design shows it. */
data class TermsClause(
    val heading: String,
    val english: String,
    val tagalog: String
)

/**
 * The Terms a worker accepts on first log in.
 *
 * This text is EVIDENCE. On acceptance the app writes two rows:
 *   attendance_terms_acceptances -- the versioned flag the gate reads
 *   agreement_events (0021)      -- the immutable evidence, carrying the
 *                                   full doc_text and its doc_sha256
 * agreement_events is append-only even for service_role, so what is
 * hashed here is what can be produced later if an acceptance is ever
 * disputed.
 *
 * CHANGING ANY WORD BELOW REQUIRES BUMPING [VERSION]. The gate keys on
 * the version string; edit the text without bumping it and every worker
 * who accepted the old wording keeps a row claiming they accepted the
 * new one -- exactly the claim the hash exists to prevent.
 */
object AttendanceTerms {

    /** Bump on ANY edit to the clauses below. */
    const val VERSION = "2026-08-v2"

    const val TITLE = "DAC's Attendance -- Terms & Conditions"

    val clauses: List<TermsClause> = listOf(
        TermsClause(
            heading = "Recording attendance.",
            english = "You record your own Time In and Time Out using this app. " +
                "The date and time are set by the system, not typed by you.",
            tagalog = "Ikaw mismo ang magta-Time In at Time Out. Ang petsa at oras ay " +
                "galing sa sistema, hindi ito tina-type."
        ),
        TermsClause(
            heading = "Project selection.",
            english = "You choose the project you are working on each time you time in " +
                "and time out.",
            tagalog = "Pipiliin mo ang project na pinagtatrabahuhan mo sa tuwing " +
                "magta-Time In at Time Out ka."
        ),
        TermsClause(
            heading = "Photo documentation.",
            english = "A photo is taken at Time In and at Time Out as proof of " +
                "attendance and is stored with your record.",
            tagalog = "May kukunang litrato tuwing Time In at Time Out bilang patunay " +
                "ng pagpasok, at itatago ito kasama ng iyong record."
        ),
        TermsClause(
            heading = "Honest use.",
            english = "Your account is yours alone. Do not let another person time in " +
                "or out for you.",
            tagalog = "Sa iyo lang ang account mo. Huwag hayaang may ibang tao ang " +
                "mag-Time In o Time Out para sa iyo."
        ),
        TermsClause(
            heading = "Who can see your records.",
            // "payroll purposes" was removed on the owner's confirmation:
            // DAC's labour is pakyaw, capped by labor_contracts, and these
            // hours are NOT what the pay is computed from. Saying they
            // were would have been a false statement in binding text --
            // and the sentence a worker would quote back in a dispute.
            english = "Your attendance records, photos and location may be viewed by " +
                "the Admin and the Owner to check attendance and site activity. " +
                "These records are not used to compute your pay.",
            tagalog = "Ang iyong mga record, litrato at lokasyon ay maaaring makita ng " +
                "Admin at ng May-ari para tingnan ang pasok at ang trabaho sa site. " +
                "Hindi ito ang batayan ng sahod mo."
        )
    )

    /**
     * The exact text that gets hashed and stored. Built FROM the clauses,
     * so the wording shown and the wording hashed cannot drift apart --
     * there is no second copy of this text anywhere.
     */
    fun canonicalText(): String = buildString {
        append(TITLE).append('\n')
        append("Version: ").append(VERSION).append("\n\n")
        clauses.forEachIndexed { index, clause ->
            append(index + 1).append(". ").append(clause.heading).append(' ')
            append(clause.english).append('\n')
            append(clause.tagalog).append("\n\n")
        }
    }.trimEnd()

    fun sha256(): String = sha256Hex(canonicalText())
}

/** Lowercase hex SHA-256 -- the format agreement_events.doc_sha256 already holds. */
fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
