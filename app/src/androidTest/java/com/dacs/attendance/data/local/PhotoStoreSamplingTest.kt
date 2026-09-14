package com.dacs.attendance.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A real-sized capture, sampled during the decode.
 *
 * PhotoStoreOrientationTest proves the EXIF transform on a 200x100 source
 * -- small enough that no sampling happens at all. This covers the case
 * the field actually produces: a multi-megapixel front-camera selfie,
 * which is decoded at a fraction of its size and must still come out
 * upright, un-mirrored, and exactly at the storage budget.
 *
 * The two interact: sampling changes the dimensions the flip and rotate
 * matrix runs against, and the scale that follows has to still land on
 * the budget edge. Getting that wrong files a sideways photo, or one
 * larger than the storage estimate assumed.
 *
 * Method names here are plain identifiers, not backticked sentences: a
 * backticked name containing a lambda (runBlocking) generates an inner
 * class whose SimpleName carries the spaces, and DEX rejects that below
 * version 040. Same reason PhotoStoreOrientationTest reads the way it does.
 */
@RunWith(AndroidJUnit4::class)
class PhotoStoreSamplingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = PhotoStore(context)

    /** The longest edge every filed photo is meant to have. */
    private val budget = 1600

    /** A 12 MP mirrored selfie comes out upright and exactly at the budget. */
    @Test
    fun mirroredSelfieAtTwelveMegapixels() {
        // TRANSVERSE is what a typical upright phone writes for a
        // front-camera capture saved as previewed.
        val source = quadrantJpeg(4000, 3000, ExifInterface.ORIENTATION_TRANSVERSE)
        val result = runBlocking {
            store.prepare(source, "sampling-${UUID.randomUUID()}", "Site", Instant.EPOCH)
        }

        try {
            val bitmap = BitmapFactory.decodeFile(result.absolutePath)
            val w = bitmap.width
            val h = bitmap.height

            // Sampling must not change what gets filed: the budget edge is
            // what the storage estimate and the office's viewer assume.
            assertEquals("longest edge", budget, maxOf(w, h))
            assertTrue("transverse turns a landscape capture portrait", h > w)

            // The same quadrant map PhotoStoreOrientationTest asserts for
            // transverse. If sampling had run before the flip, or the
            // matrix had been applied to the wrong dimensions, these move.
            assertColor("top-left", YELLOW, bitmap.getPixel(w / 4, h / 4))
            assertColor("top-right", GREEN, bitmap.getPixel(w * 3 / 4, h / 4))
            assertColor("bottom-left", BLUE, bitmap.getPixel(w / 4, h * 7 / 10))
            assertColor("bottom-right", RED, bitmap.getPixel(w * 3 / 4, h * 7 / 10))
            bitmap.recycle()
        } finally {
            store.discard(result.absolutePath)
        }
    }

    /** A capture already inside the budget is not enlarged. */
    @Test
    fun captureInsideBudgetIsNotEnlarged() {
        val source = quadrantJpeg(1200, 900, ExifInterface.ORIENTATION_NORMAL)
        val result = runBlocking {
            store.prepare(source, "small-${UUID.randomUUID()}", "Site", Instant.EPOCH)
        }

        try {
            val bitmap = BitmapFactory.decodeFile(result.absolutePath)
            assertEquals("width kept", 1200, bitmap.width)
            assertEquals("height kept", 900, bitmap.height)
            bitmap.recycle()
        } finally {
            store.discard(result.absolutePath)
        }
    }

    private fun quadrantJpeg(width: Int, height: Int, orientation: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val midX = width / 2f
        val midY = height / 2f
        fun fill(color: Int, l: Float, t: Float, r: Float, b: Float) {
            paint.color = color
            canvas.drawRect(l, t, r, b, paint)
        }
        fill(RED, 0f, 0f, midX, midY)
        fill(GREEN, midX, 0f, width.toFloat(), midY)
        fill(BLUE, 0f, midY, midX, height.toFloat())
        fill(YELLOW, midX, midY, width.toFloat(), height.toFloat())

        val file = File.createTempFile("sampling-", ".jpg", context.cacheDir)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()

        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }

    private fun assertColor(where: String, expected: Int, actual: Int) {
        val near = abs(Color.red(expected) - Color.red(actual)) < 60 &&
            abs(Color.green(expected) - Color.green(actual)) < 60 &&
            abs(Color.blue(expected) - Color.blue(actual)) < 60
        assertTrue("$where: got #%06X".format(actual and 0xFFFFFF), near)
    }

    private companion object {
        val RED = Color.rgb(220, 30, 30)
        val GREEN = Color.rgb(30, 200, 30)
        val BLUE = Color.rgb(30, 30, 220)
        val YELLOW = Color.rgb(230, 220, 30)
    }
}
