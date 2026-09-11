package com.dacs.attendance.ui.timeflow

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dacs.attendance.R
import com.dacs.attendance.domain.photoOverlayCaptionLines
import com.dacs.attendance.ui.components.FlowHeader
import com.dacs.attendance.ui.theme.Dimens
import com.dacs.attendance.ui.theme.Green
import com.dacs.attendance.ui.theme.MonoFamily
import com.dacs.attendance.ui.theme.PreviewBackdrop
import java.io.File
import java.time.Instant
import java.util.concurrent.Executor
import kotlinx.coroutines.delay

/**
 * Step 2. The photo that proves attendance.
 *
 * No gallery picker anywhere in the app -- a photo that must be taken
 * now, at the site, is the cheapest anti-spoofing measure available.
 *
 * FRONT camera by default, BACK one tap away. This screen was front-only
 * at first, to keep it a selfie; the design's camera-switch was added
 * back on request (2026-09-11). The in-app capture above is what still
 * holds the line. See the note on [ShutterRow].
 *
 * The front camera is SAVED AS PREVIEWED, i.e. mirrored. PreviewView
 * mirrors it and ImageCapture does not, so without that the filed photo
 * is the reverse of the one the worker lined up and approved.
 *
 * The caption at the top of the preview is the SAME text that gets burned
 * into the file, shown before the shutter rather than after. A worker who
 * can see what is about to be stamped on their photo can catch a wrong
 * project or a wrong phone clock while it still costs one tap to fix,
 * instead of discovering it in a report.
 */
@Composable
fun CameraStep(
    projectName: String?,
    onPhotoTaken: (File, Instant) -> Unit,
    onBack: () -> Unit,
    /**
     * Leave the flow entirely, back to Home.
     *
     * Distinct from [onBack], which steps back to the picker. A worker
     * who has decided NOT to grant the camera has nothing to go back
     * FOR -- every earlier step leads here again. Without this the
     * screen is a dead end held open by a shutter that cannot fire.
     */
    onGiveUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var capturing by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    // Front unless the phone has none. Hard-coding the front selector
    // left a phone without one staring at a black preview.
    val hasFront = remember { context.hasCameraLens(PackageManager.FEATURE_CAMERA_FRONT) }
    val hasBack = remember { context.hasCameraLens(PackageManager.FEATURE_CAMERA) }
    var lensFacing by rememberSaveable {
        mutableIntStateOf(
            if (hasFront) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        )
    }

    // ── CAMERA AND LOCATION ARE ASKED FOR TOGETHER, here.
    //
    // Location is not used until SUBMIT, three steps later. It is
    // requested at the camera because that is where a worker already
    // expects to grant something, and because asking again on the last
    // screen -- with a photo taken and a description typed -- is where a
    // denial costs the most.
    //
    // It is NOT optional any more. Since 0069 a refused permission
    // refuses the attendance, so an app that never asked would refuse
    // every Time In on every device: the manifest entry alone grants
    // nothing on Android 6 and later.
    //
    // The CAMERA result is the only one that gates this screen. A worker
    // who declines location can still take the photo and reach Describe,
    // where the refusal is explained in words they can act on, rather
    // than being stopped here with a camera they never got to use.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> granted = results[Manifest.permission.CAMERA] ?: granted }

    // ── RE-CHECK WHEN THE APP COMES BACK.
    //
    // The launcher below asks ONCE. After a permanent denial Android
    // never shows the prompt again, so the only route is Settings -- and
    // returning from Settings does not recompose this screen on its own.
    // Without this the worker grants the permission, comes back, and the
    // camera is still dead with no way to shift it. That is a trap, and
    // it is exactly what the message below used to promise it was not.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = context.hasCameraPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        val wanted = buildList {
            if (!granted) add(Manifest.permission.CAMERA)
            if (!context.hasLocationPermission()) {
                // FINE first: coarse is accurate to roughly 1-3 km and
                // cannot tell a 150 m site fence from the next barangay.
                // Android 12 and later may still hand back approximate
                // only, which degrades to a flagged record rather than a
                // refusal.
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray())
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

    Column(modifier = modifier.fillMaxSize().background(PreviewBackdrop)) {
        FlowHeader(
            step = 2,
            total = 4,
            accent = Color.White,
            onBack = onBack,
            inlineTitle = stringResource(R.string.flow_take_photo),
            inlineSubtitle = stringResource(R.string.flow_take_photo_sub),
            dark = true,
            modifier = Modifier.padding(
                start = Dimens.ScreenPadding,
                end = Dimens.ScreenPadding,
                top = 6.dp,
                bottom = 14.dp
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(Dimens.RadiusPhoto))
                .background(Color(0xFF1E211E)),
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
                    // Reads lensFacing, so a switch re-runs this and rebinds.
                    update = { view -> bindCamera(view, lifecycleOwner, imageCapture, lensFacing) }
                )

                FaceGuide(Modifier.fillMaxSize())

                projectName?.let { project ->
                    val (name, stamp) = photoOverlayCaptionLines(project, now)
                    StampChip(
                        name = name,
                        stamp = stamp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(14.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.flow_camera_hint),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp)
                )
            } else {
                // The message alone was advice with nothing to act on.
                // After a permanent denial the app cannot re-prompt, so
                // the button is the ONLY way forward -- and the observer
                // above is what makes coming back from it work.
                Column(
                    modifier = Modifier.padding(Dimens.SheetPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.camera_permission_needed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.action_open_settings),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // The other honest answer. Returning to Home is NOT
                    // automatic on a denial: doing that would whisk the
                    // worker away before they had a chance to read the
                    // Open Settings route, which is the one thing that
                    // actually fixes this. Offer both, let them choose.
                    TextButton(onClick = onGiveUp) {
                        Text(
                            text = stringResource(R.string.action_not_now),
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        ShutterRow(
            enabled = granted && !capturing,
            capturing = capturing,
            onClick = {
                capturing = true
                imageCapture.takeInto(
                    context,
                    mirror = lensFacing == CameraSelector.LENS_FACING_FRONT
                ) { file, takenAt ->
                    capturing = false
                    if (file != null && takenAt != null) onPhotoTaken(file, takenAt)
                }
            },
            // Only offered when there is something to switch TO.
            onSwitchCamera = if (hasFront && hasBack) {
                {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        CameraSelector.LENS_FACING_BACK
                    } else {
                        CameraSelector.LENS_FACING_FRONT
                    }
                }
            } else {
                null
            }
        )
    }
}

/**
 * The dashed oval: where to put your face.
 *
 * Drawn rather than bordered because Compose has no dashed
 * Modifier.border, and a solid ring reads as a mask the photo will be
 * cropped to -- which it is not. The dashes say "aim here", not "this is
 * the frame".
 */
@Composable
private fun FaceGuide(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // PROPORTIONAL, not the design's fixed 270dp. The mock draws the
        // preview at one size; a real one is whatever is left after the
        // header and the shutter, and it changes with the phone. Pinning
        // the oval to dp put it half off the bottom of a tall preview.
        val ovalWidth = size.width * 0.71f
        val ovalHeight = minOf(ovalWidth * 1.45f, size.height * 0.56f)
        if (ovalHeight <= 0f) return@Canvas

        drawOval(
            color = Color.White.copy(alpha = 0.3f),
            topLeft = Offset(
                x = (size.width - ovalWidth) / 2f,
                // Above centre: a face held at arm's length sits in the
                // top half of the frame, and an oval pinned to the middle
                // makes people lower the phone.
                y = size.height * 0.12f
            ),
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(12.dp.toPx(), 10.dp.toPx())
                )
            )
        )
    }
}

/** The project and stamp about to be burned in, over the live preview. */
@Composable
private fun StampChip(name: String, stamp: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Color.Black.copy(alpha = 0.45f),
                RoundedCornerShape(Dimens.RadiusField)
            )
            .padding(horizontal = 13.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Apartment,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(17.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            // Two lines, because the joined caption does not fit across a
            // phone and it was the TIME that got cut. The name may
            // ellipsise; the stamp never.
            Text(
                text = name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stamp,
                fontFamily = MonoFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

/**
 * The shutter, with the camera-switch on its right.
 *
 * The design flanks it with a flash toggle and a camera-switch. The
 * switch is built; the flash is not, deliberately: a flash control on a
 * front camera is inert on most of the phones this app targets, and a
 * button that does nothing on the screen where a worker is already
 * unsure is worse than no button.
 *
 * [onSwitchCamera] is null on a phone with only one camera, and the
 * button is simply not there. Three equal slots, so the shutter keeps
 * the design's size, ring and centring whether it is or not.
 */
@Composable
private fun ShutterRow(
    enabled: Boolean,
    capturing: Boolean,
    onClick: () -> Unit,
    onSwitchCamera: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Dimens.ScreenPadding,
                end = Dimens.ScreenPadding,
                top = 18.dp,
                bottom = Dimens.BottomPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(Dimens.Shutter)
                .clip(CircleShape)
                // clickable BEFORE the ring and the inset, so the whole
                // 78dp is the target. Hanging it on the inner disc instead
                // would make the outer ring look pressable and do nothing.
                .clickable(enabled = enabled, onClick = onClick)
                .border(4.dp, Color.White.copy(alpha = if (enabled) 0.85f else 0.3f), CircleShape)
                .padding(5.dp)
                .semantics {
                    contentDescription = "Take photo"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (capturing) {
                    CircularProgressIndicator(
                        color = Green,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(30.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = Green,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (onSwitchCamera != null) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        // Not mid-capture: rebinding under a shutter that
                        // has already fired loses the photo.
                        .clickable(enabled = enabled, onClick = onSwitchCamera)
                        .background(Color.White.copy(alpha = if (enabled) 0.16f else 0.06f))
                        .semantics {
                            contentDescription = "Switch camera"
                            role = Role.Button
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cameraswitch,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/** Either grade counts. Coarse is poor, but poor is flagged, not refused. */
private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.hasCameraLens(feature: String): Boolean =
    packageManager.hasSystemFeature(feature)

private fun bindCamera(
    view: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    imageCapture: ImageCapture,
    lensFacing: Int
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
                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
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
 *
 * [mirror] only tags the EXIF; the pixels are flipped later, by PhotoStore
 * (and on the review screen, by Coil). ImageCapture has no mirror mode
 * of its own -- its setMirrorMode throws.
 */
private fun ImageCapture.takeInto(
    context: Context,
    mirror: Boolean,
    onResult: (File?, Instant?) -> Unit
) {
    val file = File.createTempFile("attendance-", ".jpg", context.cacheDir)
    val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = mirror }
    val output = ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build()
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
