// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.privateai.camera.R
import com.privateai.camera.bridge.GemmaRunner
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Returns a time-based greeting string (morning/afternoon/evening/night).
 */
fun getTimeBasedGreeting(context: Context): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val resId = when (hour) {
        in 5..11 -> R.string.greeting_morning
        in 12..16 -> R.string.greeting_afternoon
        in 17..20 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }
    return context.getString(resId)
}

/**
 * Pool of daily tips. Rotates based on day-of-year → same tip for the full day.
 * All tips are about Privora features (no external data needed).
 */
@StringRes
private val DAILY_TIPS: List<Int> = listOf(
    R.string.tip_link_note_person,
    R.string.tip_vault_label_search,
    R.string.tip_face_groups_auto_name,
    R.string.tip_emergency_pin,
    R.string.tip_countdown_timer,
    R.string.tip_long_press_multi_select,
    R.string.tip_duplicates_blurry,
    R.string.tip_ai_notes_sparkle,
    R.string.tip_voice_notes_encrypted,
    R.string.tip_checklist_mode,
    R.string.tip_on_device_only,
    R.string.tip_advanced_settings_pin,
    R.string.tip_backup_downloads,
    R.string.tip_qr_generator,
    R.string.tip_face_threshold_slider,
    // AI Assistant tips — surface the chat surface, action proposals, and the
    // sparkle menus that exist inside notes and translate. Most users don't
    // discover these without a nudge.
    R.string.tip_ai_chat_open,
    R.string.tip_ai_chat_summary,
    R.string.tip_ai_chat_actions,
    R.string.tip_ai_sparkle_anywhere,
    R.string.tip_ai_translate_sentences,
    R.string.tip_ai_calibrate,
    R.string.tip_camera_pinch_zoom,
    R.string.tip_widget_quick_note,
    R.string.tip_voice_clean,
    R.string.tip_wifi_transfer,
    R.string.tip_play_all_voice
)

/**
 * Returns the daily tip string for today. Same tip shows all day, rotates at midnight.
 */
fun getTodayTip(context: Context): String {
    val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val resId = DAILY_TIPS[dayOfYear % DAILY_TIPS.size]
    return context.getString(resId)
}

// ── AI-generated hourly tip (when Gemma is available & vault unlocked) ─────

private const val TIP_PREFS = "daily_ai_tip"
private const val MAX_CACHED_ENTRIES = 48 // 2 days of hourly entries

enum class AiTipCategory(@StringRes val labelRes: Int, val prompt: String) {
    FAMILY(
        R.string.tip_cat_family,
        "a warm family bonding tip or a kind reminder about spending time with loved ones"
    ),
    JOKE(
        R.string.tip_cat_joke,
        "a clean, wholesome short joke that makes someone smile"
    ),
    FOOD(
        R.string.tip_cat_food,
        "a healthy eating or nutrition tip about real everyday food"
    ),
    SPORT(
        R.string.tip_cat_sport,
        "a simple motivation to exercise, walk, stretch, or move today"
    ),
    STRESS(
        R.string.tip_cat_stress,
        "a calming mindfulness tip or stress-relief suggestion for the current moment"
    )
}

/** Holds an AI-generated tip and its category label resource. */
data class AiTipResult(@StringRes val labelRes: Int, val text: String)

private fun getAppLocale(context: Context): Locale {
    val locales = AppCompatDelegate.getApplicationLocales()
    return if (locales.isEmpty) context.resources.configuration.locales[0] else locales[0]!!
}

private fun localeLanguageName(locale: Locale): String {
    return when (locale.language) {
        "ar" -> "Arabic"
        "es" -> "Spanish"
        "fr" -> "French"
        "zh" -> "Chinese"
        else -> "English"
    }
}

/** Cache key: yyyy-MM-dd_HH_lang. Changes every hour and when language changes. */
fun getHourLanguageKey(context: Context): String {
    val ts = SimpleDateFormat("yyyy-MM-dd_HH", Locale.US).format(Calendar.getInstance().time)
    val lang = getAppLocale(context).language
    return "${ts}_$lang"
}

/** Cache key including category: yyyy-MM-dd_HH_lang_CATEGORY. */
private fun getHourLanguageCategoryKey(context: Context, category: AiTipCategory): String {
    return "${getHourLanguageKey(context)}_${category.name}"
}

/** Pick a category deterministically based on the hour — rotates through themes day-long. */
fun pickCategoryForHour(): AiTipCategory {
    val cal = Calendar.getInstance()
    val index = (cal.get(Calendar.DAY_OF_YEAR) * 24 + cal.get(Calendar.HOUR_OF_DAY)) % AiTipCategory.entries.size
    return AiTipCategory.entries[index]
}

/** Next category in cyclic order (for "Next" button). */
fun nextCategory(current: AiTipCategory): AiTipCategory {
    val entries = AiTipCategory.entries
    return entries[(current.ordinal + 1) % entries.size]
}

/** Get cached AI tip for the auto-picked hour category, or null. */
fun getCachedAiTip(context: Context): AiTipResult? {
    return getCachedAiTipForCategory(context, pickCategoryForHour())
}

/** Get cached AI tip for a specific category in this hour+language. */
fun getCachedAiTipForCategory(context: Context, category: AiTipCategory): AiTipResult? {
    val prefs = context.getSharedPreferences(TIP_PREFS, Context.MODE_PRIVATE)
    val text = prefs.getString(getHourLanguageCategoryKey(context, category), null) ?: return null
    return AiTipResult(category.labelRes, text)
}

/**
 * Generate an AI tip for a specific category (or auto-picked if null). Cached per hour+lang+category.
 * Returns null if AI unavailable or generation fails.
 */
suspend fun generateAiTip(context: Context, forceCategory: AiTipCategory? = null): AiTipResult? {
    if (!GemmaRunner.isAvailable(context)) return null
    val category = forceCategory ?: pickCategoryForHour()
    val prefs = context.getSharedPreferences(TIP_PREFS, Context.MODE_PRIVATE)
    val key = getHourLanguageCategoryKey(context, category)
    prefs.getString(key, null)?.let { return AiTipResult(category.labelRes, it) }

    val language = localeLanguageName(getAppLocale(context))
    val prompt = "Write ONE short piece — about 15 words maximum — that is ${category.prompt}. " +
            "Write it in $language. Output ONLY the text itself, no quotes, no preamble, no explanation, no label."

    val text = GemmaRunner.complete(context, prompt, temperature = 0.9)
        ?.trim()
        ?.removeSurrounding("\"")
        ?.removeSurrounding("'")
        ?.removeSurrounding("「", "」")
        ?.trim()

    if (!text.isNullOrBlank()) {
        val editor = prefs.edit()
        editor.putString(key, text)
        // Trim older entries — keep only the most recent MAX_CACHED_ENTRIES
        val sortedKeys = prefs.all.keys.sortedDescending()
        if (sortedKeys.size >= MAX_CACHED_ENTRIES) {
            sortedKeys.drop(MAX_CACHED_ENTRIES - 1).forEach { editor.remove(it) }
        }
        editor.apply()
        return AiTipResult(category.labelRes, text)
    }
    return null
}
