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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dacs.attendance.R
import com.dacs.attendance.ui.components.PrimaryActionButton
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.TextMuted
import java.io.File
import java.time.Instant
import java.util.concurrent.Executor

/**
 * Screen 05. The selfie that proves attendance.
 *
 * FRONT camera and no gallery picker anywhere in the app -- a photo that
 * must be taken now, at the site, by the person holding the phone, is the
 * cheapest anti-spoofing measure available. The burned-in overlay the
 * design shows lands in B4 with the watermarker.
 */
@Composable
fun CameraCapture(
    onPhotoTaken: (File, Instant) -> Unit,
    modifier: Modifier = Modifier
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

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.GapMedium)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
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
            } else {
                // Not a dead end: the launcher above already asked, and
                // this explains why the screen is empty if they refused.
                Text(
                    text = stringResource(R.string.camera_permission_needed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(Dimens.ScreenPadding)
                )
            }
        }

        PrimaryActionButton(
            english = stringResource(R.string.action_take_photo),
            tagalog = stringResource(R.string.action_take_photo_tl),
            enabled = granted,
            loading = capturing,
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
