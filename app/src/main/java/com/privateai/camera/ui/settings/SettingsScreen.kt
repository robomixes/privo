// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.GroupRemove
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.privateai.camera.R
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.PinRateLimiter
import com.privateai.camera.security.VaultCategory
import com.privateai.camera.security.VaultLockManager
import com.privateai.camera.security.AppPinManager
import com.privateai.camera.security.VaultRepository
import com.privateai.camera.security.NoteRepository
import com.privateai.camera.service.DeviceProfiler
import com.privateai.camera.service.StorageManager
import com.privateai.camera.ui.onboarding.AuthMode
import com.privateai.camera.ui.onboarding.getAuthMode
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: (() -> Unit)? = null, onBackupClick: (() -> Unit)? = null, onDuressClick: (() -> Unit)? = null, onChangePinClick: (() -> Unit)? = null, onRerunWizardClick: (() -> Unit)? = null, onOcrLanguagesClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val crypto = remember { CryptoManager(context).also { it.initialize() } }
    val vault = remember { VaultRepository(context, crypto) }
    val noteRepo = remember { NoteRepository(File(context.filesDir, "vault/notes"), crypto) }

    val storageInfo = remember { StorageManager.getStorageInfo(context) }
    var deviceProfile by remember { mutableStateOf(DeviceProfiler.getProfile(context)) }
    val noteCount = remember { noteRepo.noteCount() }
    val photoCount = remember {
        VaultCategory.entries.filter { it != VaultCategory.FILES }
            .sumOf { vault.listPhotos(it).size }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
            },
            // Re-run calibration wizard top-bar action removed 2026-05-13 —
            // the wizard still lives in Settings → Advanced for users who want
            // to repeat it. Top-bar surfaced it too prominently for a rarely-
            // used flow.
        )
    }) { padding ->
        var searchQuery by remember { mutableStateOf("") }

        fun matchesSearch(vararg texts: String): Boolean {
            if (searchQuery.isBlank()) return true
            return texts.any { it.contains(searchQuery, ignoreCase = true) }
        }

        // Hoisted theme + language state so both the Essentials section
        // (pinned at the top) and the Device section can trigger the same
        // dialog without duplicating it.
        var showThemeDialog by remember { mutableStateOf(false) }
        var currentThemeMode by remember { mutableStateOf(com.privateai.camera.ui.theme.ThemePreference.mode) }
        val themeNames = mapOf(
            com.privateai.camera.ui.theme.ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
            com.privateai.camera.ui.theme.ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
            com.privateai.camera.ui.theme.ThemeMode.DARK to stringResource(R.string.settings_theme_dark)
        )

        var showLanguageDialog by remember { mutableStateOf(false) }
        var currentLang by remember {
            mutableStateOf(
                context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .getString("language", "system") ?: "system"
            )
        }
        val langNames = mapOf(
            "system" to stringResource(R.string.settings_language_system),
            "en" to "English",
            "ar" to "العربية",
            "es" to "Español",
            "fr" to "Français",
            "tr" to "Türkçe",
            "zh" to "中文"
        )

        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = { Text(stringResource(R.string.settings_theme)) },
                text = {
                    Column {
                        themeNames.forEach { (mode, name) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentThemeMode = mode
                                        com.privateai.camera.ui.theme.ThemePreference.set(context, mode)
                                        showThemeDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = currentThemeMode == mode,
                                    onClick = {
                                        currentThemeMode = mode
                                        com.privateai.camera.ui.theme.ThemePreference.set(context, mode)
                                        showThemeDialog = false
                                    }
                                )
                                Text(name, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text(stringResource(R.string.settings_language)) },
                text = {
                    Column {
                        langNames.forEach { (code, name) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentLang = code
                                        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                                            .edit().putString("language", code).apply()
                                        if (code == "system") {
                                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                                        } else {
                                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                                        }
                                        showLanguageDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = currentLang == code,
                                    onClick = {
                                        currentLang = code
                                        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                                            .edit().putString("language", code).apply()
                                        if (code == "system") {
                                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                                        } else {
                                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                                        }
                                        showLanguageDialog = false
                                    }
                                )
                                Text(name, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.settings_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search)) },
                singleLine = true
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {

            // ─── Essentials section (always visible, no collapse toggle) ──────
            //
            // Pinned at the top of Settings so the most common controls are
            // one tap away. Same prefs / dialogs as the deeper sections —
            // these are just shortcuts. Hidden when a search query filters
            // them out so the search results stay focused on matches.
            val showEssentials = matchesSearch("Essentials", "Theme", "Dark", "Light", "Language", "AI", "Auto-tag", "AI labels", "Layout", "Grid", "Tabs", "Performance", "tier", "Device", "Screen lock", "Auto-lock", "Screenshot", "Recording")
            if (showEssentials && !VaultLockManager.isDuressActive) {
                SectionHeader(stringResource(R.string.settings_section_essentials))

                // Layout selector (Grid vs Tabs) — first item: most users want
                // to choose Grid or Tabs on day one and never touch it again.
                var currentLayout by remember { mutableStateOf(FeatureToggleManager.getHomeLayout(context)) }
                Text(
                    stringResource(R.string.settings_layout_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LayoutOption(
                        selected = currentLayout == HomeLayout.GRID,
                        title = stringResource(R.string.settings_layout_grid),
                        description = stringResource(R.string.settings_layout_grid_desc),
                        preview = "⊞",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            FeatureToggleManager.setHomeLayout(context, HomeLayout.GRID)
                            currentLayout = HomeLayout.GRID
                        }
                    )
                    LayoutOption(
                        selected = currentLayout == HomeLayout.TABS,
                        title = stringResource(R.string.settings_layout_tabs),
                        description = stringResource(R.string.settings_layout_tabs_desc),
                        preview = "≡",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            FeatureToggleManager.setHomeLayout(context, HomeLayout.TABS)
                            currentLayout = HomeLayout.TABS
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))

                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_performance_tier, deviceProfile.tier),
                    subtitle = DeviceProfiler.getTierDescription(deviceProfile.tier)
                )

                SettingsItem(
                    icon = Icons.Default.DarkMode,
                    title = stringResource(R.string.settings_theme),
                    subtitle = themeNames[currentThemeMode] ?: stringResource(R.string.settings_theme_system),
                    onClick = { showThemeDialog = true }
                )

                SettingsItem(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.settings_language),
                    subtitle = langNames[currentLang] ?: stringResource(R.string.settings_language_system),
                    onClick = { showLanguageDialog = true }
                )

                // (AI toggles moved into the dedicated AI section below.
                // Essentials only carries general settings now — anything
                // Gemma-related lives in one place so the user has a single
                // truth for AI state.)

                // Screen lock + screenshot protection — both also appear in the
                // Security section (same prefs). Surfacing them here saves a
                // collapse-tap for the most-used security controls.
                ScreenshotProtectionSetting(context)
                GracePeriodSetting(context)

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }

            // ─── AI section ─────────────────────────────────────────────
            // Everything Gemma-related lives here, in one collapsible block,
            // out of the PIN-gated Advanced. The main switch is what flips
            // the app's AI surface on/off; when AI isn't READY the only row
            // visible is the switch itself (plus a status hint), so the user
            // never sees a wall of disabled controls.
            val showAi = matchesSearch(
                "AI", "AI Assistant", "Gemma", "AI model", "Auto-tag", "AI labels",
                "Voice", "Smart scan", "Read aloud", "TTS", "Process all", "Delete AI",
                "Allow Assistant", "Locked assistant"
            )
            if (showAi) {
                val expandedAiSection = rememberSectionExpansion(context, "ai_main")
                val effExpandedAiSection = expandedAiSection.value || searchQuery.isNotBlank()
                CollapsibleSectionHeader(
                    stringResource(R.string.settings_section_ai),
                    effExpandedAiSection
                ) { expandedAiSection.value = !expandedAiSection.value }
                if (effExpandedAiSection) {

                    // Main AI Assistant toggle — moved out of Advanced. Local
                    // state mirrors GemmaRunner; the SharedPrefs listener in
                    // AiStatus.kt picks up the same flip and rebuilds every
                    // other AI-conditional surface across the app.
                    var aiEnabled by remember { mutableStateOf(com.privateai.camera.bridge.GemmaRunner.isEnabled(context)) }
                    var aiModelDownloaded by remember { mutableStateOf(com.privateai.camera.bridge.GemmaRunner.isModelDownloaded(context)) }
                    var showAiDownloadDialog by remember { mutableStateOf(false) }
                    val aiModelSize = remember(aiModelDownloaded) { com.privateai.camera.bridge.GemmaRunner.getModelSizeBytes(context) }
                    val downloadState by com.privateai.camera.bridge.GemmaModelManager.downloadState.collectAsState()

                    androidx.compose.runtime.LaunchedEffect(downloadState) {
                        when (downloadState) {
                            is com.privateai.camera.bridge.GemmaModelManager.DownloadState.Complete -> {
                                aiModelDownloaded = true
                            }
                            is com.privateai.camera.bridge.GemmaModelManager.DownloadState.Error -> {
                                com.privateai.camera.bridge.GemmaRunner.setEnabled(context, false)
                                aiEnabled = false
                                aiModelDownloaded = false
                            }
                            else -> {}
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                if (!aiEnabled) {
                                    if (aiModelDownloaded) {
                                        com.privateai.camera.bridge.GemmaRunner.resetCrashFlag(context)
                                        com.privateai.camera.bridge.GemmaRunner.resetVisionCrashFlag(context)
                                        com.privateai.camera.bridge.GemmaRunner.setEnabled(context, true)
                                        aiEnabled = true
                                    } else {
                                        showAiDownloadDialog = true
                                    }
                                } else {
                                    com.privateai.camera.bridge.GemmaRunner.setEnabled(context, false)
                                    com.privateai.camera.bridge.GemmaRunner.unload()
                                    com.privateai.camera.bridge.GemmaModelManager.cancelDownload(context)
                                    aiEnabled = false
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome, null, Modifier.size(24.dp),
                            tint = if (aiEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_ai_assistant_title), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when {
                                    aiEnabled && aiModelDownloaded -> stringResource(R.string.settings_ai_assistant_enabled, StorageManager.formatSize(aiModelSize))
                                    aiEnabled -> stringResource(R.string.settings_ai_assistant_downloading)
                                    else -> stringResource(R.string.settings_ai_assistant_off)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (downloadState is com.privateai.camera.bridge.GemmaModelManager.DownloadState.Downloading) {
                                val dl = downloadState as com.privateai.camera.bridge.GemmaModelManager.DownloadState.Downloading
                                val pct = if (dl.totalBytes > 0) (dl.progressBytes.toFloat() / dl.totalBytes) else 0f
                                val pctInt = (pct * 100).toInt()
                                Spacer(Modifier.height(6.dp))
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { pct },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "$pctInt%  —  ${StorageManager.formatSize(dl.progressBytes)} / ${StorageManager.formatSize(dl.totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (downloadState is com.privateai.camera.bridge.GemmaModelManager.DownloadState.Error) {
                                val err = downloadState as com.privateai.camera.bridge.GemmaModelManager.DownloadState.Error
                                Spacer(Modifier.height(4.dp))
                                Text(err.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        com.privateai.camera.bridge.GemmaRunner.setEnabled(context, true)
                                        aiEnabled = true
                                        com.privateai.camera.bridge.GemmaModelManager.startDownload(context)
                                    }) { Text(stringResource(R.string.action_retry)) }
                                }
                            }
                        }
                        Switch(checked = aiEnabled, onCheckedChange = null)
                    }

                    if (showAiDownloadDialog) {
                        val profiler = remember { com.privateai.camera.service.DeviceProfiler.getProfile(context) }
                        val freeStorage = remember { storageInfo.deviceFreeBytes }
                        val totalRamMb = profiler.ramMb
                        val requiredStorageBytes = 3_000_000_000L
                        val requiredRamMb = 4000
                        val hasEnoughStorage = freeStorage >= requiredStorageBytes
                        val hasEnoughRam = totalRamMb >= requiredRamMb
                        val canProceed = hasEnoughStorage

                        AlertDialog(
                            onDismissRequest = { showAiDownloadDialog = false },
                            title = { Text(stringResource(R.string.settings_ai_dialog_title)) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.settings_ai_dialog_intro))
                                    Text(stringResource(R.string.settings_ai_dialog_feature_summarize), style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.settings_ai_dialog_feature_grammar), style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.settings_ai_dialog_feature_photos), style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(4.dp))
                                    Text(stringResource(R.string.settings_ai_dialog_storage_label), fontWeight = FontWeight.Medium)
                                    Text(
                                        stringResource(R.string.settings_ai_dialog_storage_line, StorageManager.formatSize(freeStorage)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (hasEnoughStorage) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                    )
                                    if (!hasEnoughStorage) {
                                        Text(
                                            stringResource(R.string.settings_ai_dialog_storage_low, StorageManager.formatSize(requiredStorageBytes - freeStorage)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(stringResource(R.string.settings_ai_dialog_memory_label), fontWeight = FontWeight.Medium)
                                    Text(
                                        stringResource(R.string.settings_ai_dialog_memory_line, totalRamMb),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (hasEnoughRam) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                    )
                                    if (!hasEnoughRam) {
                                        Text(stringResource(R.string.settings_ai_dialog_memory_low), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                    if (totalRamMb in requiredRamMb..5999) {
                                        Text(stringResource(R.string.settings_ai_dialog_memory_recommended), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showAiDownloadDialog = false
                                        com.privateai.camera.bridge.GemmaRunner.setEnabled(context, true)
                                        aiEnabled = true
                                        if (!aiModelDownloaded) {
                                            com.privateai.camera.bridge.GemmaModelManager.startDownload(context)
                                        }
                                    },
                                    enabled = canProceed
                                ) { Text(stringResource(if (canProceed) R.string.settings_ai_dialog_confirm else R.string.settings_ai_dialog_confirm_blocked)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAiDownloadDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                            }
                        )
                    }

                    // Status-conditional sub-toggles + actions.
                    val aiStatusForSection by com.privateai.camera.bridge.rememberAiStatus()
                    when (aiStatusForSection) {
                        com.privateai.camera.bridge.AiStatus.READY -> {
                            AppSettingToggle(
                                context = context,
                                key = "show_ai_labels",
                                title = stringResource(R.string.settings_show_ai_labels),
                                subtitle = stringResource(R.string.settings_show_ai_labels_desc),
                                defaultValue = true
                            )
                            AppSettingToggle(
                                context = context,
                                key = "auto_ai_tag_new_photos",
                                title = stringResource(R.string.settings_auto_ai_tag),
                                subtitle = stringResource(R.string.settings_auto_ai_tag_desc),
                                defaultValue = false
                            )
                            AppSettingToggle(
                                context = context,
                                key = "detect_tts",
                                title = stringResource(R.string.settings_detect_tts),
                                subtitle = stringResource(R.string.settings_detect_tts_desc),
                                defaultValue = false
                            )
                            AppSettingToggle(
                                context = context,
                                key = "smart_scan_enabled",
                                title = stringResource(R.string.settings_smart_scan),
                                subtitle = stringResource(R.string.settings_smart_scan_desc),
                                defaultValue = false
                            )
                            AppSettingToggle(
                                context = context,
                                key = "voice_output_enabled",
                                title = stringResource(R.string.settings_voice_output),
                                subtitle = stringResource(R.string.settings_voice_output_desc),
                                defaultValue = false
                            )
                            AppSettingToggle(
                                context = context,
                                key = "assistant_unlocked_access",
                                title = stringResource(R.string.settings_assistant_unlocked_access),
                                subtitle = stringResource(R.string.settings_assistant_unlocked_access_desc),
                                defaultValue = false
                            )

                            // Process all photos with AI (moved from Advanced).
                            var showProcessAllDialog by remember { mutableStateOf(false) }
                            var pendingCounts by remember {
                                mutableStateOf(com.privateai.camera.service.GemmaIndexingManager.PendingCounts(0, 0, 0))
                            }
                            var selectedMode by remember {
                                mutableStateOf(com.privateai.camera.service.GemmaIndexingManager.ProcessMode.BOTH)
                            }
                            var selectedLimit by remember { mutableStateOf(Int.MAX_VALUE) }
                            val gemmaRunning by com.privateai.camera.service.GemmaIndexingManager.isRunning.collectAsState()
                            val gemmaProgress by com.privateai.camera.service.GemmaIndexingManager.progress.collectAsState()

                            LaunchedEffect(gemmaRunning) {
                                if (!gemmaRunning) {
                                    pendingCounts = withContext(Dispatchers.IO) {
                                        com.privateai.camera.service.GemmaIndexingManager.countPending(context)
                                    }
                                }
                            }

                            SettingsItem(
                                icon = Icons.Default.AutoAwesome,
                                title = if (gemmaRunning) {
                                    stringResource(R.string.settings_process_all_ai_progress, gemmaProgress.first, gemmaProgress.second)
                                } else {
                                    stringResource(R.string.settings_process_all_ai)
                                },
                                subtitle = if (!gemmaRunning && pendingCounts.both > 0) {
                                    stringResource(R.string.settings_process_all_ai_pending, pendingCounts.both)
                                } else {
                                    stringResource(R.string.settings_process_all_ai_desc)
                                },
                                onClick = {
                                    if (gemmaRunning) {
                                        com.privateai.camera.service.GemmaIndexingManager.stop()
                                    } else {
                                        scope.launch {
                                            pendingCounts = withContext(Dispatchers.IO) {
                                                com.privateai.camera.service.GemmaIndexingManager.countPending(context)
                                            }
                                            selectedMode = com.privateai.camera.service.GemmaIndexingManager.ProcessMode.BOTH
                                            showProcessAllDialog = true
                                        }
                                    }
                                }
                            )

                            if (showProcessAllDialog) {
                                val modeAvailableCount = when (selectedMode) {
                                    com.privateai.camera.service.GemmaIndexingManager.ProcessMode.DESCRIPTION_ONLY -> pendingCounts.description
                                    com.privateai.camera.service.GemmaIndexingManager.ProcessMode.TAGS_ONLY -> pendingCounts.tags
                                    com.privateai.camera.service.GemmaIndexingManager.ProcessMode.BOTH -> pendingCounts.both
                                }
                                val selectedCount = minOf(modeAvailableCount, selectedLimit)
                                val perPhotoSec = if (selectedMode == com.privateai.camera.service.GemmaIndexingManager.ProcessMode.BOTH) 10 else 5
                                val etaMin = (selectedCount * perPhotoSec + 59) / 60
                                AlertDialog(
                                    onDismissRequest = { showProcessAllDialog = false },
                                    title = { Text(stringResource(R.string.settings_process_all_ai)) },
                                    text = {
                                        Column {
                                            Text(
                                                stringResource(R.string.settings_process_all_ai_pick_mode),
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                            ModeRow(
                                                label = stringResource(R.string.settings_process_mode_description),
                                                count = pendingCounts.description,
                                                selected = selectedMode == com.privateai.camera.service.GemmaIndexingManager.ProcessMode.DESCRIPTION_ONLY,
                                                onClick = { selectedMode = com.privateai.camera.service.GemmaIndexingManager.ProcessMode.DESCRIPTION_ONLY }
                                            )
                                            ModeRow(
                                                label = stringResource(R.string.settings_process_mode_tags),
                                                count = pendingCounts.tags,
                                                selected = selectedMode == com.privateai.camera.service.GemmaIndexingManager.ProcessMode.TAGS_ONLY,
                                                onClick = { selectedMode = com.privateai.camera.service.GemmaIndexingManager.ProcessMode.TAGS_ONLY }
                                            )
                                            ModeRow(
                                                label = stringResource(R.string.settings_process_mode_both),
                                                count = pendingCounts.both,
                                                selected = selectedMode == com.privateai.camera.service.GemmaIndexingManager.ProcessMode.BOTH,
                                                onClick = { selectedMode = com.privateai.camera.service.GemmaIndexingManager.ProcessMode.BOTH }
                                            )
                                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                            Text(
                                                stringResource(R.string.settings_process_all_ai_pick_limit),
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                            val limitOptions = listOf(25, 100, 500, Int.MAX_VALUE)
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                limitOptions.forEach { lim ->
                                                    val label = if (lim == Int.MAX_VALUE)
                                                        stringResource(R.string.settings_process_limit_all) else lim.toString()
                                                    FilterChip(
                                                        selected = selectedLimit == lim,
                                                        onClick = { selectedLimit = lim },
                                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                                    )
                                                }
                                            }
                                            Text(
                                                if (selectedLimit == Int.MAX_VALUE)
                                                    stringResource(R.string.settings_process_all_ai_eta, etaMin)
                                                else
                                                    stringResource(R.string.settings_process_all_ai_eta_capped, selectedCount, etaMin),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 8.dp)
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                showProcessAllDialog = false
                                                com.privateai.camera.service.GemmaIndexingManager.processAll(context, selectedMode, selectedLimit)
                                            },
                                            enabled = selectedCount > 0
                                        ) { Text(stringResource(R.string.action_process)) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showProcessAllDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                                    }
                                )
                            }

                            // Delete AI model — destructive, but it's just data,
                            // so no PIN gate. Visible only when the model is on
                            // disk.
                            if (aiModelDownloaded) {
                                SettingsItem(
                                    icon = Icons.Default.Delete,
                                    title = stringResource(R.string.settings_ai_delete_title),
                                    subtitle = stringResource(R.string.settings_ai_delete_subtitle, StorageManager.formatSize(aiModelSize)),
                                    onClick = {
                                        com.privateai.camera.bridge.GemmaRunner.deleteModel(context)
                                        com.privateai.camera.bridge.GemmaRunner.setEnabled(context, false)
                                        aiEnabled = false
                                        aiModelDownloaded = false
                                    }
                                )
                            }
                        }
                        com.privateai.camera.bridge.AiStatus.DOWNLOADING -> {
                            Text(
                                stringResource(R.string.settings_ai_section_downloading_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        com.privateai.camera.bridge.AiStatus.OFF,
                        com.privateai.camera.bridge.AiStatus.FAILED -> {
                            Text(
                                stringResource(R.string.settings_ai_section_off_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                } // end effExpandedAiSection
            } // end showAi

            // Features section
            val showFeatures = matchesSearch("Home Screen Features", "Camera", "Detect", "Scan", "QR Scan", "Translate", "Vault", "Notes", "Insights", "Tools", "reorder")
            if (showFeatures) {
            val expandedFeatures = rememberSectionExpansion(context, "features")
            val effExpandedFeatures = expandedFeatures.value || searchQuery.isNotBlank()
            CollapsibleSectionHeader(stringResource(R.string.settings_section_home_features), effExpandedFeatures) {
                expandedFeatures.value = !expandedFeatures.value
            }
            if (effExpandedFeatures) {

            val featureInfo = mapOf(
                "camera" to Triple(stringResource(R.string.feature_camera), stringResource(R.string.feature_camera_desc), Icons.Default.CameraAlt),
                "detect" to Triple(stringResource(R.string.feature_detect), stringResource(R.string.feature_detect_desc), Icons.Default.Search),
                "scan" to Triple(stringResource(R.string.feature_scan), stringResource(R.string.feature_scan_desc), Icons.Default.DocumentScanner),
                "qrscanner" to Triple(stringResource(R.string.feature_qr_scan), stringResource(R.string.feature_qr_scan_desc), Icons.Default.Search),
                "translate" to Triple(stringResource(R.string.feature_translate), stringResource(R.string.feature_translate_desc), Icons.Default.Translate),
                "vault" to Triple(stringResource(R.string.feature_vault), stringResource(R.string.feature_vault_desc), Icons.Default.Lock),
                "notes" to Triple(stringResource(R.string.feature_notes), stringResource(R.string.feature_notes_desc), Icons.Default.NoteAlt),
                "insights" to Triple(stringResource(R.string.feature_insights), stringResource(R.string.feature_insights_desc), Icons.Default.Info),
                "reminders" to Triple(stringResource(R.string.feature_reminders), stringResource(R.string.feature_reminders_desc), Icons.Default.Notifications),
                "passwords" to Triple(stringResource(R.string.feature_passwords), stringResource(R.string.feature_passwords_desc), Icons.Default.Lock),
                "totp" to Triple(stringResource(R.string.feature_authenticator), stringResource(R.string.feature_authenticator_desc), Icons.Default.LockClock),
                "tools" to Triple(stringResource(R.string.feature_tools), stringResource(R.string.feature_tools_desc), Icons.Default.Info),
                "contacts" to Triple(stringResource(R.string.feature_contacts), stringResource(R.string.feature_contacts_desc), Icons.Default.Person)
            )
            var featureOrder by remember { mutableStateOf(FeatureToggleManager.getOrderedFeatures(context)) }

            featureOrder.forEachIndexed { index, route ->
                val (title, desc, icon) = featureInfo[route] ?: return@forEachIndexed
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Move up/down
                    Column(modifier = Modifier.padding(end = 4.dp)) {
                        IconButton(onClick = {
                            if (index > 0) {
                                featureOrder = featureOrder.toMutableList().apply {
                                    val item = removeAt(index); add(index - 1, item)
                                }
                                FeatureToggleManager.saveOrder(context, featureOrder)
                            }
                        }, modifier = Modifier.size(24.dp), enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.action_move_up), Modifier.size(18.dp), tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                        }
                        IconButton(onClick = {
                            if (index < featureOrder.size - 1) {
                                featureOrder = featureOrder.toMutableList().apply {
                                    val item = removeAt(index); add(index + 1, item)
                                }
                                FeatureToggleManager.saveOrder(context, featureOrder)
                            }
                        }, modifier = Modifier.size(24.dp), enabled = index < featureOrder.size - 1) {
                            Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.action_move_down), Modifier.size(18.dp), tint = if (index < featureOrder.size - 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                        }
                    }
                    // Feature toggle
                    var enabled by remember { mutableStateOf(FeatureToggleManager.isFeatureEnabled(context, route)) }
                    Row(
                        modifier = Modifier.weight(1f).clickable { enabled = !enabled; FeatureToggleManager.setFeatureEnabled(context, route, enabled) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(icon, null, Modifier.size(24.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.bodyLarge)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it; FeatureToggleManager.setFeatureEnabled(context, route, it) })
                    }
                }
            }

            Text(
                stringResource(R.string.settings_reorder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            } // end effExpandedFeatures
            } // end showFeatures

            // Object Detection — knobs for the always-on ONNX YOLOv8n
            // classifier. All Gemma-related toggles (show AI labels, auto-tag,
            // smart scan, voice output, etc.) moved to the dedicated AI
            // section above. What's left here is just the classifier's own
            // controls, which run regardless of whether Gemma is enabled.
            val showObjectDetection = matchesSearch("Object Detection", "Detection", "Confidence", "Detection Categories", "categories")
            if (showObjectDetection) {
            val expandedAi = rememberSectionExpansion(context, "ai_detection")
            val effExpandedAi = expandedAi.value || searchQuery.isNotBlank()
            CollapsibleSectionHeader(stringResource(R.string.settings_section_object_detection), effExpandedAi) {
                expandedAi.value = !expandedAi.value
            }
            if (effExpandedAi) {

            var showCategoriesDialog by remember { mutableStateOf(false) }
            var categoryCount by remember { mutableStateOf(getSelectedCategories(context).size) }
            var confidencePercent by remember { mutableStateOf(getConfidencePercent(context)) }

            // Confidence threshold
            SettingsItem(
                icon = Icons.Default.Search,
                title = stringResource(R.string.settings_min_confidence, confidencePercent),
                subtitle = stringResource(R.string.settings_min_confidence_desc),
                onClick = {
                    confidencePercent = when {
                        confidencePercent < 25 -> 25
                        confidencePercent < 35 -> 35
                        confidencePercent < 45 -> 45
                        confidencePercent < 55 -> 55
                        confidencePercent < 65 -> 65
                        confidencePercent < 75 -> 75
                        else -> 15
                    }
                    saveConfidenceThreshold(context, confidencePercent)
                }
            )

            // Categories
            SettingsItem(
                icon = Icons.Default.Search,
                title = stringResource(R.string.settings_detection_categories),
                subtitle = stringResource(R.string.settings_detection_categories_desc, categoryCount),
                onClick = { showCategoriesDialog = true }
            )

            if (showCategoriesDialog) {
                DetectionCategoriesDialog(context = context, onDismiss = {
                    showCategoriesDialog = false
                    categoryCount = getSelectedCategories(context).size
                })
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            } // end effExpandedAi
            } // end showObjectDetection

            // Device section
            val showDevice = matchesSearch("Device", "Performance Tier", "Device Info", "Re-benchmark", "benchmark", "Language")
            if (showDevice) {
            val expandedDevice = rememberSectionExpansion(context, "device")
            val effExpandedDevice = expandedDevice.value || searchQuery.isNotBlank()
            CollapsibleSectionHeader(stringResource(R.string.settings_section_device), effExpandedDevice) {
                expandedDevice.value = !expandedDevice.value
            }
            if (effExpandedDevice) {

            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_performance_tier, deviceProfile.tier),
                subtitle = DeviceProfiler.getTierDescription(deviceProfile.tier)
            )

            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_device_info),
                subtitle = stringResource(R.string.settings_device_info_desc, deviceProfile.cpuCores, deviceProfile.ramMb, deviceProfile.inferenceMs)
            )

            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_rebenchmark),
                subtitle = stringResource(R.string.settings_rebenchmark_desc),
                onClick = {
                    Toast.makeText(context, context.getString(R.string.settings_running_benchmark), Toast.LENGTH_SHORT).show()
                    deviceProfile = DeviceProfiler.runBenchmark(context)
                    Toast.makeText(context, context.getString(R.string.settings_benchmark_complete), Toast.LENGTH_SHORT).show()
                }
            )

            // Theme + Language: state + dialogs hoisted to SettingsScreen top
            // so the Essentials section can trigger the same picker without
            // duplicating it. Only the row entries live here.
            SettingsItem(
                icon = Icons.Default.DarkMode,
                title = stringResource(R.string.settings_theme),
                subtitle = themeNames[currentThemeMode] ?: stringResource(R.string.settings_theme_system),
                onClick = { showThemeDialog = true }
            )

            SettingsItem(
                icon = Icons.Default.Language,
                title = stringResource(R.string.settings_language),
                subtitle = langNames[currentLang] ?: stringResource(R.string.settings_language_system),
                onClick = { showLanguageDialog = true }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            } // end effExpandedDevice
            } // end showDevice

            // Camera section
            val showCamera = matchesSearch("Camera", "Countdown", "Timer", "Self-timer", "Delay")
            if (showCamera) {
                val expandedCamera = rememberSectionExpansion(context, "camera")
                val effExpandedCamera = expandedCamera.value || searchQuery.isNotBlank()
                CollapsibleSectionHeader(stringResource(R.string.settings_section_camera), effExpandedCamera) {
                    expandedCamera.value = !expandedCamera.value
                }
                if (effExpandedCamera) {

                val cameraPref = remember { context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE) }
                var countdownSec by remember { mutableIntStateOf(cameraPref.getInt("countdown_seconds", 5)) }
                val options = listOf(3, 5, 10)

                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = stringResource(R.string.settings_countdown_timer),
                    subtitle = stringResource(R.string.settings_countdown_desc)
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { sec ->
                        val selected = countdownSec == sec
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    countdownSec = sec
                                    cameraPref.edit().putInt("countdown_seconds", sec).apply()
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                "${sec}s",
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Video auto-record duration
                var videoDurationSec by remember { mutableIntStateOf(cameraPref.getInt("countdown_video_seconds", 30)) }
                val videoOptions = listOf(5, 10, 15, 30)

                SettingsItem(
                    icon = Icons.Default.Videocam,
                    title = stringResource(R.string.settings_video_duration),
                    subtitle = stringResource(R.string.settings_video_duration_desc)
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    videoOptions.forEach { sec ->
                        val selected = videoDurationSec == sec
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    videoDurationSec = sec
                                    cameraPref.edit().putInt("countdown_video_seconds", sec).apply()
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                "${sec}s",
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Face grouping threshold slider
                var faceThreshold by remember { mutableStateOf(cameraPref.getFloat("face_threshold", 0.60f)) }
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "Face Grouping Sensitivity",
                    subtitle = "Lower = more faces per group, Higher = stricter matching (${(faceThreshold * 100).toInt()}%)"
                )
                androidx.compose.material3.Slider(
                    value = faceThreshold,
                    onValueChange = { faceThreshold = it },
                    onValueChangeFinished = {
                        cameraPref.edit().putFloat("face_threshold", faceThreshold).apply()
                    },
                    valueRange = 0.40f..0.80f,
                    steps = 7,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("More groups", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Fewer groups", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))

                // Reset face groups — wipes cluster identities + names +
                // contact links but PRESERVES per-face embeddings, AI tags,
                // and photo metadata. Cheap (3 DELETEs); the next Face
                // Groups screen open re-clusters from existing embeddings.
                // Useful after detector swaps (Track A1.2: ML Kit → ONNX)
                // or after a tangled merge/rename chain.
                var showResetFaceGroupsDialog by remember { mutableStateOf(false) }
                SettingsItem(
                    icon = Icons.Default.GroupRemove,
                    title = stringResource(R.string.settings_reset_face_groups),
                    subtitle = stringResource(R.string.settings_reset_face_groups_desc),
                    onClick = { showResetFaceGroupsDialog = true }
                )
                if (showResetFaceGroupsDialog) {
                    AlertDialog(
                        onDismissRequest = { showResetFaceGroupsDialog = false },
                        title = { Text(stringResource(R.string.settings_reset_face_groups)) },
                        text = { Text(stringResource(R.string.settings_reset_face_groups_confirm)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showResetFaceGroupsDialog = false
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val crypto = com.privateai.camera.security.CryptoManager(context).also { it.initialize() }
                                            val db = com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)
                                            com.privateai.camera.security.PhotoIndex(db).clearFaceGroups()
                                        } catch (e: Exception) {
                                            android.util.Log.e("Settings", "reset face groups failed: ${e.message}", e)
                                        }
                                    }
                                    Toast.makeText(context, context.getString(R.string.settings_reset_face_groups_done), Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text(stringResource(R.string.settings_reset_face_groups_action),
                                    color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResetFaceGroupsDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }
                // OCR languages — manage downloaded Tesseract .traineddata
                // files. Track A1.3: now that we own the OCR backend, the
                // user gets a per-language picker that ML Kit never offered.
                SettingsItem(
                    icon = Icons.Default.Translate,
                    title = stringResource(R.string.settings_ocr_languages),
                    subtitle = stringResource(R.string.settings_ocr_languages_desc),
                    onClick = { onOcrLanguagesClick?.invoke() }
                )
                Spacer(Modifier.height(8.dp))
                } // end effExpandedCamera
            }

            // Security section
            val showSecurity = matchesSearch("Security", "Encryption", "Screenshot Protection", "Emergency PIN", "Grace period", "Auto-lock")
            if (showSecurity) {
            val expandedSecurity = rememberSectionExpansion(context, "security")
            val effExpandedSecurity = expandedSecurity.value || searchQuery.isNotBlank()
            CollapsibleSectionHeader(stringResource(R.string.settings_section_security), effExpandedSecurity) {
                expandedSecurity.value = !expandedSecurity.value
            }
            if (effExpandedSecurity) {

            SettingsItem(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.settings_encryption),
                subtitle = stringResource(R.string.settings_encryption_desc)
            )

            ScreenshotProtectionSetting(context)

            GracePeriodSetting(context)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            } // end effExpandedSecurity
            } // end showSecurity

            // Storage section
            val showStorage = matchesSearch("Storage", "Vault", "Notes", "Cache", "Device Storage", "Clear Cache")
            if (showStorage) {
            val expandedStorage = rememberSectionExpansion(context, "storage")
            val effExpandedStorage = expandedStorage.value || searchQuery.isNotBlank()
            CollapsibleSectionHeader(stringResource(R.string.settings_section_storage), effExpandedStorage) {
                expandedStorage.value = !expandedStorage.value
            }
            if (effExpandedStorage) {

            SettingsItem(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.settings_vault),
                subtitle = stringResource(R.string.settings_vault_desc, StorageManager.formatSize(storageInfo.vaultSizeBytes), photoCount)
            )

            SettingsItem(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.settings_notes),
                subtitle = stringResource(R.string.settings_notes_desc, StorageManager.formatSize(storageInfo.notesSizeBytes), noteCount)
            )

            SettingsItem(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.settings_cache),
                subtitle = StorageManager.formatSize(storageInfo.cacheSizeBytes)
            )

            SettingsItem(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.settings_device_storage),
                subtitle = stringResource(R.string.settings_device_storage_desc, StorageManager.formatSize(storageInfo.deviceFreeBytes), StorageManager.formatSize(storageInfo.deviceTotalBytes), "%.0f".format(storageInfo.usagePercent))
            )

            if (storageInfo.deviceFreeBytes < 500 * 1024 * 1024) {
                Text(
                    stringResource(R.string.settings_storage_low_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            SettingsItem(
                icon = Icons.Default.Delete,
                title = stringResource(R.string.settings_clear_cache),
                subtitle = stringResource(R.string.settings_clear_cache_desc),
                onClick = {
                    val freed = StorageManager.clearCache(context)
                    Toast.makeText(context, context.getString(R.string.settings_cleared_size, StorageManager.formatSize(freed)), Toast.LENGTH_SHORT).show()
                }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            } // end effExpandedStorage
            } // end showStorage

            // Privacy section
            val showPrivacy = matchesSearch("Privacy", "EXIF", "Face Blur", "Network Policy", "Backup Exclusion", "Voice", "Noise")
            if (showPrivacy) {
            val expandedPrivacy = rememberSectionExpansion(context, "privacy")
            val effExpandedPrivacy = expandedPrivacy.value || searchQuery.isNotBlank()
            CollapsibleSectionHeader(stringResource(R.string.settings_section_privacy), effExpandedPrivacy) {
                expandedPrivacy.value = !expandedPrivacy.value
            }
            if (effExpandedPrivacy) {

            SettingsItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_exif_stripping),
                subtitle = stringResource(R.string.settings_exif_stripping_desc)
            )

            PrivacyToggle(
                context = context,
                key = "face_blur_on_share",
                title = stringResource(R.string.settings_face_blur),
                subtitle = stringResource(R.string.settings_face_blur_desc)
            )

            PrivacyToggle(
                context = context,
                key = "clean_voice_notes",
                title = stringResource(R.string.settings_clean_voice_notes),
                subtitle = stringResource(R.string.settings_clean_voice_notes_desc),
                defaultValue = true
            )

            SettingsItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_network_policy),
                subtitle = stringResource(R.string.settings_network_policy_desc)
            )

            SettingsItem(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.settings_backup_exclusion),
                subtitle = stringResource(R.string.settings_backup_exclusion_desc)
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            } // end effExpandedPrivacy
            } // end showPrivacy

            // (Backup & Migration moved to Advanced section)

            // (Device Transfer moved to Advanced section)

            // About section
            val showAbout = matchesSearch("About", "Privora", "Version", "Crash Logs", "Privacy Policy", "Privacy Promise")
            if (showAbout) {
            val expandedAbout = rememberSectionExpansion(context, "about")
            val effExpandedAbout = expandedAbout.value || searchQuery.isNotBlank()
            CollapsibleSectionHeader(stringResource(R.string.settings_section_about), effExpandedAbout) {
                expandedAbout.value = !expandedAbout.value
            }
            if (effExpandedAbout) {

            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_privo),
                subtitle = stringResource(R.string.settings_privo_desc, com.privateai.camera.BuildConfig.VERSION_NAME)
            )

            var crashLogs by remember { mutableStateOf(com.privateai.camera.service.CrashHandler.listLogs(context)) }
            var showCrashList by remember { mutableStateOf(false) }
            var viewingCrashLog by remember { mutableStateOf<com.privateai.camera.service.CrashLog?>(null) }

            SettingsItem(
                icon = Icons.Default.Info,
                title = if (crashLogs.isNotEmpty()) stringResource(R.string.settings_crash_logs, crashLogs.size)
                        else stringResource(R.string.settings_crash_logs_title),
                subtitle = if (crashLogs.isNotEmpty()) stringResource(R.string.settings_crash_logs_desc)
                           else "No crashes recorded",
                onClick = if (crashLogs.isNotEmpty()) { { showCrashList = true } } else null
            )

            // Crash log list dialog
            if (showCrashList) {
                val dateFmt = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()) }
                AlertDialog(
                    onDismissRequest = { showCrashList = false },
                    title = { Text(stringResource(R.string.settings_crash_logs_title)) },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            crashLogs.forEach { log ->
                                Card(Modifier.fillMaxWidth().clickable { viewingCrashLog = log; showCrashList = false }) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(dateFmt.format(java.util.Date(log.timestamp)), style = MaterialTheme.typography.bodyMedium)
                                        Text(log.preview.take(100), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            com.privateai.camera.service.CrashHandler.clearLogs(context)
                            crashLogs = emptyList()
                            showCrashList = false
                            Toast.makeText(context, context.getString(R.string.settings_crash_logs_cleared), Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.action_clear_all), color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { showCrashList = false }) { Text(stringResource(R.string.action_close)) } }
                )
            }

            // View single crash log dialog
            viewingCrashLog?.let { log ->
                val content = remember(log) { com.privateai.camera.service.CrashHandler.readLog(log.file) }
                AlertDialog(
                    onDismissRequest = { viewingCrashLog = null },
                    title = { Text(stringResource(R.string.settings_crash_report)) },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(content, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(R.string.settings_crash_report_subject))
                                putExtra(android.content.Intent.EXTRA_TEXT, content)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.action_share)))
                        }) { Text(stringResource(R.string.action_share)) }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                com.privateai.camera.service.CrashHandler.deleteLog(log.file)
                                crashLogs = com.privateai.camera.service.CrashHandler.listLogs(context)
                                viewingCrashLog = null
                                Toast.makeText(context, context.getString(R.string.settings_log_deleted), Toast.LENGTH_SHORT).show()
                            }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                            TextButton(onClick = { viewingCrashLog = null }) { Text(stringResource(R.string.action_close)) }
                        }
                    }
                )
            }

            SettingsItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_privacy_policy),
                subtitle = stringResource(R.string.settings_privacy_policy_desc),
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/robomixes/private-ai-camera/blob/main/PRIVACY.md"))
                    context.startActivity(intent)
                }
            )

            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_privacy_promise), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.settings_privacy_promise_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            } // end effExpandedAbout
            } // end showAbout

            // ─── Advanced Section (auth-gated, hidden during duress) ──────────
            if (!VaultLockManager.isDuressActive) {
                val showAdvanced = matchesSearch("Advanced", "Backup", "Emergency", "Transfer", "Wipe", "Re-index", "Export", "Import")
                if (showAdvanced) {
                    var advancedUnlocked by remember { mutableStateOf(false) }
                    var showAdvancedPinDialog by remember { mutableStateOf(false) }
                    var advPin by remember { mutableStateOf("") }
                    var advPinError by remember { mutableStateOf<String?>(null) }

                    val currentAuthMode = remember { getAuthMode(context) }

                    // Biometric auth for phone lock mode
                    val activity = context as? androidx.fragment.app.FragmentActivity
                    fun authenticateAdvanced() {
                        if (activity == null) return
                        val prompt = androidx.biometric.BiometricPrompt(activity, object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                advancedUnlocked = true
                            }
                        })
                        prompt.authenticate(
                            androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                .setTitle(context.getString(R.string.settings_advanced_unlock_title))
                                .setSubtitle(context.getString(R.string.settings_advanced_unlock_subtitle))
                                .setAllowedAuthenticators(
                                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                ).build()
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SectionHeader(stringResource(R.string.settings_advanced))

                    if (!advancedUnlocked) {
                        // Locked state — tap to authenticate
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (currentAuthMode == AuthMode.APP_PIN) {
                                        showAdvancedPinDialog = true
                                    } else {
                                        authenticateAdvanced()
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.Lock, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_advanced_tap_to_unlock), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.settings_advanced_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // PIN dialog for APP_PIN mode
                        if (showAdvancedPinDialog) {
                            AlertDialog(
                                onDismissRequest = { showAdvancedPinDialog = false; advPin = ""; advPinError = null },
                                title = { Text(stringResource(R.string.settings_advanced_unlock_title)) },
                                text = {
                                    Column {
                                        Text(stringResource(R.string.settings_advanced_pin_prompt), style = MaterialTheme.typography.bodySmall)
                                        Spacer(Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = advPin,
                                            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) { advPin = it; advPinError = null } },
                                            label = { Text(stringResource(R.string.enter_pin)) },
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                            isError = advPinError != null,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (advPinError != null) {
                                            Text(advPinError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        // Duress PIN check first
                                        if (com.privateai.camera.security.DuressManager.isEnabled(context) &&
                                            com.privateai.camera.security.DuressManager.isDuressPin(context, advPin)) {
                                            showAdvancedPinDialog = false
                                            advPin = ""
                                            VaultLockManager.activateDuress()
                                            scope.launch(Dispatchers.IO) {
                                                com.privateai.camera.security.DuressManager.executeDuress(context, crypto)
                                            }
                                            return@TextButton
                                        }
                                        // Rate limit check
                                        if (!PinRateLimiter.canAttempt(context)) {
                                            val remaining = PinRateLimiter.remainingLockoutMs(context)
                                            val seconds = (remaining / 1000).toInt()
                                            advPinError = context.getString(R.string.pin_locked_out, "%d:%02d".format(seconds / 60, seconds % 60))
                                            return@TextButton
                                        }
                                        if (AppPinManager.verify(context, advPin)) {
                                            PinRateLimiter.recordSuccess(context)
                                            advancedUnlocked = true
                                            showAdvancedPinDialog = false
                                            advPin = ""
                                            advPinError = null
                                        } else {
                                            PinRateLimiter.recordFailure(context)
                                            advPinError = context.getString(R.string.incorrect_pin)
                                            advPin = ""
                                        }
                                    }, enabled = advPin.length >= 4) { Text(stringResource(R.string.unlock)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showAdvancedPinDialog = false; advPin = ""; advPinError = null }) { Text(stringResource(R.string.action_cancel)) }
                                }
                            )
                        }
                    } else {
                        // ─── Unlocked: show all critical settings ───

                        // Wi-Fi Transfer size limit
                        val sizeOptions = listOf(50, 100, 250, 500, 1024)
                        val sizeLabels = listOf("50 MB", "100 MB", "250 MB", "500 MB", "1 GB")
                        var transferMaxMB by remember {
                            mutableStateOf(
                                context.getSharedPreferences("wifi_transfer", android.content.Context.MODE_PRIVATE)
                                    .getInt("max_file_mb", 100)
                            )
                        }
                        val currentSizeIdx = sizeOptions.indexOf(transferMaxMB).coerceAtLeast(0)
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.Info, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.wifi_transfer_size_title), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.wifi_transfer_size_desc, sizeLabels[currentSizeIdx]),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = {
                                    val idx = (currentSizeIdx - 1).coerceAtLeast(0)
                                    transferMaxMB = sizeOptions[idx]
                                    context.getSharedPreferences("wifi_transfer", android.content.Context.MODE_PRIVATE)
                                        .edit().putInt("max_file_mb", transferMaxMB).apply()
                                }, modifier = Modifier.size(32.dp)) { Text("−", style = MaterialTheme.typography.titleMedium) }
                                Text(sizeLabels[currentSizeIdx], style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = {
                                    val idx = (currentSizeIdx + 1).coerceAtMost(sizeOptions.size - 1)
                                    transferMaxMB = sizeOptions[idx]
                                    context.getSharedPreferences("wifi_transfer", android.content.Context.MODE_PRIVATE)
                                        .edit().putInt("max_file_mb", transferMaxMB).apply()
                                }, modifier = Modifier.size(32.dp)) { Text("+", style = MaterialTheme.typography.titleMedium) }
                            }
                        }

                        // Hidden folder tap count
                        var hiddenTapCount by remember {
                            mutableStateOf(
                                context.getSharedPreferences("vault_hidden", android.content.Context.MODE_PRIVATE)
                                    .getInt("tap_count", 7)
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.Lock, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.hidden_folder_title), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.hidden_folder_desc, hiddenTapCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = {
                                    if (hiddenTapCount > 3) {
                                        hiddenTapCount--
                                        context.getSharedPreferences("vault_hidden", android.content.Context.MODE_PRIVATE)
                                            .edit().putInt("tap_count", hiddenTapCount).apply()
                                    }
                                }, modifier = Modifier.size(32.dp)) {
                                    Text("−", style = MaterialTheme.typography.titleMedium)
                                }
                                Text("$hiddenTapCount", style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = {
                                    if (hiddenTapCount < 15) {
                                        hiddenTapCount++
                                        context.getSharedPreferences("vault_hidden", android.content.Context.MODE_PRIVATE)
                                            .edit().putInt("tap_count", hiddenTapCount).apply()
                                    }
                                }, modifier = Modifier.size(32.dp)) {
                                    Text("+", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }

                        // Calculator disguise — swap launcher icon
                        var disguiseEnabled by remember {
                            mutableStateOf(com.privateai.camera.ui.disguise.DisguiseManager.isDisguiseEnabled(context))
                        }
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.PhoneAndroid, null, Modifier.size(24.dp),
                                tint = if (disguiseEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.disguise_title), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (disguiseEnabled) stringResource(R.string.disguise_enabled_desc)
                                    else stringResource(R.string.disguise_disabled_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = disguiseEnabled, onCheckedChange = {
                                disguiseEnabled = it
                                com.privateai.camera.ui.disguise.DisguiseManager.setDisguiseEnabled(context, it)
                            })
                        }

                        // Change PIN — full-screen flow (current → new → confirm)
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onChangePinClick?.invoke() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.Lock, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.change_pin_title), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.change_pin_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Re-run setup wizard — re-routes to the calibration wizard
                        // for layout/modules/AI/duress reconfiguration.
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onRerunWizardClick?.invoke() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_rerun_wizard), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.settings_rerun_wizard_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Intruder alerts — front camera on wrong PIN
                        IntruderAlertsSetting(context)
                        Spacer(Modifier.height(8.dp))

                        // (AI Assistant toggle, download dialog, and Delete
                        // AI Model live in the dedicated top-level AI section
                        // now — no PIN gate for AI controls.)
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        // Emergency PIN
                        if (getAuthMode(context) == AuthMode.APP_PIN) {
                            if (com.privateai.camera.security.DuressManager.isEnabled(context)) {
                                SettingsItem(
                                    icon = Icons.Default.Security,
                                    title = stringResource(R.string.settings_emergency_pin),
                                    subtitle = stringResource(R.string.settings_emergency_pin_active),
                                    onClick = { onDuressClick?.invoke() }
                                )
                            } else {
                                SettingsItem(
                                    icon = Icons.Default.Security,
                                    title = stringResource(R.string.settings_set_emergency_pin),
                                    subtitle = stringResource(R.string.settings_set_emergency_pin_desc),
                                    onClick = { onDuressClick?.invoke() }
                                )
                            }
                        }

                        // Backup
                        SettingsItem(
                            icon = Icons.Default.CloudSync,
                            title = stringResource(R.string.settings_export_import),
                            subtitle = stringResource(R.string.settings_export_import_desc),
                            onClick = { onBackupClick?.invoke() }
                        )

                        Text(
                            stringResource(R.string.settings_backup_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        // Device Transfer
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.PhoneAndroid, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_transfer_title), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.settings_transfer_step1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.settings_transfer_step2), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.settings_transfer_step3), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        // Wipe
                        var showWipeDialog by remember { mutableStateOf(false) }
                        Row(
                            Modifier.fillMaxWidth().clickable { showWipeDialog = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.DeleteForever, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_wipe_title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                                Text(stringResource(R.string.settings_wipe_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                            }
                        }

                        if (showWipeDialog) {
                            AlertDialog(
                                onDismissRequest = { showWipeDialog = false },
                                title = { Text(stringResource(R.string.settings_wipe_dialog_title)) },
                                text = {
                                    Column {
                                        Text(stringResource(R.string.settings_wipe_dialog_intro))
                                        Spacer(Modifier.height(8.dp))
                                        Text(stringResource(R.string.settings_wipe_dialog_item_photos), style = MaterialTheme.typography.bodySmall)
                                        Text(stringResource(R.string.settings_wipe_dialog_item_notes), style = MaterialTheme.typography.bodySmall)
                                        Text(stringResource(R.string.settings_wipe_dialog_item_contacts), style = MaterialTheme.typography.bodySmall)
                                        Text(stringResource(R.string.settings_wipe_dialog_item_faces), style = MaterialTheme.typography.bodySmall)
                                        Text(stringResource(R.string.settings_wipe_dialog_item_keys), style = MaterialTheme.typography.bodySmall)
                                        Text(stringResource(R.string.settings_wipe_dialog_item_settings), style = MaterialTheme.typography.bodySmall)
                                        Spacer(Modifier.height(8.dp))
                                        Text(stringResource(R.string.settings_wipe_dialog_question), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showWipeDialog = false
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    com.privateai.camera.security.KeyManager.deleteKEK()
                                                    crypto.wipeAll()
                                                    File(context.filesDir, "vault").deleteRecursively()
                                                    context.getDatabasePath("privora.db")?.delete()
                                                    listOf("app_settings", "privacy_settings", "feature_toggles", "duress_settings", "privateai_prefs", "device_profile", "pin_rate_limiter", "qr_history").forEach { name ->
                                                        context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE).edit().clear().apply()
                                                    }
                                                    context.cacheDir.deleteRecursively()
                                                    crypto.initialize()
                                                } catch (_: Exception) {}
                                            }
                                            Toast.makeText(context, context.getString(R.string.settings_wipe_done_toast), Toast.LENGTH_LONG).show()
                                            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        }
                                    }) { Text(stringResource(R.string.settings_wipe_dialog_confirm), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showWipeDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                                }
                            )
                        }

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        // Re-index
                        var showReindexDialog by remember { mutableStateOf(false) }
                        var isReindexing by remember { mutableStateOf(false) }
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = !isReindexing) { showReindexDialog = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_reindex), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (isReindexing) stringResource(R.string.settings_reindex_running)
                                    else stringResource(R.string.settings_reindex_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (showReindexDialog) {
                            AlertDialog(
                                onDismissRequest = { showReindexDialog = false },
                                title = { Text(stringResource(R.string.settings_reindex)) },
                                text = { Text(stringResource(R.string.settings_reindex_confirm)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showReindexDialog = false
                                        isReindexing = true
                                        scope.launch {
                                            com.privateai.camera.service.IndexingManager.stop()
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    val c = CryptoManager(context).also { it.initialize() }
                                                    val db = com.privateai.camera.security.PrivoraDatabase.getInstance(context, c)
                                                    com.privateai.camera.security.PhotoIndex(db).clearIndex()
                                                } catch (_: Exception) {}
                                            }
                                            isReindexing = false
                                            com.privateai.camera.service.IndexingManager.startIndexing(context)
                                            Toast.makeText(context, context.getString(R.string.settings_reindex_done), Toast.LENGTH_SHORT).show()
                                        }
                                    }) { Text(stringResource(R.string.settings_reindex_action), color = MaterialTheme.colorScheme.error) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showReindexDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                                }
                            )
                        }

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        // ("Process all photos with AI" lives in the
                        // dedicated AI section now — same code, different
                        // home, gated on AiStatus.READY.)
                    }
                } // end showAdvanced
            } // end !isDuressActive

            Spacer(Modifier.height(16.dp))
            } // end inner scrollable Column
        } // end outer Column
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * Header row for a collapsible section. Whole row is clickable; trailing
 * chevron rotates to indicate expand/collapse state. Used by every Settings
 * section so the page opens short and the user expands what they need.
 */
@Composable
private fun CollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Per-section expand state persisted to SharedPreferences("settings_ui_state"),
 * so a section the user has opened stays open across launches. Caller is
 * responsible for combining the returned state with a search override (force
 * expand when the search query is non-blank).
 */
@Composable
private fun rememberSectionExpansion(
    context: Context,
    sectionKey: String
): androidx.compose.runtime.MutableState<Boolean> {
    val prefs = remember { context.getSharedPreferences("settings_ui_state", Context.MODE_PRIVATE) }
    val state = remember(sectionKey) {
        mutableStateOf(prefs.getBoolean("expanded_$sectionKey", false))
    }
    LaunchedEffect(state.value) {
        prefs.edit().putBoolean("expanded_$sectionKey", state.value).apply()
    }
    return state
}

@Composable
private fun LayoutOption(
    selected: Boolean,
    title: String,
    description: String,
    preview: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                  else MaterialTheme.colorScheme.surface
    Column(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            preview,
            style = MaterialTheme.typography.headlineMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = tint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FeatureToggle(
    context: android.content.Context,
    route: String,
    title: String,
    description: String,
    icon: ImageVector
) {
    var enabled by remember { mutableStateOf(FeatureToggleManager.isFeatureEnabled(context, route)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                enabled = !enabled
                FeatureToggleManager.setFeatureEnabled(context, route, enabled)
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = {
            enabled = it
            FeatureToggleManager.setFeatureEnabled(context, route, it)
        })
    }
}

@Composable
private fun PrivacyToggle(
    context: android.content.Context,
    key: String,
    title: String,
    subtitle: String,
    defaultValue: Boolean = false
) {
    val prefs = remember { context.getSharedPreferences("privacy_settings", android.content.Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(key, defaultValue)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { enabled = !enabled; prefs.edit().putBoolean(key, enabled).apply() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.Security, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = {
            enabled = it
            prefs.edit().putBoolean(key, it).apply()
        })
    }
}

fun isFaceBlurEnabled(context: android.content.Context): Boolean {
    return context.getSharedPreferences("privacy_settings", android.content.Context.MODE_PRIVATE)
        .getBoolean("face_blur_on_share", false)
}

@Composable
private fun AppSettingToggle(
    context: android.content.Context,
    key: String,
    title: String,
    subtitle: String,
    defaultValue: Boolean = false
) {
    val prefs = remember { context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(key, defaultValue)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { enabled = !enabled; prefs.edit().putBoolean(key, enabled).apply() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.Visibility, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = {
            enabled = it
            prefs.edit().putBoolean(key, it).apply()
        })
    }
}

fun isShowAiLabelsEnabled(context: android.content.Context): Boolean {
    return context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        .getBoolean("show_ai_labels", true)
}

fun isDetectTtsEnabled(context: android.content.Context): Boolean {
    return context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        .getBoolean("detect_tts", false)
}

fun isAutoAiTagEnabled(context: android.content.Context): Boolean {
    return context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        .getBoolean("auto_ai_tag_new_photos", false)
}

/**
 * True when the user has opted in to reaching the AI Assistant without
 * entering the vault PIN. Default OFF — the safe privacy default.
 *
 * In this mode the Assistant tile is visible on Home even when the vault
 * is locked, but the chat runs in text-only mode: snapshot is empty,
 * vault picker is disabled, and data tools (search_notes, search_photos,
 * etc.) refuse to run until the user explicitly unlocks.
 */
fun isAssistantUnlockedAccessEnabled(context: android.content.Context): Boolean {
    return context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        .getBoolean("assistant_unlocked_access", false)
}

@Composable
private fun ModeRow(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = count > 0, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = count > 0
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (count == 0) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (count == 0) stringResource(R.string.settings_process_mode_none_pending)
                else stringResource(R.string.settings_process_mode_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Block screenshots + screen recording (FLAG_SECURE) toggle. Extracted into a
 * reusable composable so both the Security section and the Essentials section
 * can mount it — they share the same `privacy_settings/block_screenshots`
 * pref so toggling in one updates the other on next recomposition.
 */
@Composable
private fun ScreenshotProtectionSetting(context: android.content.Context) {
    val prefs = remember { context.getSharedPreferences("privacy_settings", android.content.Context.MODE_PRIVATE) }
    var blocked by remember { mutableStateOf(prefs.getBoolean("block_screenshots", true)) }

    fun applyFlag(value: Boolean) {
        prefs.edit().putBoolean("block_screenshots", value).apply()
        val activity = context as? android.app.Activity
        if (value) {
            activity?.window?.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                blocked = !blocked
                applyFlag(blocked)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Security, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(stringResource(R.string.settings_screenshot_protection), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (blocked) stringResource(R.string.settings_screenshot_protection_desc)
                else stringResource(R.string.settings_screenshot_protection_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = blocked, onCheckedChange = {
            blocked = it
            applyFlag(it)
        })
    }
}

@Composable
private fun GracePeriodSetting(context: android.content.Context) {
    val prefs = remember { context.getSharedPreferences("privacy_settings", android.content.Context.MODE_PRIVATE) }
    val options = listOf(0, 10, 30, 60, 120, 300)
    val labels = listOf(stringResource(R.string.grace_immediately), stringResource(R.string.grace_10_seconds), stringResource(R.string.grace_30_seconds), stringResource(R.string.grace_1_minute), stringResource(R.string.grace_2_minutes), stringResource(R.string.grace_5_minutes))
    var currentValue by remember { mutableStateOf(prefs.getInt("lock_grace_seconds", 30)) }
    var expanded by remember { mutableStateOf(false) }

    val currentLabel = labels[options.indexOf(currentValue).coerceAtLeast(0)]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_autolock_delay), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.settings_lock_vault_after, currentLabel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (expanded) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(stringResource(R.string.settings_autolock_delay)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_autolock_description), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))
                    options.forEachIndexed { index, value ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentValue = value
                                    prefs.edit().putInt("lock_grace_seconds", value).apply()
                                    expanded = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = currentValue == value,
                                onClick = {
                                    currentValue = value
                                    prefs.edit().putInt("lock_grace_seconds", value).apply()
                                    expanded = false
                                }
                            )
                            Text(labels[index], modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }
}

/**
 * Intruder Alerts setting: toggle + list of captured photos from wrong PIN attempts.
 * Matches the Advanced section's Row layout pattern (icon + column + switch).
 */
@Composable
private fun IntruderAlertsSetting(context: android.content.Context) {
    var enabled by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(com.privateai.camera.security.IntruderCapture.isEnabled(context))
    }
    var showViewer by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    // Main toggle row — same padding/spacing as AI Assistant and other Advanced items
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Security, null, Modifier.size(24.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.intruder_alerts_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (enabled) {
                    val count = com.privateai.camera.security.IntruderCapture.listCaptures(context).size
                    if (count > 0) stringResource(R.string.intruder_view_captures, count)
                    else stringResource(R.string.intruder_alerts_desc)
                } else {
                    stringResource(R.string.intruder_alerts_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = {
            enabled = it
            com.privateai.camera.security.IntruderCapture.setEnabled(context, it)
        })
    }

    // Tap the row to view captures when enabled
    if (enabled) {
        val count = com.privateai.camera.security.IntruderCapture.listCaptures(context).size
        if (count > 0) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable { showViewer = true }
                    .padding(horizontal = 56.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.intruder_view_captures, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // Viewer dialog
    if (showViewer) {
        val captures = androidx.compose.runtime.remember { com.privateai.camera.security.IntruderCapture.listCaptures(context) }
        if (captures.isEmpty()) {
            showViewer = false
            return
        }
        val dateFmt = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", java.util.Locale.getDefault())
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showViewer = false },
            title = { Text(stringResource(R.string.intruder_alerts_title)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(captures.size) { i ->
                        val entry = captures[i]
                        val bitmap = androidx.compose.runtime.remember(entry.timestamp) {
                            com.privateai.camera.security.IntruderCapture.decryptCapture(context, entry)
                        }
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Text(
                                    dateFmt.format(java.util.Date(entry.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (bitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth().height(180.dp)
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Text("Could not load photo", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    com.privateai.camera.security.IntruderCapture.clearAll(context)
                    showViewer = false
                }) { Text(stringResource(R.string.intruder_clear_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showViewer = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

