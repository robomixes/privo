// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.calibrate

import android.content.Context

/**
 * First-run gate for the calibration wizard. Mirrors the onboarding pattern
 * (`isOnboardingComplete` / `completeOnboardingQuick` in OnboardingScreen.kt).
 * The wizard runs once after onboarding+optional-import; users can re-run it
 * from Settings any time.
 */
private const val PREFS_NAME = "privateai_prefs"
private const val KEY_WIZARD_DONE = "wizard_completed"

fun isWizardComplete(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_WIZARD_DONE, false)

fun markWizardComplete(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_WIZARD_DONE, true)
        .apply()
}

fun resetWizard(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .remove(KEY_WIZARD_DONE)
        .apply()
}
