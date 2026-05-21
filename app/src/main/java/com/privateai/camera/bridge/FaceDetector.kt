// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * One face the detector found in a bitmap.
 *
 * Bounding box is in PIXEL coordinates of the source bitmap (post letterbox
 * un-mapping) so callers can crop directly. [landmarks] is a flat array of
 * 10 floats — 5 (x, y) pairs in the order YuNet emits: right eye, left eye,
 * nose tip, right mouth corner, left mouth corner. May be null if the model
 * didn't produce landmarks (current YuNet does; kept nullable for forward
 * compatibility with other detectors).
 */
data class DetectedFace(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float,
    val landmarks: FloatArray? = null
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    fun toRect(): Rect = Rect(left, top, right, bottom)
}

/**
 * ONNX-based face detector. Replaces the ML Kit `FaceDetection` client used
 * throughout v2.0.x — see Track A1.2 in the roadmap. Removing ML Kit is
 * required for F-Droid main eligibility and de-risks the desktop port
 * (LiteRT-LM and ONNX Runtime both ship JVM artifacts; ML Kit doesn't).
 *
 * Model: YuNet 2023mar (`face_detection_yunet_2023mar.onnx` from OpenCV Zoo,
 * ~234 KB). Fixed input 1×3×640×640 BGR float32 0-255 (no normalisation —
 * the original libfacedetection convention). Output is 12 tensors across
 * 3 strides (8/16/32):
 *
 *   cls_<S>:  [1, N, 1]   face/no-face logit  (sigmoid → P(face))
 *   obj_<S>:  [1, N, 1]   objectness logit    (sigmoid → P(any object))
 *   bbox_<S>: [1, N, 4]   (cx_off, cy_off, log_w, log_h) in grid units
 *   kps_<S>:  [1, N, 10]  5 landmark (x, y) offsets in grid units
 *
 * where N = (640/S)² = 6400 / 1600 / 400. Final confidence is
 * `sigmoid(cls) * sigmoid(obj)`, threshold 0.6, NMS IoU 0.3 — matches the
 * official OpenCV YuNet demo.
 *
 * Same letterbox pattern as [OnnxDetector] so detections come back in the
 * source bitmap's pixel space without callers needing to think about it.
 */
class FaceDetector(context: Context) {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    private val inputSize = 640
    private var inputName: String = "input"

    // Letterbox state — populated each [detect] call so we can un-map output
    // boxes back to the source image space.
    private var padLeft = 0f
    private var padTop = 0f
    private var scale = 1f

    var debugInfo: String = "loading…"
        private set

    companion object {
        private const val TAG = "FaceDetector"
        // OpenCV's reference default is 0.9, but on Pixel preview frames
        // (480×640 letterboxed to 640×640) that strict bar misses many
        // real faces. 0.6 swings the other way — ghost detections give
        // a flickering count (user saw 1→2→5 for a single face).
        // 0.75 is the empirical sweet spot: kills the ghost anchors while
        // still catching off-angle / slightly-occluded faces. Callers can
        // override per-call via [detect]'s [minConfidence] param.
        // OpenCV's reference YuNet default. With the 2023mar export, cls
        // and obj outputs are already-sigmoided probabilities in [0, 1],
        // so high-confidence faces routinely score 0.95+ and the 0.9
        // bar is the right cut.
        private const val CONF_THRESHOLD = 0.9f
        private const val IOU_THRESHOLD = 0.3f
        // Strides used by YuNet's 3 detection heads. Order matters for the
        // output-name lookup below; the loop assumes (stride, suffix) pairs.
        private val STRIDES = intArrayOf(8, 16, 32)
    }

    init {
        try {
            val modelBytes = context.assets.open("models/face_detect.onnx").readBytes()
            // CPU only — matches OnnxDetector. NNAPI's tensor layout / quantization
            // assumptions don't play well with YuNet's multi-output graph.
            ortSession = ortEnv.createSession(modelBytes)
            ortSession?.inputInfo?.keys?.firstOrNull()?.let { inputName = it }
            Log.i(TAG, "YuNet face-detector loaded (input=$inputName, ${inputSize}x${inputSize})")
            debugInfo = "loaded"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load face_detect.onnx: ${e.javaClass.simpleName}: ${e.message}", e)
            debugInfo = "load failed: ${e.message}"
        }
    }

    fun isAvailable(): Boolean = ortSession != null

    /**
     * Detect faces in [bitmap]. Returns boxes in the bitmap's pixel space.
     * Empty list on any failure — never throws, mirroring the old ML Kit
     * paths that returned an empty Task result on errors.
     */
    fun detect(bitmap: Bitmap, minConfidence: Float = CONF_THRESHOLD): List<DetectedFace> {
        val session = ortSession ?: return emptyList()
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
        return try {
            detectInternal(session, bitmap, minConfidence)
        } catch (e: Exception) {
            Log.e(TAG, "Face detection failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun detectInternal(session: OrtSession, bitmap: Bitmap, minConfidence: Float): List<DetectedFace> {
        // 1. Letterbox so we don't squash aspect ratios on non-square inputs.
        val letterboxed = letterbox(bitmap)
        val inputBuffer = preprocessBitmap(letterboxed)
        if (letterboxed !== bitmap) letterboxed.recycle()

        // 2. Run inference. YuNet has a single input tensor.
        val inputTensor = OnnxTensor.createTensor(
            ortEnv, inputBuffer,
            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        )
        val results = try { session.run(mapOf(inputName to inputTensor)) }
                      finally { inputTensor.close() }

        // 3. Decode each stride and concatenate raw candidates.
        val raw = mutableListOf<DetectedFace>()
        try {
            for (stride in STRIDES) {
                val cls = (results["cls_$stride"].get() as OnnxTensor).value as Array<Array<FloatArray>>
                val obj = (results["obj_$stride"].get() as OnnxTensor).value as Array<Array<FloatArray>>
                val bbx = (results["bbox_$stride"].get() as OnnxTensor).value as Array<Array<FloatArray>>
                val kps = (results["kps_$stride"].get() as OnnxTensor).value as Array<Array<FloatArray>>
                decodeStride(stride, cls[0], obj[0], bbx[0], kps[0], minConfidence, raw)
            }
        } finally {
            results.close()
        }

        // 4. NMS, then map back to source image pixels.
        val kept = nms(raw, IOU_THRESHOLD)
        debugInfo = "raw=${raw.size} kept=${kept.size}"
        return kept.map { unmapToSource(it) }
    }

    private fun decodeStride(
        stride: Int,
        cls: Array<FloatArray>,   // [N, 1]
        obj: Array<FloatArray>,   // [N, 1]
        bbox: Array<FloatArray>,  // [N, 4]
        kps: Array<FloatArray>,   // [N, 10]
        minConf: Float,
        out: MutableList<DetectedFace>
    ) {
        val gridSide = inputSize / stride  // 80 / 40 / 20
        val n = cls.size
        // Each anchor index i maps to grid (row, col) = (i / gridSide, i % gridSide).
        // YuNet's bbox / kps offsets are in GRID UNITS (multiply by stride for pixels).
        // Decode formula matches OpenCV's `FaceDetectorYN_Impl::postProcess`.
        for (i in 0 until n) {
            val confFace = cls[i][0]
            val confObj = obj[i][0]
            // Guard against NaN: when cls or obj is a raw logit, the
            // product can be negative and sqrt(negative)=NaN. NaN compares
            // false against any threshold, so a NaN score would PASS the
            // `< minConf` check and pollute the output with thousands of
            // ghost anchors — the actual root cause of the 1500-2500
            // counts we saw before this guard. Clamp non-positive products
            // to 0 so they're cleanly filtered.
            val product = confFace * confObj
            val score = if (product > 0f) kotlin.math.sqrt(product) else 0f
            if (score < minConf) continue

            val col = (i % gridSide).toFloat()
            val row = (i / gridSide).toFloat()

            val cxOff = bbox[i][0]
            val cyOff = bbox[i][1]
            val logW = bbox[i][2]
            val logH = bbox[i][3]

            val cx = (col + cxOff) * stride
            val cy = (row + cyOff) * stride
            val w = exp(logW) * stride
            val h = exp(logH) * stride

            val x1 = cx - w / 2f
            val y1 = cy - h / 2f
            val x2 = cx + w / 2f
            val y2 = cy + h / 2f

            // Landmarks (5 points, x then y per point — alternating in YuNet's
            // output). Keep in letterboxed pixel space; unmapToSource() will
            // shift them back to source coords below.
            val lm = FloatArray(10)
            for (j in 0 until 5) {
                lm[2 * j] = (col + kps[i][2 * j]) * stride
                lm[2 * j + 1] = (row + kps[i][2 * j + 1]) * stride
            }

            out.add(
                DetectedFace(
                    left = x1.toInt(),
                    top = y1.toInt(),
                    right = x2.toInt(),
                    bottom = y2.toInt(),
                    confidence = score,
                    landmarks = lm
                )
            )
        }
    }

    /**
     * Letterbox to 640×640 (gray padding) so the detector sees a square
     * frame without aspect squash. Identical structure to OnnxDetector.
     */
    private fun letterbox(bitmap: Bitmap): Bitmap {
        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()
        scale = min(inputSize / srcW, inputSize / srcH)
        val newW = (srcW * scale).toInt()
        val newH = (srcH * scale).toInt()
        padLeft = (inputSize - newW) / 2f
        padTop = (inputSize - newH) / 2f

        val out = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(0xFF808080.toInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        canvas.drawBitmap(scaled, padLeft, padTop, null)
        if (scaled !== bitmap) scaled.recycle()
        return out
    }

    /**
     * NCHW float buffer. YuNet expects BGR 0-255 (no normalisation) — the
     * original libfacedetection convention. We pull pixels in ARGB (Android's
     * native) and split into B, G, R channels in that order.
     */
    private fun preprocessBitmap(bitmap: Bitmap): FloatBuffer {
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        val channelSize = inputSize * inputSize
        val totalSize = 3 * channelSize
        val byteBuffer = ByteBuffer.allocateDirect(totalSize * 4).order(ByteOrder.nativeOrder())
        val buf = byteBuffer.asFloatBuffer()
        // Channel 0: B
        for (i in 0 until channelSize) buf.put((pixels[i] and 0xFF).toFloat())
        // Channel 1: G
        for (i in 0 until channelSize) buf.put(((pixels[i] shr 8) and 0xFF).toFloat())
        // Channel 2: R
        for (i in 0 until channelSize) buf.put(((pixels[i] shr 16) and 0xFF).toFloat())
        buf.rewind()
        return buf
    }

    /**
     * Standard greedy NMS. Sorted by confidence desc; suppress any later
     * candidate whose IoU with a kept one exceeds [iouThreshold]. Returns
     * a NEW list — input is not mutated.
     */
    private fun nms(input: List<DetectedFace>, iouThreshold: Float): List<DetectedFace> {
        if (input.isEmpty()) return emptyList()
        val sorted = input.sortedByDescending { it.confidence }.toMutableList()
        val keep = mutableListOf<DetectedFace>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            keep.add(best)
            sorted.removeAll { iou(best, it) > iouThreshold }
        }
        return keep
    }

    private fun iou(a: DetectedFace, b: DetectedFace): Float {
        val ix1 = max(a.left, b.left)
        val iy1 = max(a.top, b.top)
        val ix2 = min(a.right, b.right)
        val iy2 = min(a.bottom, b.bottom)
        val iw = max(0, ix2 - ix1)
        val ih = max(0, iy2 - iy1)
        val inter = (iw * ih).toFloat()
        if (inter <= 0f) return 0f
        val areaA = ((a.right - a.left) * (a.bottom - a.top)).toFloat()
        val areaB = ((b.right - b.left) * (b.bottom - b.top)).toFloat()
        val union = areaA + areaB - inter
        return if (union > 0f) inter / union else 0f
    }

    /**
     * Reverse letterbox: subtract pad offsets and divide by [scale] so the
     * box lands in source-image pixel space. Landmarks get the same
     * treatment in-place.
     */
    private fun unmapToSource(d: DetectedFace): DetectedFace {
        val lx = ((d.left - padLeft) / scale).toInt()
        val ly = ((d.top - padTop) / scale).toInt()
        val rx = ((d.right - padLeft) / scale).toInt()
        val ry = ((d.bottom - padTop) / scale).toInt()
        val lm = d.landmarks?.let { src ->
            FloatArray(10).also { dst ->
                for (j in 0 until 5) {
                    dst[2 * j] = (src[2 * j] - padLeft) / scale
                    dst[2 * j + 1] = (src[2 * j + 1] - padTop) / scale
                }
            }
        }
        return d.copy(left = lx, top = ly, right = rx, bottom = ry, landmarks = lm)
    }

}
