// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.security

import android.content.ContentValues
import android.graphics.Bitmap
import android.util.Log
import com.privateai.camera.bridge.FaceEmbedder
import com.privateai.camera.bridge.ImageClassifier
import com.privateai.camera.bridge.OnnxDetector
import com.privateai.camera.util.BlurDetector
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONArray

/**
 * Manages an encrypted index that maps vault photo IDs to their AI-generated
 * labels, feature vectors, and blur scores. Backed by SQLCipher via PrivoraDatabase.
 */
class PhotoIndex(private val database: PrivoraDatabase) {

    companion object {
        private const val TAG = "PhotoIndex"
    }

    private val db: SQLiteDatabase get() = database.db

    /**
     * Named face identities -- each has a stable ID, a user-given name, and a centroid embedding.
     */
    data class FaceIdentity(
        val id: String,           // stable UUID
        val name: String,         // display name (can be renamed freely)
        val centroid: FloatArray,  // average embedding of all faces in this group
        val personId: String? = null  // linked PrivateContact.id (survives renames)
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FaceIdentity) return false
            return id == other.id && name == other.name && personId == other.personId && centroid.contentEquals(other.centroid)
        }
        override fun hashCode(): Int = id.hashCode()
    }

    data class FaceEntry(
        val box: FloatArray,      // [x, y, w, h] normalized 0-1
        val embedding: FloatArray // 128-dim
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FaceEntry) return false
            return box.contentEquals(other.box) &&
                    embedding.contentEquals(other.embedding)
        }

        override fun hashCode(): Int {
            var result = box.contentHashCode()
            result = 31 * result + embedding.contentHashCode()
            return result
        }
    }

    data class PhotoIndexEntry(
        val labels: List<String>,        // top-5 ImageNet labels
        val scores: List<Float>,         // confidence scores for labels
        val featureVector: FloatArray?,   // 1000-dim for similarity
        val blurScore: Double,
        val faces: List<FaceEntry> = emptyList(),
        val description: String = ""     // AI-generated description (Gemma 4)
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PhotoIndexEntry) return false
            return labels == other.labels &&
                    scores == other.scores &&
                    featureVector.contentEquals(other.featureVector) &&
                    blurScore == other.blurScore &&
                    faces == other.faces &&
                    description == other.description
        }

        override fun hashCode(): Int {
            var result = labels.hashCode()
            result = 31 * result + scores.hashCode()
            result = 31 * result + (featureVector?.contentHashCode() ?: 0)
            result = 31 * result + blurScore.hashCode()
            result = 31 * result + faces.hashCode()
            result = 31 * result + description.hashCode()
            return result
        }
    }

    /**
     * Check if a photo is already indexed.
     */
    fun isIndexed(photoId: String): Boolean {
        db.rawQuery("SELECT 1 FROM photo_index WHERE photo_id = ?", arrayOf(photoId)).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    /**
     * Remove deleted photos from the index.
     */
    fun removeEntries(photoIds: Set<String>) {
        if (photoIds.isEmpty()) return
        db.beginTransaction()
        try {
            for (id in photoIds) {
                db.delete("photo_index", "photo_id = ?", arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Index a single photo. Returns the entry.
     */
    fun indexPhoto(
        photoId: String,
        bitmap: Bitmap,
        classifier: ImageClassifier,
        blurDetector: BlurDetector = BlurDetector,
        faceEmbedder: FaceEmbedder? = null,
        detector: OnnxDetector? = null
    ): PhotoIndexEntry {
        if (bitmap.isRecycled) return PhotoIndexEntry(emptyList(), emptyList(), null, -1.0, emptyList())

        // 1. Detect objects with YOLOv8n (COCO 80 — optional, skipped in batch mode)
        val detections = if (detector != null && !bitmap.isRecycled) {
            try { detector.detect(bitmap, minConfidence = 0.45f) }
            catch (_: Throwable) { emptyList() }
        } else emptyList()

        // 2. Classify + get embedding in ONE inference (fused — saves ~150ms per photo)
        val (classifications, featureVector) = if (!bitmap.isRecycled) {
            classifier.classifyWithEmbedding(bitmap)
        } else emptyList<Pair<String, Float>>() to FloatArray(0)

        // 3a. Preserve any Gemma-generated tags (score >= 0.99) from a previous
        //     run so re-indexing doesn't silently strip them. mergeAiTags writes
        //     these at score 1.0; ONNX classifier scores top out around 0.85.
        val existingGemmaTags = getLabelsWithScores(photoId).filter { it.second >= 0.99f }

        // 3b. Merge: existing Gemma tags first, then YOLO detections, then
        //     ImageNet classifier. Case-insensitive dedup throughout.
        val merged = mutableListOf<Pair<String, Float>>()
        val seen = mutableSetOf<String>()
        for ((label, score) in existingGemmaTags) {
            if (label.lowercase() !in seen) { seen.add(label.lowercase()); merged.add(label to score) }
        }
        for (det in detections.distinctBy { it.className }) {
            val label = det.className.replaceFirstChar { it.uppercase() }
            if (label.lowercase() !in seen) { seen.add(label.lowercase()); merged.add(label to det.confidence) }
        }
        for ((label, score) in classifications) {
            if (label.lowercase() !in seen) { seen.add(label.lowercase()); merged.add(label to score) }
        }
        // Cap a touch higher than the old 8 to leave room for both Gemma + ONNX.
        val labels = merged.take(12).map { it.first }
        val scores = merged.take(12).map { it.second }

        // 4. Blur score
        val blurScore = if (!bitmap.isRecycled) blurDetector.getBlurScore(bitmap) else -1.0

        // 5. Detect faces and compute embeddings
        val faces = if (!bitmap.isRecycled) {
            faceEmbedder?.detectAndEmbed(bitmap)?.map { (box, emb) ->
                FaceEntry(floatArrayOf(box.left, box.top, box.width(), box.height()), emb)
            } ?: emptyList()
        } else emptyList()

        // 5b. Create face identities for new faces that don't match any existing identity
        if (faces.isNotEmpty()) {
            val existingIdentities = loadFaceIdentitiesList()
            db.beginTransaction()
            try {
                for (face in faces) {
                    val matchesExisting = existingIdentities.any { identity ->
                        cosineSimilarity(face.embedding, identity.centroid) > 0.40f
                    }
                    if (!matchesExisting) {
                        val newId = java.util.UUID.randomUUID().toString()
                        val cv = ContentValues().apply {
                            put("id", newId)
                            put("name", "")
                            put("centroid", face.embedding.copyOf().toBlob())
                        }
                        db.insertWithOnConflict("face_identities", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        // 6. Store in database.
        //    Use ensureRow + UPDATE instead of INSERT OR REPLACE so the
        //    `description` column (set by Gemma describePhoto) is preserved
        //    across re-indexing. CONFLICT_REPLACE would delete the existing
        //    row and the new ContentValues doesn't carry `description`, so
        //    every re-index would silently wipe Gemma descriptions.
        db.beginTransaction()
        try {
            ensureRow(photoId)
            val cv = ContentValues().apply {
                put("labels", JSONArray(labels).toString())
                put("scores", JSONArray(scores.map { it.toDouble() }).toString())
                put("feature_vector", featureVector?.toBlob())
                put("blur_score", blurScore)
                put("indexed_at", System.currentTimeMillis())
            }
            db.update("photo_index", cv, "photo_id = ?", arrayOf(photoId))

            // Delete old face entries for this photo then insert new ones
            db.delete("face_entries", "photo_id = ?", arrayOf(photoId))
            faces.forEachIndexed { i, face ->
                val faceCv = ContentValues().apply {
                    put("photo_id", photoId)
                    put("face_index", i)
                    put("box", face.box.toBlob())
                    put("embedding", face.embedding.toBlob())
                }
                db.insert("face_entries", null, faceCv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        val entry = PhotoIndexEntry(labels, scores, featureVector, blurScore, faces)
        Log.d(TAG, "Indexed photo $photoId: ${labels.take(3)}, blur=$blurScore, faces=${faces.size}")
        return entry
    }

    /** Map common search terms to ImageNet labels that don't match intuitively. */
    private val searchAliases = mapOf(
            "person" to listOf("jean", "suit", "jersey", "sweatshirt", "T-shirt", "bikini", "miniskirt", "bow_tie", "wig", "sunglass", "sunglasses", "mask", "lipstick", "hair_spray"),
            "people" to listOf("jean", "suit", "jersey", "sweatshirt", "T-shirt", "bikini", "miniskirt", "bow_tie", "wig", "sunglass", "sunglasses"),
            "selfie" to listOf("sunglass", "sunglasses", "wig", "lipstick", "hair_spray", "mask", "jean", "T-shirt", "jersey"),
            "face" to listOf("wig", "sunglass", "sunglasses", "lipstick", "mask", "hair_spray"),
            "food" to listOf("pizza", "cheeseburger", "hotdog", "ice_cream", "burrito", "plate", "restaurant", "menu", "cup", "bowl", "tray", "bakery", "meat_loaf", "potpie"),
            "animal" to listOf("dog", "cat", "bird", "fish", "horse", "cow", "sheep", "elephant", "bear", "zebra", "giraffe", "tiger", "lion", "rabbit", "hamster", "monkey"),
            "car" to listOf("car_wheel", "sports_car", "convertible", "limousine", "minivan", "cab", "jeep", "ambulance", "pickup", "racer", "beach_wagon"),
            "flower" to listOf("daisy", "rose", "sunflower", "tulip", "pot", "vase", "bouquet"),
            "building" to listOf("church", "castle", "palace", "mosque", "monastery", "library", "cinema", "barn", "greenhouse", "dome"),
            "beach" to listOf("seashore", "sandbar", "lakeside", "pier", "bikini", "snorkel", "surfboard"),
            "nature" to listOf("valley", "mountain", "volcano", "cliff", "lakeside", "seashore", "coral_reef", "alp"),
            "sky" to listOf("cloud", "balloon", "parachute", "kite"),
            "tree" to listOf("oak", "palm", "fig", "bonsai", "larch"),
            "baby" to listOf("bib", "diaper", "crib", "cradle", "bassinet"),
            "sport" to listOf("soccer_ball", "basketball", "tennis_ball", "volleyball", "baseball", "golf_ball", "rugby_ball", "ping-pong_ball"),
            "phone" to listOf("cell_phone", "cellular_telephone", "smartphone", "iPod", "dial_telephone"),
            "laptop" to listOf("laptop", "notebook", "desktop_computer", "screen", "monitor"),
            "book" to listOf("book_jacket", "comic_book", "notebook"),
            "cat" to listOf("tabby", "Persian_cat", "Siamese_cat", "Egyptian_cat", "tiger_cat"),
            "dog" to listOf("golden_retriever", "Labrador_retriever", "German_shepherd", "poodle", "beagle", "husky", "bulldog", "pug", "Chihuahua", "collie", "dalmatian"),
    )

    /**
     * Search by label text. Returns matching photo IDs sorted by relevance.
     * Supports aliases: "person" -> matches jean, suit, T-shirt, etc.
     */
    fun searchByLabel(query: String): List<String> {
        val queryWords = query.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (queryWords.isEmpty()) return emptyList()

        // Expand query with aliases
        val expandedWords = mutableSetOf<String>()
        for (word in queryWords) {
            expandedWords.add(word)
            searchAliases[word]?.forEach { expandedWords.add(it.lowercase()) }
        }

        val results = mutableListOf<Pair<String, Float>>()

        // Search by face group names
        val faceMatchPhotoIds = mutableSetOf<String>()
        val identities = loadFaceIdentitiesList()
        val namedIdentityIds = identities.filter { identity ->
            if (identity.name.isNotBlank() && queryWords.any { q -> identity.name.lowercase().contains(q) }) return@filter true
            false
        }.map { it.id }.toMutableSet()
        identities.forEach { identity ->
            if (identity.personId != null && queryWords.any { q -> q == identity.personId }) {
                namedIdentityIds.add(identity.id)
            }
        }
        if (namedIdentityIds.isNotEmpty()) {
            val groups = getFaceGroups()
            for (identityId in namedIdentityIds) {
                groups[identityId]?.forEach { (photoId, _, _) ->
                    faceMatchPhotoIds.add(photoId)
                }
            }
        }
        faceMatchPhotoIds.forEach { results.add(Pair(it, 2.0f)) }

        // "person"/"people"/"face"/"selfie" -> return ALL photos that have any detected face
        val faceKeywords = setOf("person", "people", "face", "selfie", "faces")
        if (queryWords.any { it in faceKeywords }) {
            // Find all photo_ids that have face_entries
            db.rawQuery(
                "SELECT DISTINCT photo_id FROM face_entries",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val photoId = cursor.getString(0)
                    if (photoId !in faceMatchPhotoIds) {
                        results.add(Pair(photoId, 1.5f))
                        faceMatchPhotoIds.add(photoId) // prevent dups in label search below
                    }
                }
            }
        }

        // Search labels with LIKE for each expanded word
        for (word in expandedWords) {
            val likePattern = "%$word%"
            db.rawQuery(
                "SELECT photo_id, labels, scores FROM photo_index WHERE labels LIKE ?",
                arrayOf(likePattern)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val photoId = cursor.getString(0)
                    if (photoId in faceMatchPhotoIds) continue
                    val labelsJson = cursor.getString(1)
                    val scoresJson = cursor.getString(2)
                    val labelsList = parseJsonStringArray(labelsJson)
                    val scoresList = parseJsonFloatArray(scoresJson)

                    var totalScore = 0f
                    for ((labelIndex, label) in labelsList.withIndex()) {
                        // Tokenize the label on underscore / hyphen / space
                        // and match per-token instead of substring. The old
                        // .contains() check pulled in "card" / "scarecrow"
                        // when the user searched for "car"; matching against
                        // tokens fixes the bleed-through while still letting
                        // multi-word labels ("sports_car") match a single
                        // word query.
                        val labelLower = label.lowercase()
                        val tokens = labelLower.split('_', '-', ' ').filter { it.isNotBlank() }
                        for (w in expandedWords) {
                            val hit = tokens.any { it == w } ||
                                tokens.any { it.startsWith(w) && it.length - w.length <= 1 } ||  // singular/plural slack
                                labelLower == w
                            if (hit) {
                                totalScore += if (labelIndex < scoresList.size) scoresList[labelIndex] else 0.1f
                            }
                        }
                    }
                    if (totalScore > 0f) {
                        results.add(Pair(photoId, totalScore))
                    }
                }
            }
        }

        // Also search AI descriptions (Gemma-generated)
        val descMatches = searchByDescription(query)
        for (id in descMatches) {
            results.add(Pair(id, 1.8f)) // high relevance — description is a full sentence match
        }

        // Deduplicate: keep highest score per photoId
        val bestScores = mutableMapOf<String, Float>()
        for ((id, score) in results) {
            bestScores[id] = maxOf(bestScores[id] ?: 0f, score)
        }

        return bestScores.entries.sortedByDescending { it.value }.map { it.key }
    }

    /**
     * Find photos similar to the given photo.
     * If the photo has faces, prioritize face similarity (find same person).
     * Otherwise fall back to general image similarity.
     */
    fun findSimilar(photoId: String, topN: Int = 20): List<Pair<String, Float>> {
        // Load target photo's faces
        val targetFaces = loadFaceEntriesForPhoto(photoId)

        if (targetFaces.isNotEmpty()) {
            val faceResults = mutableMapOf<String, Float>()
            // Load all face entries from other photos
            db.rawQuery(
                "SELECT photo_id, embedding FROM face_entries WHERE photo_id != ?",
                arrayOf(photoId)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val otherId = cursor.getString(0)
                    val otherEmb = cursor.getBlob(1).toFloatArray()
                    for (targetFace in targetFaces) {
                        val sim = cosineSimilarity(targetFace.embedding, otherEmb)
                        if (sim > 0.5f) {
                            faceResults[otherId] = maxOf(faceResults[otherId] ?: 0f, sim)
                        }
                    }
                }
            }
            if (faceResults.isNotEmpty()) {
                return faceResults.entries.sortedByDescending { it.value }
                    .take(topN).map { it.key to it.value }
            }
        }

        // Fall back to general image feature similarity
        val targetVector = loadFeatureVector(photoId) ?: return emptyList()
        val results = mutableListOf<Pair<String, Float>>()
        db.rawQuery(
            "SELECT photo_id, feature_vector FROM photo_index WHERE photo_id != ? AND feature_vector IS NOT NULL",
            arrayOf(photoId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val otherId = cursor.getString(0)
                val otherVector = cursor.getBlob(1).toFloatArray()
                val similarity = cosineSimilarity(targetVector, otherVector)
                if (similarity > 0.3f) {
                    results.add(Pair(otherId, similarity))
                }
            }
        }
        return results.sortedByDescending { it.second }.take(topN)
    }

    /**
     * Find groups of duplicate/near-identical photos.
     */
    fun findDuplicates(threshold: Float = 0.90f, validPhotoIds: Set<String>? = null): List<List<String>> {
        // Load all photo IDs and feature vectors (skip trashed/deleted photos)
        val entries = mutableListOf<Pair<String, FloatArray>>()
        db.rawQuery(
            "SELECT photo_id, feature_vector FROM photo_index WHERE feature_vector IS NOT NULL",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                if (validPhotoIds != null && id !in validPhotoIds) continue
                val fv = cursor.getBlob(1).toFloatArray()
                if (fv.isEmpty()) continue
                entries.add(id to fv)
            }
        }

        val photoIds = entries.map { it.first }
        val parent = mutableMapOf<String, String>()

        // Union-Find helpers
        fun find(x: String): String {
            var root = x
            while (parent[root] != root) {
                parent[root] = parent[parent[root]!!]!!
                root = parent[root]!!
            }
            return root
        }

        fun union(x: String, y: String) {
            val rootX = find(x)
            val rootY = find(y)
            if (rootX != rootY) {
                parent[rootX] = rootY
            }
        }

        for (id in photoIds) {
            parent[id] = id
        }

        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val similarity = cosineSimilarity(entries[i].second, entries[j].second)
                if (similarity >= threshold) {
                    union(entries[i].first, entries[j].first)
                }
            }
        }

        val groups = mutableMapOf<String, MutableList<String>>()
        for (id in photoIds) {
            val root = find(id)
            groups.getOrPut(root) { mutableListOf() }.add(id)
        }

        return groups.values.filter { it.size >= 2 }
    }

    /**
     * Find blurry photos. Returns photo IDs with blur score below threshold.
     */
    fun findBlurry(threshold: Double = 100.0, validPhotoIds: Set<String>? = null): List<String> {
        val results = mutableListOf<String>()
        db.rawQuery(
            "SELECT photo_id FROM photo_index WHERE blur_score < ? AND blur_score >= 0",
            arrayOf(threshold.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                if (validPhotoIds == null || id in validPhotoIds) results.add(id)
            }
        }
        return results
    }

    /**
     * Get index progress: number of indexed photos.
     */
    fun getIndexedCount(): Int {
        db.rawQuery("SELECT COUNT(*) FROM photo_index", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /**
     * Get labels for a specific photo.
     */
    fun getLabels(photoId: String): List<String> {
        db.rawQuery(
            "SELECT labels FROM photo_index WHERE photo_id = ?",
            arrayOf(photoId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return parseJsonStringArray(cursor.getString(0))
            }
        }
        return emptyList()
    }

    fun getLabelsWithScores(photoId: String): List<Pair<String, Float>> {
        db.rawQuery(
            "SELECT labels, scores FROM photo_index WHERE photo_id = ?",
            arrayOf(photoId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val labels = parseJsonStringArray(cursor.getString(0))
                val scoresJson = cursor.getString(1)
                val scores = try {
                    val arr = JSONArray(scoresJson)
                    (0 until arr.length()).map { arr.getDouble(it).toFloat() }
                } catch (_: Exception) { emptyList() }
                return labels.zip(scores)
            }
        }
        return emptyList()
    }

    /**
     * Get all unique labels across all indexed photos (for auto-suggest).
     */
    fun getAllLabels(): List<String> {
        val allLabels = mutableSetOf<String>()
        db.rawQuery("SELECT DISTINCT labels FROM photo_index", null).use { cursor ->
            while (cursor.moveToNext()) {
                val labelsJson = cursor.getString(0)
                parseJsonStringArray(labelsJson).forEach { allLabels.add(it) }
            }
        }
        return allLabels.sorted()
    }

    /** Get the AI-generated description for a photo. */
    fun getDescription(photoId: String): String {
        db.rawQuery(
            "SELECT description FROM photo_index WHERE photo_id = ?",
            arrayOf(photoId)
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: ""
        }
        return ""
    }

    /**
     * Ensure a photo_index row exists for [photoId] so subsequent UPDATEs land.
     * Without this, photos that were never auto-indexed (older imports, vault
     * items added before indexing ran) silently swallow writes from
     * [setDescription] and [mergeAiTags].
     */
    private fun ensureRow(photoId: String) {
        db.execSQL(
            "INSERT OR IGNORE INTO photo_index (photo_id, labels, scores, indexed_at) VALUES (?, ?, ?, ?)",
            arrayOf<Any>(photoId, "[]", "[]", System.currentTimeMillis())
        )
    }

    /**
     * Merge AI-generated tags into the existing labels for a photo.
     *
     * Tags coming from Gemma vision are scored 1.0 (max) so they sort above the
     * ONNX classifier labels, which typically land in the 0.60-0.85 band. New
     * tags that case-insensitively match an existing label are dropped — we never
     * downgrade an existing high-confidence label, and we don't duplicate.
     *
     * The merged list is capped at 12 entries to keep the chip row from blowing
     * up on busy photos.
     */
    fun mergeAiTags(photoId: String, newTags: List<String>) {
        if (newTags.isEmpty()) {
            Log.d(TAG, "mergeAiTags: newTags empty, skipping")
            return
        }
        ensureRow(photoId)
        val existing = getLabelsWithScores(photoId).toMutableList()
        // Build a lookup so we can BUMP existing entries (not just skip) when
        // Gemma's reply overlaps with ONNX classifier labels. Previously we
        // dropped overlapping tags silently — which meant a photo whose tags
        // Gemma already agreed with (e.g. ONNX "Person" + Gemma "person") was
        // never marked as Gemma-processed (no score >= 0.99 entry was added),
        // so countPending kept reporting it as "missing Gemma tags" and the
        // bulk pass kept re-selecting the same photos. Bumping to 1.0
        // guarantees a Gemma marker exists even when the set fully overlaps.
        val byLower = HashMap<String, Int>()
        existing.forEachIndexed { idx, pair -> byLower[pair.first.lowercase()] = idx }
        for (raw in newTags) {
            val tag = raw.trim().trim(',', '.', ';', ':')
            if (tag.isBlank()) continue
            val lower = tag.lowercase()
            val existingIdx = byLower[lower]
            if (existingIdx != null) {
                if (existing[existingIdx].second < 1.0f) {
                    existing[existingIdx] = existing[existingIdx].first to 1.0f
                }
            } else {
                existing.add(tag to 1.0f)
                byLower[lower] = existing.size - 1
            }
        }
        val capped = existing.take(12)
        val labelsJson = JSONArray(capped.map { it.first }).toString()
        val scoresJson = JSONArray(capped.map { it.second.toDouble() }).toString()
        val cv = ContentValues().apply {
            put("labels", labelsJson)
            put("scores", scoresJson)
        }
        val rows = db.update("photo_index", cv, "photo_id = ?", arrayOf(photoId))
        Log.d(TAG, "mergeAiTags: photo=$photoId newTagCount=${newTags.size} stored=${capped.size} rowsUpdated=$rows labels=$labelsJson")
    }

    /**
     * Stamp the photo's update time = NOW. Called from VaultRepository on
     * replacePhoto so the sort modes UPDATED_DESC / UPDATED_ASC can find
     * recently-edited photos without scanning every file's mtime.
     */
    fun markUpdated(photoId: String, atMillis: Long = System.currentTimeMillis()) {
        ensureRow(photoId)
        val cv = android.content.ContentValues().apply { put("updated_at", atMillis) }
        db.update("photo_index", cv, "photo_id = ?", arrayOf(photoId))
    }

    /**
     * Batch lookup: photo id → last updated_at millis (0 if never edited).
     * Used by the gallery sort path so we don't fire a query per photo.
     */
    fun getUpdatedTimes(photoIds: Collection<String>): Map<String, Long> {
        if (photoIds.isEmpty()) return emptyMap()
        val result = HashMap<String, Long>(photoIds.size)
        // SQLite has a default 999-parameter cap. Chunk to stay safely below.
        photoIds.chunked(500).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            db.rawQuery(
                "SELECT photo_id, updated_at FROM photo_index WHERE photo_id IN ($placeholders)",
                chunk.toTypedArray()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val t = cursor.getLong(1)
                    if (t > 0L) result[id] = t
                }
            }
        }
        return result
    }

    /** Set/update the AI-generated description for a photo. */
    fun setDescription(photoId: String, description: String) {
        ensureRow(photoId)
        val cv = android.content.ContentValues().apply {
            put("description", description)
        }
        db.update("photo_index", cv, "photo_id = ?", arrayOf(photoId))
    }

    /**
     * Result of a person-aware search: the matched photo IDs plus what the
     * tokenizer detected so the caller can render UI affordances (e.g. a
     * removable "Person: Anas ×" chip in the Vault search bar).
     */
    data class PersonAwareSearchResult(
        val photoIds: List<String>,
        val detectedPerson: String?,  // display name that matched (or null)
        val residualQuery: String     // remaining tokens after person stripped
    )

    /**
     * Compound search that recognizes a person's name embedded in free-text.
     *
     * "anas dogs" → finds photos that BOTH contain Anas's face AND match the
     * "dogs" label/description search. "anas" alone → all photos with Anas.
     * "dogs" alone → falls back to plain [searchByLabel].
     *
     * Person detection is greedy: tries 3-token → 2-token → 1-token spans
     * looking for a face-identity name (loadFaceIdentitiesList) or a contact
     * name ([ContactRepository.listContacts]) that matches case-insensitively.
     * First match wins.
     *
     * Cost: face clustering via [getFaceGroups] is O(m²) on total faces;
     * runs once per call (no cross-call cache yet). On ~5k photos with ~1k
     * faces this is sub-second on Pixel 9a. searchByLabel is a LIKE scan
     * over `photo_index.labels` JSON.
     */
    fun searchByPersonAndTags(
        contactRepo: ContactRepository,
        rawText: String,
        limit: Int = 100
    ): PersonAwareSearchResult {
        val text = rawText.trim()
        if (text.isEmpty()) return PersonAwareSearchResult(emptyList(), null, "")

        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return PersonAwareSearchResult(emptyList(), null, "")

        // Build a (name → identityId) lookup over face-identity names + contact
        // names. Contact names also map via personId → identity, so renaming a
        // face group doesn't break "Anas" search if the contact name is intact.
        val identities = loadFaceIdentitiesList()
        val contacts = contactRepo.listContacts()

        val nameToIdentity = HashMap<String, String>() // lowercase name → identity.id
        for (id in identities) {
            if (id.name.isNotBlank()) nameToIdentity[id.name.lowercase()] = id.id
        }
        for (c in contacts) {
            if (c.name.isBlank()) continue
            val matchedIdentity = identities.firstOrNull { it.personId == c.id }
            if (matchedIdentity != null) {
                nameToIdentity.putIfAbsent(c.name.lowercase(), matchedIdentity.id)
            }
        }

        // Greedy span match: try 3-token, then 2-token, then 1-token windows
        // at every position. First hit wins. Multi-word names like "john
        // smith" only match if the user typed them in that order.
        var matchedIdentityId: String? = null
        var matchedName: String? = null
        var matchStart = -1
        var matchEnd = -1
        outer@ for (span in 3 downTo 1) {
            for (start in 0..tokens.size - span) {
                val candidate = tokens.subList(start, start + span).joinToString(" ").lowercase()
                val id = nameToIdentity[candidate]
                if (id != null) {
                    matchedIdentityId = id
                    matchedName = identities.firstOrNull { it.id == id }?.name?.ifBlank {
                        contacts.firstOrNull { it.id == identities.first { ii -> ii.id == id }.personId }?.name
                    } ?: candidate.replaceFirstChar { it.uppercase() }
                    matchStart = start
                    matchEnd = start + span
                    break@outer
                }
            }
        }

        val residualTokens = if (matchedIdentityId != null) {
            tokens.subList(0, matchStart) + tokens.subList(matchEnd, tokens.size)
        } else {
            tokens
        }
        val residual = residualTokens.joinToString(" ")

        // Per-token search with a cheap plural→singular fallback so "cars"
        // matches the same labels as "car" (the ImageNet vocabulary stores
        // singulars, and the alias-expansion map keys are singular). Only
        // kicks in when the typed form returns nothing, to avoid polluting
        // genuinely-distinct plural queries.
        fun searchForToken(tok: String): List<String> {
            val primary = searchByLabel(tok)
            if (primary.isNotEmpty()) return primary
            if (tok.length > 3 && tok.endsWith("s") && !tok.endsWith("ss")) {
                return searchByLabel(tok.dropLast(1))
            }
            return primary
        }

        // Photos that match ALL residual tokens (per-token AND).
        // Single-token residual is just the per-token search.
        // Multi-token residual ("girl car") intersects per-token results so
        // we don't return photos that match only one of the words.
        fun multiTokenAnd(): List<String> {
            if (residualTokens.isEmpty()) return emptyList()
            if (residualTokens.size == 1) return searchForToken(residualTokens[0])
            val perTokenRanked = residualTokens.map { tok -> searchForToken(tok) }
            // Intersect: keep ranked order of the FIRST token's hits, filter
            // by remaining token sets. This preserves searchByLabel's scoring
            // (face-group hits at 2.0, description hits at 1.8, label sum) for
            // the most-specific token while still requiring every word to hit.
            val intersection = perTokenRanked.drop(1)
                .map { it.toSet() }
                .fold(perTokenRanked[0]) { acc, set -> acc.filter { it in set } }
            return intersection
        }

        // No person → AND across residual tokens (or single-token plain search).
        if (matchedIdentityId == null) {
            return PersonAwareSearchResult(
                photoIds = multiTokenAnd().take(limit),
                detectedPerson = null,
                residualQuery = text
            )
        }

        // Person matched. Collect photo IDs containing that identity.
        val faceGroups = getFaceGroups()
        val personPhotoIds = faceGroups[matchedIdentityId]
            ?.map { it.first }
            ?.distinct()
            ?: emptyList()

        if (residual.isBlank()) {
            return PersonAwareSearchResult(
                photoIds = personPhotoIds.take(limit),
                detectedPerson = matchedName,
                residualQuery = ""
            )
        }

        // Compound: per-token AND filtered to the person's photo set.
        val personSet = personPhotoIds.toSet()
        val intersected = multiTokenAnd().filter { it in personSet }

        return PersonAwareSearchResult(
            photoIds = intersected.take(limit),
            detectedPerson = matchedName,
            residualQuery = residual
        )
    }

    /** Search photos by description text (in addition to labels). */
    fun searchByDescription(query: String): List<String> {
        val results = mutableListOf<String>()
        db.rawQuery(
            "SELECT photo_id FROM photo_index WHERE description LIKE ? AND description != ''",
            arrayOf("%$query%")
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(cursor.getString(0))
            }
        }
        return results
    }

    /** Get all photos that don't have a description yet (for lazy generation). */
    fun getPhotosWithoutDescription(): List<String> {
        val ids = mutableListOf<String>()
        db.rawQuery(
            "SELECT photo_id FROM photo_index WHERE description = '' OR description IS NULL",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                ids.add(cursor.getString(0))
            }
        }
        return ids
    }

    /**
     * Clear the entire index.
     */
    fun clearIndex() {
        db.beginTransaction()
        try {
            db.delete("photo_index", null, null) // cascades to face_entries
            db.setTransactionSuccessful()
            Log.d(TAG, "Index cleared")
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Group faces across all photos by similarity, using stable identities.
     * Returns map of identity ID -> list of (photoId, faceIndex, FaceEntry).
     * Photos with multiple faces appear in multiple groups.
     */
    /**
     * Direct centroid clustering. READ-ONLY — does not modify the database.
     * Deterministic: same data always produces same groups.
     * No transitivity — each face matched directly to cluster centroid.
     */
    fun getFaceGroups(threshold: Float = 0.55f): Map<String, List<Triple<String, Int, FaceEntry>>> {
        // Load all face entries in deterministic order
        val allFaces = mutableListOf<Triple<String, Int, FaceEntry>>()
        db.rawQuery(
            "SELECT photo_id, face_index, box, embedding FROM face_entries ORDER BY photo_id, face_index",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val emb = cursor.getBlob(3).toFloatArray()
                if (emb.isEmpty()) continue
                allFaces.add(Triple(
                    cursor.getString(0), cursor.getInt(1),
                    FaceEntry(cursor.getBlob(2).toFloatArray(), emb)
                ))
            }
        }
        if (allFaces.isEmpty()) return emptyMap()
        Log.d(TAG, "Direct clustering: ${allFaces.size} faces, threshold=$threshold")

        // Build clusters from scratch — no identity table dependency
        data class Cluster(val members: MutableList<Int>, var centroid: FloatArray)
        val clusters = mutableListOf<Cluster>()

        for (i in allFaces.indices) {
            val emb = allFaces[i].third.embedding
            var bestIdx = -1
            var bestSim = threshold

            for (c in clusters.indices) {
                val sim = cosineSimilarity(emb, clusters[c].centroid)
                if (sim > bestSim) {
                    bestSim = sim
                    bestIdx = c
                }
            }

            if (bestIdx >= 0) {
                // Add to existing cluster and update centroid
                val cluster = clusters[bestIdx]
                cluster.members.add(i)
                val n = cluster.members.size
                val dim = cluster.centroid.size
                val newCentroid = FloatArray(dim)
                for (m in cluster.members) {
                    val me = allFaces[m].third.embedding
                    for (d in 0 until dim) newCentroid[d] += me[d]
                }
                for (d in 0 until dim) newCentroid[d] /= n
                cluster.centroid = newCentroid
            } else {
                // Create new cluster
                clusters.add(Cluster(mutableListOf(i), emb.copyOf()))
            }
        }

        // Map clusters to existing face_identities to preserve names/person links
        val identities = loadFaceIdentitiesList()
        val excluded = loadAllExclusions()
        val result = mutableMapOf<String, MutableList<Triple<String, Int, FaceEntry>>>()

        for (cluster in clusters) {
            if (cluster.members.size < 2) continue
            val members = cluster.members.map { allFaces[it] }

            // Find best matching identity
            var bestIdentity: FaceIdentity? = null
            var bestSim = 0.3f
            for (id in identities) {
                if (id.centroid.size != cluster.centroid.size) continue
                val sim = cosineSimilarity(cluster.centroid, id.centroid)
                if (sim > bestSim) { bestSim = sim; bestIdentity = id }
            }

            val groupId = if (bestIdentity != null) {
                bestIdentity.id
            } else {
                // Create new identity so merge/remove/rename work
                val newId = java.util.UUID.randomUUID().toString()
                try {
                    val cv = ContentValues().apply {
                        put("id", newId)
                        put("name", "")
                        put("centroid", cluster.centroid.toBlob())
                    }
                    db.insertWithOnConflict("face_identities", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                } catch (_: Exception) {}
                newId
            }

            val excludedSet = excluded[groupId] ?: emptySet()
            val filtered = members.filter { it.first !in excludedSet }
            if (filtered.isNotEmpty()) {
                // If two clusters map to same identity (after merge), combine them
                val existing = result[groupId]
                if (existing != null) {
                    existing.addAll(filtered)
                } else {
                    result[groupId] = filtered.toMutableList()
                }
            }
        }

        // Filter to groups with 2+ members after combining
        val final2 = result.filter { it.value.size >= 2 }
        Log.d(TAG, "Direct clustering: ${final2.size} groups from ${clusters.size} clusters")
        return final2.toSortedMap(compareByDescending { final2[it]?.size ?: 0 })
    }

    /**
     * Merge two face groups into one. The kept group gets the combined centroid
     * and the name from whichever group had one. Future indexing will use the
     * broader centroid to match both appearances.
     */
    fun mergeFaceGroups(keepId: String, mergeIntoId: String) {
        val identities = loadFaceIdentitiesList()
        val keep = identities.find { it.id == keepId } ?: return
        val merge = identities.find { it.id == mergeIntoId } ?: return

        // Count how many faces belong to each group by comparing cosine similarity
        var keepCount = 0
        var mergeCount = 0
        db.rawQuery("SELECT embedding FROM face_entries", null).use { cursor ->
            while (cursor.moveToNext()) {
                val emb = cursor.getBlob(0).toFloatArray()
                val simKeep = cosineSimilarity(emb, keep.centroid)
                val simMerge = cosineSimilarity(emb, merge.centroid)
                if (simKeep > simMerge) keepCount++ else mergeCount++
            }
        }
        keepCount = keepCount.coerceAtLeast(1)
        mergeCount = mergeCount.coerceAtLeast(1)
        val total = keepCount + mergeCount

        // Weighted average centroid
        val newCentroid = FloatArray(keep.centroid.size) { i ->
            (keep.centroid[i] * keepCount + merge.centroid[i] * mergeCount) / total
        }

        // Keep name from whichever has one
        val name = keep.name.ifBlank { merge.name }

        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("name", name)
                put("centroid", newCentroid.toBlob())
            }
            db.update("face_identities", cv, "id = ?", arrayOf(keepId))
            db.delete("face_identities", "id = ?", arrayOf(mergeIntoId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Log.d(TAG, "Merged face group '$mergeIntoId' into '$keepId' (name='$name', keep=$keepCount, merge=$mergeCount)")
    }

    /**
     * Remove specific photos from a face group.
     */
    fun removeFromFaceGroup(identityId: String, photoIds: Set<String>) {
        db.beginTransaction()
        try {
            for (photoId in photoIds) {
                val cv = ContentValues().apply {
                    put("identity_id", identityId)
                    put("photo_id", photoId)
                }
                db.insertWithOnConflict("face_exclusions", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getExcludedFromGroup(identityId: String): Set<String> {
        val result = mutableSetOf<String>()
        db.rawQuery(
            "SELECT photo_id FROM face_exclusions WHERE identity_id = ?",
            arrayOf(identityId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getString(0))
            }
        }
        return result
    }

    /**
     * Auto-name face groups by matching against People profile photos.
     */
    fun autoNameFromContacts(
        contactRepo: ContactRepository,
        faceEmbedder: com.privateai.camera.bridge.FaceEmbedder,
        threshold: Float = 0.40f
    ) {
        val identities = loadFaceIdentitiesList().toMutableList()
        val contacts = contactRepo.listContacts()
        if (contacts.isEmpty() || identities.isEmpty()) return

        var changed = false
        for (contact in contacts) {
            if (identities.any { it.personId == contact.id }) continue
            if (wasExplicitlyUnlinked(contact.id)) continue

            val profileBitmap = contactRepo.loadProfilePhoto(contact.id) ?: continue
            val embeddings = faceEmbedder.detectAndEmbed(profileBitmap)
            profileBitmap.recycle()
            if (embeddings.isEmpty()) continue

            val profileEmbedding = embeddings[0].second

            var bestIdentityIdx = -1
            var bestSim = threshold

            for ((idx, identity) in identities.withIndex()) {
                if (identity.personId != null) continue
                val sim = cosineSimilarity(profileEmbedding, identity.centroid)
                if (sim > bestSim) {
                    bestSim = sim
                    bestIdentityIdx = idx
                }
            }

            if (bestIdentityIdx >= 0) {
                val identity = identities[bestIdentityIdx]
                val newName = identity.name.ifBlank { contact.name }
                identities[bestIdentityIdx] = identity.copy(name = newName, personId = contact.id)
                val cv = ContentValues().apply {
                    put("name", newName)
                    put("person_id", contact.id)
                }
                db.update("face_identities", cv, "id = ?", arrayOf(identity.id))
                changed = true
                Log.d(TAG, "Auto-linked face group '${identity.id}' to person '${contact.name}' (similarity: ${"%.3f".format(bestSim)})")
            }
        }
    }

    fun setFaceGroupName(identityId: String, name: String) {
        val cv = ContentValues().apply {
            put("name", name)
        }
        db.update("face_identities", cv, "id = ?", arrayOf(identityId))
    }

    /**
     * Soft-reset face groups: wipe identities + exclusions + "not this
     * person" flags but KEEP the per-face embeddings (`face_entries`) and
     * AI tags (`photo_index.labels` / `description`). The next call to
     * [getFaceGroups] re-clusters from the surviving embeddings — no model
     * inference needed, no re-index pass.
     *
     * Useful after a detector backend swap (ML Kit → ONNX YuNet, Track A1.2),
     * after the user tweaks the clustering threshold, or to recover from a
     * messy chain of merges/renames. Cheap: three DELETEs in a single
     * transaction.
     */
    fun clearFaceGroups() {
        db.beginTransaction()
        try {
            db.delete("face_identities", null, null)
            db.delete("face_exclusions", null, null)
            db.delete("face_unlinked", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Link a face group to a person (contact). Survives group renames. */
    fun linkFaceGroupToPerson(identityId: String, personId: String, personName: String) {
        // Get current name to preserve it if non-blank
        var currentName = ""
        db.rawQuery("SELECT name FROM face_identities WHERE id = ?", arrayOf(identityId)).use { cursor ->
            if (cursor.moveToFirst()) currentName = cursor.getString(0) ?: ""
        }
        val cv = ContentValues().apply {
            put("person_id", personId)
            put("name", currentName.ifBlank { personName })
        }
        val updated = db.update("face_identities", cv, "id = ?", arrayOf(identityId))
        if (updated == 0) return

        // Clear unlinked status since user explicitly re-linked
        db.delete("face_unlinked", "person_id = ?", arrayOf(personId))
    }

    /** Get the personId linked to a face group. */
    fun getFaceGroupPersonId(identityId: String): String? {
        db.rawQuery(
            "SELECT person_id FROM face_identities WHERE id = ?",
            arrayOf(identityId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val value = cursor.getString(0)
                return if (value.isNullOrEmpty()) null else value
            }
        }
        return null
    }

    /** Find face group identity linked to a specific person. */
    fun findIdentityByPersonId(personId: String): FaceIdentity? {
        db.rawQuery(
            "SELECT id, name, centroid, person_id FROM face_identities WHERE person_id = ?",
            arrayOf(personId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return FaceIdentity(
                    cursor.getString(0),
                    cursor.getString(1) ?: "",
                    cursor.getBlob(2).toFloatArray(),
                    cursor.getString(3)?.ifEmpty { null }
                )
            }
        }
        return null
    }

    /** Unlink a face group from a person. Clears personId but keeps the name. */
    fun unlinkFaceGroupFromPerson(personId: String) {
        val cv = ContentValues().apply {
            putNull("person_id")
        }
        db.update("face_identities", cv, "person_id = ?", arrayOf(personId))

        // Remember this was explicitly unlinked so auto-link doesn't redo it
        val ulCv = ContentValues().apply {
            put("person_id", personId)
        }
        db.insertWithOnConflict("face_unlinked", null, ulCv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** Check if a person was explicitly unlinked (prevents auto-relinking). */
    fun wasExplicitlyUnlinked(personId: String): Boolean {
        db.rawQuery(
            "SELECT 1 FROM face_unlinked WHERE person_id = ?",
            arrayOf(personId)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    /**
     * Find photos by matching a face embedding against all indexed faces.
     * Used when profile photo exists but no face group is linked.
     */
    fun findPhotosByFaceEmbedding(embedding: FloatArray, threshold: Float = 0.40f): List<String> {
        val results = mutableSetOf<String>()
        db.rawQuery("SELECT photo_id, embedding FROM face_entries", null).use { cursor ->
            while (cursor.moveToNext()) {
                val photoId = cursor.getString(0)
                if (photoId in results) continue
                val emb = cursor.getBlob(1).toFloatArray()
                if (cosineSimilarity(emb, embedding) > threshold) {
                    results.add(photoId)
                }
            }
        }
        return results.toList()
    }

    /** Clear the unlinked status (e.g., when profile photo is added). */
    fun clearUnlinkedStatus(personId: String) {
        db.delete("face_unlinked", "person_id = ?", arrayOf(personId))
    }

    fun getFaceGroupName(identityId: String): String? {
        db.rawQuery(
            "SELECT name FROM face_identities WHERE id = ?",
            arrayOf(identityId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                return if (name.isNullOrBlank()) null else name
            }
        }
        return null
    }

    fun loadFaceIdentities() {
        // No-op: identities are now in SQLCipher. Kept for API compatibility.
    }

    // ---- Private helpers ----

    /**
     * Load all face identities from DB.
     */
    fun loadFaceIdentitiesList(): List<FaceIdentity> {
        val result = mutableListOf<FaceIdentity>()
        db.rawQuery("SELECT id, name, centroid, person_id FROM face_identities", null).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(FaceIdentity(
                    cursor.getString(0),
                    cursor.getString(1) ?: "",
                    cursor.getBlob(2).toFloatArray(),
                    cursor.getString(3)?.ifEmpty { null }
                ))
            }
        }
        return result
    }

    /**
     * Load face entries for a specific photo.
     */
    private fun loadFaceEntriesForPhoto(photoId: String): List<FaceEntry> {
        val result = mutableListOf<FaceEntry>()
        db.rawQuery(
            "SELECT box, embedding FROM face_entries WHERE photo_id = ?",
            arrayOf(photoId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(FaceEntry(
                    cursor.getBlob(0).toFloatArray(),
                    cursor.getBlob(1).toFloatArray()
                ))
            }
        }
        return result
    }

    /**
     * Load feature vector for a specific photo.
     */
    private fun loadFeatureVector(photoId: String): FloatArray? {
        db.rawQuery(
            "SELECT feature_vector FROM photo_index WHERE photo_id = ? AND feature_vector IS NOT NULL",
            arrayOf(photoId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getBlob(0).toFloatArray()
            }
        }
        return null
    }

    /**
     * Load all face exclusions.
     */
    private fun loadAllExclusions(): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        db.rawQuery("SELECT identity_id, photo_id FROM face_exclusions", null).use { cursor ->
            while (cursor.moveToNext()) {
                val identityId = cursor.getString(0)
                val photoId = cursor.getString(1)
                result.getOrPut(identityId) { mutableSetOf() }.add(photoId)
            }
        }
        return result
    }

    /**
     * Compute cosine similarity between two float arrays.
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return if (normA > 0 && normB > 0) {
            dot / (Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())).toFloat()
        } else {
            0f
        }
    }

    /**
     * Parse a JSON array string into a list of strings.
     */
    private fun parseJsonStringArray(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Parse a JSON array string into a list of floats.
     */
    private fun parseJsonFloatArray(json: String): List<Float> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getDouble(it).toFloat() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
