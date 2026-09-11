package com.dacs.attendance.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Not sent yet" on the home screen. Phones are shared on site, so it must
 * only ever speak about the worker it is showing.
 */
@RunWith(AndroidJUnit4::class)
class PendingSubmissionDaoTest {

    private lateinit var db: AttendanceDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AttendanceDatabase::class.java
        ).build()
    }

    @After
    fun close() = db.close()

    private fun row(eventId: String, workerId: String, failed: Boolean = false) =
        PendingSubmissionEntity(
            eventId = eventId,
            workerId = workerId,
            direction = "IN",
            projectSystem = "pc",
            projectId = "p1",
            projectName = "Site",
            capturedAt = 0L,
            photoLocalPath = "/nowhere.jpg",
            description = null,
            latitude = null,
            longitude = null,
            accuracyMetres = null,
            wasOffline = true,
            failedPermanently = failed,
            createdAt = 0L
        )

    @Test
    fun anEmptyQueueHasNothingPending() = runBlocking {
        assertFalse(db.pendingSubmissions().hasPendingFor("w1"))
    }

    @Test
    fun theWorkersOwnRowCounts() = runBlocking {
        db.pendingSubmissions().insert(row("e1", "w1"))

        assertTrue(db.pendingSubmissions().hasPendingFor("w1"))
    }

    @Test
    fun anotherWorkersRowDoesNotCount() = runBlocking {
        db.pendingSubmissions().insert(row("e1", "w2"))

        assertFalse(db.pendingSubmissions().hasPendingFor("w1"))
    }

    @Test
    fun aPermanentlyFailedRowDoesNotCount() = runBlocking {
        // It will never be sent, so "not sent YET" would promise something
        // the queue has already given up on.
        db.pendingSubmissions().insert(row("e1", "w1", failed = true))

        assertFalse(db.pendingSubmissions().hasPendingFor("w1"))
    }
}
