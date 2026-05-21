// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fdroid-flavor [Translator] — Gemma-backed translation.
 *
 * Track A3 — the F-Droid build can't ship ML Kit Translate (Play Services
 * blob), so this implementation routes the user's text through the
 * on-device Gemma 4 LLM that the rest of the app already uses for the
 * Assistant. Quality is excellent for major language pairs (English ↔
 * Arabic/French/Spanish/German/Chinese/Japanese/Korean/Russian); some of
 * the smaller languages in our 15-language list (Hindi, Turkish, Polish)
 * may be a step behind ML Kit. Honest trade-off documented in Track A3.
 *
 * Prompt format identical to the existing "AI" translation button in
 * v2.0.x's TranslateScreen so users get the same output whether they're
 * on the playstore or fdroid flavor. `temperature = 0.3` keeps the
 * model literal — translation is not a creative task.
 */
class TranslatorImpl(private val context: Context) : Translator {

    override suspend fun translate(text: String, source: LangItem, target: LangItem): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        if (!GemmaRunner.isAvailable(context)) {
            Log.w("TranslatorImpl", "Gemma not available on fdroid flavor — translation skipped")
            return@withContext null
        }
        val prompt = buildString {
            append("Translate the following text from ")
            append(source.name)
            append(" to ")
            append(target.name)
            append(". Output ONLY the translation, nothing else — no explanations, no quotes, no language tags.\n\n")
            append(text)
        }
        try {
            val out = GemmaRunner.complete(
                context, prompt,
                systemInstruction = "You are a professional translator. Output only the translated text.",
                temperature = 0.3
            )
            out?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e("TranslatorImpl", "Gemma translate failed: ${e.message}", e)
            null
        }
    }

    // Gemma model load is handled separately by AssistantScreen / first AI
    // use — we don't need to pre-warm anything here. Empty close() too;
    // the engine is a process-wide singleton, not per-instance.
    override suspend fun prepare(source: LangItem, target: LangItem) {}
    override fun close() {}
}
