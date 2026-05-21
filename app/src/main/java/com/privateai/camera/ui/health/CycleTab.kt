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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.privateai.camera.R
import com.privateai.camera.security.CycleEntry
import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.ui.insights.SELF_PROFILE_ID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cycle / period tracker — third tab in the new Health feature.
 *
 * Layout: header card (current state + prediction), 30-day calendar, action
 * row, history list. First open shows the non-medical disclaimer.
 *
 * Predictions are intentionally simple — last-3-cycles average, defaults to
 * 28d/5d when there's not enough history. The UI labels every prediction as
 * "estimate" and never references ovulation, fertility, or safe days.
 */
@Composable
fun CycleTab(
    repo: InsightsRepository,
    selectedProfileId: String = SELF_PROFILE_ID
) {
    val context = LocalContext.current
    val accent = Color(0xFFE91E63)
    var allEntries by remember { mutableStateOf(repo.listCycleEntries()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDisclaimer by remember { mutableStateOf(!isCycleDisclaimerShown(context)) }

    // Calendar month navigation — defaults to the current month and lets the
    // user browse historical periods + projected future estimates.
    val nowCal = remember { java.util.Calendar.getInstance() }
    var selYear by remember { mutableIntStateOf(nowCal.get(java.util.Calendar.YEAR)) }
    var selMonth by remember { mutableIntStateOf(nowCal.get(java.util.Calendar.MONTH)) }
    val isCurrentMonth = remember(selYear, selMonth) {
        val t = java.util.Calendar.getInstance()
        t.get(java.util.Calendar.YEAR) == selYear && t.get(java.util.Calendar.MONTH) == selMonth
    }
    val monthNames = remember { listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec") }

    // First-open disclaimer is profile-agnostic (it's about the app, not the
    // tracked person), so we only show it once per device install.
    LaunchedEffect(Unit) {
        if (!isCycleDisclaimerShown(context)) {
            showDisclaimer = true
        }
    }

    val entries = allEntries.filter { it.profileId == selectedProfileId }
    val prediction = remember(entries) { repo.predictNextPeriod(entries) }

    if (showDisclaimer) {
        CycleDisclaimerDialog(onDismiss = {
            markCycleDisclaimerShown(context)
            showDisclaimer = false
        })
    }

    if (showAddDialog) {
        AddCycleDialog(
            profileId = selectedProfileId,
            onDismiss = { showAddDialog = false },
            onSave = { entry ->
                repo.saveCycleEntry(entry)
                allEntries = repo.listCycleEntries()
                showAddDialog = false
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header card — current state + prediction
            item { CycleHeaderCard(entries, prediction, accent) }

            // Calendar — wrapped in a Card with a chevron month navigator
            // above so users can scroll back through history and forward into
            // projected estimates.
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        // Month navigator
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                if (selMonth == 0) { selMonth = 11; selYear-- } else selMonth--
                            }) {
                                Icon(
                                    androidx.compose.material.icons.Icons.Default.ChevronLeft,
                                    contentDescription = stringResource(R.string.action_previous)
                                )
                            }
                            Text(
                                "${monthNames[selMonth]} $selYear",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isCurrentMonth) {
                                    androidx.compose.material3.TextButton(onClick = {
                                        val now = java.util.Calendar.getInstance()
                                        selYear = now.get(java.util.Calendar.YEAR)
                                        selMonth = now.get(java.util.Calendar.MONTH)
                                    }) {
                                        Text(stringResource(R.string.cycle_today_button))
                                    }
                                }
                                IconButton(onClick = {
                                    if (selMonth == 11) { selMonth = 0; selYear++ } else selMonth++
                                }) {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Default.ChevronRight,
                                        contentDescription = stringResource(R.string.action_next)
                                    )
                                }
                            }
                        }

                        CycleCalendar(
                            entries = entries,
                            prediction = prediction,
                            year = selYear,
                            month = selMonth,
                            accent = accent
                        )
                        Spacer(Modifier.height(8.dp))
                        // Legend
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LegendDot(accent, stringResource(R.string.cycle_legend_period))
                            LegendDot(accent, stringResource(R.string.cycle_legend_predicted), filled = false)
                        }
                    }
                }
            }

            // History
            if (entries.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.cycle_history_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(entries, key = { it.id }) { entry ->
                    CycleHistoryRow(
                        entry = entry,
                        accent = accent,
                        onDelete = {
                            repo.deleteCycleEntry(entry.id)
                            allEntries = repo.listCycleEntries()
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = accent
        ) {
            Icon(Icons.Default.Add, stringResource(R.string.cycle_add_action), tint = Color.White)
        }
    }
}

@Composable
private fun CycleHeaderCard(
    entries: List<CycleEntry>,
    prediction: com.privateai.camera.security.CyclePrediction,
    accent: Color
) {
    val today = System.currentTimeMillis()
    val dayMs = 24L * 60 * 60 * 1000L
    val mostRecent = entries.maxByOrNull { it.periodStart }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.12f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            if (mostRecent == null) {
                Text(
                    stringResource(R.string.cycle_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.cycle_empty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val daysSinceStart = ((today - mostRecent.periodStart) / dayMs).toInt() + 1
                Text(
                    stringResource(R.string.cycle_header_day, daysSinceStart),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))

                val nextStart = prediction.nextStart
                if (nextStart != null) {
                    val fmt = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
                    val daysUntil = ((nextStart - today) / dayMs).toInt()
                    val nextLine = if (daysUntil > 0)
                        stringResource(R.string.cycle_header_next_in, fmt.format(Date(nextStart)), daysUntil)
                    else if (daysUntil == 0)
                        stringResource(R.string.cycle_header_next_today)
                    else
                        stringResource(R.string.cycle_header_next_overdue, -daysUntil)
                    Text(
                        nextLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(
                            R.string.cycle_header_avg,
                            prediction.cycleLengthDays,
                            prediction.periodLengthDays
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (prediction.confidence < 2) {
                        Text(
                            stringResource(R.string.cycle_header_low_confidence),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CycleHistoryRow(
    entry: CycleEntry,
    accent: Color,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val fmt = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(10.dp).padding(0.dp)
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(10.dp)) {
                    drawCircle(accent)
                }
            }
            Column(Modifier.weight(1f)) {
                val rangeText = if (entry.periodEnd != null) {
                    val days = ((entry.periodEnd - entry.periodStart) / (24L * 60 * 60 * 1000)).toInt() + 1
                    "${fmt.format(Date(entry.periodStart))} – ${fmt.format(Date(entry.periodEnd))} • ${days}d"
                } else {
                    fmt.format(Date(entry.periodStart))
                }
                Text(
                    rangeText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (entry.flow != null || entry.symptoms.isNotEmpty()) {
                    val parts = buildList {
                        entry.flow?.let { add(context.getString(R.string.cycle_flow_short, it)) }
                        if (entry.symptoms.isNotEmpty()) {
                            add(entry.symptoms.joinToString(" · ") { symptomLabelInternal(context, it) })
                        }
                    }
                    Text(
                        parts.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                entry.notes?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, stringResource(R.string.action_delete), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun LegendDot(accent: Color, label: String, filled: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(12.dp)) {
            if (filled) drawCircle(accent)
            else drawCircle(accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun symptomLabelInternal(context: android.content.Context, key: String): String {
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
