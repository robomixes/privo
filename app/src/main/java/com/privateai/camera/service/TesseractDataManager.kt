// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages Tesseract `.traineddata` language files.
 *
 * Tesseract requires each model file to live on the local filesystem under
 * a `tessdata/` subdirectory; the engine then loads them by language code
 * (e.g. `eng.traineddata`, `ara.traineddata`). We download them lazily on
 * first use to keep the APK small (Track A1.3 user choice). Models come
 * from the official `tesseract-ocr/tessdata_fast` GitHub repo — the LSTM
 * "fast" variants are 4-7 MB each and produce results equivalent to ML
 * Kit's text recognition for typical document scans.
 *
 * Files live under `filesDir/tessdata/` (app-private, not encrypted —
 * they're public model files, not user data). Cleared when the app is
 * uninstalled. The user can browse / delete / re-download from
 * Settings → OCR languages.
 */
object TesseractDataManager {

    private const val TAG = "TesseractDataMgr"
    /** GitHub raw URL for tessdata_fast files. CDN-cached, stable since 2021. */
    private const val BASE_URL = "https://github.com/tesseract-ocr/tessdata_fast/raw/main"
    private const val SUBDIR = "tessdata"

    /**
     * The languages we surface in the picker. `code` is Tesseract's
     * three-letter ISO 639-2 code (the .traineddata filename). `displayName`
     * is shown to the user — no translation needed; these are the language
     * names speakers would recognise.
     */
    data class Language(val code: String, val displayName: String, val sizeApprox: String)

    val SUPPORTED_LANGUAGES = listOf(
        Language("eng", "English", "~4 MB"),
        Language("fra", "Français (French)", "~1 MB"),
        Language("spa", "Español (Spanish)", "~3 MB"),
        Language("deu", "Deutsch (German)", "~2 MB"),
        Language("ita", "Italiano (Italian)", "~2 MB"),
        Language("por", "Português (Portuguese)", "~2 MB"),
        Language("nld", "Nederlands (Dutch)", "~3 MB"),
        Language("ara", "العربية (Arabic)", "~1 MB"),
        Language("heb", "עברית (Hebrew)", "~700 KB"),
        Language("rus", "Русский (Russian)", "~5 MB"),
        Language("chi_sim", "中文简体 (Simplified Chinese)", "~9 MB"),
        Language("chi_tra", "中文繁體 (Traditional Chinese)", "~6 MB"),
        Language("jpn", "日本語 (Japanese)", "~6 MB"),
        Language("kor", "한국어 (Korean)", "~3 MB"),
        Language("tur", "Türkçe (Turkish)", "~1 MB"),
        Language("hin", "हिन्दी (Hindi)", "~3 MB"),
        Language("tha", "ภาษาไทย (Thai)", "~1 MB"),
        Language("vie", "Tiếng Việt (Vietnamese)", "~1 MB"),
    )

    /** Resolve `tessdata/` directory, creating it on first call. */
    fun tessdataDir(context: Context): File {
        // Tesseract requires `tessdata/` to be a subdirectory of the path
        // we pass to `init` — so we point the engine at `filesDir/` and the
        // models live in `filesDir/tessdata/*.traineddata`.
        val parent = context.filesDir
        val tess = File(parent, SUBDIR)
        if (!tess.exists()) tess.mkdirs()
        return tess
    }

    /** Path Tesseract expects for `init(dataPath, lang)`. */
    fun dataPath(context: Context): String = context.filesDir.absolutePath

    /** Is the model for [languageCode] already on disk? */
    fun isInstalled(context: Context, languageCode: String): Boolean {
        return File(tessdataDir(context), "$languageCode.traineddata").exists()
    }

    /** Size on disk for installed languages, in bytes. 0 if not installed. */
    fun sizeBytes(context: Context, languageCode: String): Long {
        val f = File(tessdataDir(context), "$languageCode.traineddata")
        return if (f.exists()) f.length() else 0L
    }

    /** All language codes currently installed (in arbitrary order). */
    fun installedLanguages(context: Context): List<String> {
        val dir = tessdataDir(context)
        return dir.listFiles()?.mapNotNull { f ->
            f.name.removeSuffix(".traineddata").takeIf { f.name.endsWith(".traineddata") }
        } ?: emptyList()
    }

    /**
     * Download the `.traineddata` for [languageCode] into [tessdataDir].
     * Returns true on success, false on network / IO failure. Atomic:
     * writes to `<lang>.traineddata.part` first and renames on success
     * so a half-downloaded file can't poison the next OCR attempt.
     *
     * Call from a coroutine on `Dispatchers.IO`. Reports progress via
     * [onProgress] as `(bytesDownloaded, totalBytes)`; total may be -1
     * when the server doesn't send Content-Length.
     */
    suspend fun download(
        context: Context,
        languageCode: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        if (isInstalled(context, languageCode)) return@withContext true
        val outFile = File(tessdataDir(context), "$languageCode.traineddata")
        val tempFile = File(outFile.parentFile, "${outFile.name}.part")
        val url = "$BASE_URL/$languageCode.traineddata"
        Log.i(TAG, "Downloading $languageCode from $url")
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            conn.connect()
            if (conn.responseCode !in 200..299) {
                Log.e(TAG, "HTTP ${conn.responseCode} for $url")
                conn.disconnect()
                return@withContext false
            }
            val total = conn.contentLengthLong
            var downloaded = 0L
            conn.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                    }
                }
            }
            conn.disconnect()
            // Atomic rename — the engine never sees a half-written file.
            val ok = tempFile.renameTo(outFile)
            if (!ok) {
                Log.e(TAG, "Rename .part → final failed for $languageCode")
                tempFile.delete()
                return@withContext false
            }
            Log.i(TAG, "Downloaded $languageCode (${downloaded} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $languageCode: ${e.message}", e)
            try { tempFile.delete() } catch (_: Exception) {}
            false
        }
    }

    /** Delete the model file for [languageCode]. Returns true if the file
     *  was actually deleted (false if it wasn't there to begin with). */
    fun delete(context: Context, languageCode: String): Boolean {
        val f = File(tessdataDir(context), "$languageCode.traineddata")
        return f.exists() && f.delete()
    }
}
