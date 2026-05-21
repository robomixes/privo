// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.net.Uri
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.privateai.camera.R
import com.privateai.camera.util.PerspectiveTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * Self-hosted document-scanner capture flow — replaces ML Kit's
 * `GmsDocumentScanning` activity (Track A2). The screen runs entirely in
 * Privora's process, so we can drop `play-services-mlkit-document-scanner`
 * from the build entirely.
 *
 * Two modes, toggled internally:
 *   LIVE   — CameraX preview, shutter button, flash toggle, gallery picker.
 *   CROP   — captured image with 4 draggable corner handles for manual
 *            perspective correction. Apply warps via [PerspectiveTransform],
 *            Skip keeps the original photo, Retake throws it away.
 *
 * Pages accumulate in [pages]. The "Done" button surfaces only after the
 * first page lands and calls [onDone] with the list of cache-file URIs —
 * same shape ScannerScreen used to receive from ML Kit, so the rest of
 * the screen (page list, enhancement, PDF save) keeps working unchanged.
 *
 * Manual cropping was chosen over OpenCV auto-detection — the OpenCV
 * Android Maven distribution had rotted (quickbirdstudios is gone, the
 * official AAR isn't published to any registry). Manual drag covers the
 * common cases and ships in hours instead of days; an auto-detector can
 * be layered on later as its own track if we want it.
 */
@Composable
fun ScannerCaptureScreen(
    onDone: (List<Uri>) -> Unit,
    onCancel: () -> Unit,
    maxPages: Int = 10
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Captured page URIs (file:// in cacheDir). Order is the page order
    // ScannerScreen will receive — appended on each "Apply" / "Skip".
    val pages = remember { mutableStateListOf<Uri>() }
    // The captured (raw) bitmap awaiting crop. Null = LIVE mode.
    var pendingBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    if (pendingBitmap != null) {
        CornerCropOverlay(
            bitmap = pendingBitmap!!,
            onApply = { warped ->
                val bmp = pendingBitmap!!
                pendingBitmap = null
                scope.launch {
                    val uri = withContext(Dispatchers.IO) {
                        saveBitmapToCache(context, warped)
                    }
                    if (uri != null) pages.add(uri)
                    if (warped !== bmp) warped.recycle()
                    bmp.recycle()
                }
            },
            onSkip = {
                val bmp = pendingBitmap!!
                pendingBitmap = null
                scope.launch {
                    val uri = withContext(Dispatchers.IO) { saveBitmapToCache(context, bmp) }
                    if (uri != null) pages.add(uri)
                    bmp.recycle()
                }
            },
            onRetake = {
                pendingBitmap?.recycle()
                pendingBitmap = null
            }
        )
        return
    }

    if (!hasCameraPermission) {
        Box(Modifier.fillMaxSize().background(ComposeColor.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.scanner_capture_permission_required),
                    color = ComposeColor.White,
                    modifier = Modifier.padding(24.dp)
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.scanner_capture_grant_permission))
                }
            }
        }
        return
    }

    LiveCameraMode(
        pageCount = pages.size,
        maxPages = maxPages,
        onCaptured = { bmp -> pendingBitmap = bmp },
        onGalleryImport = { bmp -> pendingBitmap = bmp },
        onDone = {
            // Pass the immutable list to the host; clear locally so
            // backstack pops don't re-emit if recomposition happens.
            val out = pages.toList()
            pages.clear()
            onDone(out)
        },
        onCancel = {
            // Discard any pages already in flight — user explicitly cancelled.
            pages.forEach { try { File(it.path ?: "").delete() } catch (_: Exception) {} }
            pages.clear()
            onCancel()
        }
    )
}

@Composable
private fun LiveCameraMode(
    pageCount: Int,
    maxPages: Int,
    onCaptured: (android.graphics.Bitmap) -> Unit,
    onGalleryImport: (android.graphics.Bitmap) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var flashOn by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }
    // Camera handle is stashed so tap-to-focus can drive
    // cameraControl.startFocusAndMetering. Set in the DisposableEffect's
    // bindToLifecycle callback; nulled on dispose.
    val cameraRef = remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    // Brief focus-ring animation state — tap shows a yellow ring where the
    // user pressed for ~700 ms so they get visual confirmation the camera
    // is refocusing on that spot.
    var focusRing by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(focusRing) {
        if (focusRing != null) {
            kotlinx.coroutines.delay(700)
            focusRing = null
        }
    }

    // Gallery import path — Privora already uses this contract elsewhere
    // (Assistant attach). Selected URI is decoded into a Bitmap and
    // handed off to the same corner-crop flow as a fresh capture.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { ins ->
                            BitmapFactory.decodeStream(ins)
                        }
                    } catch (e: Exception) {
                        Log.w("ScannerCapture", "gallery decode failed: ${e.message}")
                        null
                    }
                }
                if (bmp != null) onGalleryImport(bmp)
            }
        }
    }

    // Re-bind CameraX whenever flash state changes — the ImageCapture
    // use case caches the flash mode at bind time.
    DisposableEffect(lifecycleOwner, flashOn) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                // CAPTURE_MODE_MAXIMIZE_QUALITY (slower but sharper) is the
                // right default for documents — text rendering benefits much
                // more from a few extra ms of shutter lag than from snappier
                // capture. Resolution bumped from 1920×2560 → 2592×3456 so
                // small fonts survive Tesseract OCR.
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .setTargetResolution(Size(2592, 3456))
                    .build()
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, capture
                )
                imageCaptureRef.value = capture
                cameraRef.value = camera
                // No manual focus seed — CameraX's Preview use case
                // defaults to CONTROL_AF_MODE_CONTINUOUS_PICTURE which
                // keeps hunting AF on its own. The earlier seed call
                // ran before PreviewView was laid out and locked the
                // focus point to (0, 0), defeating the default.
            } catch (e: Exception) {
                Log.e("ScannerCapture", "camera bind failed: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            try { ProcessCameraProvider.getInstance(context).get().unbindAll() } catch (_: Exception) {}
            cameraRef.value = null
        }
    }
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    Box(Modifier.fillMaxSize().background(ComposeColor.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Tap-to-focus overlay — separate transparent Box that sits ON TOP
        // of the AndroidView. Putting the gesture detector on the
        // AndroidView itself doesn't work: PreviewView consumes touches
        // for its own scale-type / built-in zoom logic and the Compose
        // pointerInput modifier never sees them.
        //
        // NO padding: the overlay must cover the exact same area as the
        // PreviewView so the gesture's `offset.x / offset.y` matches the
        // PreviewView pixel coordinates `meteringPointFactory` expects.
        // The control bars rendered later in source order naturally stack
        // on top and intercept their own taps before reaching us.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val cam = cameraRef.value ?: return@detectTapGestures
                        focusRing = offset
                        try {
                            val factory = previewView.meteringPointFactory
                            val point = factory.createPoint(offset.x, offset.y)
                            val action = androidx.camera.core.FocusMeteringAction.Builder(
                                point,
                                androidx.camera.core.FocusMeteringAction.FLAG_AF or
                                    androidx.camera.core.FocusMeteringAction.FLAG_AE
                            )
                                .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                                .build()
                            // Log the future result so we can see if the
                            // camera actually accepted the focus request.
                            val future = cam.cameraControl.startFocusAndMetering(action)
                            future.addListener({
                                try {
                                    val r = future.get()
                                    Log.i("ScannerCapture", "tap-focus result: focused=${r.isFocusSuccessful}")
                                } catch (e: Exception) {
                                    Log.w("ScannerCapture", "tap-focus future failed: ${e.message}")
                                }
                            }, java.util.concurrent.Executors.newSingleThreadExecutor())
                        } catch (e: Exception) {
                            Log.w("ScannerCapture", "tap focus failed: ${e.message}")
                        }
                    }
                }
        )

        // Brief yellow ring at the focus point — fades after 700 ms.
        focusRing?.let { p ->
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawCircle(color = ComposeColor.Yellow, radius = 64f, center = p, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
            }
        }

        // Doc viewfinder hint — soft white corner brackets occupying ~85%
        // of the frame, telling the user roughly where to put the
        // document. The brackets are decorative only; the capture itself
        // uses the whole frame and the user crops afterwards.
        androidx.compose.foundation.Canvas(
            Modifier.fillMaxSize().padding(top = 56.dp, bottom = 144.dp, start = 24.dp, end = 24.dp)
        ) {
            val w = size.width
            val h = size.height
            val cornerLen = minOf(w, h) * 0.08f
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
            val col = ComposeColor.White.copy(alpha = 0.6f)
            // 4 L-shaped corner brackets
            // TL
            drawLine(col, Offset(0f, 0f), Offset(cornerLen, 0f), strokeWidth = stroke.width)
            drawLine(col, Offset(0f, 0f), Offset(0f, cornerLen), strokeWidth = stroke.width)
            // TR
            drawLine(col, Offset(w - cornerLen, 0f), Offset(w, 0f), strokeWidth = stroke.width)
            drawLine(col, Offset(w, 0f), Offset(w, cornerLen), strokeWidth = stroke.width)
            // BL
            drawLine(col, Offset(0f, h - cornerLen), Offset(0f, h), strokeWidth = stroke.width)
            drawLine(col, Offset(0f, h), Offset(cornerLen, h), strokeWidth = stroke.width)
            // BR
            drawLine(col, Offset(w - cornerLen, h), Offset(w, h), strokeWidth = stroke.width)
            drawLine(col, Offset(w, h - cornerLen), Offset(w, h), strokeWidth = stroke.width)
        }

        // Top bar — Back + flash + page badge
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = ComposeColor.White)
            }
            if (pageCount > 0) {
                Text(
                    stringResource(R.string.scanner_capture_page_count, pageCount, maxPages),
                    color = ComposeColor.White,
                    modifier = Modifier
                        .background(ComposeColor.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            } else {
                Spacer(Modifier.size(1.dp))
            }
            IconButton(onClick = { flashOn = !flashOn }) {
                Icon(
                    if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = stringResource(R.string.scanner_capture_flash),
                    tint = if (flashOn) MaterialTheme.colorScheme.primary else ComposeColor.White
                )
            }
        }

        // Bottom controls — gallery / shutter / done
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery import
            IconButton(
                onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.size(56.dp).background(ComposeColor.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Default.PhotoLibrary, stringResource(R.string.scanner_capture_gallery), tint = ComposeColor.White)
            }
            // Shutter button — capture still photo via CameraX ImageCapture
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(ComposeColor.White)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(if (capturing) ComposeColor.Gray else ComposeColor.White)
            ) {
                IconButton(
                    onClick = {
                        if (capturing) return@IconButton
                        if (pageCount >= maxPages) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.scanner_capture_max_pages, maxPages),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            return@IconButton
                        }
                        val capture = imageCaptureRef.value ?: return@IconButton
                        capturing = true
                        captureToBitmap(context, capture) { bmp ->
                            capturing = false
                            if (bmp != null) onCaptured(bmp)
                        }
                    },
                    enabled = !capturing,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (capturing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
            // Done — only meaningful once at least one page is captured
            if (pageCount > 0) {
                IconButton(
                    onClick = onDone,
                    modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(Icons.Default.Check, stringResource(R.string.scanner_capture_done), tint = ComposeColor.White)
                }
            } else {
                Spacer(Modifier.size(56.dp))
            }
        }
    }
}

/**
 * Capture a single still image via CameraX [ImageCapture.takePicture],
 * decode the JPEG bytes into a Bitmap on a worker thread, and hand back
 * to the main thread. We use the in-memory `OnImageCapturedCallback`
 * variant rather than the file-based one so we don't have to manage a
 * temp file before the corner-crop step (the cache write happens after
 * the user confirms the quad).
 */
private fun captureToBitmap(
    context: Context,
    capture: ImageCapture,
    onResult: (android.graphics.Bitmap?) -> Unit
) {
    capture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val rot = image.imageInfo.rotationDegrees
                    if (bmp != null && rot != 0) {
                        val m = android.graphics.Matrix().apply { postRotate(rot.toFloat()) }
                        val rotated = android.graphics.Bitmap.createBitmap(
                            bmp, 0, 0, bmp.width, bmp.height, m, true
                        )
                        if (rotated !== bmp) bmp.recycle()
                        bmp = rotated
                    }
                    onResult(bmp)
                } catch (e: Exception) {
                    Log.e("ScannerCapture", "decode failed: ${e.message}", e)
                    onResult(null)
                } finally {
                    image.close()
                }
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e("ScannerCapture", "takePicture failed: ${exception.message}", exception)
                onResult(null)
            }
        }
    )
}

/**
 * 4-corner manual crop overlay. Shows the captured image filling its
 * frame; four draggable handles let the user outline the document
 * quadrilateral; Apply runs the perspective transform and emits the
 * warped bitmap; Skip emits the original; Retake discards everything.
 *
 * Coordinate system: we work in IMAGE-pixel space throughout. The
 * Image displays the bitmap with `ContentScale.Fit` and we compute the
 * pixel-↔-display mapping each gesture frame so users feel like they
 * drag the actual image content, not a transformed overlay.
 */
@Composable
private fun CornerCropOverlay(
    bitmap: android.graphics.Bitmap,
    onApply: (android.graphics.Bitmap) -> Unit,
    onSkip: () -> Unit,
    onRetake: () -> Unit
) {
    val context = LocalContext.current
    val bmpW = bitmap.width.toFloat()
    val bmpH = bitmap.height.toFloat()

    // Initial quad — inset 10% from each edge so users see immediately
    // they CAN move the handles, not just the document is already
    // perfectly aligned.
    val inset = 0.10f
    val corners = remember {
        mutableStateListOf(
            PointF(bmpW * inset, bmpH * inset),               // TL
            PointF(bmpW * (1 - inset), bmpH * inset),         // TR
            PointF(bmpW * (1 - inset), bmpH * (1 - inset)),   // BR
            PointF(bmpW * inset, bmpH * (1 - inset))          // BL
        )
    }

    // Layout cache — populated by the Image's onSize callback so the
    // gesture handler knows how to map screen pixels → image pixels.
    var displayedW by remember { mutableStateOf(0f) }
    var displayedH by remember { mutableStateOf(0f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var draggingIdx by remember { mutableStateOf(-1) }

    Box(Modifier.fillMaxSize().background(ComposeColor.Black)) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp, top = 56.dp)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(bmpW, bmpH) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                if (displayedW <= 0f || displayedH <= 0f) return@detectDragGestures
                                // Convert touch (display coords) → image pixel coords.
                                val px = (pos.x - offsetX) * bmpW / displayedW
                                val py = (pos.y - offsetY) * bmpH / displayedH
                                draggingIdx = nearestCorner(corners, px, py, bmpW, bmpH)
                            },
                            onDrag = { change, drag ->
                                if (draggingIdx < 0) return@detectDragGestures
                                if (displayedW <= 0f || displayedH <= 0f) return@detectDragGestures
                                change.consume()
                                val dxImg = drag.x * bmpW / displayedW
                                val dyImg = drag.y * bmpH / displayedH
                                val c = corners[draggingIdx]
                                corners[draggingIdx] = PointF(
                                    (c.x + dxImg).coerceIn(0f, bmpW),
                                    (c.y + dyImg).coerceIn(0f, bmpH)
                                )
                            },
                            onDragEnd = { draggingIdx = -1 },
                            onDragCancel = { draggingIdx = -1 }
                        )
                    }
            )
            // Compute the displayed-image rectangle inside the Box —
            // ContentScale.Fit centres the image and leaves matte bars.
            // We re-derive each composition so a rotation doesn't strand
            // stale measurements.
            val sourceAspect = bmpW / bmpH
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                val containerW = constraints.maxWidth.toFloat()
                val containerH = constraints.maxHeight.toFloat()
                val containerAspect = containerW / containerH
                if (sourceAspect > containerAspect) {
                    displayedW = containerW
                    displayedH = containerW / sourceAspect
                } else {
                    displayedH = containerH
                    displayedW = containerH * sourceAspect
                }
                offsetX = (containerW - displayedW) / 2f
                offsetY = (containerH - displayedH) / 2f

                // Overlay the quad + corner handles in display coords.
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val displayCorners = corners.map { c ->
                        Offset(
                            offsetX + c.x / bmpW * displayedW,
                            offsetY + c.y / bmpH * displayedH
                        )
                    }
                    // Quad edges
                    for (i in 0 until 4) {
                        val a = displayCorners[i]
                        val b = displayCorners[(i + 1) % 4]
                        drawLine(
                            color = ComposeColor.Yellow,
                            start = a, end = b,
                            strokeWidth = 3f
                        )
                    }
                    // Corner handles
                    displayCorners.forEach { p ->
                        drawCircle(color = ComposeColor.White, radius = 22f, center = p)
                        drawCircle(color = ComposeColor.Yellow, radius = 16f, center = p)
                    }
                }
            }
        }

        // Top bar — Retake (discards) + helper text
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onRetake) {
                Icon(Icons.Default.Refresh, stringResource(R.string.scanner_capture_retake), tint = ComposeColor.White)
            }
            Text(
                stringResource(R.string.scanner_crop_hint),
                color = ComposeColor.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .background(ComposeColor.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Spacer(Modifier.size(48.dp))
        }

        // Bottom action bar — Skip (use original) + Apply (warp)
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.scanner_crop_skip), color = ComposeColor.White)
            }
            Button(
                onClick = {
                    val warped = PerspectiveTransform.warpQuadToRect(bitmap, corners.toList())
                    onApply(warped)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.scanner_crop_apply))
            }
        }
    }
}

private fun nearestCorner(
    corners: List<PointF>,
    px: Float, py: Float,
    bmpW: Float, bmpH: Float
): Int {
    // Hit-tolerance ~6% of the longer image side so the handles are easy
    // to grab on a phone screen without grabbing the wrong neighbour.
    val tolerance = max(bmpW, bmpH) * 0.06f
    var bestIdx = -1
    var bestDist = Float.MAX_VALUE
    corners.forEachIndexed { i, c ->
        val d = (c.x - px) * (c.x - px) + (c.y - py) * (c.y - py)
        if (d < bestDist) { bestDist = d; bestIdx = i }
    }
    return if (bestIdx >= 0 && bestDist < tolerance * tolerance) bestIdx else -1
}

/** Encode [bmp] as JPEG into Privora's cache dir; return a file:// URI. */
private fun saveBitmapToCache(context: Context, bmp: android.graphics.Bitmap): Uri? {
    return try {
        val f = File(context.cacheDir, "scanner_page_${System.currentTimeMillis()}.jpg")
        FileOutputStream(f).use { out ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
        }
        Uri.fromFile(f)
    } catch (e: Exception) {
        Log.e("ScannerCapture", "saveBitmapToCache failed: ${e.message}", e)
        null
    }
}
