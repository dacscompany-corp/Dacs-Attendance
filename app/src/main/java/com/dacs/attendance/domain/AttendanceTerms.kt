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
    const val VERSION = "2026-09-v3"

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
            heading = "Location check.",
            // Four sentences where the others use two, and each one is
            // load-bearing:
            //   what is taken, and when
            //   that it is NOT continuous tracking -- the single most
            //     likely fear, and the spec is explicit that the system
            //     must not be a worker-monitoring tool
            //   that a poor signal does NOT cost them the day, because
            //     otherwise this clause reads far harsher than the
            //     software actually behaves
            //   what genuinely does refuse
            english = "Your location is checked when you Time In and Time Out, to " +
                "confirm you are at the project you selected. It is taken only at " +
                "those two moments; the app does not follow you at any other time. " +
                "If your phone cannot get a clear location, your attendance is still " +
                "recorded and marked for the Admin to check. If it shows you are away " +
                "from the site, or location is turned off, the attendance cannot be " +
                "recorded.",
            tagalog = "Titingnan ang lokasyon mo tuwing Time In at Time Out, para " +
                "makumpirma na nasa project ka na iyong pinili. Sa dalawang sandaling " +
                "iyon lang ito kinukuha; hindi ka sinusundan ng app sa ibang oras. " +
                "Kung hindi makakuha ng malinaw na lokasyon ang telepono mo, " +
                "maitatala pa rin ang pasok mo at mamarkahan para tingnan ng Admin. " +
                "Kung malayo ka sa site ayon dito, o naka-off ang lokasyon, hindi " +
                "maitatala ang pasok."
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
        ),
        TermsClause(
            heading = "Weekly attendance reward.",
            // ── NO PESO FIGURE, DELIBERATELY.
            //    The amount lives in attendance_config and the Owner can
            //    change it. This text is hashed into agreement_events as
            //    evidence, so naming a number here would mean either
            //    binding text that goes quietly wrong the day it changes,
            //    or forcing every worker to re-accept the Terms over a
            //    raise. The app shows the current figure; the Terms say
            //    how it is earned.
            //
            // ── NO CUTOFF TIME EITHER, for the same reason: the start
            //    time is per project since 0065.
            //
            // ── "may give" rather than "gives". Qualification is computed
            //    automatically, but payment happens outside this system
            //    entirely (decision 14) -- nothing here creates an
            //    accounting entry. Promising payment in binding text
            //    would claim more than the software does.
            english = "The Owner may give a weekly attendance reward for a complete, " +
                "on-time week. It is based on your Time In on each required day from " +
                "Monday to Friday, compared against the start time set for your " +
                "project: one late day, or one required day with no Time In, means no " +
                "reward for that week, and there is no partial amount. Days the site " +
                "is closed are not counted against you. This reward is separate from " +
                "your pay and is not computed from your hours.",
            tagalog = "Maaaring magbigay ang May-ari ng lingguhang reward para sa " +
                "kumpleto at hindi nahuling pasok. Nakabatay ito sa Time In mo sa " +
                "bawat araw na kailangan mula Lunes hanggang Biyernes, ayon sa oras ng " +
                "simula na nakatakda para sa project mo: isang araw na late, o isang " +
                "araw na walang Time In, ay walang reward para sa linggong iyon, at " +
                "walang bahagyang halaga. Hindi bibilangin laban sa iyo ang mga araw " +
                "na sarado ang site. Hiwalay ito sa sahod mo at hindi ito kinukuwenta " +
                "mula sa oras mo."
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
