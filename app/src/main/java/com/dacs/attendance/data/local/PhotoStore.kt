package com.dacs.attendance.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import com.dacs.attendance.domain.fitTextSize
import com.dacs.attendance.domain.photoOverlayCaption
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Longest edge, per the storage budget in the design's section 4.7. */
private const val MAX_EDGE_PX = 1600
private const val JPEG_QUALITY = 80

/**
 * Turns a freshly captured photo into the file that gets uploaded:
 * rotated upright, caption burned in, compressed, stored app-private.
 *
 * Every step here exists for a stated reason:
 *
 * - **Burned in, not overlaid.** The caption has to survive export and
 *   download; an overlay drawn at display time proves nothing once the
 *   image leaves the app.
 * - **App-private, not MediaStore.** Attendance photos are evidence, not
 *   the worker's gallery, and they should not appear in it.
 * - **~300 KB.** 20 workers x 2 photos x 250 workdays is 10,000 photos a
 *   year; at full camera resolution that is a storage bill nobody
 *   budgeted for.
 */
@Singleton
class PhotoStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Where queued photos live until their upload is confirmed. */
    private val directory: File
        get() = File(context.filesDir, "attendance-photos").apply { mkdirs() }

    /**
     * Returns a new file, app-private and ready to upload. The source
     * (a CameraX capture in the cache) is deleted afterwards -- it has
     * no caption and should never be the thing that gets sent.
     */
    suspend fun prepare(
        source: File,
        eventId: String,
        projectName: String,
        capturedAt: Instant
    ): File = withContext(Dispatchers.IO) {
        val upright = decodeUpright(source)
        val scaled = scaleToBudget(upright)
        if (scaled !== upright) upright.recycle()

        drawCaption(scaled, photoOverlayCaption(projectName, capturedAt))

        val target = File(directory, "$eventId.jpg")
        target.outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        scaled.recycle()
        source.delete()
        target
    }

    /** Called only once the upload is confirmed -- never before. */
    fun discard(path: String) {
        runCatching { File(path).delete() }
    }

    /**
     * CameraX records rotation in EXIF rather than rotating the pixels.
     * Ignoring it burns the caption sideways across half the phones in
     * the field.
     */
    private fun decodeUpright(source: File): Bitmap {
        val bitmap = BitmapFactory.decodeFile(source.absolutePath)
            ?: error("Could not decode captured photo")

        val degrees = when (
            ExifInterface(source.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap

        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        val rotated =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleToBudget(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_EDGE_PX) return bitmap

        val factor = MAX_EDGE_PX.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * factor).toInt(),
            (bitmap.height * factor).toInt(),
            true
        )
    }

    /**
     * The design's bottom gradient with monospaced text over it. The
     * gradient is what keeps the caption readable over a bright sky or a
     * white wall -- both common on a construction site at 07:45.
     */
    private fun drawCaption(bitmap: Bitmap, caption: String) {
        val canvas = Canvas(bitmap)
        val bandHeight = bitmap.height * 0.14f
        val top = bitmap.height - bandHeight

        canvas.drawRect(
            0f,
            top,
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f, top, 0f, bitmap.height.toFloat(),
                    Color.TRANSPARENT, Color.argb(190, 0, 0, 0),
                    Shader.TileMode.CLAMP
                )
            }
        )

        val margin = bitmap.width * 0.04f
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(bitmap.width * 0.006f, 0f, 0f, Color.BLACK)
        }

        // Shrink until the WHOLE caption fits. Letting it overflow costs
        // the "PM" off the end of the time, which is precisely the
        // ambiguity the burned-in stamp exists to remove.
        text.textSize = fitTextSize(
            preferred = bitmap.width * 0.038f,
            maxWidth = bitmap.width - margin * 2,
            measure = { size ->
                text.textSize = size
                text.measureText(caption)
            }
        )
        canvas.drawText(caption, margin, bitmap.height - bandHeight * 0.32f, text)
    }
}
