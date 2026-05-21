// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.translate

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.privateai.camera.R
import com.privateai.camera.bridge.LangItem
import com.privateai.camera.bridge.Translator
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

// LANGUAGES and LangItem live in [com.privateai.camera.bridge.Translator].
// Re-exported here so the rest of this file can keep its `LANGUAGES` /
// `LangItem` references unchanged.
private val LANGUAGES: List<LangItem> = Translator.LANGUAGES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    var sourceText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("") }
    var sourceLang by remember { mutableStateOf(LANGUAGES[0]) }
    var targetLang by remember { mutableStateOf(LANGUAGES[2]) }
    var isTranslating by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var translatedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lastImageFile by remember { mutableStateOf<File?>(null) }

    // TTS
    val ttsEngine = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) ttsEngine.value = engine
        }
        onDispose { engine?.shutdown() }
    }

    // Full-resolution camera capture
    val photoFile = remember { File(context.cacheDir, "translate_photo.jpg") }
    val photoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            lastImageFile = photoFile
            processImage(photoFile, scope, context, sourceLang, targetLang,
                onStart = { isProcessing = true; statusMessage = "Processing image..." },
                onOcrDone = { text -> sourceText = text },
                onTranslated = { text, bitmap ->
                    translatedText = text
                    translatedImageBitmap = bitmap
                    statusMessage = ""
                    isProcessing = false
                },
                onError = { msg -> statusMessage = msg; isProcessing = false }
            )
        }
    }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                val tempFile = File(context.cacheDir, "translate_picked.jpg")
                tempFile.writeBytes(bytes)
                lastImageFile = tempFile
                processImage(tempFile, scope, context, sourceLang, targetLang,
                    onStart = { isProcessing = true; statusMessage = "Processing image..." },
                    onOcrDone = { text -> sourceText = text },
                    onTranslated = { text, bitmap ->
                        translatedText = text
                        translatedImageBitmap = bitmap
                        statusMessage = ""
                        isProcessing = false
                    },
                    onError = { msg -> statusMessage = msg; isProcessing = false }
                )
            }
        }
    }

    fun translateText() {
        if (sourceText.isBlank()) return
        focusManager.clearFocus() // dismiss keyboard so translated text is visible

        // If we have an image, re-process it with new language
        val imgFile = lastImageFile
        if (imgFile != null && imgFile.exists()) {
            processImage(imgFile, scope, context, sourceLang, targetLang,
                onStart = { isTranslating = true; statusMessage = "Re-translating image..." },
                onOcrDone = { /* keep existing sourceText */ },
                onTranslated = { text, bitmap ->
                    translatedText = text
                    translatedImageBitmap?.recycle()
                    translatedImageBitmap = bitmap
                    statusMessage = ""
                    isTranslating = false
                },
                onError = { msg -> statusMessage = msg; isTranslating = false }
            )
            return
        }

        // Text-only translation. The Translator interface is implemented
        // per build flavor (Track A3) — playstore wraps ML Kit, fdroid
        // wraps Gemma. Both expose the same suspend translate() so this
        // call doesn't change between flavors. Translator instances are
        // cheap in playstore (one ML Kit client per language pair, cached
        // and reused) and a no-op in fdroid (Gemma is a process-wide
        // singleton).
        isTranslating = true
        translatedText = "" // clear old result + alternatives immediately
        statusMessage = "Translating..."
        scope.launch {
            val translator = Translator.create(context)
            try {
                val result = translator.translate(sourceText, sourceLang, targetLang)
                if (!result.isNullOrBlank()) {
                    translatedText = result
                    statusMessage = ""
                } else {
                    statusMessage = "Translation failed — the word may not exist in this language."
                }
            } catch (e: Exception) {
                statusMessage = "Translation failed: ${e.message}"
            } finally {
                isTranslating = false
                translator.close()
            }
        }
    }

    fun speakText(text: String, langCode: String) {
        val engine = ttsEngine.value ?: return
        engine.language = Locale.forLanguageTag(langCode)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts")
    }

    fun swapLanguages() {
        val tmp = sourceLang; sourceLang = targetLang; targetLang = tmp
        val tmpT = sourceText; sourceText = translatedText; translatedText = tmpT
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Translate") },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Performance warning for LOW tier devices
            if (com.privateai.camera.service.DeviceProfiler.getProfile(context).tier == com.privateai.camera.service.DeviceTier.LOW) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "⚡ Translation may be slow on this device",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Language selectors
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LanguageDropdown(sourceLang, LANGUAGES, { sourceLang = it }, Modifier.weight(1f))
                IconButton(onClick = { swapLanguages() }) { Icon(Icons.Default.SwapHoriz, "Swap") }
                LanguageDropdown(targetLang, LANGUAGES, { targetLang = it }, Modifier.weight(1f))
            }

            // Source text input
            OutlinedTextField(
                value = sourceText,
                onValueChange = { sourceText = it },
                label = { Text(sourceLang.name) },
                placeholder = { Text("Type, paste, or use camera") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                trailingIcon = {
                    if (sourceText.isNotBlank()) {
                        IconButton(onClick = { speakText(sourceText, sourceLang.code) }) {
                            Icon(Icons.Default.VolumeUp, "Listen")
                        }
                    }
                }
            )

            // AI grammar check — fix spelling/grammar before translating (only when AI active)
            val aiStatusForGrammar by com.privateai.camera.bridge.rememberAiStatus()
            val aiAvailable = aiStatusForGrammar.isReady
            var isFixingGrammar by remember { mutableStateOf(false) }
            if (aiAvailable && sourceText.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            isFixingGrammar = true
                            scope.launch {
                                val fixed = com.privateai.camera.bridge.GemmaRunner.complete(
                                    context,
                                    com.privateai.camera.bridge.GemmaPrompts.rewrite(sourceText, com.privateai.camera.bridge.RewriteStyle.FIX_GRAMMAR),
                                    com.privateai.camera.bridge.GemmaPrompts.NOTES_SYSTEM,
                                    temperature = 0.2
                                )
                                isFixingGrammar = false
                                if (!fixed.isNullOrBlank() && fixed.trim() != sourceText.trim()) {
                                    sourceText = fixed.trim()
                                    statusMessage = "Grammar fixed by AI"
                                } else {
                                    statusMessage = "No grammar issues found"
                                }
                            }
                        },
                        enabled = !isFixingGrammar && !isTranslating
                    ) {
                        if (isFixingGrammar) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                        }
                        Spacer(Modifier.size(4.dp))
                        Text("Fix grammar", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Action buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { cameraLauncher.launch(photoUri) }, enabled = !isProcessing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp))
                    Text("  Camera")
                }
                OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, enabled = !isProcessing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Image, null, Modifier.size(18.dp))
                    Text("  Gallery")
                }
            }

            // Translate buttons — ML Kit (default) + AI (when active, better for sentences/paragraphs)
            var isAiTranslating by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { translateText() },
                    enabled = sourceText.isNotBlank() && !isTranslating && !isAiTranslating,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isTranslating || isProcessing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Translate, null, Modifier.size(18.dp))
                    }
                    Text("  Translate")
                }

                // AI Translate — better for longer text, no per-language download
                if (aiAvailable) {
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus() // dismiss keyboard
                            isAiTranslating = true
                            translatedText = "" // clear old result + alternatives
                            statusMessage = "✨ AI translating..."
                            scope.launch {
                                try {
                                    val prompt = "Translate the following text from ${sourceLang.name} to ${targetLang.name}. " +
                                        "Output ONLY the translation, nothing else.\n\n" + sourceText
                                    val result = com.privateai.camera.bridge.GemmaRunner.complete(
                                        context, prompt,
                                        systemInstruction = "You are a professional translator. Output only the translated text.",
                                        maxTokens = 1024, temperature = 0.3
                                    )
                                    if (!result.isNullOrBlank()) {
                                        translatedText = result.trim()
                                        statusMessage = "✨ Translated with AI"
                                    } else {
                                        statusMessage = "AI returned empty — use Translate instead"
                                    }
                                } catch (e: Exception) {
                                    statusMessage = "AI translation failed: ${e.message}"
                                } finally {
                                    isAiTranslating = false
                                }
                            }
                        },
                        enabled = sourceText.isNotBlank() && !isTranslating && !isAiTranslating,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isAiTranslating) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                        }
                        Text("  AI")
                    }
                }
            }

            // Status
            if (statusMessage.isNotBlank()) {
                Text(statusMessage, style = MaterialTheme.typography.bodySmall,
                    color = if (statusMessage.contains("failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Translated image with overlay (Google Lens style)
            translatedImageBitmap?.let { bmp ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Translated Image", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = {
                        translatedImageBitmap?.recycle()
                        translatedImageBitmap = null
                        lastImageFile = null
                    }) {
                        Icon(Icons.Default.Close, "Remove image", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Translated image",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                )
            }

            // Translation text result — alternatives reset when source text or translated text changes
            var alternatives by remember { mutableStateOf<List<TranslationAlt>>(emptyList()) }
            var isLoadingAlts by remember { mutableStateOf(false) }
            var lastAltSource by remember { mutableStateOf("") }

            // Clear stale alternatives when source or result changes
            if (sourceText != lastAltSource) {
                alternatives = emptyList()
            }

            if (translatedText.isNotBlank()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(targetLang.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Row {
                                // "Show alternatives" button — AI only
                                if (aiAvailable && !isLoadingAlts) {
                                    IconButton(onClick = {
                                        isLoadingAlts = true
                                        alternatives = emptyList()
                                        lastAltSource = sourceText
                                        scope.launch {
                                            val alts = getAlternativeTranslations(context, sourceText, sourceLang.name, targetLang.name)
                                            alternatives = alts
                                            isLoadingAlts = false
                                        }
                                    }) {
                                        Icon(Icons.Default.AutoAwesome, "Alternatives", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                if (isLoadingAlts) {
                                    CircularProgressIndicator(Modifier.size(20.dp).padding(4.dp), strokeWidth = 2.dp)
                                }
                                IconButton(onClick = { speakText(translatedText, targetLang.code) }) { Icon(Icons.Default.VolumeUp, "Listen") }
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(translatedText))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }) { Icon(Icons.Default.ContentCopy, "Copy") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(translatedText, style = MaterialTheme.typography.bodyLarge)

                        // Alternative translations from Gemma
                        if (alternatives.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Alternatives", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            alternatives.forEach { alt ->
                                Card(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                        .clickable {
                                            translatedText = alt.text
                                            clipboardManager.setText(AnnotatedString(alt.text))
                                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(alt.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        if (alt.context.isNotBlank()) {
                                            Text(alt.context, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Process an image: OCR → translate each text block → overlay translated text on image
 */
private fun processImage(
    imageFile: File,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    sourceLang: LangItem,
    targetLang: LangItem,
    onStart: () -> Unit,
    onOcrDone: (String) -> Unit,
    onTranslated: (String, Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    onStart()
    scope.launch {
        try {
            var originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: throw Exception("Failed to load image")

            // Fix EXIF rotation
            try {
                val exif = ExifInterface(imageFile.absolutePath)
                val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (rotation != 0f) {
                    val matrix = Matrix().apply { postRotate(rotation) }
                    val rotated = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
                    originalBitmap.recycle()
                    originalBitmap = rotated
                }
            } catch (_: Exception) {}

            // OCR via Tesseract (Track A1.3 — replaces ML Kit text recognition).
            // The previous ML Kit path exposed per-block bounding boxes which
            // this screen used to draw a translation overlay on top of the
            // original photo. Tesseract4Android does expose word-level
            // boxes (TessBaseAPI.regions / words), but the multi-region
            // overlay code below is not Track A1.3's scope — for now we
            // translate the full document text and surface it via the
            // existing onOcrDone / onSuccess callbacks. The bitmap returned
            // is the original image (no overlay yet).
            val ocrText = com.privateai.camera.bridge.TesseractRecognizer
                .recognizeInstalledLanguages(context, originalBitmap)
            if (ocrText.isBlank()) {
                if (!com.privateai.camera.bridge.TesseractRecognizer.hasAnyLanguage(context)) {
                    onError(context.getString(R.string.scanner_ocr_no_languages))
                } else {
                    onError("No text found in image")
                }
                originalBitmap.recycle()
                return@launch
            }
            onOcrDone(ocrText)

            // Translate via the flavor-specific Translator (Track A3).
            // We translate paragraph-by-paragraph because per-paragraph
            // calls keep the LLM's working context small in the fdroid
            // path and the ML Kit path's per-call cost is dominated by
            // model load (already paid on first call) regardless.
            val translator = Translator.create(context)
            val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val fullTranslated = StringBuilder()
            try {
                for (block in ocrText.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }) {
                    val translatedBlock = translator.translate(block, sourceLang, targetLang)
                    if (!translatedBlock.isNullOrBlank()) fullTranslated.appendLine(translatedBlock)
                }
            } finally {
                translator.close()
            }
            onTranslated(fullTranslated.toString().trim(), resultBitmap)

        } catch (e: Exception) {
            onError("Failed: ${e.message}")
        }
    }
}

private fun calculateTextSize(text: String, box: Rect): Float {
    val boxHeight = box.height().toFloat()
    val boxWidth = box.width().toFloat()
    // Start with a size proportional to box height, then shrink if text is too wide
    var size = boxHeight * 0.6f
    val paint = Paint().apply { textSize = size }
    val lines = text.split("\n", " ").filter { it.isNotBlank() }
    val maxLineWidth = lines.maxOfOrNull { paint.measureText(it) } ?: boxWidth

    if (maxLineWidth > boxWidth) {
        size *= (boxWidth / maxLineWidth) * 0.9f
    }
    return size.coerceIn(12f, boxHeight * 0.8f)
}

private fun drawTextInBox(canvas: Canvas, text: String, box: Rect, paint: Paint) {
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var currentLine = ""

    for (word in words) {
        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
        if (paint.measureText(testLine) <= box.width()) {
            currentLine = testLine
        } else {
            if (currentLine.isNotEmpty()) lines.add(currentLine)
            currentLine = word
        }
    }
    if (currentLine.isNotEmpty()) lines.add(currentLine)

    val lineHeight = paint.textSize * 1.2f
    val totalHeight = lines.size * lineHeight
    var y = box.top + (box.height() - totalHeight) / 2 + paint.textSize

    for (line in lines) {
        val x = box.left + (box.width() - paint.measureText(line)) / 2
        canvas.drawText(line, x, y, paint)
        y += lineHeight
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    selected: LangItem,
    languages: List<LangItem>,
    onSelect: (LangItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected.name, onValueChange = {}, readOnly = true, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.forEach { lang ->
                DropdownMenuItem(text = { Text(lang.name) }, onClick = { onSelect(lang); expanded = false })
            }
        }
    }
}

/** A single translation alternative with optional context note. */
private data class TranslationAlt(val text: String, val context: String)

/**
 * Ask Gemma for 2-4 alternative translations with context notes.
 * Returns parsed alternatives, or empty list if AI fails.
 */
private suspend fun getAlternativeTranslations(
    context: android.content.Context,
    sourceText: String,
    sourceLang: String,
    targetLang: String
): List<TranslationAlt> {
    val prompt = buildString {
        append("Provide 2-4 alternative translations of the following text from $sourceLang to $targetLang.\n")
        append("For each alternative, give the translation and a SHORT context note (3-5 words) explaining when to use it.\n")
        append("Format each line as: translation — context note\n")
        append("Output ONLY the numbered list, nothing else.\n\n")
        append("Text: $sourceText")
    }
    val raw = com.privateai.camera.bridge.GemmaRunner.complete(
        context, prompt,
        systemInstruction = "You are a professional translator. Output only the numbered alternatives list.",
        maxTokens = 512, temperature = 0.5
    ) ?: return emptyList()

    // Parse lines like "1. بنك — financial institution" or "- ضفة — river bank"
    return raw.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            // Strip leading numbering: "1. ", "1) ", "- ", "• "
            val cleaned = line.replace(Regex("^\\d+[.)\\-]\\s*"), "")
                .removePrefix("- ").removePrefix("• ").trim()
            if (cleaned.isBlank()) return@mapNotNull null
            val parts = cleaned.split(Regex("\\s*[—–-]\\s*"), limit = 2)
            val text = parts[0].trim()
            val ctx = parts.getOrNull(1)?.trim() ?: ""
            if (text.isNotBlank()) TranslationAlt(text, ctx) else null
        }
        .take(4)
}
