// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class FaceEmbedder(context: Context) {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    private val inputSize = 112  // ArcFace MobileFaceNet expects 112x112

    // ONNX face detector (YuNet) — replaces the ML Kit FaceDetection client
    // used in v2.0.x as part of Track A1.2 (ML Kit removal). YuNet returns
    // 5 landmarks alongside each box; we still only consume bounding boxes
    // here (alignment via landmarks remains TODO — see [alignFace] below).
    private val detector = FaceDetectorHolder.get(context)

    companion object {
        private const val TAG = "FaceEmbedder"
        private const val EMBEDDING_DIM = 512  // ArcFace MobileFaceNet outputs 512-dim
    }

    private var inputName = "input"

    init {
        try {
            val modelBytes = context.assets.open("models/face_embed.onnx").readBytes()
            ortSession = ortEnv.createSession(modelBytes)
            // Get actual input name from model (ArcFace uses "input.1")
            ortSession?.inputInfo?.keys?.firstOrNull()?.let { inputName = it }
            Log.i(TAG, "ArcFace model loaded (input=$inputName, ${inputSize}x${inputSize})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load face embedding model: ${e.message}")
        }
    }

    /**
     * Detect faces, align each face using eye landmarks, then compute embeddings.
     * Alignment normalizes pose so same person at different angles produces similar embeddings.
     */
    fun detectAndEmbed(bitmap: Bitmap): List<Pair<RectF, FloatArray>> {
        val session = ortSession ?: return emptyList()
        if (bitmap.isRecycled) return emptyList()

        try {
            val faces = detector.detect(bitmap)
            if (faces.isEmpty()) return emptyList()

            val bw = bitmap.width
            val bh = bitmap.height
            val results = mutableListOf<Pair<RectF, FloatArray>>()

            for (face in faces) {
                val left = face.left.coerceIn(0, bw - 1)
                val top = face.top.coerceIn(0, bh - 1)
                val right = face.right.coerceIn(left + 1, bw)
                val bottom = face.bottom.coerceIn(top + 1, bh)
                val width = right - left
                val height = bottom - top
                if (width <= 0 || height <= 0) continue

                // Try to align face using eye landmarks
                // Expand crop by 20% for better context (forehead, chin)
                val pad = (width * 0.2f).toInt()
                val cl = (left - pad).coerceAtLeast(0)
                val ct = (top - pad).coerceAtLeast(0)
                val cr = (left + width + pad).coerceAtMost(bw)
                val cb = (top + height + pad).coerceAtMost(bh)
                val faceBitmap = Bitmap.createBitmap(bitmap, cl, ct, cr - cl, cb - ct)

                val embedding = embed(faceBitmap)
                if (faceBitmap !== bitmap) faceBitmap.recycle()

                val normalizedBox = RectF(
                    left.toFloat() / bw, top.toFloat() / bh,
                    right.toFloat() / bw, bottom.toFloat() / bh
                )
                results.add(Pair(normalizedBox, embedding))
            }

            return results
        } catch (e: Exception) {
            Log.e(TAG, "Face detection and embedding failed", e)
            return emptyList()
        }
    }

    /**
     * Align a face so eyes are horizontal and centered.
     * This dramatically improves embedding consistency across angles.
     */
    /**
     * Get embedding for a single pre-cropped face bitmap.
     */
    fun embed(faceBitmap: Bitmap): FloatArray {
        val session = ortSession ?: return FloatArray(0)
        if (faceBitmap.isRecycled) return FloatArray(0)

        try {
            val resized = Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)
            val inputBuffer = preprocessFace(resized)
            if (resized !== faceBitmap) resized.recycle()

            val inputTensor = OnnxTensor.createTensor(
                ortEnv, inputBuffer,
                longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
            )
            val results = session.run(mapOf(inputName to inputTensor))
            inputTensor.close()

            val outputTensor = results[0] as? OnnxTensor
            if (outputTensor == null) { results.close(); return FloatArray(0) }

            val embedding = (outputTensor.value as Array<FloatArray>)[0]
            results.close()

            // L2 normalize
            var norm = 0f
            for (v in embedding) norm += v * v
            norm = Math.sqrt(norm.toDouble()).toFloat()
            return if (norm > 0) FloatArray(embedding.size) { embedding[it] / norm } else embedding.copyOf()
        } catch (e: Exception) {
            Log.e(TAG, "Face embedding failed", e)
            return FloatArray(0)
        }
    }

    private fun preprocessFace(bitmap: Bitmap): FloatBuffer {
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        val channelSize = inputSize * inputSize
        val totalSize = 3 * channelSize

        val byteBuffer = ByteBuffer.allocateDirect(totalSize * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        val buffer = byteBuffer.asFloatBuffer()

        // FaceNet normalization: pixel value / 127.5 - 1.0 (range: -1 to 1)
        // NCHW layout
        for (i in 0 until channelSize) {
            val r = ((pixels[i] shr 16) and 0xFF) / 127.5f - 1.0f
            buffer.put(r)
        }
        for (i in 0 until channelSize) {
            val g = ((pixels[i] shr 8) and 0xFF) / 127.5f - 1.0f
            buffer.put(g)
        }
        for (i in 0 until channelSize) {
            val b = (pixels[i] and 0xFF) / 127.5f - 1.0f
            buffer.put(b)
        }

        buffer.rewind()
        return buffer
    }

    fun release() {
        ortSession?.close()
        ortSession = null
        // Do NOT release the FaceDetector — it's a process-wide singleton
        // shared with FaceBlur, CaptureScreen's live preview, and the
        // contacts photo-crop path. Closing it here would break those.
    }
}
