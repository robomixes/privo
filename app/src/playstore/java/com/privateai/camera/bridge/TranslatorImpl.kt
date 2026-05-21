// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Playstore-flavor [Translator] — wraps Google ML Kit Translate.
 *
 * Behaviour matches v2.0.x verbatim: 8-second hard timeout on each
 * `translate()` (long enough for first-time model downloads on slow
 * networks, short enough to bail before the user assumes the app froze),
 * model auto-downloaded with the default `DownloadConditions` (Wi-Fi only).
 * One client per (source, target) pair; reused across calls until [close].
 *
 * The fdroid flavor's sibling file at
 * `app/src/fdroid/java/.../bridge/TranslatorImpl.kt` provides a
 * Gemma-backed equivalent — same class name, same package, so the rest
 * of the code (TranslateScreen, Translator.create) doesn't know or care
 * which flavor it got.
 */
class TranslatorImpl(@Suppress("UNUSED_PARAMETER") context: Context) : Translator {

    private var cached: Pair<Pair<String, String>, com.google.mlkit.nl.translate.Translator>? = null

    private fun obtain(source: LangItem, target: LangItem): com.google.mlkit.nl.translate.Translator {
        val key = source.code to target.code
        cached?.let { (k, t) -> if (k == key) return t else t.close() }
        // ML Kit's TranslateLanguage codes match ISO 639-1 except for a
        // handful of legacy aliases (zh-Hans → zh, etc.). Our LangItem
        // codes are picked from the intersection, so a direct pass-through
        // works for all 15 languages we expose.
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.fromLanguageTag(source.code) ?: source.code)
            .setTargetLanguage(TranslateLanguage.fromLanguageTag(target.code) ?: target.code)
            .build()
        val t = Translation.getClient(options)
        cached = key to t
        return t
    }

    override suspend fun translate(text: String, source: LangItem, target: LangItem): String? = withContext(Dispatchers.IO) {
        try {
            val translator = obtain(source, target)
            // Lazy model download. If already downloaded this is ~free.
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            withTimeoutOrNull(8_000L) { translator.translate(text).await() }
        } catch (e: Exception) {
            Log.e("TranslatorImpl", "ML Kit translate failed: ${e.message}", e)
            null
        }
    }

    override suspend fun prepare(source: LangItem, target: LangItem) {
        withContext(Dispatchers.IO) {
            try {
                obtain(source, target)
                    .downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .await()
            } catch (_: Exception) {}
        }
    }

    override fun close() {
        try { cached?.second?.close() } catch (_: Exception) {}
        cached = null
    }
}
