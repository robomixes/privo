// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.util

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.privateai.camera.security.PhotoMetadata
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * EXIF reader for imports.
 *
 * The job of this helper is to pull source metadata out of the raw image
 * bytes BEFORE [android.graphics.BitmapFactory.decodeByteArray] turns them
 * into a Bitmap (which discards EXIF). Everything we extract here gets
 * written back into an encrypted `.meta.enc` sidecar so the vault can
 * answer "when / where / how" about a photo even though the encrypted
 * JPEG payload itself carries no EXIF.
 *
 * In-memory only — never writes plaintext to disk.
 */
object ExifUtils {

    private const val TAG = "ExifUtils"

    /** Convenience for callers that only need the capture date. */
    fun readDateTaken(bytes: ByteArray): Long? = readMetadata(bytes)?.dateTaken

    /**
     * Read every metadata field we currently care about. Returns null only
     * when the bytes can't be parsed at all; otherwise individual fields can
     * still be null when the source didn't include that tag.
     */
    fun readMetadata(bytes: ByteArray): PhotoMetadata? {
        if (bytes.isEmpty()) return null
        return try {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            PhotoMetadata(
                dateTaken = readExifDate(exif),
                width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                    .takeIf { it > 0 }
                    ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0).takeIf { it > 0 },
                height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                    .takeIf { it > 0 }
                    ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0).takeIf { it > 0 },
                orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                ).takeIf { it != ExifInterface.ORIENTATION_UNDEFINED },
                // ExifInterface.latLong is the canonical accessor — handles
                // the rational-number + N/S/E/W ref parsing internally.
                gpsLat = exif.latLong?.getOrNull(0),
                gpsLng = exif.latLong?.getOrNull(1)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF: ${e.message}")
            null
        }
    }

    private fun readExifDate(exif: ExifInterface): Long? {
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        return try {
            // EXIF stores the timestamp without a timezone — read as UTC for
            // consistent cross-device sorting. Rare hour-level drift is
            // acceptable for "which year was this from".
            val fmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.parse(raw)?.time
        } catch (_: Exception) { null }
    }
}
