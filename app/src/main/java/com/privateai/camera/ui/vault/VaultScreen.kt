// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.vault

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.outlined.LabelOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.privateai.camera.R
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.DuressManager
import com.privateai.camera.security.PinRateLimiter
import com.privateai.camera.security.FolderManager
import com.privateai.camera.security.VaultFolder
import com.privateai.camera.security.VaultLockManager
import com.privateai.camera.ui.onboarding.AuthMode
import com.privateai.camera.ui.onboarding.getAuthMode
import com.privateai.camera.security.VaultCategory
import com.privateai.camera.security.VaultMediaType
import com.privateai.camera.security.VaultPhoto
import com.privateai.camera.security.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SuggestionChip
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.text.style.TextAlign
import com.privateai.camera.bridge.FaceEmbedder
import com.privateai.camera.bridge.ImageClassifier
import com.privateai.camera.security.PhotoIndex
import com.privateai.camera.security.PrivoraDatabase

// Screens: LOCKED -> CATEGORIES -> GALLERY -> VIEWER / VIDEO_PLAYER / PDF_VIEWER
private enum class VaultPage { LOCKED, CATEGORIES, GALLERY, VIEWER, VIDEO_PLAYER, PDF_VIEWER, FOLDER_VIEW, TRASH, WIFI_TRANSFER }

/**
 * Sort order for vault photo grids. Persisted across launches in
 * `app_settings/vault_sort_mode`. Applied at every `photos = ...` and
 * `searchResults = ...` assignment so every list (gallery, folder view,
 * smart filters, face groups, search results) respects the user's choice.
 *
 * UPDATED_* falls back to creation timestamp when a photo has never been
 * edited, so freshly-imported photos slot in alongside edited ones.
 */
private enum class SortMode { CREATED_DESC, CREATED_ASC, UPDATED_DESC, UPDATED_ASC }

private fun getSortMode(context: android.content.Context): SortMode {
    val name = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        .getString("vault_sort_mode", null) ?: return SortMode.CREATED_DESC
    return try { SortMode.valueOf(name) } catch (_: Exception) { SortMode.CREATED_DESC }
}

private fun setSortMode(context: android.content.Context, mode: SortMode) {
    context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        .edit().putString("vault_sort_mode", mode.name).apply()
}

/**
 * Sort a list of photos for display. `updatedTimes` carries the
 * (photoId → last-edit millis) map from [PhotoIndex.getUpdatedTimes]; for
 * the CREATED_* modes it can safely be empty.
 */
private fun sortPhotos(
    photos: List<VaultPhoto>,
    mode: SortMode,
    updatedTimes: Map<String, Long>
): List<VaultPhoto> {
    return when (mode) {
        SortMode.CREATED_DESC -> photos.sortedByDescending { it.timestamp }
        SortMode.CREATED_ASC -> photos.sortedBy { it.timestamp }
        SortMode.UPDATED_DESC -> photos.sortedByDescending {
            updatedTimes[it.id]?.takeIf { t -> t > 0L } ?: it.timestamp
        }
        SortMode.UPDATED_ASC -> photos.sortedBy {
            updatedTimes[it.id]?.takeIf { t -> t > 0L } ?: it.timestamp
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    onBack: (() -> Unit)? = null,
    initialSearchQuery: String = "",
    initialOpenPhotoId: String? = null,
    onNavigate: ((route: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val crypto = remember { CryptoManager(context) }
    val vault = remember { VaultRepository(context, crypto) }
    // Lazy: ContactRepository's constructor opens the encrypted DB, which
    // requires crypto.initialize() to have run first. That doesn't happen
    // until the vault unlock screen passes, so we can't eagerly construct
    // here — that crashed at composition time with "CryptoManager not
    // initialized". Lazy defers the build to first access (the search
    // lambda), by which point unlock has happened.
    val contactRepoLazy = remember {
        lazy {
            com.privateai.camera.security.ContactRepository(
                java.io.File(context.filesDir, "vault/contacts"),
                crypto,
                com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)
            )
        }
    }

    // Check if already unlocked within grace period (e.g. from Notes, or returning quickly)
    val startUnlocked = remember {
        VaultLockManager.isUnlockedWithinGrace(context) && crypto.initialize()
    }
    var page by remember { mutableStateOf(if (startUnlocked) VaultPage.CATEGORIES else VaultPage.LOCKED) }
    var currentCategory by remember { mutableStateOf(VaultCategory.CAMERA) }
    var photos by remember { mutableStateOf<List<VaultPhoto>>(emptyList()) }
    // Sort preference (creation/update × asc/desc) + the per-photo
    // last-edit timestamps used by UPDATED_*. Persisted in app_settings.
    // When sortMode changes, a LaunchedEffect below re-sorts the live
    // photos + searchResults lists in place.
    var sortMode by remember { mutableStateOf(getSortMode(context)) }
    var updatedTimes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var thumbnails by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var categoryCounts by remember { mutableStateOf<Map<VaultCategory, Int>>(emptyMap()) }
    var trashCount by remember { mutableIntStateOf(0) }
    var trashItems by remember { mutableStateOf<List<VaultRepository.TrashedItem>>(emptyList()) }
    var trashThumbnails by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

    // Viewer state
    var viewerPhoto by remember { mutableStateOf<VaultPhoto?>(null) }
    var viewerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var videoTempFile by remember { mutableStateOf<File?>(null) }
    var pdfTempFile by remember { mutableStateOf<File?>(null) }
    var pdfTitle by remember { mutableStateOf("") }
    // Holds the decrypted OCR text when the user taps "View extracted text"
    // in the PDF viewer overflow. null = dialog hidden.
    var extractedTextDialog by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    // Custom folders
    val folderManager = remember { FolderManager(context, crypto) }
    var rootFolders by remember { mutableStateOf<List<VaultFolder>>(emptyList()) }
    var currentFolder by remember { mutableStateOf<VaultFolder?>(null) }
    var subfolders by remember { mutableStateOf<List<VaultFolder>>(emptyList()) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showRenameFolderDialog by remember { mutableStateOf(false) }
    var showDeleteFolderDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var editorPhoto by remember { mutableStateOf<VaultPhoto?>(null) }
    var editorBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Duress mode — blocks all data access when active
    var isDuressActive by remember { mutableStateOf(VaultLockManager.isDuressActive) }
    val faceThreshold = remember { context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE).getFloat("face_threshold", 0.55f) }

    // Search
    var searchQuery by remember { mutableStateOf(initialSearchQuery) }

    // Track where the viewer was opened from (for correct back navigation)
    var viewerFromHidden by remember { mutableStateOf(false) }
    var viewerFromFolder by remember { mutableStateOf(false) }
    // True when the viewer was opened via the `vault?openPhotoId=...` deep
    // link (e.g. from an Assistant search_photos thumb). Back from the viewer
    // in this mode pops the entire Vault destination so the user lands back
    // where the link came from (the Assistant chat), not in a Vault grid
    // they never visited.
    var viewerFromDeepLink by remember { mutableStateOf(false) }
    // True when the viewer was opened from a face-group / search / smart-
    // filter (blurry, duplicates) result list. Back returns to the search-
    // results UI (rendered over CATEGORIES via the search/face/smart state
    // flags) instead of dropping the user into an empty Gallery and then
    // forcing two more Back presses to escape.
    var viewerFromSearch by remember { mutableStateOf(false) }

    // Hidden folder — tap vault title N times to reveal (like Android dev options)
    val hiddenTapThreshold = remember {
        context.getSharedPreferences("vault_hidden", android.content.Context.MODE_PRIVATE)
            .getInt("tap_count", 7)
    }
    var hiddenTapCount by remember { mutableStateOf(0) }
    var isHiddenFolderActive by remember { mutableStateOf(false) }
    var hiddenLastTapTime by remember { mutableLongStateOf(0L) }
    var searchResults by remember { mutableStateOf<List<VaultPhoto>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchThumbnails by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

    // Selection
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Photo Intelligence
    var photoIndex by remember { mutableStateOf<PhotoIndex?>(null) }
    var classifier by remember { mutableStateOf<ImageClassifier?>(null) }
    var objectDetector by remember { mutableStateOf<com.privateai.camera.bridge.OnnxDetector?>(null) }
    var isIndexing by remember { mutableStateOf(false) }
    var indexProgress by remember { mutableStateOf(0 to 0) }  // (done, total)
    // Import progress
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableIntStateOf(0) }
    var importTotal by remember { mutableIntStateOf(0) }
    var importErrors by remember { mutableIntStateOf(0) }
    var smartMode by remember { mutableStateOf<String?>(null) } // "duplicates", "blurry", or null
    // Filter counts (loaded in background)
    var duplicateGroupCount by remember { mutableIntStateOf(-1) } // -1 = not loaded
    var blurryCount by remember { mutableIntStateOf(-1) }
    var faceGroupCount by remember { mutableIntStateOf(-1) }
    var isSmartLoading by remember { mutableStateOf(false) }
    var showFaceGroups by remember { mutableStateOf(false) }
    var faceGroups by remember { mutableStateOf<Map<String, List<Triple<String, Int, PhotoIndex.FaceEntry>>>>(emptyMap()) }
    var selectedFaceGroup by remember { mutableStateOf<String?>(null) }
    var renamingGroup by remember { mutableStateOf<String?>(null) }
    var mergeSourceGroup by remember { mutableStateOf<String?>(null) } // first group selected for merge
    var mergeTargetGroup by remember { mutableStateOf<String?>(null) } // second group to confirm merge
    var searchFromViewer by remember { mutableStateOf(false) } // true = back returns to VIEWER
    var searchSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    // Person detected by [PhotoIndex.searchByPersonAndTags] from the query text;
    // when non-null we render a removable "Person: Anas ×" chip above the grid
    // so the user sees their query was interpreted as a face filter.
    var detectedPerson by remember { mutableStateOf<String?>(null) }
    var detectedResidual by remember { mutableStateOf("") }
    var duplicateGroups by remember { mutableStateOf<List<List<String>>>(emptyList()) }

    // Virtual smart views
    var isVirtualPhotos by remember { mutableStateOf(false) }
    var isVirtualVideos by remember { mutableStateOf(false) }
    var isVirtualFiles by remember { mutableStateOf(false) }

    // Gallery scroll state — hoisted above the page `when` switch so it
    // survives the gallery → viewer → gallery round-trip. Otherwise the
    // user lands at the top of a 1500-photo list every time they peek at
    // an image.
    val galleryListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var filesFilter by remember { mutableStateOf("all") } // "all", "photo", "video", "pdf", "other"

    // Auto-lock with shared grace period
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    VaultLockManager.markLeft()
                }
                Lifecycle.Event.ON_START -> {
                    if (page != VaultPage.LOCKED && !VaultLockManager.isUnlockedWithinGrace(context)) {
                        page = VaultPage.LOCKED
                        crypto.lock()
                        VaultLockManager.lock()
                        // thumbnails cleared — GC handles bitmap recycling (Compose may still be drawing)
                        thumbnails = emptyMap()
                        // viewerBitmap cleared — GC handles recycling
                        viewerBitmap = null
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // thumbnails cleared — GC handles bitmap recycling (Compose may still be drawing)
            // viewerBitmap cleared — GC handles recycling
            videoTempFile?.delete()
        }
    }

    // Auto-import shared URIs from other apps (Share → Privora)
    // Uses the same import logic as the manual picker below.
    var pendingShareProcessed by remember { mutableStateOf(false) }
    fun runImport(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        isImporting = true
        importTotal = uris.size
        importProgress = 0
        importErrors = 0
        scope.launch {
            var importedImages = 0; var importedPdfs = 0; var importedVideos = 0; var skippedLarge = 0
            VaultLockManager.markUnlocked()
            withContext(Dispatchers.IO) {
                uris.forEachIndexed { idx, uri ->
                    VaultLockManager.markUnlocked()
                    try {
                        val mimeType = context.contentResolver.getType(uri)
                        if (mimeType?.startsWith("video/") == true) {
                            val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                            if (size > 150L * 1024 * 1024) { skippedLarge++; importErrors++; withContext(Dispatchers.Main) { importProgress = idx + 1 }; return@forEachIndexed }
                            val tempFile = java.io.File(context.cacheDir, "share_vid_${System.currentTimeMillis()}.mp4")
                            context.contentResolver.openInputStream(uri)?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
                            vault.saveVideo(tempFile); importedVideos++
                        } else {
                            val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                            if (bytes == null) { importErrors++; withContext(Dispatchers.Main) { importProgress = idx + 1 }; return@forEachIndexed }
                            if (mimeType?.startsWith("image/") == true) {
                                val meta = com.privateai.camera.util.ExifUtils.readMetadata(bytes)
                                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bitmap != null) { vault.savePhoto(bitmap, VaultCategory.CAMERA, metadata = meta); bitmap.recycle(); importedImages++ }
                                else importErrors++
                            } else if (mimeType == "application/pdf") {
                                vault.saveFile(bytes, "share_${System.currentTimeMillis()}.pdf", VaultCategory.SCAN); importedPdfs++
                            }
                        }
                    } catch (_: Exception) { importErrors++ }
                    withContext(Dispatchers.Main) { importProgress = idx + 1 }
                }
            }
            isImporting = false
            if (!isDuressActive) { categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders(); trashCount = vault.trashCount() }
            val summary = buildString {
                if (importedImages > 0) append("$importedImages photo(s) ")
                if (importedVideos > 0) append("$importedVideos video(s) ")
                if (importedPdfs > 0) append("$importedPdfs PDF(s) ")
                append("saved to vault")
                if (importErrors > 0) append(" ($importErrors failed)")
            }
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, summary, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // Check for pending share from other apps on first composition
    LaunchedEffect(Unit) {
        if (!pendingShareProcessed) {
            pendingShareProcessed = true
            val (shareUris, _) = com.privateai.camera.MainActivity.consumePendingShare()
            if (!shareUris.isNullOrEmpty()) {
                runImport(shareUris)
            }
        }
    }

    // Import files launcher (manual picker)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        isImporting = true
        importTotal = uris.size
        importProgress = 0
        importErrors = 0
        scope.launch {
            var importedImages = 0
            var importedPdfs = 0
            var importedVideos = 0
            var skippedLarge = 0
            VaultLockManager.markUnlocked()
            // Capture destination folder ONCE before the long-running IO loop.
            // Previously `folderDir` was re-evaluated inside each iteration by
            // reading the `page` and `currentFolder` Compose state vars from
            // inside withContext(Dispatchers.IO) — a 2000-photo import takes
            // minutes, and any recomposition that flipped `page` mid-stream
            // would cause the rest of the batch to fall through to the
            // default CAMERA category. Capture the snapshot at the start so
            // every photo in this batch lands in the same place.
            val capturedPage = page
            val capturedFolder = currentFolder
            val capturedFolderDir = if (capturedPage == VaultPage.FOLDER_VIEW) {
                capturedFolder?.let { folderManager.getFolderDir(it.id) }
            } else null
            withContext(Dispatchers.IO) {
                uris.forEachIndexed { idx, uri ->
                    VaultLockManager.markUnlocked()
                    try {
                        val mimeType = context.contentResolver.getType(uri)
                        val folderDir = capturedFolderDir

                        if (mimeType?.startsWith("video/") == true) {
                            val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                            if (size > 150L * 1024 * 1024) { skippedLarge++; importErrors++; withContext(Dispatchers.Main) { importProgress = idx + 1 }; return@forEachIndexed }
                            val tempFile = java.io.File(context.cacheDir, "import_vid_${System.currentTimeMillis()}.mp4")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                tempFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            if (folderDir != null) {
                                vault.saveVideo(tempFile, VaultCategory.VIDEO)
                                val videoItems = vault.listPhotos(VaultCategory.VIDEO)
                                videoItems.firstOrNull()?.let { vault.moveToFolder(it, folderDir) }
                            } else {
                                vault.saveVideo(tempFile)
                            }
                            importedVideos++
                        } else {
                            val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                            if (bytes == null) { importErrors++; withContext(Dispatchers.Main) { importProgress = idx + 1 }; return@forEachIndexed }
                            if (mimeType?.startsWith("image/") == true) {
                                // Pull full EXIF metadata before BitmapFactory
                                // strips it — preserves capture date,
                                // original dimensions, orientation, and GPS
                                // via an encrypted sidecar.
                                val meta = com.privateai.camera.util.ExifUtils.readMetadata(bytes)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bitmap == null) { importErrors++; withContext(Dispatchers.Main) { importProgress = idx + 1 }; return@forEachIndexed }
                                if (folderDir != null) {
                                    vault.savePhotoToFolder(bitmap, folderDir, metadata = meta)
                                } else {
                                    vault.savePhoto(bitmap, VaultCategory.CAMERA, metadata = meta)
                                }
                                bitmap.recycle()
                                importedImages++
                            } else if (mimeType == "application/pdf") {
                                if (folderDir != null) {
                                    // Was previously writing the PDF twice — once into
                                    // `vault/files/` via saveFile() AND directly into the
                                    // folder via crypto.encryptToFile(). One PDF appeared
                                    // in both the FILES category and the folder. Now we
                                    // write to the folder only, matching how photos use
                                    // savePhotoToFolder().
                                    val pdfFile = File(folderDir, "import_${System.currentTimeMillis()}.pdf.enc")
                                    crypto.encryptToFile(bytes, pdfFile)
                                } else {
                                    vault.saveFile(bytes, "import_${System.currentTimeMillis()}.pdf", VaultCategory.SCAN)
                                }
                                importedPdfs++
                            }
                        }
                    } catch (_: Exception) { importErrors++ }
                    withContext(Dispatchers.Main) { importProgress = idx + 1 }
                }
            }
            isImporting = false
            if (!isDuressActive) { categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders(); trashCount = vault.trashCount() }
            // Refresh folder view if importing into a folder
            val refreshFolder = currentFolder
            if (page == VaultPage.FOLDER_VIEW && refreshFolder != null) {
                val dir = folderManager.getFolderDir(refreshFolder.id)
                photos = if (isDuressActive) emptyList() else vault.listFolderItems(dir)
                // Load thumbnails for newly imported items
                withContext(Dispatchers.IO) {
                    val thumbMap = thumbnails.toMutableMap()
                    photos.forEach { photo ->
                        if (photo.id !in thumbMap) {
                            vault.loadThumbnail(photo)?.let { thumbMap[photo.id] = it }
                        }
                    }
                    withContext(Dispatchers.Main) { thumbnails = thumbMap }
                }
            }
            val parts = mutableListOf<String>()
            if (importedImages > 0) parts.add(context.getString(R.string.n_photos, importedImages))
            if (importedVideos > 0) parts.add(context.getString(R.string.n_videos, importedVideos))
            if (importedPdfs > 0) parts.add(context.getString(R.string.n_pdfs, importedPdfs))
            val dest = if (currentFolder != null && page == VaultPage.FOLDER_VIEW) context.getString(R.string.to_folder, currentFolder?.name.orEmpty()) else ""
            val msg = if (parts.isNotEmpty()) context.getString(R.string.imported_summary, parts.joinToString(" + "), dest) else ""
            val skipMsg = if (skippedLarge > 0) context.getString(R.string.skipped_large, skippedLarge) else ""
            if (msg.isNotEmpty() || skipMsg.isNotEmpty()) {
                Toast.makeText(context, "$msg$skipMsg", Toast.LENGTH_LONG).show()
            }
            // Auto-index newly imported photos in background
            val pi = photoIndex
            val cl = classifier
            if (pi != null && cl != null && importedImages > 0) {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val fe = try { com.privateai.camera.bridge.FaceEmbedder(context) } catch (_: Exception) { null }
                        val fromCats = vault.listAllPhotos()
                        val fromFolders = folderManager.listAllFolders().flatMap { f -> vault.listFolderItems(folderManager.getFolderDir(f.id)) }
                        val allPhotos = (fromCats + fromFolders).distinctBy { p -> p.id }.filter { p -> p.mediaType == VaultMediaType.PHOTO }
                        val unindexed = allPhotos.filter { p -> !pi.isIndexed(p.id) }
                        if (unindexed.isNotEmpty()) {
                            isIndexing = true
                            unindexed.forEachIndexed { i, photo ->
                                val bmp = vault.loadFullPhoto(photo) ?: vault.loadThumbnail(photo)
                                bmp?.let { img ->
                                    try { pi.indexPhoto(photo.id, img, cl, faceEmbedder = fe, detector = objectDetector) } catch (_: Exception) {}
                                    if (!img.isRecycled) img.recycle()
                                }
                                indexProgress = i + 1 to unindexed.size
                            }
                            isIndexing = false
                        }
                        fe?.release()
                    }
                }
            }
        }
    }

    fun authenticate() {
        val activity = context as? FragmentActivity ?: return
        val bm = BiometricManager.from(context)
        val canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS

        if (!canAuth) {
            if (crypto.initialize()) {
                VaultLockManager.markUnlocked()
                categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders()
                page = VaultPage.CATEGORIES
            }
            return
        }

        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (crypto.initialize()) {
                        VaultLockManager.markUnlocked()
                        categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders()
                        page = VaultPage.CATEGORIES
                    }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.unlock_vault))
                .setSubtitle(context.getString(R.string.authenticate_to_access_vault))
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                ).build()
        )
    }

    /** Get ALL items from all categories + all folders (unified view for AI). */
    fun getAllVaultItems(): List<VaultPhoto> {
        val fromCategories = vault.listAllPhotos()
        val fromFolders = folderManager.listAllFolders().flatMap { vault.listFolderItems(folderManager.getFolderDir(it.id)) }
        return (fromCategories + fromFolders).distinctBy { it.id }.sortedByDescending { it.timestamp }
    }

    fun openCategory(cat: VaultCategory) {
        if (isDuressActive) {
            // Duress mode: show empty gallery
            photos = emptyList()
            thumbnails = emptyMap()
            currentCategory = cat
            page = VaultPage.GALLERY
            return
        }
        scope.launch {
            if (!crypto.isUnlocked()) crypto.initialize()
            val loaded = withContext(Dispatchers.IO) { vault.listPhotos(cat) }

            // Show the gallery immediately. With 1500 photos, the previous
            // approach blocked the page transition for ~3 seconds while every
            // thumbnail was decrypted + decoded. Now the LazyColumn renders
            // right away with grey placeholders (existing aspect-ratio default
            // in the cell code), and thumbnails fill in as they arrive.
            photos = sortPhotos(loaded, sortMode, updatedTimes)
            thumbnails = emptyMap()
            currentCategory = cat
            selectedIds = emptySet()
            isSelectionMode = false
            page = VaultPage.GALLERY

            // Stream thumbnails in chunks. Chunk size 40 keeps the recompose
            // rate reasonable (~38 state updates for 1500 items at <100ms
            // each) while still feeling progressive.
            val acc = mutableMapOf<String, Bitmap>()
            loaded.chunked(40).forEach { chunk ->
                withContext(Dispatchers.IO) {
                    chunk.forEach { photo ->
                        vault.loadThumbnail(photo)?.let { acc[photo.id] = it }
                    }
                }
                // Bail if the user navigated away mid-stream — avoids a
                // misleading UI update on a stale gallery.
                if (currentCategory != cat || page != VaultPage.GALLERY) return@launch
                thumbnails = acc.toMap()
            }
        }
    }

    fun openViewer(photo: VaultPhoto) {
        when (photo.mediaType) {
            VaultMediaType.VIDEO -> {
                scope.launch {
                    val tempFile = withContext(Dispatchers.IO) { vault.decryptVideoToTempFile(photo) }
                    if (tempFile != null) {
                        videoTempFile = tempFile
                        viewerPhoto = photo
                        page = VaultPage.VIDEO_PLAYER
                    } else {
                        Toast.makeText(context, context.getString(R.string.failed_to_decrypt_video), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            VaultMediaType.PDF -> {
                // Decrypt PDF to private cache and open in the built-in viewer.
                // The decrypted file lives in app-internal cacheDir (no FileProvider
                // grant) and is deleted when the user backs out of the viewer.
                scope.launch {
                    val tempPdf = withContext(Dispatchers.IO) {
                        try {
                            val decrypted = crypto.decryptFile(photo.encryptedFile)
                            File(context.cacheDir, "view_${photo.id}.pdf").also { it.writeBytes(decrypted) }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (tempPdf != null) {
                        // Clean up any previous viewer temp file
                        pdfTempFile?.delete()
                        pdfTempFile = tempPdf
                        pdfTitle = photo.id
                        viewerPhoto = photo
                        page = VaultPage.PDF_VIEWER
                    } else {
                        Toast.makeText(context, context.getString(R.string.failed_to_open_pdf, ""), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else -> {
                scope.launch {
                    val bmp = withContext(Dispatchers.IO) { vault.loadFullPhoto(photo) }
                    viewerPhoto = photo
                    viewerBitmap = bmp
                    page = VaultPage.VIEWER
                }
            }
        }
    }

    fun deletePhotos(ids: Set<String>) {
        // Move to trash instead of permanent delete — keep index entries for fast restore.
        // The work runs on Dispatchers.IO so the UI doesn't freeze on large
        // multi-deletes (73 photos = 73 file renames + an encrypted index
        // write; on the main thread the user sees no feedback for seconds).
        val allKnown = (photos + searchResults).distinctBy { it.id }
        val toDelete = allKnown.filter { it.id in ids }
        if (toDelete.isEmpty()) {
            Toast.makeText(context, "Nothing to delete", Toast.LENGTH_SHORT).show()
            return
        }
        // Optimistic UI update — remove from view immediately so taps feel responsive.
        thumbnails = thumbnails - ids
        photos = photos.filter { it.id !in ids }
        searchResults = searchResults.filter { it.id !in ids }
        searchThumbnails = searchThumbnails - ids
        selectedIds = emptySet()
        isSelectionMode = false

        scope.launch {
            val movedCount = withContext(Dispatchers.IO) {
                vault.moveToTrashBatch(toDelete)
            }
            // Refresh state on main thread.
            categoryCounts = vault.countByCategory()
            rootFolders = folderManager.listRootFolders()
            trashCount = vault.trashCount()

            // Surface honest counts — silently dropping items would mask
            // exactly the bug we just fixed.
            val msg = when {
                movedCount == toDelete.size -> "Moved ${movedCount} to Trash"
                movedCount in 0 until toDelete.size ->
                    "Moved ${movedCount} of ${toDelete.size} to Trash — ${toDelete.size - movedCount} failed"
                movedCount < 0 ->
                    "Moved ${-movedCount} to Trash but index save failed — restart the app to recover"
                else -> "Moved to Trash"
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    fun sharePdf(ids: Set<String>) {
        val toInclude = photos.filter { it.id in ids }
        scope.launch {
            withContext(Dispatchers.IO) {
                val pdf = PdfDocument()
                toInclude.forEachIndexed { i, photo ->
                    val bmp = vault.loadFullPhoto(photo) ?: return@forEachIndexed
                    val scale = minOf(1240f / bmp.width, 1754f / bmp.height, 1f)
                    val w = (bmp.width * scale).toInt()
                    val h = (bmp.height * scale).toInt()
                    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, w, h, true).also { bmp.recycle() } else bmp
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
                    page.canvas.drawBitmap(scaled, 0f, 0f, null)
                    pdf.finishPage(page)
                    scaled.recycle()
                }
                val pdfFile = File(context.cacheDir, "vault_${System.currentTimeMillis()}.pdf")
                FileOutputStream(pdfFile).use { pdf.writeTo(it) }
                pdf.close()
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, context.getString(R.string.share_pdf)
                    ))
                    selectedIds = emptySet()
                    isSelectionMode = false
                }
            }
        }
    }

    fun sharePhoto(photo: VaultPhoto) {
        scope.launch {
            withContext(Dispatchers.IO) {
                var bitmap = vault.loadFullPhoto(photo) ?: return@withContext

                // Face blur if enabled
                if (com.privateai.camera.ui.settings.isFaceBlurEnabled(context)) {
                    bitmap = com.privateai.camera.util.FaceBlur.blurFaces(context, bitmap)
                }

                val uri = com.privateai.camera.util.saveBitmapToCache(context, bitmap, "vault_share.jpg")
                bitmap.recycle()
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, context.getString(R.string.share_photo)
                    ))
                }
            }
        }
    }

    fun shareImages(ids: Set<String>) {
        val toShare = photos.filter { it.id in ids }
        val hasVideos = toShare.any { it.mediaType == VaultMediaType.VIDEO }
        scope.launch {
            withContext(Dispatchers.IO) {
                val uris = ArrayList<android.net.Uri>()
                toShare.forEach { photo ->
                    if (photo.mediaType == VaultMediaType.VIDEO) {
                        val tempFile = vault.decryptVideoToTempFile(photo) ?: return@forEach
                        uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile))
                    } else {
                        val bytes = vault.loadPhotoBytes(photo) ?: return@forEach
                        uris.add(com.privateai.camera.util.saveJpegBytesToCache(context, bytes, "vault_share_${photo.id}.jpg"))
                    }
                }
                val mimeType = if (hasVideos && toShare.any { it.mediaType == VaultMediaType.PHOTO }) "*/*" else if (hasVideos) "video/mp4" else "image/jpeg"
                withContext(Dispatchers.Main) {
                    if (uris.size == 1) {
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = mimeType
                                putExtra(Intent.EXTRA_STREAM, uris[0])
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, context.getString(R.string.share)
                        ))
                    } else {
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = mimeType
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, context.getString(R.string.share_n_items, uris.size)
                        ))
                    }
                    selectedIds = emptySet()
                    isSelectionMode = false
                }
            }
        }
    }

    fun saveToDevice(ids: Set<String>) {
        val toSave = photos.filter { it.id in ids }
        scope.launch {
            var saved = 0
            withContext(Dispatchers.IO) {
                toSave.forEach { photo ->
                    try {
                        if (photo.mediaType == VaultMediaType.VIDEO) {
                            val bytes = crypto.decryptFile(photo.encryptedFile)
                            val filename = "vault_${photo.id}.mp4"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val values = ContentValues().apply {
                                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/PrivateAICamera")
                                }
                                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                                uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
                            }
                        } else {
                            val bytes = vault.loadPhotoBytes(photo) ?: return@forEach
                            val filename = "vault_${photo.id}.jpg"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val values = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PrivateAICamera")
                                }
                                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
                            } else {
                                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                FileOutputStream(File(dir, filename)).use { it.write(bytes) }
                            }
                        }
                        saved++
                    } catch (_: Exception) {}
                }
            }
            Toast.makeText(context, context.getString(R.string.items_saved_to_gallery, saved), Toast.LENGTH_SHORT).show()
            selectedIds = emptySet()
            isSelectionMode = false
        }
    }

    // Details dialog
    var showDetailsDialog by remember { mutableStateOf(false) }

    // Extracted-OCR-text inspector dialog. Lets the user verify whether a
    // wrong answer from the assistant came from bad OCR (the text shows
    // "202024" too) or from Gemma hallucinating digits (text shows "2024").
    extractedTextDialog?.let { ocrText ->
        AlertDialog(
            onDismissRequest = { extractedTextDialog = null },
            title = { Text(stringResource(R.string.assistant_view_extracted_text)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Size header — total chars + how much actually reaches the
                    // model. The doc budget here mirrors what's used in
                    // runAssistantTurn so the user sees the same truth.
                    val totalChars = ocrText.length
                    val docBudget = 5120
                    val sentToAi = totalChars.coerceAtMost(docBudget)
                    val statusLine = if (totalChars > docBudget) {
                        stringResource(R.string.assistant_view_extracted_text_size_truncated, totalChars, sentToAi)
                    } else {
                        stringResource(R.string.assistant_view_extracted_text_size_full, totalChars)
                    }
                    Text(
                        statusLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)
                    ) {
                        item {
                            Text(
                                if (ocrText.isBlank()) stringResource(R.string.assistant_view_extracted_text_empty) else ocrText,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("OCR text", ocrText))
                    Toast.makeText(context, context.getString(R.string.totp_copied), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.totp_copy)) }
            },
            dismissButton = {
                TextButton(onClick = { extractedTextDialog = null }) { Text(stringResource(R.string.close)) }
            }
        )
    }
    // Gallery / folder overflow menu (3-dot top-right). One state var works
    // for both because GALLERY and FOLDER_VIEW are mutually exclusive pages.
    var showOverflowMenu by remember { mutableStateOf(false) }
    var starredOnly by remember { mutableStateOf(false) }
    // sortMode + updatedTimes are declared up top alongside `photos`.
    // The LaunchedEffect + sortPhotos helper depend on them, so they live
    // here in the same scope rather than at the top of the composable.
    LaunchedEffect(sortMode, photoIndex) {
        val pi = photoIndex
        if (pi != null && (sortMode == SortMode.UPDATED_DESC || sortMode == SortMode.UPDATED_ASC)) {
            withContext(Dispatchers.IO) {
                val ids = getAllVaultItems().map { it.id }
                val times = pi.getUpdatedTimes(ids)
                withContext(Dispatchers.Main) { updatedTimes = times }
            }
        }
    }
    // Helper: apply the active sort mode to a photo list. Centralized so
    // every list view in the Vault uses the same ordering.
    fun sortPhotos(list: List<VaultPhoto>): List<VaultPhoto> {
        return when (sortMode) {
            SortMode.CREATED_DESC -> list.sortedByDescending { it.timestamp }
            SortMode.CREATED_ASC -> list.sortedBy { it.timestamp }
            SortMode.UPDATED_DESC -> list.sortedByDescending { updatedTimes[it.id] ?: it.timestamp }
            SortMode.UPDATED_ASC -> list.sortedBy { updatedTimes[it.id] ?: it.timestamp }
        }
    }
    // AI detection / description label visibility in the photo viewer.
    // Default: shown. User can change in Settings → AI Detection → Show AI labels.
    // Re-reads on every fresh entry into VaultScreen, which is enough — users
    // navigate out to Settings to flip it.
    val showAiLabels = com.privateai.camera.ui.settings.isShowAiLabelsEnabled(context)
    // Reset the Starred-only filter whenever the user returns to the
    // categories list — it's a per-folder browsing state, not global.
    LaunchedEffect(page) {
        if (page == VaultPage.CATEGORIES) starredOnly = false
    }
    val detailsItem = viewerPhoto
    if (showDetailsDialog && detailsItem != null) {
        val item = detailsItem
        val encSize = if (item.encryptedFile.exists()) item.encryptedFile.length() else 0L
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(item.timestamp))
        val typeLabel = when (item.mediaType) {
            VaultMediaType.PHOTO -> context.getString(R.string.type_photo_jpeg)
            VaultMediaType.VIDEO -> context.getString(R.string.type_video_mp4)
            VaultMediaType.PDF -> context.getString(R.string.type_pdf_document)
            VaultMediaType.FILE -> item.id.substringAfterLast('.', "File").uppercase()
        }

        // Import progress dialog
        if (isImporting) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.importing_files)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(
                            progress = { if (importTotal > 0) importProgress.toFloat() / importTotal else 0f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Text("$importProgress / $importTotal", style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        if (importErrors > 0) {
                            Text("${importErrors} skipped", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // Lazy-load the metadata sidecar (decrypts a tiny JSON, ~10ms).
        // Only photos have sidecars; videos/PDFs/files always return null.
        val photoMeta = remember(item.id) {
            if (item.mediaType == VaultMediaType.PHOTO) vault.loadMetadata(item) else null
        }

        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text(stringResource(R.string.file_details)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailRow(stringResource(R.string.detail_type), typeLabel)
                    DetailRow(stringResource(R.string.detail_name), item.id)
                    DetailRow(stringResource(R.string.detail_date), dateStr)
                    DetailRow(stringResource(R.string.detail_encrypted_size), com.privateai.camera.service.StorageManager.formatSize(encSize))
                    DetailRow(stringResource(R.string.detail_category), item.category.label)

                    // Original metadata section — only shown when the sidecar
                    // actually has at least one populated field.
                    if (photoMeta != null && (photoMeta.width != null || photoMeta.gpsLat != null)) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text(
                            stringResource(R.string.detail_original_metadata),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (photoMeta.width != null && photoMeta.height != null) {
                            DetailRow(
                                stringResource(R.string.detail_dimensions),
                                "${photoMeta.width} × ${photoMeta.height}"
                            )
                        }
                        if (photoMeta.gpsLat != null && photoMeta.gpsLng != null) {
                            DetailRow(
                                stringResource(R.string.detail_gps),
                                formatGps(photoMeta.gpsLat, photoMeta.gpsLng)
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.security), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    DetailRow(stringResource(R.string.detail_encryption), stringResource(R.string.aes_256_gcm))
                    DetailRow(stringResource(R.string.detail_key_storage), stringResource(R.string.hardware_tee_strongbox))
                    DetailRow(stringResource(R.string.detail_exif_data), stringResource(R.string.stripped_on_share))
                    DetailRow(stringResource(R.string.detail_storage), stringResource(R.string.app_internal_hidden))
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    // Create folder dialog
    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(stringResource(R.string.create_folder)) },
            text = {
                OutlinedTextField(
                    value = folderName, onValueChange = { folderName = it },
                    label = { Text(stringResource(R.string.folder_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderName.isNotBlank()) {
                        folderManager.createFolder(folderName.trim(), currentFolder?.id)
                        rootFolders = folderManager.listRootFolders()
                        currentFolder?.let { subfolders = folderManager.listSubfolders(it.id) }
                    }
                    showCreateFolderDialog = false
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Rename folder dialog
    val renameFolder = currentFolder
    if (showRenameFolderDialog && renameFolder != null) {
        var newName by remember { mutableStateOf(renameFolder.name) }
        AlertDialog(
            onDismissRequest = { showRenameFolderDialog = false },
            title = { Text(stringResource(R.string.rename_folder)) },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.new_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        folderManager.renameFolder(renameFolder.id, newName.trim())
                        currentFolder = renameFolder.copy(name = newName.trim())
                        rootFolders = folderManager.listRootFolders()
                    }
                    showRenameFolderDialog = false
                }) { Text(stringResource(R.string.rename)) }
            },
            dismissButton = { TextButton(onClick = { showRenameFolderDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Delete folder dialog
    val deleteFolder = currentFolder
    if (showDeleteFolderDialog && deleteFolder != null) {
        AlertDialog(
            onDismissRequest = { showDeleteFolderDialog = false },
            title = { Text(stringResource(R.string.delete_folder_title)) },
            text = { Text(stringResource(R.string.delete_folder_message, deleteFolder.name)) },
            confirmButton = {
                TextButton(onClick = {
                    folderManager.deleteFolder(deleteFolder.id)
                    rootFolders = folderManager.listRootFolders()
                    showDeleteFolderDialog = false
                    currentFolder = null
                    page = VaultPage.CATEGORIES
                }) { Text(stringResource(R.string.delete), color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showDeleteFolderDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Move to folder dialog
    if (showMoveDialog) {
        val allFolders = remember { folderManager.listAllFolders() }
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text(stringResource(R.string.move_to)) },
            text = {
                Column(
                    Modifier.height(300.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // System categories
                    Text(stringResource(R.string.system_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    VaultCategory.entries.filter { it != VaultCategory.FILES }.forEach { cat ->
                        TextButton(onClick = {
                            val toMove = photos.filter { it.id in selectedIds }
                            toMove.forEach { photo ->
                                val targetDir = File(context.filesDir, "vault/${cat.dirName}")
                                vault.moveToFolder(photo, targetDir)
                            }
                            photos = photos.filter { it.id !in selectedIds }
                            selectedIds = emptySet(); isSelectionMode = false
                            if (!isDuressActive) { categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders(); trashCount = vault.trashCount() }
                            showMoveDialog = false
                            Toast.makeText(context, context.getString(R.string.moved_n_items, toMove.size), Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(cat.label, modifier = Modifier.fillMaxWidth())
                        }
                    }

                    if (allFolders.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.my_folders), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        allFolders.forEach { folder ->
                            val path = folderManager.getFolderPath(folder.id).joinToString(" / ") { it.name }
                            TextButton(onClick = {
                                val toMove = photos.filter { it.id in selectedIds }
                                val targetDir = folderManager.getFolderDir(folder.id)
                                toMove.forEach { photo -> vault.moveToFolder(photo, targetDir) }
                                photos = photos.filter { it.id !in selectedIds }
                                selectedIds = emptySet(); isSelectionMode = false
                                if (!isDuressActive) { categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders(); trashCount = vault.trashCount() }
                                showMoveDialog = false
                                Toast.makeText(context, context.getString(R.string.moved_n_items_to_folder, toMove.size, folder.name), Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(path, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    // Move to Hidden folder
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = {
                        val hiddenDir = java.io.File(context.filesDir, "vault/hidden").also { it.mkdirs() }
                        val toMove = photos.filter { it.id in selectedIds }
                        toMove.forEach { photo -> vault.moveToFolder(photo, hiddenDir) }
                        photos = photos.filter { it.id !in selectedIds }
                        selectedIds = emptySet(); isSelectionMode = false
                        if (!isDuressActive) { categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders(); trashCount = vault.trashCount() }
                        showMoveDialog = false
                        Toast.makeText(context, "Moved ${toMove.size} item(s) to Hidden", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Text("Hidden folder", color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMoveDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Delete dialog
    if (showDeleteDialog) {
        val count = if (isSelectionMode) selectedIds.size else 1
        val isVideo = viewerPhoto?.mediaType == VaultMediaType.VIDEO
        val itemLabel = when {
            isSelectionMode && count > 1 -> stringResource(R.string.n_items, count)
            isVideo -> stringResource(R.string.video)
            else -> stringResource(R.string.photo)
        }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_item, itemLabel)) },
            text = { Text(stringResource(R.string.permanently_deleted_from_vault)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    if (isSelectionMode) deletePhotos(selectedIds)
                    else viewerPhoto?.let { current ->
                        // Find the next item BEFORE deletion mutates `photos`.
                        // Prefer the photo that currently sits after this one;
                        // if this was the last item, fall back to the one
                        // before. Only navigate back to the gallery when the
                        // viewable list is empty after delete.
                        val navigable = photos.filter { it.mediaType != VaultMediaType.PDF }
                        val idx = navigable.indexOfFirst { it.id == current.id }
                        val nextItem = when {
                            idx < 0 -> null
                            idx + 1 < navigable.size -> navigable[idx + 1]
                            idx > 0 -> navigable[idx - 1]
                            else -> null
                        }

                        deletePhotos(setOf(current.id))
                        videoTempFile?.delete()
                        videoTempFile = null
                        // PDF viewer also owns a decrypted temp file —
                        // clean it up so we don't leak plaintext on disk.
                        pdfTempFile?.delete()
                        pdfTempFile = null
                        pdfTitle = ""

                        if (nextItem != null) {
                            // Stays on VaultPage.VIEWER (or switches to video
                            // player if the next item is a video) — re-uses
                            // the same code path the user takes when they
                            // tap a thumbnail in the gallery.
                            openViewer(nextItem)
                        } else {
                            page = VaultPage.GALLERY
                        }
                    }
                }) { Text(stringResource(R.string.delete), color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Merge face groups confirmation dialog
    mergeTargetGroup?.let { targetId ->
        val sourceId = mergeSourceGroup ?: return@let
        val sourceName = photoIndex?.getFaceGroupName(sourceId) ?: "Unknown"
        val targetName = photoIndex?.getFaceGroupName(targetId) ?: "Unknown"
        AlertDialog(
            onDismissRequest = { mergeTargetGroup = null; mergeSourceGroup = null },
            title = { Text("Merge Face Groups") },
            text = { Text("Merge \"$sourceName\" into \"$targetName\"? This combines both groups and helps the AI recognize this person better.") },
            confirmButton = {
                TextButton(onClick = {
                    photoIndex?.mergeFaceGroups(targetId, sourceId)
                    mergeTargetGroup = null
                    mergeSourceGroup = null
                    // Refresh face groups
                    val pi = photoIndex
                    if (pi != null) {
                        scope.launch {
                            val groups = withContext(Dispatchers.IO) { pi.getFaceGroups(faceThreshold) }
                            faceGroups = groups
                        }
                    }
                }) { Text("Merge") }
            },
            dismissButton = {
                TextButton(onClick = { mergeTargetGroup = null; mergeSourceGroup = null }) { Text("Cancel") }
            }
        )
    }

    // Rename face group dialog
    renamingGroup?.let { groupId ->
        var newName by remember { mutableStateOf(photoIndex?.getFaceGroupName(groupId) ?: "") }
        AlertDialog(
            onDismissRequest = { renamingGroup = null },
            title = { Text("Name this person") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        photoIndex?.setFaceGroupName(groupId, newName.trim())
                    }
                    renamingGroup = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingGroup = null }) { Text("Cancel") }
            }
        )
    }

    // PIN input state for lock screen
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var isLockedOut by remember { mutableStateOf(PinRateLimiter.remainingLockoutMs(context) > 0) }
    var lockoutRemainingMs by remember { mutableLongStateOf(PinRateLimiter.remainingLockoutMs(context)) }

    // Countdown timer for lockout
    LaunchedEffect(isLockedOut) {
        if (isLockedOut) {
            while (true) {
                val remaining = PinRateLimiter.remainingLockoutMs(context)
                if (remaining <= 0) { isLockedOut = false; lockoutRemainingMs = 0L; break }
                lockoutRemainingMs = remaining
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    fun checkPin(enteredPin: String) {
        // Duress check always runs first (even during lockout)
        if (DuressManager.isEnabled(context) && DuressManager.isDuressPin(context, enteredPin)) {
            isDuressActive = true
            VaultLockManager.activateDuress()
            VaultLockManager.markUnlocked()
            categoryCounts = VaultCategory.entries.associateWith { 0 }
            photos = emptyList()
            thumbnails = emptyMap()
            trashCount = 0
            rootFolders = emptyList()
            faceGroups = emptyMap()
            faceGroupCount = 0
            showFaceGroups = false
            selectedFaceGroup = null
            duplicateGroups = emptyList()
            duplicateGroupCount = 0
            blurryCount = 0
            searchResults = emptyList()
            isSearching = false
            smartMode = null
            page = VaultPage.CATEGORIES
            pinInput = ""
            pinError = null
            scope.launch(Dispatchers.IO) { DuressManager.executeDuress(context, crypto) }
            return
        }

        // Rate limit check
        if (!PinRateLimiter.canAttempt(context)) {
            pinInput = ""
            isLockedOut = true
            lockoutRemainingMs = PinRateLimiter.remainingLockoutMs(context)
            return
        }

        // Check app PIN
        if (com.privateai.camera.security.AppPinManager.verify(context, enteredPin)) {
            PinRateLimiter.recordSuccess(context)
            if (crypto.initialize()) {
                isDuressActive = false
                VaultLockManager.clearDuress()
                VaultLockManager.markUnlocked()
                categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders()
                page = VaultPage.CATEGORIES
                pinInput = ""
                pinError = null
            }
            return
        }

        // Wrong PIN
        PinRateLimiter.recordFailure(context)
        val remaining = PinRateLimiter.remainingLockoutMs(context)
        if (remaining > 0) {
            isLockedOut = true
            lockoutRemainingMs = remaining
            pinError = null
        } else {
            pinError = context.getString(R.string.incorrect_pin)
        }
        pinInput = ""
    }

    val currentAuthMode = remember { getAuthMode(context) }

    // Handle back gesture — return to vault categories instead of leaving vault.
    // CRITICAL: VIEWER check runs FIRST so a Back from the photo viewer
    // doesn't get swallowed by selectedFaceGroup/isSearching/smartMode flags
    // (which are still set while the viewer overlays the search results UI).
    // Without this, Back from a face-group viewer first clears the face
    // group flag (silent — page still VIEWER), then second Back goes to an
    // empty Gallery, then third Back to Categories — user perceived this as
    // "Back jumped to home" instead of returning to the search results.
    BackHandler(
        enabled = showFaceGroups || selectedFaceGroup != null || isSearching || smartMode != null ||
                page == VaultPage.GALLERY || page == VaultPage.VIEWER || page == VaultPage.FOLDER_VIEW || page == VaultPage.TRASH
    ) {
        when {
            page == VaultPage.VIEWER -> {
                when {
                    viewerFromDeepLink -> { viewerFromDeepLink = false; onBack?.invoke() }
                    viewerFromHidden -> { page = VaultPage.CATEGORIES; viewerFromHidden = false }
                    viewerFromFolder -> { page = VaultPage.FOLDER_VIEW; viewerFromFolder = false }
                    // From a face-group / search / smart-filter result list:
                    // return to the search-results UI (rendered over the
                    // CATEGORIES page; the selectedFaceGroup / isSearching /
                    // smartMode state flags stay set so the right view paints).
                    viewerFromSearch -> { page = VaultPage.CATEGORIES; viewerFromSearch = false }
                    else -> page = VaultPage.GALLERY
                }
            }
            selectedFaceGroup != null -> { selectedFaceGroup = null; searchResults = emptyList() }
            showFaceGroups -> { showFaceGroups = false; isSmartLoading = false }
            isSearching || smartMode != null -> { isSearching = false; smartMode = null; isSmartLoading = false; searchResults = emptyList() }
            page == VaultPage.GALLERY -> page = VaultPage.CATEGORIES
            page == VaultPage.FOLDER_VIEW -> page = VaultPage.CATEGORIES
            page == VaultPage.TRASH -> page = VaultPage.CATEGORIES
        }
    }

    // Auto-authenticate if phone lock mode
    LaunchedEffect(Unit) {
        if (page == VaultPage.LOCKED) {
            if (currentAuthMode == AuthMode.PHONE_LOCK) {
                authenticate() // biometric/device credential only
            }
        } else if (!isDuressActive) {
            categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders()
        }
    }

    // Auto-purge old trash items
    LaunchedEffect(Unit) {
        if (!isDuressActive) {
            withContext(Dispatchers.IO) { vault.autoPurgeTrash(); trashCount = vault.trashCount() }
        }
    }

    // Initialize Photo Intelligence index + classifier, then auto-index in background
    LaunchedEffect(Unit) {
        if (!isDuressActive) {
            withContext(Dispatchers.IO) {
                try {
                    val c = ImageClassifier(context)
                    classifier = c
                    val det = try { com.privateai.camera.bridge.OnnxDetector(context) } catch (_: Exception) { null }
                    objectDetector = det
                    val pi = PhotoIndex(PrivoraDatabase.getInstance(context, crypto))
                    photoIndex = pi
                } catch (e: Exception) {
                    Log.e("VaultScreen", "Failed to init photo index: ${e.message}")
                }
            }
            // Start persistent background indexing (survives navigation)
            com.privateai.camera.service.IndexingManager.startIndexing(context)
            // Load filter counts in background
            val pi = photoIndex
            if (pi != null) {
                scope.launch {
                    val validIds = withContext(Dispatchers.IO) { getAllVaultItems().map { it.id }.toSet() }
                    blurryCount = withContext(Dispatchers.IO) { pi.findBlurry(validPhotoIds = validIds).size }
                    faceGroupCount = withContext(Dispatchers.IO) { pi.getFaceGroups(faceThreshold).size }
                    duplicateGroupCount = withContext(Dispatchers.Default) { pi.findDuplicates(validPhotoIds = validIds).size }
                }
            }
        }
    }

    // Re-sort live lists when the user changes sort mode. UPDATED_* needs
    // a DB hit for the per-photo edit timestamps; CREATED_* doesn't.
    LaunchedEffect(sortMode) {
        val pi = photoIndex
        val needsUpdateTimes = sortMode == SortMode.UPDATED_DESC || sortMode == SortMode.UPDATED_ASC
        val times: Map<String, Long> = if (needsUpdateTimes && pi != null) {
            val ids = (photos + searchResults).map { it.id }.toSet()
            withContext(Dispatchers.IO) { pi.getUpdatedTimes(ids) }
        } else emptyMap()
        updatedTimes = times
        photos = sortPhotos(photos, sortMode, times)
        searchResults = sortPhotos(searchResults, sortMode, times)
    }

    // Observe IndexingManager progress
    val indexingRunning by com.privateai.camera.service.IndexingManager.isRunning.collectAsState()
    val indexingProgress by com.privateai.camera.service.IndexingManager.progress.collectAsState()
    // Sync to local state for UI
    LaunchedEffect(indexingRunning, indexingProgress) {
        isIndexing = indexingRunning
        indexProgress = indexingProgress
    }

    // Auto-open the photo viewer when navigated in with a target photo id
    // (e.g., tap on an Assistant search_photos thumbnail). Fires once
    // post-unlock, finds the photo across categories + folders, and routes
    // it through openViewer so videos / PDFs / images all behave correctly.
    LaunchedEffect(initialOpenPhotoId, page) {
        val targetId = initialOpenPhotoId ?: return@LaunchedEffect
        if (page == VaultPage.LOCKED) return@LaunchedEffect
        if (viewerPhoto?.id == targetId) return@LaunchedEffect
        if (!crypto.isUnlocked()) {
            try { crypto.initialize() } catch (_: Exception) { return@LaunchedEffect }
        }
        val photo = withContext(Dispatchers.IO) {
            getAllVaultItems().firstOrNull { it.id == targetId }
        }
        if (photo != null) {
            // Flag the viewer as deep-linked BEFORE openViewer so the back
            // handler below can short-circuit to onBack instead of popping
            // to a Gallery the user never visited.
            viewerFromDeepLink = true
            openViewer(photo)
        }
    }

    // Auto-search when opened with a search query (e.g., from People → Photos)
    LaunchedEffect(initialSearchQuery, photoIndex, page) {
        if (initialSearchQuery.isNotBlank() && photoIndex != null && page == VaultPage.CATEGORIES && !isSearching) {
            val pi = photoIndex ?: return@LaunchedEffect
            isSearching = true
            scope.launch {
                val allItems = withContext(Dispatchers.IO) { getAllVaultItems() }
                var matchedIds = emptySet<String>()

                withContext(Dispatchers.IO) {
                    try {
                        val parts = initialSearchQuery.split(" ")
                        val contactId = parts.lastOrNull()?.takeIf { it.length > 10 }
                        if (contactId != null) {
                            // 1. FIRST: Check linked face group — most accurate
                            val groups = pi.getFaceGroups(faceThreshold)
                            val identities = pi.loadFaceIdentitiesList()
                            val linkedIdentity = identities.find { it.personId == contactId }
                            if (linkedIdentity != null && groups.containsKey(linkedIdentity.id)) {
                                matchedIds = groups[linkedIdentity.id]!!.map { it.first }.distinct().toSet()
                            }

                            // 2. If no linked group, try profile photo face matching
                            if (matchedIds.isEmpty()) {
                                val contactRepo = com.privateai.camera.security.ContactRepository(java.io.File(context.filesDir, "vault/contacts"), crypto, com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto))
                                val profileBmp = contactRepo.loadProfilePhoto(contactId)
                                if (profileBmp != null) {
                                    val fe = com.privateai.camera.bridge.FaceEmbedder(context)
                                    val embeddings = fe.detectAndEmbed(profileBmp)
                                    profileBmp.recycle()
                                    if (embeddings.isNotEmpty()) {
                                        matchedIds = pi.findPhotosByFaceEmbedding(embeddings[0].second, faceThreshold).toSet()
                                    }
                                    fe.release()
                                }
                            }
                        }

                        // 3. Fallback: label search
                        if (matchedIds.isEmpty()) {
                            matchedIds = pi.searchByLabel(initialSearchQuery).toSet()
                        }
                    } catch (_: Exception) {}
                }

                searchResults = allItems.filter { it.id in matchedIds }
                val thumbMap = mutableMapOf<String, Bitmap>()
                withContext(Dispatchers.IO) {
                    searchResults.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } }
                }
                searchThumbnails = thumbMap
            }
        }
    }

    when (page) {
        VaultPage.LOCKED -> {
            Scaffold(topBar = {
                TopAppBar(title = { Text(stringResource(R.string.encrypted_vault)) }, navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                })
            }) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.vault_is_locked), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))

                    Spacer(Modifier.height(24.dp))

                    if (currentAuthMode == AuthMode.APP_PIN) {
                        if (isLockedOut) {
                            val seconds = (lockoutRemainingMs / 1000).toInt()
                            Text(
                                stringResource(R.string.pin_locked_out, "%d:%02d".format(seconds / 60, seconds % 60)),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = {
                                    if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                                        pinInput = it
                                        pinError = null
                                    }
                                },
                                label = { Text(stringResource(R.string.enter_pin)) },
                                modifier = Modifier.width(200.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { if (pinInput.length >= 4) checkPin(pinInput) }),
                                visualTransformation = PasswordVisualTransformation(),
                                isError = pinError != null,
                                supportingText = {
                                    pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                }
                            )

                            Button(
                                onClick = { if (pinInput.length >= 4) checkPin(pinInput) },
                                enabled = pinInput.length >= 4,
                                modifier = Modifier.width(200.dp)
                            ) { Text(stringResource(R.string.unlock)) }
                        }
                    } else {
                        Button(
                            onClick = { authenticate() },
                            modifier = Modifier.width(200.dp)
                        ) { Text(stringResource(R.string.unlock)) }
                    }
                }
            }
        }

        VaultPage.CATEGORIES -> {
            Scaffold(topBar = {
                TopAppBar(title = {
                    Text(
                        stringResource(R.string.encrypted_vault),
                        modifier = Modifier.clickable {
                            if (isDuressActive) return@clickable // no hidden folder during duress
                            val now = System.currentTimeMillis()
                            if (now - hiddenLastTapTime > 1000L) hiddenTapCount = 0
                            hiddenLastTapTime = now
                            hiddenTapCount++
                            val remaining = hiddenTapThreshold - hiddenTapCount
                            if (remaining in 1..3) {
                                android.widget.Toast.makeText(context, "$remaining taps to go", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            if (hiddenTapCount >= hiddenTapThreshold) {
                                isHiddenFolderActive = !isHiddenFolderActive
                                hiddenTapCount = 0
                                android.widget.Toast.makeText(
                                    context,
                                    if (isHiddenFolderActive) "Hidden folder revealed" else "Hidden folder closed",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }, navigationIcon = {
                    if (onBack != null) IconButton(onClick = {
                        if (showFaceGroups) {
                            if (selectedFaceGroup != null) {
                                selectedFaceGroup = null
                            } else {
                                showFaceGroups = false
                            }
                        } else if (isSearching || smartMode != null) {
                            if (searchFromViewer) {
                                // Return to the photo viewer
                                searchFromViewer = false
                                page = VaultPage.VIEWER
                            }
                            searchQuery = ""; isSearching = false; smartMode = null
                            searchResults = emptyList(); searchThumbnails = emptyMap()
                            searchSuggestions = emptyList()
                        } else {
                            onBack()
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                }, actions = {
                    if (!isDuressActive) {
                        IconButton(onClick = { page = VaultPage.WIFI_TRANSFER }) {
                            Icon(Icons.Default.Wifi, contentDescription = stringResource(R.string.wifi_transfer_title), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                })
            }) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { query ->
                            searchQuery = query
                            smartMode = null
                            searchFromViewer = false
                            if (isDuressActive) { isSearching = false; searchResults = emptyList(); return@OutlinedTextField }
                            // Auto-suggest from known labels + aliases
                            if (query.isNotEmpty()) {
                                val q = query.lowercase()
                                val aliasKeys = photoIndex?.let {
                                    it.getAllLabels().filter { l -> l.lowercase().startsWith(q) }
                                } ?: emptyList()
                                val aliasMatches = listOf("person", "people", "selfie", "face", "food", "animal", "car", "flower", "building", "beach", "nature", "sky", "tree", "baby", "sport", "phone", "laptop", "book", "cat", "dog")
                                    .filter { it.startsWith(q) && it != q }
                                searchSuggestions = (aliasMatches + aliasKeys).distinct().take(5)
                            } else {
                                searchSuggestions = emptyList()
                            }
                            if (query.length >= 2) {
                                isSearching = true
                                scope.launch {
                                    val allItems = withContext(Dispatchers.IO) { getAllVaultItems() }
                                    val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                    val plainResults = allItems.filter { item ->
                                        item.id.contains(query, ignoreCase = true) ||
                                        item.mediaType.name.contains(query, ignoreCase = true) ||
                                        item.category.label.contains(query, ignoreCase = true) ||
                                        dateFmt.format(java.util.Date(item.timestamp)).contains(query)
                                    }
                                    // Person-aware index search: recognizes a face-group / contact
                                    // name embedded in the query and intersects with the rest.
                                    val personResult = withContext(Dispatchers.IO) {
                                        photoIndex?.searchByPersonAndTags(contactRepoLazy.value, query, limit = 500)
                                    }
                                    detectedPerson = personResult?.detectedPerson
                                    detectedResidual = personResult?.residualQuery ?: query
                                    val indexMatches = personResult?.photoIds?.toSet() ?: emptySet()
                                    val indexPhotos = if (indexMatches.isNotEmpty()) allItems.filter { it.id in indexMatches } else emptyList()
                                    // When a person is detected, prefer the indexed result alone
                                    // (the plain text scan would mix in unrelated items containing
                                    // the person's name in their id or category label).
                                    searchResults = if (personResult?.detectedPerson != null) {
                                        indexPhotos
                                    } else {
                                        (plainResults + indexPhotos).distinctBy { it.id }
                                    }
                                    // Load thumbnails for search results
                                    val thumbMap = searchThumbnails.toMutableMap()
                                    withContext(Dispatchers.IO) {
                                        searchResults.forEach { photo ->
                                            if (photo.id !in thumbMap) {
                                                vault.loadThumbnail(photo)?.let { thumbMap[photo.id] = it }
                                            }
                                        }
                                    }
                                    searchThumbnails = thumbMap
                                }
                            } else {
                                isSearching = false
                                searchResults = emptyList()
                                detectedPerson = null
                                detectedResidual = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.search_vault)) },
                        leadingIcon = { Icon(Icons.Default.Search, stringResource(R.string.search)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    isSearching = false
                                    searchResults = emptyList()
                                    smartMode = null
                                    detectedPerson = null
                                    detectedResidual = ""
                                }) {
                                    Icon(Icons.Default.Close, stringResource(R.string.clear))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )

                    // Detected-person chip: shows when the search text contained
                    // a face-group / contact name. Tapping × strips the person
                    // token from the query, keeping any remaining label words.
                    if (detectedPerson != null) {
                        val personName = detectedPerson!!
                        androidx.compose.material3.InputChip(
                            selected = true,
                            onClick = { },
                            label = { Text(stringResource(R.string.search_person_chip, personName)) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val newQuery = detectedResidual.trim()
                                        searchQuery = newQuery
                                        if (newQuery.length < 2) {
                                            isSearching = false
                                            searchResults = emptyList()
                                        }
                                        detectedPerson = null
                                        detectedResidual = ""
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Close, stringResource(R.string.clear), modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }

                    // Auto-suggest
                    if (searchSuggestions.isNotEmpty() && searchQuery.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            searchSuggestions.forEach { suggestion ->
                                AssistChip(
                                    onClick = {
                                        searchQuery = suggestion
                                        searchSuggestions = emptyList()
                                        // Trigger search
                                        isSearching = true
                                        scope.launch {
                                            val allItems = withContext(Dispatchers.IO) { getAllVaultItems() }
                                            val labelMatches = photoIndex?.searchByLabel(suggestion)?.toSet() ?: emptySet()
                                            searchResults = allItems.filter { it.id in labelMatches }
                                            val thumbMap = mutableMapOf<String, Bitmap>()
                                            withContext(Dispatchers.IO) {
                                                searchResults.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } }
                                            }
                                            searchThumbnails = thumbMap
                                        }
                                    },
                                    label = { Text(suggestion, style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }
                    }

                    // Smart search chips (hidden during duress)
                    if (!isDuressActive) Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val pi = photoIndex
                        if (pi != null) {
                            val indexedCount = pi.getIndexedCount()
                            val totalPhotos = getAllVaultItems().count { it.mediaType == VaultMediaType.PHOTO }

                            if (indexedCount < totalPhotos && !isIndexing) {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        isIndexing = true
                                        scope.launch(Dispatchers.IO) {
                                            val allPhotos = getAllVaultItems().filter { it.mediaType == VaultMediaType.PHOTO }
                                            val cl = classifier ?: return@launch
                                            val fe = try { FaceEmbedder(context) } catch (_: Exception) { null }
                                            allPhotos.forEachIndexed { i, photo ->
                                                if (!pi.isIndexed(photo.id)) {
                                                    val bmp = vault.loadFullPhoto(photo) ?: vault.loadThumbnail(photo)
                                                    bmp?.let { img ->
                                                        try { pi.indexPhoto(photo.id, img, cl, faceEmbedder = fe, detector = objectDetector) } catch (_: Exception) {}
                                                        if (!img.isRecycled) img.recycle()
                                                    }
                                                }
                                                indexProgress = i + 1 to allPhotos.size
                                            }
                                            fe?.release()
                                            isIndexing = false
                                        }
                                    },
                                    label = { Text("Index Photos ($indexedCount/$totalPhotos)") }
                                )
                            }

                            if (isIndexing) {
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text("Indexing... ${indexProgress.first}/${indexProgress.second}") }
                                )
                            }
                            if (isImporting) {
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text("Importing... $importProgress/$importTotal${if (importErrors > 0) " ($importErrors errors)" else ""}") }
                                )
                            }

                            if (indexedCount > 0) {
                                FilterChip(
                                    selected = smartMode == "duplicates",
                                    onClick = {
                                        showFaceGroups = false; selectedFaceGroup = null
                                        if (smartMode == "duplicates") {
                                            smartMode = null
                                            isSearching = false
                                            isSmartLoading = false
                                        } else {
                                            searchResults = emptyList()
                                            smartMode = "duplicates"
                                            isSearching = true
                                            isSmartLoading = true
                                            scope.launch {
                                                val validIds = withContext(Dispatchers.IO) { getAllVaultItems().map { it.id }.toSet() }
                                                val groups = withContext(Dispatchers.Default) { pi.findDuplicates(validPhotoIds = validIds) }
                                                duplicateGroups = groups
                                                val dupIds = groups.flatten().toSet()
                                                val allPhotos = withContext(Dispatchers.IO) { getAllVaultItems() }
                                                searchResults = allPhotos.filter { it.id in dupIds }
                                                isSmartLoading = false
                                                val thumbMap = mutableMapOf<String, Bitmap>()
                                                withContext(Dispatchers.IO) {
                                                    searchResults.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } }
                                                }
                                                searchThumbnails = thumbMap
                                            }
                                        }
                                    },
                                    label = { Text(if (duplicateGroupCount > 0) "Duplicates ($duplicateGroupCount)" else "Duplicates") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) }
                                )

                                FilterChip(
                                    selected = smartMode == "blurry",
                                    onClick = {
                                        showFaceGroups = false; selectedFaceGroup = null
                                        if (smartMode == "blurry") {
                                            smartMode = null
                                            isSearching = false
                                            isSmartLoading = false
                                        } else {
                                            searchResults = emptyList()
                                            smartMode = "blurry"
                                            isSearching = true
                                            isSmartLoading = true
                                            scope.launch {
                                                val blurryIds = withContext(Dispatchers.IO) { pi.findBlurry().toSet() }
                                                val allPhotos = withContext(Dispatchers.IO) { getAllVaultItems() }
                                                searchResults = allPhotos.filter { it.id in blurryIds }
                                                isSmartLoading = false
                                                val thumbMap = mutableMapOf<String, Bitmap>()
                                                withContext(Dispatchers.IO) {
                                                    searchResults.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } }
                                                }
                                                searchThumbnails = thumbMap
                                            }
                                        }
                                    },
                                    label = { Text(if (blurryCount > 0) "Blurry ($blurryCount)" else "Blurry") },
                                    leadingIcon = { Icon(Icons.Default.BlurOn, null, Modifier.size(16.dp)) }
                                )

                                FilterChip(
                                    selected = showFaceGroups,
                                    onClick = {
                                        if (showFaceGroups) {
                                            showFaceGroups = false
                                            selectedFaceGroup = null
                                            isSmartLoading = false
                                        } else {
                                            val pi = photoIndex ?: return@FilterChip
                                            faceGroups = emptyMap()
                                            showFaceGroups = true
                                            isSearching = false
                                            smartMode = null
                                            isSmartLoading = true
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val groups = pi.getFaceGroups(faceThreshold)
                                                    try {
                                                        val contactRepo = com.privateai.camera.security.ContactRepository(
                                                            java.io.File(context.filesDir, "vault/contacts"), crypto, com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)
                                                        )
                                                        val fe = com.privateai.camera.bridge.FaceEmbedder(context)
                                                        pi.autoNameFromContacts(contactRepo, fe)
                                                        fe.release()
                                                    } catch (_: Exception) {}
                                                    faceGroups = groups
                                                }
                                                isSmartLoading = false
                                            }
                                        }
                                    },
                                    label = { Text(if (faceGroupCount > 0) "\uD83D\uDC64 Faces ($faceGroupCount)" else "\uD83D\uDC64 Faces") }
                                )
                            }
                        }
                    }

                    if (showFaceGroups && selectedFaceGroup == null) {
                        // Load thumbnails for face group avatars
                        var faceGroupThumbs by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
                        LaunchedEffect(faceGroups) {
                            val thumbs = mutableMapOf<String, Bitmap>()
                            withContext(Dispatchers.IO) {
                                val allItems = getAllVaultItems().associateBy { it.id }
                                faceGroups.forEach { (groupId, members) ->
                                    // Try each member until we find a loadable thumbnail
                                    for (member in members) {
                                        val photo = allItems[member.first] ?: continue
                                        val thumb = vault.loadThumbnail(photo)
                                        if (thumb != null) { thumbs[groupId] = thumb; break }
                                    }
                                }
                            }
                            faceGroupThumbs = thumbs
                        }
                        // Show face groups grid
                        if (faceGroups.isEmpty()) {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                if (isSmartLoading) {
                                    CircularProgressIndicator(Modifier.size(32.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text(stringResource(R.string.analyzing_photos), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("No face groups found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Index more photos to detect faces", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            if (mergeSourceGroup != null) {
                                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Tap another group to merge", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    TextButton(onClick = { mergeSourceGroup = null }) { Text("Cancel") }
                                }
                            } else {
                                Text("${faceGroups.size} groups  •  Long press to merge", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                faceGroups.forEach { (groupId, members) ->
                                    item {
                                        val groupName = photoIndex?.getFaceGroupName(groupId)
                                        val photoCount = members.map { it.first }.distinct().size
                                        val firstThumb = faceGroupThumbs[groupId]

                                        Card(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .then(if (mergeSourceGroup == groupId) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                                                .combinedClickable(
                                                    onClick = {
                                                        if (mergeSourceGroup != null && mergeSourceGroup != groupId) {
                                                            // Second group tapped — confirm merge
                                                            mergeTargetGroup = groupId
                                                        } else if (mergeSourceGroup == groupId) {
                                                            // Cancel merge mode
                                                            mergeSourceGroup = null
                                                        } else {
                                                            // Normal: show photos
                                                            selectedFaceGroup = groupId
                                                            val photoIds = members.map { it.first }.distinct().toSet()
                                                            val allPhotos = getAllVaultItems()
                                                            searchResults = allPhotos.filter { it.id in photoIds }
                                                            scope.launch {
                                                                val thumbMap = mutableMapOf<String, Bitmap>()
                                                                withContext(Dispatchers.IO) {
                                                                    searchResults.forEach { p ->
                                                                        vault.loadThumbnail(p)?.let { thumbMap[p.id] = it }
                                                                    }
                                                                }
                                                                searchThumbnails = thumbMap
                                                            }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (mergeSourceGroup == null) {
                                                            mergeSourceGroup = groupId
                                                        } else {
                                                            renamingGroup = groupId
                                                        }
                                                    }
                                                )
                                        ) {
                                            Column(
                                                Modifier.fillMaxSize().padding(8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                // Face avatar circle
                                                if (firstThumb != null) {
                                                    Image(
                                                        firstThumb.asImageBitmap(),
                                                        "Face group",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.size(88.dp).clip(CircleShape)
                                                    )
                                                } else {
                                                    Icon(Icons.Default.Face, "Face group", Modifier.size(88.dp), tint = MaterialTheme.colorScheme.primary)
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    groupName ?: "Unknown",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    "$photoCount photos",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (showFaceGroups && selectedFaceGroup != null) {
                        // Show photos for selected face group (sorted newest first)
                        val groupName = photoIndex?.getFaceGroupName(selectedFaceGroup!!) ?: "Unknown Person"
                        val sortedResults = remember(searchResults, sortMode, updatedTimes) { sortPhotos(searchResults) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(groupName, style = MaterialTheme.typography.titleSmall)
                                Text("${sortedResults.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSelectionMode && selectedIds.isNotEmpty()) {
                                    Text("${selectedIds.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 4.dp))
                                    // Remove from group (doesn't delete the photo)
                                    IconButton(onClick = {
                                        selectedFaceGroup?.let { gId ->
                                            photoIndex?.removeFromFaceGroup(gId, selectedIds)
                                            searchResults = searchResults.filter { it.id !in selectedIds }
                                            // Refresh faceGroups so re-entering shows updated data
                                            val pi = photoIndex
                                            if (pi != null) {
                                                scope.launch {
                                                    faceGroups = withContext(Dispatchers.IO) { pi.getFaceGroups(faceThreshold) }
                                                }
                                            }
                                        }
                                        selectedIds = emptySet(); isSelectionMode = false
                                    }) {
                                        Icon(Icons.Default.Close, "Remove from group", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    // Delete photo permanently
                                    IconButton(onClick = { showDeleteDialog = true }) {
                                        Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                                    }
                                    IconButton(onClick = { selectedIds = emptySet(); isSelectionMode = false }) {
                                        Icon(Icons.Default.Close, stringResource(R.string.action_cancel))
                                    }
                                }
                                IconButton(onClick = { selectedFaceGroup = null; selectedIds = emptySet(); isSelectionMode = false }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to groups")
                                }
                            }
                        }
                        // Google Photos-style staggered grid, now grouped
                        // by date — same Today / Yesterday / weekday / month
                        // headers the main vault gallery uses (see
                        // [groupPhotosByDate]). Headers are LazyColumn
                        // items so they recycle alongside the photo rows.
                        val faceGridWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 24.dp
                        val maxItemHeight = 160f
                        val faceGrouped = remember(sortedResults) { groupPhotosByDate(sortedResults) }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            faceGrouped.forEach { (header, groupPhotos) ->
                                item(key = "face_header_$header") {
                                    Text(
                                        text = header,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                                // Build rows: pack each date group's photos into rows of 3
                                // using real aspect ratios so portraits don't get stretched.
                                val photosWithAspect = groupPhotos.map { photo ->
                                    val thumb = searchThumbnails[photo.id]
                                    val aspect = if (thumb != null && thumb.height > 0) thumb.width.toFloat() / thumb.height else if (photo.mediaType == VaultMediaType.PDF) 0.75f else 1.33f
                                    photo to aspect
                                }
                                val rows = photosWithAspect.chunked(3)
                                items(rows) { row ->
                                    val totalAspect = row.sumOf { it.second.toDouble() }.toFloat()
                                    val gaps = (row.size - 1) * 3f
                                    val rowHeight = ((faceGridWidth.value - gaps) / totalAspect).coerceAtMost(maxItemHeight)
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        row.forEach { (photo, aspect) ->
                                            val itemWidth = (rowHeight * aspect).dp
                                            val itemHeight = rowHeight.dp
                                            val thumb = searchThumbnails[photo.id]
                                            val isSelected = photo.id in selectedIds
                                            Box(
                                                Modifier.width(itemWidth).height(itemHeight)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (isSelectionMode) {
                                                                selectedIds = if (isSelected) selectedIds - photo.id else selectedIds + photo.id
                                                                if (selectedIds.isEmpty()) isSelectionMode = false
                                                            } else {
                                                                photos = sortedResults; thumbnails = searchThumbnails; viewerFromSearch = true; openViewer(photo)
                                                            }
                                                        },
                                                        onLongClick = { isSelectionMode = true; selectedIds = selectedIds + photo.id }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (thumb != null) {
                                                    Image(thumb.asImageBitmap(), "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                } else {
                                                    Icon(Icons.Default.Lock, "Encrypted", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                if (isSelectionMode && isSelected) {
                                                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (isSearching && smartMode == "duplicates" && duplicateGroups.isNotEmpty()) {
                        // === Grouped duplicate review ===
                        var keepSelection by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                        var dupThumbs by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

                        // Load thumbnails progressively + set default keep selection
                        LaunchedEffect(duplicateGroups) {
                            dupThumbs = emptyMap()
                            val allVault = withContext(Dispatchers.IO) { getAllVaultItems().associateBy { it.id } }
                            val allIds = duplicateGroups.flatten().distinct()
                            val batch = mutableMapOf<String, Bitmap>()
                            allIds.forEachIndexed { i, pid ->
                                withContext(Dispatchers.IO) {
                                    allVault[pid]?.let { photo -> vault.loadThumbnail(photo)?.let { batch[pid] = it } }
                                }
                                if ((i + 1) % 20 == 0 || i == allIds.size - 1) { dupThumbs = dupThumbs + batch; batch.clear() }
                            }
                            keepSelection = duplicateGroups.associate { it.first() to it.first() }
                        }

                        Column {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${duplicateGroups.size} groups", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                TextButton(onClick = {
                                    val toDelete = mutableSetOf<String>()
                                    duplicateGroups.forEach { grp -> val k = keepSelection[grp.first()] ?: grp.first(); toDelete.addAll(grp.filter { it != k }) }
                                    if (toDelete.isNotEmpty()) deletePhotos(toDelete)
                                    duplicateGroups = emptyList(); duplicateGroupCount = 0; smartMode = null; isSearching = false
                                }) { Text("Delete All Duplicates", color = MaterialTheme.colorScheme.error) }
                            }
                            LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(duplicateGroups, key = { it.first() }) { group ->
                                    val gk = group.first()
                                    val keepId = keepSelection[gk] ?: gk
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("${group.size} similar photos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.height(8.dp))
                                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                group.forEach { pid ->
                                                    val thumb = dupThumbs[pid]
                                                    val isKept = pid == keepId
                                                    Box(
                                                        Modifier.size(100.dp).clip(RoundedCornerShape(12.dp))
                                                            .border(if (isKept) 3.dp else 0.dp, if (isKept) Color(0xFF4CAF50) else Color.Transparent, RoundedCornerShape(12.dp))
                                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                                            .clickable { keepSelection = keepSelection + (gk to pid) },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (thumb != null) {
                                                            Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                        } else {
                                                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                                        }
                                                        if (isKept) {
                                                            Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(24.dp).background(Color(0xFF4CAF50), CircleShape), contentAlignment = Alignment.Center) {
                                                                Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = Color.White)
                                                            }
                                                            Text("KEEP", Modifier.align(Alignment.BottomCenter).background(Color(0xFF4CAF50).copy(alpha = 0.8f), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).fillMaxWidth().padding(2.dp), color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center)
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            TextButton(onClick = {
                                                val toDelete = group.filter { it != keepId }.toSet()
                                                if (toDelete.isNotEmpty()) deletePhotos(toDelete)
                                                duplicateGroups = duplicateGroups.filter { it.first() != gk }
                                                duplicateGroupCount = duplicateGroups.size
                                                if (duplicateGroups.isEmpty()) { smartMode = null; isSearching = false }
                                            }, modifier = Modifier.align(Alignment.End)) {
                                                Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                                Spacer(Modifier.width(4.dp))
                                                Text("Delete ${group.size - 1} others", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (isSearching) {
                        // Search results view
                        if (searchResults.isEmpty()) {
                            Column(
                                Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (isSmartLoading) {
                                    CircularProgressIndicator(Modifier.size(32.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text(stringResource(R.string.analyzing_photos), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text(stringResource(R.string.no_results_found), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.n_results, searchResults.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isSelectionMode && selectedIds.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${selectedIds.size}", style = MaterialTheme.typography.titleSmall)
                                        IconButton(onClick = { showDeleteDialog = true }) {
                                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                                        }
                                        IconButton(onClick = { selectedIds = emptySet(); isSelectionMode = false }) {
                                            Icon(Icons.Default.Close, stringResource(R.string.action_cancel))
                                        }
                                    }
                                }
                            }
                            val searchGridWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 24.dp
                            LazyColumn(contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                val searchRows = searchResults.chunked(3)
                                items(searchRows) { row ->
                                    val totalAspect = row.sumOf {
                                        val t = searchThumbnails[it.id]
                                        (if (t != null && t.height > 0) t.width.toFloat() / t.height else if (it.mediaType == VaultMediaType.PDF) 0.75f else 1.33f).toDouble()
                                    }.toFloat()
                                    val gaps = (row.size - 1) * 3f
                                    val rowHeight = ((searchGridWidth.value - gaps) / totalAspect).coerceIn(80f, 160f)
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        row.forEach { photo ->
                                            val thumb = searchThumbnails[photo.id]
                                            val aspect = if (thumb != null && thumb.height > 0) thumb.width.toFloat() / thumb.height else if (photo.mediaType == VaultMediaType.PDF) 0.75f else 1.33f
                                            val isSelected = photo.id in selectedIds
                                            Box(
                                                Modifier.width((rowHeight * aspect).dp).height(rowHeight.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (isSelectionMode) {
                                                                selectedIds = if (isSelected) selectedIds - photo.id else selectedIds + photo.id
                                                                if (selectedIds.isEmpty()) isSelectionMode = false
                                                            } else { photos = searchResults; thumbnails = searchThumbnails; viewerFromSearch = true; openViewer(photo) }
                                                        },
                                                        onLongClick = { isSelectionMode = true; selectedIds = selectedIds + photo.id }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (photo.mediaType == VaultMediaType.PDF) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                                                        Icon(Icons.Default.PictureAsPdf, stringResource(R.string.cd_pdf_document), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                                                        Text(photo.id.let { if (it.length > 15) it.take(12) + "..." else it }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                        val sizeKB = photo.encryptedFile.length() / 1024
                                                        Text(if (sizeKB > 1024) "${"%.1f".format(sizeKB / 1024.0)} MB" else "$sizeKB KB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                                    }
                                                } else if (photo.mediaType == VaultMediaType.FILE) {
                                                    val ext = photo.id.substringAfterLast('.', "").lowercase()
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                                                        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                                        Text(photo.id.let { if (it.length > 15) it.take(12) + "..." else it }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                                        val sizeKB = photo.encryptedFile.length() / 1024
                                                        Text(if (sizeKB > 1024) "${"%.1f".format(sizeKB / 1024.0)} MB" else "$sizeKB KB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                                        Text(ext.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                                    }
                                                } else if (thumb != null) {
                                                    Image(thumb.asImageBitmap(), if (photo.mediaType == VaultMediaType.VIDEO) stringResource(R.string.cd_video_thumbnail) else stringResource(R.string.cd_photo_thumbnail), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                } else {
                                                    Icon(Icons.Default.Lock, stringResource(R.string.cd_encrypted_item), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                if (photo.mediaType == VaultMediaType.VIDEO) {
                                                    Box(Modifier.fillMaxWidth().height(36.dp).align(Alignment.BottomCenter)
                                                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)))))
                                                    Icon(Icons.Default.PlayCircleFilled, stringResource(R.string.video), tint = Color.White.copy(alpha = 0.9f),
                                                        modifier = Modifier.size(40.dp).align(Alignment.Center))
                                                }
                                                if (isSelectionMode && isSelected) {
                                                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Normal categories view — scrollable (no LazyColumn children here)
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val halfWidth = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 48.dp) / 2
                            val allFolderItems = if (!isDuressActive) folderManager.listAllFolders().flatMap { vault.listFolderItems(folderManager.getFolderDir(it.id)) } else emptyList()
                            val allPhotosCount = if (!isDuressActive) vault.listAllPhotosOnly(allFolderItems).size else 0
                            val allVideosCount = if (!isDuressActive) vault.listAllVideosOnly(allFolderItems).size else 0
                            CompactCategoryCard(stringResource(R.string.category_camera), categoryCounts[VaultCategory.CAMERA] ?: 0, Icons.Default.CameraAlt, halfWidth) { openCategory(VaultCategory.CAMERA) }
                            // Photos (virtual smart view) — duress safe: openCategory handles duress
                            CompactCategoryCard(stringResource(R.string.all_photos), allPhotosCount, Icons.Default.Photo, halfWidth) {
                                if (isDuressActive) { openCategory(VaultCategory.CAMERA); return@CompactCategoryCard }
                                isVirtualPhotos = true
                                isVirtualVideos = false
                                isVirtualFiles = false
                                scope.launch {
                                    val fi = withContext(Dispatchers.IO) {
                                        folderManager.listAllFolders().flatMap { f -> vault.listFolderItems(folderManager.getFolderDir(f.id)) }
                                    }
                                    photos = withContext(Dispatchers.IO) { vault.listAllPhotosOnly(fi) }
                                    thumbnails = emptyMap()
                                    currentCategory = VaultCategory.CAMERA
                                    page = VaultPage.GALLERY
                                    scope.launch {
                                        val tm = mutableMapOf<String, Bitmap>()
                                        withContext(Dispatchers.IO) { photos.forEach { p -> vault.loadThumbnail(p)?.let { tm[p.id] = it } } }
                                        thumbnails = tm
                                    }
                                }
                            }
                            // Videos (virtual smart view)
                            CompactCategoryCard(stringResource(R.string.all_videos), allVideosCount, Icons.Default.Videocam, halfWidth) {
                                if (isDuressActive) { openCategory(VaultCategory.VIDEO); return@CompactCategoryCard }
                                isVirtualVideos = true
                                isVirtualPhotos = false
                                isVirtualFiles = false
                                scope.launch {
                                    val fi = withContext(Dispatchers.IO) {
                                        folderManager.listAllFolders().flatMap { f -> vault.listFolderItems(folderManager.getFolderDir(f.id)) }
                                    }
                                    photos = withContext(Dispatchers.IO) { vault.listAllVideosOnly(fi) }
                                    thumbnails = emptyMap()
                                    currentCategory = VaultCategory.VIDEO
                                    page = VaultPage.GALLERY
                                    scope.launch {
                                        val tm = mutableMapOf<String, Bitmap>()
                                        withContext(Dispatchers.IO) { photos.forEach { p -> vault.loadThumbnail(p)?.let { tm[p.id] = it } } }
                                        thumbnails = tm
                                    }
                                }
                            }
                            CompactCategoryCard(stringResource(R.string.category_scans), categoryCounts[VaultCategory.SCAN] ?: 0, Icons.Default.DocumentScanner, halfWidth) { openCategory(VaultCategory.SCAN) }
                            CompactCategoryCard(stringResource(R.string.category_detections), categoryCounts[VaultCategory.DETECT] ?: 0, Icons.Default.CameraAlt, halfWidth) { openCategory(VaultCategory.DETECT) }
                            CompactCategoryCard(stringResource(R.string.category_reports), categoryCounts[VaultCategory.REPORTS] ?: 0, Icons.Default.Description, halfWidth) { openCategory(VaultCategory.REPORTS) }
                            // Files — universal file explorer (ALL items except hidden)
                            val totalFileCount = if (!isDuressActive) {
                                val catTotal = VaultCategory.entries.sumOf { categoryCounts[it] ?: 0 }
                                val folderTotal = allFolderItems.size
                                catTotal + folderTotal
                            } else 0
                            CompactCategoryCard(stringResource(R.string.category_files), totalFileCount, Icons.Default.Folder, halfWidth) {
                                if (isDuressActive) { openCategory(VaultCategory.FILES); return@CompactCategoryCard }
                                isVirtualFiles = true
                                isVirtualPhotos = false
                                isVirtualVideos = false
                                filesFilter = "all"
                                scope.launch {
                                    val allItems = withContext(Dispatchers.IO) { vault.listAllPhotos() }
                                    val folderItems = withContext(Dispatchers.IO) {
                                        folderManager.listAllFolders().flatMap { f -> vault.listFolderItems(folderManager.getFolderDir(f.id)) }
                                    }
                                    photos = (allItems + folderItems).distinctBy { it.id }.sortedByDescending { it.timestamp }
                                    thumbnails = emptyMap()
                                    currentCategory = VaultCategory.FILES
                                    page = VaultPage.GALLERY
                                    scope.launch {
                                        val tm = mutableMapOf<String, Bitmap>()
                                        withContext(Dispatchers.IO) { photos.forEach { p -> vault.loadThumbnail(p)?.let { tm[p.id] = it } } }
                                        thumbnails = tm
                                    }
                                }
                            }
                        }

                        // Hidden folder — only visible after tap-to-reveal
                        if (isHiddenFolderActive && !isDuressActive) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))

                            // Hidden folder section
                            val hiddenDir = remember { java.io.File(context.filesDir, "vault/hidden").also { it.mkdirs() } }
                            var hiddenPhotos by remember { mutableStateOf<List<VaultPhoto>>(emptyList()) }
                            var hiddenThumbs by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

                            LaunchedEffect(isHiddenFolderActive) {
                                withContext(Dispatchers.IO) {
                                    hiddenPhotos = vault.listFolderItems(hiddenDir)
                                    val thumbMap = mutableMapOf<String, Bitmap>()
                                    hiddenPhotos.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } }
                                    hiddenThumbs = thumbMap
                                }
                            }

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                    Text("Hidden", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                                    Text("${hiddenPhotos.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row {
                                    // Import to hidden folder
                                    val hiddenImportLauncher = rememberLauncherForActivityResult(
                                        contract = ActivityResultContracts.OpenMultipleDocuments()
                                    ) { uris ->
                                        if (uris.isEmpty()) return@rememberLauncherForActivityResult
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                uris.forEach { uri ->
                                                    try {
                                                        val mimeType = context.contentResolver.getType(uri)
                                                        if (mimeType?.startsWith("video/") == true) {
                                                            val tempFile = java.io.File(context.cacheDir, "hidden_vid_${System.currentTimeMillis()}.mp4")
                                                            context.contentResolver.openInputStream(uri)?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
                                                            vault.saveVideo(tempFile, VaultCategory.VIDEO)
                                                            val vid = vault.listPhotos(VaultCategory.VIDEO).firstOrNull()
                                                            vid?.let { vault.moveToFolder(it, hiddenDir) }
                                                        } else {
                                                            val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@forEach
                                                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@forEach
                                                            vault.savePhotoToFolder(bitmap, hiddenDir)
                                                            bitmap.recycle()
                                                        }
                                                    } catch (_: Exception) {}
                                                }
                                                hiddenPhotos = vault.listFolderItems(hiddenDir)
                                                val thumbMap = mutableMapOf<String, Bitmap>()
                                                hiddenPhotos.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } }
                                                hiddenThumbs = thumbMap
                                            }
                                        }
                                    }
                                    IconButton(onClick = { hiddenImportLauncher.launch(arrayOf("image/*", "video/*")) }) {
                                        Icon(Icons.Default.Add, "Import to hidden", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                    // Close hidden folder
                                    IconButton(onClick = { isHiddenFolderActive = false }) {
                                        Icon(Icons.Default.Close, "Close hidden", Modifier.size(20.dp))
                                    }
                                }
                            }

                            if (hiddenPhotos.isEmpty()) {
                                Text("Empty — import files to hide them here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                // Grid of hidden photos — FlowRow instead of LazyVerticalGrid
                                // to avoid nested scrollable conflicts with the outer Column
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    hiddenPhotos.forEachIndexed { i, photo ->
                                        val thumb = hiddenThumbs[photo.id]
                                        Box(
                                            Modifier.size(100.dp).clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable {
                                                    scope.launch {
                                                        val bmp = withContext(Dispatchers.IO) { vault.loadFullPhoto(photo) }
                                                        viewerPhoto = photo
                                                        viewerBitmap = bmp
                                                        photos = hiddenPhotos
                                                        thumbnails = hiddenThumbs
                                                        viewerFromHidden = true
                                                        page = VaultPage.VIEWER
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (thumb != null) {
                                                Image(
                                                    bitmap = thumb.asImageBitmap(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))

                        // My Folders section
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.my_folders), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            androidx.compose.material3.TextButton(
                                onClick = { showCreateFolderDialog = true },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(stringResource(R.string.new_folder), style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        if (rootFolders.isEmpty()) {
                            Text(stringResource(R.string.no_folders_yet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                        }

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val halfW = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 48.dp) / 2
                        rootFolders.forEach { folder ->
                            val itemCount = if (isDuressActive) 0 else folderManager.countItems(folder.id)
                            CompactCategoryCard(folder.name, itemCount, Icons.Default.Description, halfW) {
                                currentFolder = folder
                                val dir = folderManager.getFolderDir(folder.id)
                                photos = if (isDuressActive) emptyList() else vault.listFolderItems(dir)
                                thumbnails = emptyMap()
                                scope.launch {
                                    val thumbMap = mutableMapOf<String, Bitmap>()
                                    withContext(Dispatchers.IO) {
                                        photos.forEach { photo ->
                                            vault.loadThumbnail(photo)?.let { thumbMap[photo.id] = it }
                                        }
                                    }
                                    thumbnails = thumbMap
                                    subfolders = folderManager.listSubfolders(folder.id)
                                    page = VaultPage.FOLDER_VIEW
                                }
                            }
                        }
                        } // end FlowRow

                        // Trash section (hidden in duress mode)
                        if (trashCount > 0 && !isDuressActive) {
                            Spacer(Modifier.height(16.dp))
                            Card(
                                Modifier.fillMaxWidth().clickable {
                                    trashItems = vault.listTrash()
                                    // Load trash thumbnails
                                    scope.launch {
                                        val thumbs = mutableMapOf<String, Bitmap>()
                                        withContext(Dispatchers.IO) {
                                            trashItems.forEach { item ->
                                                if (item.thumbnailFile.exists()) {
                                                    try {
                                                        val bytes = crypto.decryptFile(item.thumbnailFile)
                                                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { thumbs[item.id] = it }
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                        }
                                        trashThumbnails = thumbs
                                    }
                                    page = VaultPage.TRASH
                                },
                                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                            ) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.Delete, "Trash", Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                                    Column(Modifier.weight(1f)) {
                                        Text("Trash", style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                        Text("$trashCount item${if (trashCount > 1) "s" else ""} • auto-deletes after 30 days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } // end scrollable Column
                    } // end if/else isSearching
                }
            }
        }

        VaultPage.GALLERY -> {
            Scaffold(topBar = {
                if (isSelectionMode) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.n_selected, selectedIds.size)) },
                        navigationIcon = { IconButton(onClick = { selectedIds = emptySet(); isSelectionMode = false }) { Icon(Icons.Default.Close, stringResource(R.string.cancel)) } },
                        actions = {
                            if (selectedIds.size == 1) {
                                IconButton(onClick = {
                                    viewerPhoto = photos.find { it.id in selectedIds }
                                    showDetailsDialog = true
                                }) { Icon(Icons.Default.Info, stringResource(R.string.details)) }
                            }
                            if (!isVirtualPhotos && !isVirtualVideos) {
                                IconButton(onClick = { showMoveDialog = true }) { Icon(Icons.Default.DriveFileMove, stringResource(R.string.move)) }
                            }
                            IconButton(onClick = { shareImages(selectedIds) }) { Icon(Icons.Default.Share, stringResource(R.string.share)) }
                            IconButton(onClick = { saveToDevice(selectedIds) }) { Icon(Icons.Default.SaveAlt, stringResource(R.string.save)) }
                            IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                        }
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                when {
                                    isVirtualPhotos -> "${stringResource(R.string.all_photos)} (${photos.size})"
                                    isVirtualVideos -> "${stringResource(R.string.all_videos)} (${photos.size})"
                                    isVirtualFiles -> "${stringResource(R.string.category_files)} (${photos.size})"
                                    else -> stringResource(R.string.category_with_count, currentCategory.label, photos.size)
                                }
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                thumbnails = emptyMap()
                                isVirtualPhotos = false
                                isVirtualVideos = false
                                isVirtualFiles = false
                                if (!isDuressActive) categoryCounts = vault.countByCategory(); rootFolders = folderManager.listRootFolders()
                                page = VaultPage.CATEGORIES
                            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                        },
                        actions = {
                            // Overflow menu — first action is "Select all".
                            // Hidden during duress (no actions on a fake-empty
                            // gallery) and when there's nothing to select.
                            if (!isDuressActive && photos.isNotEmpty()) {
                                Box {
                                    IconButton(onClick = { showOverflowMenu = true }) {
                                        Icon(Icons.Default.MoreVert, stringResource(R.string.action_more))
                                    }
                                    DropdownMenu(
                                        expanded = showOverflowMenu,
                                        onDismissRequest = { showOverflowMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_select_all)) },
                                            leadingIcon = { Icon(Icons.Default.SelectAll, null) },
                                            onClick = {
                                                showOverflowMenu = false
                                                selectedIds = photos.map { it.id }.toSet()
                                                isSelectionMode = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(
                                                    if (starredOnly) R.string.filter_show_all
                                                    else R.string.filter_starred_only
                                                ))
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    if (starredOnly) Icons.Default.Star else Icons.Outlined.StarBorder,
                                                    null,
                                                    tint = if (starredOnly) Color(0xFFFFC107) else LocalContentColor.current
                                                )
                                            },
                                            onClick = {
                                                showOverflowMenu = false
                                                starredOnly = !starredOnly
                                            }
                                        )
                                        // Sort by — global preference, applied across every
                                        // vault list view (gallery, folders, search, smart
                                        // filters, face groups). Choosing a new mode triggers
                                        // a LaunchedEffect that re-sorts the live state lists.
                                        androidx.compose.material3.HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_created_desc)) },
                                            leadingIcon = { Icon(Icons.Default.ArrowDownward, null, tint = if (sortMode == SortMode.CREATED_DESC) MaterialTheme.colorScheme.primary else LocalContentColor.current) },
                                            onClick = { showOverflowMenu = false; sortMode = SortMode.CREATED_DESC; setSortMode(context, SortMode.CREATED_DESC) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_created_asc)) },
                                            leadingIcon = { Icon(Icons.Default.ArrowUpward, null, tint = if (sortMode == SortMode.CREATED_ASC) MaterialTheme.colorScheme.primary else LocalContentColor.current) },
                                            onClick = { showOverflowMenu = false; sortMode = SortMode.CREATED_ASC; setSortMode(context, SortMode.CREATED_ASC) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_updated_desc)) },
                                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = if (sortMode == SortMode.UPDATED_DESC) MaterialTheme.colorScheme.primary else LocalContentColor.current) },
                                            onClick = { showOverflowMenu = false; sortMode = SortMode.UPDATED_DESC; setSortMode(context, SortMode.UPDATED_DESC) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_updated_asc)) },
                                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = if (sortMode == SortMode.UPDATED_ASC) MaterialTheme.colorScheme.primary else LocalContentColor.current) },
                                            onClick = { showOverflowMenu = false; sortMode = SortMode.UPDATED_ASC; setSortMode(context, SortMode.UPDATED_ASC) }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }) { padding ->
                if (photos.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(stringResource(R.string.no_items_yet), style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    // Apply files filter if in virtual files mode
                    val typeFiltered = if (isVirtualFiles && filesFilter != "all") {
                        when (filesFilter) {
                            "photo" -> photos.filter { it.mediaType == VaultMediaType.PHOTO }
                            "video" -> photos.filter { it.mediaType == VaultMediaType.VIDEO }
                            "pdf" -> photos.filter { it.mediaType == VaultMediaType.PDF }
                            "other" -> photos.filter { it.mediaType == VaultMediaType.FILE }
                            else -> photos
                        }
                    } else photos
                    val starFiltered = if (starredOnly) {
                        val starredIds = remember(starredOnly, typeFiltered) { vault.listStarred() }
                        typeFiltered.filter { it.id in starredIds }
                    } else typeFiltered
                    // Re-sort the gallery according to the user's Sort menu choice.
                    val displayPhotos = remember(starFiltered, sortMode, updatedTimes) { sortPhotos(starFiltered) }
                    val grouped = remember(displayPhotos) { groupPhotosByDate(displayPhotos) }

                    val galleryGridWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 16.dp
                    LazyColumn(
                        state = galleryListState,
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(padding)
                    ) {
                        // File type filter chips (only in virtual files mode)
                        if (isVirtualFiles) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val filters = listOf(
                                        "all" to stringResource(R.string.filter_all),
                                        "photo" to stringResource(R.string.filter_photos),
                                        "video" to stringResource(R.string.filter_videos),
                                        "pdf" to "PDF",
                                        "other" to stringResource(R.string.filter_other)
                                    )
                                    filters.forEach { (key, label) ->
                                        FilterChip(
                                            selected = filesFilter == key,
                                            onClick = { filesFilter = key },
                                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                        grouped.forEach { (header, groupPhotos) ->
                            item {
                                Text(
                                    text = header,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                            // Build rows: separate videos (2 per row, full width) from photos (3/2 alternating)
                            val isVideoCategory = isVirtualVideos || currentCategory == VaultCategory.VIDEO
                            val isScanCategory = currentCategory == VaultCategory.SCAN && !isVirtualPhotos && !isVirtualVideos

                            // Split into typed rows: videos always 2-per-row full width, photos use normal layout
                            data class TypedRow(val items: List<VaultPhoto>, val isVideoRow: Boolean)
                            val typedRows = if (isVideoCategory) {
                                groupPhotos.chunked(2).map { TypedRow(it, true) }
                            } else if (isScanCategory) {
                                groupPhotos.chunked(3).map { TypedRow(it, false) }
                            } else {
                                // Build rows preserving chronological order, grouping consecutive same-type items
                                val rows = mutableListOf<TypedRow>()
                                var currentPhotoBatch = mutableListOf<VaultPhoto>()
                                var currentVideoBatch = mutableListOf<VaultPhoto>()

                                fun flushPhotos() {
                                    if (currentPhotoBatch.isEmpty()) return
                                    var i2 = 0; var ri = 0
                                    while (i2 < currentPhotoBatch.size) {
                                        val count = if (ri % 3 == 1) 2 else 3
                                        rows.add(TypedRow(currentPhotoBatch.subList(i2, (i2 + count).coerceAtMost(currentPhotoBatch.size)), false))
                                        i2 += count; ri++
                                    }
                                    currentPhotoBatch = mutableListOf()
                                }
                                fun flushVideos() {
                                    if (currentVideoBatch.isEmpty()) return
                                    currentVideoBatch.chunked(2).forEach { rows.add(TypedRow(it, true)) }
                                    currentVideoBatch = mutableListOf()
                                }

                                // Items are already sorted by timestamp desc
                                groupPhotos.forEach { item ->
                                    if (item.mediaType == VaultMediaType.VIDEO) {
                                        flushPhotos()
                                        currentVideoBatch.add(item)
                                    } else {
                                        flushVideos()
                                        currentPhotoBatch.add(item)
                                    }
                                }
                                flushPhotos()
                                flushVideos()
                                rows
                            }
                            typedRows.forEach { typedRow ->
                                val row = typedRow.items
                                item {
                                    val isVidRow = typedRow.isVideoRow
                                    val totalAspect = row.sumOf {
                                        val t = thumbnails[it.id]
                                        val a = if (t != null && t.height > 0) t.width.toFloat() / t.height else if (it.mediaType == VaultMediaType.PDF) 0.75f else 1.33f
                                        a.toDouble()
                                    }.toFloat()
                                    val gaps = (row.size - 1) * 3f
                                    val maxRowH = if (isVidRow) 220f else 160f
                                    val rowHeight = ((galleryGridWidth.value - gaps) / totalAspect).coerceIn(80f, maxRowH)
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                        row.forEach { photo ->
                                            val thumb = thumbnails[photo.id]
                                            val aspect = if (thumb != null && thumb.height > 0) thumb.width.toFloat() / thumb.height else if (photo.mediaType == VaultMediaType.PDF) 0.75f else 1.33f
                                            val isSelected = photo.id in selectedIds
                                            val fillEqual = isVidRow || isScanCategory
                                            val equalWidth = (galleryGridWidth.value - (row.size - 1) * 3f) / row.size
                                            val equalHeight = (equalWidth / (if (isVidRow) 1.78f else 0.75f)).dp
                                            Box(
                                                Modifier.then(if (fillEqual) Modifier.weight(1f).height(equalHeight) else Modifier.width((rowHeight * aspect).dp).height(rowHeight.dp))
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier)
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (isSelectionMode) {
                                                                selectedIds = if (isSelected) selectedIds - photo.id else selectedIds + photo.id
                                                                if (selectedIds.isEmpty()) isSelectionMode = false
                                                            } else openViewer(photo)
                                                        },
                                                        onLongClick = { isSelectionMode = true; selectedIds = setOf(photo.id) }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (photo.mediaType == VaultMediaType.PDF) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                                                        Icon(Icons.Default.PictureAsPdf, stringResource(R.string.cd_pdf_document), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                                                        Text(photo.id.let { if (it.length > 15) it.take(12) + "..." else it }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                        val sizeKB = photo.encryptedFile.length() / 1024
                                                        Text(if (sizeKB > 1024) "${"%.1f".format(sizeKB / 1024.0)} MB" else "$sizeKB KB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                                    }
                                                } else if (photo.mediaType == VaultMediaType.FILE) {
                                                    val ext = photo.id.substringAfterLast('.', "").lowercase()
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                                                        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                                        Text(photo.id.let { if (it.length > 15) it.take(12) + "..." else it }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                                        val sizeKB = photo.encryptedFile.length() / 1024
                                                        Text(if (sizeKB > 1024) "${"%.1f".format(sizeKB / 1024.0)} MB" else "$sizeKB KB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                                        Text(ext.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                                    }
                                                } else if (thumb != null) {
                                                    Image(thumb.asImageBitmap(), if (photo.mediaType == VaultMediaType.VIDEO) stringResource(R.string.cd_video_thumbnail) else stringResource(R.string.cd_photo_thumbnail), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                } else {
                                                    Icon(Icons.Default.Lock, stringResource(R.string.cd_encrypted_item), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                if (photo.mediaType == VaultMediaType.VIDEO) {
                                                    Box(Modifier.fillMaxWidth().height(36.dp).align(Alignment.BottomCenter)
                                                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)))))
                                                    Icon(Icons.Default.PlayCircleFilled, stringResource(R.string.video), tint = Color.White.copy(alpha = 0.9f),
                                                        modifier = Modifier.size(40.dp).align(Alignment.Center))
                                                }
                                                if (isSelectionMode && isSelected) {
                                                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Color.White)
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
        }

        VaultPage.VIEWER -> {
            // Get navigable items (photos + videos, not PDFs)
            val viewablePhotos = remember(photos) { photos.filter { it.mediaType != VaultMediaType.PDF } }
            val currentIndex = viewablePhotos.indexOfFirst { it.id == viewerPhoto?.id }

            fun navigateToItem(index: Int) {
                val item = viewablePhotos.getOrNull(index) ?: return
                if (item.mediaType == VaultMediaType.VIDEO) {
                    // Switch to video player
                    scope.launch {
                        val tempFile = withContext(Dispatchers.IO) { vault.decryptVideoToTempFile(item) }
                        if (tempFile != null) {
                            // viewerBitmap cleared — GC handles recycling
                            viewerBitmap = null
                            videoTempFile = tempFile
                            viewerPhoto = item
                            page = VaultPage.VIDEO_PLAYER
                        }
                    }
                } else {
                    scope.launch {
                        val bmp = withContext(Dispatchers.IO) { vault.loadFullPhoto(item) }
                        // viewerBitmap cleared — GC handles recycling
                        viewerBitmap = bmp
                        viewerPhoto = item
                    }
                }
            }

            Box(Modifier.fillMaxSize().background(Color.White)) {
                viewerBitmap?.let { bmp ->
                    // Pinch-to-zoom + pan + swipe-to-navigate (when not zoomed)
                    var scale by remember(viewerPhoto?.id) { mutableStateOf(1f) }
                    var offsetX by remember(viewerPhoto?.id) { mutableStateOf(0f) }
                    var offsetY by remember(viewerPhoto?.id) { mutableStateOf(0f) }

                    // Smooth crossfade when the current image swaps for the
                    // next/previous one. Snappier 100ms duration — long enough
                    // to soften the swap, short enough to feel responsive.
                    androidx.compose.animation.Crossfade(
                        targetState = bmp,
                        animationSpec = androidx.compose.animation.core.tween(100),
                        label = "vault-viewer-crossfade"
                    ) { animatedBmp ->
                    Image(
                        animatedBmp.asImageBitmap(), stringResource(R.string.photo),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                            .pointerInput(currentIndex, viewerPhoto?.id) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    // Pinch zoom: 1x to 5x
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    scale = newScale

                                    if (scale > 1.05f) {
                                        // Panning when zoomed in
                                        offsetX += pan.x
                                        offsetY += pan.y
                                        // Constrain pan to reasonable bounds
                                        val maxX = (scale - 1f) * size.width / 2
                                        val maxY = (scale - 1f) * size.height / 2
                                        offsetX = offsetX.coerceIn(-maxX, maxX)
                                        offsetY = offsetY.coerceIn(-maxY, maxY)
                                    } else {
                                        // Reset offset when zoomed out
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            }
                            .pointerInput(currentIndex, scale) {
                                // Swipe to navigate only when NOT zoomed
                                if (scale <= 1.05f) {
                                    var dragTotal = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = { dragTotal = 0f },
                                        onDragEnd = {
                                            if (dragTotal > 100 && currentIndex > 0) {
                                                navigateToItem(currentIndex - 1)
                                            } else if (dragTotal < -100 && currentIndex < viewablePhotos.size - 1) {
                                                navigateToItem(currentIndex + 1)
                                            }
                                        },
                                        onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount }
                                    )
                                }
                            }
                            .pointerInput(viewerPhoto?.id) {
                                // Double-tap to toggle zoom
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (scale > 1.1f) {
                                            scale = 1f; offsetX = 0f; offsetY = 0f
                                        } else {
                                            scale = 2.5f
                                        }
                                    }
                                )
                            }
                    )
                    } // end Crossfade
                }

                // Top-center: datetime of the photo, counter underneath when
                // multiple items are visible.
                viewerPhoto?.let { vp ->
                    val dateLabel = remember(vp.id) {
                        SimpleDateFormat("MMM d, yyyy · HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(vp.timestamp))
                    }
                    Column(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 54.dp)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(dateLabel, color = Color(0xFF333333), fontSize = 12.sp)
                        if (viewablePhotos.size > 1 && currentIndex >= 0) {
                            Text(
                                "${currentIndex + 1} / ${viewablePhotos.size}",
                                color = Color(0xFF555555),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        viewerBitmap = null
                        // Mirror the BackHandler: deep-linked viewer leaves the
                        // Vault entirely; viewer-from-search returns to the
                        // search-results UI; folder / hidden / default all
                        // route back to their origin page.
                        when {
                            viewerFromDeepLink -> { viewerFromDeepLink = false; onBack?.invoke() }
                            viewerFromHidden -> { viewerFromHidden = false; page = VaultPage.CATEGORIES }
                            viewerFromFolder -> { viewerFromFolder = false; page = VaultPage.FOLDER_VIEW }
                            viewerFromSearch -> { viewerFromSearch = false; page = VaultPage.CATEGORIES }
                            else -> page = VaultPage.GALLERY
                        }
                    },
                    Modifier.align(Alignment.TopStart).padding(top = 48.dp, start = 16.dp).size(40.dp).background(Color.White.copy(alpha = 0.9f), CircleShape)
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color(0xFF333333)) }

                val blurDefault = com.privateai.camera.ui.settings.isFaceBlurEnabled(context)

                // Star button — sits to the left of the 3-dot. Toggles the
                // photo's starred flag; persisted in an encrypted set on disk.
                // Smaller, white-circle pill for a lighter feel.
                viewerPhoto?.let { vp ->
                    var isStarred by remember(vp.id) { mutableStateOf(vault.isStarred(vp.id)) }
                    IconButton(
                        onClick = {
                            val next = !isStarred
                            vault.setStarred(vp.id, next)
                            isStarred = next
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 52.dp, end = 56.dp)
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.9f), CircleShape)
                    ) {
                        Icon(
                            if (isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
                            stringResource(if (isStarred) R.string.action_unstar else R.string.action_star),
                            tint = if (isStarred) Color(0xFFFFC107) else Color(0xFF333333),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // ── AI per-photo state ─────────────────────────────────────────────
                // Hoisted out of viewerPhoto.let so the overflow menu items below
                // (Describe / Ask / Generate AI tags) can call into them. Keyed on
                // photo id so they refresh when the user navigates between photos.
                // The Settings toggle "Show AI labels" only gates the on-image
                // chips + description text rendered farther down; the menu items
                // themselves are always available when the AI Assistant is active.
                val viewerVpId = viewerPhoto?.id
                var aiDescription by remember(viewerVpId) {
                    mutableStateOf(viewerPhoto?.let { photoIndex?.getDescription(it.id) } ?: "")
                }
                var aiDescLoading by remember(viewerVpId) { mutableStateOf(false) }
                var showAskDialog by remember { mutableStateOf(false) }
                var askQuestion by remember { mutableStateOf("") }
                var askAnswer by remember { mutableStateOf<String?>(null) }
                var askLoading by remember { mutableStateOf(false) }
                var aiLabels by remember(viewerVpId) {
                    mutableStateOf(viewerPhoto?.let {
                        (photoIndex?.getLabelsWithScores(it.id) ?: emptyList()).sortedByDescending { p -> p.second }
                    } ?: emptyList())
                }
                var aiTagsLoading by remember(viewerVpId) { mutableStateOf(false) }

                LaunchedEffect(viewerVpId) {
                    val id = viewerVpId ?: return@LaunchedEffect
                    aiDescription = withContext(Dispatchers.IO) { photoIndex?.getDescription(id) ?: "" }
                    aiLabels = withContext(Dispatchers.IO) {
                        (photoIndex?.getLabelsWithScores(id) ?: emptyList()).sortedByDescending { it.second }
                    }
                }

                /** Ask Gemma vision for short semantic tags and merge into the photo index. */
                fun generateAiTags() {
                    val vp = viewerPhoto ?: return
                    if (aiTagsLoading) return
                    aiTagsLoading = true
                    scope.launch {
                        try {
                            val bmp = withContext(Dispatchers.IO) { vault.loadFullPhoto(vp) }
                            if (bmp != null) {
                                val tempFile = java.io.File(context.cacheDir, "tags_${vp.id}.jpg")
                                withContext(Dispatchers.IO) {
                                    java.io.FileOutputStream(tempFile).use { out ->
                                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                                    }
                                }
                                val reply = com.privateai.camera.bridge.GemmaRunner.describeImage(
                                    context, tempFile.absolutePath,
                                    com.privateai.camera.bridge.GemmaPrompts.generateTags()
                                )
                                tempFile.delete()
                                if (!reply.isNullOrBlank()) {
                                    val cleaned = reply
                                        .replace('\n', ',')
                                        .replace(Regex("""[•\-*]+"""), "")
                                        .replace(Regex("""\d+\.\s*"""), "")
                                    val newTags = cleaned.split(',')
                                        .map { it.trim().lowercase() }
                                        .filter { it.length in 2..30 }
                                        .distinct()
                                        .take(12)
                                    if (newTags.isNotEmpty()) {
                                        withContext(Dispatchers.IO) {
                                            photoIndex?.mergeAiTags(vp.id, newTags)
                                        }
                                        aiLabels = withContext(Dispatchers.IO) {
                                            (photoIndex?.getLabelsWithScores(vp.id) ?: emptyList()).sortedByDescending { it.second }
                                        }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, newTags.joinToString(", "), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("VaultAI", "Generate tags failed: ${e.message}", e)
                        }
                        aiTagsLoading = false
                    }
                }

                /** Generate a one-sentence Gemma vision description and cache it. */
                fun generateDescription() {
                    val vp = viewerPhoto ?: return
                    if (aiDescLoading) return
                    aiDescLoading = true
                    scope.launch {
                        try {
                            val bmp = withContext(Dispatchers.IO) { vault.loadFullPhoto(vp) }
                            if (bmp != null) {
                                val tempFile = java.io.File(context.cacheDir, "describe_${vp.id}.jpg")
                                withContext(Dispatchers.IO) {
                                    java.io.FileOutputStream(tempFile).use { out ->
                                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                                    }
                                }
                                val desc = com.privateai.camera.bridge.GemmaRunner.describeImage(
                                    context, tempFile.absolutePath,
                                    com.privateai.camera.bridge.GemmaPrompts.describePhoto()
                                )
                                tempFile.delete()
                                if (!desc.isNullOrBlank()) {
                                    aiDescription = desc.trim()
                                    withContext(Dispatchers.IO) {
                                        photoIndex?.setDescription(vp.id, aiDescription)
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, aiDescription, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("VaultAI", "Describe failed: ${e.message}", e)
                        }
                        aiDescLoading = false
                    }
                }

                // AiStatus is the single source of truth — same rule as the
                // rest of the app: when AI isn't READY the AI menu items
                // don't render at all (they're hidden, not disabled).
                val aiStatusForVault by com.privateai.camera.bridge.rememberAiStatus()
                val aiActionsAvailable = aiStatusForVault.isReady

                // Top-right overflow menu — Info / Find similar / AI actions /
                // Share with face blur. Mirrors the gallery's 3-dot pattern and
                // keeps the bottom action bar uncluttered (Share / Edit / Delete only).
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 16.dp)
                ) {
                    val aiWorking = aiDescLoading || aiTagsLoading || askLoading
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.9f), CircleShape)
                    ) {
                        if (aiWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF333333)
                            )
                        } else {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.action_more), tint = Color(0xFF333333))
                        }
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        // Find similar — search-same-image. Only when the
                        // photo index is built (AI search has run).
                        if (photoIndex != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.find_similar)) },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    val pi = photoIndex ?: return@DropdownMenuItem
                                    val vp = viewerPhoto ?: return@DropdownMenuItem
                                    scope.launch {
                                        val similarIds = withContext(Dispatchers.IO) {
                                            pi.findSimilar(vp.id).map { it.first }.toSet()
                                        }
                                        if (similarIds.isNotEmpty()) {
                                            val allPhotos = withContext(Dispatchers.IO) { getAllVaultItems() }
                                            searchResults = allPhotos.filter { it.id in similarIds }
                                            isSearching = true
                                            smartMode = null
                                            searchFromViewer = true
                                            page = VaultPage.CATEGORIES
                                            val thumbMap = mutableMapOf<String, Bitmap>()
                                            withContext(Dispatchers.IO) {
                                                searchResults.forEach { p ->
                                                    vault.loadThumbnail(p)?.let { thumbMap[p.id] = it }
                                                }
                                            }
                                            searchThumbnails = thumbMap
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.no_similar_found), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                        // AI actions — gated by AI Assistant availability, not by
                        // the Settings "Show AI labels" toggle (which only controls
                        // the on-image overlay).
                        if (aiActionsAvailable) {
                            if (aiDescription.isEmpty() && !aiDescLoading) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.vault_describe)) },
                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        generateDescription()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.vault_ask_about_image)) },
                                leadingIcon = { Icon(Icons.Default.QuestionAnswer, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showAskDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(
                                        if (aiTagsLoading) R.string.vault_generating_ai_tags
                                        else R.string.vault_generate_ai_tags
                                    ))
                                },
                                leadingIcon = { Icon(Icons.Default.Sell, null) },
                                enabled = !aiTagsLoading,
                                onClick = {
                                    showOverflowMenu = false
                                    generateAiTags()
                                }
                            )
                        }
                        // Divider between AI-driven actions above (Find similar /
                        // Describe / Ask / Generate AI tags) and utility actions
                        // below (face-blur share / Details).
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        // Share with face blur (or share without blur if blur
                        // is the default). Same logic as the old face-toggle
                        // button — moved into the overflow because it's a
                        // less-frequent action than plain Share.
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(
                                    if (blurDefault) R.string.share_without_blur else R.string.blur_and_share
                                ))
                            },
                            leadingIcon = { Icon(Icons.Default.Face, null) },
                            onClick = {
                                showOverflowMenu = false
                                viewerPhoto?.let { photo ->
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            var bitmap = vault.loadFullPhoto(photo) ?: return@withContext
                                            if (!blurDefault) {
                                                bitmap = com.privateai.camera.util.FaceBlur.blurFaces(context, bitmap)
                                            }
                                            val uri = com.privateai.camera.util.saveBitmapToCache(context, bitmap, "vault_alt_share.jpg")
                                            bitmap.recycle()
                                            withContext(Dispatchers.Main) {
                                                val label = if (blurDefault) context.getString(R.string.share_no_blur)
                                                            else context.getString(R.string.share_faces_blurred)
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
                            }
                        )
                        // Details — moved from the bottom action bar.
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.details)) },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = {
                                showOverflowMenu = false
                                showDetailsDialog = true
                            }
                        )
                        // Delete — duplicate of the bottom-bar Delete icon so
                        // users who landed on the overflow menu first don't
                        // have to look down. Same confirm dialog + moveToTrash
                        // path is reused (state hoisted via showDeleteDialog).
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showOverflowMenu = false
                                showDeleteDialog = true
                            }
                        )
                    }
                }

                // Bottom action bar — primary actions only (Share / Edit /
                // Delete). Less-frequent actions (Find similar, Share with
                // face blur, Details) live in the top-right overflow menu.
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 40.dp, start = 24.dp, end = 24.dp)
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(28.dp)).padding(horizontal = 32.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share (respects global face-blur setting)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { viewerPhoto?.let { sharePhoto(it) } },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Share, stringResource(R.string.share), tint = Color(0xFF333333), modifier = Modifier.size(28.dp))
                        }
                        Text(stringResource(R.string.share), color = Color(0xFF333333), fontSize = 10.sp)
                    }
                    // Edit (photos only)
                    if (viewerPhoto?.mediaType == VaultMediaType.PHOTO) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    viewerPhoto?.let { photo ->
                                        viewerBitmap?.let { bmp ->
                                            showEditor = true
                                            editorPhoto = photo
                                            editorBitmap = bmp
                                        }
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Edit, stringResource(R.string.edit), tint = Color(0xFF333333), modifier = Modifier.size(28.dp))
                            }
                            Text(stringResource(R.string.edit), color = Color(0xFF333333), fontSize = 10.sp)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = Color(0xFFD32F2F), modifier = Modifier.size(28.dp))
                        }
                        Text(stringResource(R.string.delete), color = Color(0xFFD32F2F), fontSize = 10.sp)
                    }
                }

                // AI labels (above action bar, pass-through touches)
                viewerPhoto?.let { vp ->
                    // Vision enabled (2026-05-13). Unblocked by adding
                    // visionBackend = Backend.GPU() to EngineConfig in GemmaRunner —
                    // see comment there. The prior SIGSEGV was a missing API parameter,
                    // not a runtime or model issue. Tested on Pixel 9a / Tensor G4 with
                    // LiteRT-LM 0.11.0 final + April-2026 gemma-4-E2B-it model.
                    // AI state + functions are hoisted above the overflow menu
                    // (see lines ~3019-3120). Only the on-image overlay rendering
                    // lives here, gated by the Settings "Show AI labels" toggle.

                    // AI description text — anchored ABOVE the image (just below
                    // the top overflow menu / status bar) instead of at the
                    // bottom over the tags. Gives the description its own
                    // room to breathe and stops it competing with the chip
                    // row for screen real estate.
                    if (showAiLabels && (aiDescription.isNotEmpty() || aiDescLoading)) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 96.dp, start = 64.dp, end = 64.dp)
                        ) {
                            if (aiDescription.isNotEmpty()) {
                                Text(
                                    aiDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            } else {
                                Text(
                                    "Describing image…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp, start = 16.dp, end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (showAiLabels) {
                            // Description moved to the TopCenter overlay above.

                            // AI action chips moved to the photo viewer's 3-dot
                            // overflow menu so the photo isn't covered by chips.

                            // Label chips (fed from hoisted state so they refresh after
                            // a Gemma tag generation without re-entering the screen).
                            if (aiLabels.isNotEmpty()) {
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    aiLabels.forEach { (label, score) ->
                                        val pct = (score * 100).toInt()
                                        SuggestionChip(onClick = {
                                            searchQuery = label
                                            searchFromViewer = true
                                            scope.launch {
                                                val allItems = withContext(Dispatchers.IO) { getAllVaultItems() }
                                                val labelMatches = photoIndex?.searchByLabel(label)?.toSet() ?: emptySet()
                                                searchResults = allItems.filter { it.id in labelMatches }
                                                isSearching = true
                                                smartMode = null
                                                page = VaultPage.CATEGORIES
                                                val thumbMap = mutableMapOf<String, Bitmap>()
                                                withContext(Dispatchers.IO) {
                                                    searchResults.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } }
                                                }
                                                searchThumbnails = thumbMap
                                            }
                                        }, label = { Text("$label $pct%", style = MaterialTheme.typography.labelSmall) })
                                    }
                                }
                            }
                        }
                    }

                    // Ask about image dialog
                    if (showAskDialog) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showAskDialog = false; askQuestion = ""; askAnswer = null },
                            title = { Text(stringResource(R.string.vault_ask_about_image)) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    androidx.compose.material3.OutlinedTextField(
                                        value = askQuestion,
                                        onValueChange = { askQuestion = it },
                                        label = { Text(stringResource(R.string.vault_ask_question_label)) },
                                        placeholder = { Text(stringResource(R.string.vault_ask_question_placeholder)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        enabled = !askLoading
                                    )
                                    if (askLoading) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Text(stringResource(R.string.vault_ask_analyzing), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    if (askAnswer != null) {
                                        // Capped + scrollable so a long Gemma answer
                                        // doesn't push the dialog buttons off-screen.
                                        Text(
                                            askAnswer!!,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                                .padding(12.dp)
                                                .fillMaxWidth()
                                                .heightIn(max = 260.dp)
                                                .verticalScroll(rememberScrollState())
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        if (askQuestion.isNotBlank()) {
                                            askLoading = true
                                            scope.launch {
                                                val bmp = withContext(Dispatchers.IO) { vault.loadFullPhoto(vp) }
                                                if (bmp != null) {
                                                    val tempFile = java.io.File(context.cacheDir, "ask_${vp.id}.jpg")
                                                    withContext(Dispatchers.IO) {
                                                        java.io.FileOutputStream(tempFile).use { out ->
                                                            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                                                        }
                                                    }
                                                    val prompt = com.privateai.camera.bridge.GemmaPrompts.askAboutImage(askQuestion)
                                                    val answer = com.privateai.camera.bridge.GemmaRunner.describeImage(
                                                        context, tempFile.absolutePath, prompt
                                                    )
                                                    tempFile.delete()
                                                    askAnswer = answer ?: context.getString(R.string.vault_ask_failed)
                                                } else {
                                                    askAnswer = context.getString(R.string.vault_ask_load_failed)
                                                }
                                                askLoading = false
                                            }
                                        }
                                    },
                                    enabled = askQuestion.isNotBlank() && !askLoading
                                ) { Text(stringResource(R.string.vault_ask_submit)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAskDialog = false; askQuestion = ""; askAnswer = null }) {
                                    Text(stringResource(R.string.action_close))
                                }
                            }
                        )
                    }
                }
            }
        }

        VaultPage.PDF_VIEWER -> {
            val file = pdfTempFile
            if (file != null) {
                // AI actions are surfaced only when the doc has an OCR sidecar.
                // For freshly-scanned docs the sidecar is written at save-time.
                // For imported PDFs (Wi-Fi transfer, share-to-vault, anything
                // pre-existing) the user can trigger one-shot extraction from
                // the overflow menu — hybrid PdfBox (native text layer) +
                // ML Kit OCR (image fallback). Refresh the trigger when the
                // sidecar state changes so the menu items rotate.
                val viewerDoc = viewerPhoto
                var ocrRefresh by remember { mutableStateOf(0) }
                val docHasOcr = remember(viewerDoc?.id, ocrRefresh) {
                    viewerDoc != null && vault.hasOcr(viewerDoc)
                }
                var extractionProgress by remember(viewerDoc?.id) {
                    mutableStateOf<Pair<Int, Int>?>(null)
                }
                val seedAsk = if (docHasOcr && onNavigate != null) {
                    {
                        // The doc name now lives in the attached-doc chip at
                        // the top of the chat (always visible), so the seed
                        // prompts are clean complete questions — sending them
                        // as-is gives a sensible default instead of "scan_X"
                        // being mistakenly parsed as a literal search query.
                        val id = viewerDoc!!.id
                        val seed = context.getString(R.string.assistant_seed_ask)
                        onNavigate(
                            "assistant?seed=${android.net.Uri.encode(seed)}" +
                                "&docId=${android.net.Uri.encode(id)}"
                        )
                    }
                } else null
                val seedSummarize = if (docHasOcr && onNavigate != null) {
                    {
                        val id = viewerDoc!!.id
                        val seed = context.getString(R.string.assistant_seed_summarize)
                        onNavigate(
                            "assistant?seed=${android.net.Uri.encode(seed)}" +
                                "&docId=${android.net.Uri.encode(id)}"
                        )
                    }
                } else null
                val viewOcr = if (docHasOcr) {
                    { extractedTextDialog = vault.loadOcr(viewerDoc!!) ?: "" }
                } else null
                val rename: (String) -> Unit = { newName ->
                    val current = viewerPhoto
                    if (current != null) {
                        val result = vault.renameItem(current, newName)
                        when (result) {
                            is com.privateai.camera.security.VaultRepository.RenameResult.Success -> {
                                viewerPhoto = result.updated
                                pdfTitle = result.updated.id
                                // Refresh the gallery list so the rename is
                                // visible the next time the user backs out.
                                photos = photos.map { if (it.id == current.id) result.updated else it }
                                Toast.makeText(context, context.getString(R.string.vault_rename_done), Toast.LENGTH_SHORT).show()
                            }
                            com.privateai.camera.security.VaultRepository.RenameResult.NameAlreadyExists ->
                                Toast.makeText(context, context.getString(R.string.vault_rename_collision), Toast.LENGTH_SHORT).show()
                            com.privateai.camera.security.VaultRepository.RenameResult.InvalidName ->
                                Toast.makeText(context, context.getString(R.string.vault_rename_invalid), Toast.LENGTH_SHORT).show()
                            com.privateai.camera.security.VaultRepository.RenameResult.Failed ->
                                Toast.makeText(context, context.getString(R.string.vault_rename_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                PdfViewerScreen(
                    pdfFile = file,
                    title = pdfTitle,
                    onBack = {
                        pdfTempFile?.delete()
                        pdfTempFile = null
                        pdfTitle = ""
                        page = VaultPage.GALLERY
                    },
                    onAskAssistant = seedAsk,
                    onSummarize = seedSummarize,
                    onViewExtractedText = viewOcr,
                    onRename = rename,
                    // Only offer the extraction action when no sidecar exists
                    // (i.e. the doc came from outside the scanner). After
                    // success the rerun increments ocrRefresh which flips
                    // docHasOcr to true and the menu rotates to Summarize /
                    // Ask the Assistant.
                    onDelete = { showDeleteDialog = true },
                    onExtractText = if (!docHasOcr && viewerDoc != null) {
                        {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    vault.extractOcrForPdf(viewerDoc) { current, total ->
                                        extractionProgress = current to total
                                    }
                                }
                                extractionProgress = null
                                when (result) {
                                    is com.privateai.camera.security.VaultRepository.OcrExtractionResult.Success -> {
                                        ocrRefresh++
                                        val msg = if (result.viaOcr)
                                            context.getString(R.string.assistant_extract_ok_ocr, result.pageCount)
                                        else
                                            context.getString(R.string.assistant_extract_ok_text, result.charCount)
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                    com.privateai.camera.security.VaultRepository.OcrExtractionResult.NoTextFound ->
                                        Toast.makeText(context, context.getString(R.string.assistant_extract_empty), Toast.LENGTH_LONG).show()
                                    com.privateai.camera.security.VaultRepository.OcrExtractionResult.Failed ->
                                        Toast.makeText(context, context.getString(R.string.assistant_extract_failed), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } else null,
                    extractionProgress = extractionProgress
                )
            } else {
                page = VaultPage.GALLERY
            }
        }

        VaultPage.VIDEO_PLAYER -> {
            val viewablePhotos = remember(photos) { photos.filter { it.mediaType != VaultMediaType.PDF } }
            val currentVideoIndex = viewablePhotos.indexOfFirst { it.id == viewerPhoto?.id }

            fun navigateVideo(index: Int) {
                val item = viewablePhotos.getOrNull(index) ?: return
                videoTempFile?.delete()
                videoTempFile = null
                if (item.mediaType == VaultMediaType.VIDEO) {
                    scope.launch {
                        val tempFile = withContext(Dispatchers.IO) { vault.decryptVideoToTempFile(item) }
                        if (tempFile != null) {
                            videoTempFile = tempFile
                            viewerPhoto = item
                        }
                    }
                } else {
                    // Switch to photo viewer
                    scope.launch {
                        val bmp = withContext(Dispatchers.IO) { vault.loadFullPhoto(item) }
                        viewerBitmap = bmp
                        viewerPhoto = item
                        page = VaultPage.VIEWER
                    }
                }
            }

            Box(Modifier.fillMaxSize().background(Color.Black)) {
                videoTempFile?.let { file ->
                    com.privateai.camera.ui.camera.VideoPlayerWithControls(
                        videoFile = file,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Back button
                IconButton(
                    onClick = {
                        videoTempFile?.delete()
                        videoTempFile = null
                        page = VaultPage.GALLERY
                    },
                    Modifier.align(Alignment.TopStart).padding(top = 48.dp, start = 16.dp)
                        .size(40.dp).background(Color.White.copy(alpha = 0.9f), CircleShape)
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color(0xFF333333)) }

                // Top-center: datetime + counter (matches photo viewer).
                viewerPhoto?.let { vp ->
                    val dateLabel = remember(vp.id) {
                        SimpleDateFormat("MMM d, yyyy · HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(vp.timestamp))
                    }
                    Column(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 54.dp)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(dateLabel, color = Color(0xFF333333), fontSize = 12.sp)
                        if (viewablePhotos.size > 1 && currentVideoIndex >= 0) {
                            Text(
                                "${currentVideoIndex + 1} / ${viewablePhotos.size}",
                                color = Color(0xFF555555),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Star button — top-right of the video viewer.
                viewerPhoto?.let { vp ->
                    var isStarred by remember(vp.id) { mutableStateOf(vault.isStarred(vp.id)) }
                    IconButton(
                        onClick = {
                            val next = !isStarred
                            vault.setStarred(vp.id, next)
                            isStarred = next
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 52.dp, end = 16.dp)
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.9f), CircleShape)
                    ) {
                        Icon(
                            if (isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
                            stringResource(if (isStarred) R.string.action_unstar else R.string.action_star),
                            tint = if (isStarred) Color(0xFFFFC107) else Color(0xFF333333),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Prev/Next arrows on sides
                if (currentVideoIndex > 0) {
                    IconButton(
                        onClick = { navigateVideo(currentVideoIndex - 1) },
                        Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
                            .size(40.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.previous), tint = Color.White) }
                }
                if (currentVideoIndex < viewablePhotos.size - 1) {
                    IconButton(
                        onClick = { navigateVideo(currentVideoIndex + 1) },
                        Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                            .size(40.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.next), tint = Color.White,
                            modifier = Modifier.graphicsLayer(scaleX = -1f)
                        )
                    }
                }

                // Bottom bar: share + save + info + delete
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(bottom = 40.dp, start = 24.dp, end = 24.dp)
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(28.dp))
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share video
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                viewerPhoto?.let { photo ->
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            val tempFile = vault.decryptVideoToTempFile(photo) ?: return@withContext
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                                            withContext(Dispatchers.Main) {
                                                context.startActivity(Intent.createChooser(
                                                    Intent(Intent.ACTION_SEND).apply {
                                                        type = "video/mp4"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }, context.getString(R.string.share_video)
                                                ))
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Share, stringResource(R.string.share), tint = Color(0xFF333333), modifier = Modifier.size(28.dp))
                        }
                        Text(stringResource(R.string.share), color = Color(0xFF333333), fontSize = 10.sp)
                    }
                    // Save to device
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                viewerPhoto?.let { photo ->
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            try {
                                                val bytes = vault.loadFile(photo.encryptedFile) ?: return@withContext
                                                val filename = "vault_${photo.id}.mp4"
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                    val values = ContentValues().apply {
                                                        put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                                                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/PrivateAICamera")
                                                    }
                                                    val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                                                    uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
                                                }
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, context.getString(R.string.video_saved_to_gallery), Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (_: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, context.getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.SaveAlt, stringResource(R.string.save_to_device), tint = Color(0xFF333333), modifier = Modifier.size(28.dp))
                        }
                        Text(stringResource(R.string.save_to_device), color = Color(0xFF333333), fontSize = 10.sp)
                    }
                    // Details
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { showDetailsDialog = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Info, stringResource(R.string.details), tint = Color(0xFF333333), modifier = Modifier.size(28.dp))
                        }
                        Text(stringResource(R.string.details), color = Color(0xFF333333), fontSize = 10.sp)
                    }
                    // Delete
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = Color(0xFFD32F2F), modifier = Modifier.size(28.dp))
                        }
                        Text(stringResource(R.string.delete), color = Color(0xFFD32F2F), fontSize = 10.sp)
                    }
                }
            }
        }

        VaultPage.FOLDER_VIEW -> {
            val folder = currentFolder
            if (folder == null) { page = VaultPage.CATEGORIES } else {
            val breadcrumb = remember(folder.id) { folderManager.getFolderPath(folder.id) }

            Scaffold(topBar = {
                if (isSelectionMode) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.n_selected, selectedIds.size)) },
                        navigationIcon = { IconButton(onClick = { selectedIds = emptySet(); isSelectionMode = false }) { Icon(Icons.Default.Close, stringResource(R.string.cancel)) } },
                        actions = {
                            IconButton(onClick = { showMoveDialog = true }) { Icon(Icons.Default.DriveFileMove, stringResource(R.string.move)) }
                            IconButton(onClick = { shareImages(selectedIds) }) { Icon(Icons.Default.Share, stringResource(R.string.share)) }
                            IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                        }
                    )
                } else {
                TopAppBar(
                    title = { Text(folder.name) },
                    navigationIcon = {
                        IconButton(onClick = {
                            thumbnails = emptyMap()
                            if (folder.parentId != null) {
                                // Go up to parent folder
                                val parent = folderManager.getFolder(folder.parentId)
                                if (parent != null) {
                                    currentFolder = parent
                                    val dir = folderManager.getFolderDir(parent.id)
                                    photos = if (isDuressActive) emptyList() else vault.listFolderItems(dir)
                                    subfolders = if (isDuressActive) emptyList() else folderManager.listSubfolders(parent.id)
                                    scope.launch {
                                        val thumbMap = mutableMapOf<String, Bitmap>()
                                        withContext(Dispatchers.IO) { photos.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } } }
                                        thumbnails = thumbMap
                                    }
                                } else {
                                    currentFolder = null
                                    rootFolders = folderManager.listRootFolders()
                                    page = VaultPage.CATEGORIES
                                }
                            } else {
                                currentFolder = null
                                rootFolders = folderManager.listRootFolders()
                                page = VaultPage.CATEGORIES
                            }
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    },
                    actions = {
                        IconButton(onClick = {
                            importLauncher.launch(arrayOf("image/*", "video/*", "application/pdf"))
                        }) { Icon(Icons.Default.Add, stringResource(R.string.import_to_folder)) }
                        IconButton(onClick = { showCreateFolderDialog = true }) { Icon(Icons.Default.CreateNewFolder, stringResource(R.string.new_subfolder)) }
                        IconButton(onClick = { showRenameFolderDialog = true }) { Icon(Icons.Default.Edit, stringResource(R.string.rename)) }
                        IconButton(onClick = { showDeleteFolderDialog = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                        // Overflow menu — Select all (first action). Hidden
                        // when there's nothing in the folder to select.
                        if (!isDuressActive && photos.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(Icons.Default.MoreVert, stringResource(R.string.action_more))
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_select_all)) },
                                        leadingIcon = { Icon(Icons.Default.SelectAll, null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            selectedIds = photos.map { it.id }.toSet()
                                            isSelectionMode = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
                } // end else (not selection mode)
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    // Breadcrumb
                    if (breadcrumb.size > 1) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            breadcrumb.forEachIndexed { i, f ->
                                if (i > 0) Text(" / ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Text(
                                    f.name,
                                    color = if (f.id == folder.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable {
                                        if (f.id != folder.id) {
                                            currentFolder = f
                                            val dir = folderManager.getFolderDir(f.id)
                                            photos = if (isDuressActive) emptyList() else vault.listFolderItems(dir)
                                            subfolders = if (isDuressActive) emptyList() else folderManager.listSubfolders(f.id)
                                            scope.launch {
                                                val thumbMap = mutableMapOf<String, Bitmap>()
                                                withContext(Dispatchers.IO) { photos.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } } }
                                                thumbnails = thumbMap
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Subfolders (compact 2-column grid)
                    if (subfolders.isNotEmpty()) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val halfW = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 40.dp) / 2
                            subfolders.forEach { sub ->
                                val itemCount = folderManager.countItems(sub.id)
                                CompactCategoryCard(sub.name, itemCount, Icons.Default.Folder, halfW) {
                                    currentFolder = sub
                                    val dir = folderManager.getFolderDir(sub.id)
                                    photos = vault.listFolderItems(dir)
                                    subfolders = folderManager.listSubfolders(sub.id)
                                    thumbnails = emptyMap()
                                    scope.launch {
                                        val thumbMap = mutableMapOf<String, Bitmap>()
                                        withContext(Dispatchers.IO) { photos.forEach { p -> vault.loadThumbnail(p)?.let { thumbMap[p.id] = it } } }
                                        thumbnails = thumbMap
                                    }
                                }
                            }
                        }
                    }

                    // Items in this folder (same grid as GALLERY)
                    if (photos.isEmpty() && subfolders.isEmpty()) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(stringResource(R.string.empty_folder), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (photos.isNotEmpty()) {
                        val folderSorted = remember(photos, sortMode, updatedTimes) { sortPhotos(photos) }
                        val grouped = remember(folderSorted) { groupPhotosByDate(folderSorted) }
                        val folderGridW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 16.dp
                        LazyColumn(contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            grouped.forEach { (header, groupPhotos) ->
                                item {
                                    Text(header, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                                groupPhotos.chunked(3).forEach { row ->
                                    item {
                                        val totalAspect = row.sumOf {
                                            val t = thumbnails[it.id]
                                            (if (t != null && t.height > 0) t.width.toFloat() / t.height else if (it.mediaType == VaultMediaType.PDF) 0.75f else 1.33f).toDouble()
                                        }.toFloat()
                                        val gaps = (row.size - 1) * 3f
                                        val rowH = ((folderGridW.value - gaps) / totalAspect).coerceIn(80f, 160f)
                                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                                            row.forEach { photo ->
                                                val thumb = thumbnails[photo.id]
                                                val aspect = if (thumb != null && thumb.height > 0) thumb.width.toFloat() / thumb.height else if (photo.mediaType == VaultMediaType.PDF) 0.75f else 1.33f
                                                val isSelected = photo.id in selectedIds
                                                @OptIn(ExperimentalFoundationApi::class)
                                                Box(Modifier.width((rowH * aspect).dp).height(rowH.dp).clip(RoundedCornerShape(4.dp))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (isSelectionMode) {
                                                                selectedIds = if (isSelected) selectedIds - photo.id else selectedIds + photo.id
                                                                if (selectedIds.isEmpty()) isSelectionMode = false
                                                            } else {
                                                                viewerFromFolder = true; openViewer(photo)
                                                            }
                                                        },
                                                        onLongClick = { isSelectionMode = true; selectedIds = setOf(photo.id) }
                                                    ), contentAlignment = Alignment.Center) {
                                                    if (photo.mediaType == VaultMediaType.FILE) {
                                                        // File display: icon + name + size + type
                                                        val ext = photo.id.substringAfterLast('.', "").lowercase()
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                                                            Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                                            Text(photo.id.let { if (it.length > 18) it.take(15) + "…" else it }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                                            val sizeKB = photo.encryptedFile.length() / 1024
                                                            Text(if (sizeKB > 1024) "${"%.1f".format(sizeKB / 1024.0)} MB" else "$sizeKB KB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                                            if (ext.isNotBlank()) Text(ext.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                                        }
                                                    } else if (photo.mediaType == VaultMediaType.PDF) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                                                            Icon(Icons.Default.PictureAsPdf, stringResource(R.string.cd_pdf_document), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                                                            Text(photo.id.let { if (it.length > 18) it.take(15) + "…" else it }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                                            val sizeKB = photo.encryptedFile.length() / 1024
                                                            Text(if (sizeKB > 1024) "${"%.1f".format(sizeKB / 1024.0)} MB" else "$sizeKB KB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                                        }
                                                    } else if (thumb != null) {
                                                        Image(thumb.asImageBitmap(), if (photo.mediaType == VaultMediaType.VIDEO) stringResource(R.string.cd_video_thumbnail) else stringResource(R.string.cd_photo_thumbnail), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                    } else {
                                                        Icon(Icons.Default.Lock, stringResource(R.string.cd_encrypted_item), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    if (photo.mediaType == VaultMediaType.VIDEO) {
                                                        Icon(Icons.Default.PlayCircleFilled, stringResource(R.string.video), tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(32.dp).align(Alignment.Center))
                                                    }
                                                    if (isSelectionMode && isSelected) {
                                                        Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Color.White)
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
            }
        }
        } // end else (folder != null)

        VaultPage.TRASH -> {
            Scaffold(topBar = {
                TopAppBar(
                    title = { Text("Trash (${trashItems.size})") },
                    navigationIcon = {
                        IconButton(onClick = { page = VaultPage.CATEGORIES; trashCount = vault.trashCount() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (isSelectionMode && selectedIds.isNotEmpty()) {
                            // Restore selected
                            IconButton(onClick = {
                                val restoredIds = selectedIds.toSet()
                                selectedIds.forEach { vault.restoreFromTrash(it) }
                                trashItems = vault.listTrash()
                                categoryCounts = vault.countByCategory(); trashCount = vault.trashCount()
                                selectedIds = emptySet(); isSelectionMode = false
                                Toast.makeText(context, "Restored", Toast.LENGTH_SHORT).show()
                                // Re-index restored photos in background
                                val pi = photoIndex; val cl = classifier
                                if (pi != null && cl != null) {
                                    scope.launch(Dispatchers.IO) {
                                        val fe = try { FaceEmbedder(context) } catch (_: Exception) { null }
                                        val allPhotos = getAllVaultItems().filter { it.id in restoredIds && it.mediaType == VaultMediaType.PHOTO }
                                        allPhotos.forEach { photo ->
                                            if (!pi.isIndexed(photo.id)) {
                                                val bmp = try {
                                                    if (photo.encryptedFile.length() > 10 * 1024 * 1024) vault.loadThumbnail(photo)
                                                    else vault.loadFullPhoto(photo) ?: vault.loadThumbnail(photo)
                                                } catch (_: OutOfMemoryError) { vault.loadThumbnail(photo) }
                                                bmp?.let { img -> try { pi.indexPhoto(photo.id, img, cl, faceEmbedder = fe, detector = objectDetector) } catch (_: Exception) {}; if (!img.isRecycled) img.recycle() }
                                            }
                                        }
                                        fe?.release()
                                    }
                                }
                            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Restore") }
                            // Permanent delete selected
                            IconButton(onClick = {
                                selectedIds.forEach { vault.permanentDeleteFromTrash(it) }
                                trashItems = vault.listTrash()
                                trashCount = vault.trashCount()
                                selectedIds = emptySet(); isSelectionMode = false
                                Toast.makeText(context, "Permanently deleted", Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Default.Delete, "Delete forever", tint = MaterialTheme.colorScheme.error) }
                            IconButton(onClick = { selectedIds = emptySet(); isSelectionMode = false }) { Icon(Icons.Default.Close, stringResource(R.string.action_cancel)) }
                        } else if (trashItems.isNotEmpty()) {
                            var showEmptyTrashConfirm by remember { mutableStateOf(false) }
                            TextButton(onClick = { showEmptyTrashConfirm = true }) {
                                Text("Empty Trash", color = MaterialTheme.colorScheme.error)
                            }
                            if (showEmptyTrashConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showEmptyTrashConfirm = false },
                                    title = { Text(stringResource(R.string.empty_trash_title)) },
                                    text = { Text(stringResource(R.string.empty_trash_message, trashItems.size)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showEmptyTrashConfirm = false
                                            vault.emptyTrash()
                                            trashItems = emptyList()
                                            trashThumbnails = emptyMap()
                                            trashCount = 0
                                            Toast.makeText(context, "Trash emptied", Toast.LENGTH_SHORT).show()
                                            page = VaultPage.CATEGORIES
                                        }) { Text(stringResource(R.string.empty_trash_confirm), color = MaterialTheme.colorScheme.error) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showEmptyTrashConfirm = false }) { Text(stringResource(R.string.cancel)) }
                                    }
                                )
                            }
                        }
                    }
                )
            }) { padding ->
                if (trashItems.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Delete, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text("Trash is empty", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val dateFmt = remember { java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()) }
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        trashItems.forEach { item ->
                            val isSelected = item.id in selectedIds
                            val thumb = trashThumbnails[item.id]
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                                if (selectedIds.isEmpty()) isSelectionMode = false
                                            }
                                        },
                                        onLongClick = { isSelectionMode = true; selectedIds = selectedIds + item.id }
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Thumbnail
                                Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                    if (thumb != null) {
                                        Image(thumb.asImageBitmap(), "Trashed", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Icon(if (item.mediaType == VaultMediaType.PDF) Icons.Default.PictureAsPdf else Icons.Default.Photo, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (item.mediaType == VaultMediaType.VIDEO) {
                                        Icon(Icons.Default.PlayCircleFilled, null, Modifier.size(24.dp), tint = Color.White.copy(alpha = 0.8f))
                                    }
                                }
                                // Info
                                Column(Modifier.weight(1f)) {
                                    Text(item.mediaType.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                                    Text("From: ${item.originalCategory.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Deleted: ${dateFmt.format(java.util.Date(item.trashedAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                // Selection check
                                if (isSelectionMode && isSelected) {
                                    Icon(Icons.Default.CheckCircle, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }

        VaultPage.WIFI_TRANSFER -> {
            WifiTransferScreen(onBack = { page = VaultPage.CATEGORIES })
        }
    }

    // Photo editor overlay
    val editPhoto = editorPhoto
    val editBmp = editorBitmap
    if (showEditor && editPhoto != null && editBmp != null) {
        com.privateai.camera.ui.camera.PhotoEditorScreen(
            photo = editPhoto,
            initialBitmap = editBmp,
            vault = vault,
            // After a successful save, stamp the photo's `updated_at` in
            // photo_index so the Sort menu's "Updated date" modes find it.
            onSaved = { editedId ->
                scope.launch(Dispatchers.IO) {
                    try { photoIndex?.markUpdated(editedId) } catch (_: Exception) {}
                }
            },
            onDone = {
                showEditor = false
                // Reload the photo + its thumbnail after edit so both the
                // viewer and the gallery grid reflect rotation / filter /
                // crop changes. Without the thumb refresh, the gallery kept
                // showing the pre-edit cached bitmap from `thumbnails`.
                editorPhoto?.let { photo ->
                    scope.launch {
                        val (full, thumb) = withContext(Dispatchers.IO) {
                            vault.loadFullPhoto(photo) to vault.loadThumbnail(photo)
                        }
                        viewerBitmap = full
                        if (thumb != null) {
                            thumbnails = thumbnails.toMutableMap().apply { put(photo.id, thumb) }
                            // search results grid shares a parallel cache
                            searchThumbnails = searchThumbnails.toMutableMap().apply { put(photo.id, thumb) }
                        }
                    }
                }
                editorPhoto = null
                editorBitmap = null
            }
        )
    }
}

private fun groupPhotosByDate(photos: List<VaultPhoto>): List<Pair<String, List<VaultPhoto>>> {
    val now = Calendar.getInstance()
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val weekAgo = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }
    val monthAgo = (today.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
    val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
    val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    val groups = linkedMapOf<String, MutableList<VaultPhoto>>()

    for (photo in photos) {
        val photoTime = Calendar.getInstance().apply { timeInMillis = photo.timestamp }
        val label = when {
            photoTime >= today -> "Today"
            photoTime >= yesterday -> "Yesterday"
            photoTime >= weekAgo -> dayFormat.format(Date(photo.timestamp))
            photoTime >= monthAgo -> "This Month"
            else -> monthYearFormat.format(Date(photo.timestamp))
        }
        groups.getOrPut(label) { mutableListOf() }.add(photo)
    }

    return groups.map { (k, v) -> k to v.toList() }
}

@Composable
private fun CategoryCard(
    label: String, count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, label, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.n_items_count, count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CompactCategoryCard(
    label: String, count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.size(width, 76.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, label, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                Text("$count items", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Format GPS coordinates as `48.8520°N, 2.3500°E`. Locale-neutral — uses
 * `Locale.US` for the number format so European decimals (`48,8520`) don't
 * appear in a row that's read as compass coordinates.
 */
private fun formatGps(lat: Double, lng: Double): String {
    val latHemi = if (lat >= 0) "N" else "S"
    val lngHemi = if (lng >= 0) "E" else "W"
    return "%.4f°%s, %.4f°%s".format(java.util.Locale.US, kotlin.math.abs(lat), latHemi, kotlin.math.abs(lng), lngHemi)
}
