// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Single source of truth for the AI (Gemma) feature state.
 *
 * Privora's on-device AI is fully optional. Every AI-conditional UI in the
 * app reads this status and hides itself when not [READY] — no grey-disabled
 * buttons that mislead the user into thinking AI is "almost there". The rule
 * is binary: AI is either ready to use, or it isn't visible at all (the one
 * exception is the Settings → AI section itself, which always shows the
 * enable affordance).
 *
 * Composed from three signals:
 *  - `GemmaRunner.isEnabled(context)` — user toggle in Settings
 *  - `GemmaRunner.isModelDownloaded(context)` — the ~2.7 GB model file exists
 *  - `GemmaModelManager.downloadState` — Idle / Downloading / Complete / Error
 *  - `GemmaRunner.loadFailed` (via `isAvailable`) — engine crashed on load
 */
enum class AiStatus {
    /** User hasn't enabled AI, or has explicitly disabled it. */
    OFF,
    /** User has enabled AI; the model file is being downloaded. */
    DOWNLOADING,
    /** Model present + enabled + engine healthy. Every AI surface should
     *  show its AI controls only in this state. */
    READY,
    /** Engine load or vision call crashed; user needs to intervene in
     *  Settings (re-enable, or delete + redownload the model). */
    FAILED;

    /** Convenience for the only check the rest of the app cares about: is
     *  it safe to *render* AI controls right now? */
    val isReady: Boolean get() = this == READY
}

/**
 * Observe the current [AiStatus] from a Compose UI. Re-evaluates when the
 * lifecycle moves to RESUMED so changes the user made in Settings (toggling
 * AI on/off, deleting the model, completing a download) are reflected when
 * they navigate back to the calling screen.
 *
 * Cheap to call from any composable: the model-existence check is a single
 * `File.exists()`; the enabled check is a SharedPreferences read; the
 * download state is already a hot StateFlow shared across the app.
 */
@Composable
fun rememberAiStatus(): State<AiStatus> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val downloadState by GemmaModelManager.downloadState.collectAsState()
    // Three triggers force a recompute: lifecycle resume (catches changes
    // made on another screen), the download-state flow (catches the model
    // becoming present), and the ai_enabled SharedPref listener (catches
    // toggles made *within* the current screen — without this, flipping
    // the Settings AI switch wouldn't hide the sub-toggles below it until
    // the user left and re-entered Settings).
    val tick = remember { mutableStateOf(0) }

    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick.value++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("gemma_settings", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "ai_enabled") tick.value++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return remember(downloadState) {
        derivedStateOf {
            @Suppress("UNUSED_EXPRESSION") tick.value
            computeAiStatus(context, downloadState)
        }
    }
}

private fun computeAiStatus(
    context: Context,
    downloadState: GemmaModelManager.DownloadState
): AiStatus {
    // An active download takes precedence over the file check — DownloadManager
    // doesn't always atomically rename, so during the final flush the file may
    // appear present but the engine is still seconds away from being loadable.
    if (downloadState is GemmaModelManager.DownloadState.Downloading) return AiStatus.DOWNLOADING
    if (!GemmaRunner.isEnabled(context)) return AiStatus.OFF
    if (!GemmaRunner.isModelDownloaded(context)) {
        // Enabled but no file → either user just toggled on (idle) or a prior
        // download errored. Either way, surface DOWNLOADING isn't honest;
        // treat as OFF so the UI prompts the user to start the download.
        return AiStatus.OFF
    }
    // Same predicate `isAvailable` used to check, only now we route it
    // through the enum so callers stop branching on three different flags.
    return if (GemmaRunner.isAvailable(context)) AiStatus.READY else AiStatus.FAILED
}
