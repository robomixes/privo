// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.privateai.camera.security.DuressManager
import com.privateai.camera.service.CrashHandler
import com.privateai.camera.ui.PrivateAICameraApp
import com.privateai.camera.ui.theme.PrivateAICameraTheme

class MainActivity : AppCompatActivity() {

    companion object {
        /** Set by widget intents; read once by NavHost to navigate on startup. */
        var pendingWidgetRoute: String? = null
            private set

        fun consumePendingRoute(): String? {
            val route = pendingWidgetRoute
            pendingWidgetRoute = null
            return route
        }

        /** URIs shared from other apps via ACTION_SEND / SEND_MULTIPLE. Consumed after auth. */
        var pendingShareUris: List<Uri>? = null
            private set
        /** Text shared from other apps via ACTION_SEND text/plain. Consumed after auth. */
        var pendingShareText: String? = null
            private set

        fun consumePendingShare(): Pair<List<Uri>?, String?> {
            val uris = pendingShareUris
            val text = pendingShareText
            pendingShareUris = null
            pendingShareText = null
            return uris to text
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore saved language preference
        val savedLang = getSharedPreferences("app_settings", MODE_PRIVATE).getString("language", "system")
        if (savedLang != null && savedLang != "system") {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLang))
        }

        // Restore saved theme preference (SYSTEM / LIGHT / DARK)
        com.privateai.camera.ui.theme.ThemePreference.load(this)

        // Block screenshots and screen recording (respects user setting)
        val blockScreenshots = getSharedPreferences("privacy_settings", MODE_PRIVATE).getBoolean("block_screenshots", true)
        if (blockScreenshots) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        enableEdgeToEdge()

        // Install crash handler (logs locally, never sends anywhere)
        CrashHandler.install(this)

        // One-time migration: move pre-2.0.2 model file from internal to
        // external app-private storage (DownloadManager can't write to internal).
        com.privateai.camera.bridge.GemmaRunner.migrateModelLocation(this)
        // Auto-clear sticky AI crash flags when the app has been upgraded —
        // a flag set on a broken old build shouldn't strand the user after the
        // bug is fixed. See clearStaleCrashFlagsOnUpgrade() docs.
        com.privateai.camera.bridge.GemmaRunner.clearStaleCrashFlagsOnUpgrade(this)
        // If a download was in flight when the app was last killed, resume polling.
        com.privateai.camera.bridge.GemmaModelManager.reconnect(this)

        // Create reminders notification channel + enqueue daily missed-sweep worker
        com.privateai.camera.service.ReminderReceiver.createNotificationChannel(this)
        com.privateai.camera.service.MissedSweepWorker.enqueue(this)

        // Request POST_NOTIFICATIONS (Android 13+) — best-effort, silent fail if denied
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        // Clean up any leftover files from interrupted duress wipe
        Thread { DuressManager.deleteMarkedFiles(this) }.start()

        handleWidgetIntent(intent)
        handleShareIntent(intent)

        setContent {
            PrivateAICameraTheme {
                PrivateAICameraApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        when (intent?.action) {
            "OPEN_CAMERA" -> pendingWidgetRoute = "camera"
            "OPEN_VAULT" -> pendingWidgetRoute = "vault"
            "com.privateai.camera.OPEN_NEW_NOTE" -> pendingWidgetRoute = "notes?openNoteId=__new__"
            "com.privateai.camera.OPEN_REMINDERS" -> pendingWidgetRoute = "reminders"
            "com.privateai.camera.OPEN_ASSISTANT" -> pendingWidgetRoute = "assistant"
        }
    }

    @Suppress("DEPRECATION")
    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val type = intent.type ?: return
                if (type == "text/plain") {
                    pendingShareText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    pendingWidgetRoute = "notes" // will create a new note with the text
                } else {
                    // image/*, video/*, application/pdf
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uri != null) {
                        pendingShareUris = listOf(uri)
                        pendingWidgetRoute = "vault" // will auto-import
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (!uris.isNullOrEmpty()) {
                    pendingShareUris = uris
                    pendingWidgetRoute = "vault"
                }
            }
        }
    }
}
