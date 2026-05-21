// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.qrscanner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onBack: (() -> Unit)? = null,
    onOtpAuthScanned: ((String) -> Unit)? = null,
    // When true, the scanner is in "authenticator setup" mode: ZXing is
    // restricted to QR_CODE only (so a stray 1-D barcode in frame can't be
    // mis-decoded into something that isn't an otpauth:// URI), and any
    // non-otpauth result is silently ignored so the scan keeps running until
    // a real authenticator QR is captured.
    otpAuthOnly: Boolean = false
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val history = remember { mutableStateListOf<QrHistoryItem>() }

    // Load history on first composition
    LaunchedEffect(Unit) {
        history.clear()
        history.addAll(QrHistoryRepository.load(context))
    }

    // Detail bottom sheet state
    var detailItem by remember { mutableStateOf<QrHistoryItem?>(null) }

    if (detailItem != null) {
        ModalBottomSheet(
            onDismissRequest = { detailItem = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            ScannedResultContent(
                item = detailItem!!,
                context = context,
                onDismiss = { detailItem = null }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (otpAuthOnly) "Scan authenticator QR" else "QR Code") },
                navigationIcon = {
                    IconButton(onClick = { onBack?.invoke() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            // Tab row — hidden in otpAuthOnly mode (the user is here to scan a
            // single authenticator code, not to browse history or generate).
            if (!otpAuthOnly) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Scan") },
                        icon = { Icon(Icons.Default.QrCodeScanner, null, Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Generate") },
                        icon = { Icon(Icons.Default.QrCode2, null, Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("History") },
                        icon = { Icon(Icons.Default.History, null, Modifier.size(18.dp)) }
                    )
                }
            }

            when (selectedTab) {
                0 -> QrScanTab(
                    onBack = onBack,
                    onCodeScanned = { item ->
                        QrHistoryRepository.addItem(context, item)
                        history.add(0, item)
                        if (history.size > 200) history.removeAt(history.lastIndex)
                    },
                    onShowDetail = { detailItem = it },
                    onOtpAuthScanned = onOtpAuthScanned,
                    otpAuthOnly = otpAuthOnly
                )
                1 -> QrGenerateTab(
                    onBack = onBack,
                    onCodeGenerated = { item ->
                        QrHistoryRepository.addItem(context, item)
                        history.add(0, item)
                        if (history.size > 200) history.removeAt(history.lastIndex)
                    }
                )
                2 -> QrHistoryTab(
                    onBack = onBack,
                    history = history,
                    onDelete = { id ->
                        QrHistoryRepository.deleteItem(context, id)
                        history.removeAll { it.id == id }
                    },
                    onClearAll = {
                        QrHistoryRepository.clearAll(context)
                        history.clear()
                    },
                    onItemClick = { detailItem = it }
                )
            }
        }
    }
}

// ─── Scan Tab ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrScanTab(
    onBack: (() -> Unit)?,
    onCodeScanned: (QrHistoryItem) -> Unit,
    onShowDetail: (QrHistoryItem) -> Unit,
    onOtpAuthScanned: ((String) -> Unit)? = null,
    otpAuthOnly: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCameraPermission = it }

    var scannedResult by remember { mutableStateOf<QrHistoryItem?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    // Re-scan suppression. ZXing has no built-in dedup (ML Kit's old path did),
    // so without this, dismissing the result sheet while the camera is still
    // pointed at the same code re-fires the analyzer on the very next frame
    // and the sheet pops back instantly — making it impossible to back out of
    // the scanner. Track last-seen value + timestamp and skip duplicates that
    // arrive within the cooldown window. Different code or window expired →
    // a normal scan proceeds. Refs (not state) — we never render off these,
    // so we don't want them to trigger recomposition.
    val lastScanned = remember { java.util.concurrent.atomic.AtomicReference<Pair<String, Long>?>(null) }

    if (!hasCameraPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.QrCodeScanner, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Camera access needed to scan codes")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Grant Permission") }
            }
        }
        LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.CAMERA) }
        return
    }

    fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        } catch (_: Exception) {}
    }

    // Result bottom sheet
    if (showResult && scannedResult != null) {
        // Re-stamp the cooldown anchor at dismiss time so the same QR still
        // in the viewfinder can't immediately re-trigger. Detection-time
        // anchoring alone fails when the user reads the sheet longer than
        // the cooldown window.
        val dismiss = {
            scannedResult?.rawValue?.let { lastScanned.set(it to System.currentTimeMillis()) }
            showResult = false
        }
        ModalBottomSheet(
            onDismissRequest = dismiss,
            sheetState = rememberModalBottomSheetState()
        ) {
            ScannedResultContent(
                item = scannedResult!!,
                context = context,
                onDismiss = dismiss
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Camera preview with barcode analysis
        val previewView = remember { PreviewView(context) }
        val executor = remember { Executors.newSingleThreadExecutor() }
        // ZXing reader (Track A1.1 — replaces ML Kit BarcodeScanning so the
        // fdroid flavor sheds another Google dependency on the way to F-Droid
        // main eligibility). MultiFormatReader is single-threaded; we wrap
        // it in a single-thread executor so each frame is decoded serially.
        // POSSIBLE_FORMATS narrows the symbology set we try — Privora's
        // scanner is QR-first but 1-D barcodes (EAN/UPC) are nice-to-have
        // for products / ISBNs. TRY_HARDER trades a small bit of CPU for
        // better recovery on tilted / dim / partly occluded codes.
        val reader = remember(otpAuthOnly) {
            // In otpAuthOnly mode, narrow to QR_CODE only. MultiFormatReader's
            // 1-D readers (especially the loose ones like CODE_128 / ITF) can
            // produce a "successful" decode on noisy regions of a QR finder
            // pattern, returning a short numeric string instead of the real
            // otpauth:// payload. That made the authenticator setup fail
            // intermittently — the scanner would post a non-otpauth result
            // and the user would see the generic result sheet with garbage
            // text instead of jumping into the TOTP add screen.
            val formats = if (otpAuthOnly) {
                listOf(BarcodeFormat.QR_CODE)
            } else {
                listOf(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.AZTEC,
                    BarcodeFormat.DATA_MATRIX,
                    BarcodeFormat.PDF_417,
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.UPC_E,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.CODE_39,
                    BarcodeFormat.CODE_93,
                    BarcodeFormat.CODABAR,
                    BarcodeFormat.ITF,
                )
            }
            MultiFormatReader().apply {
                setHints(mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to formats,
                    DecodeHintType.TRY_HARDER to true,
                ))
            }
        }

        DisposableEffect(lifecycleOwner) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(executor) { imageProxy ->
                        try {
                            @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                            val mediaImage = imageProxy.image
                            if (mediaImage != null && !showResult) {
                                // Pull the Y plane (luminance) out of the
                                // YUV_420_888 frame. PlanarYUVLuminanceSource
                                // operates directly on this without an extra
                                // ARGB conversion — much faster than going
                                // through Bitmap. The row stride may exceed
                                // the image width on some devices (padding
                                // bytes); ZXing handles that via its
                                // dataWidth argument.
                                val plane = mediaImage.planes[0]
                                val buffer = plane.buffer
                                val data = ByteArray(buffer.remaining())
                                buffer.get(data)
                                val width = mediaImage.width
                                val height = mediaImage.height
                                val rowStride = plane.rowStride

                                // Account for camera rotation. The Y plane is
                                // delivered in sensor orientation (usually
                                // landscape); rotating the bytes lets ZXing
                                // see the QR upright. Cheaper than re-encoding
                                // a Bitmap. We rotate only the multiples of
                                // 90 — anything in between never happens for
                                // back-camera CameraX feeds.
                                val rotation = imageProxy.imageInfo.rotationDegrees
                                val source = buildLuminanceSource(data, width, height, rowStride, rotation)

                                val bitmap = BinaryBitmap(HybridBinarizer(source))
                                try {
                                    val result = reader.decode(bitmap)
                                    val raw = result.text ?: ""
                                    // Cooldown — see lastScanned declaration.
                                    val now = System.currentTimeMillis()
                                    val prev = lastScanned.get()
                                    val isDuplicate = prev != null && prev.first == raw && (now - prev.second) < 3000L
                                    if (raw.isNotEmpty() && !showResult && !isDuplicate) {
                                        lastScanned.set(raw to now)
                                        // Decode ran on the CameraX analyzer thread.
                                        // ML Kit's BarcodeScanning used to post its
                                        // success callback to main automatically; ZXing
                                        // is synchronous, so we have to hop ourselves —
                                        // otherwise `navController.navigate(...)` inside
                                        // onOtpAuthScanned silently no-ops on a worker
                                        // thread and the user never reaches the
                                        // Authenticator add screen even though decoding
                                        // succeeded.
                                        ContextCompat.getMainExecutor(context).execute {
                                            // TOTP shortcut — same as the ML Kit
                                            // path it replaces. Route otpauth://
                                            // URIs straight to the Authenticator
                                            // add screen instead of the generic
                                            // result sheet.
                                            val isOtp = raw.startsWith("otpauth://", ignoreCase = true)
                                            if (otpAuthOnly && !isOtp) {
                                                // Authenticator setup mode: silently drop
                                                // anything that isn't an otpauth URI so the
                                                // user can keep scanning. Don't update the
                                                // cooldown timestamp here — we already set
                                                // it before the main-thread hop, which is
                                                // fine; a wrong code won't loop tightly
                                                // because the QR has to actually decode
                                                // again before this branch runs.
                                                android.util.Log.d("QrScanner", "Ignoring non-otpauth scan in authenticator mode")
                                            } else if (onOtpAuthScanned != null && isOtp) {
                                                try { vibrate() } catch (_: Exception) {}
                                                showResult = true
                                                onOtpAuthScanned(raw)
                                            } else {
                                                val valueType = BarcodeType.classify(raw)
                                                val item = QrHistoryItem(
                                                    rawValue = raw,
                                                    displayValue = raw,
                                                    format = BarcodeType.FORMAT_QR_CODE,
                                                    valueType = valueType,
                                                    typeLabel = getTypeLabel(valueType),
                                                    source = QrSource.SCANNED
                                                )
                                                scannedResult = item
                                                showResult = true
                                                onCodeScanned(item)
                                                try { vibrate() } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                } catch (_: NotFoundException) {
                                    // No barcode found in this frame — normal
                                    // case during scanning; ignore silently.
                                } catch (e: Exception) {
                                    android.util.Log.w("QrScanner", "ZXing decode error: ${e.message}")
                                } finally {
                                    reader.reset()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("QrScanner", "Analyzer error: ${e.message}")
                        } finally {
                            imageProxy.close()
                        }
                    }

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    cameraControl = camera.cameraControl
                } catch (e: Exception) {
                    android.util.Log.e("QrScanner", "Camera setup failed: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                try { cameraProviderFuture.get().unbindAll() } catch (_: Exception) {}
                executor.shutdown()
            }
        }

        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Scan overlay
        Canvas(Modifier.fillMaxSize()) {
            val scanBoxSize = size.width * 0.7f
            val left = (size.width - scanBoxSize) / 2
            val top = (size.height - scanBoxSize) / 2
            val right = left + scanBoxSize
            val bottom = top + scanBoxSize
            val dimColor = Color.Black.copy(alpha = 0.5f)

            drawRect(dimColor, topLeft = Offset.Zero, size = Size(size.width, top))
            drawRect(dimColor, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
            drawRect(dimColor, topLeft = Offset(0f, top), size = Size(left, scanBoxSize))
            drawRect(dimColor, topLeft = Offset(right, top), size = Size(size.width - right, scanBoxSize))

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = Size(scanBoxSize, scanBoxSize),
                cornerRadius = CornerRadius(16f),
                style = Stroke(width = 3f)
            )

            val cornerLen = 30f
            val cornerWidth = 4f
            val accentColor = Color(0xFF4CAF50)
            drawLine(accentColor, Offset(left, top + 8), Offset(left, top + cornerLen), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(left + 8, top), Offset(left + cornerLen, top), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(right, top + 8), Offset(right, top + cornerLen), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(right - 8, top), Offset(right - cornerLen, top), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(left, bottom - 8), Offset(left, bottom - cornerLen), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(left + 8, bottom), Offset(left + cornerLen, bottom), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(right, bottom - 8), Offset(right, bottom - cornerLen), strokeWidth = cornerWidth)
            drawLine(accentColor, Offset(right - 8, bottom), Offset(right - cornerLen, bottom), strokeWidth = cornerWidth)
        }

        // Flash toggle overlay
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = {
                    flashOn = !flashOn
                    cameraControl?.enableTorch(flashOn)
                },
                Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, "Flash", tint = Color.White)
            }
        }

        // Bottom hint
        Text(
            if (otpAuthOnly) "Point camera at the authenticator QR code"
            else "Point camera at a QR code or barcode",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

// ─── Shared Result Content ──────────────────────────────────────────────────

@Composable
fun ScannedResultContent(item: QrHistoryItem, context: Context, onDismiss: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.typeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Text(
                if (item.source == QrSource.SCANNED) "Scanned" else "Generated",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(item.displayValue, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Copy
            IconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("QR code", item.rawValue))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, "Copy")
            }

            // Share
            IconButton(onClick = {
                context.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, item.rawValue)
                    }, "Share"
                ))
            }) {
                Icon(Icons.Default.Share, "Share")
            }

            // URL: open in browser
            if (item.valueType == BarcodeType.URL || item.rawValue.startsWith("http")) {
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.rawValue)))
                }) {
                    Icon(Icons.Default.OpenInBrowser, "Open URL")
                }
            }

            // WiFi
            if (item.valueType == BarcodeType.WIFI) {
                IconButton(onClick = {
                    Toast.makeText(context, "WiFi: ${item.displayValue}", Toast.LENGTH_LONG).show()
                }) {
                    Icon(Icons.Default.Wifi, "WiFi")
                }
            }

            // Phone: dial
            if (item.valueType == BarcodeType.PHONE) {
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.rawValue}")))
                }) {
                    Icon(Icons.Default.Link, "Call")
                }
            }

            // Email: compose
            if (item.valueType == BarcodeType.EMAIL) {
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${item.rawValue}")))
                }) {
                    Icon(Icons.Default.Link, "Email")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = onDismiss, Modifier.fillMaxWidth()) {
            Text("Done")
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ─── Helpers ────────────────────────────────────────────────────────────────

fun getTypeLabel(valueType: Int): String {
    return when (valueType) {
        BarcodeType.URL -> "URL"
        BarcodeType.WIFI -> "WiFi"
        BarcodeType.EMAIL -> "Email"
        BarcodeType.PHONE -> "Phone"
        BarcodeType.SMS -> "SMS"
        BarcodeType.GEO -> "Location"
        BarcodeType.CONTACT_INFO -> "Contact"
        BarcodeType.CALENDAR_EVENT -> "Calendar Event"
        BarcodeType.ISBN -> "ISBN"
        BarcodeType.PRODUCT -> "Product"
        else -> "Code"
    }
}

/**
 * Build a ZXing [PlanarYUVLuminanceSource] from a CameraX Y-plane buffer,
 * honouring the device's reported rotation so the QR code reads upright.
 *
 * CameraX delivers preview frames in sensor orientation — typically landscape
 * even when the phone is held portrait — and ZXing has no built-in rotate.
 * For the 90° / 270° common cases we rotate the byte array directly; 180° is
 * a simple reverse. 0° is the cheap fast-path (no copy past the buffer get).
 */
private fun buildLuminanceSource(
    data: ByteArray,
    width: Int,
    height: Int,
    rowStride: Int,
    rotationDegrees: Int
): PlanarYUVLuminanceSource {
    return when (rotationDegrees) {
        90 -> {
            val rotated = ByteArray(width * height)
            for (y in 0 until height) {
                val srcBase = y * rowStride
                for (x in 0 until width) {
                    rotated[x * height + (height - y - 1)] = data[srcBase + x]
                }
            }
            PlanarYUVLuminanceSource(rotated, height, width, 0, 0, height, width, false)
        }
        180 -> {
            val rotated = ByteArray(width * height)
            for (y in 0 until height) {
                val srcBase = y * rowStride
                for (x in 0 until width) {
                    rotated[(height - y - 1) * width + (width - x - 1)] = data[srcBase + x]
                }
            }
            PlanarYUVLuminanceSource(rotated, width, height, 0, 0, width, height, false)
        }
        270 -> {
            val rotated = ByteArray(width * height)
            for (y in 0 until height) {
                val srcBase = y * rowStride
                for (x in 0 until width) {
                    rotated[(width - x - 1) * height + y] = data[srcBase + x]
                }
            }
            PlanarYUVLuminanceSource(rotated, height, width, 0, 0, height, width, false)
        }
        else -> {
            // 0° or unrecognised — pass the raw Y plane through. ZXing's
            // dataWidth=rowStride argument handles padding bytes.
            PlanarYUVLuminanceSource(data, rowStride, height, 0, 0, width, height, false)
        }
    }
}
