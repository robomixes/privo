// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Manual perspective correction for the document scanner (Track A2 —
 * replaces ML Kit's auto deskew).
 *
 * Approach: pure Android. `Matrix.setPolyToPoly(src, dst, count=4)`
 * computes a full perspective transform from the user-drawn quad to a
 * rectangle, then `Canvas.drawBitmap(bitmap, matrix, paint)` rasterises
 * the warp. No OpenCV needed — `setPolyToPoly` with `count=4` is exactly
 * a 4-point projective map.
 */
object PerspectiveTransform {

    /**
     * Warp [src] so that the quadrilateral defined by [quad] becomes an
     * upright rectangle. [quad] is expected in TL, TR, BR, BL order (the
     * convention the corner-drag UI maintains).
     *
     * Output dimensions: the average of the quad's two horizontal edges
     * for width, two vertical edges for height. This preserves real-
     * world aspect ratio reasonably well for documents shot from
     * moderate angles. Long-edge capped at [maxEdge] so we don't allocate
     * a multi-megabyte bitmap when the user zooms in close on a small doc.
     */
    fun warpQuadToRect(src: Bitmap, quad: List<PointF>, maxEdge: Int = 2400): Bitmap {
        require(quad.size == 4) { "expected 4 corners, got ${quad.size}" }
        val tl = quad[0]; val tr = quad[1]; val br = quad[2]; val bl = quad[3]

        // Output rectangle size = the AVERAGE of the two horizontal /
        // vertical edge lengths. Single-side max would over-shoot the
        // bitmap when the quad is sharply tilted; averaging gives a
        // result that looks close to the real document aspect.
        val widthTop = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
        val widthBottom = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())
        val heightLeft = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble())
        val heightRight = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble())

        var outW = ((widthTop + widthBottom) / 2.0).roundToInt().coerceAtLeast(1)
        var outH = ((heightLeft + heightRight) / 2.0).roundToInt().coerceAtLeast(1)

        // Scale down if either edge exceeds the cap. A 4-corner quad
        // covering most of a 4032×3024 photo could blow past 4K — keep
        // the result reasonable for downstream JPEG encoding.
        val longest = max(outW, outH)
        if (longest > maxEdge) {
            val s = maxEdge.toDouble() / longest
            outW = (outW * s).roundToInt().coerceAtLeast(1)
            outH = (outH * s).roundToInt().coerceAtLeast(1)
        }

        val srcPts = floatArrayOf(
            tl.x, tl.y,
            tr.x, tr.y,
            br.x, br.y,
            bl.x, bl.y
        )
        val dstPts = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        // White background — when the warp pulls in a slightly-larger
        // area than the destination (rare edge case at the quad seams),
        // we get white, not transparent black on the JPEG.
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(src, matrix, paint)
        return out
    }
}
