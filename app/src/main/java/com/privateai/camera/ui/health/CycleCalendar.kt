// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privateai.camera.security.CycleEntry
import com.privateai.camera.security.CyclePrediction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Monthly cycle calendar — renders the calendar of `(year, month)` exactly,
 * day-1 to day-N of that month. Period days are filled; predicted starts are
 * ringed; today's cell gets a thin ring overlay (only when the displayed month
 * is the current month).
 *
 * Predictions cover **multiple cycles** projected forward from the most recent
 * logged period using `prediction.cycleLengthDays`. That makes browsing 2-3
 * months ahead useful — the user sees the rolling estimate, clearly marked
 * as estimate (ringed, not filled).
 *
 * Implementation note: pure Compose `Canvas` + TextMeasurer. The project
 * already follows this pattern in [SimpleChart.kt] for line/bar/pie charts.
 */
@Composable
fun CycleCalendar(
    entries: List<CycleEntry>,
    prediction: CyclePrediction?,
    year: Int,
    month: Int,           // 0-based (Calendar.MONTH convention)
    accent: Color = Color(0xFFE91E63),
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val today = remember { todayMidnight() }
    val dayMs = 24L * 60 * 60 * 1000L

    // Build the list of day-millis for every day in the displayed month.
    val monthCal = Calendar.getInstance().apply {
        set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
    }
    val firstDayOfMonth = monthCal.timeInMillis
    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val days = (0 until daysInMonth).map { i -> firstDayOfMonth + i * dayMs }
    val firstDow = monthCal.get(Calendar.DAY_OF_WEEK) - 1   // Sunday = 0

    val periodSet = remember(entries) {
        val set = mutableSetOf<Long>()
        entries.forEach { e ->
            val end = e.periodEnd ?: (e.periodStart + 4 * dayMs) // open-ended: assume 5 days
            var d = dayMidnight(e.periodStart)
            val last = dayMidnight(end)
            while (d <= last) { set.add(d); d += dayMs }
        }
        set
    }

    // Project predicted starts forward — the UI shows estimates for the next
    // 12 cycles so navigating 6 months ahead still has markers. Each is a
    // simple multiple of `cycleLengthDays` from the most recent real start.
    val predictedSet = remember(prediction, entries) {
        val set = mutableSetOf<Long>()
        val first = prediction?.nextStart ?: return@remember set
        val cycleLen = prediction.cycleLengthDays.coerceAtLeast(14)
        for (k in 0 until 12) {
            set.add(dayMidnight(first + k.toLong() * cycleLen * dayMs))
        }
        set
    }

    val cellPx = with(density) { 36.dp.toPx() }
    val gapPx = with(density) { 4.dp.toPx() }
    val cornerPx = with(density) { 8.dp.toPx() }
    val cols = 7
    // Variable row count — depends on which day of the week the 1st falls on
    // and how many days are in the month. Range: 4 (Feb starting on a Sunday
    // in a non-leap year) up to 6 (31-day months that start late in the week).
    val rows = ((firstDow + daysInMonth + cols - 1) / cols).coerceAtLeast(4)

    // Today's ring should only paint when the displayed month is "right now".
    val todayCal = Calendar.getInstance().apply { timeInMillis = today }
    val showToday = todayCal.get(Calendar.YEAR) == year && todayCal.get(Calendar.MONTH) == month

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val emptyBg = onSurfaceVariant.copy(alpha = 0.08f)
    val todayRing = MaterialTheme.colorScheme.primary
    val canvasHeight = (rows * cellPx + (rows - 1) * gapPx)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium)

    Column(modifier = modifier) {
        // Day-of-week header row (Sun..Sat for now; localization deferred to v2)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val dows = listOf("S", "M", "T", "W", "T", "F", "S")
            dows.forEach { d ->
                Text(
                    d,
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.size(36.dp).padding(top = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { canvasHeight.toDp() })
        ) {
            val totalWidth = size.width
            val effectiveCellWidth = (totalWidth - (cols - 1) * gapPx) / cols

            days.forEachIndexed { i, dayMs ->
                // Day 1 of the month sits at column `firstDow`; subsequent days
                // wrap into a 7-column grid. firstDow is 0..6 (Sunday..Saturday).
                val cellIndex = firstDow + i
                val col = cellIndex % cols
                val row = cellIndex / cols

                val x = col * (effectiveCellWidth + gapPx)
                val y = row * (cellPx + gapPx)

                val isPeriod = periodSet.contains(dayMs)
                val isToday = showToday && dayMs == today
                val isPredicted = predictedSet.contains(dayMs)

                val bg = if (isPeriod) accent else emptyBg
                drawRoundedFilledRect(Offset(x, y), Size(effectiveCellWidth, cellPx), cornerPx, bg)

                // Predicted ring — drawn over empty cells (not on period fills,
                // since real data trumps an estimate visually).
                if (isPredicted && !isPeriod) {
                    drawRoundedStrokeRect(
                        Offset(x, y), Size(effectiveCellWidth, cellPx),
                        cornerPx, accent, strokeWidth = with(density) { 2.dp.toPx() }
                    )
                }

                // Today marker — thin inner ring, only when this is the
                // current calendar month (suppressed while browsing history
                // or the future).
                if (isToday) {
                    drawRoundedStrokeRect(
                        Offset(x + 2f, y + 2f),
                        Size(effectiveCellWidth - 4f, cellPx - 4f),
                        cornerPx, todayRing, strokeWidth = with(density) { 2.dp.toPx() }
                    )
                }

                // Day-of-month label (1..N), centered. White on period fill
                // (high contrast); default on empty/predicted cells.
                val dayOfMonth = i + 1
                val labelColor = if (isPeriod) Color.White else onSurface
                val layout = textMeasurer.measure(
                    text = dayOfMonth.toString(),
                    style = labelStyle.copy(color = labelColor)
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x + (effectiveCellWidth - layout.size.width) / 2f,
                        y + (cellPx - layout.size.height) / 2f
                    )
                )
            }
        }
    }
}

// ── small Canvas helpers — kept private to avoid leaking into other charts ──

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundedFilledRect(
    topLeft: Offset, size: Size, corner: Float, color: Color
) {
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundedStrokeRect(
    topLeft: Offset, size: Size, corner: Float, color: Color, strokeWidth: Float
) {
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
        style = Stroke(width = max(strokeWidth, 1f))
    )
}

// ── time helpers ──

private fun todayMidnight(): Long = dayMidnight(System.currentTimeMillis())

private fun dayMidnight(ms: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}
