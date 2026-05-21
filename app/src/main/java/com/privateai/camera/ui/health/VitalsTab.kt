// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.health

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.privateai.camera.R
import com.privateai.camera.security.HealthEntry
import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.security.MOOD_EMOJIS
import com.privateai.camera.ui.insights.ExportDialog
import com.privateai.camera.ui.insights.LineChart
import com.privateai.camera.ui.insights.SELF_PROFILE_ID
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun VitalsTab(
    repo: InsightsRepository,
    selectedProfileId: String = SELF_PROFILE_ID,
    onProfilesChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val profiles by remember { mutableStateOf(repo.loadProfiles()) }
    var allEntries by remember { mutableStateOf(repo.listHealthEntries()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    // Month navigation
    var selYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    val monthStart = remember(selYear, selMonth) { Calendar.getInstance().apply { set(selYear, selMonth, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }
    val monthEnd = remember(selYear, selMonth) { Calendar.getInstance().apply { set(selYear, selMonth + 1, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis - 1 }

    val entries = allEntries.filter { it.profileId == selectedProfileId && it.date in monthStart..monthEnd }

    // Trend data
    val weightData = entries.reversed().mapNotNull { e -> e.weight?.let { dateFormat.format(Date(e.date)).take(6) to it } }
    val hrData = entries.reversed().mapNotNull { e -> e.heartRate?.let { dateFormat.format(Date(e.date)).take(6) to it.toFloat() } }

    val myselfLabel = stringResource(R.string.health_myself)
    val unknownLabel = stringResource(R.string.health_unknown)
    val selectedProfileName = if (selectedProfileId == "self") myselfLabel else profiles.find { it.id == selectedProfileId }?.name ?: unknownLabel

    if (showAddDialog) {
        AddHealthDialog(onDismiss = { showAddDialog = false }, onSave = { entry ->
            repo.saveHealthEntry(entry.copy(profileId = selectedProfileId))
            allEntries = repo.listHealthEntries(); showAddDialog = false
        })
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentProfile = profiles.find { it.id == selectedProfileId }
                    Text(
                        if (currentProfile != null) "${currentProfile.icon} ${currentProfile.name}"
                        else "👤 ${stringResource(R.string.health_myself)}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Month navigator
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (selMonth == 0) { selMonth = 11; selYear-- } else selMonth-- }) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.action_previous)) }
                    Text("${monthNames[selMonth]} $selYear", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { if (selMonth == 11) { selMonth = 0; selYear++ } else selMonth++ }) { Icon(Icons.Default.ChevronRight, stringResource(R.string.action_next)) }
                }
            }

            // Summary + export
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(selectedProfileName, style = MaterialTheme.typography.titleSmall)
                            val latest = entries.firstOrNull()
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                latest?.weight?.let { Text("${"%.1f".format(it)}kg", style = MaterialTheme.typography.bodySmall) }
                                latest?.heartRate?.let { Text("${it}bpm", style = MaterialTheme.typography.bodySmall) }
                                latest?.systolic?.let { Text("${it}/${latest.diastolic}mmHg", style = MaterialTheme.typography.bodySmall) }
                            }
                            Text(stringResource(R.string.health_entries_this_month, entries.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.PictureAsPdf, stringResource(R.string.action_export_pdf), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Charts
            if (weightData.size >= 2) {
                item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.health_weight), style = MaterialTheme.typography.titleSmall)
                    LineChart(weightData, Color(0xFF4CAF50), Modifier.fillMaxWidth().height(100.dp))
                } } }
            }
            if (hrData.size >= 2) {
                item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.health_heart_rate), style = MaterialTheme.typography.titleSmall)
                    LineChart(hrData, Color(0xFFE91E63), Modifier.fillMaxWidth().height(100.dp))
                } } }
            }

            if (entries.isEmpty()) {
                item { Text(stringResource(R.string.health_no_entries), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }

            // Entry list
            items(entries) { entry ->
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val profileName = if (entry.profileId == "self") myselfLabel else profiles.find { it.id == entry.profileId }?.let { "${it.icon} ${it.name}" } ?: unknownLabel
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(dateFormat.format(Date(entry.date)), style = MaterialTheme.typography.bodyMedium)
                                Text(timeFormat.format(Date(entry.date)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Text("• $profileName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                entry.weight?.let { Text("${"%.1f".format(it)}kg", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50)) }
                                entry.sleepHours?.let { Text("${"%.1f".format(it)}hrs", style = MaterialTheme.typography.bodySmall, color = Color(0xFF42A5F5)) }
                                entry.heartRate?.let { Text("${it}bpm", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE91E63)) }
                                entry.temperature?.let { Text("${"%.1f".format(it)}°", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800)) }
                                entry.steps?.let { Text("${it}steps", style = MaterialTheme.typography.bodySmall) }
                                entry.systolic?.let { Text("${it}/${entry.diastolic}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9C27B0)) }
                                entry.mood?.let { if (it in 1..5) Text(MOOD_EMOJIS[it - 1], fontSize = 16.sp) }
                            }
                            entry.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        IconButton(onClick = { repo.deleteHealthEntry(entry.id); allEntries = repo.listHealthEntries() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, stringResource(R.string.action_delete), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        FloatingActionButton(onClick = { showAddDialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, stringResource(R.string.action_add)) }

        if (showExportDialog) {
            val (sub, hdr, rws) = remember(entries, selectedProfileName) { repo.generateHealthReportTable(entries, selectedProfileName) }
            ExportDialog(
                context = context,
                fileName = "health_report_${selectedProfileName}_${monthNames[selMonth]}_$selYear",
                onDismiss = { showExportDialog = false },
                title = "Health Report — $selectedProfileName",
                subtitle = sub,
                headers = hdr,
                rows = rws
            )
        }
    }
}

@Composable
private fun AddHealthDialog(onDismiss: () -> Unit, onSave: (HealthEntry) -> Unit) {
    var weight by remember { mutableStateOf("") }
    var sleep by remember { mutableFloatStateOf(7f) }
    val pain by remember { mutableIntStateOf(0) }
    var mood by remember { mutableIntStateOf(3) }
    var temp by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var hr by remember { mutableStateOf("") }
    var systolic by remember { mutableStateOf("") }
    var diastolic by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val context = LocalContext.current
    val cal = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(cal.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableIntStateOf(cal.get(Calendar.DAY_OF_MONTH)) }
    var selectedHour by remember { mutableIntStateOf(cal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(cal.get(Calendar.MINUTE)) }

    val dateDisplay = "%04d-%02d-%02d".format(selectedYear, selectedMonth + 1, selectedDay)
    val timeDisplay = "%02d:%02d".format(selectedHour, selectedMinute)

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.health_log_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        android.app.DatePickerDialog(context, { _, y, m, d -> selectedYear = y; selectedMonth = m; selectedDay = d }, selectedYear, selectedMonth, selectedDay).show()
                    }, modifier = Modifier.weight(1f)) { Text("📅 $dateDisplay") }
                    OutlinedButton(onClick = {
                        android.app.TimePickerDialog(context, { _, h, m -> selectedHour = h; selectedMinute = m }, selectedHour, selectedMinute, true).show()
                    }, modifier = Modifier.weight(1f)) { Text("🕐 $timeDisplay") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text(stringResource(R.string.health_label_weight)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = temp, onValueChange = { temp = it }, label = { Text(stringResource(R.string.health_label_temp)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = hr, onValueChange = { hr = it }, label = { Text(stringResource(R.string.health_label_heart_bpm)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = steps, onValueChange = { steps = it }, label = { Text(stringResource(R.string.health_label_steps)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = systolic, onValueChange = { systolic = it }, label = { Text(stringResource(R.string.health_label_bp_sys)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = diastolic, onValueChange = { diastolic = it }, label = { Text(stringResource(R.string.health_label_bp_dia)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                }
                Text(stringResource(R.string.health_label_sleep, "%.1f".format(sleep)))
                Slider(value = sleep, onValueChange = { sleep = it }, valueRange = 0f..14f)
                Text(stringResource(R.string.health_label_mood))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MOOD_EMOJIS.forEachIndexed { i, e -> Text(e, fontSize = if (mood == i + 1) 32.sp else 22.sp, modifier = Modifier.clickable { mood = i + 1 }.padding(4.dp)) }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.label_notes)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = {
            val entryDate = Calendar.getInstance().apply { set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute, 0) }.timeInMillis
            onSave(HealthEntry(
                date = entryDate,
                weight = weight.toFloatOrNull(), sleepHours = sleep, painLevel = pain.takeIf { it > 0 }, mood = mood,
                temperature = temp.toFloatOrNull(), steps = steps.toIntOrNull(), heartRate = hr.toIntOrNull(),
                systolic = systolic.toIntOrNull(), diastolic = diastolic.toIntOrNull(), notes = notes.ifBlank { null }
            ))
        }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
