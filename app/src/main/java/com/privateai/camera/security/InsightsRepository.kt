// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.security

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ===== Data Models =====

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val currency: String = "USD",
    val category: String,
    val description: String,
    val date: Long = System.currentTimeMillis(),
    val receiptPhotoId: String? = null,
    val personId: String? = null,
    val profileId: String = "self" // "self" or HealthProfile.id — unified with Health/Habits
)

data class HealthProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "👤", // emoji
    val personId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class HealthEntry(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String = "self", // "self" or profile ID
    val date: Long = System.currentTimeMillis(),
    val weight: Float? = null,
    val sleepHours: Float? = null,
    val painLevel: Int? = null,
    val mood: Int? = null, // 1-5
    val temperature: Float? = null, // °C or °F
    val steps: Int? = null,
    val heartRate: Int? = null, // bpm
    val systolic: Int? = null, // blood pressure
    val diastolic: Int? = null,
    val notes: String? = null
)

data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "✅",
    val color: Int = 0xFF4CAF50.toInt(),
    val createdAt: Long = System.currentTimeMillis(),
    val profileId: String = "self", // "self" or HealthProfile.id
    val scheduleId: String? = null  // optional linked ScheduleItem (Phase F)
)

data class HabitLog(
    val date: String, // "2026-04-03"
    val completed: Set<String> // habit IDs
)

data class Medication(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String = "self",   // "self" or HealthProfile.id
    val name: String,                 // "Aspirin 100mg"
    val dosage: String = "",          // "1 pill", "5ml"
    val instructions: String = "",    // "With food"
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,        // null = indefinite / ongoing
    val scheduleId: String? = null,   // optional linked ScheduleItem
    val notes: String = ""
)

/**
 * One menstrual-cycle entry. Stored as `${id}.cycle.enc` in `vault/insights/`,
 * encrypted with the same DEK as everything else. Multi-profile via `profileId`.
 *
 * Day-precision: `periodStart` and `periodEnd` are millis but the UI rounds
 * them to local-day boundaries — there is no "11:42 AM" precision for periods.
 *
 * `flow` is 1 (light) – 5 (heaviest); `symptoms` is a list of canonical keys
 * (`cramps`, `headache`, etc.) — never free text in v1, to keep translations tight.
 */
data class CycleEntry(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String = "self",
    val periodStart: Long,
    val periodEnd: Long? = null,
    val flow: Int? = null,
    val symptoms: List<String> = emptyList(),
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** Result of `predictNextPeriod`. `nextStart` is null when no entries exist. */
data class CyclePrediction(
    val nextStart: Long?,
    val cycleLengthDays: Int,    // average gap between starts; defaults to 28 when 0 history
    val periodLengthDays: Int,   // average duration; defaults to 5 when 0 history
    val confidence: Int          // 0..3 — how many historical gaps drove the average
)

enum class ScheduleKind { MEDICATION, HABIT, CUSTOM }
enum class LogState { DONE, SKIPPED, MISSED }

data class ScheduleItem(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String = "self",
    val kind: ScheduleKind = ScheduleKind.CUSTOM,
    val sourceId: String? = null,         // Medication.id / Habit.id when linked
    val title: String,                    // "Take Aspirin", "Gym", "Walk the dog"
    val timesOfDay: List<String> = emptyList(), // ["08:00", "20:00"] — recurring mode
    val daysOfWeek: Set<Int> = emptySet(),      // 1..7 (Mon..Sun); empty = every day — recurring mode
    val oneShotAt: Long? = null,          // absolute epoch millis — fires once at this moment (non-recurring)
    val enabled: Boolean = true,
    val reminderMinutesBefore: Int = 0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val isOneShot: Boolean get() = oneShotAt != null
}

data class ScheduleLogEntry(
    val scheduleId: String,
    val time: String,        // "08:00"
    val state: LogState
)

data class ScheduleLog(
    val date: String,                          // "2026-04-14"
    val entries: List<ScheduleLogEntry> = emptyList()
)

val EXPENSE_CATEGORIES = listOf("Food", "Transport", "Shopping", "Bills", "Health", "Entertainment", "Education", "Other")
val MOOD_EMOJIS = listOf("😞", "😕", "😐", "🙂", "😀")

/**
 * Encrypted storage for Insights data (expenses, health, habits).
 * Same encryption as vault — AES-256-GCM.
 */
class InsightsRepository(private val baseDir: File, private val crypto: CryptoManager) {

    companion object {
        private const val TAG = "InsightsRepository"
    }

    private val expensesDir = File(baseDir, "expenses").also { it.mkdirs() }
    private val healthDir = File(baseDir, "health").also { it.mkdirs() }
    private val habitsDir = File(baseDir, "habits").also { it.mkdirs() }
    private val medsDir = File(baseDir, "medications").also { it.mkdirs() }

    // Reminders storage moved to vault/reminders/ (Phase G — Reminders promoted to top-level feature).
    // baseDir is vault/insights, so parent is vault/ — put reminders alongside insights.
    private val remindersBaseDir = File(baseDir.parentFile, "reminders").also { it.mkdirs() }
    private val scheduleDir = File(remindersBaseDir, "items").also { it.mkdirs() }
    private val scheduleLogsDir = File(remindersBaseDir, "logs").also { it.mkdirs() }

    init {
        // One-shot migration from the old Insights-scoped paths to the new Reminders-scoped paths.
        // Copy any files from vault/insights/schedule/ → vault/reminders/items/ and
        // vault/insights/schedule_logs/ → vault/reminders/logs/, then delete the old dirs.
        migrateLegacyScheduleDirs()
    }

    private fun migrateLegacyScheduleDirs() {
        try {
            val oldItems = File(baseDir, "schedule")
            if (oldItems.exists() && oldItems.isDirectory) {
                oldItems.listFiles()?.forEach { src ->
                    val dst = File(scheduleDir, src.name)
                    if (!dst.exists()) src.copyTo(dst, overwrite = false)
                    src.delete()
                }
                oldItems.delete()
            }
            val oldLogs = File(baseDir, "schedule_logs")
            if (oldLogs.exists() && oldLogs.isDirectory) {
                oldLogs.listFiles()?.forEach { src ->
                    val dst = File(scheduleLogsDir, src.name)
                    if (!dst.exists()) src.copyTo(dst, overwrite = false)
                    src.delete()
                }
                oldLogs.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Schedule migration failed: ${e.message}")
        }
    }

    // ===== Expenses =====

    fun saveExpense(expense: Expense) {
        val json = JSONObject().apply {
            put("id", expense.id); put("amount", expense.amount); put("currency", expense.currency)
            put("category", expense.category); put("description", expense.description)
            put("date", expense.date); put("receiptPhotoId", expense.receiptPhotoId ?: "")
            put("personId", expense.personId ?: "")
            put("profileId", expense.profileId)
        }.toString()
        crypto.encryptToFile(json.toByteArray(Charsets.UTF_8), File(expensesDir, "${expense.id}.expense.enc"))
    }

    fun listExpenses(): List<Expense> {
        return (expensesDir.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".expense.enc") && !it.name.startsWith("_tobedeleted_") }
            .mapNotNull { file ->
                try {
                    val json = String(crypto.decryptFile(file), Charsets.UTF_8)
                    val obj = JSONObject(json)
                    Expense(
                        id = obj.getString("id"), amount = obj.getDouble("amount"),
                        currency = obj.optString("currency", "USD"),
                        category = obj.getString("category"), description = obj.getString("description"),
                        date = obj.getLong("date"),
                        receiptPhotoId = obj.optString("receiptPhotoId", "").ifEmpty { null },
                        personId = obj.optString("personId", "").ifEmpty { null },
                        profileId = obj.optString("profileId", "self") // migration: existing = "self"
                    )
                } catch (e: Exception) { Log.e(TAG, "Failed to load expense: ${e.message}"); null }
            }
            .sortedByDescending { it.date }
    }

    fun listExpensesForProfile(profileId: String): List<Expense> =
        listExpenses().filter { it.profileId == profileId }

    fun deleteExpense(id: String) {
        File(expensesDir, "$id.expense.enc").delete()
    }

    fun getMonthlyTotal(year: Int, month: Int): Double {
        val fmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val target = "$year-${"%02d".format(month)}"
        return listExpenses().filter { fmt.format(Date(it.date)) == target }.sumOf { it.amount }
    }

    fun getCategoryTotals(): Map<String, Double> {
        val expenses = listExpenses()
        return EXPENSE_CATEGORIES.associateWith { cat ->
            expenses.filter { it.category == cat }.sumOf { it.amount }
        }.filter { it.value > 0 }
    }

    // ===== Health =====

    fun saveHealthEntry(entry: HealthEntry) {
        val json = JSONObject().apply {
            put("id", entry.id); put("profileId", entry.profileId); put("date", entry.date)
            if (entry.weight != null) put("weight", entry.weight)
            if (entry.sleepHours != null) put("sleepHours", entry.sleepHours)
            if (entry.painLevel != null) put("painLevel", entry.painLevel)
            if (entry.mood != null) put("mood", entry.mood)
            if (entry.temperature != null) put("temperature", entry.temperature)
            if (entry.steps != null) put("steps", entry.steps)
            if (entry.heartRate != null) put("heartRate", entry.heartRate)
            if (entry.systolic != null) put("systolic", entry.systolic)
            if (entry.diastolic != null) put("diastolic", entry.diastolic)
            if (entry.notes != null) put("notes", entry.notes)
        }.toString()
        crypto.encryptToFile(json.toByteArray(Charsets.UTF_8), File(healthDir, "${entry.id}.health.enc"))
    }

    fun listHealthEntries(): List<HealthEntry> {
        return (healthDir.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".health.enc") && !it.name.startsWith("_tobedeleted_") }
            .mapNotNull { file ->
                try {
                    val json = String(crypto.decryptFile(file), Charsets.UTF_8)
                    val obj = JSONObject(json)
                    HealthEntry(
                        id = obj.getString("id"),
                        profileId = obj.optString("profileId", "self"),
                        date = obj.getLong("date"),
                        weight = if (obj.has("weight")) obj.getDouble("weight").toFloat() else null,
                        sleepHours = if (obj.has("sleepHours")) obj.getDouble("sleepHours").toFloat() else null,
                        painLevel = if (obj.has("painLevel")) obj.getInt("painLevel") else null,
                        mood = if (obj.has("mood")) obj.getInt("mood") else null,
                        temperature = if (obj.has("temperature")) obj.getDouble("temperature").toFloat() else null,
                        steps = if (obj.has("steps")) obj.getInt("steps") else null,
                        heartRate = if (obj.has("heartRate")) obj.getInt("heartRate") else null,
                        systolic = if (obj.has("systolic")) obj.getInt("systolic") else null,
                        diastolic = if (obj.has("diastolic")) obj.getInt("diastolic") else null,
                        notes = obj.optString("notes", "").ifEmpty { null }
                    )
                } catch (e: Exception) { Log.e(TAG, "Failed to load health: ${e.message}"); null }
            }
            .sortedByDescending { it.date }
    }

    fun deleteHealthEntry(id: String) {
        File(healthDir, "$id.health.enc").delete()
    }

    fun listHealthEntriesForProfile(profileId: String): List<HealthEntry> =
        listHealthEntries().filter { it.profileId == profileId }

    fun listHealthEntriesInRange(from: Long, to: Long, profileId: String = "self"): List<HealthEntry> =
        listHealthEntries().filter { it.profileId == profileId && it.date in from..to }

    // ===== Cycle / Period =====

    fun saveCycleEntry(entry: CycleEntry) {
        val json = JSONObject().apply {
            put("id", entry.id)
            put("profileId", entry.profileId)
            put("periodStart", entry.periodStart)
            if (entry.periodEnd != null) put("periodEnd", entry.periodEnd)
            if (entry.flow != null) put("flow", entry.flow)
            put("symptoms", JSONArray(entry.symptoms))
            if (entry.notes != null) put("notes", entry.notes)
            put("createdAt", entry.createdAt)
        }.toString()
        crypto.encryptToFile(json.toByteArray(Charsets.UTF_8), File(healthDir, "${entry.id}.cycle.enc"))
    }

    fun listCycleEntries(): List<CycleEntry> {
        return (healthDir.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".cycle.enc") && !it.name.startsWith("_tobedeleted_") }
            .mapNotNull { file ->
                try {
                    val obj = JSONObject(String(crypto.decryptFile(file), Charsets.UTF_8))
                    val symptomsArr = obj.optJSONArray("symptoms")
                    val symptoms = if (symptomsArr != null)
                        (0 until symptomsArr.length()).map { symptomsArr.getString(it) }
                    else emptyList()
                    CycleEntry(
                        id = obj.getString("id"),
                        profileId = obj.optString("profileId", "self"),
                        periodStart = obj.getLong("periodStart"),
                        periodEnd = if (obj.has("periodEnd")) obj.getLong("periodEnd") else null,
                        flow = if (obj.has("flow")) obj.getInt("flow") else null,
                        symptoms = symptoms,
                        notes = obj.optString("notes", "").ifEmpty { null },
                        createdAt = obj.optLong("createdAt", 0L)
                    )
                } catch (e: Exception) { Log.e(TAG, "Failed to load cycle: ${e.message}"); null }
            }
            .sortedByDescending { it.periodStart }
    }

    fun deleteCycleEntry(id: String) {
        File(healthDir, "$id.cycle.enc").delete()
    }

    /**
     * Last-3-cycles average. Defaults to 28-day cycle / 5-day period when there's
     * insufficient history. The `confidence` field exposes how many real data
     * points went in (0..3) so the UI can label outputs as "estimate" honestly.
     *
     * Always retrospective: takes existing logged starts, computes gap averages,
     * projects the next start forward. **Never** outputs ovulation, fertility
     * window, or "safe day" — that's regulatory territory we explicitly avoid.
     */
    fun predictNextPeriod(entries: List<CycleEntry>): CyclePrediction {
        if (entries.isEmpty()) return CyclePrediction(null, 28, 5, 0)
        val sorted = entries.sortedBy { it.periodStart }.takeLast(4)
        val dayMs = 24L * 60 * 60 * 1000
        val gaps = sorted.zipWithNext { a, b ->
            ((b.periodStart - a.periodStart) / dayMs).toInt()
        }.filter { it in 14..60 }   // sanity range; ignore outliers
        val avgCycle = if (gaps.isNotEmpty()) gaps.average().toInt() else 28
        val durations = sorted.mapNotNull { e ->
            if (e.periodEnd != null) ((e.periodEnd - e.periodStart) / dayMs).toInt().takeIf { it in 1..14 } else null
        }
        val avgPeriod = if (durations.isNotEmpty()) durations.average().toInt() else 5
        val lastStart = sorted.last().periodStart
        return CyclePrediction(
            nextStart = lastStart + avgCycle * dayMs,
            cycleLengthDays = avgCycle,
            periodLengthDays = avgPeriod,
            confidence = gaps.size.coerceAtMost(3)
        )
    }

    // ===== Health Profiles =====

    fun saveProfiles(profiles: List<HealthProfile>) {
        val arr = JSONArray()
        profiles.forEach { p -> arr.put(JSONObject().apply { put("id", p.id); put("name", p.name); put("icon", p.icon); put("personId", p.personId ?: ""); put("createdAt", p.createdAt) }) }
        crypto.encryptToFile(arr.toString().toByteArray(Charsets.UTF_8), File(healthDir, "profiles.enc"))
    }

    fun loadProfiles(): List<HealthProfile> {
        val file = File(healthDir, "profiles.enc")
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(String(crypto.decryptFile(file), Charsets.UTF_8))
            (0 until arr.length()).map { i -> val o = arr.getJSONObject(i)
                HealthProfile(o.getString("id"), o.getString("name"), o.optString("icon", "👤"), o.optString("personId", "").ifEmpty { null }, o.optLong("createdAt", 0))
            }
        } catch (_: Exception) { emptyList() }
    }

    // ===== Expense Date Range =====

    fun listExpensesInRange(from: Long, to: Long): List<Expense> =
        listExpenses().filter { it.date in from..to }

    // ===== PDF Report Generation =====

    fun generateExpenseReport(expenses: List<Expense>): String = "" // kept for compat, use table version

    fun generateExpenseReportTable(expenses: List<Expense>): Triple<List<String>, List<String>, List<List<String>>> {
        val fmt = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val subtitle = listOf(
            "Generated: ${fmt.format(Date())}",
            "Total: ${"%.2f".format(expenses.sumOf { it.amount })} • ${expenses.size} items"
        )
        val summary = EXPENSE_CATEGORIES.mapNotNull { cat ->
            val total = expenses.filter { it.category == cat }.sumOf { it.amount }
            if (total > 0) "$cat: ${"%.2f".format(total)}" else null
        }
        val headers = listOf("Date", "Category", "Description", "Amount")
        val rows = expenses.map { e ->
            listOf(fmt.format(Date(e.date)), e.category, e.description, "${"%.2f".format(e.amount)}")
        }
        return Triple(subtitle + listOf("---") + summary, headers, rows)
    }

    fun generateHealthReport(entries: List<HealthEntry>, profileName: String): String = "" // compat

    fun generateHealthReportTable(entries: List<HealthEntry>, profileName: String): Triple<List<String>, List<String>, List<List<String>>> {
        val fmt = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
        val subtitle = mutableListOf(
            "Profile: $profileName",
            "Generated: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())}",
            "Entries: ${entries.size}"
        )
        // Averages
        val avgs = mutableListOf<String>()
        entries.mapNotNull { it.weight }.let { if (it.isNotEmpty()) avgs.add("Weight: ${"%.1f".format(it.average())}kg") }
        entries.mapNotNull { it.sleepHours }.let { if (it.isNotEmpty()) avgs.add("Sleep: ${"%.1f".format(it.average())}hrs") }
        entries.mapNotNull { it.heartRate }.let { if (it.isNotEmpty()) avgs.add("HR: ${it.average().toInt()}bpm") }
        entries.mapNotNull { it.temperature }.let { if (it.isNotEmpty()) avgs.add("Temp: ${"%.1f".format(it.average())}°") }
        entries.mapNotNull { it.steps }.let { if (it.isNotEmpty()) avgs.add("Steps: ${it.average().toInt()}") }
        entries.filter { it.systolic != null }.let { if (it.isNotEmpty()) avgs.add("BP: ${it.map { e -> e.systolic!! }.average().toInt()}/${it.map { e -> e.diastolic!! }.average().toInt()}") }

        val headers = listOf("Date", "Weight", "Sleep", "HR", "Temp", "BP", "Steps", "Mood", "Notes")
        val rows = entries.map { e ->
            listOf(
                fmt.format(Date(e.date)),
                e.weight?.let { "${"%.1f".format(it)}" } ?: "",
                e.sleepHours?.let { "${"%.1f".format(it)}" } ?: "",
                e.heartRate?.toString() ?: "",
                e.temperature?.let { "${"%.1f".format(it)}" } ?: "",
                if (e.systolic != null) "${e.systolic}/${e.diastolic}" else "",
                e.steps?.toString() ?: "",
                e.mood?.let { if (it in 1..5) MOOD_EMOJIS[it - 1] else "" } ?: "",
                e.notes ?: ""
            )
        }
        return Triple(subtitle + if (avgs.isNotEmpty()) listOf("---", "Averages: ${avgs.joinToString(" • ")}") else emptyList(), headers, rows)
    }

    // ===== Habits =====

    fun saveHabits(habits: List<Habit>) {
        val arr = JSONArray()
        habits.forEach { h ->
            arr.put(JSONObject().apply {
                put("id", h.id); put("name", h.name); put("icon", h.icon)
                put("color", h.color); put("createdAt", h.createdAt)
                put("profileId", h.profileId)
                if (h.scheduleId != null) put("scheduleId", h.scheduleId)
            })
        }
        crypto.encryptToFile(arr.toString().toByteArray(Charsets.UTF_8), File(habitsDir, "habits_config.enc"))
    }

    fun loadHabits(): List<Habit> {
        val file = File(habitsDir, "habits_config.enc")
        if (!file.exists()) return emptyList()
        return try {
            val json = String(crypto.decryptFile(file), Charsets.UTF_8)
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Habit(
                    id = obj.getString("id"), name = obj.getString("name"),
                    icon = obj.optString("icon", "✅"),
                    color = obj.optInt("color", 0xFF4CAF50.toInt()),
                    createdAt = obj.optLong("createdAt", 0),
                    profileId = obj.optString("profileId", "self"), // migration
                    scheduleId = obj.optString("scheduleId", "").ifEmpty { null }
                )
            }
        } catch (e: Exception) { Log.e(TAG, "Failed to load habits: ${e.message}"); emptyList() }
    }

    fun loadHabitsForProfile(profileId: String): List<Habit> =
        loadHabits().filter { it.profileId == profileId }

    fun saveHabitLog(log: HabitLog) {
        val json = JSONObject().apply {
            put("date", log.date)
            put("completed", JSONArray(log.completed.toList()))
        }.toString()
        crypto.encryptToFile(json.toByteArray(Charsets.UTF_8), File(habitsDir, "${log.date}.hablog.enc"))
    }

    fun loadHabitLog(date: String): HabitLog {
        val file = File(habitsDir, "$date.hablog.enc")
        if (!file.exists()) return HabitLog(date, emptySet())
        return try {
            val json = String(crypto.decryptFile(file), Charsets.UTF_8)
            val obj = JSONObject(json)
            val arr = obj.getJSONArray("completed")
            val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
            HabitLog(obj.getString("date"), set)
        } catch (e: Exception) { HabitLog(date, emptySet()) }
    }

    fun loadHabitLogs(days: Int = 30): List<HabitLog> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = java.util.Calendar.getInstance()
        return (0 until days).mapNotNull { i ->
            cal.timeInMillis = System.currentTimeMillis() - i * 86400000L
            val date = fmt.format(cal.time)
            val log = loadHabitLog(date)
            if (log.completed.isNotEmpty()) log else null
        }
    }

    fun getStreak(habitId: String): Int {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = java.util.Calendar.getInstance()
        var streak = 0
        for (i in 0..365) {
            cal.timeInMillis = System.currentTimeMillis() - i * 86400000L
            val date = fmt.format(cal.time)
            val log = loadHabitLog(date)
            if (habitId in log.completed) streak++ else if (i > 0) break
        }
        return streak
    }

    // ===== Medications =====

    fun saveMedication(med: Medication) {
        val json = JSONObject().apply {
            put("id", med.id)
            put("profileId", med.profileId)
            put("name", med.name)
            put("dosage", med.dosage)
            put("instructions", med.instructions)
            put("startDate", med.startDate)
            if (med.endDate != null) put("endDate", med.endDate)
            if (med.scheduleId != null) put("scheduleId", med.scheduleId)
            put("notes", med.notes)
        }.toString()
        crypto.encryptToFile(json.toByteArray(Charsets.UTF_8), File(medsDir, "${med.id}.med.enc"))
    }

    fun listMedications(): List<Medication> {
        return (medsDir.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".med.enc") && !it.name.startsWith("_tobedeleted_") }
            .mapNotNull { file ->
                try {
                    val json = String(crypto.decryptFile(file), Charsets.UTF_8)
                    val obj = JSONObject(json)
                    Medication(
                        id = obj.getString("id"),
                        profileId = obj.optString("profileId", "self"),
                        name = obj.getString("name"),
                        dosage = obj.optString("dosage", ""),
                        instructions = obj.optString("instructions", ""),
                        startDate = obj.optLong("startDate", 0L),
                        endDate = if (obj.has("endDate")) obj.getLong("endDate") else null,
                        scheduleId = obj.optString("scheduleId", "").ifEmpty { null },
                        notes = obj.optString("notes", "")
                    )
                } catch (e: Exception) { Log.e(TAG, "Failed to load medication: ${e.message}"); null }
            }
            .sortedByDescending { it.startDate }
    }

    fun listMedicationsForProfile(profileId: String): List<Medication> =
        listMedications().filter { it.profileId == profileId }

    fun deleteMedication(id: String) {
        File(medsDir, "$id.med.enc").delete()
    }

    // ===== Schedule =====

    fun saveSchedule(item: ScheduleItem) {
        val json = JSONObject().apply {
            put("id", item.id)
            put("profileId", item.profileId)
            put("kind", item.kind.name)
            if (item.sourceId != null) put("sourceId", item.sourceId)
            put("title", item.title)
            put("timesOfDay", JSONArray(item.timesOfDay))
            put("daysOfWeek", JSONArray(item.daysOfWeek.toList()))
            if (item.oneShotAt != null) put("oneShotAt", item.oneShotAt)
            put("enabled", item.enabled)
            put("reminderMinutesBefore", item.reminderMinutesBefore)
            put("notes", item.notes)
            put("createdAt", item.createdAt)
        }.toString()
        crypto.encryptToFile(json.toByteArray(Charsets.UTF_8), File(scheduleDir, "${item.id}.sched.enc"))
    }

    fun listScheduleItems(): List<ScheduleItem> {
        return (scheduleDir.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".sched.enc") && !it.name.startsWith("_tobedeleted_") }
            .mapNotNull { file ->
                try {
                    val json = String(crypto.decryptFile(file), Charsets.UTF_8)
                    val obj = JSONObject(json)
                    val timesArr = obj.optJSONArray("timesOfDay")
                    val times = if (timesArr != null) (0 until timesArr.length()).map { timesArr.getString(it) } else emptyList()
                    val daysArr = obj.optJSONArray("daysOfWeek")
                    val days = if (daysArr != null) (0 until daysArr.length()).map { daysArr.getInt(it) }.toSet() else emptySet()
                    ScheduleItem(
                        id = obj.getString("id"),
                        profileId = obj.optString("profileId", "self"),
                        kind = try { ScheduleKind.valueOf(obj.optString("kind", "CUSTOM")) } catch (_: Exception) { ScheduleKind.CUSTOM },
                        sourceId = obj.optString("sourceId", "").ifEmpty { null },
                        title = obj.getString("title"),
                        timesOfDay = times,
                        daysOfWeek = days,
                        oneShotAt = if (obj.has("oneShotAt")) obj.getLong("oneShotAt") else null,
                        enabled = obj.optBoolean("enabled", true),
                        reminderMinutesBefore = obj.optInt("reminderMinutesBefore", 0),
                        notes = obj.optString("notes", ""),
                        createdAt = obj.optLong("createdAt", 0L)
                    )
                } catch (e: Exception) { Log.e(TAG, "Failed to load schedule: ${e.message}"); null }
            }
            .sortedBy { it.title }
    }

    fun listScheduleItemsForProfile(profileId: String): List<ScheduleItem> =
        listScheduleItems().filter { it.profileId == profileId }

    /** Load a single schedule item by id, or null if missing/corrupt. */
    fun loadScheduleItem(id: String): ScheduleItem? =
        listScheduleItems().find { it.id == id }

    /** Find every schedule linked to a given source (medication or habit). */
    fun findSchedulesForSource(sourceId: String, kind: ScheduleKind): List<ScheduleItem> =
        listScheduleItems().filter { it.sourceId == sourceId && it.kind == kind }

    fun deleteScheduleItem(id: String) {
        File(scheduleDir, "$id.sched.enc").delete()
    }

    // ===== Schedule Logs (done/skipped/missed marks) =====

    fun saveScheduleLog(log: ScheduleLog) {
        val json = JSONObject().apply {
            put("date", log.date)
            val arr = JSONArray()
            log.entries.forEach { e ->
                arr.put(JSONObject().apply {
                    put("scheduleId", e.scheduleId)
                    put("time", e.time)
                    put("state", e.state.name)
                })
            }
            put("entries", arr)
        }.toString()
        crypto.encryptToFile(json.toByteArray(Charsets.UTF_8), File(scheduleLogsDir, "${log.date}.log.enc"))
    }

    fun loadScheduleLog(date: String): ScheduleLog {
        val file = File(scheduleLogsDir, "$date.log.enc")
        if (!file.exists()) return ScheduleLog(date, emptyList())
        return try {
            val obj = JSONObject(String(crypto.decryptFile(file), Charsets.UTF_8))
            val arr = obj.getJSONArray("entries")
            val entries = (0 until arr.length()).mapNotNull { i ->
                try {
                    val e = arr.getJSONObject(i)
                    ScheduleLogEntry(
                        scheduleId = e.getString("scheduleId"),
                        time = e.getString("time"),
                        state = LogState.valueOf(e.getString("state"))
                    )
                } catch (_: Exception) { null }
            }
            ScheduleLog(obj.getString("date"), entries)
        } catch (_: Exception) { ScheduleLog(date, emptyList()) }
    }

    /** Convenience: mark a single schedule entry with a state for a given date. */
    fun markScheduleEntry(date: String, scheduleId: String, time: String, state: LogState) {
        val current = loadScheduleLog(date)
        val filtered = current.entries.filterNot { it.scheduleId == scheduleId && it.time == time }
        val updated = ScheduleLog(date, filtered + ScheduleLogEntry(scheduleId, time, state))
        saveScheduleLog(updated)
    }

    // ===== Wipe =====

    fun wipeAll() {
        expensesDir.listFiles()?.forEach { it.delete() }
        healthDir.listFiles()?.forEach { it.delete() }
        habitsDir.listFiles()?.forEach { it.delete() }
        medsDir.listFiles()?.forEach { it.delete() }
        scheduleDir.listFiles()?.forEach { it.delete() }
        scheduleLogsDir.listFiles()?.forEach { it.delete() }
        Log.i(TAG, "Insights data wiped")
    }
}
