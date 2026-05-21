// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import com.privateai.camera.service.TesseractDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Tesseract 5 wrapper — replaces ML Kit text recognition (Track A1.3).
 *
 * The Tesseract C++ engine is wrapped by tesseract4android (Maven Central,
 * 4.9.0). The Kotlin layer here is thin: pick the right language model,
 * init the engine, feed the bitmap, return the recognised text.
 *
 * **Threading**: `TessBaseAPI.init` and `setImage`/`getUTF8Text` are
 * NOT thread-safe — they mutate a per-instance C++ session. We serialize
 * all calls via [mutex] and run on `Dispatchers.IO`. Each [recognize]
 * call creates a fresh `TessBaseAPI`, recognises, then recycles — the
 * one-time engine init is ~30-50ms, dominated by reading the
 * `.traineddata` file (mmap-backed after the first call).
 *
 * **Language selection**: the caller passes one or more language codes
 * (eng, ara, chi_sim, …). Tesseract joins them with `+` internally and
 * recognises mixed-script documents in one pass. The default falls back
 * to whatever the user has installed via Settings → OCR languages —
 * usually just `eng`.
 */
object TesseractRecognizer {

    private const val TAG = "TesseractRecognizer"
    private val mutex = Mutex()

    /**
     * Recognise text in [bitmap] using the listed [languages]. Languages
     * not yet downloaded are silently skipped. Returns the recognised
     * text (possibly multi-line), or an empty string when no models are
     * installed or recognition fails. Caller is responsible for keeping
     * the bitmap alive for the duration of the call (we don't recycle).
     */
    suspend fun recognize(
        context: Context,
        bitmap: Bitmap,
        languages: List<String> = listOf("eng")
    ): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            // Filter to languages the user actually has on disk. Tesseract's
            // init() crashes hard (or returns silently failing recognise)
            // if it's asked to load a missing model — better to drop the
            // missing ones than to corrupt the next OCR pass.
            val installed = languages.filter { TesseractDataManager.isInstalled(context, it) }
            if (installed.isEmpty()) {
                Log.w(TAG, "No installed languages from request=$languages — OCR returns empty")
                return@withContext ""
            }
            val joined = installed.joinToString("+")
            val api = TessBaseAPI()
            try {
                val ok = api.init(TesseractDataManager.dataPath(context), joined)
                if (!ok) {
                    Log.e(TAG, "TessBaseAPI.init failed for langs=$joined")
                    return@withContext ""
                }
                // PSM 3 = fully automatic page segmentation — same default
                // ML Kit used internally. AUTO_OSD detects orientation
                // automatically, but it's slower and requires the `osd`
                // model; we skip it for the common-case rectified scans.
                api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                api.setImage(bitmap)
                api.utF8Text?.trim().orEmpty()
            } catch (e: Exception) {
                Log.e(TAG, "Recognition failed (langs=$joined): ${e.message}", e)
                ""
            } finally {
                try { api.recycle() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Convenience for callers that want OCR using "whatever the user has
     * installed". When no models exist yet, returns empty — callers
     * should surface a "go to Settings → OCR languages" hint.
     */
    suspend fun recognizeInstalledLanguages(context: Context, bitmap: Bitmap): String {
        val installed = TesseractDataManager.installedLanguages(context)
        if (installed.isEmpty()) return ""
        return recognize(context, bitmap, installed)
    }

    /** True when at least one .traineddata file is on disk. */
    fun hasAnyLanguage(context: Context): Boolean =
        TesseractDataManager.installedLanguages(context).isNotEmpty()
}
