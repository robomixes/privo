// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.settings

import android.content.Context

private const val PREFS_NAME = "feature_toggles"
private const val KEY_ORDER = "feature_order"
private const val KEY_LAYOUT = "home_layout"

enum class HomeLayout { GRID, TABS }

/**
 * Manages which features are visible on the home screen and their order.
 */
object FeatureToggleManager {

    private val DEFAULT_ORDER = listOf("camera", "detect", "scan", "qrscanner", "translate", "vault", "notes", "insights", "health", "reminders", "passwords", "totp", "tools", "contacts")

    fun isFeatureEnabled(context: Context, route: String): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(route, true)
    }

    fun setFeatureEnabled(context: Context, route: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(route, enabled)
            .apply()
    }

    fun getEnabledFeatures(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return DEFAULT_ORDER.filter { prefs.getBoolean(it, true) }.toSet()
    }

    /**
     * Get ordered list of all features (enabled and disabled).
     */
    fun getOrderedFeatures(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_ORDER, null)
        if (stored != null) {
            val order = stored.split(",").filter { it.isNotBlank() }
            // Add any new features not in stored order
            val missing = DEFAULT_ORDER.filter { it !in order }
            return order + missing
        }
        return DEFAULT_ORDER
    }

    /**
     * Save feature order.
     */
    fun saveOrder(context: Context, order: List<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORDER, order.joinToString(","))
            .apply()
    }

    /**
     * Get ordered list of enabled features (for home screen).
     */
    fun getOrderedEnabledFeatures(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return getOrderedFeatures(context).filter { prefs.getBoolean(it, true) }
    }

    /** Get home screen layout preference (Grid or Tabs). Default: GRID. */
    fun getHomeLayout(context: Context): HomeLayout {
        val v = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAYOUT, "grid")
        return if (v == "tabs") HomeLayout.TABS else HomeLayout.GRID
    }

    /** Set home screen layout preference. */
    fun setHomeLayout(context: Context, layout: HomeLayout) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAYOUT, layout.name.lowercase())
            .apply()
    }
}
