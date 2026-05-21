// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

import android.content.Context

/**
 * Process-wide singleton for [FaceDetector]. Lazy-initialised on first call.
 *
 * The ONNX session for the YuNet detector takes ~30-50 ms to allocate the
 * 12 output tensors and warm up on a Pixel-class CPU; doing that per
 * `FaceBlur.blurFaces` call (or worse, per camera-preview frame) would be
 * a real waste. Holding one [FaceDetector] for the lifetime of the app
 * process keeps cost amortised. Thread-safe: `synchronized` guards the
 * first-init; subsequent calls hit the cached instance unguarded.
 */
object FaceDetectorHolder {
    @Volatile private var instance: FaceDetector? = null

    fun get(context: Context): FaceDetector {
        val existing = instance
        if (existing != null) return existing
        return synchronized(this) {
            instance ?: FaceDetector(context.applicationContext).also { instance = it }
        }
    }
}
