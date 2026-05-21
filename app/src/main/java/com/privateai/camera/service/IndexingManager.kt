// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.privateai.camera.bridge.FaceEmbedder
import com.privateai.camera.bridge.ImageClassifier
import com.privateai.camera.bridge.OnnxDetector
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.PhotoIndex
import com.privateai.camera.security.PrivoraDatabase
import com.privateai.camera.security.VaultLockManager
import com.privateai.camera.security.VaultMediaType
import com.privateai.camera.security.VaultRepository
import com.privateai.camera.security.FolderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Persistent singleton that manages photo indexing.
 * Survives navigation — indexing continues even after leaving VaultScreen.
 */
object IndexingManager {

    private const val TAG = "IndexingManager"

    private val _progress = MutableStateFlow(0 to 0) // done to total
    val progress: StateFlow<Pair<Int, Int>> = _progress

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Start indexing unindexed photos. Safe to call multiple times — skips if already running.
     * @param skipDetector If true, skips YOLOv8n for faster batch processing.
     */
    fun startIndexing(context: Context, skipDetector: Boolean = false) {
        if (job?.isActive == true) {
            Log.d(TAG, "Already running, skipping")
            return
        }

        job = scope.launch {
            _isRunning.value = true
            Log.d(TAG, "Indexing started (skipDetector=$skipDetector)")

            try {
                val crypto = CryptoManager(context).also { it.initialize() }
                val vault = VaultRepository(context, crypto)
                val folderManager = FolderManager(context, crypto)
                val db = PrivoraDatabase.getInstance(context, crypto)
                val pi = PhotoIndex(db)
                val classifier = ImageClassifier(context)
                val detector = if (!skipDetector) {
                    try { OnnxDetector(context) } catch (_: Exception) { null }
                } else null
                val faceEmbedder = try { FaceEmbedder(context) } catch (_: Exception) { null }

                // Find all unindexed photos
                val allPhotos = getAllVaultPhotos(vault, folderManager)
                val unindexed = allPhotos.filter { !pi.isIndexed(it.id) }
                Log.d(TAG, "Total: ${allPhotos.size}, unindexed: ${unindexed.size}")

                if (unindexed.isEmpty()) {
                    _isRunning.value = false
                    return@launch
                }

                _progress.value = 0 to unindexed.size

                unindexed.forEachIndexed { i, photo ->
                    VaultLockManager.markUnlocked()
                    try {
                        val bmp = try {
                            if (photo.encryptedFile.length() > 10 * 1024 * 1024) vault.loadThumbnail(photo)
                            else vault.loadFullPhoto(photo) ?: vault.loadThumbnail(photo)
                        } catch (_: OutOfMemoryError) { vault.loadThumbnail(photo) }

                        if (bmp != null && !bmp.isRecycled) {
                            try {
                                pi.indexPhoto(photo.id, bmp, classifier, faceEmbedder = faceEmbedder, detector = detector)
                            } catch (_: Throwable) {}
                            if (!bmp.isRecycled) bmp.recycle()
                        }
                    } catch (_: Throwable) {}

                    _progress.value = (i + 1) to unindexed.size
                }

                // Auto-name face groups from contacts
                if (faceEmbedder != null) {
                    try {
                        val contactRepo = com.privateai.camera.security.ContactRepository(
                            java.io.File(context.filesDir, "vault/contacts"), crypto, db
                        )
                        pi.autoNameFromContacts(contactRepo, faceEmbedder)
                    } catch (_: Exception) {}
                }

                faceEmbedder?.release()
                detector?.release()
                classifier.release()
                Log.d(TAG, "Indexing complete: ${unindexed.size} photos")
            } catch (e: Exception) {
                Log.e(TAG, "Indexing failed: ${e.message}")
            } finally {
                _isRunning.value = false
                // ONNX indexer just finished — Gemma was holding off while
                // this ran. Tell GemmaIndexingManager to pick up its queue
                // now if there's any pending work.
                try {
                    GemmaIndexingManager.tryDrain(context)
                } catch (_: Exception) {}
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _isRunning.value = false
    }

    private fun getAllVaultPhotos(
        vault: VaultRepository,
        folderManager: FolderManager
    ): List<com.privateai.camera.security.VaultPhoto> {
        val fromCats = vault.listAllPhotos()
        val fromFolders = folderManager.listAllFolders().flatMap { f ->
            vault.listFolderItems(folderManager.getFolderDir(f.id))
        }
        return (fromCats + fromFolders).distinctBy { it.id }
            .filter { it.mediaType == VaultMediaType.PHOTO }
    }
}
