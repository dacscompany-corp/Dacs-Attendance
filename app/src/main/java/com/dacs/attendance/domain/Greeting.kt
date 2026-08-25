package com.dacs.attendance.domain

import java.time.Instant

/**
 * Which greeting the dashboard opens with.
 *
 * Not decoration: a worker timing out at half past one was being told
 * "magandang umaga", and a night shift arriving at ten would have been
 * told the same. The app is used across the whole day.
 */
enum class Greeting { MORNING, AFTERNOON, EVENING }

/** Manila hour, because the greeting should match the worker's day. */
fun greetingAt(now: Instant): Greeting = greetingAtHour(now.atZone(AttendanceZone).hour)

fun greetingAtHour(hour: Int): Greeting = when (hour) {
    in 5..11 -> Greeting.MORNING
    in 12..17 -> Greeting.AFTERNOON
    else -> Greeting.EVENING
}
