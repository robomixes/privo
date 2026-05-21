// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.scanner

import com.privateai.camera.R
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.VaultCategory
import com.privateai.camera.security.VaultRepository
import java.io.File
import java.io.FileOutputStream

enum class EnhancementMode(val label: String) {
    ORIGINAL("Original"),
    AUTO("Auto"),
    BW("B&W"),
    COLOR("Color")
}

@Composable
fun ScannerScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var scannedPages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var enhancementMode by remember { mutableStateOf(EnhancementMode.ORIGINAL) }
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var ocrText by remember { mutableStateOf<String?>(null) }
    var isProcessingOcr by remember { mutableStateOf(false) }
    var showOcrResult by remember { mutableStateOf(false) }

    // Smart Scanner — Gemma classifies the saved doc and proposes a
    // filename + folder. Loading flips true while analyze runs; suggestion
    // populated after parse; pendingSavedFile is the encrypted .pdf.enc the
    // user accepts/renames/moves from the sheet.
    var smartScanLoading by remember { mutableStateOf(false) }
    var smartScanSuggestion by remember { mutableStateOf<com.privateai.camera.bridge.ScannerAi.Suggestion?>(null) }
    var pendingSavedFile by remember { mutableStateOf<java.io.File?>(null) }

    // Encrypted vault
    val crypto = remember { CryptoManager(context).also { it.initialize() } }
    val vault = remember { VaultRepository(context, crypto) }
    val folderManager = remember { com.privateai.camera.security.FolderManager(context, crypto) }

    // Track A2: replaced ML Kit's `GmsDocumentScanning` activity with
    // Privora's own CameraX-based capture flow + manual corner-drag
    // perspective correction (see ScannerCaptureScreen). The intent
    // launcher / scanner client are gone; we toggle an in-place
    // composable instead so back-press works the same and ML Kit's
    // play-services dep can leave the build entirely.
    var showCaptureFlow by remember { mutableStateOf(false) }

    fun startScan() { showCaptureFlow = true }

    fun switchPage(index: Int) {
        if (index in scannedPages.indices) {
            currentPageIndex = index
            displayBitmap = loadAndEnhanceBitmap(context, scannedPages[index], enhancementMode)
            ocrText = null
            showOcrResult = false
        }
    }

    fun runOcr() {
        val bitmap = displayBitmap ?: return
        isProcessingOcr = true
        scope.launch {
            try {
                // Track A1.3: ML Kit text-recognition → Tesseract 5.
                // We OCR with whichever languages the user has downloaded
                // via Settings → OCR languages. If none, the recognizer
                // returns empty and we surface a helpful pointer.
                val text = com.privateai.camera.bridge.TesseractRecognizer
                    .recognizeInstalledLanguages(context, bitmap)
                ocrText = if (text.isBlank() &&
                    !com.privateai.camera.bridge.TesseractRecognizer.hasAnyLanguage(context)) {
                    context.getString(R.string.scanner_ocr_no_languages)
                } else text
                showOcrResult = true
            } catch (e: Exception) {
                ocrText = "OCR failed: ${e.message}"
                showOcrResult = true
            } finally {
                isProcessingOcr = false
            }
        }
    }

    fun saveCurrentPageToGallery() {
        val bitmap = displayBitmap ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val filename = "scan_${System.currentTimeMillis()}.jpg"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PrivateAICamera")
                        }
                        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        uri?.let {
                            context.contentResolver.openOutputStream(it)?.use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                        }
                    } else {
                        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        val file = File(dir, filename)
                        FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved to gallery", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun saveAsPdfAndShare(shareAfterSave: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val pdfDocument = PdfDocument()
                    // A4 at 150 DPI = 1240x1754 — good quality, reasonable file size
                    val maxWidth = 1240
                    val maxHeight = 1754

                    for (i in scannedPages.indices) {
                        val bmp = loadAndEnhanceBitmap(context, scannedPages[i], enhancementMode) ?: continue

                        // Scale down to fit within A4 at 150 DPI
                        val scale = minOf(maxWidth.toFloat() / bmp.width, maxHeight.toFloat() / bmp.height, 1f)
                        val scaledW = (bmp.width * scale).toInt()
                        val scaledH = (bmp.height * scale).toInt()
                        val scaled = if (scale < 1f) {
                            Bitmap.createScaledBitmap(bmp, scaledW, scaledH, true).also { bmp.recycle() }
                        } else {
                            bmp
                        }

                        val pageInfo = PdfDocument.PageInfo.Builder(scaledW, scaledH, i + 1).create()
                        val page = pdfDocument.startPage(pageInfo)
                        page.canvas.drawBitmap(scaled, 0f, 0f, null)
                        pdfDocument.finishPage(page)
                        if (i != currentPageIndex || scale < 1f) scaled.recycle()
                    }

                    val pdfFile = File(context.cacheDir, "scan_${System.currentTimeMillis()}.pdf")
                    FileOutputStream(pdfFile).use { out ->
                        pdfDocument.writeTo(out)
                    }
                    pdfDocument.close()

                    withContext(Dispatchers.Main) {
                        if (shareAfterSave) {
                            val uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", pdfFile
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share PDF"))
                        } else {
                            Toast.makeText(context, "PDF saved (${scannedPages.size} pages)", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "PDF failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun shareCurrentPage() {
        val bitmap = displayBitmap ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val uri = com.privateai.camera.util.saveBitmapToCache(context, bitmap, "share_scan.jpg")
                    withContext(Dispatchers.Main) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share scan (EXIF stripped)"))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Render the capture flow full-screen while it's active; on Done it
    // hands back the captured page URIs (same shape ML Kit used to give us)
    // and we fall through to the existing page-display + enhance + save UI.
    if (showCaptureFlow) {
        ScannerCaptureScreen(
            onDone = { uris ->
                if (uris.isNotEmpty()) {
                    scannedPages = uris
                    currentPageIndex = 0
                    ocrText = null
                    showOcrResult = false
                    displayBitmap = loadAndEnhanceBitmap(context, uris[0], enhancementMode)
                }
                showCaptureFlow = false
            },
            onCancel = { showCaptureFlow = false }
        )
        return
    }

    @OptIn(ExperimentalMaterial3Api::class)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document Scanner") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                }
            )
        }
    ) { scaffoldPadding ->

    if (scannedPages.isEmpty()) {
        // No scan yet
        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.DocumentScanner,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text("Document Scanner", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Scan documents, receipts, notes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { startScan() }) {
                Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("  Scan Document", modifier = Modifier.padding(start = 4.dp))
            }
        }
    } else {
        // Scanned result
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Scanned image
            displayBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Scanned page",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            // Page navigation
            if (scannedPages.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { switchPage(currentPageIndex - 1) },
                        enabled = currentPageIndex > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, "Previous page")
                    }
                    Text(
                        "Page ${currentPageIndex + 1} of ${scannedPages.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(
                        onClick = { switchPage(currentPageIndex + 1) },
                        enabled = currentPageIndex < scannedPages.size - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, "Next page")
                    }
                }
            }

            // Enhancement chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnhancementMode.entries.forEach { mode ->
                    FilterChip(
                        selected = enhancementMode == mode,
                        onClick = {
                            enhancementMode = mode
                            displayBitmap = loadAndEnhanceBitmap(
                                context, scannedPages[currentPageIndex], mode
                            )
                        },
                        label = { Text(mode.label) }
                    )
                }
            }

            // Action buttons row 1: Save to Vault & Share
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Save to encrypted vault
                Button(
                    onClick = {
                        val bitmap = displayBitmap ?: return@Button
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    // Save all pages to vault
                                    for (i in scannedPages.indices) {
                                        val bmp = if (i == currentPageIndex) {
                                            bitmap
                                        } else {
                                            loadAndEnhanceBitmap(context, scannedPages[i], enhancementMode) ?: continue
                                        }
                                        vault.savePhoto(bmp, VaultCategory.SCAN)
                                        if (i != currentPageIndex) bmp.recycle()
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "${scannedPages.size} page(s) saved to vault", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Vault save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Save to Vault")
                }
                OutlinedButton(onClick = { shareCurrentPage() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Share")
                }
            }

            // Action buttons row 2: Save to gallery & Share PDF
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { saveCurrentPageToGallery() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Save Image")
                }
                OutlinedButton(
                    onClick = { saveAsPdfAndShare(shareAfterSave = true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Share PDF")
                }
            }

            // Action buttons row 2b: Save PDF to vault
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    val pdfDocument = android.graphics.pdf.PdfDocument()
                                    val maxWidth = 1240
                                    val maxHeight = 1754
                                    for (i in scannedPages.indices) {
                                        val bmp = loadAndEnhanceBitmap(context, scannedPages[i], enhancementMode) ?: continue
                                        val scale = minOf(maxWidth.toFloat() / bmp.width, maxHeight.toFloat() / bmp.height, 1f)
                                        val scaledW = (bmp.width * scale).toInt()
                                        val scaledH = (bmp.height * scale).toInt()
                                        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, scaledW, scaledH, true).also { bmp.recycle() } else bmp
                                        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(scaledW, scaledH, i + 1).create()
                                        val page = pdfDocument.startPage(pageInfo)
                                        page.canvas.drawBitmap(scaled, 0f, 0f, null)
                                        pdfDocument.finishPage(page)
                                        scaled.recycle()
                                    }
                                    val pdfBytes = java.io.ByteArrayOutputStream().use { out ->
                                        pdfDocument.writeTo(out)
                                        pdfDocument.close()
                                        out.toByteArray()
                                    }
                                    val pdfFilename = "scan_${System.currentTimeMillis()}.pdf"
                                    val savedFile = vault.saveFile(pdfBytes, pdfFilename, VaultCategory.SCAN)

                                    // OCR every page and write an encrypted sidecar so the
                                    // Assistant can answer questions about this document.
                                    // Best-effort: failures here don't block the PDF save.
                                    // Track A1.3: now uses Tesseract 5 across whichever
                                    // languages the user has downloaded — multi-script
                                    // (Arabic + English in the same doc, etc.) just works.
                                    try {
                                        val perPage = mutableListOf<String>()
                                        for (i in scannedPages.indices) {
                                            val pageBmp = loadAndEnhanceBitmap(context, scannedPages[i], enhancementMode) ?: continue
                                            try {
                                                val res = com.privateai.camera.bridge.TesseractRecognizer
                                                    .recognizeInstalledLanguages(context, pageBmp)
                                                perPage.add(res)
                                            } catch (e: Exception) {
                                                Log.w("ScannerScreen", "OCR failed on page ${i+1}: ${e.message}")
                                                perPage.add("")
                                            } finally {
                                                pageBmp.recycle()
                                            }
                                        }
                                        val fullText = perPage.joinToString("\n\n").trim()
                                        // With Tesseract we no longer need the
                                        // Latin-only sanity gate — the user picks
                                        // which languages to install, so the
                                        // output script is by definition something
                                        // the engine knows how to read. Any
                                        // non-empty result is worth saving.
                                        if (fullText.isNotEmpty()) {
                                            val sidecarId = savedFile.name.removeSuffix(".pdf.enc")
                                            vault.saveOcrSidecar(sidecarId, savedFile.parentFile!!, fullText, perPage)
                                        }
                                    } catch (e: Exception) {
                                        Log.w("ScannerScreen", "OCR sidecar pass failed: ${e.message}")
                                    }

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "PDF saved to vault (${scannedPages.size} pages)", Toast.LENGTH_SHORT).show()
                                    }

                                    // Smart Scanner — only when the user has it enabled in
                                    // Settings AND Gemma is loaded. Trigger on the first
                                    // page bitmap (already in memory from the enhancement
                                    // loop) so the analyze call is a single ~5s GPU pass.
                                    if (isSmartScanEnabled(context) &&
                                        com.privateai.camera.bridge.GemmaRunner.isAvailable(context)) {
                                        withContext(Dispatchers.Main) {
                                            smartScanLoading = true
                                            pendingSavedFile = savedFile
                                        }
                                        try {
                                            val firstPageBmp = loadAndEnhanceBitmap(context, scannedPages[0], enhancementMode)
                                            if (firstPageBmp != null) {
                                                val folders = folderManager.listAllFolders().map { it.name }
                                                val suggestion = com.privateai.camera.bridge.ScannerAi.analyze(
                                                    context, firstPageBmp, folders
                                                )
                                                firstPageBmp.recycle()
                                                withContext(Dispatchers.Main) {
                                                    smartScanLoading = false
                                                    smartScanSuggestion = suggestion
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) { smartScanLoading = false }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.w("ScannerScreen", "Smart scan failed: ${e.message}")
                                            withContext(Dispatchers.Main) { smartScanLoading = false }
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Save PDF to Vault")
                }
            }

            // Action buttons row 3: OCR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedButton(
                    onClick = { runOcr() },
                    enabled = !isProcessingOcr,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isProcessingOcr) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text("  Extract Text")
                }
            }

            // New scan button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedButton(onClick = { startScan() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  New Scan")
                }
            }

            // OCR result
            if (showOcrResult && ocrText != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Extracted Text", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(ocrText!!))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = ocrText!!.ifEmpty { "(No text found)" },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    } // Scaffold

    // Smart Scanner bottom sheet — shown while Gemma is analyzing or after
    // a parsed suggestion arrives. Accept renames + moves the saved PDF.
    val activeSuggestion = smartScanSuggestion
    val activeFile = pendingSavedFile
    if (smartScanLoading || (activeSuggestion != null && activeFile != null)) {
        SmartScanSheet(
            loading = smartScanLoading,
            suggestion = activeSuggestion,
            existingFolders = remember(activeSuggestion) {
                folderManager.listAllFolders().map { it.name }.sorted()
            },
            onAccept = { editedTitle, pickedFolder ->
                val file = pendingSavedFile
                if (file != null) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            // Reconstruct a VaultPhoto handle for the saved PDF.
                            val id = file.name.removeSuffix(".pdf.enc")
                            val photo = com.privateai.camera.security.VaultPhoto(
                                id = id,
                                timestamp = file.lastModified(),
                                category = com.privateai.camera.security.VaultCategory.SCAN,
                                encryptedFile = file,
                                thumbnailFile = file,
                                mediaType = com.privateai.camera.security.VaultMediaType.PDF
                            )
                            // Rename. Pass the raw user-typed title — renameItem
                            // strips path-illegal chars + appends .pdf for PDFs
                            // (so the user typing "Receipt 2024" lands as
                            // "Receipt 2024.pdf"; spaces are preserved).
                            val renamed = editedTitle.trim().takeIf { it.isNotBlank() }?.let { newName ->
                                val result = vault.renameItem(photo, newName)
                                android.util.Log.i("ScannerScreen", "Smart-scan rename '${photo.id}' → '$newName' result=$result")
                                (result as? com.privateai.camera.security.VaultRepository.RenameResult.Success)?.updated
                            } ?: photo
                            // Destination: null = stay in Scan album; otherwise
                            // find/create folder and move.
                            val folderName = pickedFolder?.trim()?.takeIf { it.isNotBlank() }
                            val finalDestination: String = if (folderName != null) {
                                val existing = folderManager.listAllFolders()
                                    .firstOrNull { it.name.equals(folderName, ignoreCase = true) }
                                val target = existing ?: folderManager.createFolder(folderName, parentId = null)
                                vault.moveToFolder(renamed, folderManager.getFolderDir(target.id))
                                target.name
                            } else {
                                // Stay in Scan album — no move.
                                context.getString(R.string.smart_scan_scan_album)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.smart_scan_saved_to, finalDestination),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("ScannerScreen", "Smart-scan apply failed: ${e.message}")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Couldn't apply suggestions", Toast.LENGTH_SHORT).show()
                            }
                        }
                        withContext(Dispatchers.Main) {
                            smartScanSuggestion = null
                            pendingSavedFile = null
                        }
                    }
                } else {
                    smartScanSuggestion = null
                }
            },
            onDismiss = {
                smartScanLoading = false
                smartScanSuggestion = null
                pendingSavedFile = null
            }
        )
    }
}

/** Setting accessor for the smart-scan opt-in. */
internal fun isSmartScanEnabled(context: android.content.Context): Boolean {
    return context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        .getBoolean("smart_scan_enabled", false)
}

private fun loadAndEnhanceBitmap(
    context: android.content.Context,
    uri: Uri,
    mode: EnhancementMode
): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        when (mode) {
            EnhancementMode.ORIGINAL -> original
            EnhancementMode.AUTO -> applyAutoEnhance(original)
            EnhancementMode.BW -> applyBlackWhite(original)
            EnhancementMode.COLOR -> applyColorEnhance(original)
        }
    } catch (e: Exception) {
        null
    }
}

private fun applyAutoEnhance(src: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)
    val paint = Paint()
    val cm = ColorMatrix(floatArrayOf(
        1.3f, 0f, 0f, 0f, -30f,
        0f, 1.3f, 0f, 0f, -30f,
        0f, 0f, 1.3f, 0f, -30f,
        0f, 0f, 0f, 1f, 0f
    ))
    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return result
}

private fun applyBlackWhite(src: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)
    val paint = Paint()
    val cm = ColorMatrix().apply { setSaturation(0f) }
    val contrastCm = ColorMatrix(floatArrayOf(
        2.0f, 0f, 0f, 0f, -180f,
        0f, 2.0f, 0f, 0f, -180f,
        0f, 0f, 2.0f, 0f, -180f,
        0f, 0f, 0f, 1f, 0f
    ))
    cm.postConcat(contrastCm)
    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return result
}

private fun applyColorEnhance(src: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)
    val paint = Paint()
    val cm = ColorMatrix().apply { setSaturation(1.5f) }
    val contrastCm = ColorMatrix(floatArrayOf(
        1.15f, 0f, 0f, 0f, -15f,
        0f, 1.15f, 0f, 0f, -15f,
        0f, 0f, 1.15f, 0f, -15f,
        0f, 0f, 0f, 1f, 0f
    ))
    cm.postConcat(contrastCm)
    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return result
}
