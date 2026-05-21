// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

enum class VaultMediaType { PHOTO, VIDEO, PDF, FILE }

enum class VaultCategory(val label: String, val dirName: String) {
    CAMERA("Camera", "camera"),
    VIDEO("Videos", "video"),
    SCAN("Scans", "scan"),
    DETECT("Detections", "detect"),
    REPORTS("Reports", "reports"),
    FILES("Files", "files")
}

data class VaultPhoto(
    val id: String,
    val timestamp: Long,
    val category: VaultCategory,
    val encryptedFile: File,
    val thumbnailFile: File,
    val mediaType: VaultMediaType = VaultMediaType.PHOTO
)

/**
 * Source-side metadata preserved per photo. Stored as an encrypted
 * `<id>.meta.enc` sidecar next to the photo's encrypted JPEG. Everything is
 * optional — fields stay null when the source didn't include that EXIF tag.
 *
 * Why a sidecar (vs. extending VaultPhoto): the file listing path
 * (`listFolderItems` etc.) uses filesystem walks for performance — fast for
 * 1500 photos. Reading per-photo metadata sidecars in that same walk would
 * undo the gain. The sidecar is loaded lazily by callers that need it
 * (e.g., the photo-details dialog).
 */
data class PhotoMetadata(
    val dateTaken: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val orientation: Int? = null,   // EXIF ORIENTATION_* enum (1..8)
    val gpsLat: Double? = null,
    val gpsLng: Double? = null
) {
    fun isEmpty(): Boolean =
        dateTaken == null && width == null && height == null &&
            orientation == null && gpsLat == null && gpsLng == null
}

/**
 * Manages encrypted photo storage.
 * Photos and thumbnails are AES-256-GCM encrypted.
 * Never writes plaintext to disk.
 */
class VaultRepository(private val context: Context, private val crypto: CryptoManager) {

    companion object {
        private const val TAG = "VaultRepository"
        private const val VAULT_DIR = "vault"
        private const val THUMB_SIZE = 200
    }

    private val vaultDir: File by lazy {
        File(context.filesDir, VAULT_DIR).also { it.mkdirs() }
    }

    private fun categoryDir(category: VaultCategory): File {
        return File(vaultDir, category.dirName).also { it.mkdirs() }
    }

    /**
     * Save a video file to the vault (encrypted).
     * CRITICAL ORDER: extract thumbnail BEFORE encrypting (retriever needs raw file).
     */
    fun saveVideo(tempVideoFile: File, category: VaultCategory = VaultCategory.VIDEO): VaultPhoto {
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val dir = categoryDir(category)

        // 1. Extract first frame as thumbnail (must happen before encryption/deletion)
        val retriever = MediaMetadataRetriever()
        var thumbBytes: ByteArray
        try {
            retriever.setDataSource(tempVideoFile.absolutePath)
            val frame = retriever.getFrameAtTime(0) ?: retriever.getFrameAtTime(1000000) // fallback to 1s
            retriever.release()

            if (frame != null) {
                val scale = THUMB_SIZE.toFloat() / maxOf(frame.width, frame.height)
                val thumb = Bitmap.createScaledBitmap(
                    frame,
                    (frame.width * scale).toInt(),
                    (frame.height * scale).toInt(),
                    true
                )
                thumbBytes = ByteArrayOutputStream().use { out ->
                    thumb.compress(Bitmap.CompressFormat.JPEG, 70, out)
                    out.toByteArray()
                }
                thumb.recycle()
                frame.recycle()
            } else {
                // No frame available — use empty placeholder
                thumbBytes = ByteArray(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract video thumbnail: ${e.message}")
            retriever.release()
            thumbBytes = ByteArray(0)
        }

        // 2. Encrypt video file
        val videoEncFile = File(dir, "$id.vid.enc")
        crypto.encryptFile(tempVideoFile, videoEncFile)

        // 3. Encrypt thumbnail
        val thumbFile = File(dir, "$id.vid.thumb.enc")
        if (thumbBytes.isNotEmpty()) {
            crypto.encryptToFile(thumbBytes, thumbFile)
        }

        // 4. Delete temp file
        tempVideoFile.delete()

        val videoSize = videoEncFile.length() / 1024
        Log.d(TAG, "Video saved: $id ($category, ${videoSize}KB)")

        return VaultPhoto(id, timestamp, category, videoEncFile, thumbFile, VaultMediaType.VIDEO)
    }

    /**
     * Save a bitmap to the vault (encrypted).
     *
     * `metadata`, when supplied, becomes the source of the stored timestamp
     * (so 2018 photos sort at 2018, not the import date) and gets written
     * out as an encrypted `<id>.meta.enc` sidecar carrying original
     * dimensions, orientation, and GPS — fields the Bitmap pipeline would
     * otherwise drop. Callers importing from external sources (Wi-Fi,
     * ACTION_SEND, gallery picker) should pull metadata via
     * `ExifUtils.readMetadata(bytes)` BEFORE decoding to Bitmap.
     */
    fun savePhoto(bitmap: Bitmap, category: VaultCategory = VaultCategory.CAMERA, metadata: PhotoMetadata? = null): VaultPhoto {
        val id = UUID.randomUUID().toString()
        val timestamp = metadata?.dateTaken ?: System.currentTimeMillis()
        val dir = categoryDir(category)

        val jpegBytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.toByteArray()
        }

        val scale = THUMB_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val thumb = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true
        )
        val thumbBytes = ByteArrayOutputStream().use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 70, out)
            out.toByteArray()
        }
        thumb.recycle()

        val photoFile = File(dir, "$id.enc")
        val thumbFile = File(dir, "$id.thumb.enc")

        crypto.encryptToFile(jpegBytes, photoFile)
        crypto.encryptToFile(thumbBytes, thumbFile)
        // Align the file mtime with the photo's authoritative timestamp so
        // listPhotos() — which sorts by lastModified() — returns the correct
        // chronological order without having to decrypt sidecars on every list.
        photoFile.setLastModified(timestamp)
        thumbFile.setLastModified(timestamp)
        if (metadata != null && !metadata.isEmpty()) saveMetadataSidecar(id, dir, metadata)

        Log.d(TAG, "Photo saved: $id ($category, ${jpegBytes.size / 1024}KB)")

        return VaultPhoto(id, timestamp, category, photoFile, thumbFile)
    }

    /**
     * Decrypt and return a thumbnail bitmap. Never touches disk.
     */
    fun loadThumbnail(photo: VaultPhoto): Bitmap? {
        return try {
            Log.d(TAG, "loadThumbnail: ${photo.id}, file=${photo.thumbnailFile.absolutePath}, exists=${photo.thumbnailFile.exists()}, size=${photo.thumbnailFile.length()}")
            val decrypted = crypto.decryptFile(photo.thumbnailFile)
            Log.d(TAG, "loadThumbnail: decrypted ${decrypted.size} bytes")
            val bmp = BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
            Log.d(TAG, "loadThumbnail: bitmap=${bmp?.width}x${bmp?.height}")
            bmp
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt thumbnail: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * Decrypt and return the full-resolution photo. Never touches disk.
     */
    fun loadFullPhoto(photo: VaultPhoto): Bitmap? {
        return try {
            val decrypted = crypto.decryptFile(photo.encryptedFile)
            BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt photo: ${e.message}")
            null
        }
    }

    /**
     * Decrypt and return photo as JPEG bytes (for sharing).
     */
    fun loadPhotoBytes(photo: VaultPhoto): ByteArray? {
        return try {
            crypto.decryptFile(photo.encryptedFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt photo bytes: ${e.message}")
            null
        }
    }

    /**
     * Decrypt a video to a temp file for playback. Caller must delete the temp file when done.
     */
    fun decryptVideoToTempFile(item: VaultPhoto): File? {
        return try {
            val decrypted = crypto.decryptFile(item.encryptedFile)
            val tempFile = File(context.cacheDir, "playback_${item.id}.mp4")
            tempFile.writeBytes(decrypted)
            Log.d(TAG, "Video decrypted to temp: ${tempFile.absolutePath} (${decrypted.size / 1024}KB)")
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt video: ${e.message}")
            null
        }
    }

    /**
     * Replace a photo's content with a new bitmap. Overwrites encrypted file + thumbnail.
     *
     * Preserves `file.lastModified()` on both the photo and its thumbnail
     * across the write. Without this, editing a photo (rotate / crop /
     * filter / sticker) bumped the mtime to "now" and the edited photo
     * jumped to the top of every gallery / smart view / search result —
     * because `VaultPhoto.timestamp` (used by date-grouping and sort
     * comparators across the entire vault) reads `file.lastModified()`,
     * and that's also the only persistent creation-date carrier we have
     * for fresh captures. The fix preserves the original creation date as
     * the sort key; an explicit "last edited" timestamp is tracked
     * separately if/when a future UI feature surfaces it.
     */
    fun replacePhoto(photo: VaultPhoto, newBitmap: Bitmap) {
        val originalMtime = photo.encryptedFile.lastModified()
        val originalThumbMtime = photo.thumbnailFile.lastModified()
        val jpegBytes = ByteArrayOutputStream().use { out ->
            newBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.toByteArray()
        }
        val scale = THUMB_SIZE.toFloat() / maxOf(newBitmap.width, newBitmap.height)
        val thumb = Bitmap.createScaledBitmap(
            newBitmap, (newBitmap.width * scale).toInt(), (newBitmap.height * scale).toInt(), true
        )
        val thumbBytes = ByteArrayOutputStream().use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 70, out)
            out.toByteArray()
        }
        thumb.recycle()
        crypto.encryptToFile(jpegBytes, photo.encryptedFile)
        crypto.encryptToFile(thumbBytes, photo.thumbnailFile)
        // Restore the original mtimes so sort order stays anchored to the
        // creation date (not the edit time).
        if (originalMtime > 0L) photo.encryptedFile.setLastModified(originalMtime)
        if (originalThumbMtime > 0L) photo.thumbnailFile.setLastModified(originalThumbMtime)
        Log.d(TAG, "Photo replaced: ${photo.id} (${jpegBytes.size / 1024}KB, mtime preserved=${originalMtime})")
    }

    /**
     * Delete a photo from the vault.
     */
    fun deletePhoto(photo: VaultPhoto) {
        photo.encryptedFile.delete()
        photo.thumbnailFile.delete()
        Log.d(TAG, "Photo deleted: ${photo.id}")
    }

    /**
     * List photos and videos in a specific category, sorted by newest first.
     */
    fun listPhotos(category: VaultCategory): List<VaultPhoto> {
        val dir = categoryDir(category)
        Log.d(TAG, "listPhotos($category): dir=${dir.absolutePath}, exists=${dir.exists()}")
        val files = dir.listFiles()
        Log.d(TAG, "listPhotos($category): files found=${files?.size ?: 0}")
        if (files == null) return emptyList()

        val items = mutableListOf<VaultPhoto>()

        // Photos: {id}.enc (excluding .thumb.enc, .vid.enc, .pdf.enc, .file.enc, .meta.enc, .ocr.enc, _tobedeleted_)
        files.filter {
            it.name.endsWith(".enc") &&
                !it.name.endsWith(".thumb.enc") &&
                !it.name.endsWith(".vid.enc") &&
                !it.name.endsWith(".pdf.enc") &&
                !it.name.endsWith(".file.enc") &&
                !it.name.endsWith(".meta.enc") &&
                !it.name.endsWith(".ocr.enc") &&
                !it.name.startsWith("_tobedeleted_")
        }.forEach { file ->
            val id = file.name.removeSuffix(".enc")
            items.add(VaultPhoto(id, file.lastModified(), category, file, File(dir, "$id.thumb.enc"), VaultMediaType.PHOTO))
        }

        // Generic files: {name}.file.enc (docs, spreadsheets, text, etc.)
        // If original filename contains .pdf, treat as PDF (Wi-Fi transfer saves PDFs with .file.enc)
        files.filter {
            it.name.endsWith(".file.enc") &&
                !it.name.startsWith("_tobedeleted_")
        }.forEach { file ->
            val id = file.name.removeSuffix(".file.enc")
            val isPdf = id.lowercase().endsWith(".pdf") || id.lowercase().contains(".pdf")
            items.add(VaultPhoto(id, file.lastModified(), category, file, file, if (isPdf) VaultMediaType.PDF else VaultMediaType.FILE))
        }

        // Videos: {id}.vid.enc (excluding .vid.thumb.enc, _tobedeleted_)
        files.filter {
            it.name.endsWith(".vid.enc") &&
                !it.name.endsWith(".vid.thumb.enc") &&
                !it.name.startsWith("_tobedeleted_")
        }.forEach { file ->
            val id = file.name.removeSuffix(".vid.enc")
            items.add(VaultPhoto(id, file.lastModified(), category, file, File(dir, "$id.vid.thumb.enc"), VaultMediaType.VIDEO))
        }

        // PDFs: {name}.pdf.enc (_tobedeleted_ excluded)
        files.filter {
            it.name.endsWith(".pdf.enc") &&
                !it.name.startsWith("_tobedeleted_")
        }.forEach { file ->
            val id = file.name.removeSuffix(".pdf.enc")
            items.add(VaultPhoto(id, file.lastModified(), category, file, file, VaultMediaType.PDF))
        }

        Log.d(TAG, "listPhotos($category): items=${items.size} (photos=${items.count { it.mediaType == VaultMediaType.PHOTO }}, videos=${items.count { it.mediaType == VaultMediaType.VIDEO }}, pdfs=${items.count { it.mediaType == VaultMediaType.PDF }})")

        return items.sortedByDescending { it.timestamp }
    }

    /**
     * List all photos across all categories, sorted by newest first.
     */
    fun listAllPhotos(): List<VaultPhoto> {
        return VaultCategory.entries.flatMap { listPhotos(it) }
            .sortedByDescending { it.timestamp }
    }

    /**
     * List ALL photos (not videos, not PDFs) from all categories + custom folders.
     * Used by the virtual "Photos" smart view.
     */
    fun listAllPhotosOnly(folderItems: List<VaultPhoto> = emptyList()): List<VaultPhoto> {
        val fromCategories = VaultCategory.entries.flatMap { listPhotos(it) }
            .filter { it.mediaType == VaultMediaType.PHOTO }
        return (fromCategories + folderItems.filter { it.mediaType == VaultMediaType.PHOTO })
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
    }

    /**
     * List ALL videos from all categories + custom folders.
     * Used by the virtual "Videos" smart view.
     */
    fun listAllVideosOnly(folderItems: List<VaultPhoto> = emptyList()): List<VaultPhoto> {
        val fromCategories = VaultCategory.entries.flatMap { listPhotos(it) }
            .filter { it.mediaType == VaultMediaType.VIDEO }
        return (fromCategories + folderItems.filter { it.mediaType == VaultMediaType.VIDEO })
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
    }

    /**
     * Get count per category.
     */
    fun countByCategory(): Map<VaultCategory, Int> {
        return VaultCategory.entries.associateWith { listPhotos(it).size }
    }

    /**
     * Save a raw file (e.g. PDF) encrypted to the vault.
     * Returns the encrypted file path.
     */
    fun saveFile(data: ByteArray, filename: String, category: VaultCategory = VaultCategory.FILES): File {
        val dir = categoryDir(category)
        // Use .pdf.enc for PDFs, .file.enc for everything else (distinct from .enc photos)
        val ext = if (filename.lowercase().endsWith(".pdf")) ".pdf.enc" else ".file.enc"
        val encFile = File(dir, "$filename$ext")
        crypto.encryptToFile(data, encFile)
        Log.d(TAG, "File saved: $filename ($category, ${data.size / 1024}KB)")
        return encFile
    }

    /**
     * Decrypt a raw file and return bytes.
     */
    fun loadFile(encFile: File): ByteArray? {
        return try {
            crypto.decryptFile(encFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt file: ${e.message}")
            null
        }
    }

    /**
     * List encrypted files (non-photo) in a category.
     */
    fun listFiles(category: VaultCategory = VaultCategory.FILES): List<File> {
        val dir = categoryDir(category)
        return (dir.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".enc") && !it.name.endsWith(".thumb.enc") && !it.name.startsWith("_tobedeleted_") }
            .sortedByDescending { it.lastModified() }
    }

    /**
     * Save a photo to a custom folder.
     *
     * See `savePhoto` for the `metadata` semantics — pulls timestamp from
     * `metadata.dateTaken` if present and writes a `.meta.enc` sidecar with
     * the rest of the EXIF (dimensions, orientation, GPS).
     */
    fun savePhotoToFolder(bitmap: Bitmap, folderDir: File, metadata: PhotoMetadata? = null): VaultPhoto {
        val id = UUID.randomUUID().toString()
        val timestamp = metadata?.dateTaken ?: System.currentTimeMillis()
        folderDir.mkdirs()

        val jpegBytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.toByteArray()
        }
        val scale = THUMB_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val thumb = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        val thumbBytes = ByteArrayOutputStream().use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 70, out)
            out.toByteArray()
        }
        thumb.recycle()

        val photoFile = File(folderDir, "$id.enc")
        val thumbFile = File(folderDir, "$id.thumb.enc")
        crypto.encryptToFile(jpegBytes, photoFile)
        crypto.encryptToFile(thumbBytes, thumbFile)
        photoFile.setLastModified(timestamp)
        thumbFile.setLastModified(timestamp)
        if (metadata != null && !metadata.isEmpty()) saveMetadataSidecar(id, folderDir, metadata)

        Log.d(TAG, "Photo saved to folder: $id (${jpegBytes.size / 1024}KB)")
        return VaultPhoto(id, timestamp, VaultCategory.FILES, photoFile, thumbFile)
    }

    // ===== Metadata sidecars (encrypted JSON per photo) =====

    /**
     * Encrypted JSON written next to a photo as `<id>.meta.enc`. JSON keys
     * are short (`d`, `w`, `h`, `o`, `lat`, `lng`) to keep the encrypted
     * payload tiny — most sidecars are <80 bytes plaintext.
     */
    fun saveMetadataSidecar(photoId: String, dir: File, metadata: PhotoMetadata) {
        val json = JSONObject().apply {
            metadata.dateTaken?.let { put("d", it) }
            metadata.width?.let { put("w", it) }
            metadata.height?.let { put("h", it) }
            metadata.orientation?.let { put("o", it) }
            metadata.gpsLat?.let { put("lat", it) }
            metadata.gpsLng?.let { put("lng", it) }
        }.toString()
        try {
            crypto.encryptToFile(json.toByteArray(Charsets.UTF_8), File(dir, "$photoId.meta.enc"))
        } catch (e: Exception) {
            // Sidecar failure shouldn't block the photo save — photo + thumb
            // are already on disk above this call. Just log and move on.
            Log.w(TAG, "Failed to write metadata sidecar for $photoId: ${e.message}")
        }
    }

    /** Load the sidecar for a given photo. Returns null when absent or unreadable. */
    fun loadMetadata(photo: VaultPhoto): PhotoMetadata? {
        val sidecar = File(photo.encryptedFile.parentFile, "${photo.id}.meta.enc")
        if (!sidecar.exists()) return null
        return try {
            val obj = JSONObject(String(crypto.decryptFile(sidecar), Charsets.UTF_8))
            PhotoMetadata(
                dateTaken = obj.optLongOrNull("d"),
                width = obj.optIntOrNull("w"),
                height = obj.optIntOrNull("h"),
                orientation = obj.optIntOrNull("o"),
                gpsLat = obj.optDoubleOrNull("lat"),
                gpsLng = obj.optDoubleOrNull("lng")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read metadata sidecar for ${photo.id}: ${e.message}")
            null
        }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null
    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null
    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    // ===== OCR sidecars (encrypted plaintext per scanned document) =====

    /**
     * Encrypted JSON written next to a scanned PDF as `<id>.ocr.enc`.
     * Captures the OCR'd text so the AI Assistant can read the document
     * without re-running OCR. Format:
     *   { "v": 1, "text": "<concatenated text>", "perPage": ["p1","p2",...] }
     */
    fun saveOcrSidecar(photoId: String, dir: File, fullText: String, perPage: List<String>? = null) {
        val json = JSONObject().apply {
            put("v", 1)
            put("text", fullText)
            if (perPage != null) {
                put("perPage", org.json.JSONArray(perPage))
            }
        }.toString()
        try {
            crypto.encryptToFile(json.toByteArray(Charsets.UTF_8), File(dir, "$photoId.ocr.enc"))
        } catch (e: Exception) {
            // Sidecar failure shouldn't block the doc save — PDF is already on
            // disk above this call. The user just won't get Ask-the-Assistant
            // until they re-scan or re-OCR.
            Log.w(TAG, "Failed to write OCR sidecar for $photoId: ${e.message}")
        }
    }

    /** Load the OCR text for a given vault item. Returns null when absent or unreadable. */
    fun loadOcr(photo: VaultPhoto): String? {
        val sidecar = File(photo.encryptedFile.parentFile, "${photo.id}.ocr.enc")
        if (!sidecar.exists()) return null
        return try {
            val obj = JSONObject(String(crypto.decryptFile(sidecar), Charsets.UTF_8))
            obj.optString("text", null)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read OCR sidecar for ${photo.id}: ${e.message}")
            null
        }
    }

    /** True if an OCR sidecar exists for this item. Cheap file-existence check. */
    fun hasOcr(photo: VaultPhoto): Boolean =
        File(photo.encryptedFile.parentFile, "${photo.id}.ocr.enc").exists()

    /**
     * Heuristic: does this OCR output look like coherent Latin-script text,
     * or did the recognizer give up on a non-Latin script and emit garbage?
     *
     * ML Kit Text Recognition v2 only ships a Latin model (no Arabic, no
     * Hebrew, no Thai). Pointing it at Arabic glyphs returns either non-Latin
     * Unicode (random) or short bursts of symbols. Writing a sidecar for that
     * gives the AI Assistant a corpus it can't read — every answer will be
     * wrong. We refuse to write the sidecar at all in that case.
     *
     * Heuristic: at least 50 non-whitespace chars, AND at least 40% of those
     * are Latin letters (Unicode < U+024F, covering Basic Latin + Latin-1
     * Supplement + Latin Extended-A/B). Not bulletproof — a Latin recognizer
     * fed Arabic can still emit plausibly-Latin garbage — but catches the
     * common failure mode where output is dominated by punctuation, digits,
     * or non-Latin Unicode characters.
     */
    fun looksLikeReadableLatinOcr(text: String): Boolean {
        if (text.length < 50) return false
        val nonWhitespace = text.filterNot { it.isWhitespace() }
        if (nonWhitespace.length < 30) return false
        val latinLetters = nonWhitespace.count { it.isLetter() && it.code < 0x024F }
        val ratio = latinLetters.toFloat() / nonWhitespace.length
        return ratio >= 0.4f
    }

    /**
     * Outcome of [extractOcrForPdf]. Lets the caller distinguish "wasn't a
     * useable PDF" from "found pages but no text" so the UI can show the
     * right message.
     */
    sealed class OcrExtractionResult {
        /** Text was extracted and the sidecar was written. */
        data class Success(val pageCount: Int, val charCount: Int, val viaOcr: Boolean) : OcrExtractionResult()
        /** PDF opened OK but produced no readable text after both passes. */
        object NoTextFound : OcrExtractionResult()
        /** PDF couldn't be opened / decrypted / parsed at all. */
        object Failed : OcrExtractionResult()
    }

    /**
     * Try to extract text from an existing vault PDF and write an
     * `<id>.ocr.enc` sidecar so the Assistant can answer questions about it.
     *
     * Hybrid strategy:
     *  1. Native text-layer pull via PdfBox-Android — fast, accurate, works
     *     on PDFs generated from Word / Pages / browsers / bank statements.
     *  2. If PdfBox returns less than ~50 chars total, fall back to rendering
     *     each page via [android.graphics.pdf.PdfRenderer] and running ML Kit
     *     `TextRecognizer` on the bitmap — same pipeline the in-app scanner
     *     uses for fresh scans. Slow (a few seconds per page) but works on
     *     image-of-pages PDFs.
     *
     * `onProgress(current, total)` fires during the OCR fallback only — for
     * PdfBox-only docs it skips straight to completion.
     *
     * Idempotent: if a sidecar already exists, it's overwritten. The caller
     * is responsible for asking the user before re-extracting.
     *
     * Must be called from a coroutine on `Dispatchers.IO`. PdfBox + ML Kit
     * are blocking; the scanner's parent fileprovider is also write-bound.
     */
    suspend fun extractOcrForPdf(
        photo: VaultPhoto,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): OcrExtractionResult {
        if (photo.mediaType != VaultMediaType.PDF) return OcrExtractionResult.Failed

        // One-time PdfBox init — safe to call repeatedly, it short-circuits.
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        } catch (e: Exception) {
            Log.w(TAG, "PdfBox init failed: ${e.message}")
            // Not fatal — we can still try the render-OCR fallback.
        }

        // Decrypt to a temp file. Both PdfBox and PdfRenderer need a file
        // handle, not raw bytes. Keep it in cacheDir so the OS reclaims it
        // if we crash before the `finally` cleanup runs.
        val tempPdf = File.createTempFile("ocr_extract_", ".pdf", context.cacheDir)
        try {
            val bytes = loadFile(photo.encryptedFile) ?: return OcrExtractionResult.Failed
            tempPdf.writeBytes(bytes)

            // 1. Try the cheap native path first.
            val (nativeText, nativePageCount) = try {
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(tempPdf).use { doc ->
                    val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                    stripper.getText(doc).trim() to doc.numberOfPages
                }
            } catch (e: Exception) {
                Log.w(TAG, "PdfBox extraction failed for ${photo.id}: ${e.message}")
                "" to 0
            }

            // If the native pass got a usable amount of text, write the
            // sidecar and skip the render-OCR fallback. We dropped the
            // looksLikeReadableLatinOcr gate when Tesseract replaced ML
            // Kit (Track A1.3) — the fallback handles non-Latin scripts
            // correctly now via downloaded language packs, so falling
            // through is safe even on Arabic / CJK docs.
            if (nativeText.length >= 50) {
                val perPage = nativeText.split("")  // PdfBox separates pages with form-feed
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                saveOcrSidecar(
                    photo.id,
                    photo.encryptedFile.parentFile ?: return OcrExtractionResult.Failed,
                    nativeText,
                    perPage.takeIf { it.size > 1 }
                )
                Log.i(TAG, "OCR sidecar via PdfBox: ${photo.id} (${nativeText.length} chars, $nativePageCount pages)")
                return OcrExtractionResult.Success(
                    pageCount = nativePageCount.coerceAtLeast(1),
                    charCount = nativeText.length,
                    viaOcr = false
                )
            }

            // 2. Fall back to render-and-OCR. Slow but covers image-of-pages
            // PDFs that PdfBox can't read text from. Track A1.3: ML Kit
            // text-recognition → Tesseract 5. Uses whichever languages the
            // user has downloaded via Settings → OCR languages.
            val pfd = android.os.ParcelFileDescriptor.open(
                tempPdf, android.os.ParcelFileDescriptor.MODE_READ_ONLY
            )
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            val perPage = mutableListOf<String>()
            try {
                for (i in 0 until pageCount) {
                    onProgress(i + 1, pageCount)
                    val page = renderer.openPage(i)
                    // Target ~1240px wide to match the scanner's saved-PDF
                    // resolution — gives the recogniser comparable input quality.
                    val targetW = 1240
                    val targetH = (targetW.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bmp = android.graphics.Bitmap.createBitmap(
                        targetW, targetH, android.graphics.Bitmap.Config.ARGB_8888
                    )
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    try {
                        val text = com.privateai.camera.bridge.TesseractRecognizer
                            .recognizeInstalledLanguages(context, bmp)
                        perPage.add(text)
                    } catch (e: Exception) {
                        Log.w(TAG, "OCR failed for page $i of ${photo.id}: ${e.message}")
                        perPage.add("")
                    } finally {
                        bmp.recycle()
                    }
                }
            } finally {
                try { renderer.close() } catch (_: Exception) {}
                try { pfd.close() } catch (_: Exception) {}
            }

            val fullText = perPage.joinToString("\n\n").trim()
            if (fullText.isEmpty()) {
                Log.i(TAG, "OCR fallback produced no text for ${photo.id}")
                return OcrExtractionResult.NoTextFound
            }
            // With Tesseract we no longer need the Latin-only sanity gate
            // — the user picked which languages to install, so the output
            // script is by definition something the engine knows. Any
            // non-empty result is worth saving.
            saveOcrSidecar(
                photo.id,
                photo.encryptedFile.parentFile ?: return OcrExtractionResult.Failed,
                fullText,
                perPage
            )
            Log.i(TAG, "OCR sidecar written via render+Tesseract: ${photo.id} ($pageCount pages, ${fullText.length} chars)")
            return OcrExtractionResult.Success(
                pageCount = pageCount,
                charCount = fullText.length,
                viaOcr = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "extractOcrForPdf failed for ${photo.id}: ${e.message}", e)
            return OcrExtractionResult.Failed
        } finally {
            try { tempPdf.delete() } catch (_: Exception) {}
        }
    }

    /**
     * Extract OCR text directly from an UNENCRYPTED on-disk PDF file. Used by
     * the AI Assistant when the user attaches a PDF from device storage (SAF
     * picker) — the file isn't in the vault, so there's no `VaultPhoto` and
     * no sidecar to write. We just want the text in memory for one session.
     *
     * Same two-pass pipeline as [extractOcrForPdf]: PdfBox native text first,
     * fall back to render + ML Kit OCR. Returns the extracted text on success,
     * or null when the file can't be opened / both passes produce nothing
     * usable.
     */
    suspend fun extractOcrTextFromPdfFile(
        pdfFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): String? {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return null
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        } catch (e: Exception) {
            Log.w(TAG, "PdfBox init failed: ${e.message}")
        }
        try {
            val (nativeText, _) = try {
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(pdfFile).use { doc ->
                    val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                    stripper.getText(doc).trim() to doc.numberOfPages
                }
            } catch (e: Exception) {
                Log.w(TAG, "PdfBox extraction failed for ${pdfFile.name}: ${e.message}")
                "" to 0
            }
            // Pre-Tesseract this required a Latin-only sanity gate to
            // avoid pointing the Assistant at garbled non-Latin output.
            // Tesseract handles whatever language the user has installed,
            // so any non-empty native text layer is good to return.
            if (nativeText.length >= 50) {
                return nativeText
            }
            val pfd = android.os.ParcelFileDescriptor.open(
                pdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY
            )
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            val perPage = mutableListOf<String>()
            try {
                for (i in 0 until pageCount) {
                    onProgress(i + 1, pageCount)
                    val page = renderer.openPage(i)
                    val targetW = 1240
                    val targetH = (targetW.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bmp = android.graphics.Bitmap.createBitmap(
                        targetW, targetH, android.graphics.Bitmap.Config.ARGB_8888
                    )
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    try {
                        val text = com.privateai.camera.bridge.TesseractRecognizer
                            .recognizeInstalledLanguages(context, bmp)
                        perPage.add(text)
                    } catch (e: Exception) {
                        Log.w(TAG, "OCR failed for page $i of ${pdfFile.name}: ${e.message}")
                        perPage.add("")
                    } finally {
                        bmp.recycle()
                    }
                }
            } finally {
                try { renderer.close() } catch (_: Exception) {}
                try { pfd.close() } catch (_: Exception) {}
            }
            val fullText = perPage.joinToString("\n\n").trim()
            if (fullText.isEmpty()) return null
            return fullText
        } catch (e: Exception) {
            Log.e(TAG, "extractOcrTextFromPdfFile failed for ${pdfFile.name}: ${e.message}", e)
            return null
        }
    }

    /**
     * One-shot pass over the entire vault directory: for every `<id>.meta.enc`
     * sidecar, decrypt it, read the EXIF `dateTaken`, and re-apply it as the
     * file mtime of the corresponding photo / thumb / video files.
     *
     * Used after backup restore to fix the situation where the extracted
     * files all carry the restore-time mtime (because older backups didn't
     * preserve mtime in the ZipEntry). Returns the count of photos updated.
     */
    fun restorePhotoTimestampsFromMetadata(): Int {
        if (!vaultDir.exists()) return 0
        var updated = 0
        // Walk top-down through every dir (categories + custom folders + trash).
        vaultDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".meta.enc") }
            .forEach { sidecar ->
                val id = sidecar.name.removeSuffix(".meta.enc")
                val parent = sidecar.parentFile ?: return@forEach
                val dateTaken = try {
                    val obj = JSONObject(String(crypto.decryptFile(sidecar), Charsets.UTF_8))
                    obj.optLongOrNull("d")
                } catch (e: Exception) {
                    Log.w(TAG, "Sidecar unreadable during restore-fixup ${sidecar.name}: ${e.message}")
                    null
                } ?: return@forEach

                // Apply to every plausible companion file for this id.
                listOf(
                    "$id.enc", "$id.thumb.enc",
                    "$id.vid.enc", "$id.vid.thumb.enc",
                    "$id.pdf.enc", "$id.file.enc"
                ).forEach { name ->
                    val f = File(parent, name)
                    if (f.exists()) f.setLastModified(dateTaken)
                }
                updated++
            }
        return updated
    }

    // ===== Starred photos =====

    private val starredFile = File(vaultDir, "_starred.enc")

    fun isStarred(photoId: String): Boolean = loadStarred().contains(photoId)

    fun setStarred(photoId: String, starred: Boolean) {
        val current = loadStarred().toMutableSet()
        if (starred) current.add(photoId) else current.remove(photoId)
        saveStarred(current)
    }

    fun listStarred(): Set<String> = loadStarred()

    private fun loadStarred(): Set<String> {
        if (!starredFile.exists()) return emptySet()
        return try {
            val plaintext = crypto.decryptFile(starredFile)
            val arr = JSONArray(String(plaintext, Charsets.UTF_8))
            buildSet {
                for (i in 0 until arr.length()) add(arr.getString(i))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load starred set: ${e.message}")
            emptySet()
        }
    }

    private fun saveStarred(ids: Set<String>) {
        try {
            val arr = JSONArray()
            ids.forEach { arr.put(it) }
            crypto.encryptToFile(arr.toString().toByteArray(Charsets.UTF_8), starredFile)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save starred set: ${e.message}")
        }
    }

    /**
     * List items in a custom folder directory.
     */
    fun listFolderItems(folderDir: File): List<VaultPhoto> {
        if (!folderDir.exists()) return emptyList()
        val files = folderDir.listFiles() ?: return emptyList()
        val items = mutableListOf<VaultPhoto>()

        // Photos: {id}.enc (excluding .thumb.enc, .vid.enc, .pdf.enc, .file.enc, .meta.enc, .ocr.enc, and any file containing .pdf)
        files.filter {
            it.isFile && it.name.endsWith(".enc") &&
                !it.name.endsWith(".thumb.enc") &&
                !it.name.endsWith(".vid.enc") &&
                !it.name.endsWith(".pdf.enc") &&
                !it.name.endsWith(".file.enc") &&
                !it.name.endsWith(".meta.enc") &&
                !it.name.endsWith(".ocr.enc") &&
                !it.name.contains(".pdf.") &&
                !it.name.startsWith("_tobedeleted_")
        }.forEach { file ->
            val id = file.name.removeSuffix(".enc")
            items.add(VaultPhoto(id, file.lastModified(), VaultCategory.FILES, file, File(folderDir, "$id.thumb.enc")))
        }

        // Videos
        files.filter {
            it.name.endsWith(".vid.enc") && !it.name.endsWith(".vid.thumb.enc") && !it.name.startsWith("_tobedeleted_")
        }.forEach { file ->
            val id = file.name.removeSuffix(".vid.enc")
            items.add(VaultPhoto(id, file.lastModified(), VaultCategory.FILES, file, File(folderDir, "$id.vid.thumb.enc"), VaultMediaType.VIDEO))
        }

        // PDFs
        files.filter {
            it.name.endsWith(".pdf.enc") && !it.name.startsWith("_tobedeleted_")
        }.forEach { file ->
            val id = file.name.removeSuffix(".pdf.enc")
            items.add(VaultPhoto(id, file.lastModified(), VaultCategory.FILES, file, file, VaultMediaType.PDF))
        }

        // Generic files (docs, spreadsheets, etc.) — PDFs detected by name get PDF type
        files.filter {
            it.name.endsWith(".file.enc") && !it.name.startsWith("_tobedeleted_")
        }.forEach { file ->
            val id = file.name.removeSuffix(".file.enc")
            val isPdf = id.lowercase().endsWith(".pdf") || id.lowercase().contains(".pdf")
            if (items.none { it.encryptedFile == file }) {
                items.add(VaultPhoto(id, file.lastModified(), VaultCategory.FILES, file, file, if (isPdf) VaultMediaType.PDF else VaultMediaType.FILE))
            }
        }

        return items.sortedByDescending { it.timestamp }
    }

    /**
     * Move a photo to a custom folder directory.
     */
    fun moveToFolder(photo: VaultPhoto, targetDir: File) {
        targetDir.mkdirs()
        val newEncFile = File(targetDir, photo.encryptedFile.name)
        val newThumbFile = File(targetDir, photo.thumbnailFile.name)
        photo.encryptedFile.renameTo(newEncFile)
        if (photo.thumbnailFile.exists() && photo.thumbnailFile != photo.encryptedFile) {
            photo.thumbnailFile.renameTo(newThumbFile)
        }
        // Carry the metadata + OCR sidecars along — otherwise moving a scanned
        // doc would silently lose its dateTaken/GPS or its Ask-the-Assistant text.
        val srcMeta = File(photo.encryptedFile.parentFile, "${photo.id}.meta.enc")
        if (srcMeta.exists()) srcMeta.renameTo(File(targetDir, "${photo.id}.meta.enc"))
        val srcOcr = File(photo.encryptedFile.parentFile, "${photo.id}.ocr.enc")
        if (srcOcr.exists()) srcOcr.renameTo(File(targetDir, "${photo.id}.ocr.enc"))
        Log.d(TAG, "Photo moved to folder: ${photo.id}")
    }

    /**
     * Get vault size in bytes.
     */
    fun getVaultSize(): Long {
        return vaultDir.walkTopDown().filter { it.isFile }.fold(0L) { acc, file -> acc + file.length() }
    }

    // ===== Rename =====

    /** Outcome of a rename attempt — used by callers to display the right toast. */
    sealed class RenameResult {
        data class Success(val updated: VaultPhoto) : RenameResult()
        object InvalidName : RenameResult()
        object NameAlreadyExists : RenameResult()
        object Failed : RenameResult()
    }

    /**
     * Rename a vault item. The user supplies a base name (e.g. "Lease 2024");
     * we preserve the existing extension/suffix internally so the item type
     * detection in [listPhotos] / [listFolderItems] keeps working. Updates:
     *   - the encrypted file
     *   - the thumbnail file (if separate from the encrypted file)
     *   - `<id>.meta.enc` sidecar (EXIF preservation)
     *   - `<id>.ocr.enc` sidecar (Ask My Documents)
     *   - the starred set entry, if the item was starred
     *
     * Refuses if the resulting filename already exists in the same dir
     * (uniqueness is the source of truth for the id, so two items with the
     * same name would silently collide).
     */
    fun renameItem(photo: VaultPhoto, newBaseName: String): RenameResult {
        // Sanitize: strip any path separators and a few characters that would
        // confuse downstream filesystems / our id parsing. Empty or only-dot
        // names are rejected.
        val cleaned = newBaseName.trim()
            .replace(Regex("""[/\\:*?"<>|]"""), "")
        if (cleaned.isBlank() || cleaned == "." || cleaned == "..") return RenameResult.InvalidName

        val parent = photo.encryptedFile.parentFile ?: return RenameResult.Failed
        val oldEncFile = photo.encryptedFile
        val oldEncName = oldEncFile.name
        val oldId = photo.id

        // Determine the encrypted-file suffix we need to preserve based on
        // current naming. listFolderItems / listPhotos detect type by suffix.
        val encSuffix = when {
            oldEncName.endsWith(".pdf.enc") -> ".pdf.enc"
            oldEncName.endsWith(".vid.enc") -> ".vid.enc"
            oldEncName.endsWith(".file.enc") -> ".file.enc"
            oldEncName.endsWith(".enc") -> ".enc"
            else -> return RenameResult.Failed
        }

        // For PDFs the id (== old filename) keeps the .pdf hint. Strip it so
        // the user types just "Lease 2024" and we add ".pdf" back. For files
        // we preserve any extension the user typed; for photos/videos there's
        // no conventional extension on the id.
        val newId = when {
            encSuffix == ".pdf.enc" -> if (cleaned.lowercase().endsWith(".pdf")) cleaned else "$cleaned.pdf"
            encSuffix == ".vid.enc" -> cleaned
            encSuffix == ".file.enc" -> cleaned
            else -> cleaned
        }
        if (newId == oldId) return RenameResult.Success(photo)  // no-op

        val newEncFile = File(parent, "$newId$encSuffix")
        if (newEncFile.exists()) return RenameResult.NameAlreadyExists

        // Atomic-enough: rename the primary file first; if that fails, we
        // never touched anything else. After it succeeds, sidecar renames
        // are best-effort.
        if (!oldEncFile.renameTo(newEncFile)) return RenameResult.Failed

        // Thumbnail (only when stored in a separate file — for PDFs the
        // encrypted file IS the thumbnail).
        if (photo.thumbnailFile != photo.encryptedFile && photo.thumbnailFile.exists()) {
            val thumbSuffix = when {
                photo.thumbnailFile.name.endsWith(".vid.thumb.enc") -> ".vid.thumb.enc"
                photo.thumbnailFile.name.endsWith(".thumb.enc") -> ".thumb.enc"
                else -> null
            }
            if (thumbSuffix != null) {
                val newThumb = File(parent, "$newId$thumbSuffix")
                photo.thumbnailFile.renameTo(newThumb)
            }
        }
        // Metadata sidecar.
        val oldMeta = File(parent, "$oldId.meta.enc")
        if (oldMeta.exists()) oldMeta.renameTo(File(parent, "$newId.meta.enc"))
        // OCR sidecar.
        val oldOcr = File(parent, "$oldId.ocr.enc")
        if (oldOcr.exists()) oldOcr.renameTo(File(parent, "$newId.ocr.enc"))

        // Starred set: if this id was starred, replace with the new id.
        try {
            val starred = loadStarred()
            if (oldId in starred) {
                val updated = starred.toMutableSet().apply {
                    remove(oldId)
                    add(newId)
                }
                saveStarred(updated)
            }
        } catch (e: Exception) {
            Log.w(TAG, "rename: failed to update starred set for $oldId → $newId: ${e.message}")
        }

        // Build the updated VaultPhoto. Thumbnail file is recomputed because
        // for non-PDFs it was a separate file (we just renamed it).
        val newThumbFile = when {
            photo.thumbnailFile == photo.encryptedFile -> newEncFile
            photo.thumbnailFile.name.endsWith(".vid.thumb.enc") ->
                File(parent, "$newId.vid.thumb.enc")
            else -> File(parent, "$newId.thumb.enc")
        }
        val updated = VaultPhoto(
            id = newId,
            timestamp = photo.timestamp,
            category = photo.category,
            encryptedFile = newEncFile,
            thumbnailFile = newThumbFile,
            mediaType = photo.mediaType
        )
        Log.d(TAG, "Renamed: $oldId → $newId")
        return RenameResult.Success(updated)
    }

    // ===== Trash / Recycle Bin =====

    private val trashDir = File(vaultDir, "_trash").also { it.mkdirs() }
    private val trashIndexFile = File(trashDir, "_index.enc")

    data class TrashedItem(
        val id: String,
        val originalCategory: VaultCategory,
        val trashedAt: Long,
        val encryptedFile: File,
        val thumbnailFile: File,
        val mediaType: VaultMediaType
    )

    /** Move a photo to trash instead of permanently deleting. */
    fun moveToTrash(photo: VaultPhoto) {
        // Move files to trash dir
        val trashedEnc = File(trashDir, photo.encryptedFile.name)
        val trashedThumb = File(trashDir, photo.thumbnailFile.name)
        photo.encryptedFile.renameTo(trashedEnc)
        if (photo.thumbnailFile.exists() && photo.thumbnailFile != photo.encryptedFile) {
            photo.thumbnailFile.renameTo(trashedThumb)
        }
        // Move metadata + OCR sidecars (best-effort — not all photos have them).
        val srcMeta = File(photo.encryptedFile.parentFile, "${photo.id}.meta.enc")
        if (srcMeta.exists()) {
            srcMeta.renameTo(File(trashDir, "${photo.id}.meta.enc"))
        }
        val srcOcr = File(photo.encryptedFile.parentFile, "${photo.id}.ocr.enc")
        if (srcOcr.exists()) {
            srcOcr.renameTo(File(trashDir, "${photo.id}.ocr.enc"))
        }
        // Add to trash index
        val items = loadTrashIndex().toMutableList()
        items.add(TrashedItem(photo.id, photo.category, System.currentTimeMillis(), trashedEnc, trashedThumb, photo.mediaType))
        saveTrashIndex(items)
        Log.d(TAG, "Moved to trash: ${photo.id}")
    }

    /**
     * Bulk move to trash. Each item is processed independently — a single
     * failed rename or exception does NOT abort the rest. The trash index is
     * loaded once and saved once at the end, which makes large multi-deletes
     * dramatically faster (turns 73× read+encrypt+write into 1× read + 1×
     * write) and prevents partial-failure orphans where the loop blew up
     * mid-iteration after a few photos succeeded.
     *
     * Returns the number of items successfully moved (so callers can surface
     * "moved X of N" to the user when not all succeed).
     */
    fun moveToTrashBatch(photos: List<VaultPhoto>): Int {
        if (photos.isEmpty()) return 0
        val items = loadTrashIndex().toMutableList()
        var successCount = 0
        val now = System.currentTimeMillis()
        photos.forEach { photo ->
            try {
                val trashedEnc = File(trashDir, photo.encryptedFile.name)
                val trashedThumb = File(trashDir, photo.thumbnailFile.name)
                val encMoved = photo.encryptedFile.renameTo(trashedEnc)
                if (!encMoved) {
                    Log.w(TAG, "moveToTrashBatch: rename failed for ${photo.id} (file missing or cross-device)")
                    return@forEach
                }
                if (photo.thumbnailFile.exists() && photo.thumbnailFile != photo.encryptedFile) {
                    photo.thumbnailFile.renameTo(trashedThumb)
                }
                // Best-effort sidecar move alongside the photo.
                val srcMeta = File(photo.encryptedFile.parentFile, "${photo.id}.meta.enc")
                if (srcMeta.exists()) srcMeta.renameTo(File(trashDir, "${photo.id}.meta.enc"))
                val srcOcr = File(photo.encryptedFile.parentFile, "${photo.id}.ocr.enc")
                if (srcOcr.exists()) srcOcr.renameTo(File(trashDir, "${photo.id}.ocr.enc"))
                items.add(TrashedItem(photo.id, photo.category, now, trashedEnc, trashedThumb, photo.mediaType))
                successCount++
            } catch (e: Exception) {
                Log.e(TAG, "moveToTrashBatch failed for ${photo.id}: ${e.message}")
            }
        }
        try {
            saveTrashIndex(items)
        } catch (e: Exception) {
            // Index save failed — the files are already renamed into _trash/
            // but the index doesn't know about them. They become orphans.
            // Returning 0 here would also be wrong (they ARE in _trash, just
            // unreferenced). Surface the failure honestly.
            Log.e(TAG, "moveToTrashBatch: index save failed — ${successCount} items orphaned: ${e.message}")
            return -successCount  // negative signals "moved but unindexed"
        }
        Log.d(TAG, "moveToTrashBatch: ${successCount}/${photos.size} moved to trash")
        return successCount
    }

    /** Restore a photo from trash to its original category. */
    fun restoreFromTrash(itemId: String) {
        val items = loadTrashIndex().toMutableList()
        val item = items.find { it.id == itemId } ?: return
        val targetDir = categoryDir(item.originalCategory)
        // Move files back
        val restoredEnc = File(targetDir, item.encryptedFile.name)
        val restoredThumb = File(targetDir, item.thumbnailFile.name)
        item.encryptedFile.renameTo(restoredEnc)
        if (item.thumbnailFile.exists() && item.thumbnailFile != item.encryptedFile) {
            item.thumbnailFile.renameTo(restoredThumb)
        }
        // Restore the metadata + OCR sidecars if they're in the trash dir.
        val trashedMeta = File(trashDir, "$itemId.meta.enc")
        if (trashedMeta.exists()) trashedMeta.renameTo(File(targetDir, "$itemId.meta.enc"))
        val trashedOcr = File(trashDir, "$itemId.ocr.enc")
        if (trashedOcr.exists()) trashedOcr.renameTo(File(targetDir, "$itemId.ocr.enc"))
        items.removeAll { it.id == itemId }
        saveTrashIndex(items)
        Log.d(TAG, "Restored from trash: $itemId")
    }

    /** List all items in trash. */
    fun listTrash(): List<TrashedItem> = loadTrashIndex().sortedByDescending { it.trashedAt }

    /** Permanently delete a single item from trash. */
    fun permanentDeleteFromTrash(itemId: String) {
        val items = loadTrashIndex().toMutableList()
        val item = items.find { it.id == itemId } ?: return
        item.encryptedFile.delete()
        item.thumbnailFile.delete()
        File(trashDir, "$itemId.meta.enc").delete()
        File(trashDir, "$itemId.ocr.enc").delete()
        items.removeAll { it.id == itemId }
        saveTrashIndex(items)
        Log.d(TAG, "Permanently deleted from trash: $itemId")
    }

    /** Empty all trash. */
    fun emptyTrash() {
        val items = loadTrashIndex()
        items.forEach {
            it.encryptedFile.delete(); it.thumbnailFile.delete()
            File(trashDir, "${it.id}.meta.enc").delete()
            File(trashDir, "${it.id}.ocr.enc").delete()
        }
        saveTrashIndex(emptyList())
        Log.d(TAG, "Trash emptied: ${items.size} items")
    }

    /** Auto-purge items older than maxDays. Call on vault open. */
    fun autoPurgeTrash(maxDays: Int = 30) {
        val cutoff = System.currentTimeMillis() - maxDays.toLong() * 24 * 60 * 60 * 1000
        val items = loadTrashIndex().toMutableList()
        val expired = items.filter { it.trashedAt < cutoff }
        if (expired.isEmpty()) return
        expired.forEach {
            it.encryptedFile.delete(); it.thumbnailFile.delete()
            File(trashDir, "${it.id}.meta.enc").delete()
            File(trashDir, "${it.id}.ocr.enc").delete()
        }
        items.removeAll { it.trashedAt < cutoff }
        saveTrashIndex(items)
        Log.d(TAG, "Auto-purged ${expired.size} items from trash")
    }

    /** Trash item count. */
    fun trashCount(): Int = loadTrashIndex().size

    private fun loadTrashIndex(): List<TrashedItem> {
        if (!trashIndexFile.exists()) return emptyList()
        return try {
            val json = String(crypto.decryptFile(trashIndexFile), Charsets.UTF_8)
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                TrashedItem(
                    id = obj.getString("id"),
                    originalCategory = try { VaultCategory.valueOf(obj.getString("category")) } catch (_: Exception) { VaultCategory.CAMERA },
                    trashedAt = obj.getLong("trashedAt"),
                    encryptedFile = File(trashDir, obj.getString("encFile")),
                    thumbnailFile = File(trashDir, obj.getString("thumbFile")),
                    mediaType = try { VaultMediaType.valueOf(obj.getString("mediaType")) } catch (_: Exception) { VaultMediaType.PHOTO }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load trash index: ${e.message}")
            emptyList()
        }
    }

    private fun saveTrashIndex(items: List<TrashedItem>) {
        try {
            val arr = org.json.JSONArray()
            items.forEach { item ->
                arr.put(org.json.JSONObject().apply {
                    put("id", item.id)
                    put("category", item.originalCategory.name)
                    put("trashedAt", item.trashedAt)
                    put("encFile", item.encryptedFile.name)
                    put("thumbFile", item.thumbnailFile.name)
                    put("mediaType", item.mediaType.name)
                })
            }
            crypto.encryptToFile(arr.toString().toByteArray(Charsets.UTF_8), trashIndexFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save trash index: ${e.message}")
        }
    }

    /**
     * Wipe all vault files. Used by duress PIN.
     */
    fun wipeAll() {
        vaultDir.listFiles()?.forEach {
            if (it.isDirectory) it.deleteRecursively() else it.delete()
        }
        Log.i(TAG, "Vault wiped")
    }
}
