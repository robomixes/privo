// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.privateai.camera.R
import com.privateai.camera.bridge.Detection
import com.privateai.camera.bridge.OnnxDetector
import com.privateai.camera.util.cropDetectionRegion
import com.privateai.camera.util.launchImageSearch
import java.util.concurrent.TimeUnit

@Composable
fun CameraScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    if (hasCameraPermission) {
        CameraPreviewWithDetection(onBack = onBack)
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.camera_needs_access),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    stringResource(R.string.camera_privacy_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.grant_camera_permission))
                }
            }
        }

        LaunchedEffect(Unit) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
fun CameraPreviewWithDetection(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val captureScope = androidx.compose.runtime.rememberCoroutineScope()
    // Single source of truth for the AI gating in this screen (Detect FAB +
    // whole-scene Describe). When AI isn't READY the buttons don't render
    // at all — see AiStatus.kt for the rule.
    val aiStatus by com.privateai.camera.bridge.rememberAiStatus()
    val aiReady = aiStatus.isReady
    var detections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var inferenceTimeMs by remember { mutableLongStateOf(0L) }
    var frameCount by remember { mutableLongStateOf(0L) }

    // Camera toggle
    val hasFrontCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    }
    var useFrontCamera by remember { mutableStateOf(false) }
    val cameraSelector = if (useFrontCamera) {
        CameraSelector.DEFAULT_FRONT_CAMERA
    } else {
        CameraSelector.DEFAULT_BACK_CAMERA
    }

    // Focus & selection
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var meteringPointFactory by remember { mutableStateOf<PreviewView?>(null) }
    var selectedDetection by remember { mutableStateOf<Detection?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var isFrozen by remember { mutableStateOf(false) }
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Retain latest frame for image search cropping
    var latestFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Per-detection AI description (Gemma vision). Cleared when the user
    // selects a different detection so the card doesn't show stale text.
    var aiDescription by remember { mutableStateOf("") }
    var aiDescLoading by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(selectedDetection) {
        aiDescription = ""
    }

    // Whole-scene AI description (Gemma vision over the entire frozen frame).
    // Cleared when the user unfreezes or picks a specific detection — the
    // detection then has its own per-detection description path above.
    var sceneDescription by remember { mutableStateOf("") }
    var sceneDescLoading by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(isFrozen, selectedDetection) {
        if (!isFrozen || selectedDetection != null) {
            sceneDescription = ""
            sceneDescLoading = false
        }
    }

    // TTS engine for "read AI descriptions aloud" (D10). Inline DisposableEffect
    // mirrors TranslateScreen.kt:130-243; if Vault/Notes ever adopt TTS we'll
    // extract this to util/TtsManager.kt then. F-Droid posture: pure AOSP
    // TextToSpeech, no Play Services / proprietary deps.
    val ttsEngine = remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsUnavailableNoticeShown by remember { mutableStateOf(false) }
    var ttsSpeaking by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale.getDefault()
                val langStatus = try { engine?.setLanguage(locale) ?: TextToSpeech.LANG_MISSING_DATA } catch (_: Exception) { TextToSpeech.LANG_MISSING_DATA }
                if (langStatus >= TextToSpeech.LANG_AVAILABLE) {
                    ttsEngine.value = engine
                    // Track speaking state so the UI can show a stop control
                    // while audio is playing. Listener fires on a TTS-engine
                    // thread; hop to main for Compose state mutation.
                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    engine?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) { mainHandler.post { ttsSpeaking = true } }
                        override fun onDone(utteranceId: String?) { mainHandler.post { ttsSpeaking = false } }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { mainHandler.post { ttsSpeaking = false } }
                        override fun onError(utteranceId: String?, errorCode: Int) { mainHandler.post { ttsSpeaking = false } }
                    })
                }
                // If language isn't available we leave ttsEngine null —
                // speakText becomes a no-op; the one-time Toast surfaces on
                // first attempted read so the user knows to install a voice.
            }
        }
        onDispose { engine?.shutdown() }
    }

    fun speakAiText(text: String) {
        if (text.isBlank()) return
        val engine = ttsEngine.value
        if (engine == null) {
            if (!ttsUnavailableNoticeShown) {
                ttsUnavailableNoticeShown = true
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.detect_tts_unavailable),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            return
        }
        engine.language = Locale.getDefault()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "detect-ai")
    }

    fun stopSpeaking() {
        ttsEngine.value?.stop()
        ttsSpeaking = false
    }

    val ttsAutoEnabled = com.privateai.camera.ui.settings.isDetectTtsEnabled(context)

    // Initialize detector
    val detector = remember { OnnxDetector(context) }
    var isDisposing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            isDisposing = true
            try { detector.release() } catch (_: Exception) {}
            latestFrameBitmap?.recycle()
            frozenBitmap?.recycle()
        }
    }

    // Clear stale state on camera switch
    LaunchedEffect(useFrontCamera) {
        detections = emptyList()
        selectedDetection = null
        focusPoint = null
        isFrozen = false
        frozenBitmap?.recycle()
        frozenBitmap = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview with frame analysis
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            cameraSelector = cameraSelector,
            onFrameAnalyzed = { bitmap ->
                // Skip processing when frozen, disposing, or throttled
                if (isFrozen || isDisposing) {
                    bitmap.recycle()
                    return@CameraPreview
                }

                frameCount++
                val frameSkip = com.privateai.camera.service.DeviceProfiler.getDetectionFrameSkip(context)
                if (frameCount % frameSkip != 0L) {
                    bitmap.recycle()
                    return@CameraPreview
                }

                val start = System.currentTimeMillis()
                val catFilter = com.privateai.camera.ui.settings.getSelectedCategories(context)
                val minConf = com.privateai.camera.ui.settings.getConfidenceThreshold(context)
                val results = try {
                    detector.detect(bitmap, catFilter, minConf)
                } catch (_: Exception) { emptyList() }
                inferenceTimeMs = System.currentTimeMillis() - start
                detections = results

                // Keep latest frame for image search (copy for thread safety)
                val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                val old = latestFrameBitmap
                latestFrameBitmap = copy
                old?.recycle()
                bitmap.recycle()
            },
            onCameraBound = { camera, previewView ->
                cameraControl = camera.cameraControl
                meteringPointFactory = previewView
            }
        )

        // Frozen frame with blur + clear selected region.
        // Whole-scene describe (D3b) freezes without a selection — we still
        // need to cover the live CameraPreview, so the frozen Image is drawn
        // whenever `isFrozen` is true. The blurred / cropped selection visuals
        // only apply when a detection has been chosen.
        val frozen = frozenBitmap
        val det = selectedDetection
        if (isFrozen && frozen != null) {

            // Frozen frame covers the live preview. Blurred when a detection
            // is selected (to highlight the crop); sharp when no selection
            // (scene-describe shows the user the same frame Gemma is seeing).
            Image(
                bitmap = frozen.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = if (det != null) {
                    Modifier.fillMaxSize().blur(20.dp)
                } else {
                    Modifier.fillMaxSize()
                }
            )
        }

        // Selected-detection focus visuals (blur dim + clear cropped image)
        // only when we actually have a selection.
        if (isFrozen && frozen != null && det != null) {

            // Dark overlay on top of blur
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )

            // Clear (unblurred) crop of selected object on top
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenW = maxWidth
                val screenH = maxHeight

                // Add padding to detection box (15% expansion)
                val padX = (det.x2 - det.x1) * 0.15f
                val padY = (det.y2 - det.y1) * 0.15f
                val ex1 = (det.x1 - padX).coerceAtLeast(0f)
                val ey1 = (det.y1 - padY).coerceAtLeast(0f)
                val ex2 = (det.x2 + padX).coerceAtMost(1f)
                val ey2 = (det.y2 + padY).coerceAtMost(1f)

                // Crop from unblurred frozen bitmap
                val cropX = (ex1 * frozen.width).toInt().coerceIn(0, frozen.width - 1)
                val cropY = (ey1 * frozen.height).toInt().coerceIn(0, frozen.height - 1)
                val cropW = ((ex2 - ex1) * frozen.width).toInt().coerceIn(1, frozen.width - cropX)
                val cropH = ((ey2 - ey1) * frozen.height).toInt().coerceIn(1, frozen.height - cropY)

                val cropped = remember(det, frozen) {
                    Bitmap.createBitmap(frozen, cropX, cropY, cropW, cropH)
                }

                Image(
                    bitmap = cropped.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_selected_object, det.className),
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset(
                            x = screenW * ex1,
                            y = screenH * ey1
                        )
                        .width(screenW * (ex2 - ex1))
                        .height(screenH * (ey2 - ey1))
                )
            }
        }

        // Detection overlay — when frozen, only show the selected detection
        // (if any). With scene-describe the user freezes the frame without
        // any selection — in that case we want NO boxes at all so the
        // analyzer's continuing detection updates don't render over the
        // frozen image.
        DetectionOverlay(
            detections = if (isFrozen) {
                listOfNotNull(selectedDetection)
            } else {
                detections
            },
            selectedDetection = selectedDetection,
            isFrontCamera = useFrontCamera,
            onDetectionTapped = { detection, tapOffset ->
                selectedDetection = detection
                focusPoint = tapOffset
                isFrozen = true

                // Keep frozen frame (latest frame is already retained)
                frozenBitmap?.recycle()
                frozenBitmap = latestFrameBitmap?.copy(Bitmap.Config.ARGB_8888, false)

                // Trigger autofocus at tap point
                val factory = meteringPointFactory?.meteringPointFactory ?: return@DetectionOverlay
                val control = cameraControl ?: return@DetectionOverlay
                try {
                    val meteringPoint = factory.createPoint(tapOffset.x, tapOffset.y)
                    val action = FocusMeteringAction.Builder(meteringPoint)
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build()
                    control.startFocusAndMetering(action)
                } catch (_: Exception) { }
            },
            onBackgroundTapped = {
                selectedDetection = null
                isFrozen = false
                frozenBitmap?.recycle()
                frozenBitmap = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // Floating "Describe with AI" button anchored to the top-right corner
        // of the selected box. Rendered AFTER DetectionOverlay so it sits on
        // top of it in z-order — otherwise the overlay's tap-detection would
        // intercept the click and treat it as a background tap (which clears
        // the selection and unfreezes).
        if (isFrozen) {
            selectedDetection?.let { det ->
                if (aiReady) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val sw = maxWidth
                        val sh = maxHeight
                        val padX = (det.x2 - det.x1) * 0.15f
                        val padY = (det.y2 - det.y1) * 0.15f
                        val ex2 = (det.x2 + padX).coerceAtMost(1f)
                        val ey1 = (det.y1 - padY).coerceAtLeast(0f)
                        val btnSize = 40.dp
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = sw * ex2 - btnSize - 4.dp,
                                    y = sh * ey1 + 4.dp
                                )
                                .size(btnSize)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .clickable(enabled = !aiDescLoading) {
                                    val frame = latestFrameBitmap ?: return@clickable
                                    aiDescLoading = true
                                    captureScope.launch {
                                        try {
                                            val crop = cropDetectionRegion(frame, det)
                                            val tempFile = java.io.File(context.cacheDir, "detect_${det.classId}_${System.currentTimeMillis()}.jpg")
                                            withContext(Dispatchers.IO) {
                                                java.io.FileOutputStream(tempFile).use { out ->
                                                    crop.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                                }
                                            }
                                            crop.recycle()
                                            val prompt = com.privateai.camera.bridge.GemmaPrompts.describeDetection(det.className)
                                            val desc = com.privateai.camera.bridge.GemmaRunner.describeImage(
                                                context, tempFile.absolutePath, prompt
                                            )
                                            tempFile.delete()
                                            aiDescription = desc?.trim().orEmpty()
                                            if (ttsAutoEnabled && aiDescription.isNotEmpty()) {
                                                speakAiText(aiDescription)
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("DetectAI", "Describe failed: ${e.message}", e)
                                        }
                                        aiDescLoading = false
                                    }
                                }
                                .semantics {
                                    contentDescription = context.getString(R.string.detect_describe_with_ai)
                                    role = Role.Button
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (aiDescLoading) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Focus ring animation
        focusPoint?.let { point ->
            FocusRingIndicator(
                center = point,
                onAnimationComplete = { focusPoint = null }
            )
        }

        // Top bar: back + object count + inference time + camera switch
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 48.dp, start = 8.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Color.White)
                    }
                }
                Text(
                    text = stringResource(R.string.object_count, detections.size),
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.inference_time_ms, inferenceTimeMs),
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )

                if (hasFrontCamera) {
                    IconButton(
                        onClick = {
                            useFrontCamera = !useFrontCamera
                        },
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
        }

        // Selected detection detail card
        selectedDetection?.let { det ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = det.className.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(R.string.confidence_percent, (det.confidence * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Text search
                        TextButton(onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.google.com/search?q=${det.className}")
                            )
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.web_search), modifier = Modifier.padding(start = 4.dp))
                        }

                        // Image search
                        TextButton(onClick = {
                            val frame = latestFrameBitmap ?: return@TextButton
                            val cropped = cropDetectionRegion(frame, det)
                            launchImageSearch(context, cropped)
                            cropped.recycle()
                        }) {
                            Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.search_by_image), modifier = Modifier.padding(start = 4.dp))
                        }

                        // Describe-with-AI lives as a floating icon on the
                        // selected box itself (anchored to its top-right corner)
                        // — closer to where the user's finger already is than
                        // a button buried in this bottom card.
                    }

                    // AI description result (under the action buttons) with
                    // a manual replay speaker icon (D10). Speaker shown
                    // regardless of the auto-TTS Settings toggle so users can
                    // always re-hear; tap is silent no-op if TTS isn't
                    // available on the device (Toast surfaces the reason once).
                    if (aiDescription.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                )
                                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = aiDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    if (ttsSpeaking) stopSpeaking() else speakAiText(aiDescription)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    if (ttsSpeaking) Icons.AutoMirrored.Filled.VolumeOff
                                    else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = stringResource(
                                        if (ttsSpeaking) R.string.detect_stop_speech
                                        else R.string.detect_replay_speech
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Capture button — saves current frame with detection boxes to vault
        if (!isFrozen) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 32.dp, end = 16.dp)
                    .size(56.dp)
                    .semantics {
                        contentDescription = context.getString(R.string.cd_save_detection_photo)
                        role = Role.Button
                    }
                    .background(Color.White, CircleShape)
                    .border(3.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    .clickable {
                        val frame = latestFrameBitmap ?: return@clickable
                        val dets = detections
                        captureScope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    val mutable = frame.copy(Bitmap.Config.ARGB_8888, true)
                                    val canvas = android.graphics.Canvas(mutable)
                                    val w = mutable.width.toFloat()
                                    val h = mutable.height.toFloat()
                                    val boxPaint = android.graphics.Paint().apply {
                                        style = android.graphics.Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true
                                    }
                                    val textPaint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.WHITE; textSize = (h * 0.025f).coerceIn(24f, 48f); isAntiAlias = true; isFakeBoldText = true
                                    }
                                    val bgPaint = android.graphics.Paint().apply { style = android.graphics.Paint.Style.FILL }
                                    for (det in dets) {
                                        val left = det.x1 * w; val top = det.y1 * h; val right = det.x2 * w; val bottom = det.y2 * h
                                        val hue = (det.classId * 37 % 360).toFloat()
                                        val color = android.graphics.Color.HSVToColor(200, floatArrayOf(hue, 0.8f, 1f))
                                        boxPaint.color = color; bgPaint.color = color
                                        canvas.drawRect(android.graphics.RectF(left, top, right, bottom), boxPaint)
                                        val label = "${det.className} ${(det.confidence * 100).toInt()}%"
                                        val tw = textPaint.measureText(label)
                                        canvas.drawRect(left, top - textPaint.textSize - 8f, left + tw + 16f, top, bgPaint)
                                        canvas.drawText(label, left + 8f, top - 6f, textPaint)
                                    }
                                    val crypto = com.privateai.camera.security.CryptoManager(context).also { it.initialize() }
                                    val vault = com.privateai.camera.security.VaultRepository(context, crypto)
                                    vault.savePhoto(mutable, com.privateai.camera.security.VaultCategory.DETECT)
                                    mutable.recycle()
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, context.getString(R.string.detection_photo_saved), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, context.getString(R.string.save_failed_generic), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(44.dp).background(Color.White, CircleShape))
            }
        }

        // Floating "Describe scene" button at BottomStart (mirrors capture
        // FAB on the right). Only shown when live (not frozen) and Gemma is
        // available. Tap → freezes the current frame, runs Gemma vision over
        // the whole image, surfaces the result in a card at BottomCenter.
        if (!isFrozen && aiReady) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 32.dp, start = 16.dp)
                    .size(56.dp)
                    .semantics {
                        contentDescription = context.getString(R.string.detect_describe_scene)
                        role = Role.Button
                    }
                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    .border(3.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    .clickable(enabled = !sceneDescLoading) {
                        val frame = latestFrameBitmap?.copy(Bitmap.Config.ARGB_8888, false) ?: return@clickable
                        // Freeze the live preview so the user sees what's being described.
                        frozenBitmap?.recycle()
                        frozenBitmap = frame.copy(Bitmap.Config.ARGB_8888, false)
                        isFrozen = true
                        sceneDescLoading = true
                        sceneDescription = ""
                        captureScope.launch {
                            try {
                                val tempFile = java.io.File(context.cacheDir, "scene_${System.currentTimeMillis()}.jpg")
                                withContext(Dispatchers.IO) {
                                    java.io.FileOutputStream(tempFile).use { out ->
                                        frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                    }
                                }
                                frame.recycle()
                                val desc = com.privateai.camera.bridge.GemmaRunner.describeImage(
                                    context, tempFile.absolutePath,
                                    com.privateai.camera.bridge.GemmaPrompts.describePhoto()
                                )
                                tempFile.delete()
                                sceneDescription = desc?.trim().orEmpty()
                                if (ttsAutoEnabled && sceneDescription.isNotEmpty()) {
                                    speakAiText(sceneDescription)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("DetectAI", "Scene describe failed: ${e.message}", e)
                            }
                            sceneDescLoading = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (sceneDescLoading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Scene description result card — shown when Gemma returned a
        // whole-frame description and there's no selected detection (the
        // per-detection card takes over in that case).
        if (sceneDescription.isNotEmpty() && selectedDetection == null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 100.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.detect_describe_scene),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        // Manual replay / stop speaker (D10)
                        IconButton(
                            onClick = {
                                if (ttsSpeaking) stopSpeaking() else speakAiText(sceneDescription)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (ttsSpeaking) Icons.AutoMirrored.Filled.VolumeOff
                                else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = stringResource(
                                    if (ttsSpeaking) R.string.detect_stop_speech
                                    else R.string.detect_replay_speech
                                ),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        sceneDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        // Scene-describe spinner overlay while Gemma is working with no
        // result yet — centered for visibility while the frozen frame is up.
        if (sceneDescLoading && selectedDetection == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .size(72.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                    color = Color.White
                )
            }
        }

        // Bottom chips (only when no detection is selected and live preview).
        // Hidden when frozen so they don't compete with the scene-describe
        // result card. Centered so they don't run under the FABs at the
        // bottom-left (Describe scene) and bottom-right (capture).
        if (!isFrozen && detections.isNotEmpty() && selectedDetection == null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 88.dp, end = 88.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                detections.take(3).forEach { detection ->
                    Row(
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.google.com/search?q=${detection.className}")
                                )
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            text = detection.className,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
