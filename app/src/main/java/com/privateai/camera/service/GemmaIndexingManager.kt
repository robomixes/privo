// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.privateai.camera.bridge.GemmaPrompts
import com.privateai.camera.bridge.GemmaRunner
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.FolderManager
import com.privateai.camera.security.PhotoIndex
import com.privateai.camera.security.PrivoraDatabase
import com.privateai.camera.security.VaultLockManager
import com.privateai.camera.security.VaultMediaType
import com.privateai.camera.security.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Persistent singleton that runs Gemma vision over vault photos to generate
 * descriptions + AI tags in the background. Parallel to [IndexingManager] —
 * the ONNX classifier indexer keeps its existing fast path; this is an
 * additive slower path for Gemma data (~10 sec/photo on warm engine).
 *
 * Two entry points:
 *   • [enqueue] — single-photo trigger after capture / scanner / import.
 *     Only fires when the Settings toggle `auto_ai_tag_new_photos` is on.
 *   • [processAll] — bulk pass over every vault photo missing a Gemma
 *     description (driven by the Settings "Process all photos with AI…"
 *     button). Skips photos that already have a description.
 *
 * Throttling for v1: one photo at a time, 2-sec sleep between photos to keep
 * the UI responsive. No battery / charging / thermal gates yet — add only
 * if real-world reports come in.
 */
object GemmaIndexingManager {

    private const val TAG = "GemmaIndexingManager"
    private const val PER_PHOTO_GAP_MS = 2_000L
    private const val PREFS_NAME = "gemma_settings"
    private const val KEY_PENDING_QUEUE = "gemma_pending_queue"

    @Volatile private var hydrated = false

    /**
     * What Gemma data to generate during a bulk pass. Per-photo single-shot
     * `enqueue()` always runs BOTH — those are new captures with neither
     * field cached yet.
     */
    enum class ProcessMode { DESCRIPTION_ONLY, TAGS_ONLY, BOTH }

    /** Per-mode counts of photos still missing their respective Gemma data. */
    data class PendingCounts(val description: Int, val tags: Int, val both: Int)

    private val _progress = MutableStateFlow(0 to 0) // done to total
    val progress: StateFlow<Pair<Int, Int>> = _progress

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Camera coexistence + ONNX indexer priority: while the camera UI is
    // active OR the ONNX [IndexingManager] is running, Gemma is held off
    // so the live preview / fast indexer don't compete for GPU + RAM.
    // Photos enqueued during these windows go to [pendingQueue] along with
    // the [ProcessMode] they should run when conditions clear.
    @Volatile private var paused = false
    private data class QueueEntry(val photoId: String, val mode: ProcessMode)
    private val pendingQueue = mutableListOf<QueueEntry>()
    private val pendingLock = Any()

    private fun shouldYield(context: Context): Boolean =
        paused || IndexingManager.isRunning.value || !GemmaRunner.isAvailable(context)

    /**
     * Restore the pending queue from disk. Called by every public entry point
     * so a process death between sessions doesn't lose queued work. Idempotent
     * — only reads once per process (the in-memory queue is authoritative
     * after that).
     */
    private fun hydrateIfNeeded(context: Context) {
        if (hydrated) return
        synchronized(pendingLock) {
            if (hydrated) return
            val csv = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PENDING_QUEUE, null)
            if (!csv.isNullOrBlank()) {
                csv.split(",").forEach { token ->
                    val parts = token.split(":")
                    if (parts.size == 2 && parts[0].isNotBlank()) {
                        val mode = try { ProcessMode.valueOf(parts[1]) } catch (_: Exception) { ProcessMode.BOTH }
                        if (pendingQueue.none { it.photoId == parts[0] }) {
                            pendingQueue.add(QueueEntry(parts[0], mode))
                        }
                    }
                }
                Log.i(TAG, "hydrated ${pendingQueue.size} pending photo IDs from prefs")
            }
            hydrated = true
        }
    }

    private fun persistQueue(context: Context) {
        synchronized(pendingLock) {
            val csv = pendingQueue.joinToString(",") { "${it.photoId}:${it.mode.name}" }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_PENDING_QUEUE, csv).apply()
        }
    }

    /**
     * Mark the manager as "paused" while a UI surface that conflicts with
     * Gemma's GPU/RAM use (the camera preview) is on screen. enqueue() calls
     * during this window append to [pendingQueue] without firing the engine.
     * Any currently-running job is also cancelled so the camera preview
     * doesn't lag while Gemma is mid-call — camera work has priority.
     * Unprocessed photo IDs are pushed back to [pendingQueue] inside the
     * job's `finally` so they get a second chance on resume().
     */
    fun pause() {
        paused = true
        job?.cancel()
        job = null
        _isRunning.value = false
    }

    /**
     * Resume processing and drain any photos that were enqueued while paused.
     * Called from the capture screen's DisposableEffect onDispose AND from
     * [IndexingManager]'s finally block when the ONNX indexer completes
     * (so Gemma waits for ONNX to finish before starting up).
     */
    fun resume(context: Context) {
        paused = false
        tryDrain(context)
    }

    /**
     * Attempt to start a drain pass if we have pending work and the
     * conditions are right (not paused, ONNX indexer not running, AI ready,
     * no other job in flight). Safe to call repeatedly — no-ops if conditions
     * aren't met. The IndexingManager calls this when it finishes so Gemma
     * naturally takes over.
     */
    fun tryDrain(context: Context) {
        if (job?.isActive == true) return
        hydrateIfNeeded(context)
        if (shouldYield(context)) return
        val drained: List<QueueEntry>
        synchronized(pendingLock) {
            drained = pendingQueue.toList()
            pendingQueue.clear()
        }
        if (drained.isEmpty()) {
            persistQueue(context)
            return
        }
        persistQueue(context)
        val pending = ArrayDeque(drained)
        job = scope.launch {
            _isRunning.value = true
            val total = pending.size
            var done = 0
            try {
                val crypto = CryptoManager(context).also { it.initialize() }
                val vault = VaultRepository(context, crypto)
                val folderManager = FolderManager(context, crypto)
                val db = PrivoraDatabase.getInstance(context, crypto)
                val pi = PhotoIndex(db)
                _progress.value = 0 to total
                while (pending.isNotEmpty()) {
                    if (shouldYield(context)) break
                    val entry = pending.removeFirst()
                    val photo = findPhoto(vault, folderManager, entry.photoId)
                    if (photo != null) processOne(context, vault, pi, photo, entry.mode)
                    done++
                    _progress.value = done to total
                    if (pending.isNotEmpty()) delay(PER_PHOTO_GAP_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "tryDrain failed: ${e.message}", e)
            } finally {
                // If we yielded (camera, ONNX indexer, AI off) with work
                // remaining, push it back to the queue so the next trigger
                // picks up where we left off — no work is silently dropped.
                if (pending.isNotEmpty()) {
                    synchronized(pendingLock) {
                        val existing = pendingQueue.map { it.photoId }.toSet()
                        pending.forEach { e ->
                            if (e.photoId !in existing) pendingQueue.add(e)
                        }
                    }
                }
                persistQueue(context)
                _isRunning.value = false
            }
        }
    }

    /**
     * Process a single photo by id. Used as the post-save hook from
     * CaptureScreen / ScannerScreen / import paths when the user has opted
     * in via the Settings toggle. Coalesces with a bulk run if one is
     * already active — the photo will be picked up there. If [paused] is
     * true (camera UI in foreground), the id goes into the pending queue
     * to be drained by [resume].
     */
    fun enqueue(context: Context, photoId: String) {
        if (!GemmaRunner.isAvailable(context)) return
        hydrateIfNeeded(context)
        // Always go through the queue so a bulk-pass-in-flight, an active
        // camera UI, or a running ONNX indexer can't drop fresh captures
        // on the floor. tryDrain() will pick them up the moment we're free.
        synchronized(pendingLock) {
            if (pendingQueue.none { it.photoId == photoId }) {
                pendingQueue.add(QueueEntry(photoId, ProcessMode.BOTH))
            }
        }
        persistQueue(context)
        if (shouldYield(context) || job?.isActive == true) {
            Log.d(TAG, "enqueue($photoId) queued (yielding to camera/ONNX/active job)")
            return
        }
        tryDrain(context)
    }

    /**
     * Bulk pass — process every vault photo whose Gemma data matches the
     * selected [mode]. Driven by the Settings → "Process all photos with
     * AI…" button. The dialog lets the user pick DESCRIPTION_ONLY,
     * TAGS_ONLY, or BOTH; the per-photo step skips the half it's not
     * asked for.
     */
    /**
     * Bulk pass. [limit] caps how many photos to process this run — useful
     * for big libraries where the user wants to chip away in chunks instead
     * of committing to "all 5000 photos = 8 hours." Default Int.MAX_VALUE
     * = process everything pending for [mode].
     */
    fun processAll(context: Context, mode: ProcessMode = ProcessMode.BOTH, limit: Int = Int.MAX_VALUE) {
        if (!GemmaRunner.isAvailable(context)) return
        hydrateIfNeeded(context)
        if (job?.isActive == true) {
            Log.d(TAG, "processAll skipped — already running")
            return
        }
        job = scope.launch {
            _isRunning.value = true
            var startIndex = 0
            var targets: List<com.privateai.camera.security.VaultPhoto> = emptyList()
            try {
                val crypto = CryptoManager(context).also { it.initialize() }
                val vault = VaultRepository(context, crypto)
                val folderManager = FolderManager(context, crypto)
                val db = PrivoraDatabase.getInstance(context, crypto)
                val pi = PhotoIndex(db)

                targets = collectPending(vault, folderManager, pi, mode).take(limit)
                if (targets.isEmpty()) {
                    Log.d(TAG, "processAll($mode, limit=$limit): nothing to do")
                    return@launch
                }
                _progress.value = 0 to targets.size
                Log.i(TAG, "processAll($mode, limit=$limit): ${targets.size} photos to process")

                for (i in targets.indices) {
                    startIndex = i
                    // Yield to camera UI, ONNX indexer, AI-disable. The
                    // remainder is pushed into pendingQueue in the finally
                    // so tryDrain() resumes it after the conflict clears.
                    if (shouldYield(context)) {
                        Log.i(TAG, "processAll yielded at $i/${targets.size} (paused=$paused, indexing=${IndexingManager.isRunning.value})")
                        break
                    }
                    processOne(context, vault, pi, targets[i], mode)
                    startIndex = i + 1
                    _progress.value = startIndex to targets.size
                    if (i < targets.size - 1) delay(PER_PHOTO_GAP_MS)
                }
                Log.i(TAG, "processAll($mode) complete or paused at $startIndex/${targets.size}")
            } catch (e: Exception) {
                Log.e(TAG, "processAll failed: ${e.message}", e)
            } finally {
                if (startIndex < targets.size) {
                    val remainder = targets.subList(startIndex, targets.size)
                    synchronized(pendingLock) {
                        val existing = pendingQueue.map { it.photoId }.toSet()
                        remainder.forEach { p ->
                            if (p.id !in existing) pendingQueue.add(QueueEntry(p.id, mode))
                        }
                    }
                    persistQueue(context)
                    Log.i(TAG, "processAll: pushed ${remainder.size} remaining photos to queue (mode=$mode)")
                }
                _isRunning.value = false
            }
        }
    }

    /**
     * Per-mode counts of photos still pending Gemma processing. The Settings
     * dialog shows these next to each radio option so the user knows how
     * much work + time each choice implies.
     */
    fun countPending(context: Context): PendingCounts {
        return try {
            hydrateIfNeeded(context)
            val crypto = CryptoManager(context).also { it.initialize() }
            val vault = VaultRepository(context, crypto)
            val folderManager = FolderManager(context, crypto)
            val db = PrivoraDatabase.getInstance(context, crypto)
            val pi = PhotoIndex(db)
            val allPhotos = allCandidatePhotos(vault, folderManager)
            var descMissing = 0
            var tagsMissing = 0
            var bothMissing = 0
            for (p in allPhotos) {
                val noDesc = pi.getDescription(p.id).isEmpty()
                val noGemmaTag = pi.getLabelsWithScores(p.id).none { it.second >= 0.99f }
                if (noDesc) descMissing++
                if (noGemmaTag) tagsMissing++
                if (noDesc || noGemmaTag) bothMissing++
            }
            PendingCounts(description = descMissing, tags = tagsMissing, both = bothMissing)
        } catch (e: Exception) {
            Log.w(TAG, "countPending failed: ${e.message}")
            PendingCounts(0, 0, 0)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _isRunning.value = false
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private suspend fun processOne(
        context: Context,
        vault: VaultRepository,
        pi: PhotoIndex,
        photo: com.privateai.camera.security.VaultPhoto,
        mode: ProcessMode = ProcessMode.BOTH
    ) {
        VaultLockManager.markUnlocked()
        var bmp: Bitmap? = null
        var tempFile: File? = null
        try {
            bmp = try {
                if (photo.encryptedFile.length() > 10 * 1024 * 1024) vault.loadThumbnail(photo)
                else vault.loadFullPhoto(photo) ?: vault.loadThumbnail(photo)
            } catch (_: OutOfMemoryError) { vault.loadThumbnail(photo) }
            if (bmp == null) return

            // Skip per-half if it's already cached AND the user picked that
            // half explicitly — saves Gemma compute on redundant work.
            val needsDesc = pi.getDescription(photo.id).isEmpty()
            val needsTags = pi.getLabelsWithScores(photo.id).none { it.second >= 0.99f }
            val doDesc = mode != ProcessMode.TAGS_ONLY && needsDesc
            val doTags = mode != ProcessMode.DESCRIPTION_ONLY && needsTags
            if (!doDesc && !doTags) return

            tempFile = File(context.cacheDir, "gemma_bulk_${photo.id}.jpg")
            FileOutputStream(tempFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            if (doDesc) {
                val desc = GemmaRunner.describeImage(
                    context, tempFile.absolutePath, GemmaPrompts.describePhoto()
                )?.trim().orEmpty()
                if (desc.isNotEmpty()) pi.setDescription(photo.id, desc)
            }

            if (doTags) {
                // Re-check AI availability between the two calls — the user
                // might have disabled AI after the description completed.
                if (!GemmaRunner.isAvailable(context)) return
                val rawTags = GemmaRunner.describeImage(
                    context, tempFile.absolutePath, GemmaPrompts.generateTags()
                )
                if (!rawTags.isNullOrBlank()) {
                    val cleaned = rawTags
                        .replace('\n', ',')
                        .replace(Regex("""[•\-*]+"""), "")
                        .replace(Regex("""\d+\.\s*"""), "")
                    val newTags = cleaned.split(',')
                        .map { it.trim().lowercase() }
                        .filter { it.length in 2..30 }
                        .distinct()
                        .take(12)
                    if (newTags.isNotEmpty()) pi.mergeAiTags(photo.id, newTags)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "processOne(${photo.id}) failed: ${e.message}")
        } finally {
            try { tempFile?.delete() } catch (_: Exception) {}
            if (bmp != null && !bmp.isRecycled) bmp.recycle()
        }
    }

    private fun allCandidatePhotos(
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

    private fun collectPending(
        vault: VaultRepository,
        folderManager: FolderManager,
        pi: PhotoIndex,
        mode: ProcessMode
    ): List<com.privateai.camera.security.VaultPhoto> {
        return allCandidatePhotos(vault, folderManager).filter { p ->
            val noDesc = pi.getDescription(p.id).isEmpty()
            val noGemmaTag = pi.getLabelsWithScores(p.id).none { it.second >= 0.99f }
            when (mode) {
                ProcessMode.DESCRIPTION_ONLY -> noDesc
                ProcessMode.TAGS_ONLY -> noGemmaTag
                ProcessMode.BOTH -> noDesc || noGemmaTag
            }
        }
    }

    private fun findPhoto(
        vault: VaultRepository,
        folderManager: FolderManager,
        photoId: String
    ): com.privateai.camera.security.VaultPhoto? {
        vault.listAllPhotos().firstOrNull { it.id == photoId }?.let { return it }
        folderManager.listAllFolders().forEach { f ->
            vault.listFolderItems(folderManager.getFolderDir(f.id))
                .firstOrNull { it.id == photoId }
                ?.let { return it }
        }
        return null
    }
}
