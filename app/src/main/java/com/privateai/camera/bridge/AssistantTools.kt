// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

import android.graphics.Bitmap
import android.util.Base64
import com.privateai.camera.security.ContactRepository
import com.privateai.camera.security.FolderManager
import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.security.NoteRepository
import com.privateai.camera.security.PhotoIndex
import com.privateai.camera.security.SecureNote
import com.privateai.camera.security.VaultCategory
import com.privateai.camera.security.VaultMediaType
import com.privateai.camera.security.VaultRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Parsed reply from the assistant model. Either a final text answer,
 * a tool-call request the host must execute and feed back, or a write
 * action proposed for user confirmation.
 */
sealed class ParsedReply {
    data class Answer(val text: String) : ParsedReply()
    /** A tool call. [query] is the primary param — doubles as query/id/period depending on the tool. */
    data class ToolCall(val name: String, val query: String) : ParsedReply()
    /** A user-data write the model wants to perform. [text] is the chat-bubble copy; [action] is the proposal. */
    data class ActionProposal(val text: String, val action: ProposedAction) : ParsedReply()

    companion object {
        /**
         * Parse the model's JSON output into a [ParsedReply].
         *
         * Tolerant: if the JSON is malformed or doesn't match the schema,
         * falls back to treating the entire raw output as a text answer
         * (so the user still sees something useful — never raw JSON/brackets).
         */
        fun parse(raw: String?): ParsedReply {
            if (raw.isNullOrBlank()) return Answer("I couldn't generate a response. Please try again.")

            // Try to extract JSON object first
            val json = GemmaRunner.extractFirstJsonObject(raw)
            if (json != null) {
                val type = json.optString("type", "answer").lowercase()
                // Tolerate Gemma's frequent misformat where it puts the tool
                // name in the `type` field instead of `type:"tool"`. e.g.
                // `{"type":"search_notes","query":"x"}` is treated the same
                // as `{"type":"tool","name":"search_notes","query":"x"}`.
                //
                // summarize_document / ask_document are no longer advertised
                // in the system prompt (the assistant reads doc text directly
                // from the ATTACHED DOCUMENT block). They stay in this set
                // only as a safety net: if Gemma emits them anyway from
                // training-data memory, the dispatcher returns a graceful
                // error rather than leaking raw JSON to the chat.
                val knownTools = setOf(
                    "search_notes", "fetch_note", "summarize_expenses",
                    "summarize_document", "ask_document", "search_photos"
                )
                val effectiveToolName = when {
                    type == "tool" -> json.optString("name", "")
                    type in knownTools -> type
                    else -> ""
                }
                if (effectiveToolName.isNotBlank()) {
                    // Tool param can be "query", "id", or "period" depending on the tool
                    val query = json.optString("query", "")
                        .ifEmpty { json.optString("id", "") }
                        .ifEmpty { json.optString("period", "") }
                    if (query.isNotBlank()) {
                        return ToolCall(effectiveToolName, query)
                    }
                }
                if (type == "action") {
                    val parsed = AssistantActions.parse(json)
                    if (parsed != null) {
                        // Bubble copy: use the model's `summary` (already mirrored into ProposedAction.summary)
                        // or the optional `text` field if the model also wrote one.
                        val bubble = json.optString("text", "").trim().ifEmpty { parsed.summary }
                        return ActionProposal(cleanupText(bubble), parsed)
                    }
                    // Fall through to text extraction if action JSON was malformed
                }
                val text = json.optString("text", "").trim()
                if (text.isNotEmpty()) return Answer(cleanupText(text))
            }

            // Fallback: strip any remaining JSON artifacts and treat as plain text
            return Answer(cleanupText(raw.trim()))
        }

        /**
         * Clean up model output that may contain JSON-like artifacts, code fences,
         * or leftover structural tokens. The user should never see raw brackets,
         * `"type":"answer"` fields, or triple backtick fences.
         *
         * Gemma sometimes outputs malformed "JSON" without quotes:
         *   {type:answer, text: The summary is…}
         * or partially quoted:
         *   {"type":"answer","text":"The summary…}
         * All must be stripped down to just the human-readable text.
         */
        private fun cleanupText(text: String): String {
            var cleaned = text
            // Strip leading/trailing code fences (```json ... ```)
            cleaned = cleaned.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            // If the entire string looks like a JSON object the parser missed, extract "text" field
            if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
                try {
                    val obj = org.json.JSONObject(cleaned)
                    val inner = obj.optString("text", "").trim()
                    if (inner.isNotEmpty()) return inner
                } catch (_: Exception) { /* not valid JSON, proceed with regex cleanup */ }
            }

            // Truncated-output salvage: when Gemma's reply hits the output budget
            // mid-string, the JSON is unclosed (`{"type":"answer","text":"…content`)
            // and the closed-JSON regexes below all fail to match. Extract whatever
            // text was emitted between `"text":"` and the cutoff so the user at
            // least sees the partial answer instead of raw JSON.
            if (cleaned.startsWith("{")) {
                val openMarker = Regex("""[\"]?text[\"]?\s*:\s*[\"]""").find(cleaned)
                if (openMarker != null) {
                    val start = openMarker.range.last + 1
                    var partial = cleaned.substring(start)
                    // Trim a trailing closing-quote-and-brace if it happens to be there.
                    partial = partial.trimEnd().trimEnd('}', ' ').trimEnd('"', ' ')
                    val decoded = partial
                        .replace("\\n", "\n")
                        .replace("\\t", "\t")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                    if (decoded.isNotBlank() && decoded.length > 10) {
                        return decoded.trim()
                    }
                }
            }

            // Regex: strip {type:answer, text: ...} wrapper (with or without quotes)
            // Handles: {"type":"answer","text":"..."}, {type:answer,text:...}, {"type": "answer", "text": "..."}
            val patterns = listOf(
                // Fully quoted
                Regex("""\{\s*"type"\s*:\s*"answer"\s*,\s*"text"\s*:\s*"(.*?)"\s*\}""", RegexOption.DOT_MATCHES_ALL),
                // Unquoted keys/values — greedy capture after "text:"
                Regex("""\{\s*"?type"?\s*:\s*"?answer"?\s*[,;]\s*"?text"?\s*:\s*"?(.*?)"?\s*\}""", RegexOption.DOT_MATCHES_ALL),
                // Just braces wrapping everything with "text" somewhere inside
                Regex("""\{[^}]*"?text"?\s*:\s*"?(.+?)"?\s*\}""", RegexOption.DOT_MATCHES_ALL)
            )
            for (pattern in patterns) {
                val match = pattern.find(cleaned)
                if (match != null) {
                    val extracted = match.groupValues[1].trim()
                    if (extracted.isNotEmpty() && extracted.length > 10) { // sanity: at least a short sentence
                        return extracted
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\")
                            .trimEnd('"', '}', ' ')
                    }
                }
            }

            // Last resort: if the text starts with { and ends with }, just strip the outer braces
            // and any "type"/"answer" keywords to salvage readable content
            if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
                cleaned = cleaned.removePrefix("{").removeSuffix("}").trim()
                cleaned = cleaned
                    .replace(Regex("""^"?type"?\s*:\s*"?answer"?\s*[,;]?\s*"""), "")
                    .replace(Regex("""^"?text"?\s*:\s*"?"""), "")
                    .trimEnd('"')
                    .trim()
            }

            return cleaned
        }
    }
}

/**
 * Stage 1 tools the assistant can invoke. Each tool reads data and returns
 * a JSON string that gets injected into the follow-up prompt.
 */
object AssistantTools {

    /**
     * Case-insensitive substring search against note titles + bodies.
     * Returns up to 5 matching notes with a title + short snippet around the match.
     */
    fun searchNotes(repo: NoteRepository, query: String): String {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return "[]"

        val hits = repo.listNotes()
            .mapNotNull { note -> scoreNote(note, q)?.let { note to it } }
            .sortedByDescending { it.second }
            .take(5)
            .map { (note, _) ->
                JSONObject().apply {
                    put("title", note.title)
                    put("snippet", extractSnippet(note.content, q, 120))
                    put("tags", JSONArray(note.tags))
                    put("modified", note.modifiedAt)
                }
            }

        return JSONArray(hits).toString()
    }

    /** Score a note against the query. Returns null if no match. */
    private fun scoreNote(note: SecureNote, query: String): Int? {
        val titleMatch = note.title.lowercase().contains(query)
        val bodyMatch = note.content.lowercase().contains(query)
        val tagMatch = note.tags.any { it.lowercase().contains(query) }
        if (!titleMatch && !bodyMatch && !tagMatch) return null
        // Title hits are more valuable than body hits
        return (if (titleMatch) 10 else 0) + (if (tagMatch) 5 else 0) + (if (bodyMatch) 1 else 0)
    }

    /** Extract a snippet of ~maxLen characters centered on the first occurrence of query. */
    private fun extractSnippet(content: String, query: String, maxLen: Int): String {
        val idx = content.lowercase().indexOf(query)
        if (idx < 0) return content.take(maxLen)
        val start = (idx - maxLen / 3).coerceAtLeast(0)
        val end = (start + maxLen).coerceAtMost(content.length)
        val snippet = content.substring(start, end).trim()
        return buildString {
            if (start > 0) append("…")
            append(snippet)
            if (end < content.length) append("…")
        }
    }

    /**
     * Fetch the full content of a note by ID. Returns a JSON object with title + full body.
     * Lets the assistant read, discuss, and summarize specific notes.
     */
    fun fetchNote(repo: NoteRepository, noteId: String): String {
        val note = repo.listNotes().find { it.id == noteId }
            ?: return """{"error":"Note not found"}"""
        return JSONObject().apply {
            put("title", note.title)
            put("content", note.content.take(3000)) // cap to stay within context budget
            put("tags", JSONArray(note.tags))
            put("modified", note.modifiedAt)
        }.toString()
    }

    /**
     * Aggregate expenses for a time period ("week" / "month" / "year").
     * Returns category totals, top items, and the overall total — richer than the snapshot's 30-item cap.
     */
    fun summarizeExpenses(repo: InsightsRepository, period: String): String {
        val now = System.currentTimeMillis()
        val cutoff = when (period.lowercase()) {
            "week" -> now - 7L * 24 * 60 * 60 * 1000
            "year" -> now - 365L * 24 * 60 * 60 * 1000
            else -> { // default: month
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, -1)
                cal.timeInMillis
            }
        }

        val expenses = repo.listExpenses().filter { it.date >= cutoff }
        val total = expenses.sumOf { it.amount }
        val byCat = expenses.groupBy { it.category }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
        val topItems = expenses.sortedByDescending { it.amount }.take(5)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        return JSONObject().apply {
            put("period", period)
            put("total", "%.2f".format(total))
            put("count", expenses.size)
            put("byCategory", JSONArray().apply {
                byCat.forEach { (cat, sum) ->
                    put(JSONObject().apply { put("category", cat); put("total", "%.2f".format(sum)) })
                }
            })
            put("topItems", JSONArray().apply {
                topItems.forEach { e ->
                    put(JSONObject().apply {
                        put("description", e.description.ifEmpty { e.category })
                        put("amount", "%.2f".format(e.amount))
                        put("date", dateFmt.format(Date(e.date)))
                        put("category", e.category)
                    })
                }
            })
        }.toString()
    }

    // ───────── Document Q&A ("Ask My Documents") — Phase 0 ─────────
    //
    // Phase 0 is intentionally simple: load the OCR text from the vault
    // sidecar, clip to fit Gemma's context, and hand the whole thing to
    // the model. No retrieval, no chunking, no embeddings. The whole
    // point of this spike is to test whether on-device LLM + scanned
    // document text produces good-enough answers before committing to
    // the full RAG infrastructure (Phase 1).

    /** Hard cap on OCR text fed to the model. ~6 KB ≈ 1.5 K tokens; leaves
     *  room for prompt + answer in Gemma 4 E2B's effective window. */
    private const val DOC_TEXT_BUDGET = 6144

    /**
     * Locate a document by id (the encrypted-file basename, e.g.
     * `scan_1714944000000.pdf`) anywhere in the vault — categories first,
     * then custom folders. Returns null if not found.
     */
    private fun findDocument(vault: VaultRepository, id: String): com.privateai.camera.security.VaultPhoto? {
        // Most scanned docs land in the SCAN category — check there first for speed.
        // (Custom folders are walked by the live ATTACHED DOCUMENT path in
        // AssistantScreen.runAssistantTurn; this fn is only hit by the
        // deprecated summarize_document / ask_document tools.)
        VaultCategory.entries.firstOrNull { cat ->
            vault.listPhotos(cat).any { it.id == id }
        }?.let { cat ->
            return vault.listPhotos(cat).firstOrNull { it.id == id }
        }
        return null
    }

    /**
     * Return a JSON payload describing a vault document for the assistant
     * to summarize. Includes the OCR text (clipped) plus a flag if it was
     * truncated, so Gemma can hedge appropriately.
     */
    fun summarizeDocument(vault: VaultRepository, docId: String): String {
        val doc = findDocument(vault, docId)
            ?: return """{"error":"Document not found in vault."}"""
        val text = vault.loadOcr(doc)
            ?: return """{"error":"This document doesn't have searchable text. Re-scan it through the Privora scanner to enable Q&A."}"""
        val clipped = text.length > DOC_TEXT_BUDGET
        val payload = if (clipped) text.take(DOC_TEXT_BUDGET) else text
        return JSONObject().apply {
            put("docId", doc.id)
            put("text", payload)
            put("truncated", clipped)
            put("originalLength", text.length)
            put("mediaType", doc.mediaType.name)
        }.toString()
    }

    /**
     * Return a JSON payload for an "ask a question" turn. The query string
     * carries the doc id and the user's question, separated by a `|` —
     * matches the existing single-string tool param convention rather than
     * adding a second field to ParsedReply.
     *
     * Format: `<docId>|<question>`
     */
    fun askDocument(vault: VaultRepository, query: String): String {
        val sep = query.indexOf('|')
        if (sep < 0) {
            return """{"error":"ask_document needs '<docId>|<question>'."}"""
        }
        val docId = query.substring(0, sep).trim()
        val question = query.substring(sep + 1).trim()
        if (docId.isEmpty() || question.isEmpty()) {
            return """{"error":"ask_document needs both a docId and a question."}"""
        }
        val doc = findDocument(vault, docId)
            ?: return """{"error":"Document not found in vault."}"""
        val text = vault.loadOcr(doc)
            ?: return """{"error":"This document doesn't have searchable text. Re-scan it through the Privora scanner to enable Q&A."}"""
        val clipped = text.length > DOC_TEXT_BUDGET
        val payload = if (clipped) text.take(DOC_TEXT_BUDGET) else text
        return JSONObject().apply {
            put("docId", doc.id)
            put("question", question)
            put("text", payload)
            put("truncated", clipped)
            put("originalLength", text.length)
        }.toString()
    }

    // ───────── Photo search (Track F) ─────────
    //
    // Bridges Gemma's free-form "Anas with dogs"-style queries to
    // [PhotoIndex.searchByPersonAndTags]. Returns up to [MAX_PHOTO_THUMBS]
    // base64-encoded thumbnails so the host can render them in a chat bubble
    // without inventing a separate media channel through the string-only
    // tool-result pipeline.

    private const val MAX_PHOTO_THUMBS = 6
    /** Hard cap on b64-encoded JPEG size; thumbnails are typically <20 KB. */
    private const val THUMB_JPEG_QUALITY = 70

    /**
     * Find vault photos by mixing a person hint and topic words. Returns a JSON
     * payload the host parses for both the model's textual context (count,
     * detectedPerson, residualQuery) AND for rendering the inline thumb strip
     * in the chat bubble.
     */
    fun searchPhotos(
        photoIndex: PhotoIndex,
        contactRepo: ContactRepository,
        vault: VaultRepository,
        folderManager: FolderManager,
        query: String
    ): String {
        if (query.isBlank()) {
            return """{"error":"search_photos needs a query."}"""
        }

        // Detect "last saturday" / "yesterday" / "this month"-style phrases
        // and lift them out of the query before the label/face search. Pure
        // date-only queries ("show me last saturday photos") fall through to
        // a date-only filter over the full vault.
        val dateMatch = parseDateRange(query)
        val datelessQuery = (dateMatch?.let { stripDatePhrase(query, it.matchedPhrase) } ?: query).trim()
        // Strip filler words the model often includes from natural-language
        // prompts ("show me girl photos" → emits query "girl photos", where
        // "photos" then gets AND-intersected as a literal label and drops
        // the result count from 87 to 7). The user already invoked a photo
        // tool — they don't need "photo/photos/pictures/images" tokens
        // surviving into the per-token search.
        val cleanedQuery = stripNoiseTokens(datelessQuery)

        // Build the full photo lookup once (categories + folders).
        val fromCats = vault.listAllPhotos()
        val fromFolders = folderManager.listAllFolders().flatMap { f ->
            vault.listFolderItems(folderManager.getFolderDir(f.id))
        }
        val allPhotos = (fromCats + fromFolders).distinctBy { it.id }
        val photoById = allPhotos.associateBy { it.id }

        // Three modes:
        //  • pure date  → all photos in range, newest first
        //  • date+rest  → run searchByPersonAndTags on rest, then filter by date
        //  • no date    → run searchByPersonAndTags directly
        val detectedPerson: String?
        val residualQuery: String
        val rankedPhotos: List<com.privateai.camera.security.VaultPhoto>
        if (cleanedQuery.isBlank()) {
            // Pure date-only query
            detectedPerson = null
            residualQuery = ""
            val range = dateMatch?.range
            rankedPhotos = if (range != null) {
                allPhotos.filter { it.timestamp in range }
                    .sortedByDescending { it.timestamp }
            } else emptyList()
        } else {
            val result = photoIndex.searchByPersonAndTags(contactRepo, cleanedQuery, limit = 500)
            detectedPerson = result.detectedPerson
            residualQuery = result.residualQuery
            val matchedIds = result.photoIds
            val matched = matchedIds.mapNotNull { photoById[it] }
            rankedPhotos = if (dateMatch != null) {
                matched.filter { it.timestamp in dateMatch.range }
            } else matched
        }

        if (rankedPhotos.isEmpty()) {
            return JSONObject().apply {
                put("detectedPerson", detectedPerson ?: JSONObject.NULL)
                put("residualQuery", residualQuery)
                put("detectedDate", dateMatch?.matchedPhrase ?: JSONObject.NULL)
                put("total", 0)
                put("photos", JSONArray())
            }.toString()
        }

        val topPhotos = rankedPhotos.take(MAX_PHOTO_THUMBS)

        val thumbs = JSONArray()
        for (photo in topPhotos) {
            val id = photo.id
            val bmp = vault.loadThumbnail(photo) ?: continue
            val b64 = bitmapToB64(bmp)
            bmp.recycle()
            thumbs.put(JSONObject().apply {
                put("id", id)
                put("thumb", b64)
                put("ts", photo.timestamp)
            })
        }

        return JSONObject().apply {
            put("detectedPerson", detectedPerson ?: JSONObject.NULL)
            put("residualQuery", residualQuery)
            put("detectedDate", dateMatch?.matchedPhrase ?: JSONObject.NULL)
            put("total", rankedPhotos.size)
            put("photos", thumbs)
        }.toString()
    }

    private fun bitmapToB64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, THUMB_JPEG_QUALITY, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    /** Detected date phrase in a query, with the millis range it covers and the phrase that matched. */
    private data class DateRangeMatch(val range: LongRange, val matchedPhrase: String)

    /**
     * Parse common date phrases out of a search query. Recognizes:
     *   • today, yesterday
     *   • this week / last week, this month / last month, this year / last year
     *   • last <weekday> (last saturday, last monday, ...)
     *   • ISO date "YYYY-MM-DD"
     * Returns null when no phrase is found. The matched phrase is returned so
     * the caller can strip it from the query before doing tag/face search.
     */
    private fun parseDateRange(query: String, nowMillis: Long = System.currentTimeMillis()): DateRangeMatch? {
        val lc = query.lowercase()

        fun calAt(): Calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        fun startOfDay(c: Calendar) {
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        }
        fun endOfDay(c: Calendar) {
            c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59)
            c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999)
        }

        // ISO date: YYYY-MM-DD
        Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""").find(lc)?.let { m ->
            val y = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt() - 1; val d = m.groupValues[3].toInt()
            val c = Calendar.getInstance().apply { clear(); set(y, mo, d) }
            val start = c.timeInMillis
            endOfDay(c); val end = c.timeInMillis
            return DateRangeMatch(start..end, m.value)
        }

        if (lc.contains("yesterday")) {
            val c = calAt(); c.add(Calendar.DAY_OF_YEAR, -1); startOfDay(c); val start = c.timeInMillis
            endOfDay(c); return DateRangeMatch(start..c.timeInMillis, "yesterday")
        }
        if (lc.contains("today")) {
            val c = calAt(); startOfDay(c); val start = c.timeInMillis
            endOfDay(c); return DateRangeMatch(start..c.timeInMillis, "today")
        }

        if (lc.contains("last week")) {
            val c = calAt()
            // Move to Monday of this week, then subtract 7 days → last Monday
            val dow = c.get(Calendar.DAY_OF_WEEK)
            val daysSinceMonday = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
            c.add(Calendar.DAY_OF_YEAR, -daysSinceMonday - 7); startOfDay(c)
            val start = c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 6); endOfDay(c)
            return DateRangeMatch(start..c.timeInMillis, "last week")
        }
        if (lc.contains("this week")) {
            val c = calAt()
            val dow = c.get(Calendar.DAY_OF_WEEK)
            val daysSinceMonday = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
            c.add(Calendar.DAY_OF_YEAR, -daysSinceMonday); startOfDay(c)
            val start = c.timeInMillis
            val end = calAt().also { endOfDay(it) }.timeInMillis
            return DateRangeMatch(start..end, "this week")
        }

        if (lc.contains("last month")) {
            val c = calAt(); c.add(Calendar.MONTH, -1); c.set(Calendar.DAY_OF_MONTH, 1); startOfDay(c)
            val start = c.timeInMillis
            c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH)); endOfDay(c)
            return DateRangeMatch(start..c.timeInMillis, "last month")
        }
        if (lc.contains("this month")) {
            val c = calAt(); c.set(Calendar.DAY_OF_MONTH, 1); startOfDay(c)
            val start = c.timeInMillis
            val end = calAt().also { endOfDay(it) }.timeInMillis
            return DateRangeMatch(start..end, "this month")
        }

        if (lc.contains("last year")) {
            val c = calAt(); c.add(Calendar.YEAR, -1)
            c.set(Calendar.MONTH, 0); c.set(Calendar.DAY_OF_MONTH, 1); startOfDay(c)
            val start = c.timeInMillis
            c.set(Calendar.MONTH, 11); c.set(Calendar.DAY_OF_MONTH, 31); endOfDay(c)
            return DateRangeMatch(start..c.timeInMillis, "last year")
        }
        if (lc.contains("this year")) {
            val c = calAt(); c.set(Calendar.MONTH, 0); c.set(Calendar.DAY_OF_MONTH, 1); startOfDay(c)
            val start = c.timeInMillis
            val end = calAt().also { endOfDay(it) }.timeInMillis
            return DateRangeMatch(start..end, "this year")
        }

        // "last <weekday>" — finds the most recent occurrence of that weekday
        // strictly before today. e.g. on Friday, "last saturday" is 6 days ago.
        val weekdays = mapOf(
            "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY,
            "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY,
            "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY,
            "sunday" to Calendar.SUNDAY
        )
        for ((name, dow) in weekdays) {
            val phrase = "last $name"
            if (lc.contains(phrase)) {
                val c = calAt()
                var days = (c.get(Calendar.DAY_OF_WEEK) - dow + 7) % 7
                if (days == 0) days = 7
                c.add(Calendar.DAY_OF_YEAR, -days); startOfDay(c)
                val start = c.timeInMillis
                endOfDay(c)
                return DateRangeMatch(start..c.timeInMillis, phrase)
            }
        }

        return null
    }

    /** Remove the matched date phrase from the query, case-insensitive. */
    private fun stripDatePhrase(query: String, phrase: String): String {
        return Regex("\\s*${Regex.escape(phrase)}\\s*", RegexOption.IGNORE_CASE).replace(query, " ").trim()
    }

    /**
     * Drop filler words that natural-language phrasings often include but
     * that are not actual search labels — "photo / photos / picture /
     * pictures / image / images / show / find / any / from / of / the / a /
     * an / with / and / me / my". Keeps the remaining tokens for the
     * per-token AND search. Preserves order. Returns the original query if
     * stripping would empty it (so a pure noise query like "show me photos"
     * still returns ALL photos rather than zero).
     */
    private fun stripNoiseTokens(query: String): String {
        if (query.isBlank()) return query
        val noise = setOf(
            "photo", "photos", "picture", "pictures", "image", "images", "pic", "pics",
            "show", "find", "look", "any", "have", "do", "i",
            "from", "of", "the", "a", "an", "with", "and", "in", "at", "on", "to",
            "me", "my", "for", "are", "is"
        )
        val tokens = query.trim().split(Regex("\\s+"))
        val kept = tokens.filter { it.lowercase().trim('?', '.', ',', '!') !in noise }
        return if (kept.isEmpty()) query.trim() else kept.joinToString(" ")
    }
}
