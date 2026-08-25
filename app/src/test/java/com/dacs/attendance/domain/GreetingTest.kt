package com.dacs.attendance.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The dashboard opens with a greeting. It was hardcoded to "umaga" and
 * said good morning to a worker timing out at half past one.
 */
class GreetingTest {

    @Test
    fun `morning until noon`() {
        assertEquals(Greeting.MORNING, greetingAtHour(5))
        assertEquals(Greeting.MORNING, greetingAtHour(11))
    }

    @Test
    fun `afternoon from noon`() {
        assertEquals(Greeting.AFTERNOON, greetingAtHour(12))
        assertEquals(Greeting.AFTERNOON, greetingAtHour(17))
    }

    @Test
    fun `evening from six`() {
        // A worker timing out after a long day is greeted correctly, and
        // a night shift arriving at 22:00 is not told good morning.
        assertEquals(Greeting.EVENING, greetingAtHour(18))
        assertEquals(Greeting.EVENING, greetingAtHour(23))
        assertEquals(Greeting.EVENING, greetingAtHour(4))
    }
}
