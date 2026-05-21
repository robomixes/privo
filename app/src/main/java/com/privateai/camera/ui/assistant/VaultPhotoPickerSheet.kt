// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.assistant

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.privateai.camera.R
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.FolderManager
import com.privateai.camera.security.VaultMediaType
import com.privateai.camera.security.VaultPhoto
import com.privateai.camera.security.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Bottom-sheet picker for vault content. Lists recent vault photos AND PDFs.
 * Tapping a photo decrypts to a temp JPEG and calls [onPicked]; tapping a
 * PDF calls [onPdfPicked] with the PDF's id (handled separately because
 * PDFs go through the existing `attachedDocId` / OCR flow, not vision).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultPhotoPickerSheet(
    onPicked: (Uri) -> Unit,
    onPdfPicked: (String) -> Unit = {},
    onDismiss: () -> Unit,
    mediaTypeFilter: VaultMediaType? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // photosByFolder maps folder name → list of items. Special key "" is the
    // top-level Scan / Camera / etc. categories (everything not in a custom
    // folder). The user picks which to view via the chip row at top.
    var photosByFolder by remember { mutableStateOf<Map<String, List<VaultPhoto>>>(emptyMap()) }
    var folderNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }  // null = All
    var thumbs by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var decrypting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val crypto = CryptoManager(context).also { it.initialize() }
                if (!crypto.isUnlocked()) {
                    withContext(Dispatchers.Main) { loading = false }
                    return@withContext
                }
                val vault = VaultRepository(context, crypto)
                val folderManager = FolderManager(context, crypto)
                val map = mutableMapOf<String, List<VaultPhoto>>()
                // "" = top-level categories (Camera / Scans / Detect / Reports / Files / Hidden)
                // When [mediaTypeFilter] is non-null we narrow to that type so
                // the 4-way attach menu (Photo from vault / PDF from vault)
                // doesn't mix the two kinds in one grid.
                fun keep(p: VaultPhoto): Boolean =
                    if (mediaTypeFilter != null) p.mediaType == mediaTypeFilter
                    else p.mediaType == VaultMediaType.PHOTO || p.mediaType == VaultMediaType.PDF
                val fromCats = vault.listAllPhotos()
                    .filter(::keep)
                    .sortedByDescending { it.timestamp }
                map[""] = fromCats
                // Each custom folder gets its own bucket.
                val folders = folderManager.listAllFolders().sortedBy { it.name }
                for (f in folders) {
                    val items = vault.listFolderItems(folderManager.getFolderDir(f.id))
                        .filter(::keep)
                        .sortedByDescending { it.timestamp }
                    if (items.isNotEmpty()) map[f.name] = items
                }
                withContext(Dispatchers.Main) {
                    photosByFolder = map
                    folderNames = map.keys.filter { it.isNotEmpty() }.sorted()
                    loading = false
                }
                // Lazy-load thumbnails for everything visible across folders.
                val thumbMap = HashMap<String, Bitmap>()
                for ((_, items) in map) {
                    for (p in items) {
                        if (thumbMap.containsKey(p.id)) continue
                        val bmp = try { vault.loadThumbnail(p) } catch (_: Exception) { null }
                        if (bmp != null) {
                            thumbMap[p.id] = bmp
                            withContext(Dispatchers.Main) { thumbs = thumbMap.toMap() }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("VaultPhotoPickerSheet", "load failed: ${e.message}")
                withContext(Dispatchers.Main) { loading = false }
            }
        }
    }

    // The grid renders whatever bucket the user has selected. null = combine
    // all (top-level categories first, then folder contents) so "All" gives
    // a chronological view across the whole vault.
    val visiblePhotos = remember(selectedFolder, photosByFolder) {
        when (val sel = selectedFolder) {
            null -> photosByFolder.values.flatten().distinctBy { it.id }
                .sortedByDescending { it.timestamp }
            else -> photosByFolder[sel] ?: emptyList()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                stringResource(R.string.assistant_vault_picker_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            // Folder filter chips — "All" first, then "Categories" (top-level
            // Camera / Scans / etc.), then each custom folder with content.
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                androidx.compose.material3.FilterChip(
                    selected = selectedFolder == null,
                    onClick = { selectedFolder = null },
                    label = { Text(stringResource(R.string.assistant_vault_picker_all)) }
                )
                if (photosByFolder[""].orEmpty().isNotEmpty()) {
                    androidx.compose.material3.FilterChip(
                        selected = selectedFolder == "",
                        onClick = { selectedFolder = "" },
                        label = { Text(stringResource(R.string.assistant_vault_picker_categories)) }
                    )
                }
                folderNames.forEach { name ->
                    androidx.compose.material3.FilterChip(
                        selected = selectedFolder == name,
                        onClick = { selectedFolder = name },
                        label = { Text(name, maxLines = 1) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                loading -> {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                visiblePhotos.isEmpty() -> {
                    Text(
                        stringResource(R.string.assistant_vault_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().height(400.dp)
                    ) {
                        items(visiblePhotos, key = { it.id }) { photo ->
                            val isPdf = photo.mediaType == VaultMediaType.PDF
                            val thumb = thumbs[photo.id]
                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(enabled = !decrypting) {
                                        if (isPdf) {
                                            // PDFs go through the existing
                                            // `attachedDocId` / OCR flow.
                                            // The Assistant can ONLY ground in
                                            // a PDF that has an `.ocr.enc`
                                            // sidecar — otherwise loadOcr
                                            // returns null and the model
                                            // replies "I can't find the doc."
                                            // If the sidecar is missing
                                            // (typical for imported PDFs),
                                            // run extractOcrForPdf inline
                                            // first, then attach.
                                            decrypting = true
                                            scope.launch {
                                                val ok = withContext(Dispatchers.IO) {
                                                    val crypto = CryptoManager(context).also { it.initialize() }
                                                    if (!crypto.isUnlocked()) return@withContext false
                                                    val vault = VaultRepository(context, crypto)
                                                    if (vault.hasOcr(photo)) return@withContext true
                                                    // Run extraction. Best-effort —
                                                    // failure flips back to "no
                                                    // OCR" and we surface a toast.
                                                    val result = try {
                                                        vault.extractOcrForPdf(photo)
                                                    } catch (e: Exception) {
                                                        Log.w("VaultPhotoPickerSheet", "extractOcr failed: ${e.message}")
                                                        null
                                                    }
                                                    result is com.privateai.camera.security.VaultRepository.OcrExtractionResult.Success
                                                }
                                                decrypting = false
                                                if (ok) {
                                                    onPdfPicked(photo.id)
                                                } else {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        context.getString(R.string.assistant_vault_picker_pdf_no_text),
                                                        android.widget.Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        } else {
                                            decrypting = true
                                            scope.launch {
                                                val uri = withContext(Dispatchers.IO) {
                                                    decryptToTempUri(context, photo)
                                                }
                                                decrypting = false
                                                if (uri != null) onPicked(uri)
                                            }
                                        }
                                    }
                            ) {
                                if (thumb != null) {
                                    Image(
                                        bitmap = thumb.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                if (isPdf) {
                                    // PDF: badge top-left + filename strip at
                                    // the bottom so the user can identify the
                                    // document. id already includes ".pdf";
                                    // strip it for display.
                                    Box(
                                        Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "PDF",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                                            .padding(horizontal = 4.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            photo.id.removeSuffix(".pdf").removeSuffix(".PDF"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = androidx.compose.ui.graphics.Color.White,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (decrypting) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(R.string.assistant_vault_picker_preparing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Decrypt the chosen vault photo to a temp JPEG in the app cache and return
 * its file:// URI. Returns null if the decrypt or write failed.
 */
private fun decryptToTempUri(context: android.content.Context, photo: VaultPhoto): Uri? {
    return try {
        val crypto = CryptoManager(context).also { it.initialize() }
        if (!crypto.isUnlocked()) return null
        val vault = VaultRepository(context, crypto)
        val bmp = vault.loadFullPhoto(photo) ?: return null
        val temp = File(context.cacheDir, "assistant_vault_${System.currentTimeMillis()}.jpg")
        FileOutputStream(temp).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        bmp.recycle()
        Uri.fromFile(temp)
    } catch (e: Exception) {
        Log.w("VaultPhotoPickerSheet", "decrypt failed: ${e.message}")
        null
    }
}
