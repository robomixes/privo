// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.notes

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privateai.camera.R
import com.privateai.camera.grammar.GrammarError
import com.privateai.camera.grammar.LocalGrammarChecker
import com.privateai.camera.grammar.MarkdownTransformation
import com.privateai.camera.grammar.SystemSpellChecker
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.NoteRepository
import com.privateai.camera.security.SecureNote
import com.privateai.camera.security.VaultMediaType
import com.privateai.camera.security.VaultPhoto
import com.privateai.camera.security.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NoteEditorScreen(
    note: SecureNote?,
    allTags: List<String>,
    noteRepo: NoteRepository,
    onSave: (title: String, content: String, tags: List<String>, attachments: List<String>, audioAttachments: List<String>, personId: String?) -> Boolean,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isNew = note == null
    val draftKey = remember(note?.id) { note?.id ?: "__new__" }

    // Try to restore an unsaved draft. Drafts are only honored if newer than
    // the saved note's modifiedAt — otherwise a stale draft from a previous
    // session would shadow legitimate edits made elsewhere.
    val draft = remember(draftKey) {
        val d = noteRepo.loadDraft(draftKey) ?: return@remember null
        if (note != null && d.savedAt <= note.modifiedAt) {
            noteRepo.clearDraft(draftKey); null
        } else d
    }

    var title by remember { mutableStateOf(draft?.title ?: note?.title ?: "") }
    var contentValue by remember { mutableStateOf(TextFieldValue(draft?.content ?: note?.content ?: "")) }
    var tags by remember { mutableStateOf(draft?.tags ?: note?.tags ?: emptyList()) }
    var newTag by remember { mutableStateOf("") }
    var showTagInput by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var attachmentIds by remember { mutableStateOf(draft?.attachments ?: note?.attachments ?: emptyList()) }
    var attachmentThumbnails by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var showVaultPicker by remember { mutableStateOf(false) }
    var linkedPersonId by remember { mutableStateOf(draft?.personId ?: note?.personId) }

    // Audio attachments
    var audioIds by remember { mutableStateOf(draft?.audioAttachments ?: note?.audioAttachments ?: emptyList()) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableIntStateOf(0) }
    var playingAudioId by remember { mutableStateOf<String?>(null) }
    val audioDir = remember { java.io.File(context.filesDir, "vault/notes/audio").also { it.mkdirs() } }
    var mediaRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var currentRecordingFile by remember { mutableStateOf<java.io.File?>(null) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    // Play-all mode: when true, OnCompletionListener advances to the next clip
    // in audioIds order until the list is exhausted.
    var playAllMode by remember { mutableStateOf(false) }

    fun startPlayingAudio(audioId: String) {
        mediaPlayer?.release()
        mediaPlayer = null
        try {
            val encFile = java.io.File(audioDir, "$audioId.enc")
            if (!encFile.exists()) { playingAudioId = null; playAllMode = false; return }
            val crypto = com.privateai.camera.security.CryptoManager(context).also { it.initialize() }
            val decrypted = crypto.decryptFile(encFile)
            val tempFile = java.io.File(context.cacheDir, "play_${audioId}.m4a")
            tempFile.writeBytes(decrypted)
            val mp = android.media.MediaPlayer()
            mp.setDataSource(tempFile.absolutePath)
            mp.prepare()
            mp.start()
            mp.setOnCompletionListener {
                tempFile.delete()
                if (playAllMode) {
                    val idx = audioIds.indexOf(audioId)
                    val next = audioIds.getOrNull(idx + 1)
                    if (next != null) {
                        startPlayingAudio(next)
                    } else {
                        playingAudioId = null
                        playAllMode = false
                    }
                } else {
                    playingAudioId = null
                }
            }
            mediaPlayer = mp
            playingAudioId = audioId
        } catch (_: Exception) {
            playingAudioId = null
            playAllMode = false
        }
    }

    // Checklist mode
    var isChecklistMode by remember {
        mutableStateOf(note?.content?.trimStart()?.startsWith("- [") == true)
    }
    var showPersonPicker by remember { mutableStateOf(false) }
    var linkedPersonName by remember { mutableStateOf<String?>(null) }

    // Grammar checking state
    var grammarEnabled by remember { mutableStateOf(true) }
    var grammarErrors by remember { mutableStateOf<List<GrammarError>>(emptyList()) }
    val localChecker = remember { LocalGrammarChecker(context) }
    val systemChecker = remember { SystemSpellChecker(context) }

    // AI Assistant state — gated on the single AiStatus source of truth.
    // When AI isn't READY the sparkle menu items don't render at all; that
    // way the user doesn't see seven disabled actions in a row.
    val aiStatus by com.privateai.camera.bridge.rememberAiStatus()
    val aiAvailable = aiStatus.isReady
    var showAiSheet by remember { mutableStateOf(false) }
    var aiProcessing by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<String?>(null) }
    var aiAction by remember { mutableStateOf("") } // tracks which action produced the result
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val brandColor = MaterialTheme.colorScheme.primary
    val surfaceTone = MaterialTheme.colorScheme.surfaceContainerLow

    // RECORD_AUDIO permission gate. Older devices and fresh installs land here
    // without the runtime permission yet — prior code silently swallowed the
    // SecurityException from MediaRecorder.start(), so the user saw nothing.
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingRecordAfterPermission by remember { mutableStateOf(false) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (!granted) {
            pendingRecordAfterPermission = false
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.note_audio_permission_denied),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
        // If granted, the mic button click handler below picks it up via
        // pendingRecordAfterPermission on next click; we don't auto-start
        // because rememberLauncher's callback is async after the user grants.
    }

    fun startVoiceRecording() {
        val tempFile = java.io.File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        // VOICE_RECOGNITION asks the device's audio HAL to apply hardware
        // noise suppression + AGC tuned for speech. Falls back to identical
        // behavior on devices that don't process it. The toggle lets users
        // who want raw audio (musicians, ambient capture) opt out.
        val cleanEnabled = context.getSharedPreferences("privacy_settings", android.content.Context.MODE_PRIVATE)
            .getBoolean("clean_voice_notes", true)
        val source = if (cleanEnabled)
            android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION
        else
            android.media.MediaRecorder.AudioSource.MIC
        try {
            val rec = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                android.media.MediaRecorder(context)
            else @Suppress("DEPRECATION") android.media.MediaRecorder()
            rec.setAudioSource(source)
            rec.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            rec.setAudioSamplingRate(44100); rec.setAudioEncodingBitRate(128000)
            rec.setOutputFile(tempFile.absolutePath); rec.prepare(); rec.start()
            mediaRecorder = rec; currentRecordingFile = tempFile
            isRecording = true; recordingDuration = 0
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.note_audio_record_failed),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    // Auto-start recording once permission has just been granted (the user
    // tapped Mic, was prompted, accepted — this picks the recording back up
    // without making them tap Mic a second time).
    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission && pendingRecordAfterPermission) {
            pendingRecordAfterPermission = false
            startVoiceRecording()
        }
    }

    // Persist a draft on backgrounding so auto-lock doesn't silently drop the
    // user's unsaved edits. Skip when the editor is empty (nothing to save).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, draftKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val hasContent = title.isNotBlank() || contentValue.text.isNotBlank() ||
                    tags.isNotEmpty() || attachmentIds.isNotEmpty() || audioIds.isNotEmpty()
                val sameAsSaved = note != null &&
                    title == note.title && contentValue.text == note.content &&
                    tags == note.tags && attachmentIds == note.attachments &&
                    audioIds == note.audioAttachments && linkedPersonId == note.personId
                if (hasContent && !sameAsSaved) {
                    noteRepo.saveDraft(draftKey, title, contentValue.text, tags, attachmentIds, audioIds, linkedPersonId)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // If we restored from a draft, let the user know once.
    LaunchedEffect(Unit) {
        if (draft != null) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.note_draft_restored),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Load linked person name
    LaunchedEffect(linkedPersonId) {
        if (linkedPersonId != null) {
            withContext(Dispatchers.IO) {
                try {
                    val crypto = CryptoManager(context).also { it.initialize() }
                    val contactRepo = com.privateai.camera.security.ContactRepository(
                        java.io.File(context.filesDir, "vault/contacts"), crypto,
                        com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)
                    )
                    linkedPersonName = contactRepo.listContacts().find { it.id == linkedPersonId }?.name
                } catch (_: Exception) {}
            }
        } else linkedPersonName = null
    }

    // Load thumbnails
    LaunchedEffect(attachmentIds) {
        val crypto = CryptoManager(context).also { it.initialize() }
        val vault = VaultRepository(context, crypto)
        val folderManager = com.privateai.camera.security.FolderManager(context, crypto)
        val thumbs = mutableMapOf<String, Bitmap>()
        withContext(Dispatchers.IO) {
            val folderItems = folderManager.listAllFolders().flatMap { f ->
                vault.listFolderItems(folderManager.getFolderDir(f.id))
            }
            val allPhotos = (vault.listAllPhotos() + folderItems).distinctBy { it.id }
            attachmentIds.forEach { id ->
                allPhotos.find { it.id == id }?.let { photo ->
                    vault.loadThumbnail(photo)?.let { thumbs[id] = it }
                }
            }
        }
        attachmentThumbnails = thumbs
    }

    // Debounced grammar checking
    LaunchedEffect(grammarEnabled) {
        if (!grammarEnabled) { grammarErrors = emptyList(); return@LaunchedEffect }
        snapshotFlow { contentValue.text }
            .debounce(1500)
            .distinctUntilChanged()
            .collectLatest { text ->
                if (text.isBlank()) { grammarErrors = emptyList(); return@collectLatest }
                val localErrors = withContext(Dispatchers.Default) { localChecker.check(text) }
                val spellErrors = try { systemChecker.check(text) } catch (_: Exception) { emptyList() }
                val allErrors = localErrors.toMutableList()
                for (se in spellErrors) {
                    if (allErrors.none { it.fromPos < se.toPos && it.toPos > se.fromPos }) allErrors.add(se)
                }
                grammarErrors = allErrors.sortedBy { it.fromPos }
            }
    }

    // Dialogs
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_note_title)) },
            text = { Text(stringResource(R.string.delete_note_message)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if (showVaultPicker) {
        VaultPhotoPickerDialog(
            alreadyAttached = attachmentIds,
            onConfirm = { attachmentIds = (attachmentIds + it).distinct(); showVaultPicker = false },
            onDismiss = { showVaultPicker = false }
        )
    }
    if (showPersonPicker) {
        PersonPickerDialog(
            brandColor = brandColor,
            onPick = { id, name -> linkedPersonId = id; linkedPersonName = name; showPersonPicker = false },
            onDismiss = { showPersonPicker = false }
        )
    }
    if (showTagInput) {
        AlertDialog(
            onDismissRequest = { showTagInput = false; newTag = "" },
            title = { Text(stringResource(R.string.new_tag)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.tag_placeholder)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newTag.isNotBlank()) { tags = tags + newTag.trim() }
                        newTag = ""; showTagInput = false
                    })
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTag.isNotBlank()) { tags = tags + newTag.trim() }
                    newTag = ""; showTagInput = false
                }) { Text(stringResource(R.string.add_tag)) }
            },
            dismissButton = {
                TextButton(onClick = { showTagInput = false; newTag = "" }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // ── AI Actions bottom sheet ──
    if (showAiSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAiSheet = false; aiResult = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("AI Assistant", style = MaterialTheme.typography.titleMedium, color = brandColor)
                Spacer(Modifier.height(12.dp))

                if (aiProcessing) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = brandColor, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (aiResult != null) {
                    // Show result with accept/reject
                    Text(aiResult!!, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { aiResult = null }) { Text("Discard") }
                        Spacer(Modifier.weight(1f))
                        androidx.compose.material3.Button(onClick = {
                            val result = aiResult ?: return@Button
                            when (aiAction) {
                                "continue" -> {
                                    // Append to existing content
                                    val newContent = contentValue.text + "\n" + result
                                    contentValue = TextFieldValue(newContent, TextRange(newContent.length))
                                }
                                "summarize" -> {
                                    // Prepend summary above existing content
                                    val newContent = result + "\n\n" + contentValue.text
                                    contentValue = TextFieldValue(newContent, TextRange(newContent.length))
                                }
                                "extract_tasks" -> {
                                    isChecklistMode = true
                                    contentValue = TextFieldValue(result, TextRange(result.length))
                                }
                                else -> {
                                    // Rewrite, grammar fix, shorten, formal — replace content
                                    contentValue = TextFieldValue(result, TextRange(result.length))
                                }
                            }
                            aiResult = null
                            aiAction = ""
                            showAiSheet = false
                        }) { Text("Apply") }
                    }
                } else {
                    // Action list
                    AiActionItem("Summarize", "Create bullet-point summary") {
                        aiAction = "summarize"; aiProcessing = true
                        scope.launch {
                            val result = com.privateai.camera.bridge.GemmaRunner.complete(
                                context, com.privateai.camera.bridge.GemmaPrompts.summarize(contentValue.text),
                                com.privateai.camera.bridge.GemmaPrompts.NOTES_SYSTEM
                            )
                            aiResult = result
                            aiProcessing = false
                        }
                    }
                    AiActionItem("Generate Title", "Suggest a title from content") {
                        aiProcessing = true
                        scope.launch {
                            val result = com.privateai.camera.bridge.GemmaRunner.complete(
                                context, com.privateai.camera.bridge.GemmaPrompts.generateTitle(contentValue.text),
                                com.privateai.camera.bridge.GemmaPrompts.NOTES_SYSTEM
                            )
                            result?.let { title = it.trim().removeSurrounding("\"") }
                            aiProcessing = false
                            showAiSheet = false
                        }
                    }
                    AiActionItem("Extract Tasks", "Convert text to checklist") {
                        aiAction = "extract_tasks"; aiProcessing = true
                        scope.launch {
                            val result = com.privateai.camera.bridge.GemmaRunner.complete(
                                context, com.privateai.camera.bridge.GemmaPrompts.extractChecklist(contentValue.text),
                                com.privateai.camera.bridge.GemmaPrompts.NOTES_SYSTEM
                            )
                            aiResult = result
                            aiProcessing = false
                        }
                    }
                    AiActionItem("Fix Grammar", "Correct spelling and grammar") {
                        aiAction = "fix_grammar"; aiProcessing = true
                        scope.launch {
                            val result = com.privateai.camera.bridge.GemmaRunner.complete(
                                context, com.privateai.camera.bridge.GemmaPrompts.rewrite(contentValue.text, com.privateai.camera.bridge.RewriteStyle.FIX_GRAMMAR),
                                com.privateai.camera.bridge.GemmaPrompts.NOTES_SYSTEM
                            )
                            aiResult = result
                            aiProcessing = false
                        }
                    }
                    AiActionItem("Make Shorter", "Condense the text") {
                        aiAction = "shorten"; aiProcessing = true
                        scope.launch {
                            val result = com.privateai.camera.bridge.GemmaRunner.complete(
                                context, com.privateai.camera.bridge.GemmaPrompts.rewrite(contentValue.text, com.privateai.camera.bridge.RewriteStyle.SHORTEN),
                                com.privateai.camera.bridge.GemmaPrompts.NOTES_SYSTEM
                            )
                            aiResult = result
                            aiProcessing = false
                        }
                    }
                    AiActionItem("Make Formal", "Professional tone") {
                        aiAction = "formal"; aiProcessing = true
                        scope.launch {
                            val result = com.privateai.camera.bridge.GemmaRunner.complete(
                                context, com.privateai.camera.bridge.GemmaPrompts.rewrite(contentValue.text, com.privateai.camera.bridge.RewriteStyle.FORMAL),
                                com.privateai.camera.bridge.GemmaPrompts.NOTES_SYSTEM
                            )
                            aiResult = result
                            aiProcessing = false
                        }
                    }
                    AiActionItem("Continue Writing", "Add more content") {
                        aiAction = "continue"; aiProcessing = true
                        scope.launch {
                            val result = com.privateai.camera.bridge.GemmaRunner.complete(
                                context, com.privateai.camera.bridge.GemmaPrompts.continueWriting(contentValue.text),
                                com.privateai.camera.bridge.GemmaPrompts.NOTES_SYSTEM
                            )
                            aiResult = result
                            aiProcessing = false
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    Scaffold(
        // ── Top bar with tonal surface ──
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { grammarEnabled = !grammarEnabled }) {
                        Icon(
                            Icons.Default.Spellcheck, null,
                            tint = if (grammarEnabled) brandColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    if (!isNew) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.DeleteForever, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaceTone
                )
            )
        },
        // ── Toolbar above keyboard ──
        bottomBar = {
            Row(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime)
                    .background(surfaceTone)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FormatButton(Icons.Default.Checklist, "Checklist", if (isChecklistMode) brandColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) {
                    isChecklistMode = !isChecklistMode
                    if (isChecklistMode && !contentValue.text.trimStart().startsWith("- [")) {
                        val lines = contentValue.text.lines()
                        val cl = lines.joinToString("\n") { l -> if (l.isBlank()) "" else "- [ ] $l" }
                        contentValue = TextFieldValue(cl, TextRange(cl.length))
                    } else if (!isChecklistMode && contentValue.text.trimStart().startsWith("- [")) {
                        val t = contentValue.text.lines().joinToString("\n") { l -> l.removePrefix("- [x] ").removePrefix("- [ ] ") }
                        contentValue = TextFieldValue(t, TextRange(t.length))
                    }
                }
                FormatButton(Icons.Default.FormatBold, "Bold", brandColor) { contentValue = wrapSelection(contentValue, "**") }
                FormatButton(Icons.Default.FormatItalic, "Italic", brandColor) { contentValue = wrapSelection(contentValue, "*") }
                FormatButton(Icons.Default.FormatUnderlined, "Underline", brandColor) { contentValue = wrapSelection(contentValue, "__") }
                FormatButton(Icons.Default.FormatStrikethrough, "Strike", brandColor) { contentValue = wrapSelection(contentValue, "~~") }
                FormatButton(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, "Voice", if (isRecording) MaterialTheme.colorScheme.error else brandColor) {
                    if (isRecording) {
                        try { mediaRecorder?.stop(); mediaRecorder?.release(); mediaRecorder = null } catch (_: Exception) {}
                        isRecording = false
                        currentRecordingFile?.let { file ->
                            val audioId = "audio_${java.util.UUID.randomUUID()}"
                            val encFile = java.io.File(audioDir, "$audioId.enc")
                            val crypto = com.privateai.camera.security.CryptoManager(context).also { it.initialize() }
                            crypto.encryptFile(file, encFile); file.delete()
                            audioIds = audioIds + audioId
                        }; currentRecordingFile = null
                    } else if (!hasAudioPermission) {
                        pendingRecordAfterPermission = true
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        startVoiceRecording()
                    }
                }
                if (isRecording) {
                    LaunchedEffect(isRecording) { while (isRecording) { kotlinx.coroutines.delay(1000); recordingDuration++ } }
                    Text("%d:%02d".format(recordingDuration / 60, recordingDuration % 60), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                // AI sparkle button (only when Advanced AI enabled)
                if (aiAvailable && contentValue.text.isNotBlank()) {
                    FormatButton(Icons.Default.AutoAwesome, "AI", brandColor) {
                        focusManager.clearFocus()
                        showAiSheet = true
                    }
                }
                Spacer(Modifier.weight(1f))
                if (grammarEnabled && grammarErrors.isNotEmpty()) {
                    Row(Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.errorContainer).clickable {
                        var fixed = contentValue.text
                        grammarErrors.sortedByDescending { it.fromPos }.forEach { e -> if (e.suggestions.isNotEmpty() && e.fromPos < fixed.length && e.toPos <= fixed.length) fixed = fixed.substring(0, e.fromPos) + e.suggestions.first() + fixed.substring(e.toPos) }
                        contentValue = TextFieldValue(fixed, TextRange(fixed.length)); grammarErrors = emptyList()
                    }.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Spellcheck, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text("${grammarErrors.size}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        },
        // ── FAB Save button — most prominent element ──
        floatingActionButton = {
            if (title.isNotBlank() || contentValue.text.isNotBlank()) {
                FloatingActionButton(
                    onClick = { onSave(title, contentValue.text, tags, attachmentIds, audioIds, linkedPersonId) },
                    containerColor = brandColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Check, stringResource(R.string.save))
                }
            }
        }
    ) { padding ->
        // Detect keyboard visibility
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Title — bold headline ──
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                textStyle = TextStyle(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.5).sp
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                cursorBrush = SolidColor(brandColor),
                decorationBox = { innerTextField ->
                    if (title.isEmpty()) {
                        Text(
                            stringResource(R.string.title_label),
                            style = TextStyle(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                    }
                    innerTextField()
                }
            )

            // ── Persistent divider under title (always visible) ──
            HorizontalDivider(
                color = brandColor.copy(alpha = 0.25f),
                thickness = 1.dp,
                modifier = Modifier.padding(bottom = if (imeVisible) 10.dp else 0.dp)
            )

            // Hide metadata when keyboard is open to give space to content
            if (!imeVisible) {
            Spacer(Modifier.height(10.dp))

            // ── Compact meta row: Person chip + Tags + Add ──
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Person chip — compact pill
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (linkedPersonName != null) brandColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable {
                            if (linkedPersonName != null) { linkedPersonId = null; linkedPersonName = null }
                            else showPersonPicker = true
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (linkedPersonName != null) {
                        Box(
                            Modifier.size(18.dp).clip(CircleShape).background(brandColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                linkedPersonName!!.firstOrNull()?.uppercase() ?: "?",
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp
                            )
                        }
                        Text(linkedPersonName!!, style = MaterialTheme.typography.labelMedium, color = brandColor)
                        Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = brandColor.copy(alpha = 0.7f))
                    } else {
                        Icon(Icons.Default.PersonAdd, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Text(stringResource(R.string.link_to_person), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }

                tags.forEach { tag ->
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(brandColor.copy(alpha = 0.1f))
                            .clickable { tags = tags - tag }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text("#$tag", style = MaterialTheme.typography.labelMedium, color = brandColor)
                    }
                }
                allTags.filter { it !in tags }.take(3).forEach { tag ->
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                            .clickable { tags = tags + tag }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text("#$tag", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
                Box(
                    Modifier.size(28.dp).clip(CircleShape)
                        .background(brandColor.copy(alpha = 0.06f))
                        .clickable { focusManager.clearFocus(); showTagInput = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.new_tag), Modifier.size(14.dp), tint = brandColor)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Media strip — photos + Add tile in single horizontal row ──
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(attachmentIds) { id ->
                    Box(
                        Modifier.width(140.dp).aspectRatio(16f / 10f)
                            .clip(RoundedCornerShape(24.dp))
                    ) {
                        val thumb = attachmentThumbnails[id]
                        if (thumb != null) {
                            Image(
                                bitmap = thumb.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.BrokenImage, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        stringResource(R.string.image_deleted),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                        // Delete button
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { attachmentIds = attachmentIds - id },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = Color.White)
                        }
                    }
                }
                // Add tile at end of strip
                item {
                    Box(
                        Modifier.width(140.dp).aspectRatio(16f / 10f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        brandColor.copy(alpha = 0.06f),
                                        brandColor.copy(alpha = 0.12f)
                                    )
                                )
                            )
                            .border(1.dp, brandColor.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                            .clickable { showVaultPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(24.dp), tint = brandColor.copy(alpha = 0.8f))
                            Text(
                                stringResource(R.string.attach_media),
                                style = MaterialTheme.typography.labelSmall,
                                color = brandColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // ── Audio attachments — waveform bubbles ──
            if (audioIds.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))

                // Play-all toggle — only useful when 2+ clips. Tap once to play
                // all clips in sequence; OnCompletionListener advances to the
                // next clip in audioIds order until the list is exhausted.
                if (audioIds.size >= 2) {
                    val anyPlaying = playingAudioId != null
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                if (playAllMode || anyPlaying) {
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    playingAudioId = null
                                    playAllMode = false
                                } else {
                                    playAllMode = true
                                    audioIds.firstOrNull()?.let { startPlayingAudio(it) }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            if (playAllMode) Icons.Default.Stop else Icons.Default.PlayArrow,
                            null, Modifier.size(18.dp), tint = brandColor
                        )
                        Text(
                            if (playAllMode) stringResource(R.string.note_audio_stop_all)
                            else stringResource(R.string.note_audio_play_all),
                            style = MaterialTheme.typography.labelMedium,
                            color = brandColor
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

                audioIds.forEach { audioId ->
                    val isPlaying = playingAudioId == audioId
                    var audioDuration by remember { mutableIntStateOf(0) }
                    var audioPosition by remember { mutableIntStateOf(0) }

                    // Update position while playing
                    if (isPlaying && mediaPlayer != null) {
                        LaunchedEffect(isPlaying) {
                            while (isPlaying && mediaPlayer?.isPlaying == true) {
                                audioPosition = mediaPlayer?.currentPosition ?: 0
                                audioDuration = mediaPlayer?.duration ?: 0
                                kotlinx.coroutines.delay(80)
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(brandColor.copy(alpha = 0.08f))
                            .border(1.dp, brandColor.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Play/Pause button
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(brandColor)
                                .clickable {
                                    if (isPlaying) {
                                        mediaPlayer?.pause()
                                        playingAudioId = null
                                        playAllMode = false
                                    } else {
                                        // Single-clip tap: turn off play-all so we don't auto-advance.
                                        playAllMode = false
                                        startPlayingAudio(audioId)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null, Modifier.size(22.dp), tint = Color.White
                            )
                        }

                        // Waveform
                        val progress = if (audioDuration > 0) audioPosition.toFloat() / audioDuration else 0f
                        Waveform(
                            seed = audioId.hashCode(),
                            progress = progress,
                            playedColor = brandColor,
                            unplayedColor = brandColor.copy(alpha = 0.25f),
                            modifier = Modifier.weight(1f).height(32.dp).clickable {
                                if (mediaPlayer != null && audioDuration > 0) {
                                    // Tap-to-seek: clicking re-plays from start as a simple fallback
                                    mediaPlayer?.seekTo(0)
                                    audioPosition = 0
                                }
                            }
                        )

                        // Time
                        Text(
                            formatMs(if (isPlaying) audioPosition else audioDuration),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = brandColor
                        )

                        // Delete
                        Icon(Icons.Default.Close, null, Modifier.size(18.dp).clickable {
                            if (isPlaying) { mediaPlayer?.release(); playingAudioId = null }
                            java.io.File(audioDir, "$audioId.enc").delete()
                            audioIds = audioIds - audioId
                        }, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            } // end !imeVisible

            // ── Content — checklist or text canvas ──
            val activeErrors = if (grammarEnabled) grammarErrors else emptyList()
            LaunchedEffect(activeErrors) {
                contentValue = TextFieldValue(contentValue.text, contentValue.selection, contentValue.composition)
            }

            if (isChecklistMode) {
                // ── Gamified Checklist Card ──
                val lines = contentValue.text.lines()
                val checkedCount = lines.count { it.startsWith("- [x]") }
                val totalCount = lines.count { it.startsWith("- [") }
                val isComplete = totalCount > 0 && checkedCount == totalCount

                // Success accent color — warm mint
                val successColor = Color(0xFF4CAF50)
                val cardAccent by animateColorAsState(
                    targetValue = if (isComplete) successColor else brandColor,
                    animationSpec = tween(500),
                    label = "checklistAccent"
                )
                val progressAnimated by animateFloatAsState(
                    targetValue = if (totalCount > 0) checkedCount.toFloat() / totalCount else 0f,
                    animationSpec = tween(400),
                    label = "checklistProgress"
                )

                Column(
                    Modifier.fillMaxWidth().weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(cardAccent.copy(alpha = if (isComplete) 0.1f else 0.05f))
                        .border(1.dp, cardAccent.copy(alpha = if (isComplete) 0.4f else 0.15f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    // Progress header inside card
                    if (totalCount > 0) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                if (isComplete) "All done! \uD83C\uDF89" else "Tasks",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = cardAccent
                            )
                            LinearProgressIndicator(
                                progress = { progressAnimated },
                                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = cardAccent,
                                trackColor = cardAccent.copy(alpha = 0.15f)
                            )
                            Text(
                                "$checkedCount/$totalCount",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = cardAccent
                            )
                        }
                    }

                    Column(
                        Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        lines.forEachIndexed { idx, line ->
                            if (line.startsWith("- [x] ") || line.startsWith("- [ ] ")) {
                                val isChecked = line.startsWith("- [x]")
                                val itemText = line.removePrefix("- [x] ").removePrefix("- [ ] ")
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        val newLines = lines.toMutableList()
                                        newLines[idx] = if (isChecked) "- [ ] $itemText" else "- [x] $itemText"
                                        contentValue = TextFieldValue(newLines.joinToString("\n"), TextRange(contentValue.text.length))
                                    }.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                        null, Modifier.size(22.dp),
                                        tint = if (isChecked) cardAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        itemText,
                                        style = TextStyle(
                                            fontSize = 16.sp,
                                            color = if (isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                            textDecoration = if (isChecked) TextDecoration.LineThrough else null
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        Icons.Default.Close, null, Modifier.size(16.dp).clickable {
                                            val newLines = lines.toMutableList()
                                            newLines.removeAt(idx)
                                            contentValue = TextFieldValue(newLines.joinToString("\n"), TextRange(0))
                                        },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                        // Add new item
                        var newItemText by remember { mutableStateOf("") }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(22.dp), tint = cardAccent.copy(alpha = 0.6f))
                            BasicTextField(
                                value = newItemText,
                                onValueChange = { newItemText = it },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                                    if (newItemText.isNotBlank()) {
                                        val newContent = contentValue.text + (if (contentValue.text.isNotEmpty()) "\n" else "") + "- [ ] $newItemText"
                                        contentValue = TextFieldValue(newContent, TextRange(newContent.length))
                                        newItemText = ""
                                    }
                                }),
                                cursorBrush = SolidColor(cardAccent),
                                decorationBox = { innerTextField ->
                                    if (newItemText.isEmpty()) Text("Add item...", style = TextStyle(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                                    innerTextField()
                                }
                            )
                        }
                    }
                }
            } else {
                // ── Text canvas with Markdown + grammar ──
                BasicTextField(
                    value = contentValue,
                    onValueChange = { contentValue = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    cursorBrush = SolidColor(brandColor),
                    visualTransformation = MarkdownTransformation(
                        markerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        grammarErrors = activeErrors,
                        errorColor = MaterialTheme.colorScheme.error
                    ),
                    decorationBox = { innerTextField ->
                        if (contentValue.text.isEmpty()) {
                            Text(
                                stringResource(R.string.note_placeholder),
                                style = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    }
}

// ── Format button ──
@Composable
private fun FormatButton(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(46.dp)) {
        Icon(icon, desc, Modifier.size(26.dp), tint = tint)
    }
}

// ── AI action item in bottom sheet ──
@Composable
private fun AiActionItem(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Waveform for audio playback ──
@Composable
private fun Waveform(
    seed: Int,
    progress: Float,
    playedColor: Color,
    unplayedColor: Color,
    modifier: Modifier = Modifier
) {
    // Deterministic pseudo-random heights based on seed
    val barHeights = remember(seed) {
        val rnd = java.util.Random(seed.toLong())
        List(40) { 0.25f + rnd.nextFloat() * 0.75f }
    }

    Canvas(modifier = modifier) {
        val barCount = barHeights.size
        val barWidth = size.width / (barCount * 2)
        val gap = barWidth
        val centerY = size.height / 2f
        val playedBars = (progress * barCount).toInt()

        barHeights.forEachIndexed { i, h ->
            val barHeight = size.height * h
            val x = i * (barWidth + gap) + gap / 2
            drawRoundRect(
                color = if (i <= playedBars) playedColor else unplayedColor,
                topLeft = androidx.compose.ui.geometry.Offset(x, centerY - barHeight / 2),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
            )
        }
    }
}

// ── Markdown wrap helper ──
private fun formatMs(ms: Int): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private fun wrapSelection(value: TextFieldValue, marker: String): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    val mLen = marker.length

    if (start == end) {
        val newText = text.substring(0, start) + marker + marker + text.substring(start)
        return TextFieldValue(newText, TextRange(start + mLen))
    }
    val selected = text.substring(start, end)
    if (start >= mLen && end + mLen <= text.length &&
        text.substring(start - mLen, start) == marker &&
        text.substring(end, end + mLen) == marker
    ) {
        val newText = text.substring(0, start - mLen) + selected + text.substring(end + mLen)
        return TextFieldValue(newText, TextRange(start - mLen, end - mLen))
    }
    val newText = text.substring(0, start) + marker + selected + marker + text.substring(end)
    return TextFieldValue(newText, TextRange(start + mLen, end + mLen))
}

// ── Person picker dialog ──
@Composable
private fun PersonPickerDialog(
    brandColor: Color,
    onPick: (id: String, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val contacts = remember {
        try {
            val crypto = CryptoManager(context).also { it.initialize() }
            com.privateai.camera.security.ContactRepository(
                java.io.File(context.filesDir, "vault/contacts"), crypto,
                com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)
            ).listContacts()
        } catch (_: Exception) { emptyList() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_to_person)) },
        text = {
            if (contacts.isEmpty()) {
                Text(stringResource(R.string.no_people_yet))
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    contacts.forEach { person ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(person.id, person.name) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(36.dp).background(brandColor, CircleShape), contentAlignment = Alignment.Center) {
                                Text(person.name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Text(person.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// ── Vault photo picker dialog ──
@Composable
private fun VaultPhotoPickerDialog(
    alreadyAttached: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var vaultPhotos by remember { mutableStateOf<List<VaultPhoto>>(emptyList()) }
    var thumbnails by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val crypto = CryptoManager(context).also { it.initialize() }
        val vault = VaultRepository(context, crypto)
        withContext(Dispatchers.IO) {
            val folderManager = com.privateai.camera.security.FolderManager(context, crypto)
            val folderItems = folderManager.listAllFolders().flatMap { f -> vault.listFolderItems(folderManager.getFolderDir(f.id)) }
            val photos = (vault.listAllPhotos() + folderItems).distinctBy { it.id }
                .filter { it.mediaType == VaultMediaType.PHOTO }.sortedByDescending { it.timestamp }
            val thumbs = mutableMapOf<String, Bitmap>()
            photos.forEach { photo -> vault.loadThumbnail(photo)?.let { thumbs[photo.id] = it } }
            vaultPhotos = photos
            thumbnails = thumbs
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.attach_from_vault)) },
        text = {
            if (isLoading) {
                Text(stringResource(R.string.loading))
            } else if (vaultPhotos.isEmpty()) {
                Text(stringResource(R.string.no_vault_photos))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(vaultPhotos) { photo ->
                        val isSelected = photo.id in selectedIds
                        val isAlreadyAttached = photo.id in alreadyAttached
                        Box(
                            modifier = Modifier.size(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else if (isAlreadyAttached) MaterialTheme.colorScheme.outline
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = !isAlreadyAttached) {
                                    selectedIds = if (isSelected) selectedIds - photo.id else selectedIds + photo.id
                                }
                        ) {
                            val thumb = thumbnails[photo.id]
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb.asImageBitmap(), contentDescription = null,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedIds.toList()) }, enabled = selectedIds.isNotEmpty()) {
                Text(stringResource(R.string.attach_count, selectedIds.size))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
