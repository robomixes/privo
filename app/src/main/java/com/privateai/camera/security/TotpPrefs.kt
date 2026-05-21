// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.security

import android.content.Context

/**
 * SharedPreferences for the TOTP (Authenticator) feature.
 *
 * Key set is intentionally tiny. The pref store is registered in
 * [BackupManager.prefsToBackup] so these toggles are exported alongside the
 * encrypted entries.
 */
object TotpPrefs {

    private const val PREFS_NAME = "totp_settings"
    private const val KEY_HIDE_UNTIL_TAP = "hide_until_tap"
    private const val KEY_AUTOLOCK_SECONDS = "autolock_seconds"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Render codes as ••• ••• until the user taps the entry. Default: false. */
    fun hideUntilTap(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_UNTIL_TAP, false)

    fun setHideUntilTap(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDE_UNTIL_TAP, value).apply()
    }

    /** Auto-lock the Authenticator screen after this many seconds idle. Default 60s. */
    fun autolockSeconds(context: Context): Int =
        prefs(context).getInt(KEY_AUTOLOCK_SECONDS, 60)

    fun setAutolockSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_AUTOLOCK_SECONDS, seconds).apply()
    }
}
