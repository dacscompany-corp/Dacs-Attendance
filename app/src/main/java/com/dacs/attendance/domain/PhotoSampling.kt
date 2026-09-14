package com.dacs.attendance.domain

/**
 * How far down to sample a capture while decoding it.
 *
 * A full-resolution decode is what puts the Time In flow out of memory on
 * a cheap phone: a 13 MP selfie is 52 MB as ARGB_8888, and the upright
 * transform allocates a second copy before the first is released. The
 * phones this app targets are often capped at 96 MB for the whole
 * process, some at half that.
 *
 * Sampling during the decode means the full-size bitmap never exists.
 */
fun photoSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    val longest = maxOf(width, height)
    // BitmapFactory reports -1 for a file it could not measure. Decoding
    // whole is the honest answer: a huge sample would quietly turn an
    // unreadable capture into a thumbnail that uploads and proves nothing.
    if (longest <= 0 || maxEdge <= 0) return 1

    // Halve only while the result STAYS at or above the budget, so the
    // caller can still scale to exactly the budget edge afterwards and the
    // filed photo keeps the dimensions it has today. Powers of two only:
    // BitmapFactory rounds anything else down to 1.
    var sample = 1
    while (longest / (sample * 2) >= maxEdge) sample *= 2
    return sample
}
