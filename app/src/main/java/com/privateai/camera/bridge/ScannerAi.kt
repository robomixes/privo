// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Smart Scanner — runs a single Gemma vision call over a freshly-scanned page
 * to classify the document, suggest a filename + destination folder, and
 * extract a few key fields. The scanner UI's post-save bottom sheet renders
 * the result so the user can accept or skip.
 *
 * Bypasses Gemma's number-mis-copy class of bugs by parsing the JSON
 * deterministically host-side — same pattern as the Assistant's search_photos
 * / summarize_expenses summaries. The model only chooses values; we never
 * round-trip its prose summarization.
 */
object ScannerAi {

    private const val TAG = "ScannerAi"

    enum class DocType { RECEIPT, BUSINESS_CARD, ID, INVOICE, RECIPE, HANDWRITTEN_NOTE, GENERIC }

    /** Parsed Smart Scanner analysis. All fields user-editable in the sheet. */
    data class Suggestion(
        val type: DocType,
        val title: String,
        val folder: String,
        val fields: Map<String, String>
    )

    /**
     * Run Gemma vision on the given bitmap and return a parsed suggestion.
     * [existingFolders] lets the model reuse an existing folder name when it
     * fits, rather than inventing a new one.
     *
     * Returns null on tool failure, missing AI, or unparseable model output.
     */
    suspend fun analyze(
        context: Context,
        bitmap: Bitmap,
        existingFolders: List<String>
    ): Suggestion? {
        if (!GemmaRunner.isAvailable(context)) return null

        // Gemma describeImage takes a file path — write the bitmap to a temp JPEG.
        val tempFile = File(context.cacheDir, "smartscan_${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            val raw = GemmaRunner.describeImage(
                context, tempFile.absolutePath, GemmaPrompts.smartScanAnalyze(existingFolders)
            ) ?: return null

            Log.d(TAG, "raw analyze reply: ${raw.take(300)}")
            return parse(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Smart-scan analyze failed: ${e.message}")
            return null
        } finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }
    }

    /** Parse Gemma's JSON reply into a [Suggestion]. Tolerant of code fences. */
    fun parse(raw: String): Suggestion? {
        return try {
            val cleaned = raw.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val json = GemmaRunner.extractFirstJsonObject(cleaned) ?: return null

            val typeStr = json.optString("type", "generic").trim().lowercase()
                .replace(" ", "_").replace("-", "_")
            val type = when (typeStr) {
                "receipt" -> DocType.RECEIPT
                "business_card", "card" -> DocType.BUSINESS_CARD
                "id", "id_card", "identity" -> DocType.ID
                "invoice", "bill" -> DocType.INVOICE
                "recipe" -> DocType.RECIPE
                "handwritten_note", "handwritten", "note" -> DocType.HANDWRITTEN_NOTE
                else -> DocType.GENERIC
            }

            val title = json.optString("title", "").trim()
                .replace(Regex("[\\\\/:*?\"<>|]"), "")  // strip filename-illegal chars
                .take(60)
            val folder = json.optString("folder", "").trim()
                .replace(Regex("[\\\\/:*?\"<>|]"), "")
                .take(40)

            val fieldsObj = json.optJSONObject("fields") ?: JSONObject()
            val fields = mutableMapOf<String, String>()
            val keys = fieldsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = fieldsObj.optString(k, "").trim()
                if (k.isNotBlank() && v.isNotBlank()) fields[k] = v
            }

            Suggestion(type, title, folder, fields)
        } catch (e: Exception) {
            Log.w(TAG, "parse failed: ${e.message}")
            null
        }
    }
}
