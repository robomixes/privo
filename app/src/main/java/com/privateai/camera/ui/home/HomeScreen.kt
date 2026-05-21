// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.security.LogState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.privateai.camera.security.VaultLockManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.privateai.camera.R

/** A today-reminder entry shown in the Grid layout's reminder popup. */
private data class HomeTodayRem(val title: String, val time: String, val isDone: Boolean)

/**
 * One row in the home grid. [accent] is the strong per-feature hue used as
 * both the icon tint and (with alpha) the card's container tint. Computing
 * the container color at render time means the card adapts cleanly to the
 * active theme — the same accent reads as a pale tint on a light scaffold
 * and as a dark tint on a dark scaffold without separate light/dark presets.
 */
data class FeatureItem(
    val route: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val accent: Color = Color(0xFF6750A4)
)

val features = listOf(
    FeatureItem("camera", R.string.feature_camera, R.string.feature_camera_desc, Icons.Default.CameraAlt, Color(0xFF1565C0)),
    FeatureItem("detect", R.string.feature_detect, R.string.feature_detect_desc, Icons.Default.Search, Color(0xFF2E7D32)),
    FeatureItem("scan", R.string.feature_scan, R.string.feature_scan_desc, Icons.Default.DocumentScanner, Color(0xFFE65100)),
    FeatureItem("qrscanner", R.string.feature_qr_scan, R.string.feature_qr_scan_desc, Icons.Default.QrCodeScanner, Color(0xFF6A1B9A)),
    FeatureItem("translate", R.string.feature_translate, R.string.feature_translate_desc, Icons.Default.Translate, Color(0xFF00838F)),
    FeatureItem("vault", R.string.feature_vault, R.string.feature_vault_desc, Icons.Default.Lock, Color(0xFFC62828)),
    FeatureItem("notes", R.string.feature_notes, R.string.feature_notes_desc, Icons.Default.NoteAlt, Color(0xFF4E342E)),
    FeatureItem("insights", R.string.feature_insights, R.string.feature_insights_desc, Icons.Default.BarChart, Color(0xFF00695C)),
    FeatureItem("health", R.string.feature_health, R.string.feature_health_desc, Icons.Default.MonitorHeart, Color(0xFFAD1457)),
    FeatureItem("reminders", R.string.feature_reminders, R.string.feature_reminders_desc, Icons.Default.Notifications, Color(0xFFD32F2F)),
    FeatureItem("passwords", R.string.feature_passwords, R.string.feature_passwords_desc, Icons.Default.Key, Color(0xFF7B1FA2)),
    FeatureItem("totp", R.string.feature_authenticator, R.string.feature_authenticator_desc, Icons.Default.LockClock, Color(0xFF0097A7)),
    FeatureItem("tools", R.string.feature_tools, R.string.feature_tools_desc, Icons.Default.Build, Color(0xFF37474F)),
    FeatureItem("contacts", R.string.feature_contacts, R.string.feature_contacts_desc, Icons.Default.Person, Color(0xFFEF6C00)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onFeatureClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onAssistantClick: (() -> Unit)? = null,
    importSummary: String? = null
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val orderedRoutes = remember { com.privateai.camera.ui.settings.FeatureToggleManager.getOrderedEnabledFeatures(context) }
    val featureMap = remember { features.associateBy { it.route } }
    val visibleFeatures = orderedRoutes.mapNotNull { featureMap[it] }
    val layout = remember { com.privateai.camera.ui.settings.FeatureToggleManager.getHomeLayout(context) }

    // Branch to tabs layout if user selected it
    if (layout == com.privateai.camera.ui.settings.HomeLayout.TABS) {
        HomeTabsLayout(
            visibleFeatures = visibleFeatures,
            onFeatureClick = onFeatureClick,
            onSettingsClick = onSettingsClick,
            onAssistantClick = onAssistantClick
        )
        return
    }

    var isVaultUnlocked by remember { mutableStateOf(VaultLockManager.isUnlockedWithinGrace(context)) }
    var showImportBanner by remember { mutableStateOf(importSummary != null) }
    // Single source of truth for AI gating — see AiStatus.kt. Used for the
    // top-bar assistant icon and the AI-tip strip below.
    val aiStatus by com.privateai.camera.bridge.rememberAiStatus()
    val aiReady = aiStatus.isReady

    // Tip strip — starts with the synchronous daily-rotation tip, upgrades to
    // an AI-generated tip when Gemma is available + vault is unlocked. The
    // cached path returns instantly (cached per hour); only a cold cache
    // triggers an actual Gemma call.
    val dailyTip = remember(java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)) {
        getTodayTip(context)
    }
    var aiTip by remember { mutableStateOf<AiTipResult?>(null) }
    LaunchedEffect(isVaultUnlocked, VaultLockManager.isDuressActive, aiReady) {
        if (!isVaultUnlocked || VaultLockManager.isDuressActive) {
            aiTip = null; return@LaunchedEffect
        }
        if (!aiReady) { aiTip = null; return@LaunchedEffect }
        // Cached returns instantly when we have an entry for this hour+lang
        getCachedAiTip(context)?.let { aiTip = it; return@LaunchedEffect }
        // Cold cache — burn one Gemma generation in the background
        withContext(Dispatchers.Default) {
            generateAiTip(context)?.let { aiTip = it }
        }
    }

    // Today's reminders — loaded async from the encrypted insights repo.
    // Hidden during duress (reuses HomeTabsLayout's filtering pattern).
    var todayReminders by remember { mutableStateOf<List<HomeTodayRem>>(emptyList()) }
    var showRemindersDialog by remember { mutableStateOf(false) }
    LaunchedEffect(isVaultUnlocked, VaultLockManager.isDuressActive) {
        if (!isVaultUnlocked || VaultLockManager.isDuressActive) {
            todayReminders = emptyList()
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val crypto = CryptoManager(context).also { it.initialize() }
                val insightsRepo = InsightsRepository(File(context.filesDir, "vault/insights"), crypto)
                val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
                val today = dateFmt.format(Date())
                val cal = Calendar.getInstance()
                val todayDow = when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2
                    Calendar.WEDNESDAY -> 3; Calendar.THURSDAY -> 4
                    Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
                    else -> 7
                }
                val schedules = insightsRepo.listScheduleItems().filter { it.enabled }
                val todayLog = insightsRepo.loadScheduleLog(today)
                val recurring = schedules
                    .filter { !it.isOneShot && (it.daysOfWeek.isEmpty() || todayDow in it.daysOfWeek) }
                    .flatMap { item ->
                        item.timesOfDay.map { time ->
                            val done = todayLog.entries.any {
                                it.scheduleId == item.id && it.time == time && it.state == LogState.DONE
                            }
                            HomeTodayRem(item.title, time, done)
                        }
                    }
                val oneShots = schedules
                    .filter { it.isOneShot && it.oneShotAt != null && dateFmt.format(Date(it.oneShotAt!!)) == today }
                    .map { item ->
                        val t = timeFmt.format(Date(item.oneShotAt!!))
                        val done = todayLog.entries.any {
                            it.scheduleId == item.id && (it.time == t || it.time == "ONESHOT") && it.state == LogState.DONE
                        }
                        HomeTodayRem(item.title, t, done)
                    }
                todayReminders = (recurring + oneShots).sortedBy { it.time }
            } catch (_: Exception) {
                // silently fail — empty state
            }
        }
    }
    val pendingReminderCount = todayReminders.count { !it.isDone }

    // Reminders popup — lists today's coming reminders. Tapping a row navigates
    // into the reminders feature, same as the existing Tabs-layout behavior.
    if (showRemindersDialog) {
        AlertDialog(
            onDismissRequest = { showRemindersDialog = false },
            title = { Text(stringResource(R.string.home_today_reminders_title)) },
            text = {
                if (todayReminders.isEmpty()) {
                    Text(
                        stringResource(R.string.home_today_reminders_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        todayReminders.forEach { rem ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (rem.isDone)
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    else
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                )
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showRemindersDialog = false
                                            onFeatureClick("reminders")
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        if (rem.isDone) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = if (rem.isDone) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            rem.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            textDecoration = if (rem.isDone) TextDecoration.LineThrough else null,
                                            color = if (rem.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        rem.time,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rem.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRemindersDialog = false
                    onFeatureClick("reminders")
                }) { Text(stringResource(R.string.home_today_reminders_open)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemindersDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    // General vault unlock — supports both PHONE_LOCK (biometric) and APP_PIN modes
    val crypto = remember { com.privateai.camera.security.CryptoManager(context) }
    val currentAuthMode = remember { com.privateai.camera.ui.onboarding.getAuthMode(context) }
    var showPinDialog by remember { mutableStateOf(false) }

    fun unlockWithBiometric() {
        val activity = context as? androidx.fragment.app.FragmentActivity ?: return
        val bm = androidx.biometric.BiometricManager.from(context)
        val canAuth = bm.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        if (!canAuth) {
            if (crypto.initialize()) { VaultLockManager.markUnlocked(); isVaultUnlocked = true }
            return
        }
        val prompt = androidx.biometric.BiometricPrompt(
            activity, androidx.core.content.ContextCompat.getMainExecutor(context),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    if (crypto.initialize()) { VaultLockManager.markUnlocked(); isVaultUnlocked = true }
                }
            }
        )
        prompt.authenticate(
            androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.vault_unlock_title))
                .setSubtitle(context.getString(R.string.vault_unlock_subtitle))
                .setAllowedAuthenticators(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                ).build()
        )
    }

    fun unlockVault() {
        if (currentAuthMode == com.privateai.camera.ui.onboarding.AuthMode.APP_PIN) {
            showPinDialog = true
        } else {
            unlockWithBiometric()
        }
    }

    // PIN unlock dialog
    if (showPinDialog) {
        VaultPinDialog(
            crypto = crypto,
            onUnlocked = { isDuress ->
                showPinDialog = false
                isVaultUnlocked = !isDuress
            },
            onDismiss = { showPinDialog = false }
        )
    }

    Scaffold(
        topBar = {
            // Compact TopAppBar — replaces the previous LargeTopAppBar so the
            // header area can be reused for a tip strip + reminders bell with
            // count badge instead of just empty vertical space.
            TopAppBar(
                title = { Text(stringResource(R.string.app_name_home)) },
                actions = {
                    // ✨ AI Assistant — visible when AI is available AND
                    // either the vault is unlocked OR the user opted in to
                    // "Allow Assistant without unlocking vault" (Settings →
                    // AI Detection). Still hidden under duress: the duress
                    // PIN should expose nothing, not even chat.
                    val assistantUnlockedAccess = remember(isVaultUnlocked) {
                        com.privateai.camera.ui.settings.isAssistantUnlockedAccessEnabled(context)
                    }
                    if ((isVaultUnlocked || assistantUnlockedAccess)
                        && aiReady
                        && !VaultLockManager.isDuressActive
                        && onAssistantClick != null
                    ) {
                        IconButton(onClick = onAssistantClick) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = stringResource(R.string.assistant_title),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    // 🔔 Today's reminders — count badge, tap opens popup. Hidden
                    // during duress (no leakage of pending tasks) and when vault
                    // is locked (we can't decrypt the schedule until unlocked).
                    if (isVaultUnlocked && !VaultLockManager.isDuressActive) {
                        IconButton(onClick = { showRemindersDialog = true }) {
                            BadgedBox(
                                badge = {
                                    if (pendingReminderCount > 0) {
                                        Badge { Text(pendingReminderCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = stringResource(R.string.home_today_reminders_title),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    // Lock/Unlock — always visible so the user can unlock without navigating to a feature first
                    if (isVaultUnlocked) {
                        IconButton(onClick = {
                            VaultLockManager.lock()
                            isVaultUnlocked = false
                            Toast.makeText(context, context.getString(R.string.vault_notes_locked), Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier
                            .border(2.dp, Color(0xFF4CAF50), CircleShape)
                            .padding(2.dp)
                        ) {
                            Icon(
                                Icons.Default.LockOpen,
                                contentDescription = stringResource(R.string.cd_lock_vault),
                                tint = Color(0xFF4CAF50)
                            )
                        }
                    } else {
                        IconButton(onClick = { unlockVault() }) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = stringResource(R.string.action_unlock),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Import success banner
            if (showImportBanner && importSummary != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.backup_restored),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                importSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(onClick = { showImportBanner = false }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_dismiss),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Tip strip — fills the area the LargeTopAppBar used to use for
            // whitespace. Shows the AI-generated tip when available, otherwise
            // the static daily tip (rotates at midnight via day-of-year).
            val tipText = aiTip?.text ?: dailyTip
            val categoryLabel = aiTip?.let { stringResource(it.labelRes) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (aiTip != null) Icons.Default.AutoAwesome else Icons.Default.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Column(Modifier.weight(1f)) {
                        if (categoryLabel != null) {
                            Text(
                                categoryLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Text(
                            tipText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(visibleFeatures) { feature ->
                    FeatureCard(
                        feature = feature,
                        isLimited = com.privateai.camera.service.DeviceProfiler.isFeatureLimited(context, feature.route),
                        onClick = { onFeatureClick(feature.route) }
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureCard(
    feature: FeatureItem,
    isLimited: Boolean = false,
    onClick: () -> Unit
) {
    val label = stringResource(feature.labelRes)
    val description = stringResource(feature.descriptionRes)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .semantics { contentDescription = "$label: $description" }
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            // Tinted overlay of the accent on the active theme's surface — reads
            // as pale-tint on light, dark-tint on dark, no separate presets.
            containerColor = feature.accent.copy(alpha = 0.15f)
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    feature.icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = feature.accent
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (isLimited) {
                Text(
                    "⚡",
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * Reusable PIN unlock dialog. Handles the app PIN check, duress PIN detection,
 * and rate limiting — same logic as InsightsScreen's lock screen but in dialog form.
 */
@Composable
fun VaultPinDialog(
    crypto: com.privateai.camera.security.CryptoManager,
    onUnlocked: (isDuress: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var isLockedOut by remember { mutableStateOf(com.privateai.camera.security.PinRateLimiter.remainingLockoutMs(context) > 0) }
    var lockoutRemainingMs by remember { mutableStateOf(com.privateai.camera.security.PinRateLimiter.remainingLockoutMs(context)) }

    androidx.compose.runtime.LaunchedEffect(isLockedOut) {
        if (isLockedOut) {
            while (true) {
                val remaining = com.privateai.camera.security.PinRateLimiter.remainingLockoutMs(context)
                if (remaining <= 0) { isLockedOut = false; lockoutRemainingMs = 0L; break }
                lockoutRemainingMs = remaining
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    fun checkPin(pin: String) {
        // Duress PIN check
        if (com.privateai.camera.security.DuressManager.isEnabled(context) &&
            com.privateai.camera.security.DuressManager.isDuressPin(context, pin)
        ) {
            VaultLockManager.activateDuress()
            VaultLockManager.markUnlocked()
            Thread { com.privateai.camera.security.DuressManager.executeDuress(context, crypto) }.start()
            onUnlocked(true)
            return
        }

        if (!com.privateai.camera.security.PinRateLimiter.canAttempt(context)) {
            pinInput = ""
            isLockedOut = true
            lockoutRemainingMs = com.privateai.camera.security.PinRateLimiter.remainingLockoutMs(context)
            return
        }

        if (com.privateai.camera.security.AppPinManager.verify(context, pin)) {
            com.privateai.camera.security.PinRateLimiter.recordSuccess(context)
            if (crypto.initialize()) {
                VaultLockManager.clearDuress()
                VaultLockManager.markUnlocked()
                onUnlocked(false)
            }
            return
        }

        com.privateai.camera.security.PinRateLimiter.recordFailure(context)
        val remaining = com.privateai.camera.security.PinRateLimiter.remainingLockoutMs(context)
        if (remaining > 0) {
            isLockedOut = true; lockoutRemainingMs = remaining; pinError = null
        } else {
            pinError = context.getString(R.string.vault_incorrect_pin)
        }
        pinInput = ""
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_unlock_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLockedOut) {
                    val seconds = (lockoutRemainingMs / 1000).toInt()
                    Text(
                        stringResource(R.string.pin_locked_out, "%d:%02d".format(seconds / 60, seconds % 60)),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    androidx.compose.material3.OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) { pinInput = it; pinError = null } },
                        label = { Text(stringResource(R.string.vault_enter_pin)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { if (pinInput.length >= 4) checkPin(pinInput) }
                        ),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        isError = pinError != null,
                        supportingText = { pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (pinInput.length >= 4) checkPin(pinInput) },
                enabled = pinInput.length >= 4 && !isLockedOut
            ) { Text(stringResource(R.string.action_unlock)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
