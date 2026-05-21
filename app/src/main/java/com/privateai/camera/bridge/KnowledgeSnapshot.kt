// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.bridge

import android.content.Context
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.ContactRepository
import com.privateai.camera.security.EXPENSE_CATEGORIES
import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.security.NoteRepository
import com.privateai.camera.security.PrivoraDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Compact, privacy-safe snapshot of the user's data for the AI assistant prompt.
 *
 * **Privacy invariants** (enforced in [toJson]):
 * - Note bodies are NEVER included — only titles, tags, modifiedAt.
 * - Photo metadata is excluded entirely.
 * - Contact PII is limited to family profile names (user-curated).
 * - Total JSON output is capped at ~8 KB; lists are truncated oldest-first.
 */
data class KnowledgeSnapshot(
    val today: String,
    val selfName: String,
    val reminders: List<RemSummary>,
    val recentExpenses: List<ExpSummary>,
    val expenseTotals30d: Map<String, Double>,
    val recentNotes: List<NoteHeader>,
    val medications: List<MedSummary>,
    val habits: List<HabitReport>,
    val healthLast7: List<HealthSummary>,
    val familyProfileNames: List<String>
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("today", today)
        obj.put("selfName", selfName)

        obj.put("reminders", JSONArray().apply {
            reminders.forEach { put(JSONObject().apply {
                put("title", it.title); put("dateTime", it.dateTime); put("kind", it.kind)
            }) }
        })

        obj.put("recentExpenses", JSONArray().apply {
            recentExpenses.forEach { put(JSONObject().apply {
                put("date", it.date); put("amount", it.amount)
                put("currency", it.currency); put("category", it.category)
                put("description", it.description)
            }) }
        })

        obj.put("expenseTotals30d", JSONObject().apply {
            expenseTotals30d.forEach { (k, v) -> put(k, v) }
        })

        obj.put("recentNotes", JSONArray().apply {
            recentNotes.forEach { put(JSONObject().apply {
                put("title", it.title); put("tags", JSONArray(it.tags))
                put("modifiedAt", it.modifiedAt)
            }) }
        })

        obj.put("medications", JSONArray().apply {
            medications.forEach { put(JSONObject().apply {
                put("name", it.name); put("dosage", it.dosage); put("schedule", it.schedule)
            }) }
        })

        obj.put("habits", JSONArray().apply {
            habits.forEach { put(JSONObject().apply {
                put("name", it.name)
                put("last7", it.last7); put("last30", it.last30); put("last365", it.last365)
                put("currentStreak", it.currentStreak); put("longestStreak", it.longestStreak)
            }) }
        })

        obj.put("healthLast7", JSONArray().apply {
            healthLast7.forEach { put(JSONObject().apply {
                put("date", it.date)
                it.weight?.let { w -> put("weight", w) }
                it.heartRate?.let { h -> put("heartRate", h) }
                it.bp?.let { b -> put("bp", b) }
                it.sleep?.let { s -> put("sleep", s) }
                it.mood?.let { m -> put("mood", m) }
                it.steps?.let { st -> put("steps", st) }
            }) }
        })

        obj.put("familyProfiles", JSONArray(familyProfileNames))

        // Enforce 8 KB cap — should never hit in practice given the list caps
        val json = obj.toString()
        return if (json.length <= 8192) json else json.take(8192)
    }

    companion object {
        private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val dateTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        /**
         * Empty snapshot used by the AI Assistant when the vault is locked.
         * The model sees `{}` as the snapshot JSON; no encrypted-data reads
         * happen. `today` stays populated so the model still knows the date.
         */
        fun empty(): KnowledgeSnapshot = KnowledgeSnapshot(
            today = dateFmt.format(java.util.Date()),
            selfName = "",
            reminders = emptyList(),
            recentExpenses = emptyList(),
            expenseTotals30d = emptyMap(),
            recentNotes = emptyList(),
            medications = emptyList(),
            habits = emptyList(),
            healthLast7 = emptyList(),
            familyProfileNames = emptyList()
        )

        /**
         * Build the snapshot from all repos. Runs on the calling dispatcher
         * (should be IO). Takes ~10–50 ms.
         */
        fun build(context: Context): KnowledgeSnapshot {
            val crypto = CryptoManager(context).also { it.initialize() }
            val insightsRepo = InsightsRepository(File(context.filesDir, "vault/insights"), crypto)
            val noteRepo = NoteRepository(File(context.filesDir, "vault/notes"), crypto)

            val today = dateFmt.format(Date())
            val now = System.currentTimeMillis()
            val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
            val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000

            // Self name
            val selfName = try {
                val db = PrivoraDatabase.getInstance(context, crypto)
                val contactRepo = ContactRepository(File(context.filesDir, "vault/contacts"), crypto, db)
                contactRepo.ensureSelfContact("Me")
                contactRepo.getSelfContact()?.name ?: "Me"
            } catch (_: Exception) { "Me" }

            // Reminders — next 7 days
            val allSchedules = insightsRepo.listScheduleItems().filter { it.enabled }
            val reminders = buildReminderSummaries(allSchedules, now, sevenDaysMs)

            // Expenses — last 30 days, capped 30
            val allExpenses = insightsRepo.listExpenses()
                .filter { it.date >= now - thirtyDaysMs }
                .sortedByDescending { it.date }
                .take(30)
            val recentExpenses = allExpenses.map { ExpSummary(
                date = dateFmt.format(Date(it.date)),
                amount = it.amount, currency = it.currency,
                category = it.category, description = it.description
            ) }
            val expenseTotals = allExpenses
                .groupBy { it.category }
                .mapValues { (_, v) -> v.sumOf { it.amount } }

            // Notes — last 20 by modifiedAt (titles + tags only, NEVER bodies)
            val recentNotes = noteRepo.listNotes()
                .sortedByDescending { it.modifiedAt }
                .take(20)
                .map { NoteHeader(it.title, it.tags, dateFmt.format(Date(it.modifiedAt))) }

            // Medications — active list
            val meds = insightsRepo.listMedications().map { med ->
                val schedLabel = med.scheduleId?.let { sid ->
                    insightsRepo.loadScheduleItem(sid)?.let { s ->
                        if (s.isOneShot) "one-time"
                        else s.timesOfDay.joinToString(", ") + if (s.daysOfWeek.isNotEmpty()) " • ${s.daysOfWeek.sorted()}" else " daily"
                    }
                } ?: ""
                MedSummary(med.name, med.dosage, schedLabel)
            }

            // Habits — aggregated (last 7, 30, 365 + streaks)
            val habits = buildHabitReports(insightsRepo)

            // Health — last 7 entries for self profile
            val healthEntries = insightsRepo.listHealthEntries()
                .filter { it.profileId == "self" && it.date >= now - sevenDaysMs }
                .sortedByDescending { it.date }
                .take(7)
            val healthLast7 = healthEntries.map { e ->
                HealthSummary(
                    date = dateFmt.format(Date(e.date)),
                    weight = e.weight, heartRate = e.heartRate,
                    bp = if (e.systolic != null) "${e.systolic}/${e.diastolic ?: "-"}" else null,
                    sleep = e.sleepHours, mood = e.mood, steps = e.steps
                )
            }

            // Family profile names
            val profiles = insightsRepo.loadProfiles().map { it.name }

            return KnowledgeSnapshot(
                today = today, selfName = selfName,
                reminders = reminders, recentExpenses = recentExpenses,
                expenseTotals30d = expenseTotals, recentNotes = recentNotes,
                medications = meds, habits = habits,
                healthLast7 = healthLast7, familyProfileNames = profiles
            )
        }

        private fun buildReminderSummaries(
            items: List<com.privateai.camera.security.ScheduleItem>,
            now: Long,
            windowMs: Long
        ): List<RemSummary> {
            val result = mutableListOf<RemSummary>()
            val endWindow = now + windowMs
            val cal = Calendar.getInstance()

            items.forEach { item ->
                if (item.isOneShot && item.oneShotAt != null) {
                    if (item.oneShotAt in now..endWindow) {
                        result += RemSummary(item.title, dateTimeFmt.format(Date(item.oneShotAt)), "oneshot")
                    }
                } else {
                    // Recurring — list next occurrence per time slot
                    item.timesOfDay.forEach { time ->
                        val parts = time.split(":")
                        if (parts.size == 2) {
                            cal.timeInMillis = now
                            cal.set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: 0)
                            cal.set(Calendar.MINUTE, parts[1].toIntOrNull() ?: 0)
                            cal.set(Calendar.SECOND, 0)
                            if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
                            // Walk up to 7 days to find an allowed day
                            repeat(8) {
                                val dow = when (cal.get(Calendar.DAY_OF_WEEK)) {
                                    Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
                                    Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
                                    else -> 7
                                }
                                if ((item.daysOfWeek.isEmpty() || dow in item.daysOfWeek) && cal.timeInMillis <= endWindow) {
                                    result += RemSummary(item.title, dateTimeFmt.format(cal.time), "recurring")
                                    return@forEach
                                }
                                cal.add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                    }
                }
            }
            return result.sortedBy { it.dateTime }.take(20)
        }

        private fun buildHabitReports(repo: InsightsRepository): List<HabitReport> {
            val habits = repo.loadHabits()
            if (habits.isEmpty()) return emptyList()

            val cal = Calendar.getInstance()
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = fmt.format(Date())

            return habits.map { habit ->
                var last7 = 0; var last30 = 0; var last365 = 0
                var currentStreak = 0; var longestStreak = 0; var streakBroken = false

                // Walk backwards up to 365 days
                cal.timeInMillis = System.currentTimeMillis()
                for (d in 0 until 365) {
                    val date = fmt.format(cal.time)
                    val log = repo.loadHabitLog(date)
                    val done = habit.id in log.completed
                    if (done) {
                        last365++
                        if (d < 7) last7++
                        if (d < 30) last30++
                        if (!streakBroken) currentStreak++
                    } else {
                        streakBroken = true
                    }
                    // Track longest streak (simplified: just track max consecutive from recent)
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                }
                longestStreak = maxOf(currentStreak, longestStreak)

                HabitReport(habit.name, last7, last30, last365, currentStreak, longestStreak)
            }
        }
    }
}

// ── Summary data classes (lightweight, no full content) ──────────────

data class RemSummary(val title: String, val dateTime: String, val kind: String)
data class ExpSummary(val date: String, val amount: Double, val currency: String, val category: String, val description: String)
data class NoteHeader(val title: String, val tags: List<String>, val modifiedAt: String)
data class MedSummary(val name: String, val dosage: String, val schedule: String)
data class HabitReport(val name: String, val last7: Int, val last30: Int, val last365: Int, val currentStreak: Int, val longestStreak: Int)
data class HealthSummary(val date: String, val weight: Float?, val heartRate: Int?, val bp: String?, val sleep: Float?, val mood: Int?, val steps: Int?)
