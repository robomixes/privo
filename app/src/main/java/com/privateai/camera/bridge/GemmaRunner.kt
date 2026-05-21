// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Singleton wrapper for Gemma 4 on-device inference via LiteRT-LM.
 *
 * - Lazy loads the engine only when first invoked
 * - All methods are suspend-safe and thread-safe
 * - Model file must be downloaded separately (not bundled in APK)
 */
object GemmaRunner {

    private const val TAG = "GemmaRunner"
    private const val PREFS_NAME = "gemma_settings"
    private const val KEY_ENABLED = "ai_enabled"
    private const val MODEL_DIR = "models"
    private const val MODEL_FILE = "gemma-4-e2b.litertlm"

    private var engine: Engine? = null
    private var activeConversation: Conversation? = null
    private val mutex = Mutex()
    private var loadFailed = false

    // ── Status checks ────────────────────────────────────────────────

    /** Whether the user has enabled Advanced AI in settings. */
    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Whether the model file is downloaded and ready. */
    fun isModelDownloaded(context: Context): Boolean {
        return getModelFile(context).exists()
    }

    /** Whether the AI is enabled AND model is present AND hasn't failed — use this to show/hide AI buttons. */
    fun isAvailable(context: Context): Boolean {
        return isEnabled(context) && isModelDownloaded(context) && !loadFailed
    }

    /**
     * Get the model file path.
     *
     * Lives in external app-private storage (`getExternalFilesDir`) — the only
     * location DownloadManager can write to without app-side foreground service
     * code. Same privacy properties as `filesDir`: app-sandbox-private (other
     * apps can't read it), wiped on uninstall. The model is a public 2.5 GB
     * HuggingFace blob, not user data — internal vs external storage doesn't
     * affect Privora's encryption guarantees.
     */
    fun getModelFile(context: Context): File {
        val dir = context.getExternalFilesDir(MODEL_DIR)
            ?: File(context.filesDir, MODEL_DIR)  // fallback if external is unavailable
        return File(dir, MODEL_FILE)
    }

    /**
     * One-time migration: move a model file from the old internal location
     * (`filesDir/models/`) to the new external app-private location used by
     * DownloadManager. Called once on app start. Idempotent; no-op if the file
     * is already in the new location or if there's nothing to move.
     */
    fun migrateModelLocation(context: Context) {
        val oldFile = File(File(context.filesDir, MODEL_DIR), MODEL_FILE)
        val newFile = getModelFile(context)
        if (oldFile.exists() && oldFile.absolutePath != newFile.absolutePath && !newFile.exists()) {
            newFile.parentFile?.mkdirs()
            val moved = oldFile.renameTo(newFile)
            if (moved) {
                Log.i(TAG, "Migrated AI model to external app-private storage")
                // Also move sibling caches so engine doesn't waste a cold load
                File(context.filesDir, MODEL_DIR).listFiles()?.forEach { f ->
                    val dst = File(newFile.parentFile, f.name)
                    if (!dst.exists()) f.renameTo(dst)
                }
            } else {
                Log.w(TAG, "Failed to migrate model — falling back to redownload on next enable")
            }
        }
    }

    /** Get model size in bytes (0 if not downloaded). */
    fun getModelSizeBytes(context: Context): Long {
        val f = getModelFile(context)
        return if (f.exists()) f.length() else 0L
    }

    /** Delete the downloaded model and cache to free storage. */
    fun deleteModel(context: Context) {
        // Cancel any in-flight download so its partial file isn't orphaned and
        // the next "Enable AI" tap starts cleanly.
        try { GemmaModelManager.cancelDownload(context) } catch (_: Exception) {}
        getModelFile(context).delete()
        // Also delete xnnpack cache
        File(getModelFile(context).absolutePath + ".xnnpack_cache").delete()
        resetCrashFlag(context)
        unload()
    }

    // ── Engine lifecycle ─────────────────────────────────────────────

    /** Load the engine. Call from a coroutine (IO-bound, takes ~3-5 sec cold). */
    suspend fun load(context: Context) = mutex.withLock {
        if (engine != null) return@withLock
        if (loadFailed) return@withLock

        val modelFile = getModelFile(context)
        if (!modelFile.exists()) {
            Log.w(TAG, "Model not found at ${modelFile.absolutePath}")
            return@withLock
        }

        // Check if a previous load attempt crashed the app
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean("load_crashed", false)) {
            Log.w(TAG, "Previous engine load crashed — skipping. User can retry from Settings.")
            loadFailed = true
            return@withLock
        }

        withContext(Dispatchers.IO) {
            try {
                // Clean stale engine caches from previous backend attempts
                val modelDir = modelFile.parentFile
                modelDir?.listFiles()?.forEach { f ->
                    if (f.name.endsWith(".xnnpack_cache") || f.name.endsWith("_mldrift_program_cache.bin")) {
                        f.delete()
                        Log.d(TAG, "Deleted stale cache: ${f.name}")
                    }
                }

                // Mark as "loading" — if app crashes during this, we know on next launch
                prefs.edit().putBoolean("load_crashed", true).commit()

                // Try GPU first (faster), fall back to CPU if GPU/OpenCL not available.
                // visionBackend is required for Content.ImageFile / ImageBytes — without
                // it the runtime logs "VisionExecutorSettings: Not set" at init and
                // null-derefs the (never-allocated) vision executor on the first vision
                // sendMessage(). Confirmed against LiteRT-LM 0.10.2 / 0.11.0-rc1 / 0.11.0
                // on Pixel 9a, all SIGSEGV with identical PC offsets in liblitertlm_jni.so
                // before this parameter was added.
                var eng: Engine? = null
                var usedBackend = "unknown"
                try {
                    Log.i(TAG, "Attempting GPU backend (with vision)...")
                    val gpuConfig = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.GPU(),
                        visionBackend = Backend.GPU(),
                    )
                    eng = Engine(gpuConfig)
                    eng.initialize()
                    usedBackend = "GPU"
                } catch (gpuErr: Exception) {
                    Log.w(TAG, "GPU failed (${gpuErr.message}), falling back to CPU...")
                    try { eng?.close() } catch (_: Exception) {}
                    eng = null
                    // Clean GPU cache before CPU attempt
                    modelFile.parentFile?.listFiles()?.forEach { f ->
                        if (f.name.contains("mldrift")) { f.delete() }
                    }
                    val cpuConfig = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.CPU(),
                        visionBackend = Backend.CPU(),
                    )
                    eng = Engine(cpuConfig)
                    eng.initialize()
                    usedBackend = "CPU"
                }
                engine = eng

                // Load succeeded — clear the crash flag
                prefs.edit().putBoolean("load_crashed", false).apply()
                Log.i(TAG, "Gemma 4 engine loaded successfully (backend=$usedBackend)")
            } catch (e: Exception) {
                prefs.edit().putBoolean("load_crashed", false).apply()
                loadFailed = true
                Log.e(TAG, "Failed to load Gemma engine: ${e.message}", e)
            }
        }
    }

    /** Reset the crash flag so the user can retry loading. */
    fun resetCrashFlag(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("load_crashed", false).apply()
        loadFailed = false
    }

    /** True if a previous vision call crashed the process; describeImage() will refuse until reset. */
    fun isVisionCrashed(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("vision_crashed", false)

    /** Clear the vision-crash flag so the user can retry vision inference. */
    fun resetVisionCrashFlag(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("vision_crashed", false).apply()
    }

    /**
     * Auto-clear sticky crash flags. Called once from MainActivity.onCreate.
     *
     * Two triggers:
     *  1. versionCode change — a release shipping vision fixes shouldn't
     *     leave users locked out by a flag set under the old broken build.
     *  2. age-based — if the flag has been set for more than [STALE_FLAG_MS]
     *     (10 min), it's almost certainly leftover from an old session that
     *     died, not an active retry-loop. The flag's purpose is to break a
     *     loop within a single session; once enough time has passed without
     *     the user explicitly resetting it, the safer default is to give
     *     vision another chance. If it crashes again, [describeImage]
     *     re-arms the flag with a fresh timestamp before each call.
     */
    fun clearStaleCrashFlagsOnUpgrade(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedVersion = prefs.getInt("last_seen_version_code", -1)
        val currentVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        } catch (_: Exception) { return }

        val visionCrashedAt = prefs.getLong("vision_crashed_at", 0L)
        val visionStale = prefs.getBoolean("vision_crashed", false) &&
            visionCrashedAt > 0L &&
            System.currentTimeMillis() - visionCrashedAt > STALE_FLAG_MS

        if (storedVersion != currentVersion || visionStale) {
            prefs.edit()
                .putBoolean("vision_crashed", false)
                .putBoolean("load_crashed", false)
                .putLong("vision_crashed_at", 0L)
                .putInt("last_seen_version_code", currentVersion)
                .apply()
            when {
                storedVersion != -1 && storedVersion != currentVersion ->
                    Log.i(TAG, "Cleared stale crash flags after upgrade $storedVersion → $currentVersion")
                visionStale ->
                    Log.i(TAG, "Cleared stale vision_crashed flag (age=${System.currentTimeMillis() - visionCrashedAt}ms)")
            }
        }
    }

    /** Auto-clear horizon for the vision_crashed flag (10 minutes). */
    private const val STALE_FLAG_MS = 10L * 60L * 1000L

    /** Close any active conversation (LiteRT-LM only allows one at a time). */
    private fun closeActiveConversation() {
        try { activeConversation?.close() } catch (_: Exception) {}
        activeConversation = null
    }

    /**
     * Cancel any in-flight Gemma generation. Called from the Assistant's
     * Stop button so the underlying native LiteRT-LM `sendMessageAsync`
     * stream is closed promptly — without this, the engine stays in a
     * "generating" state and the next call blocks on the mutex / wedges
     * the vision crash flag.
     *
     * Closing the conversation from outside the running call is safe:
     * LiteRT-LM treats it as a stream end. The Flow `collect` inside
     * `completeStreaming` then exits cleanly, the `finally` runs (closes
     * again, which is a no-op), and the mutex releases. Vision's
     * `describeImage` runs synchronously and cannot be interrupted
     * mid-call, but closing the conversation here still releases the
     * native handle so the next call gets a fresh one.
     */
    fun cancelInflight() {
        // Drop the reference synchronously so any subsequent call sees a
        // clean state immediately. Run the native close on a background
        // dispatcher — close() on a mid-generation LiteRT-LM stream is a
        // blocking call that can take several seconds, and the Stop button
        // fires this from the UI thread. Without the dispatcher hop we ANR
        // ("Privora is not responding") when the user kills a long
        // translation and immediately sends a new prompt.
        val conv = activeConversation
        activeConversation = null
        if (conv != null) {
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                try { conv.close() } catch (e: Exception) {
                    Log.w(TAG, "cancelInflight close threw: ${e.message}")
                }
            }
        }
    }


    /** Unload the engine to free RAM. */
    fun unload() {
        closeActiveConversation()
        try { engine?.close() } catch (_: Exception) {}
        engine = null
        Log.i(TAG, "Gemma engine unloaded")
    }

    fun isLoaded(): Boolean = engine != null

    /** Reload engine on CPU backend after GPU failure. */
    private fun reloadOnCpu(context: Context) {
        try {
            closeActiveConversation()
            engine?.close()
            engine = null
            // Clean GPU caches
            getModelFile(context).parentFile?.listFiles()?.forEach { f ->
                if (f.name.contains("mldrift") || f.name.endsWith(".xnnpack_cache")) f.delete()
            }
            val config = EngineConfig(
                modelPath = getModelFile(context).absolutePath,
                backend = Backend.CPU()
            )
            val eng = Engine(config)
            eng.initialize()
            engine = eng
            Log.i(TAG, "Engine reloaded on CPU backend")
        } catch (e: Exception) {
            Log.e(TAG, "CPU reload also failed: ${e.message}", e)
            loadFailed = true
        }
    }

    /** Retry a text completion after backend switch. */
    private fun retryComplete(prompt: String, systemInstruction: String, temperature: Double): String? {
        val eng = engine ?: return null
        return try {
            closeActiveConversation()
            val conversation = eng.createConversation(
                ConversationConfig(
                    systemInstruction = if (systemInstruction.isNotEmpty()) Contents.of(Content.Text(systemInstruction)) else null,
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature)
                )
            )
            activeConversation = conversation
            val response = conversation.sendMessage(prompt)
            closeActiveConversation()
            Log.i(TAG, "Retry on CPU succeeded")
            extractText(response)
        } catch (e: Exception) {
            closeActiveConversation()
            Log.e(TAG, "Retry on CPU also failed: ${e.message}", e)
            null
        }
    }

    // ── Text inference ───────────────────────────────────────────────

    /** Single-turn text completion. Returns the full response text. */
    suspend fun complete(
        context: Context,
        prompt: String,
        systemInstruction: String = "",
        maxTokens: Int = 512,
        temperature: Double = 0.7
    ): String? {
        Log.d(TAG, "complete() called — loadFailed=$loadFailed, engine=${engine != null}")
        if (loadFailed) { Log.w(TAG, "complete() skipped — loadFailed"); return null }
        if (engine == null) load(context)
        val eng = engine
        if (eng == null) { Log.w(TAG, "complete() skipped — engine null after load"); return null }

        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    closeActiveConversation()
                    Log.d(TAG, "Creating conversation for text completion...")
                    val conversation = eng.createConversation(
                        ConversationConfig(
                            systemInstruction = if (systemInstruction.isNotEmpty()) Contents.of(Content.Text(systemInstruction)) else null,
                            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature)
                        )
                    )
                    activeConversation = conversation
                    Log.d(TAG, "Sending message (${prompt.take(50)}...)")
                    val response = conversation.sendMessage(prompt)
                    Log.d(TAG, "Response received: ${response.toString().take(100)}")
                    closeActiveConversation()
                    extractText(response)
                } catch (e: Exception) {
                    closeActiveConversation()
                    Log.e(TAG, "Inference failed: ${e.message}", e)
                    // If GPU failed with OpenCL error, retry on CPU
                    if (e.message?.contains("OpenCL") == true) {
                        Log.w(TAG, "GPU inference failed — reloading engine on CPU...")
                        reloadOnCpu(context)
                        return@withContext retryComplete(prompt, systemInstruction, temperature)
                    }
                    null
                }
            }
        }
    }

    /** Streaming text completion. Emits partial token strings as they arrive. */
    fun completeStreaming(
        context: Context,
        prompt: String,
        systemInstruction: String = "",
        temperature: Double = 0.7
    ): Flow<String> = kotlinx.coroutines.flow.channelFlow {
        if (engine == null) {
            withContext(Dispatchers.IO) { load(context) }
        }
        val eng = engine ?: return@channelFlow

        mutex.withLock {
            closeActiveConversation()
            val conversation = eng.createConversation(
                ConversationConfig(
                    systemInstruction = if (systemInstruction.isNotEmpty()) Contents.of(Content.Text(systemInstruction)) else null,
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature)
                )
            )
            activeConversation = conversation

            var emittedChars = 0
            var wasCancelled = false
            // The user-facing Job we polled to know if Stop was tapped.
            // channelFlow's `send`/`trySend` is thread-safe across contexts,
            // so we can drain the LM in NonCancellable without violating
            // Flow's emission contract (the failure that produced the
            // "I couldn't generate a response" message after the v1 fix).
            val outerJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
            try {
                // Drain runs in NonCancellable so a user Stop NEVER tears
                // down LiteRT-LM's flow upstream while its native worker
                // thread is still alive — that race dereferences freed
                // state in liblitertlm_jni.so and SIGSEGVs (confirmed via
                // tombstone: Thread-267 fault addr 0x0 at +0x4c9060).
                // Instead we keep collecting tokens to their natural end;
                // once Stop is detected we just stop emitting them. The
                // mutex is held until this returns, so the next call can't
                // race the dying worker.
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    conversation.sendMessageAsync(prompt).collect { chunk ->
                        val s = chunk.toString()
                        emittedChars += s.length
                        if (outerJob?.isActive == false) {
                            if (!wasCancelled) {
                                wasCancelled = true
                                Log.d(TAG, "Stop detected at $emittedChars chars — draining silently")
                            }
                            return@collect
                        }
                        // trySend is thread-safe across coroutine contexts,
                        // unlike `emit` on a regular flow {}. If the
                        // downstream collector has already cancelled, the
                        // channel is closed and trySend returns failure —
                        // we just record that and keep draining.
                        val result = trySend(s)
                        if (result.isClosed) {
                            if (!wasCancelled) {
                                wasCancelled = true
                                Log.d(TAG, "Downstream closed at $emittedChars chars — draining silently")
                            }
                        }
                    }
                }
                if (wasCancelled) Log.d(TAG, "Stream drained after Stop — $emittedChars chars discarded")
                else Log.d(TAG, "Streaming completed cleanly — $emittedChars chars emitted")
            } catch (e: Exception) {
                Log.e(TAG, "Streaming inference failed after $emittedChars chars: ${e.javaClass.simpleName}: ${e.message}", e)
            } finally {
                // Safe to close now — the native worker has finished. The
                // mutex stays held until the close returns, so the next
                // createConversation waits behind us, never races.
                closeActiveConversation()
            }
            if (wasCancelled) throw kotlinx.coroutines.CancellationException("Streaming stopped by user")
        }
    }

    // ── Vision inference ─────────────────────────────────────────────

    /** Describe an image or answer a question about it using Gemma vision. */
    suspend fun describeImage(
        context: Context,
        imagePath: String,
        prompt: String = "Describe this image in one sentence."
    ): String? {
        Log.d(TAG, "describeImage() called — loadFailed=$loadFailed, engine=${engine != null}, path=$imagePath")
        if (loadFailed) { Log.w(TAG, "describeImage() skipped — loadFailed"); return null }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (engine == null) load(context)
        val eng = engine
        if (eng == null) { Log.w(TAG, "describeImage() skipped — engine null"); return null }

        return mutex.withLock {
            // Crash-recovery check has to happen INSIDE the mutex, not before.
            // A still-running previous call (e.g. the user tapped Stop on a
            // long vision turn but its native sendMessage hasn't finished
            // yet) holds vision_crashed=true. If we checked outside the
            // mutex, the second call would bail out with "something went
            // wrong" even though no real crash occurred — the flag was just
            // mid-flight. Inside the mutex, the previous call's finally has
            // already cleared the flag.
            if (prefs.getBoolean("vision_crashed", false)) {
                val stampedAt = prefs.getLong("vision_crashed_at", 0L)
                val ageMs = System.currentTimeMillis() - stampedAt
                Log.w(TAG, "describeImage() skipped — vision_crashed flag armed (age=${ageMs}ms). Was the previous call interrupted mid-stream?")
                return@withLock null
            }
            withContext(Dispatchers.IO) {
                // Arm the crash flag with a synchronous commit so it persists if
                // the JVM dies (SIGSEGV) during sendMessage. The `finally` below
                // guarantees we disarm it for every NON-JVM-death exit path —
                // success, caught exception, coroutine cancellation, even an
                // adb force-stop after we leave the native call.
                //
                // Also stamp the time so [clearStaleCrashFlagsOnUpgrade] can
                // auto-clear flags that are clearly leftover from a session
                // that died long ago (10+ min stale → clear on next launch).
                prefs.edit()
                    .putBoolean("vision_crashed", true)
                    .putLong("vision_crashed_at", System.currentTimeMillis())
                    .commit()
                try {
                    closeActiveConversation()
                    Log.d(TAG, "Creating conversation for vision...")
                    val conversation = eng.createConversation(ConversationConfig())
                    activeConversation = conversation
                    // Image first, text second (required by Gemma 4 attention mechanism)
                    Log.d(TAG, "Sending vision message...")
                    val response = conversation.sendMessage(
                        Contents.of(
                            Content.ImageFile(imagePath),
                            Content.Text(prompt)
                        )
                    )
                    Log.d(TAG, "Vision response: ${response.toString().take(100)}")
                    extractText(response)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // User tapped Stop while the vision call was running.
                    // We MUST re-throw CancellationException — the previous
                    // `catch (Exception)` swallowed it and left structured
                    // concurrency thinking the call completed normally,
                    // which wedged subsequent calls.
                    Log.d(TAG, "Vision inference cancelled by user")
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Vision inference failed: ${e.message}", e)
                    null
                } finally {
                    closeActiveConversation()
                    // Use commit() (sync) so the cleared flag hits disk before
                    // any subsequent process death can strand it. The
                    // NonCancellable guard ensures this still runs even if
                    // the coroutine was cancelled — without it, a cancel
                    // mid-vision-call could leave `vision_crashed` armed,
                    // blocking every subsequent vision call until the user
                    // resets it from Settings.
                    withContext(kotlinx.coroutines.NonCancellable) {
                        prefs.edit()
                            .putBoolean("vision_crashed", false)
                            .putLong("vision_crashed_at", 0L)
                            .commit()
                    }
                }
            }
        }
    }

    /** Describe an image using its existing labels (no vision, text-only, safe). */
    suspend fun describeFromLabels(
        context: Context,
        labels: List<String>
    ): String? {
        if (loadFailed) return null
        if (labels.isEmpty()) return null
        val labelStr = labels.joinToString(", ")
        return complete(
            context,
            "A photo was analyzed and these objects were detected: $labelStr.\n" +
            "Write a single factual sentence describing what is likely in this photo. " +
            "Only mention what the detected objects confirm. " +
            "Do NOT guess gender, age, emotions, or identity of people — just say 'person' or 'people'. " +
            "Output only the description, nothing else.",
            temperature = 0.3
        )
    }

    /** Answer a question about a photo using its labels and description (text-only, safe). */
    suspend fun askAboutPhoto(
        context: Context,
        labels: List<String>,
        description: String,
        question: String
    ): String? {
        if (loadFailed) return null
        val context2 = buildString {
            append("Detected objects in photo: ${labels.joinToString(", ")}.")
            if (description.isNotEmpty()) append(" Description: $description.")
        }
        return complete(
            context,
            "Photo analysis: $context2\n\nQuestion: $question\n\n" +
            "Answer based only on what was detected. If you cannot determine the answer from the detected objects, say so. " +
            "Do NOT guess gender, age, or identity. Be concise.",
            temperature = 0.3
        )
    }

    /** Extract text from a Message response. */
    private fun extractText(message: Message?): String? {
        return message?.toString()
    }

    // ── Strict-JSON helper ───────────────────────────────────────────

    /**
     * Run a completion expected to return a single JSON object.
     *
     * Tolerant of the common ways Gemma drifts from "JSON only" — it may
     * wrap the object in ```json fences, prepend a sentence, or follow it
     * with a paragraph. We grab the first {...} substring and parse that.
     *
     * Returns null if no JSON could be extracted (caller's job to fall back
     * to a user-friendly toast or treat the raw text as an answer).
     */
    suspend fun completeJson(
        context: Context,
        prompt: String,
        systemInstruction: String = "",
        maxTokens: Int = 512,
        temperature: Double = 0.2
    ): org.json.JSONObject? {
        val raw = complete(context, prompt, systemInstruction, maxTokens, temperature) ?: return null
        return extractFirstJsonObject(raw)
    }

    /** Pull the first balanced {...} block out of arbitrary model text. */
    internal fun extractFirstJsonObject(raw: String): org.json.JSONObject? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var end = -1
        var inString = false
        var escape = false
        for (i in start until raw.length) {
            val c = raw[i]
            if (escape) { escape = false; continue }
            if (c == '\\') { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) { end = i; break }
            }
        }
        if (end < 0) return null
        return try { org.json.JSONObject(raw.substring(start, end + 1)) } catch (_: Exception) { null }
    }
}
