// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

import android.content.Context

/**
 * One supported language for the Translate feature.
 *
 * [code] is the ISO 639-1 two-letter code (e.g. `en`, `ar`, `zh`). ML Kit
 * Translate, OpenNMT/Argos, and Gemma all understand the same codes, so we
 * use them uniformly. [name] is the English display name shown in the
 * picker — also passed verbatim to the LLM prompt in the Gemma path so the
 * model knows which language to translate into.
 */
data class LangItem(val code: String, val name: String)

/**
 * Translation backend. The two flavors of Privora ship different
 * implementations:
 *
 *  - `playstore` — ML Kit Translate (`Translation.getClient(...)`). Fast,
 *    hand-curated phrasebook quality, requires Google Play Services.
 *  - `fdroid` — Gemma-only translation via [GemmaRunner.complete]. No
 *    Google dependency, slower (~1-3 s per call), quality varies by
 *    language pair but excellent for major ones.
 *
 * The flavor-specific implementation file lives at
 * `app/src/<flavor>/java/com/privateai/camera/bridge/TranslatorImpl.kt`.
 * Both must define a class `TranslatorImpl(context: Context) : Translator`.
 * Track A3 — last ML Kit dep flavor-gated, F-Droid main eligibility
 * unblocked once this lands.
 */
interface Translator {

    /**
     * Translate [text] from [source] to [target]. Returns the translated
     * text on success, null on failure / timeout. May suspend for several
     * seconds (Gemma path) — callers should run on `Dispatchers.IO`.
     *
     * Implementations should never throw on transient failures; return
     * null and let the caller surface a Toast.
     */
    suspend fun translate(text: String, source: LangItem, target: LangItem): String?

    /**
     * Optional warm-up — playstore flavor uses this to pre-download the
     * ML Kit model pair for the picker's current selection so the first
     * Translate tap is instant. Fdroid impl is a no-op (Gemma is already
     * loaded in memory via [GemmaRunner]).
     */
    suspend fun prepare(source: LangItem, target: LangItem) {}

    /**
     * Free any per-instance resources. Playstore impl closes the ML Kit
     * `Translator` (~1 MB native handle). Fdroid impl is a no-op.
     */
    fun close() {}

    companion object {
        /**
         * The 15 languages both flavors support. Subset of ML Kit's
         * coverage chosen for global reach + the languages Privora already
         * localises its own UI into. Order matches the v2.0.x picker so
         * upgrade users don't notice a re-order.
         */
        val LANGUAGES: List<LangItem> = listOf(
            LangItem("en", "English"),
            LangItem("ar", "Arabic"),
            LangItem("fr", "French"),
            LangItem("es", "Spanish"),
            LangItem("de", "German"),
            LangItem("zh", "Chinese"),
            LangItem("ja", "Japanese"),
            LangItem("ko", "Korean"),
            LangItem("pt", "Portuguese"),
            LangItem("ru", "Russian"),
            LangItem("tr", "Turkish"),
            LangItem("it", "Italian"),
            LangItem("hi", "Hindi"),
            LangItem("nl", "Dutch"),
            LangItem("pl", "Polish"),
        )

        /** Factory — resolves to the flavor-specific impl at link time. */
        fun create(context: Context): Translator = TranslatorImpl(context)
    }
}
