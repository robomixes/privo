// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.health

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.privateai.camera.R

/**
 * One-time non-medical disclaimer shown the first time a user opens the cycle
 * tab. Dismissed-once via the SharedPrefs flag `cycle_disclaimer_shown` in
 * `privateai_prefs`; subsequent opens never show it.
 *
 * Why a separate dialog (not folded into the wizard's medical info page):
 * the wizard runs once at first install, but the cycle tab can be reached by
 * users who skip the wizard — the disclaimer must be enforceable at the point
 * of first cycle access.
 */
private const val PREFS_NAME = "privateai_prefs"
private const val KEY_DISCLAIMER_SHOWN = "cycle_disclaimer_shown"

fun isCycleDisclaimerShown(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_DISCLAIMER_SHOWN, false)

fun markCycleDisclaimerShown(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_DISCLAIMER_SHOWN, true)
        .apply()
}

@Composable
fun CycleDisclaimerDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cycle_disclaimer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.cycle_disclaimer_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.cycle_disclaimer_caveat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cycle_disclaimer_ok))
            }
        }
    )
}
