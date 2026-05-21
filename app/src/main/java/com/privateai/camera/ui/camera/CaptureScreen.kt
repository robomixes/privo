// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.Recorder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privateai.camera.R
import kotlinx.coroutines.Job
import androidx.core.content.ContextCompat
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun CaptureScreen(onBack: () -> Unit, onPhotoTap: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Camera permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    // Audio permission (for video recording)
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    // Camera setup
    val hasFrontCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    }
    var useFrontCamera by remember { mutableStateOf(false) }
    val cameraSelector = if (useFrontCamera) {
        CameraSelector.DEFAULT_FRONT_CAMERA
    } else {
        CameraSelector.DEFAULT_BACK_CAMERA
    }

    // Photo mode state
    val frameLock = remember { Object() }
    var latestFrame by remember { mutableStateOf<Bitmap?>(null) }
    val crypto = remember { CryptoManager(context).also { it.initialize() } }
    val vault = remember { VaultRepository(context, crypto) }
    var lastThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var mediaCount by remember { mutableIntStateOf(0) }
    var isCapturing by remember { mutableStateOf(false) }
    var showFlash by remember { mutableStateOf(false) }
    var frameCount by remember { mutableIntStateOf(0) }

    // Last captured item — for direct preview from thumbnail
    var lastCapturedItem by remember { mutableStateOf<com.privateai.camera.security.VaultPhoto?>(null) }
    var showViewer by remember { mutableStateOf(false) }
    var viewerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var viewerVideoFile by remember { mutableStateOf<java.io.File?>(null) }

    // Video mode state
    var isVideoMode by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isSavingVideo by remember { mutableStateOf(false) }

    // Zoom (pinch-to-zoom). Captured from CameraPreview's onCameraBound; the
    // float state mirrors the live zoomRatio so we can show "1.5x" overlay
    // and clamp gestures to [minZoomRatio, maxZoomRatio].
    var cameraRef by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var showZoomIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(showZoomIndicator) {
        if (showZoomIndicator) { delay(900); showZoomIndicator = false }
    }

    // Face count (lightweight ML Kit face detection)
    var faceCount by remember { mutableIntStateOf(0) }
    // Long-press-to-record from photo mode (hold shutter = quick video)
    var isHoldRecording by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }

    // Countdown timer
    val prefs = remember { context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE) }
    var countdownEnabled by remember { mutableStateOf(false) }
    var countdownDuration by remember { mutableIntStateOf(prefs.getInt("countdown_seconds", 5)) }
    var countdownVideoDuration by remember { mutableIntStateOf(prefs.getInt("countdown_video_seconds", 30)) }
    var countdownValue by remember { mutableIntStateOf(0) } // 0 = not counting
    var isCountingDown by remember { mutableStateOf(false) }
    var countdownForVideo by remember { mutableStateOf(false) } // true = video, false = photo

    // Shutter button color animation (photo mode)
    val shutterColor by animateColorAsState(
        targetValue = if (isCapturing) Color.Gray else Color.White,
        animationSpec = tween(150),
        label = "shutter"
    )

    // Recording timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDurationSec = 0
            while (isRecording) {
                delay(1000)
                recordingDurationSec++
                if (recordingDurationSec >= 600) { // 10 minute max
                    activeRecording?.stop()
                    activeRecording = null
                }
            }
        }
    }

    // Clean up temp recording files on launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            context.cacheDir.listFiles()?.filter {
                it.name.startsWith("rec_") || it.name.startsWith("playback_")
            }?.forEach { it.delete() }
        }
    }

    // Clean up on dispose. Also pauses Gemma background indexing while
    // the camera UI is on screen — Gemma's ~1 GB RAM + GPU usage makes the
    // live preview lag if it runs during a capture session. Photo IDs
    // enqueued while paused are buffered in GemmaIndexingManager and
    // drained when the camera leaves composition.
    DisposableEffect(Unit) {
        com.privateai.camera.service.GemmaIndexingManager.pause()
        onDispose {
            activeRecording?.stop()
            latestFrame?.recycle()
            lastThumbnail?.recycle()
            com.privateai.camera.service.GemmaIndexingManager.resume(context)
        }
    }

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text(stringResource(R.string.grant_camera_permission))
            }
        }
        LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.CAMERA) }
        return
    }

    fun capturePhoto() {
        if (isCapturing) return
        val bitmap: Bitmap
        synchronized(frameLock) {
            val frame = latestFrame ?: return
            bitmap = frame.copy(Bitmap.Config.ARGB_8888, false)
        }

        isCapturing = true
        showFlash = true

        scope.launch {
            val thumbSize = 120
            val scale = thumbSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            val thumb = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
            lastThumbnail?.recycle()
            lastThumbnail = thumb

            withContext(Dispatchers.IO) {
                try {
                    val saved = vault.savePhoto(bitmap)
                    lastCapturedItem = saved
                    bitmap.recycle()
                    mediaCount++
                    // Background Gemma describe + tag (Track C). Opt-in via
                    // Settings → "Auto-tag new photos with AI" (default OFF).
                    if (com.privateai.camera.ui.settings.isAutoAiTagEnabled(context)
                        && com.privateai.camera.bridge.GemmaRunner.isAvailable(context)) {
                        com.privateai.camera.service.GemmaIndexingManager.enqueue(context, saved.id)
                    }
                } catch (e: Exception) {
                    bitmap.recycle()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.save_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                    }
                }
            }

            delay(100)
            showFlash = false
            delay(50)
            isCapturing = false
        }
    }

    @Suppress("MissingPermission")
    fun startRecording() {
        val vc = videoCapture ?: return
        if (isRecording || isSavingVideo) return

        val tempFile = File(context.cacheDir, "rec_${System.currentTimeMillis()}.mp4")
        val outputOptions = FileOutputOptions.Builder(tempFile)
            .setFileSizeLimit(150L * 1024 * 1024)
            .build()

        val pendingRecording = vc.output
            .prepareRecording(context, outputOptions)
            .apply {
                if (hasAudioPermission) withAudioEnabled()
            }

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Finalize -> {
                    isRecording = false
                    if (!event.hasError()) {
                        isSavingVideo = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    val saved = vault.saveVideo(tempFile, com.privateai.camera.security.VaultCategory.CAMERA)
                                    val thumb = vault.loadThumbnail(saved)
                                    withContext(Dispatchers.Main) {
                                        lastThumbnail?.recycle()
                                        lastThumbnail = thumb
                                        lastCapturedItem = saved
                                        mediaCount++
                                        isSavingVideo = false
                                    }
                                } catch (e: Exception) {
                                    tempFile.delete()
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.save_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                                        isSavingVideo = false
                                    }
                                }
                            }
                        }
                    } else {
                        tempFile.delete()
                        Toast.makeText(context, context.getString(R.string.recording_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        isRecording = true
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    // Countdown timer effect
    LaunchedEffect(isCountingDown) {
        if (isCountingDown) {
            countdownValue = countdownDuration
            while (countdownValue > 0) {
                delay(1000)
                countdownValue--
            }
            isCountingDown = false
            countdownEnabled = false
            if (countdownForVideo) {
                startRecording()
                // Auto-stop after configured duration for countdown videos
                val videoDurationMs = countdownVideoDuration * 1000L
                scope.launch {
                    delay(videoDurationMs)
                    if (isRecording && !isHoldRecording) {
                        stopRecording()
                    }
                }
            } else {
                capturePhoto()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview — wrap in pinch-to-zoom gesture detector. We bind to
        // (cameraRef, useFrontCamera) so the gesture closure picks up the new
        // Camera instance after a flip; switching cameras re-binds CameraX
        // and the prior cameraControl handle becomes unusable.
        CameraPreview(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(cameraRef) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val cam = cameraRef ?: return@detectTransformGestures
                        if (zoom == 1f) return@detectTransformGestures
                        val state = cam.cameraInfo.zoomState.value ?: return@detectTransformGestures
                        val target = (state.zoomRatio * zoom).coerceIn(state.minZoomRatio, state.maxZoomRatio)
                        cam.cameraControl.setZoomRatio(target)
                        zoomRatio = target
                        showZoomIndicator = true
                    }
                },
            cameraSelector = cameraSelector,
            isVideoMode = isVideoMode,
            onCameraBound = { cam, _ ->
                cameraRef = cam
                zoomRatio = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
            },
            onFrameAnalyzed = { bitmap ->
                frameCount++
                if (frameCount % 5 != 0) {
                    bitmap.recycle()
                    return@CameraPreview
                }

                // Keep latest frame for photo capture (photo mode only)
                if (!isVideoMode) {
                    synchronized(frameLock) {
                        latestFrame?.recycle()
                        latestFrame = bitmap
                    }
                }

                // Lightweight face count using the ONNX detector (gated by
                // device tier). Synchronous CPU call — for live preview at
                // 5-frame intervals on Pixel-class hardware this lands at
                // ~10-20ms per call, well under one preview frame's budget.
                // Track A1.2: replaced ML Kit FaceDetection here.
                if (com.privateai.camera.service.DeviceProfiler.isFaceCountEnabled(context, isVideoMode)) {
                    try {
                        val faces = com.privateai.camera.bridge.FaceDetectorHolder.get(context).detect(bitmap)
                        faceCount = faces.size
                    } catch (_: Exception) {
                        faceCount = 0
                    } finally {
                        if (isVideoMode) bitmap.recycle()
                    }
                } else {
                    faceCount = 0
                    if (isVideoMode) bitmap.recycle()
                }
            },
            onVideoCaptureReady = { vc -> videoCapture = vc }
        )

        // White flash overlay on capture (photo mode)
        if (showFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.6f))
            )
        }

        // Pinch-to-zoom indicator — fades in on gesture, auto-clears after 900ms.
        AnimatedVisibility(
            visible = showZoomIndicator,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 160.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    "%.1fx".format(zoomRatio),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Countdown overlay — big numbers centered on screen, tap to cancel
        if (isCountingDown || countdownValue > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable {
                        isCountingDown = false
                        countdownValue = 0
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (countdownValue > 0) "$countdownValue" else "GO",
                        fontSize = 140.sp,
                        fontWeight = FontWeight.Black,
                        color = if (countdownValue > 0) Color.White else Color(0xFF4CAF50)
                    )
                    if (countdownValue > 0) {
                        Text(
                            text = if (countdownForVideo) stringResource(R.string.countdown_video, countdownVideoDuration) else stringResource(R.string.countdown_photo),
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Recording timer (video mode or hold-to-record)
        if (isRecording) {
            val infiniteTransition = rememberInfiniteTransition(label = "rec")
            val blinkAlpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.3f,
                animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                label = "blink"
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .alpha(blinkAlpha)
                        .background(Color.Red, CircleShape)
                )
                Text(
                    text = "%02d:%02d".format(recordingDurationSec / 60, recordingDurationSec % 60),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                if (!hasAudioPermission) {
                    Text(
                        text = stringResource(R.string.no_audio),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Saving indicator
        if (isSavingVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.encrypting_video), color = Color.White, fontSize = 14.sp)
            }
        }

        // Top bar: back + camera switch
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 48.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isRecording) stopRecording()
                    onBack()
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.White
                )
            }

            // Level indicator + face count (hidden during recording and viewer)
            if (!isRecording && !showViewer && !isCountingDown) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LevelIndicator()
                    if (faceCount > 0) {
                        Text(
                            text = stringResource(R.string.face_count, faceCount),
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (hasFrontCamera && !isRecording) {
                IconButton(
                    onClick = { useFrontCamera = !useFrontCamera },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Cameraswitch,
                        contentDescription = stringResource(R.string.cd_switch_camera),
                        tint = Color.White
                    )
                }
            }
        }

        // Mode selector: PHOTO / VIDEO + Timer toggle (above bottom bar)
        if (!isRecording && !isCountingDown) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 130.dp, start = 32.dp, end = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timer toggle — fixed left position
                Box(
                    Modifier.size(36.dp)
                        .background(
                            if (countdownEnabled) Color.Yellow.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .clickable { countdownEnabled = !countdownEnabled },
                    contentAlignment = Alignment.Center
                ) {
                    if (countdownEnabled) {
                        Text("${countdownDuration}s", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    } else {
                        Icon(Icons.Default.TimerOff, null, Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.7f))
                    }
                }

                Spacer(Modifier.weight(1f))

                // PHOTO / VIDEO
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Text(
                        stringResource(R.string.mode_photo),
                        color = if (!isVideoMode) Color.Yellow else Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = if (!isVideoMode) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable {
                            if (isVideoMode) isVideoMode = false
                        }
                    )
                    Text(
                        stringResource(R.string.mode_video),
                        color = if (isVideoMode) Color.Yellow else Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = if (isVideoMode) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable {
                            if (!isVideoMode) {
                                if (!hasAudioPermission) {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                                isVideoMode = true
                            }
                        }
                    )
                }

                Spacer(Modifier.weight(1f))

                // Spacer to balance the timer button
                Spacer(Modifier.size(36.dp))
            }
        }

        // Bottom bar: thumbnail + shutter/record + count
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp, start = 32.dp, end = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Last media thumbnail — tap to view directly
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .semantics {
                        contentDescription = context.getString(R.string.cd_view_last_capture)
                        role = Role.Button
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .clickable {
                        val item = lastCapturedItem
                        if (item != null) {
                            scope.launch {
                                if (item.mediaType == com.privateai.camera.security.VaultMediaType.VIDEO) {
                                    val tempFile = withContext(Dispatchers.IO) { vault.decryptVideoToTempFile(item) }
                                    if (tempFile != null) {
                                        viewerVideoFile = tempFile
                                        showViewer = true
                                    }
                                } else {
                                    val bmp = withContext(Dispatchers.IO) { vault.loadFullPhoto(item) }
                                    if (bmp != null) {
                                        viewerBitmap = bmp
                                        showViewer = true
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                lastThumbnail?.let { thumb ->
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_last_capture),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                } // lastThumbnail
            }

            // Shutter / Record button
            if (isVideoMode) {
                // Video mode: tap to start/stop recording (with optional countdown)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .semantics {
                            contentDescription = if (isRecording) context.getString(R.string.cd_stop_recording) else context.getString(R.string.cd_start_recording)
                            role = Role.Button
                        }
                        .border(4.dp, if (isCountingDown) Color.Yellow else Color.White, CircleShape)
                        .padding(6.dp)
                        .clickable(enabled = !isSavingVideo && !isCountingDown) {
                            if (isRecording) {
                                stopRecording()
                            } else if (countdownEnabled) {
                                countdownForVideo = true
                                isCountingDown = true
                            } else {
                                startRecording()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Color.Red, RoundedCornerShape(6.dp))
                        )
                    } else {
                        Box(
                            Modifier
                                .size(64.dp)
                                .background(Color.Red, CircleShape)
                        )
                    }
                }
            } else {
                // Photo mode: tap = photo (with optional countdown), hold = quick video
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .semantics {
                            contentDescription = if (isHoldRecording) context.getString(R.string.cd_stop_recording) else context.getString(R.string.cd_take_photo)
                            role = Role.Button
                        }
                        .border(
                            4.dp,
                            if (isHoldRecording) Color.Red else if (isCountingDown) Color.Yellow else Color.White,
                            CircleShape
                        )
                        .padding(6.dp)
                        .pointerInput(isCapturing, isSavingVideo, videoCapture, isCountingDown) {
                            detectTapGestures(
                                onPress = {
                                    if (isCapturing || isSavingVideo || isCountingDown) return@detectTapGestures

                                    // Start a timer — if held > 500ms, begin recording
                                    longPressJob = scope.launch {
                                        delay(500)
                                        if (videoCapture != null) {
                                            isHoldRecording = true
                                            startRecording()
                                        }
                                    }

                                    val released = tryAwaitRelease()

                                    if (released) {
                                        if (isHoldRecording) {
                                            stopRecording()
                                            isHoldRecording = false
                                        } else {
                                            longPressJob?.cancel()
                                            longPressJob = null
                                            if (countdownEnabled) {
                                                countdownForVideo = false
                                                isCountingDown = true
                                            } else {
                                                capturePhoto()
                                            }
                                        }
                                    } else {
                                        longPressJob?.cancel()
                                        longPressJob = null
                                        if (isHoldRecording) {
                                            stopRecording()
                                            isHoldRecording = false
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isHoldRecording) {
                        // Recording: red rounded square (stop indicator)
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Color.Red, RoundedCornerShape(6.dp))
                        )
                    } else {
                        // Normal: white circle
                        Box(
                            Modifier
                                .size(64.dp)
                                .background(shutterColor, CircleShape)
                        )
                    }
                }
            }

            // Media count
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .semantics { contentDescription = context.getString(R.string.cd_photos_taken, mediaCount) },
                contentAlignment = Alignment.Center
            ) {
                if (mediaCount > 0) {
                    Text(
                        text = "$mediaCount",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
            }
        }

        // Full-screen viewer overlay for last captured item
        if (showViewer) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Photo viewer
                viewerBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_captured_photo),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Video viewer
                viewerVideoFile?.let { file ->
                    VideoPlayerWithControls(
                        videoFile = file,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Back button
                IconButton(
                    onClick = {
                        showViewer = false
                        viewerBitmap?.recycle()
                        viewerBitmap = null
                        viewerVideoFile?.delete()
                        viewerVideoFile = null
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 48.dp, start = 16.dp)
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_close), tint = Color.White)
                }

                // Bottom actions: share + delete
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 40.dp, start = 24.dp, end = 24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Share
                    IconButton(onClick = {
                        lastCapturedItem?.let { item ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    if (item.mediaType == com.privateai.camera.security.VaultMediaType.VIDEO) {
                                        val tempFile = vault.decryptVideoToTempFile(item) ?: return@withContext
                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                                        withContext(Dispatchers.Main) {
                                            context.startActivity(Intent.createChooser(
                                                Intent(Intent.ACTION_SEND).apply {
                                                    type = "video/mp4"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }, context.getString(R.string.share)
                                            ))
                                        }
                                    } else {
                                        val bytes = vault.loadPhotoBytes(item) ?: return@withContext
                                        val uri = com.privateai.camera.util.saveJpegBytesToCache(context, bytes, "capture_share.jpg")
                                        withContext(Dispatchers.Main) {
                                            context.startActivity(Intent.createChooser(
                                                Intent(Intent.ACTION_SEND).apply {
                                                    type = "image/jpeg"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }, context.getString(R.string.share)
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, stringResource(R.string.cd_share), tint = Color.White)
                    }

                    // Face blur toggle (photos only) — opposite of global setting
                    if (lastCapturedItem?.mediaType != com.privateai.camera.security.VaultMediaType.VIDEO) {
                        val blurDefault = com.privateai.camera.ui.settings.isFaceBlurEnabled(context)
                        IconButton(onClick = {
                            lastCapturedItem?.let { item ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        var bitmap = vault.loadFullPhoto(item) ?: return@withContext
                                        if (!blurDefault) {
                                            bitmap = com.privateai.camera.util.FaceBlur.blurFaces(context, bitmap)
                                        }
                                        val uri = com.privateai.camera.util.saveBitmapToCache(context, bitmap, "capture_blur_share.jpg")
                                        bitmap.recycle()
                                        withContext(Dispatchers.Main) {
                                            val label = if (blurDefault) context.getString(R.string.share_no_blur) else context.getString(R.string.share_faces_blurred)
                                            context.startActivity(Intent.createChooser(
                                                Intent(Intent.ACTION_SEND).apply {
                                                    type = "image/jpeg"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }, label
                                            ))
                                        }
                                    }
                                }
                            }
                        }) {
                            Icon(
                                Icons.Default.Face,
                                stringResource(if (blurDefault) R.string.cd_share_without_blur else R.string.cd_blur_and_share),
                                tint = if (blurDefault) Color.White else Color(0xFF4CAF50)
                            )
                        }
                    }

                    // Delete
                    IconButton(onClick = {
                        lastCapturedItem?.let { item ->
                            vault.deletePhoto(item)
                            lastCapturedItem = null
                            lastThumbnail?.recycle()
                            lastThumbnail = null
                            mediaCount = (mediaCount - 1).coerceAtLeast(0)
                        }
                        showViewer = false
                        viewerBitmap?.recycle()
                        viewerBitmap = null
                        viewerVideoFile?.delete()
                        viewerVideoFile = null
                    }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.cd_delete), tint = Color(0xFFFF6B6B))
                    }
                }
            }
        }
    }
}
