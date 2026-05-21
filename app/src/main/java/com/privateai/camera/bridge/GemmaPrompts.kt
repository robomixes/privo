// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

/**
 * Prompt templates for Gemma 4 notes intelligence features.
 * All prompts are engineered for concise, actionable output.
 */
object GemmaPrompts {

    /** System instruction for all notes-related tasks. */
    const val NOTES_SYSTEM = "You are a helpful writing assistant inside a private notes app. " +
            "Be concise and direct. Never include explanations unless asked. " +
            "Output only the requested content, no preamble. " +
            "Always respond in the same language as the user's input text."

    /** Summarize a long note into bullet points. */
    fun summarize(noteContent: String): String = buildString {
        append("Summarize the following note into 3-5 concise bullet points. ")
        append("Use '• ' prefix for each point. Output only the bullet points.\n\n")
        append(noteContent)
    }

    /** Generate a title from note body. Must match the note's language. */
    fun generateTitle(noteContent: String): String = buildString {
        append("Generate a short title (3-8 words) for this note. ")
        append("The title MUST be in the same language as the note content. ")
        append("Output only the title, nothing else.\n\n")
        append(noteContent.take(500))
    }

    /** Extract actionable tasks from free text into checklist format. */
    fun extractChecklist(noteContent: String): String = buildString {
        append("Extract all actionable tasks from this text. ")
        append("Output each task on a new line in this exact format: - [ ] task description\n")
        append("If no tasks found, output the original text unchanged.\n\n")
        append(noteContent)
    }

    /** Smart rewrite — change tone or style. */
    fun rewrite(text: String, style: RewriteStyle): String = buildString {
        append(when (style) {
            RewriteStyle.SHORTEN -> "Rewrite this text to be shorter and more concise. Keep the meaning."
            RewriteStyle.EXPAND -> "Expand this text with more detail and explanation."
            RewriteStyle.FORMAL -> "Rewrite this text in a formal, professional tone."
            RewriteStyle.CASUAL -> "Rewrite this text in a casual, friendly tone."
            RewriteStyle.FIX_GRAMMAR -> "Fix all grammar, spelling, and punctuation errors in this text. Output only the corrected text."
        })
        append("\n\n")
        append(text)
    }

    /** Continue writing from where the user left off. */
    fun continueWriting(noteContent: String): String = buildString {
        append("Continue writing this note naturally from where it left off. ")
        append("Write 2-3 sentences that follow the same style and topic.\n\n")
        append(noteContent)
    }

    /** Describe an image for the vault photo index. Localized to the device language. */
    fun describePhoto(): String {
        val lang = uiLanguageName()
        return "Describe this image in one concise sentence. " +
                "Focus on the main subject, activity, and setting. " +
                "Reply in $lang."
    }

    /**
     * Generate short, lowercase, comma-separated tags for an image.
     *
     * The reply is parsed by splitting on commas and trimming each token —
     * the prompt is deliberately strict about format because the parser is
     * intentionally dumb (no JSON, no full sentences, no enumeration markers).
     *
     * Tag words themselves are emitted in the user's UI language so they're
     * usable as search keywords later (a French user shouldn't have to search
     * "cat" instead of "chat"). **Exception**: Arabic falls back to English
     * tag words — Gemma's structured-output reliability drops in Arabic (often
     * returns a full sentence instead of a comma list, or mixes RTL/LTR
     * fragments that the parser drops). The descriptive sentence in
     * `describePhoto()` is still emitted in Arabic; only the strict
     * comma-list format is moved to English here.
     */
    fun generateTags(): String {
        val lang = if (java.util.Locale.getDefault().language == "ar") "English" else uiLanguageName()
        return "List 5 to 8 short tags in $lang for this image describing the subject, objects, " +
                "setting, mood, and time of day. Output only the tags, comma-separated, " +
                "lowercase, no punctuation other than commas, no full sentences, no numbering."
    }

    /**
     * Wrap a user-typed image question with a "Reply in <UI language>" hint
     * so the answer matches the UI language (the user's typed question may
     * itself be short / ambiguous about language).
     */
    fun askAboutImage(question: String): String {
        val lang = uiLanguageName()
        return "$question\n\nReply in $lang."
    }

    /**
     * Smart Scanner: ask Gemma to classify the scanned document, suggest a
     * filename and destination folder, and extract a few key fields.
     *
     * The reply MUST be a single JSON object so the host can deterministically
     * parse it. Free-form fields ("title", folder name, extracted values) are
     * in the user's UI language; the discrete `type` enum is always English
     * because the host matches it against known categories (receipt /
     * business_card / id / invoice / recipe / handwritten_note / generic).
     *
     * [existingFolders] is a comma-separated list of folder names the user
     * already has; the model is asked to reuse one when it fits the doc type
     * rather than inventing a new one.
     */
    fun smartScanAnalyze(existingFolders: List<String>): String {
        val lang = uiLanguageName()
        val foldersHint = if (existingFolders.isEmpty()) {
            "The user has no folders yet — pick a sensible new folder name."
        } else {
            "The user's existing folders are: ${existingFolders.joinToString(", ") { "\"$it\"" }}. " +
                "Reuse one of these names when it fits, otherwise suggest a sensible new folder name."
        }
        return buildString {
            append("Analyze this scanned document and return a single JSON object with these keys:\n")
            append("  \"type\": one of receipt, business_card, id, invoice, recipe, handwritten_note, generic\n")
            append("  \"title\": a short filename for this document (3-6 words, no extension), in $lang\n")
            append("  \"folder\": a folder name where this document belongs, in $lang. $foldersHint\n")
            append("  \"fields\": an object of key/value pairs of the most important data on the document. ")
            append("Choose keys appropriate to the type — e.g. for a receipt: merchant, total, date, currency; ")
            append("for a business card: name, phone, email, company, title; ")
            append("for an invoice: invoice_number, vendor, total, due_date; ")
            append("for an id: name, id_number, date_of_birth; ")
            append("for a recipe: title, servings; ")
            append("for a handwritten_note or generic: leave fields empty.\n")
            append("Only emit fields that are clearly visible. Copy numbers, dates and names character-by-character from the document. ")
            append("If a field is illegible or missing, omit it entirely instead of guessing.\n")
            append("Output ONLY the JSON object, no extra text, no markdown, no code fences.")
        }
    }

    /**
     * Prompt for the Detect module's per-detection "Describe with AI" action.
     * YOLO has already named the object coarsely ([yoloClass]); Gemma's job
     * is to refine — make/model for cars, breed/species for animals, type
     * for objects — in one concise sentence in the UI language.
     */
    fun describeDetection(yoloClass: String): String {
        val lang = uiLanguageName()
        return "This image shows a $yoloClass. " +
                "Identify it more specifically — e.g. make and model for a car, " +
                "breed or species for an animal, brand or type for an object. " +
                "Reply in one concise sentence in $lang."
    }

    /**
     * Map the active app locale to a Gemma-friendly English name. Used as a
     * `Reply in <X>` hint in vision prompts so the description / tags match
     * the UI language. Reads `Locale.getDefault()` which AppCompat keeps in
     * sync with the per-app language picker.
     */
    private fun uiLanguageName(): String {
        return when (java.util.Locale.getDefault().language) {
            "fr" -> "French"
            "es" -> "Spanish"
            "zh" -> "Chinese"
            "ar" -> "Arabic"
            "tr" -> "Turkish"
            "en" -> "English"
            else -> java.util.Locale.getDefault().getDisplayLanguage(java.util.Locale.ENGLISH)
        }
    }
}

enum class RewriteStyle {
    SHORTEN,
    EXPAND,
    FORMAL,
    CASUAL,
    FIX_GRAMMAR
}
