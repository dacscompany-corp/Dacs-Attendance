package com.dacs.attendance.ui.timeflow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dacs.attendance.R
import com.dacs.attendance.domain.photoOverlayCaption
import com.dacs.attendance.ui.theme.BorderDefault
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.MonoFamily
import com.dacs.attendance.ui.theme.PreviewBackdrop
import com.dacs.attendance.ui.theme.TextMuted
import java.io.File
import java.time.Instant
import java.util.concurrent.Executor
import kotlinx.coroutines.delay

/**
 * Screen 05. The selfie that proves attendance.
 *
 * FRONT camera and no gallery picker anywhere in the app -- a photo that
 * must be taken now, at the site, by the person holding the phone, is the
 * cheapest anti-spoofing measure available.
 *
 * The caption across the bottom of the preview is the SAME text that
 * [photoOverlayCaption] burns into the file, shown before the shutter
 * rather than after. A worker who can see what is about to be stamped on
 * their photo can catch a wrong project or a wrong phone clock while it
 * still costs one tap to fix, instead of discovering it in a report.
 */
@Composable
fun CameraCapture(
    onPhotoTaken: (File, Instant) -> Unit,
    modifier: Modifier = Modifier,
    projectName: String? = null,
    accent: Color = Green
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var capturing by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed -> granted = allowed }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // The preview caption has to move, or it is a lie by the time the
    // shutter fires. One tick a second is enough to keep the minute
    // honest and is nothing next to running the camera.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = Instant.now()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StepHeading(
            english = stringResource(R.string.flow_take_photo),
            tagalog = stringResource(R.string.flow_take_photo_tl)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(Dimens.RadiusLarge))
                .background(PreviewBackdrop),
            contentAlignment = Alignment.Center
        ) {
            if (granted) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).also { view ->
                            view.scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                    update = { view ->
                        bindCamera(view, lifecycleOwner, imageCapture)
                    }
                )

                // The design's framing rectangle: where to put your face.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(22.dp)
                        .border(
                            2.dp,
                            Color.White.copy(alpha = 0.28f),
                            RoundedCornerShape(14.dp)
                        )
                )

                projectName?.let { project ->
                    Text(
                        text = photoOverlayCaption(project, now),
                        fontFamily = MonoFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        // One line, ellipsised rather than clipped. The
                        // burned-in copy shrinks to fit instead (see
                        // fitTextSize); on a narrow preview an honest "…"
                        // beats a caption sliced mid-character.
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            // A scrim, not a solid bar: the caption has to
                            // stay readable over a bright sky and over a
                            // dark wall, and the photo behind it is the
                            // thing the worker is actually framing.
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            } else {
                // Not a dead end: the launcher above already asked, and
                // this explains why the screen is empty if they refused.
                Text(
                    text = stringResource(R.string.camera_permission_needed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(Dimens.ScreenPadding)
                )
            }
        }

        Text(
            text = stringResource(R.string.flow_camera_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center
        )

        ShutterButton(
            enabled = granted && !capturing,
            capturing = capturing,
            accent = accent,
            onClick = {
                capturing = true
                imageCapture.takeInto(context) { file, takenAt ->
                    capturing = false
                    if (file != null && takenAt != null) onPhotoTaken(file, takenAt)
                }
            }
        )
    }
}

/**
 * The design's "malaking bilog", and the hint above it says so by name.
 *
 * A circle carries no label, so the bilingual text every other action in
 * this app shows moves into [contentDescription] -- a worker using
 * TalkBack still hears "Take photo / Kumuha ng litrato".
 */
@Composable
private fun ShutterButton(
    enabled: Boolean,
    capturing: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val ring = if (enabled || capturing) accent else BorderDefault

    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            // clickable BEFORE the ring and the inset, so the whole 96dp
            // is the target. Hanging it on the inner disc instead would
            // make the outer 11dp look pressable and do nothing.
            .clickable(enabled = enabled, onClick = onClick)
            .border(5.dp, ring, CircleShape)
            .semantics {
                contentDescription = "Take photo / Kumuha ng litrato"
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        if (capturing) {
            CircularProgressIndicator(color = accent)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(ring)
            )
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun bindCamera(
    view: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    imageCapture: ImageCapture
) {
    val providerFuture = ProcessCameraProvider.getInstance(view.context)
    providerFuture.addListener({
        runCatching {
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = view.surfaceProvider
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                imageCapture
            )
        }
    }, ContextCompat.getMainExecutor(view.context))
}

/**
 * Captures to the app's cache. Cache, not files: an unsubmitted photo is
 * disposable, and on a 16GB phone a year of forgotten selfies is not.
 *
 * The timestamp is taken at the shutter, in this process -- it is what
 * becomes captured_at, and therefore what the record says about when the
 * worker was on site.
 */
private fun ImageCapture.takeInto(
    context: Context,
    onResult: (File?, Instant?) -> Unit
) {
    val file = File.createTempFile("attendance-", ".jpg", context.cacheDir)
    val output = ImageCapture.OutputFileOptions.Builder(file).build()
    val executor: Executor = ContextCompat.getMainExecutor(context)

    takePicture(
        output,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                onResult(file, Instant.now())
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                onResult(null, null)
            }
        }
    )
}
