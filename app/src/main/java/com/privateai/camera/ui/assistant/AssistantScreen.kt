// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.privateai.camera.R
import com.privateai.camera.bridge.AssistantPrompts
import com.privateai.camera.bridge.AssistantTools
import com.privateai.camera.bridge.GemmaRunner
import com.privateai.camera.bridge.KnowledgeSnapshot
import com.privateai.camera.bridge.ParsedReply
import com.privateai.camera.bridge.ProposedAction
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "AssistantScreen"

/**
 * Top-level AI Assistant chat surface.
 *
 * - Entry: ✨ icon in Home top bar (Grid + Tabs layouts).
 * - Chat history: in-memory only, clears when user navigates away.
 * - Per-turn: builds a fresh [KnowledgeSnapshot], sends it + user message
 *   to Gemma, handles optional tool calls (search_notes), renders answer.
 */
/**
 * Singleton session holder — survives navigation away + back.
 * Cleared only when the app process dies (privacy: nothing on disk).
 */
private object AssistantSession {
    val messages = mutableStateListOf<ChatMessage>()
    /**
     * The vault document the user is currently asking about, if any.
     * Set when the user enters the assistant via the vault PDF viewer's
     * "Summarize" / "Ask the Assistant" entries. Bound to the chat session
     * so follow-up questions ("now translate this", "give me the next part")
     * stay grounded without re-specifying the doc id.
     *
     * The id is the encrypted-file basename (e.g. `scan_1714944000000.pdf`).
     * Using the id directly in tool calls fails on Gemma 4 E2B — the model
     * truncates the 13-digit timestamp. We bypass that by injecting the OCR
     * text into the prompt directly (see `runAssistantTurn`).
     *
     * Backed by a Compose `mutableStateOf` so the attached-doc chip can
     * react when the user taps × to detach.
     */
    private val _attachedDocId = mutableStateOf<String?>(null)
    var attachedDocId: String?
        get() = _attachedDocId.value
        set(value) { _attachedDocId.value = value }

    /**
     * Image the user has attached for the next message via the input-row
     * paperclip button. One image at a time. Held as a content URI; the
     * send path decrypts/copies it to a temp file before handing to
     * [com.privateai.camera.bridge.GemmaRunner.describeImage].
     *
     * Cleared after a successful send so the next turn defaults back to
     * pure-text mode. The user can also tap × on the preview chip to
     * detach without sending.
     */
    private val _attachedImageUri = mutableStateOf<android.net.Uri?>(null)
    var attachedImageUri: android.net.Uri?
        get() = _attachedImageUri.value
        set(value) { _attachedImageUri.value = value }

    /**
     * OCR text from a PDF the user attached from device storage (SAF picker).
     * When non-null, runAssistantTurn uses this text directly instead of
     * loading from a vault sidecar — the device PDF isn't in the vault.
     * Cleared when the user detaches via × or attaches a different doc/image.
     */
    private val _attachedDocText = mutableStateOf<String?>(null)
    var attachedDocText: String?
        get() = _attachedDocText.value
        set(value) { _attachedDocText.value = value }

    /**
     * Display name for the attached doc chip. Set alongside [attachedDocText]
     * for device PDFs (where there's no vault id to fall back to). Left null
     * for vault PDFs — chip derives display from [attachedDocId].
     */
    private val _attachedDocName = mutableStateOf<String?>(null)
    var attachedDocName: String?
        get() = _attachedDocName.value
        set(value) { _attachedDocName.value = value }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    onBack: (() -> Unit)? = null,
    onNavigate: ((route: String) -> Unit)? = null,
    seedPrompt: String? = null,
    attachedDocId: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = AssistantSession.messages
    var thinking by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var showLanguagePicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Vault lock state — recomputed each composition (no StateFlow on
    // VaultLockManager, just function reads). When `vaultLocked` is true,
    // the Assistant runs in text-only mode: empty snapshot, vault picker
    // disabled, data tools refuse to run. The user reaches this state by
    // tapping the Assistant tile on Home with the "Allow Assistant without
    // unlocking vault" setting enabled.
    var vaultLockTick by remember { mutableStateOf(0) }
    val vaultLocked = remember(vaultLockTick) {
        !com.privateai.camera.security.VaultLockManager.isUnlockedWithinGrace(context)
    }
    var showPinDialog by remember { mutableStateOf(false) }

    // Full-screen image zoom — set when the user taps any thumbnail in the
    // chat (attached-image preview, sent user image, or a search-result
    // thumb). Carries the whole strip so the user can swipe between sibling
    // photos, plus the initial index. Cleared on dismiss / back / outside tap.
    var zoomTarget by remember { mutableStateOf<ZoomTarget?>(null) }

    // The in-flight assistant Job — set when sendMessage() launches a turn,
    // cancelled when the user taps Stop while the model is still thinking.
    // Cancelling propagates down to GemmaRunner.completeStreaming's Flow
    // collect, which short-circuits the LiteRT-LM stream the same way the
    // repetition-loop guard does.
    var inflightJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // True once the user taps Stop on the current turn; cleared on the next
    // send. The image-attached path can't observe CancellationException
    // because GemmaRunner.describeImage runs a blocking native call whose
    // catch handler turns interruption into a `null` return — so we read
    // this flag in the post-await branch to decide between "Stopped" and
    // "Something went wrong".
    var cancelRequested by remember { mutableStateOf(false) }
    // True while the in-flight turn is running a vision call (synchronous
    // describeImage). External close of a vision conversation mid-call is a
    // native use-after-free in LiteRT-LM and produces a silent SIGSEGV
    // ("crash without crash report") — see comment in stopInflight().
    var visionInflight by remember { mutableStateOf(false) }
    // True after Stop is tapped, until the in-flight job's finally clause
    // actually completes. Send is disabled in this window so the user can't
    // race a new request against the still-draining native call — that
    // race produced "something went wrong" replies after Stop on a long
    // vision turn, because the new call entered the engine before the old
    // one had fully released its native state. Cleared by the in-flight
    // job's finally (both image and text paths).
    var cancelling by remember { mutableStateOf(false) }
    fun stopInflight() {
        // Set the soft flags first. GemmaRunner.completeStreaming wraps its
        // LiteRT-LM collect in NonCancellable + polls outerJob.isActive, so
        // cancelling the outer Job here only makes us "go silent" on emit;
        // LiteRT-LM's upstream keeps generating to its natural end (avoids
        // the tombstoned SIGSEGV in liblitertlm_jni.so we hit when cancel
        // propagated into the library mid-warmup). The mutex inside
        // GemmaRunner stays held until the natural completion, which
        // serialises the next call automatically — Send stays disabled
        // (`cancelling = true`) until the OLD job's finally clears it.
        cancelRequested = true
        cancelling = true
        val jobToWait = inflightJob
        // This is the signal GemmaRunner watches via outerJob.isActive — it
        // stops emitting tokens to us and silently drains the rest. No
        // close, no upstream cancel into LiteRT-LM.
        inflightJob?.cancel()
        thinking = false

        scope.launch {
            // Hold the cancelling gate until the OLD job has actually
            // finished (mutex released, conversation closed naturally).
            try { jobToWait?.join() } catch (_: Exception) {}
            cancelling = false
        }
    }

    // Pre-warm the Gemma engine so the first reply doesn't pay cold-load latency
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { GemmaRunner.load(context) }
    }

    // Pending capture URI for the system camera. We allocate a fresh temp
    // file in cacheDir + FileProvider URI BEFORE launching the camera so
    // we can read the result back as the same content://. Held outside
    // the launcher closure because TakePicture's callback only returns a
    // success boolean, not the URI we passed in.
    var pendingCaptureUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val captured = pendingCaptureUri
        pendingCaptureUri = null
        if (success && captured != null) {
            // Same mutex rules as the gallery / vault paths — attaching an
            // image clears any previously-attached doc and prior image.
            AssistantSession.attachedDocId = null
            AssistantSession.attachedDocText = null
            AssistantSession.attachedDocName = null
            AssistantSession.attachedImageUri = captured
        }
    }
    var pendingCameraAfterPermission by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            pendingCameraAfterPermission = false
            android.widget.Toast.makeText(
                context, context.getString(R.string.assistant_camera_permission_denied),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    fun launchCameraCapture() {
        val tempFile = File(context.cacheDir, "assistant_capture_${System.currentTimeMillis()}.jpg")
        try { tempFile.createNewFile() } catch (e: Exception) {
            Log.w(TAG, "create temp capture file failed: ${e.message}")
            return
        }
        val uri = try {
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", tempFile
            )
        } catch (e: Exception) {
            Log.w(TAG, "FileProvider.getUriForFile failed: ${e.message}")
            null
        } ?: return
        pendingCaptureUri = uri
        try { cameraCaptureLauncher.launch(uri) } catch (e: Exception) {
            Log.w(TAG, "cameraCaptureLauncher.launch failed: ${e.message}")
            pendingCaptureUri = null
        }
    }
    LaunchedEffect(pendingCameraAfterPermission) {
        if (pendingCameraAfterPermission) {
            pendingCameraAfterPermission = false
            launchCameraCapture()
        }
    }

    // Photo-picker launcher for the image-attach (paperclip) button in the
    // input row. Uses the modern PickVisualMedia contract (no storage
    // permission needed; system picker isolates the gallery). The picked
    // URI is stashed on AssistantSession so the preview chip + send path
    // can read it. One image at a time; picking again replaces.
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // Image + PDF attachments are mutually exclusive. Attaching an
            // image clears any previously-attached document so the next
            // turn isn't double-grounded ("translate last message" with a
            // PDF still attached was hitting the doc's "I can't find that"
            // safety prompt). Clears BOTH vault-doc id and device-doc text.
            AssistantSession.attachedDocId = null
            AssistantSession.attachedDocText = null
            AssistantSession.attachedDocName = null
            AssistantSession.attachedImageUri = uri
        }
    }

    // Device-PDF picker (SAF) for the "PDF from device" attach option. The
    // user picks any PDF on the device; we copy it to cache, run OCR over
    // it (PdfBox + ML Kit fallback), then stash the extracted text on the
    // session so runAssistantTurn can ground subsequent turns. We don't
    // import to the vault — this is a one-session attachment, not a save.
    var pdfExtracting by remember { mutableStateOf(false) }
    val pdfDevicePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pdfExtracting = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val cacheFile = File(context.cacheDir, "assistant_pdf_${System.currentTimeMillis()}.pdf")
                try {
                    context.contentResolver.openInputStream(uri)?.use { ins ->
                        cacheFile.outputStream().use { out -> ins.copyTo(out) }
                    } ?: return@withContext null
                    if (!cacheFile.exists() || cacheFile.length() == 0L) return@withContext null
                    val crypto = CryptoManager(context).also { it.initialize() }
                    if (!crypto.isUnlocked()) return@withContext null
                    val vault = com.privateai.camera.security.VaultRepository(context, crypto)
                    val text = vault.extractOcrTextFromPdfFile(cacheFile)
                    val displayName = run {
                        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                        if (!name.isNullOrBlank()) name else "Document"
                    }
                    Pair(text, displayName)
                } catch (e: Exception) {
                    Log.w(TAG, "device PDF extract failed: ${e.message}")
                    null
                } finally {
                    try { cacheFile.delete() } catch (_: Exception) {}
                }
            }
            pdfExtracting = false
            val text = ok?.first
            val name = ok?.second
            if (text.isNullOrBlank()) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.assistant_attach_pdf_no_text),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } else {
                AssistantSession.attachedImageUri = null
                AssistantSession.attachedDocId = null
                AssistantSession.attachedDocText = text
                AssistantSession.attachedDocName = name
            }
        }
    }
    // Read the attached image reactively so the preview chip + send-button
    // enabled state recompose when the user attaches or clears.
    val attachedImage: android.net.Uri? = AssistantSession.attachedImageUri
    var showAttachMenu by remember { mutableStateOf(false) }
    // Which media type the vault picker should filter on. null = legacy
    // (both), set to PHOTO or PDF when launched from the dedicated menu items.
    var showVaultPicker by remember { mutableStateOf(false) }
    var vaultPickerFilter by remember { mutableStateOf<com.privateai.camera.security.VaultMediaType?>(null) }
    // Decoded thumbnail for the preview chip — cached in memory while the
    // URI is attached. Decoded once via ContentResolver.openInputStream.
    val attachedImageThumb = remember(attachedImage) {
        if (attachedImage == null) null
        else try {
            context.contentResolver.openInputStream(attachedImage)?.use { ins ->
                android.graphics.BitmapFactory.decodeStream(ins)
            }
        } catch (e: Exception) { Log.w(TAG, "thumb decode failed: ${e.message}"); null }
    }

    // ── Track B — Voice input (mic button → SpeechRecognizer) ─────────────
    // Permission gate, recognizer instance, listening flag. The recognizer
    // hands partials to onPartialResults so we can stream the transcript
    // into `inputText` as the user speaks; final results overwrite. The
    // user reviews + sends manually (no accidental auto-send).
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingMicAfterPermission by remember { mutableStateOf(false) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (!granted) {
            pendingMicAfterPermission = false
            android.widget.Toast.makeText(
                context, context.getString(R.string.assistant_voice_permission_denied),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    var listening by remember { mutableStateOf(false) }
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context)
        else null
    }
    DisposableEffect(speechRecognizer) {
        onDispose { try { speechRecognizer?.destroy() } catch (_: Exception) {} }
    }
    // Recognizer callbacks — `partial` mirrors live partial transcript into
    // the input; `final` overwrites with the cleaned recognized text. Both
    // ignore empty / whitespace-only results to avoid blowing away the
    // current draft when the recognizer reports no speech.
    fun startListening() {
        val sr = speechRecognizer ?: run {
            android.widget.Toast.makeText(
                context, context.getString(R.string.assistant_voice_unavailable),
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p0: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                listening = false
                Log.w(TAG, "SpeechRecognizer error: $error")
            }
            override fun onResults(results: android.os.Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) inputText = text
                listening = false
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) inputText = text
            }
            override fun onEvent(p0: Int, p1: android.os.Bundle?) {}
        })
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        listening = true
        try { sr.startListening(intent) } catch (e: Exception) {
            Log.w(TAG, "startListening failed: ${e.message}"); listening = false
        }
    }
    fun stopListening() {
        try { speechRecognizer?.stopListening() } catch (_: Exception) {}
        listening = false
    }
    // Permission-grant trigger: when the user taps the mic while permission
    // was missing, we set `pendingMicAfterPermission` and request — once
    // granted, this effect kicks the listener on.
    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission && pendingMicAfterPermission) {
            pendingMicAfterPermission = false
            startListening()
        }
    }

    // ── Track B — Voice output (TTS) ──────────────────────────────────────
    // Engine + speaking flag. Settings toggle `voice_output_enabled` gates
    // auto-speak of assistant chunks; the per-bubble replay icon + top-bar
    // speaker toggle stay functional regardless of the toggle.
    val ttsEngine = remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsSpeaking by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = java.util.Locale.getDefault()
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post { ttsSpeaking = true }
                    }
                    override fun onDone(utteranceId: String?) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post { ttsSpeaking = false }
                    }
                    @Deprecated("legacy") override fun onError(utteranceId: String?) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post { ttsSpeaking = false }
                    }
                })
                ttsEngine.value = engine
            }
        }
        onDispose { engine?.shutdown() }
    }
    fun speakOnce(text: String) {
        val eng = ttsEngine.value ?: return
        if (text.isBlank()) return
        eng.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant-${System.currentTimeMillis()}")
    }
    fun stopSpeaking() {
        try { ttsEngine.value?.stop() } catch (_: Exception) {}
        ttsSpeaking = false
    }
    val voiceOutputAuto = remember {
        context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
            .getBoolean("voice_output_enabled", false)
    }

    // Deep-link: bind the doc id to the session SYNCHRONOUSLY during
    // composition (via remember keyed on attachedDocId) so the attached-doc
    // chip renders on the first frame, not after a LaunchedEffect fires.
    // The session backing is a Compose mutableStateOf, so writes are
    // observed by the chip's reader and trigger recomposition cleanly.
    // Doc-switch behaviour: wipe the chat when moving between different
    // docs so prior summaries don't bias the next one; first attach (prior
    // session state is null) preserves any unrelated chat.
    remember(attachedDocId) {
        if (!attachedDocId.isNullOrBlank()) {
            val previous = AssistantSession.attachedDocId
            if (previous != null && previous != attachedDocId) {
                AssistantSession.messages.clear()
            }
            AssistantSession.attachedDocId = attachedDocId
        }
        Unit
    }

    // Seed prompt fill — runs once per new attachedDocId. The seed is a
    // UI-state side effect (writes to inputText), so it lives in
    // LaunchedEffect rather than the synchronous remember block above.
    var seedConsumed by remember(attachedDocId) { mutableStateOf(false) }
    LaunchedEffect(seedPrompt, attachedDocId) {
        if (!seedConsumed && !seedPrompt.isNullOrBlank()) {
            inputText = seedPrompt
            seedConsumed = true
        }
    }

    // Auto-scroll to bottom when a new message is added
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage() {
        val text = inputText.trim()
        val pendingImage = AssistantSession.attachedImageUri
        // An attached image counts as content too — accept image-only sends
        // (defaults to "Describe this image").
        if (text.isEmpty() && pendingImage == null) return
        if (thinking) return
        inputText = ""
        // Fresh turn — clear the cancel flag from any prior stop so the new
        // reply isn't mis-labelled as "Stopped".
        cancelRequested = false
        // Image-attached send: bypass the regular runAssistantTurn pipeline
        // (snapshot + tools + multi-turn) and call describeImage directly.
        // Single-shot vision pass; the user's typed text becomes the prompt
        // (or a localized "Describe this image" default).
        if (pendingImage != null) {
            // Capture the user's bitmap once so the chat bubble can render
            // it even after the URI is detached. PickVisualMedia's grant
            // may not survive past the send.
            val capturedBitmap = attachedImageThumb
            // Image stays attached across follow-up turns so "what color is
            // the rabbit?" → "what are they doing?" both see the same photo.
            // User explicitly detaches via × on the preview chip or by
            // attaching a different image. Tracked: previously cleared here,
            // which broke multi-question flows about the same picture.
            val displayText = text.ifBlank { context.getString(R.string.assistant_image_default_prompt) }
            messages += ChatMessage.User(displayText, image = capturedBitmap)
            thinking = true
            visionInflight = true
            // Capture a reference to THIS launch's job so the finally can
            // check whether it's still the current in-flight one. Race:
            // user stops the first call (which can't be killed mid-native),
            // sends a second, then the FIRST call's finally finally runs
            // and would otherwise stomp on the SECOND's `thinking=true` /
            // `visionInflight=true` / `inflightJob=secondJob` state.
            lateinit var thisJob: kotlinx.coroutines.Job
            thisJob = scope.launch {
                try {
                    val reply = withContext(Dispatchers.IO) {
                        runImageQueryTurn(context, pendingImage, displayText)
                    }
                    // describeImage swallows CancellationException internally
                    // and returns null. We can't distinguish "user stopped"
                    // from "model failed" by reply value alone — check the
                    // cancel flag instead so a stopped turn shows "Stopped",
                    // not the generic "something went wrong".
                    val replyText = when {
                        cancelRequested -> context.getString(R.string.assistant_stopped)
                        reply != null -> reply
                        else -> context.getString(R.string.assistant_error_generic)
                    }
                    messages += ChatMessage.Assistant(replyText, emptyList())
                    if (voiceOutputAuto && !cancelRequested && reply != null) speakOnce(replyText)
                } catch (_: kotlinx.coroutines.CancellationException) {
                    messages += ChatMessage.Assistant(
                        context.getString(R.string.assistant_stopped), emptyList()
                    )
                } finally {
                    // Only clear state if THIS is still the current job.
                    // A newer job may have replaced us already.
                    if (inflightJob === thisJob) {
                        thinking = false
                        inflightJob = null
                        visionInflight = false
                    }
                    // The cancelling gate releases whenever the cancelled
                    // job actually finishes, regardless of who replaced it.
                    // Send was disabled until this point so the next user
                    // request lands on a fully-idle engine.
                    cancelling = false
                }
            }
            inflightJob = thisJob
            return
        }
        if (text.isEmpty()) return
        messages += ChatMessage.User(text)
        thinking = true

        // The trailing function body lives below — keep this declaration as the
        // single source of truth for the message-send flow; quick-action chips
        // route through here by prepending a directive and calling sendMessage().

        // Streaming index is allocated lazily — set when the first non-empty
        // chunk arrives, so the typing indicator is replaced cleanly by a real
        // bubble (no empty placeholder rendered alongside it).
        var streamIndex = -1

        lateinit var textJob: kotlinx.coroutines.Job
        textJob = scope.launch {
            try {
                val historySnapshot = messages.toList()
                val turn = withContext(Dispatchers.IO) {
                    runAssistantTurn(context, historySnapshot, text) { partial ->
                        if (partial.isEmpty()) return@runAssistantTurn
                        // Tokens arrive on IO. Hop to main to mutate Compose state.
                        scope.launch {
                            if (streamIndex < 0) {
                                streamIndex = messages.size
                                messages += ChatMessage.Assistant(partial, emptyList())
                                thinking = false
                            } else if (streamIndex < messages.size) {
                                messages[streamIndex] = ChatMessage.Assistant(partial, emptyList())
                            }
                        }
                    }
                }
                // If the user tapped Stop, the inner Gemma catch may have
                // swallowed the cancellation and returned a half-finished
                // string. Honor cancelRequested so the bubble reads
                // "Stopped" instead of the truncated model output.
                if (cancelRequested) {
                    val partial = if (streamIndex >= 0 && streamIndex < messages.size) {
                        (messages[streamIndex] as? ChatMessage.Assistant)?.text.orEmpty()
                    } else ""
                    val stoppedText = context.getString(R.string.assistant_stopped)
                    val combined = if (partial.isBlank()) stoppedText
                                   else "$partial\n\n_${stoppedText}_"
                    val stoppedMsg = ChatMessage.Assistant(combined, emptyList())
                    if (streamIndex < 0) messages += stoppedMsg
                    else if (streamIndex < messages.size) messages[streamIndex] = stoppedMsg
                    return@launch
                }
                // Final pass: replace the streaming bubble with the cleaned
                // answer + any proposed action (or append a fresh one if nothing
                // streamed — e.g. the model emitted only an action JSON we held back).
                val finalMsg = ChatMessage.Assistant(turn.text, turn.refs, turn.action, photoThumbs = turn.photoThumbs)
                if (streamIndex < 0) {
                    messages += finalMsg
                } else if (streamIndex < messages.size) {
                    messages[streamIndex] = finalMsg
                }
                // Auto-speak the final reply when Settings → Voice output is on.
                if (voiceOutputAuto && turn.text.isNotBlank()) speakOnce(turn.text)
            } catch (_: kotlinx.coroutines.CancellationException) {
                // User tapped Stop. Replace the partial streaming bubble (if
                // any) with a "Stopped" marker so the chat doesn't end on a
                // truncated half-sentence. If no chunks streamed yet, append
                // a fresh marker bubble.
                val stoppedText = context.getString(R.string.assistant_stopped)
                val partial = if (streamIndex >= 0 && streamIndex < messages.size) {
                    (messages[streamIndex] as? ChatMessage.Assistant)?.text.orEmpty()
                } else ""
                val combined = if (partial.isBlank()) stoppedText else "$partial\n\n_${stoppedText}_"
                val stoppedMsg = ChatMessage.Assistant(combined, emptyList())
                if (streamIndex < 0) messages += stoppedMsg
                else if (streamIndex < messages.size) messages[streamIndex] = stoppedMsg
            } catch (e: Exception) {
                Log.e(TAG, "Assistant error: ${e.message}", e)
                val errMsg = ChatMessage.Assistant(context.getString(R.string.assistant_error_generic), emptyList())
                if (streamIndex < 0) messages += errMsg
                else if (streamIndex < messages.size) messages[streamIndex] = errMsg
            } finally {
                // Only clear state if THIS launch is still the active one.
                // After Stop + new send, the old launch's finally must not
                // overwrite the new launch's thinking=true / inflightJob=ref.
                if (inflightJob === textJob) {
                    thinking = false
                    inflightJob = null
                }
                // Release the cancelling gate whenever any in-flight job
                // ends (cancelled or not). This is what re-enables Send
                // after the user taps Stop and the native call has finally
                // returned. Cleared unconditionally — if a fresh send is
                // racing this finally, it sets cancelling=false at its
                // own start too, so no harm.
                cancelling = false
            }
        }
        inflightJob = textJob
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.assistant_title))
                    }
                },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // Top-bar lock icon — mirrors the lock toggle on Home.
                    // Visible only when the vault is currently unlocked AND
                    // the unlocked-access setting is on (i.e. the user opted
                    // into locked-mode Assistant — they probably want a way
                    // to re-lock from here too). Tapping re-locks the vault
                    // and stays inside the Assistant; the banner appears.
                    val unlockedAccessOn = remember(vaultLockTick) {
                        com.privateai.camera.ui.settings.isAssistantUnlockedAccessEnabled(context)
                    }
                    if (!vaultLocked && unlockedAccessOn) {
                        IconButton(onClick = {
                            com.privateai.camera.security.VaultLockManager.lock()
                            vaultLockTick++
                        }) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = stringResource(R.string.action_lock),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    // Top-bar speaker toggle — only visible while TTS is
                    // mid-reply. Tap to stop. The toggle is intentionally
                    // hidden when idle so the bar doesn't carry a dead icon
                    // when no reply is being spoken.
                    if (ttsSpeaking) {
                        IconButton(onClick = { stopSpeaking() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = stringResource(R.string.assistant_voice_stop_speaking),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    // "New chat" — clears the session so the user starts fresh
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = {
                            messages.clear()
                            stopSpeaking()
                            // Drop all doc + image bindings when starting a fresh
                            // chat — the user shouldn't keep accidentally answering
                            // against the last attachment.
                            AssistantSession.attachedDocId = null
                            AssistantSession.attachedDocText = null
                            AssistantSession.attachedDocName = null
                            AssistantSession.attachedImageUri = null
                        }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.assistant_new_chat),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // Locked-vault banner — visible whenever the user reached this
            // screen with the vault still locked (Settings → "Allow Assistant
            // without unlocking vault"). Explains why the snapshot is empty
            // and exposes an Unlock button that fires the standard PIN
            // dialog. Tapping Unlock keeps the user inside the Assistant,
            // they just gain full data access after the PIN check.
            if (vaultLocked) {
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            stringResource(R.string.assistant_vault_locked_banner),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.TextButton(
                            onClick = { showPinDialog = true }
                        ) {
                            Text(stringResource(R.string.action_unlock))
                        }
                    }
                }
            }
            // Attached-document chip — visible while EITHER a vault doc id
            // OR a device-doc text blob is bound to the session. Tap × to
            // detach: clears the session binding so the next message is a
            // normal chat, not a doc Q&A. The chat history is preserved.
            val currentAttachedDoc = AssistantSession.attachedDocId
            val currentAttachedDocText = AssistantSession.attachedDocText
            val currentAttachedDocName = AssistantSession.attachedDocName
            val docChipVisible = !currentAttachedDoc.isNullOrBlank() || !currentAttachedDocText.isNullOrBlank()
            if (docChipVisible) {
                val displayName = currentAttachedDocName
                    ?: currentAttachedDoc?.removeSuffix(".pdf")?.removeSuffix(".PDF")
                    ?: "Document"
                androidx.compose.material3.AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            stringResource(R.string.assistant_attached_doc, displayName),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Description,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                AssistantSession.attachedDocId = null
                                AssistantSession.attachedDocText = null
                                AssistantSession.attachedDocName = null
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                stringResource(R.string.assistant_attached_doc_detach),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                // OCR-language disclaimer. ML Kit Text Recognition v2 (Latin
                // only) silently drops Arabic / Hebrew / CJK / Thai etc., so
                // for mixed-script docs the OCR text is incomplete — and
                // Gemma can't tell because there are no gap markers to look
                // for. Surfacing the limitation to the user directly is the
                // honest move until non-Latin OCR (Tesseract or equivalent)
                // is added.
                Text(
                    stringResource(R.string.assistant_attached_doc_latin_only),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
            // Device-PDF extraction progress strip — visible only while
            // OCR is running over a freshly picked device PDF. Native text
            // PDFs finish in <200ms; rendered/scanned PDFs can take 5-30s.
            if (pdfExtracting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        stringResource(R.string.assistant_attach_pdf_extracting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Chat messages list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                state = listState
            ) {
                // Empty state with example chips
                if (messages.isEmpty() && !thinking) {
                    item {
                        Spacer(Modifier.height(32.dp))
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                stringResource(R.string.assistant_empty_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.assistant_try_label),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val examples = listOf(
                                stringResource(R.string.assistant_example_week),
                                stringResource(R.string.assistant_example_search),
                                stringResource(R.string.assistant_example_summarize),
                                stringResource(R.string.assistant_example_habits)
                            )
                            examples.forEach { example ->
                                AssistChip(
                                    onClick = { inputText = example },
                                    label = { Text(example, style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }
                    }
                }

                itemsIndexed(messages) { msgIndex, msg ->
                    ChatBubble(
                        message = msg,
                        onRefClick = { ref ->
                            val route = when (ref.kind) {
                                RefKind.NOTE -> "notes?openNoteId=${ref.id}"
                                RefKind.REMINDER -> "reminders"
                                RefKind.HABIT -> "insights?tab=habits"
                                RefKind.HEALTH -> "health"
                            }
                            onNavigate?.invoke(route)
                        },
                        onActionConfirm = { action ->
                            val current = messages.getOrNull(msgIndex) as? ChatMessage.Assistant ?: return@ChatBubble
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    com.privateai.camera.bridge.AssistantActions.execute(context, action)
                                }
                                val updated = current.copy(
                                    actionStatus = if (ok) ActionStatus.ADDED else ActionStatus.FAILED
                                )
                                if (msgIndex < messages.size) messages[msgIndex] = updated
                            }
                        },
                        onActionDismiss = {
                            val current = messages.getOrNull(msgIndex) as? ChatMessage.Assistant ?: return@ChatBubble
                            messages[msgIndex] = current.copy(actionStatus = ActionStatus.DISMISSED)
                        },
                        onSaveAsNote = { replyText ->
                            // Guard against re-taps before the save completes.
                            // The button itself disables when savedAsNote becomes
                            // true, but this catches concurrent taps too.
                            val cur = messages.getOrNull(msgIndex) as? ChatMessage.Assistant
                            if (cur == null || cur.savedAsNote) return@ChatBubble
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    try {
                                        val crypto = CryptoManager(context).also { it.initialize() }
                                        val noteRepo = NoteRepository(File(context.filesDir, "vault/notes"), crypto)
                                        val title = replyText.lineSequence().firstOrNull { it.isNotBlank() }
                                            ?.take(60)?.trim()
                                            ?: context.getString(R.string.assistant_saved_default_title)
                                        noteRepo.createNote(
                                            title = title,
                                            content = replyText,
                                            tags = listOf("assistant")
                                        )
                                        true
                                    } catch (_: Exception) { false }
                                }
                                if (ok && msgIndex < messages.size) {
                                    val updated = (messages[msgIndex] as? ChatMessage.Assistant)?.copy(savedAsNote = true)
                                    if (updated != null) messages[msgIndex] = updated
                                }
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(
                                        if (ok) R.string.assistant_saved_to_notes
                                        else R.string.note_save_failed
                                    ),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onPhotoClick = { photoId ->
                            // Long-press a search-result thumb → deep-link the
                            // Vault to auto-open this photo (full metadata +
                            // share actions). Short tap goes to onImageZoom.
                            onNavigate?.invoke("vault?openPhotoId=$photoId")
                        },
                        onSpeak = { replyText ->
                            // Per-bubble TTS replay — works regardless of the
                            // auto-speak setting. If TTS is already speaking
                            // (this bubble or another), QUEUE_FLUSH replaces.
                            speakOnce(replyText)
                        },
                        onImageZoom = { target -> zoomTarget = target }
                    )
                }

                if (thinking) {
                    item { ThinkingBubble() }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }

            // Attached image preview — sits between the chat list and the
            // input row when the user has picked an image for the next
            // message. Tap × to detach without sending.
            if (attachedImage != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (attachedImageThumb != null) {
                        Image(
                            bitmap = attachedImageThumb.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    attachedImageThumb?.let { bmp ->
                                        zoomTarget = ZoomTarget(
                                            listOf(ZoomSource.RawBitmap(bmp)), 0
                                        )
                                    }
                                }
                        )
                    } else {
                        Icon(
                            Icons.Default.Image, null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        stringResource(R.string.assistant_attached_image),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    // Labeled "Remove photo" action — replaces the bare × icon
                    // so users know how to stop follow-up questions about the
                    // attached image without having to discover the close icon.
                    androidx.compose.material3.TextButton(
                        onClick = { AssistantSession.attachedImageUri = null }
                    ) {
                        Icon(
                            Icons.Default.Close,
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            stringResource(R.string.assistant_remove_photo),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // Input bar — ChatGPT-style pill: a single rounded Surface that
            // wraps a leading "+" attach button, an expanding text field
            // (1 line at rest, grows up to 6 lines), and a trailing send
            // circle. No more 110dp tall block by default — the input only
            // takes the height the user's text needs.
            val canSend = (inputText.isNotBlank() || attachedImage != null) && !thinking && !cancelling
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Leading "+" attach button → dropdown (Device gallery /
                    // Privora vault). Same enable / disable logic as before.
                    Box {
                        IconButton(
                            onClick = { showAttachMenu = true },
                            enabled = !thinking
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.assistant_attach_image),
                                tint = if (!thinking) MaterialTheme.colorScheme.onSurfaceVariant
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                        DropdownMenu(
                            expanded = showAttachMenu,
                            onDismissRequest = { showAttachMenu = false }
                        ) {
                            // Device row — both items use system pickers
                            // (PickVisualMedia + SAF GetContent). No vault
                            // unlock needed.
                            // Take photo — system camera, captures directly
                            // into a cache temp file via FileProvider URI.
                            // Asks for CAMERA permission on first use.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.assistant_attach_take_photo)) },
                                leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
                                onClick = {
                                    showAttachMenu = false
                                    val hasCamera = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasCamera) {
                                        launchCameraCapture()
                                    } else {
                                        pendingCameraAfterPermission = true
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.assistant_attach_photo_device)) },
                                leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
                                onClick = {
                                    showAttachMenu = false
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.assistant_attach_pdf_device)) },
                                leadingIcon = { Icon(Icons.Default.Description, null) },
                                onClick = {
                                    showAttachMenu = false
                                    pdfDevicePickerLauncher.launch("application/pdf")
                                }
                            )
                            androidx.compose.material3.HorizontalDivider()
                            // Vault row — both items open the in-app picker,
                            // filtered to one media type each. Disabled while
                            // the vault is locked (Settings → "Allow Assistant
                            // without unlocking vault"): tap shows a toast
                            // pointing the user at the Unlock button in the
                            // banner instead of opening an empty picker.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.assistant_attach_photo_vault)) },
                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                                enabled = !vaultLocked,
                                onClick = {
                                    showAttachMenu = false
                                    vaultPickerFilter = com.privateai.camera.security.VaultMediaType.PHOTO
                                    showVaultPicker = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.assistant_attach_pdf_vault)) },
                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                                enabled = !vaultLocked,
                                onClick = {
                                    showAttachMenu = false
                                    vaultPickerFilter = com.privateai.camera.security.VaultMediaType.PDF
                                    showVaultPicker = true
                                }
                            )
                        }
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        enabled = !thinking,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 6,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 14.dp, horizontal = 4.dp),
                        decorationBox = { inner ->
                            if (inputText.isEmpty()) {
                                Text(
                                    stringResource(R.string.assistant_input_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            inner()
                        }
                    )
                    // Mic button — tap to start dictation. Toggles to a red
                    // "stop" icon while listening so the user can stop the
                    // capture without waiting for end-of-speech. Permission
                    // requested on first tap; recognizer availability checked
                    // up front (surfaces a toast on de-Googled / GrapheneOS
                    // devices without an installed speech service).
                    val micEnabled = !thinking
                    IconButton(
                        onClick = {
                            if (listening) {
                                stopListening()
                            } else if (!hasAudioPermission) {
                                pendingMicAfterPermission = true
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                startListening()
                            }
                        },
                        enabled = micEnabled
                    ) {
                        Icon(
                            if (listening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = stringResource(
                                if (listening) R.string.assistant_voice_stop else R.string.assistant_voice_start
                            ),
                            tint = when {
                                !micEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                listening -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    // Trailing button: while a turn is in flight this is a
                    // Stop button (square icon, error color) — tap to cancel
                    // the Gemma stream mid-generation. When idle, reverts to
                    // the regular Send button (filled when canSend, neutral
                    // when there's nothing to send).
                    if (thinking) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .clickable { stopInflight() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = stringResource(R.string.assistant_stop_generation),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onError
                            )
                        }
                    } else if (cancelling) {
                        // After Stop, the old native call may still be
                        // draining. Show a spinner in place of the send
                        // button so the user knows we're waiting for the
                        // engine to release before accepting a new request.
                        // Send is gated on !cancelling — see canSend.
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(
                                    if (canSend) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable(enabled = canSend) { sendMessage() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.assistant_send),
                                modifier = Modifier.size(18.dp),
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
            // Char counter — surfaces only when the user has typed enough that
            // length matters (e.g., targeting an email of a specific size).
            if (inputText.length > 50) {
                Text(
                    stringResource(R.string.assistant_chars, inputText.length),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            // Quick-action tag row — under the input field. Each chip sends
            // a writing-task directive. Works when there's typed text OR an
            // attached image / PDF (so the user can attach a doc and tap
            // "Summarize" without typing anything). With no text, the
            // directive becomes the prompt verbatim.
            val canApplyChip = (inputText.isNotBlank() ||
                attachedImage != null ||
                AssistantSession.attachedDocId != null ||
                AssistantSession.attachedDocText != null) && !thinking
            fun applyQuickAction(directive: String) {
                if (!canApplyChip) return
                val typed = inputText.trim()
                inputText = if (typed.isNotEmpty()) "$directive\n\n$typed" else directive
                sendMessage()
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickActionChip(
                    label = stringResource(R.string.assistant_chip_grammar),
                    icon = Icons.Default.Spellcheck,
                    enabled = canApplyChip,
                    onClick = {
                        applyQuickAction(context.getString(R.string.assistant_directive_grammar))
                    }
                )
                QuickActionChip(
                    label = stringResource(R.string.assistant_chip_rewrite),
                    icon = Icons.Default.AutoFixHigh,
                    enabled = canApplyChip,
                    onClick = {
                        applyQuickAction(context.getString(R.string.assistant_directive_rewrite))
                    }
                )
                QuickActionChip(
                    label = stringResource(R.string.assistant_chip_summarize),
                    icon = Icons.AutoMirrored.Filled.ShortText,
                    enabled = canApplyChip,
                    onClick = {
                        applyQuickAction(context.getString(R.string.assistant_directive_summarize))
                    }
                )
                QuickActionChip(
                    label = stringResource(R.string.assistant_chip_formal),
                    icon = Icons.Default.WorkOutline,
                    enabled = canApplyChip,
                    onClick = {
                        applyQuickAction(context.getString(R.string.assistant_directive_formal))
                    }
                )
                QuickActionChip(
                    label = stringResource(R.string.assistant_chip_simple),
                    icon = Icons.Default.Lightbulb,
                    enabled = canApplyChip,
                    onClick = {
                        applyQuickAction(context.getString(R.string.assistant_directive_simple))
                    }
                )
                QuickActionChip(
                    label = stringResource(R.string.assistant_chip_translate),
                    icon = Icons.Default.Translate,
                    enabled = canApplyChip,
                    onClick = {
                        // Defer the directive until the user picks a target language.
                        showLanguagePicker = true
                    }
                )
                QuickActionChip(
                    label = stringResource(R.string.assistant_chip_expand),
                    icon = Icons.Default.UnfoldMore,
                    enabled = canApplyChip,
                    onClick = {
                        applyQuickAction(context.getString(R.string.assistant_directive_expand))
                    }
                )
                QuickActionChip(
                    label = stringResource(R.string.assistant_chip_bullets),
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    enabled = canApplyChip,
                    onClick = {
                        applyQuickAction(context.getString(R.string.assistant_directive_bullets))
                    }
                )
            }

            // Translate target-language picker — opened from the Translate chip.
            // Five built-in languages match Privora's UI locales; users can extend
            // by typing a language name themselves into the chat.
            if (showLanguagePicker) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showLanguagePicker = false },
                    title = { Text(stringResource(R.string.assistant_translate_pick_title)) },
                    text = {
                        Column {
                            val languages = listOf(
                                R.string.assistant_translate_lang_en to "English",
                                R.string.assistant_translate_lang_fr to "French",
                                R.string.assistant_translate_lang_es to "Spanish",
                                R.string.assistant_translate_lang_zh to "Chinese",
                                R.string.assistant_translate_lang_ar to "Arabic"
                            )
                            languages.forEach { (labelRes, modelLangName) ->
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        showLanguagePicker = false
                                        applyQuickAction(
                                            context.getString(R.string.assistant_directive_translate, modelLangName)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(labelRes), modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showLanguagePicker = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }

            // Vault-content picker — bottom sheet listing recent vault images
            // AND PDFs. Image → decrypt to temp JPEG → set as attached image.
            // PDF → set attachedDocId so the existing OCR-injection path
            // grounds the next assistant turn in the doc text.
            if (showVaultPicker) {
                VaultPhotoPickerSheet(
                    mediaTypeFilter = vaultPickerFilter,
                    onPicked = { tempUri ->
                        // Picking an image from vault clears any attached
                        // PDF (mutual exclusion — see device-gallery picker).
                        AssistantSession.attachedDocId = null
                        AssistantSession.attachedDocText = null
                        AssistantSession.attachedDocName = null
                        AssistantSession.attachedImageUri = tempUri
                        showVaultPicker = false
                    },
                    onPdfPicked = { pdfId ->
                        // Switching docs wipes prior chat so a previous
                        // summary doesn't bias the next answer (matches the
                        // existing PDF-viewer → Assistant deep-link behavior).
                        val previous = AssistantSession.attachedDocId
                        if (previous != null && previous != pdfId) {
                            AssistantSession.messages.clear()
                        }
                        // Picking a PDF clears any attached image / device doc.
                        AssistantSession.attachedImageUri = null
                        AssistantSession.attachedDocText = null
                        AssistantSession.attachedDocName = null
                        AssistantSession.attachedDocId = pdfId
                        showVaultPicker = false
                    },
                    onDismiss = { showVaultPicker = false }
                )
            }

            // Unlock PIN dialog — reused from HomeScreen. Fires when the user
            // taps "Unlock" in the locked-vault banner (or the top-bar lock
            // icon when re-locking, though that path doesn't need the dialog).
            // On success VaultLockManager.markUnlocked() flips the state and
            // we bump vaultLockTick so the banner + gates recompose.
            if (showPinDialog) {
                val cryptoForDialog = remember {
                    com.privateai.camera.security.CryptoManager(context).also { it.initialize() }
                }
                com.privateai.camera.ui.home.VaultPinDialog(
                    crypto = cryptoForDialog,
                    onUnlocked = { _ ->
                        showPinDialog = false
                        vaultLockTick++
                    },
                    onDismiss = { showPinDialog = false }
                )
            }

            // Full-screen swipeable image viewer. Opens when the user taps
            // any thumbnail in the chat (attached image, sent user image, or
            // a search-result thumb). When the strip has more than one
            // source, the user can swipe left/right between siblings; the
            // viewer always pulls *full resolution* from the vault for
            // VaultPhoto sources (raw-bitmap sources — user-sent images —
            // are already full-res in memory). Plain tap / Close button /
            // Back all dismiss.
            zoomTarget?.let { target ->
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { zoomTarget = null },
                    properties = androidx.compose.ui.window.DialogProperties(
                        usePlatformDefaultWidth = false
                    )
                ) {
                    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                        initialPage = target.index.coerceIn(0, target.sources.lastIndex),
                        pageCount = { target.sources.size }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.95f)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.pager.HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            ZoomedImagePage(
                                source = target.sources[page],
                                onTap = { zoomTarget = null }
                            )
                        }
                        // Page indicator (only when multiple sources).
                        if (target.sources.size > 1) {
                            Text(
                                "${pagerState.currentPage + 1} / ${target.sources.size}",
                                color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 24.dp)
                                    .background(
                                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        IconButton(
                            onClick = { zoomTarget = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = androidx.compose.ui.graphics.Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Result of one assistant turn — final cleaned text + any data refs + an optional proposed action + optional photo thumbs. */
private data class TurnResult(
    val text: String,
    val refs: List<DataRef>,
    val action: ProposedAction? = null,
    val photoThumbs: List<PhotoThumb> = emptyList()
)

/**
 * One page of the full-screen zoom pager. For [ZoomSource.RawBitmap] we
 * render the bitmap as-is (user-sent images are already full-res). For
 * [ZoomSource.VaultPhoto] we show the thumbnail immediately, then async-load
 * the full original from the encrypted vault so the user actually gets the
 * full resolution instead of a stretched 96 dp thumbnail.
 */
@Composable
private fun ZoomedImagePage(
    source: ZoomSource,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    var fullBitmap by remember(source) {
        mutableStateOf<android.graphics.Bitmap?>(
            if (source is ZoomSource.RawBitmap) source.bmp else null
        )
    }
    var loading by remember(source) { mutableStateOf(source is ZoomSource.VaultPhoto) }

    LaunchedEffect(source) {
        if (source is ZoomSource.VaultPhoto) {
            loading = true
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    // The vault is already unlocked at this point (the user
                    // got back search results, which require a live session)
                    // so a fresh CryptoManager.initialize() returns the same
                    // KEK and decryption succeeds. Decoding here, not in the
                    // outer composable, keeps the file scoped to the page.
                    val crypto = com.privateai.camera.security.CryptoManager(context).also { it.initialize() }
                    val vault = com.privateai.camera.security.VaultRepository(context, crypto)
                    val photo = vault.listAllPhotos().firstOrNull { it.id == source.id }
                    if (photo != null) vault.loadFullPhoto(photo) else null
                }.getOrNull()
            }
            if (loaded != null) fullBitmap = loaded
            loading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        val displayBitmap = fullBitmap
            ?: (source as? ZoomSource.VaultPhoto)?.thumb
        if (displayBitmap != null) {
            Image(
                bitmap = displayBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.assistant_image_zoom_view),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (loading && fullBitmap == null) {
            androidx.compose.material3.CircularProgressIndicator(
                color = androidx.compose.ui.graphics.Color.White
            )
        }
    }
}

/**
 * Build the chat-bubble summary for a search_photos tool result host-side.
 * Bypassing the LLM here avoids Gemma's number-hallucination tendency on
 * digits inside structured prompts (logged a "total:87" → "found 7 photos"
 * mis-copy). The thumb strip already shows the visual result; the text
 * just needs to confirm the count + what was matched.
 */
/**
 * One-shot vision query: decrypt/copy the picked image URI into a temp JPEG
 * and hand it to [GemmaRunner.describeImage] with the user's typed prompt.
 *
 * Bypasses [runAssistantTurn] because the regular flow does tool-calling
 * + knowledge-snapshot context which adds zero value when the user is
 * asking about a specific photo they just attached.
 */
private suspend fun runImageQueryTurn(
    context: android.content.Context,
    imageUri: android.net.Uri,
    userPrompt: String
): String? {
    if (!GemmaRunner.isAvailable(context)) {
        Log.w(TAG, "runImageQueryTurn: GemmaRunner.isAvailable=false")
        return null
    }
    val tempFile = File(context.cacheDir, "assistant_img_${System.currentTimeMillis()}.jpg")
    try {
        // Copy the picker URI to a real file path. PickVisualMedia hands us
        // a content:// URI which GemmaRunner.describeImage can't open
        // directly (it takes a filesystem path).
        context.contentResolver.openInputStream(imageUri)?.use { ins ->
            tempFile.outputStream().use { out -> ins.copyTo(out) }
        }
        if (!tempFile.exists() || tempFile.length() == 0L) {
            Log.w(TAG, "runImageQueryTurn: temp file empty / missing for $imageUri")
            return null
        }
        Log.d(TAG, "runImageQueryTurn: temp file ready (${tempFile.length()} bytes), calling describeImage")
        // Honor user's UI language for the reply.
        val prompt = com.privateai.camera.bridge.GemmaPrompts.askAboutImage(userPrompt)
        val result = GemmaRunner.describeImage(context, tempFile.absolutePath, prompt)?.trim()
        if (result.isNullOrBlank()) {
            Log.w(TAG, "runImageQueryTurn: describeImage returned null/blank")
        }
        return result
    } catch (e: kotlinx.coroutines.CancellationException) {
        Log.d(TAG, "runImageQueryTurn cancelled")
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "runImageQueryTurn failed: ${e.javaClass.simpleName}: ${e.message}", e)
        return null
    } finally {
        try { tempFile.delete() } catch (_: Exception) {}
    }
}

private fun buildSearchPhotosSummary(
    context: android.content.Context,
    leanJson: String,
    originalQuery: String,
    thumbsShown: Int
): String {
    return try {
        val obj = org.json.JSONObject(leanJson)
        val total = obj.optInt("total", 0)
        val person = obj.optString("detectedPerson", "").ifBlank { null }
        val residual = obj.optString("residualQuery", "").trim()
        val date = obj.optString("detectedDate", "").ifBlank { null }

        if (total == 0) {
            context.getString(R.string.assistant_search_photos_none, originalQuery)
        } else {
            // Compose a single sentence. Single-form strings (no plurals)
            // keep the locale work simple and avoid mistakes — the digit
            // carries the count info; grammar slack on "1 photo" vs "1
            // photos" is acceptable for an in-chat status line.
            val parts = mutableListOf<String>()
            if (person != null) parts.add(context.getString(R.string.assistant_search_photos_of, person))
            if (residual.isNotBlank()) parts.add(context.getString(R.string.assistant_search_photos_with, residual))
            if (date != null) parts.add(context.getString(R.string.assistant_search_photos_from, date))

            val suffix = if (parts.isEmpty()) "" else " " + parts.joinToString(" ")
            val head = context.getString(R.string.assistant_search_photos_found, total)
            val tail = if (thumbsShown in 1 until total) " " + context.getString(R.string.assistant_search_photos_showing, thumbsShown) else ""
            "$head$suffix.$tail"
        }
    } catch (_: Exception) {
        // Defensive fallback — should never trigger given the lean JSON is built host-side
        context.getString(R.string.assistant_search_photos_generic)
    }
}

/**
 * Build the chat-bubble summary for a summarize_expenses tool result.
 * Same anti-hallucination rationale as the search_photos helper: Gemma
 * mis-copies digits from structured JSON ("total":"88.00" → wrote
 * "$88,000.00") so deterministic host-side formatting is more reliable.
 *
 * Expected JSON shape (from AssistantTools.summarizeExpenses):
 *   { "period":"month", "total":"123.45", "count":12,
 *     "byCategory":[{"category":"Food","total":"45.67"}, ...],
 *     "topItems":[{"description":"Lunch","amount":"12.50","date":"2026-05-01","category":"Food"}, ...] }
 */
private fun buildExpensesSummary(context: android.content.Context, toolJson: String): String {
    return try {
        val obj = org.json.JSONObject(toolJson)
        val period = obj.optString("period", "month")
        val total = obj.optString("total", "0.00")
        val count = obj.optInt("count", 0)
        val byCat = obj.optJSONArray("byCategory") ?: org.json.JSONArray()
        val topItems = obj.optJSONArray("topItems") ?: org.json.JSONArray()

        if (count == 0) {
            return context.getString(R.string.assistant_expenses_none, period)
        }

        val sb = StringBuilder()
        sb.append(context.getString(R.string.assistant_expenses_total, total, count, period))
        if (byCat.length() > 0) {
            sb.append("\n\n")
            sb.append(context.getString(R.string.assistant_expenses_by_category_header))
            sb.append("\n")
            for (i in 0 until byCat.length()) {
                val c = byCat.getJSONObject(i)
                sb.append("- **")
                sb.append(c.optString("category", "Other"))
                sb.append("**: ")
                sb.append(c.optString("total", "0.00"))
                sb.append("\n")
            }
        }
        if (topItems.length() > 0) {
            sb.append("\n")
            sb.append(context.getString(R.string.assistant_expenses_top_header))
            sb.append("\n")
            for (i in 0 until topItems.length()) {
                val it = topItems.getJSONObject(i)
                sb.append("- ")
                sb.append(it.optString("description", "?"))
                sb.append(" — ")
                sb.append(it.optString("amount", "0.00"))
                val date = it.optString("date", "")
                if (date.isNotBlank()) {
                    sb.append(" (")
                    sb.append(date)
                    sb.append(")")
                }
                sb.append("\n")
            }
        }
        sb.toString().trimEnd()
    } catch (_: Exception) {
        context.getString(R.string.assistant_expenses_generic)
    }
}

/**
 * Compact AssistChip with a leading icon, used in the quick-action row above
 * the input field. Disabled state visually dims the chip so users know they
 * need to type something first.
 */
@Composable
private fun QuickActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize)
            )
        }
    )
}

/**
 * Execute one assistant turn: build snapshot → Gemma → optional tool → answer
 * (or action proposal).
 *
 * Streams partial text via [onChunk] as tokens arrive. JSON-shaped output (tool
 * calls or wrapped answers / actions) is parsed atomically at the end so the
 * user never sees raw JSON in the chat — chunks only flow when the model is
 * producing free-text markdown inside the `text` field.
 *
 * Must be called on a background dispatcher.
 */
private suspend fun runAssistantTurn(
    context: android.content.Context,
    allMessages: List<ChatMessage>,
    userText: String,
    onChunk: (String) -> Unit
): TurnResult {
    // 1. Build knowledge snapshot. We always build it because the post-turn
    // `buildContextRefs` consumes it to surface clickable references. But
    // when an attached document is in play, the JSON we pass to Gemma is an
    // empty stub — the user's notes / expenses / health are irrelevant noise
    // for doc Q&A, and skipping them frees ~1-2 KB of context budget for the
    // doc itself.
    // Locked-vault mode: skip the snapshot build entirely (it'd just fail
    // to decrypt) and pass `{}` to the model. We still let the user attach
    // device images / device PDFs and chat about pasted text — those code
    // paths don't touch encrypted data.
    val vaultLocked = !com.privateai.camera.security.VaultLockManager.isUnlockedWithinGrace(context)
    val snapshot = if (vaultLocked) KnowledgeSnapshot.empty() else KnowledgeSnapshot.build(context)
    val docAttached = !AssistantSession.attachedDocId.isNullOrBlank() ||
        !AssistantSession.attachedDocText.isNullOrBlank()
    val snapshotJson = if (!docAttached && !vaultLocked) snapshot.toJson() else "{}"

    // 1b. If a document is attached to this session, prepend its OCR text to
    // the prompt as DOCUMENT CONTEXT. Two sources, in priority order:
    //   1. attachedDocText — pre-loaded OCR for a device-picked PDF (SAF).
    //   2. attachedDocId   — vault PDF; load OCR from its `.ocr.enc` sidecar.
    // Either way the model sees the text directly — Gemma 4 E2B can't echo
    // a 13-digit doc id through a structured tool call, but it CAN read
    // text already in its context (Phase 0 of "Ask My Documents").
    val attachedDocId = AssistantSession.attachedDocId
    val attachedDocText = AssistantSession.attachedDocText
    val attachedDocName = AssistantSession.attachedDocName
    val attachedOcr: String? = when {
        !attachedDocText.isNullOrBlank() -> attachedDocText
        !attachedDocId.isNullOrBlank() -> try {
            val crypto = com.privateai.camera.security.CryptoManager(context)
            if (crypto.initialize()) {
                val vaultRepo = com.privateai.camera.security.VaultRepository(context, crypto)
                // Find the doc across all categories AND all custom folders.
                // Smart Scanner moves the saved PDF out of the SCAN category
                // and into the user-picked folder; the previous lookup only
                // walked category dirs, so the Assistant couldn't find the
                // OCR sidecar after a folder move.
                val fromCategories = com.privateai.camera.security.VaultCategory.entries.asSequence()
                    .flatMap { vaultRepo.listPhotos(it).asSequence() }
                val folderManager = com.privateai.camera.security.FolderManager(context, crypto)
                val fromFolders = folderManager.listAllFolders().asSequence().flatMap { f ->
                    vaultRepo.listFolderItems(folderManager.getFolderDir(f.id)).asSequence()
                }
                val doc = (fromCategories + fromFolders).firstOrNull { it.id == attachedDocId }
                doc?.let { vaultRepo.loadOcr(it) }
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load attached doc OCR: ${e.message}")
            null
        }
        else -> null
    }

    // 2. Assemble recent chat history — 8 exchanges (16 messages) for real
    // follow-up context. When the vault is currently UNLOCKED but the chat
    // history contains stale "vault is locked" assistant replies from
    // before the unlock, drop those replies from history. Otherwise Gemma
    // E2B mirrors them on the very first post-unlock turn ("the vault is
    // still locked") even though the snapshot is now populated. The user's
    // original questions stay so the conversation flow is preserved.
    val staleLockPatterns = listOf(
        "vault is locked", "vault is off", "tap unlock",
        "coffre est verrouillé", "خزنتك مقفلة", "kasanız kilitli",
        "bóveda está bloqueada", "保险库已锁定"
    )
    val history = allMessages
        .dropLast(1)
        .takeLast(16)
        .filter { msg ->
            if (vaultLocked || msg !is ChatMessage.Assistant) true
            else staleLockPatterns.none { p -> msg.text.contains(p, ignoreCase = true) }
        }
        .map { msg ->
            when (msg) {
                is ChatMessage.User -> "user" to msg.text
                is ChatMessage.Assistant -> "assistant" to msg.text
            }
        }

    // 3. Dynamic temperature — creative tasks get warmer output, data queries
    // stay precise. When an attached doc is in play, force a cooler temp:
    // doc Q&A is factual / extractive, and warmer sampling pushes Gemma 4 E2B
    // into degenerate repetition loops (same summary block emitted forever).
    val temp = if (docAttached) 0.2 else classifyTemperature(userText)

    // 4. First turn — stream and decide free-text vs JSON-wrapped on the fly
    val basePromptRaw = AssistantPrompts.formatTurn(snapshotJson, history, userText)
    // Detect the lock→unlock transition: vault is currently unlocked, but
    // the recent chat history contains stale "vault is locked" replies
    // from before the user unlocked. Without this hint the model parrots
    // the past replies for several turns ("vault is off") even though the
    // snapshot is now populated — chat-history bias on Gemma E2B.
    val lockedHints = listOf("vault is locked", "vault is off", "tap unlock", "coffre est verrouillé", "خزنتك مقفلة", "kasanız kilitli", "bóveda está bloqueada", "保险库已锁定")
    val recentMentionsLocked = !vaultLocked && history.takeLast(6).any { (role, text) ->
        role == "assistant" && lockedHints.any { hint -> text.contains(hint, ignoreCase = true) }
    }
    val basePrompt = when {
        vaultLocked -> buildString {
            // When the vault is locked, prepend a small system note + forbid
            // tool calls. Snapshot is already `{}`; without this note the
            // model would hallucinate empty data ("you have no expenses")
            // instead of saying "your vault is locked — unlock to see it".
            append("VAULT LOCKED: the user's personal data (notes, expenses, photos, contacts, habits, health) is encrypted and NOT accessible this turn. The snapshot above is `{}` for that reason — it does NOT mean the user has zero data.\n")
            append("Rules while locked:\n")
            append("- For text tasks (summarize / rewrite / translate / draft / fix grammar) and general chat, answer normally using the user's typed text or attached document/image.\n")
            append("- If the user asks about THEIR data (\"my expenses this week\", \"find note about X\", \"photos of Anas\"), reply briefly: tell them the vault is locked and they need to unlock to access that. Do NOT pretend the data is missing — it is locked, not empty.\n")
            append("- DO NOT call any tools (search_notes / fetch_note / summarize_expenses / search_photos / summarize_document / ask_document). They will refuse to run.\n")
            append("- DO NOT propose actions (`{\"type\":\"action\",...}`) — reminders / expenses / notes / health / contacts / medications / habits all write to the locked encrypted store. If the user asks to save something, reply briefly that they need to unlock the vault first.\n\n")
            append(basePromptRaw)
        }
        recentMentionsLocked -> buildString {
            append("VAULT NOW UNLOCKED: the user previously asked while the vault was locked. The vault has since been unlocked and the snapshot above is current and complete. The earlier \"vault is locked\" replies in this conversation are OUTDATED — do NOT repeat them. Tools and actions are available again. Answer this turn from the current snapshot / tools as you normally would.\n\n")
            append(basePromptRaw)
        }
        else -> basePromptRaw
    }
    val prompt = if (attachedOcr != null) {
        // 5 KB budget. The snapshot is now empty `{}` when a doc is attached
        // (see step 1), so the freed context goes back to the doc. A 7 KB
        // doc was previously over-truncated to 3 KB; now ~70% of it reaches
        // the model. Real fix for long docs is still Phase 1 (RAG).
        val docBudget = 5120
        val clipped = if (attachedOcr.length > docBudget) attachedOcr.take(docBudget) else attachedOcr
        val truncatedNote = if (attachedOcr.length > docBudget)
            "\n[Note: only the first part of this document is shown (${clipped.length}/${attachedOcr.length} chars). If the answer isn't in this excerpt, say so rather than guessing.]"
        else ""
        // Detect the dominant language of the user's question — used to nudge
        // the model away from defaulting to English when the UI is Arabic /
        // the document is French / etc.
        val langHint = describeLanguage(userText)
        // Header identifier — prefer the device-doc filename when present,
        // fall back to the vault id. Lets the model refer to "the document"
        // without us leaking ugly basenames into the user-visible reply.
        val docIdentifier = attachedDocName ?: attachedDocId ?: "document"
        buildString {
            append("ATTACHED DOCUMENT (id: $docIdentifier):\n")
            append(clipped)
            append(truncatedNote)
            append("\n\n---\n\n")
            // Grounding rules — small-model assistants on noisy OCR text
            // reliably hallucinate digits and reformat values. Forcing
            // verbatim quoting + an "I can't find it" escape valve is the
            // cheapest mitigation before we add real RAG citations.
            append("Use the attached document above as your primary source when the user asks about it.\n")
            append("Critical rules:\n")
            append("- Copy any number, date, name, or value EXACTLY as it appears in the document text. Do NOT reformat, normalize, or paraphrase numbers — copy the digits character-by-character.\n")
            append("- When the user asks for a value, quote the exact phrase from the document where you found it.\n")
            append("- If the answer isn't clearly in the text, say \"I can't find that in the document\" rather than guessing.\n")
            append("- The document text above came from OCR and may contain layout artefacts (extra spaces, mis-ordered columns). Don't try to fix or re-render it — work with what's there.\n")
            append("- The OCR can only read Latin scripts. If the source document also contained Arabic, Hebrew, Chinese, Japanese, Korean, or other non-Latin text, those parts are MISSING from the text above. If a section looks short or incomplete, say so — do NOT invent or extrapolate content that isn't visible.\n")
            append("- Reply in the same language as the user's question")
            if (langHint != null) append(" ($langHint)")
            append(". Do NOT default to English unless the user wrote in English. If the user's message is too short to tell, match the document's language instead.\n")
            append("- Keep answers compact. Don't list every section of the document if the user asked about one specific thing. Long replies get cut off mid-sentence — be concise.\n")
            // Force doc-grounded replies on follow-up turns. Without this the
            // model would sometimes emit a tool call (search_notes / fetch_note
            // / ask_document) on a short follow-up like "tell me more" or
            // "translate that to French", which routes around the attached doc
            // entirely and returns "I can't find that" against an unrelated
            // tool result. The attached document IS the source of truth here.
            append("- DO NOT call any tools (search_notes / fetch_note / summarize_expenses / search_photos / summarize_document / ask_document). The document text above is already in your context — answer directly from it. Do NOT emit `{\"type\":\"tool\",...}` JSON.\n")
            append("- This grounding rule applies to EVERY turn while the document is attached, including short follow-ups like \"translate that\", \"summarize\", or \"tell me more\". Treat those as referring to the document above.\n\n")
            append(basePrompt)
        }
    } else basePrompt
    val rawFirst = streamAndCollect(context, prompt, temp, onChunk)
    Log.d(TAG, "First reply (raw): ${rawFirst.take(200)}")

    var firstReply = ParsedReply.parse(rawFirst)
    // Defensive: when a document is attached, the model is forbidden from
    // calling tools (see grounding rules in the prompt). If it slips one
    // out anyway on a follow-up turn, treat the raw text as a normal
    // answer rather than routing to a tool that has nothing to do with
    // the attached doc. This was the cause of follow-up turns failing
    // with "I can't find that in the document" — the model had emitted
    // a stray search_notes call instead of reading the doc context.
    if (docAttached && firstReply is ParsedReply.ToolCall) {
        Log.w(TAG, "Suppressed stray tool call '${firstReply.name}' — doc is attached")
        val fallback = rawFirst.replace(Regex("\\{\\s*\"type\"\\s*:\\s*\"tool\".*?\\}", RegexOption.DOT_MATCHES_ALL), "").trim()
        firstReply = ParsedReply.Answer(
            if (fallback.isNotEmpty()) fallback
            else context.getString(R.string.assistant_doc_followup_retry)
        )
    }
    // Same defensive intercept while the vault is locked. Every tool needs
    // crypto access; running them would fail at the repo layer anyway, but
    // catching the stray call here gives the user a useful "unlock first"
    // message instead of an opaque error.
    if (vaultLocked && firstReply is ParsedReply.ToolCall) {
        Log.w(TAG, "Suppressed tool call '${firstReply.name}' — vault locked")
        firstReply = ParsedReply.Answer(context.getString(R.string.assistant_vault_locked_tool))
    }
    // Also suppress action proposals while locked — "add expense", "save
    // note", "schedule reminder" all write to encrypted repos and would
    // fail or worse leak data through a half-completed write. Strip the
    // action and reply with the locked-vault hint so the user can choose
    // to unlock and re-ask.
    if (vaultLocked && firstReply is ParsedReply.ActionProposal) {
        Log.w(TAG, "Suppressed action proposal (${firstReply.action::class.simpleName}) — vault locked")
        firstReply = ParsedReply.Answer(context.getString(R.string.assistant_vault_locked_action))
    }
    val contextRefs = buildContextRefs(snapshot, userText)

    return when (firstReply) {
        is ParsedReply.Answer -> TurnResult(firstReply.text, contextRefs)

        is ParsedReply.ActionProposal -> TurnResult(firstReply.text, contextRefs, firstReply.action)

        is ParsedReply.ToolCall -> {
            // 5. Execute the tool
            val crypto = CryptoManager(context).also { it.initialize() }
            val noteRepo = NoteRepository(File(context.filesDir, "vault/notes"), crypto)
            val insightsRepo = com.privateai.camera.security.InsightsRepository(
                File(context.filesDir, "vault/insights"), crypto
            )

            var toolRefs = emptyList<DataRef>()
            val toolResult = when (firstReply.name) {
                "search_notes" -> {
                    val resultJson = AssistantTools.searchNotes(noteRepo, firstReply.query)
                    try {
                        val arr = org.json.JSONArray(resultJson)
                        toolRefs = (0 until arr.length()).mapNotNull { i ->
                            val obj = arr.optJSONObject(i)
                            val title = obj?.optString("title", "") ?: ""
                            val note = noteRepo.listNotes().find { it.title == title }
                            if (note != null && title.isNotBlank()) DataRef(RefKind.NOTE, note.id, title) else null
                        }
                    } catch (_: Exception) {}
                    resultJson
                }
                "fetch_note" -> {
                    val resultJson = AssistantTools.fetchNote(noteRepo, firstReply.query)
                    try {
                        val obj = org.json.JSONObject(resultJson)
                        val title = obj.optString("title", "")
                        if (title.isNotBlank()) toolRefs = listOf(DataRef(RefKind.NOTE, firstReply.query, title))
                    } catch (_: Exception) {}
                    resultJson
                }
                "summarize_expenses" -> {
                    AssistantTools.summarizeExpenses(insightsRepo, firstReply.query)
                }
                "summarize_document" -> {
                    val vaultRepo = com.privateai.camera.security.VaultRepository(context, crypto)
                    AssistantTools.summarizeDocument(vaultRepo, firstReply.query)
                }
                "ask_document" -> {
                    val vaultRepo = com.privateai.camera.security.VaultRepository(context, crypto)
                    AssistantTools.askDocument(vaultRepo, firstReply.query)
                }
                "search_photos" -> {
                    val vaultRepo = com.privateai.camera.security.VaultRepository(context, crypto)
                    val db = com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)
                    val pi = com.privateai.camera.security.PhotoIndex(db)
                    val contactRepo = com.privateai.camera.security.ContactRepository(
                        java.io.File(context.filesDir, "vault/contacts"),
                        crypto,
                        db
                    )
                    val folderMgr = com.privateai.camera.security.FolderManager(context, crypto)
                    AssistantTools.searchPhotos(pi, contactRepo, vaultRepo, folderMgr, firstReply.query)
                }
                else -> "[]"
            }
            Log.d(TAG, "Tool '${firstReply.name}' result: ${toolResult.take(200)}, refs=${toolRefs.size}")

            // search_photos: decode the base64 thumbnails the tool returned so
            // the host can paint them in the next assistant bubble. Done here
            // (not in the tool fn) because PhotoThumb carries a live Bitmap,
            // not a serializable shape.
            //
            // We also build a "lean" tool result with the thumbs stripped out
            // before passing it to formatToolFollowup — base64 JPEGs at
            // ~10-30 KB each blow past Gemma 4 E2B's context budget when 3+
            // are included, which produced a silent blank reply ("I couldn't
            // generate a response..."). The model only needs the count and
            // the detected-person summary to write its one-line answer.
            var leanToolResult = toolResult
            val turnPhotoThumbs: List<PhotoThumb> = if (firstReply.name == "search_photos") {
                try {
                    val obj = org.json.JSONObject(toolResult)
                    val arr = obj.optJSONArray("photos") ?: org.json.JSONArray()
                    val out = mutableListOf<PhotoThumb>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val id = item.optString("id", "")
                        val b64 = item.optString("thumb", "")
                        if (id.isBlank() || b64.isBlank()) continue
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                        out.add(PhotoThumb(id, bmp))
                    }
                    // Rebuild the JSON for the model with the thumb bytes
                    // stripped. Only `total` is reported back — exposing
                    // `photosShown` (the count of base64 thumbs rendered in
                    // the chat) made the model write awkward summaries like
                    // "Total photos found: 87. Thumbnails shown: 6." instead
                    // of one natural sentence.
                    leanToolResult = org.json.JSONObject().apply {
                        put("detectedPerson", obj.opt("detectedPerson") ?: org.json.JSONObject.NULL)
                        put("residualQuery", obj.optString("residualQuery", ""))
                        put("detectedDate", obj.opt("detectedDate") ?: org.json.JSONObject.NULL)
                        put("total", obj.optInt("total", 0))
                    }.toString()
                    out
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decode search_photos thumbs: ${e.message}")
                    emptyList()
                }
            } else emptyList()

            // 6. Second turn with tool results — also streams.
            // EXCEPT for search_photos: Gemma routinely mis-reads digits in
            // the tool result ("total:87" → wrote "**7 photos**"). The
            // thumb strip already shows the visual evidence; the text just
            // confirms the count. Build it deterministically host-side to
            // avoid the number-hallucination class of bug entirely.
            val answerText: String
            val secondAction: ProposedAction?
            if (firstReply.name == "search_photos") {
                answerText = buildSearchPhotosSummary(context, leanToolResult, firstReply.query, turnPhotoThumbs.size)
                secondAction = null
                // Stream the host-built summary to the partial-message UI so the
                // bubble doesn't blank out between the typing dot and the final
                // text. One emit is sufficient — the text is short.
                onChunk(answerText)
            } else if (firstReply.name == "summarize_expenses") {
                // Same anti-hallucination strategy as search_photos: Gemma
                // mis-copies currency digits from the JSON. Host-built
                // formatting is deterministic.
                answerText = buildExpensesSummary(context, toolResult)
                secondAction = null
                onChunk(answerText)
            } else {
                // Note: for the other tools the full toolResult string is
                // safe (no base64 payload). For search_photos we'd use the
                // leanToolResult but we've already bypassed this branch.
                val followUp = AssistantPrompts.formatToolFollowup(
                    snapshotJson, userText, firstReply.name, firstReply.query, toolResult
                )
                val rawSecond = streamAndCollect(context, followUp, temp, onChunk)
                Log.d(TAG, "Second reply (raw): ${rawSecond.take(200)}")
                val secondReply = ParsedReply.parse(rawSecond)
                when (secondReply) {
                    is ParsedReply.Answer -> { answerText = secondReply.text; secondAction = null }
                    is ParsedReply.ActionProposal -> { answerText = secondReply.text; secondAction = secondReply.action }
                    is ParsedReply.ToolCall -> { answerText = "I found some results but couldn't summarize them. Please try rephrasing."; secondAction = null }
                }
            }
            TurnResult(answerText, toolRefs.ifEmpty { contextRefs }, secondAction, turnPhotoThumbs)
        }
    }
}

/**
 * Drive [GemmaRunner.completeStreaming] with progressive parsing.
 *
 * The system prompt instructs the model to wrap every reply in
 * `{"type":"answer","text":"..."}` JSON. Naively forwarding chunks would flash
 * raw JSON in the chat; suppressing all JSON-shaped output would suppress
 * everything. So we incrementally extract the value of the `text` field as it
 * streams and emit *that* via [onChunk], decoding JSON escape sequences as we
 * go. Tool calls are detected up front and suppressed entirely (they're parsed
 * once complete and routed to a tool, not shown to the user).
 *
 * If the model unexpectedly emits raw markdown (no `{` wrapper), we forward
 * chunks directly.
 *
 * Returns the full raw text so the caller can run [ParsedReply.parse] to
 * detect tool calls vs cleaned answers and run final cleanup.
 */
private suspend fun streamAndCollect(
    context: android.content.Context,
    prompt: String,
    temperature: Double,
    onChunk: (String) -> Unit
): String {
    val raw = StringBuilder()

    // Decision state. Settled on the first non-whitespace char.
    var mode: StreamMode = StreamMode.UNDECIDED
    // For JSON_WRAPPED mode: where the text field's value begins (just after `"text":"`).
    var textValueStart = -1
    var lastEmittedLen = 0
    var textFieldClosed = false

    GemmaRunner.completeStreaming(
        context, prompt, AssistantPrompts.SYSTEM, temperature
    ).collect { chunk ->
        raw.append(chunk)

        // Repetition-loop guard. Gemma 4 E2B sometimes falls into a degenerate
        // state where it emits the same block over and over until cancelled.
        // If we see a 60-char suffix appear 3+ times in the last ~600 chars of
        // output, cut the stream — better a truncated answer than an infinite
        // loop draining battery and pinning the user on a "thinking…" state.
        if (raw.length >= 300) {
            val tail = raw.substring(raw.length - 60)
            val window = raw.substring(maxOf(0, raw.length - 600))
            if (tail.isNotBlank() && occurrences(window, tail) >= 3) {
                Log.w(TAG, "Repetition loop detected (60-char tail seen 3× in last 600 chars) — cutting stream")
                kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.cancel()
                return@collect
            }
        }

        if (mode == StreamMode.UNDECIDED) {
            val trimmed = raw.toString().trimStart()
            if (trimmed.isEmpty()) return@collect
            val first = trimmed[0]
            mode = if (first == '{' || (first == '`' && trimmed.startsWith("```"))) {
                StreamMode.JSON_WRAPPED
            } else {
                StreamMode.RAW
            }
        }

        when (mode) {
            StreamMode.RAW -> {
                onChunk(raw.toString())
            }
            StreamMode.JSON_WRAPPED -> {
                // Tool calls have no "text" field, so the marker below never
                // matches and nothing gets emitted — natural suppression.
                if (textValueStart < 0) {
                    // Tolerate `"text" : "` with arbitrary whitespace (the model
                    // sometimes emits pretty-printed JSON wrapped in ```json).
                    val match = TextFieldMarker.find(raw)
                    if (match == null) return@collect
                    textValueStart = match.range.last + 1
                }

                if (textFieldClosed) return@collect

                val sub = raw.substring(textValueStart)
                val (decoded, closed) = extractJsonStringPartial(sub)
                if (decoded.length > lastEmittedLen) {
                    onChunk(decoded)
                    lastEmittedLen = decoded.length
                }
                if (closed) textFieldClosed = true
            }
            else -> {}
        }
    }

    return raw.toString()
}

private enum class StreamMode { UNDECIDED, RAW, JSON_WRAPPED }

/** Cheap substring-occurrence count for the repetition-loop guard. */
private fun occurrences(haystack: String, needle: String): Int {
    if (needle.isEmpty()) return 0
    var count = 0
    var i = 0
    while (true) {
        val idx = haystack.indexOf(needle, i)
        if (idx < 0) break
        count++
        i = idx + needle.length
    }
    return count
}

/** Matches the start of a JSON `"text": "..."` field with arbitrary whitespace. */
private val TextFieldMarker = Regex("\"text\"\\s*:\\s*\"")

/**
 * Decode a JSON string-literal value progressively. [input] is the substring
 * starting *after* the opening quote. Returns (decoded text so far, whether
 * the closing quote has been reached). Stops cleanly at an incomplete escape
 * sequence so we don't emit half-decoded characters.
 */
private fun extractJsonStringPartial(input: String): Pair<String, Boolean> {
    val sb = StringBuilder()
    var i = 0
    val n = input.length
    while (i < n) {
        val c = input[i]
        when {
            c == '\\' -> {
                if (i + 1 >= n) return sb.toString() to false  // wait for next chunk
                val esc = input[i + 1]
                when (esc) {
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append(''); i += 2 }
                    'u' -> {
                        if (i + 5 >= n) return sb.toString() to false
                        val hex = input.substring(i + 2, i + 6)
                        try {
                            sb.append(hex.toInt(16).toChar())
                        } catch (_: Exception) {
                            sb.append('?')
                        }
                        i += 6
                    }
                    else -> { sb.append(esc); i += 2 }
                }
            }
            c == '"' -> return sb.toString() to true  // closing quote
            else -> { sb.append(c); i++ }
        }
    }
    return sb.toString() to false
}

/**
 * Best-effort language hint to nudge Gemma away from defaulting to English
 * (or the document's language) when the user is asking in something else.
 * Returns null when we genuinely can't tell — the model's general "match
 * the user's language" rule still applies in that case.
 *
 * Strategy:
 *  1. Non-Latin scripts (Arabic, CJK, Cyrillic, etc.) — return the script's
 *     language directly. High-confidence signal.
 *  2. Latin script — count uniquely-French / uniquely-Spanish stop-words
 *     (i.e. *not* "document" or "resume" which collide with English). Only
 *     commit if at least 2 hit, otherwise tie-breaks fall to the wrong side.
 *  3. Final fallback — the device's display locale. If Privora's UI is set
 *     to English, short queries like "Summarize this document." get tagged
 *     English; if UI is French, they get tagged French.
 */
private fun describeLanguage(userText: String): String? {
    // 1. Script detection — fast and reliable.
    val firstNonAscii = userText.firstOrNull { it.code > 127 }
    if (firstNonAscii != null) {
        val block = Character.UnicodeBlock.of(firstNonAscii)
        when (block) {
            Character.UnicodeBlock.ARABIC,
            Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A,
            Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B -> return "Arabic"
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS -> return "Chinese"
            Character.UnicodeBlock.CYRILLIC -> return "Russian"
            Character.UnicodeBlock.HIRAGANA, Character.UnicodeBlock.KATAKANA -> return "Japanese"
            Character.UnicodeBlock.HANGUL_SYLLABLES -> return "Korean"
            else -> {}
        }
    }
    // 2. Latin-script: only uniquely-French / uniquely-Spanish markers count.
    // Words that collide with English ("document", "resume") are excluded so
    // a short English question like "Summarize this document." doesn't get
    // mis-tagged as French.
    val lower = " ${userText.lowercase()} "
    val frenchUnique = listOf(" le ", " les ", " une ", " et ", " est ", " du ", " des ", " que ", " qu'", " ce ", " cette ", "résume", "résumé", "français", "à propos")
    val spanishUnique = listOf(" el ", " los ", " las ", " los ", " es ", " del ", " esta ", " este ", " con ", " por ", " para ", "español", "qué")
    val frenchHits = frenchUnique.count { lower.contains(it) }
    val spanishHits = spanishUnique.count { lower.contains(it) }
    if (frenchHits >= 2 && frenchHits > spanishHits) return "French"
    if (spanishHits >= 2 && spanishHits > frenchHits) return "Spanish"
    // 3. Fallback: device locale.
    return when (java.util.Locale.getDefault().language) {
        "en" -> "English"
        "fr" -> "French"
        "es" -> "Spanish"
        "ar" -> "Arabic"
        "zh" -> "Chinese"
        else -> null  // unknown — let Gemma's general rule handle it
    }
}

/** Classify user intent → temperature. Creative tasks need warmer, data queries need colder. */
private fun classifyTemperature(userText: String): Double {
    val lower = userText.lowercase()
    val creativeKeywords = listOf(
        "summarize", "rewrite", "draft", "translate", "fix grammar", "write",
        "compose", "explain", "expand", "shorten", "formal", "casual",
        "creative", "improve", "rephrase", "email", "letter", "message"
    )
    return if (creativeKeywords.any { lower.contains(it) }) 0.7 else 0.3
}

/**
 * Build tappable references from the snapshot for the **single most relevant** topic.
 * Only one category of refs per answer — avoids flooding the UI with reminders+habits+health
 * when the user's question is specifically about one thing.
 *
 * For broad queries like "what did I do this week?" the AI's text answer covers multiple
 * categories; the refs link to the single strongest match so the user can drill in.
 */
private fun buildContextRefs(
    snapshot: KnowledgeSnapshot,
    userText: String
): List<DataRef> {
    val lower = userText.lowercase()

    // Score each category — pick the one with the strongest keyword match
    val reminderScore = lower.countKeywords("reminder", "schedule", "upcoming", "coming up", "next week", "alarm")
    val habitScore = lower.countKeywords("habit", "streak", "routine", "completion")
    val healthScore = lower.countKeywords("health", "weight", "blood", "heart", "sleep", "bp", "pressure", "mood")
    val noteScore = lower.countKeywords("note", "notes")

    // Pick the winner — ties go to the first match; 0 = no refs (pure text task)
    data class Scored(val kind: String, val score: Int)
    val best = listOf(
        Scored("reminder", reminderScore),
        Scored("habit", habitScore),
        Scored("health", healthScore),
        Scored("note", noteScore)
    ).filter { it.score > 0 }.maxByOrNull { it.score } ?: return emptyList()

    return when (best.kind) {
        "reminder" -> snapshot.reminders.take(8).map {
            DataRef(RefKind.REMINDER, it.title, "${it.title} — ${it.dateTime}")
        }
        "habit" -> snapshot.habits.take(8).map {
            DataRef(RefKind.HABIT, it.name, "${it.name} (${it.last30}/30d)")
        }
        "health" -> snapshot.healthLast7.take(5).map {
            val label = buildString {
                append(it.date)
                it.weight?.let { w -> append(" • ${"%.1f".format(w)}kg") }
                it.heartRate?.let { h -> append(" • ${h}bpm") }
                it.bp?.let { b -> append(" • $b") }
            }
            DataRef(RefKind.HEALTH, it.date, label)
        }
        else -> emptyList() // notes handled via tool call, not snapshot refs
    }
}

/** Count how many of the given keywords appear in this string. */
private fun String.countKeywords(vararg keywords: String): Int =
    keywords.count { this.contains(it) }

