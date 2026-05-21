// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.privateai.camera.R
import com.privateai.camera.security.CycleEntry
import java.util.Calendar

/** Canonical symptom keys; UI labels via `string.cycle_symptom_<key>`. */
val CYCLE_SYMPTOMS = listOf("cramps", "headache", "fatigue", "mood_swings", "bloating")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AddCycleDialog(
    profileId: String,
    onDismiss: () -> Unit,
    onSave: (CycleEntry) -> Unit
) {
    val context = LocalContext.current
    val cal = remember { Calendar.getInstance() }
    var startYear by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var startMonth by remember { mutableIntStateOf(cal.get(Calendar.MONTH)) }
    var startDay by remember { mutableIntStateOf(cal.get(Calendar.DAY_OF_MONTH)) }

    var endSet by remember { mutableStateOf(false) }
    var endYear by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var endMonth by remember { mutableIntStateOf(cal.get(Calendar.MONTH)) }
    var endDay by remember { mutableIntStateOf(cal.get(Calendar.DAY_OF_MONTH)) }

    var flow by remember { mutableFloatStateOf(3f) }
    val symptoms = remember { mutableStateOf(setOf<String>()) }
    var notes by remember { mutableStateOf("") }

    fun startMs(): Long = Calendar.getInstance().apply {
        set(startYear, startMonth, startDay, 0, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    fun endMs(): Long? = if (endSet) Calendar.getInstance().apply {
        set(endYear, endMonth, endDay, 23, 59, 59); set(Calendar.MILLISECOND, 0)
    }.timeInMillis else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cycle_add_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Period start
                Text(stringResource(R.string.cycle_period_start), style = MaterialTheme.typography.labelMedium)
                OutlinedButton(
                    onClick = {
                        android.app.DatePickerDialog(
                            context,
                            { _, y, m, d -> startYear = y; startMonth = m; startDay = d },
                            startYear, startMonth, startDay
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("📅 %04d-%02d-%02d".format(startYear, startMonth + 1, startDay)) }

                // Period end (optional)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.cycle_period_end), style = MaterialTheme.typography.labelMedium)
                    androidx.compose.material3.Switch(
                        checked = endSet,
                        onCheckedChange = { endSet = it }
                    )
                }
                if (endSet) {
                    OutlinedButton(
                        onClick = {
                            android.app.DatePickerDialog(
                                context,
                                { _, y, m, d -> endYear = y; endMonth = m; endDay = d },
                                endYear, endMonth, endDay
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("📅 %04d-%02d-%02d".format(endYear, endMonth + 1, endDay)) }
                }

                // Flow 1-5
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.cycle_flow_label, flow.toInt()),
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = flow,
                    onValueChange = { flow = it },
                    valueRange = 1f..5f,
                    steps = 3
                )

                // Symptoms — fixed canonical set
                Text(stringResource(R.string.cycle_symptoms_label), style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CYCLE_SYMPTOMS.forEach { key ->
                        val selected = key in symptoms.value
                        FilterChip(
                            selected = selected,
                            onClick = {
                                symptoms.value = if (selected) symptoms.value - key
                                else symptoms.value + key
                            },
                            label = { Text(symptomLabel(context, key)) }
                        )
                    }
                }

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.label_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1, maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    CycleEntry(
                        profileId = profileId,
                        periodStart = startMs(),
                        periodEnd = endMs(),
                        flow = flow.toInt(),
                        symptoms = symptoms.value.toList(),
                        notes = notes.ifBlank { null }
                    )
                )
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private fun symptomLabel(context: android.content.Context, key: String): String {
    val resId = when (key) {
        "cramps" -> R.string.cycle_symptom_cramps
        "headache" -> R.string.cycle_symptom_headache
        "fatigue" -> R.string.cycle_symptom_fatigue
        "mood_swings" -> R.string.cycle_symptom_mood_swings
        "bloating" -> R.string.cycle_symptom_bloating
        else -> return key
    }
    return context.getString(resId)
}
