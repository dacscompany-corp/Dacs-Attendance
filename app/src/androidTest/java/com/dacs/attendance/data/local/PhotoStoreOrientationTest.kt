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
 * The stored photo must look the way the EXIF orientation says it looks.
 *
 * All EIGHT orientations, not the three rotations: a front-camera capture
 * saved as previewed is tagged with a FLIPPED orientation (usually
 * TRANSVERSE), and a decode that only knows rotations files it sideways
 * and un-mirrored -- the opposite of what the worker saw.
 *
 * The source is 200x100 with a different colour in each quadrant, so any
 * wrong rotation OR a missing flip lands the wrong colour in a corner.
 */
@RunWith(AndroidJUnit4::class)
class PhotoStoreOrientationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = PhotoStore(context)

    private data class Quadrants(val tl: Int, val tr: Int, val bl: Int, val br: Int)

    @Test fun normal() = check(ExifInterface.ORIENTATION_NORMAL, landscape = true,
        Quadrants(tl = RED, tr = GREEN, bl = BLUE, br = YELLOW))

    @Test fun flipHorizontal() = check(ExifInterface.ORIENTATION_FLIP_HORIZONTAL, landscape = true,
        Quadrants(tl = GREEN, tr = RED, bl = YELLOW, br = BLUE))

    @Test fun rotate180() = check(ExifInterface.ORIENTATION_ROTATE_180, landscape = true,
        Quadrants(tl = YELLOW, tr = BLUE, bl = GREEN, br = RED))

    @Test fun flipVertical() = check(ExifInterface.ORIENTATION_FLIP_VERTICAL, landscape = true,
        Quadrants(tl = BLUE, tr = YELLOW, bl = RED, br = GREEN))

    @Test fun transpose() = check(ExifInterface.ORIENTATION_TRANSPOSE, landscape = false,
        Quadrants(tl = RED, tr = BLUE, bl = GREEN, br = YELLOW))

    @Test fun rotate90() = check(ExifInterface.ORIENTATION_ROTATE_90, landscape = false,
        Quadrants(tl = BLUE, tr = RED, bl = YELLOW, br = GREEN))

    // The one a mirrored front-camera capture actually produces on a
    // typical phone held upright: sensor ROTATE_270, then flipped.
    @Test fun transverse() = check(ExifInterface.ORIENTATION_TRANSVERSE, landscape = false,
        Quadrants(tl = YELLOW, tr = GREEN, bl = BLUE, br = RED))

    @Test fun rotate270() = check(ExifInterface.ORIENTATION_ROTATE_270, landscape = false,
        Quadrants(tl = GREEN, tr = YELLOW, bl = RED, br = BLUE))

    private fun check(orientation: Int, landscape: Boolean, expected: Quadrants) {
        val source = quadrantJpeg(orientation)
        val result = runBlocking {
            store.prepare(source, "test-${UUID.randomUUID()}", "Site", Instant.EPOCH)
        }
        try {
            val bitmap = BitmapFactory.decodeFile(result.absolutePath)
            val (w, h) = bitmap.width to bitmap.height
            assertEquals("landscape", landscape, w > h)

            // 0.25/0.75 across; 0.25/0.70 down, to stay clear of the
            // caption band burned along the bottom 14%.
            assertColor("top-left", expected.tl, bitmap.getPixel(w / 4, h / 4))
            assertColor("top-right", expected.tr, bitmap.getPixel(w * 3 / 4, h / 4))
            assertColor("bottom-left", expected.bl, bitmap.getPixel(w / 4, h * 7 / 10))
            assertColor("bottom-right", expected.br, bitmap.getPixel(w * 3 / 4, h * 7 / 10))
            bitmap.recycle()
        } finally {
            store.discard(result.absolutePath)
        }
    }

    private fun quadrantJpeg(orientation: Int): File {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        fun fill(color: Int, l: Float, t: Float, r: Float, b: Float) {
            paint.color = color
            canvas.drawRect(l, t, r, b, paint)
        }
        fill(RED, 0f, 0f, 100f, 50f)
        fill(GREEN, 100f, 0f, 200f, 50f)
        fill(BLUE, 0f, 50f, 100f, 100f)
        fill(YELLOW, 100f, 50f, 200f, 100f)

        val file = File.createTempFile("orientation-", ".jpg", context.cacheDir)
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
        assertTrue(
            "$where: expected ${name(expected)}, got #%06X".format(actual and 0xFFFFFF),
            near
        )
    }

    private fun name(color: Int) = when (color) {
        RED -> "red"
        GREEN -> "green"
        BLUE -> "blue"
        YELLOW -> "yellow"
        else -> "#%06X".format(color and 0xFFFFFF)
    }

    private companion object {
        val RED = Color.rgb(220, 30, 30)
        val GREEN = Color.rgb(30, 200, 30)
        val BLUE = Color.rgb(30, 30, 220)
        val YELLOW = Color.rgb(230, 220, 30)
    }
}
