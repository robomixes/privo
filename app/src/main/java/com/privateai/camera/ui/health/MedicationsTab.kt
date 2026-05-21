// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.privateai.camera.R
import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.security.Medication
import com.privateai.camera.security.ScheduleKind
import com.privateai.camera.ui.insights.SELF_PROFILE_ID
import com.privateai.camera.ui.reminders.ReminderEditorState
import com.privateai.camera.ui.reminders.ReminderLinker
import com.privateai.camera.ui.reminders.RemindersEditor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MedicationsTab(
    repo: InsightsRepository,
    selectedProfileId: String = SELF_PROFILE_ID
) {
    val context = LocalContext.current
    var meds by remember(selectedProfileId) {
        mutableStateOf(repo.listMedicationsForProfile(selectedProfileId))
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Medication?>(null) }
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    if (showAddDialog) {
        MedicationDialog(
            initial = editing,
            repo = repo,
            onDismiss = { showAddDialog = false; editing = null },
            onSave = { med, reminderState ->
                val withProfile = med.copy(profileId = selectedProfileId)
                repo.saveMedication(withProfile)
                val newScheduleId = ReminderLinker.apply(
                    context = context,
                    repo = repo,
                    sourceId = withProfile.id,
                    kind = ScheduleKind.MEDICATION,
                    title = withProfile.name,
                    profileId = withProfile.profileId,
                    priorScheduleId = withProfile.scheduleId,
                    state = reminderState
                )
                if (newScheduleId != withProfile.scheduleId) {
                    repo.saveMedication(withProfile.copy(scheduleId = newScheduleId))
                }
                meds = repo.listMedicationsForProfile(selectedProfileId)
                showAddDialog = false
                editing = null
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (meds.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.LocalPharmacy,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.medications_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.medications_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(meds, key = { it.id }) { med ->
                    MedicationCard(
                        med = med,
                        dateFmt = dateFmt,
                        onEdit = { editing = med; showAddDialog = true },
                        onDelete = {
                            med.scheduleId?.let { sid ->
                                repo.loadScheduleItem(sid)?.let { item ->
                                    com.privateai.camera.service.ReminderScheduler.cancelItem(context, item.id, item.timesOfDay)
                                }
                                repo.deleteScheduleItem(sid)
                            }
                            repo.deleteMedication(med.id)
                            meds = repo.listMedicationsForProfile(selectedProfileId)
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        FloatingActionButton(
            onClick = { editing = null; showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, stringResource(R.string.medications_add))
        }
    }
}

@Composable
private fun MedicationCard(
    med: Medication,
    dateFmt: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.LocalPharmacy,
                contentDescription = null,
                modifier = Modifier.size(28.dp).padding(top = 2.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f)) {
                Text(med.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (med.dosage.isNotBlank()) {
                    Text(med.dosage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (med.instructions.isNotBlank()) {
                    Text(med.instructions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                val dateLine = if (med.endDate != null) {
                    "${dateFmt.format(Date(med.startDate))} → ${dateFmt.format(Date(med.endDate))}"
                } else {
                    "${stringResource(R.string.medications_since)} ${dateFmt.format(Date(med.startDate))}"
                }
                Text(dateLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (med.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(med.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, stringResource(R.string.edit), Modifier.size(16.dp).padding(0.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, stringResource(R.string.delete), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MedicationDialog(
    initial: Medication?,
    repo: InsightsRepository,
    onDismiss: () -> Unit,
    onSave: (Medication, ReminderEditorState) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var dosage by remember { mutableStateOf(initial?.dosage ?: "") }
    var instructions by remember { mutableStateOf(initial?.instructions ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var reminderState by remember(initial?.id) {
        mutableStateOf(ReminderLinker.loadInitialState(repo, initial?.scheduleId))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.medications_add) else stringResource(R.string.medications_edit)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.medications_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dosage, onValueChange = { dosage = it },
                    label = { Text(stringResource(R.string.medications_dosage)) },
                    placeholder = { Text("1 pill, 5ml, ...") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructions, onValueChange = { instructions = it },
                    label = { Text(stringResource(R.string.medications_instructions)) },
                    placeholder = { Text("With food, before bed, ...") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.label_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 4
                )

                Spacer(Modifier.height(8.dp))
                RemindersEditor(
                    state = reminderState,
                    onChange = { reminderState = it }
                )
            }
        },
        confirmButton = {
            val canSave = name.isNotBlank() && reminderState.isValid
            TextButton(
                onClick = {
                    if (!canSave) return@TextButton
                    val m = initial?.copy(
                        name = name.trim(), dosage = dosage.trim(),
                        instructions = instructions.trim(), notes = notes.trim()
                    ) ?: Medication(
                        name = name.trim(), dosage = dosage.trim(),
                        instructions = instructions.trim(), notes = notes.trim()
                    )
                    onSave(m, reminderState)
                },
                enabled = canSave
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
