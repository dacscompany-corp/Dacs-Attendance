package com.dacs.attendance.domain

/**
 * The design's "Or tap a common one" chips, as a pure operation on the
 * description text.
 *
 * The chips are ADDITIVE, not a replacement. A worker's day is usually
 * more than one thing -- "Masonry, Concrete pouring" -- and a chip that
 * overwrote the box would silently delete whatever they had already
 * typed. Nothing here ever discards text the worker entered: an unknown
 * part is carried through untouched.
 *
 * Kept out of the Composable so the fiddly half (matching, removing,
 * re-joining) can be tested without a device, which is where the last
 * nine bugs in this flow were found.
 */

/** What separates one part of a description from the next. */
private const val SEPARATOR = ", "

/**
 * The description split into its parts, blanks dropped.
 *
 * Split on commas only. A worker who presses enter mid-sentence has
 * written ONE part containing a newline, not two parts -- so newlines are
 * left inside the part rather than treated as separators.
 */
fun descriptionParts(description: String): List<String> =
    description.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** True when [chip] is already one of the description's parts. */
fun isChipSelected(description: String, chip: String): Boolean =
    descriptionParts(description).any { it.equals(chip, ignoreCase = true) }

/**
 * Adds [chip] to the description, or removes it if it is already there.
 *
 * Case-insensitive when matching, because a worker who typed "masonry"
 * and then taps "Masonry" means the same thing and must not end up with
 * both. The chip's own capitalisation is what gets stored.
 */
fun toggleDescriptionChip(description: String, chip: String): String {
    val parts = descriptionParts(description)
    val without = parts.filterNot { it.equals(chip, ignoreCase = true) }

    return if (without.size == parts.size) {
        (parts + chip).joinToString(SEPARATOR)
    } else {
        without.joinToString(SEPARATOR)
    }
}
