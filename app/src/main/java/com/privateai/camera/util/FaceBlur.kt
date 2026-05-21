// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.privateai.camera.bridge.FaceDetectorHolder

/**
 * Detects faces in a bitmap and returns a copy with faces blurred.
 *
 * Uses Privora's ONNX face detector (YuNet) — replaces the ML Kit detector
 * that lived here through v2.0.x. The pixelate-style blur logic below is
 * unchanged; only the detection backend swapped.
 */
object FaceBlur {

    private const val TAG = "FaceBlur"

    /**
     * Detect faces and pixelate-blur them. Returns a new bitmap on success,
     * or the original bitmap when no faces are found / detection fails.
     *
     * Kept as `suspend` for source-compat with the v2.0.x ML Kit signature
     * (callers `await`-ed it). The new ONNX path is synchronous CPU work —
     * callers should still dispatch on Dispatchers.IO if they care about
     * keeping the main thread free.
     */
    suspend fun blurFaces(context: Context, bitmap: Bitmap): Bitmap {
        return try {
            val faces = FaceDetectorHolder.get(context).detect(bitmap)
            if (faces.isEmpty()) {
                Log.d(TAG, "No faces found")
                return bitmap
            }
            Log.d(TAG, "Found ${faces.size} face(s), blurring…")
            val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            for (face in faces) {
                // Expand the box ~10% so the blur fully covers ears /
                // forehead / chin — the detector's tight crop sometimes
                // leaves identifiable skin around the box edge.
                val w = face.width
                val h = face.height
                val expanded = Rect(
                    (face.left - w * 0.1f).toInt().coerceAtLeast(0),
                    (face.top - h * 0.1f).toInt().coerceAtLeast(0),
                    (face.right + w * 0.1f).toInt().coerceAtMost(result.width),
                    (face.bottom + h * 0.1f).toInt().coerceAtMost(result.height)
                )
                pixelBlurRegion(result, expanded)
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Face blur failed: ${e.message}")
            bitmap
        }
    }

    /** Quick "are there any faces in this image" check, no blur. */
    suspend fun hasFaces(context: Context, bitmap: Bitmap): Boolean {
        return try {
            FaceDetectorHolder.get(context).detect(bitmap).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Pixel-level box blur (chunky pixelation). No RenderScript dependency —
     * just averages NxN blocks within the rect and re-stamps the block colour.
     * Block size scales with face size so small faces get a similar visual
     * effect to large ones.
     */
    private fun pixelBlurRegion(bitmap: Bitmap, rect: Rect) {
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val right = rect.right.coerceIn(left + 1, bitmap.width)
        val bottom = rect.bottom.coerceIn(top + 1, bitmap.height)
        val width = right - left
        val height = bottom - top
        if (width < 2 || height < 2) return

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, left, top, width, height)

        val blockSize = maxOf(width, height) / 8
        if (blockSize < 2) return

        for (y in 0 until height step blockSize) {
            for (x in 0 until width step blockSize) {
                val bw = minOf(blockSize, width - x)
                val bh = minOf(blockSize, height - y)
                var r = 0L; var g = 0L; var b = 0L
                var count = 0
                for (dy in 0 until bh) {
                    for (dx in 0 until bw) {
                        val pixel = pixels[(y + dy) * width + (x + dx)]
                        r += (pixel shr 16) and 0xFF
                        g += (pixel shr 8) and 0xFF
                        b += pixel and 0xFF
                        count++
                    }
                }
                if (count == 0) continue
                val avgColor = (0xFF shl 24) or
                    ((r / count).toInt() shl 16) or
                    ((g / count).toInt() shl 8) or
                    (b / count).toInt()
                for (dy in 0 until bh) {
                    for (dx in 0 until bw) {
                        pixels[(y + dy) * width + (x + dx)] = avgColor
                    }
                }
            }
        }
        bitmap.setPixels(pixels, 0, width, left, top, width, height)
    }
}
