// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.calibrate

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.privateai.camera.R
import com.privateai.camera.bridge.GemmaModelManager
import com.privateai.camera.bridge.GemmaRunner
import com.privateai.camera.security.DuressManager
import com.privateai.camera.service.DeviceProfiler
import com.privateai.camera.service.DeviceTier
import com.privateai.camera.service.StorageManager
import com.privateai.camera.ui.home.features as homeFeatures
import com.privateai.camera.ui.settings.FeatureToggleManager
import com.privateai.camera.ui.settings.HomeLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 6-step calibration wizard. Runs once after onboarding (gated by
 * `isWizardComplete`); also re-runnable from Settings.
 *
 * Steps:
 *  1. Layout (Grid vs Tabs)
 *  2. Modules — pick + reorder features for the home screen
 *  3. Device test — runs the YOLOv8n benchmark, reports tier
 *  4. Emergency PIN — navigates to existing DuressSetupScreen, or skip
 *  5. AI model — offers Gemma 4 download if device tier allows
 *  6. Finish — flips the completion flag, returns to home
 *
 * Each step persists its own state on Next, so back-navigation doesn't lose
 * partial progress and the wizard is interruption-safe (e.g. user taps Set
 * Emergency PIN, comes back via DuressSetupScreen's back arrow — the wizard
 * is still on step 4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationWizardScreen(
    onFinish: () -> Unit,
    onSetDuressPin: () -> Unit
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var currentStep by remember { mutableIntStateOf(0) }
    // 5 action steps + 7 informational pages + 1 finish = 13 total. The info
    // pages walk the user through each major Privora module so they leave the
    // wizard knowing what's there to use.
    val totalSteps = 15

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.wizard_title_progress, currentStep + 1, totalSteps),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            // Step progress indicator
            LinearProgressIndicator(
                progress = { (currentStep + 1) / totalSteps.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp)
            )

            // Step body — fills available space, scrollable as needed
            Box(modifier = Modifier.weight(1f)) {
                when (currentStep) {
                    0 -> Step1Layout()
                    1 -> Step2Modules()
                    2 -> Step3DeviceTest()
                    3 -> Step4DuressPin(onSetDuressPin = onSetDuressPin)
                    4 -> StepPrivacyDefaults()
                    5 -> Step5AIModel()
                    6 -> InfoPageContent(InfoPages.VAULT)
                    7 -> InfoPageContent(InfoPages.HEALTH)
                    8 -> InfoPageContent(InfoPages.BACKUP)
                    9 -> InfoPageContent(InfoPages.AI)
                    10 -> InfoPageContent(InfoPages.EMERGENCY)
                    11 -> InfoPageContent(InfoPages.CALCULATOR)
                    12 -> InfoPageContent(InfoPages.INTRUDER)
                    13 -> InfoPageContent(InfoPages.AUTHENTICATOR)
                    14 -> Step6Finish()
                }
            }

            // Bottom navigation bar — Back + Next/Finish
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0) {
                    TextButton(onClick = { currentStep-- }) {
                        Text(stringResource(R.string.wizard_back))
                    }
                } else {
                    Spacer(Modifier.size(1.dp))
                }
                if (currentStep < totalSteps - 1) {
                    Button(onClick = { currentStep++ }) {
                        Text(stringResource(R.string.wizard_next))
                    }
                } else {
                    Button(onClick = {
                        markWizardComplete(context)
                        onFinish()
                    }) {
                        Text(stringResource(R.string.wizard_finish))
                    }
                }
            }
        }
    }
}

// ───────── Step 1 — Layout ─────────

@Composable
private fun Step1Layout() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(FeatureToggleManager.getHomeLayout(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StepHeader(
            title = stringResource(R.string.wizard_step1_title),
            subtitle = stringResource(R.string.wizard_step1_subtitle)
        )
        LayoutChoiceCard(
            icon = Icons.Default.GridView,
            title = stringResource(R.string.wizard_layout_grid),
            subtitle = stringResource(R.string.wizard_layout_grid_desc),
            selected = selected == HomeLayout.GRID,
            onClick = {
                selected = HomeLayout.GRID
                FeatureToggleManager.setHomeLayout(context, HomeLayout.GRID)
            }
        )
        LayoutChoiceCard(
            icon = Icons.Default.ViewModule,
            title = stringResource(R.string.wizard_layout_tabs),
            subtitle = stringResource(R.string.wizard_layout_tabs_desc),
            selected = selected == HomeLayout.TABS,
            onClick = {
                selected = HomeLayout.TABS
                FeatureToggleManager.setHomeLayout(context, HomeLayout.TABS)
            }
        )
    }
}

@Composable
private fun LayoutChoiceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ───────── Step 2 — Modules ─────────

@Composable
private fun Step2Modules() {
    val context = LocalContext.current
    val featureMap = remember { homeFeatures.associateBy { it.route } }
    var ordered by remember { mutableStateOf(FeatureToggleManager.getOrderedFeatures(context)) }
    val enabledStates = remember {
        ordered.associateWith {
            context.getSharedPreferences("feature_toggles", android.content.Context.MODE_PRIVATE)
                .getBoolean(it, true)
        }.toMutableMap()
    }
    var enabledMap by remember { mutableStateOf(enabledStates.toMap()) }

    fun persistOrder(newOrder: List<String>) {
        ordered = newOrder
        FeatureToggleManager.saveOrder(context, newOrder)
    }
    fun persistEnabled(route: String, enabled: Boolean) {
        enabledMap = enabledMap + (route to enabled)
        FeatureToggleManager.setFeatureEnabled(context, route, enabled)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StepHeader(
            title = stringResource(R.string.wizard_step2_title),
            subtitle = stringResource(R.string.wizard_step2_subtitle)
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(ordered, key = { it }) { route ->
                val feature = featureMap[route]
                val enabled = enabledMap[route] ?: true
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (enabled)
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = enabled,
                            onCheckedChange = { persistEnabled(route, it) }
                        )
                        if (feature != null) {
                            Icon(feature.icon, null, Modifier.size(22.dp), tint = feature.accent)
                        }
                        Text(
                            feature?.let { stringResource(it.labelRes) } ?: route,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        val idx = ordered.indexOf(route)
                        IconButton(
                            onClick = {
                                if (idx > 0) {
                                    val m = ordered.toMutableList()
                                    val tmp = m[idx - 1]; m[idx - 1] = m[idx]; m[idx] = tmp
                                    persistOrder(m)
                                }
                            },
                            enabled = idx > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, null, Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                if (idx < ordered.size - 1) {
                                    val m = ordered.toMutableList()
                                    val tmp = m[idx + 1]; m[idx + 1] = m[idx]; m[idx] = tmp
                                    persistOrder(m)
                                }
                            },
                            enabled = idx < ordered.size - 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ───────── Step 3 — Device test ─────────

@Composable
private fun Step3DeviceTest() {
    val context = LocalContext.current
    var profile by remember { mutableStateOf<com.privateai.camera.service.DeviceProfile?>(null) }
    var benchmarking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (DeviceProfiler.isProfiled(context)) {
            profile = DeviceProfiler.getProfile(context)
        } else {
            benchmarking = true
            withContext(Dispatchers.Default) {
                profile = DeviceProfiler.runBenchmark(context)
            }
            benchmarking = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StepHeader(
            title = stringResource(R.string.wizard_step3_title),
            subtitle = stringResource(R.string.wizard_step3_subtitle)
        )
        if (benchmarking || profile == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.wizard_step3_running), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            val p = profile!!
            DeviceMetricRow(Icons.Default.Speed, stringResource(R.string.wizard_metric_inference), "${p.inferenceMs} ms")
            DeviceMetricRow(Icons.Default.Memory, stringResource(R.string.wizard_metric_ram), "${p.ramMb} MB")
            DeviceMetricRow(Icons.Default.Bolt, stringResource(R.string.wizard_metric_cores), "${p.cpuCores}")
            Spacer(Modifier.height(8.dp))
            // Verdict on AI suitability
            val (verdictRes, verdictColor) = when (p.tier) {
                DeviceTier.HIGH -> R.string.wizard_step3_ai_great to Color(0xFF2E7D32)
                DeviceTier.MEDIUM -> R.string.wizard_step3_ai_okay to Color(0xFFE65100)
                DeviceTier.LOW -> R.string.wizard_step3_ai_unsuitable to MaterialTheme.colorScheme.error
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = verdictColor.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (p.tier == DeviceTier.LOW) Icons.Default.Warning else Icons.Default.CheckCircle,
                        null, Modifier.size(24.dp), tint = verdictColor
                    )
                    Text(stringResource(verdictRes), style = MaterialTheme.typography.bodyMedium, color = verdictColor)
                }
            }
        }
    }
}

@Composable
private fun DeviceMetricRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

// ───────── Step 4 — Emergency PIN ─────────

@Composable
private fun Step4DuressPin(onSetDuressPin: () -> Unit) {
    val context = LocalContext.current
    val isSet = DuressManager.isEnabled(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StepHeader(
            title = stringResource(R.string.wizard_step4_title),
            subtitle = stringResource(R.string.wizard_step4_subtitle)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    null,
                    Modifier.size(24.dp),
                    tint = if (isSet) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (isSet) stringResource(R.string.wizard_step4_set)
                    else stringResource(R.string.wizard_step4_not_set),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Button(onClick = onSetDuressPin, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (isSet) stringResource(R.string.wizard_step4_change)
                else stringResource(R.string.wizard_step4_setup)
            )
        }
        Text(
            stringResource(R.string.wizard_step4_skip_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ───────── Step 4.5 — Privacy defaults ─────────

@Composable
private fun StepPrivacyDefaults() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("privacy_settings", android.content.Context.MODE_PRIVATE)
    }

    var blockScreenshots by remember {
        mutableStateOf(prefs.getBoolean("block_screenshots", true))
    }
    var graceSeconds by remember {
        mutableStateOf(prefs.getInt("lock_grace_seconds", 30))
    }
    val graceOptions = listOf(0, 10, 30, 60, 120, 300)
    val graceLabels = listOf(
        stringResource(R.string.grace_immediately),
        stringResource(R.string.grace_10_seconds),
        stringResource(R.string.grace_30_seconds),
        stringResource(R.string.grace_1_minute),
        stringResource(R.string.grace_2_minutes),
        stringResource(R.string.grace_5_minutes)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StepHeader(
            title = stringResource(R.string.wizard_privacy_title),
            subtitle = stringResource(R.string.wizard_privacy_subtitle)
        )

        // Screenshot protection — toggle. Applied immediately to the live
        // window so the user sees the effect (recents preview goes black).
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Security, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.wizard_privacy_screenshot_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.wizard_privacy_screenshot_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = blockScreenshots,
                    onCheckedChange = { v ->
                        blockScreenshots = v
                        prefs.edit().putBoolean("block_screenshots", v).apply()
                        val activity = context as? android.app.Activity
                        if (v) {
                            activity?.window?.setFlags(
                                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                                android.view.WindowManager.LayoutParams.FLAG_SECURE
                            )
                        } else {
                            activity?.window?.clearFlags(
                                android.view.WindowManager.LayoutParams.FLAG_SECURE
                            )
                        }
                    }
                )
            }
        }

        // Lock-after-inactivity radio list. Same options & key as Settings.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.wizard_privacy_autolock_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.wizard_privacy_autolock_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                graceOptions.forEachIndexed { index, value ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                graceSeconds = value
                                prefs.edit().putInt("lock_grace_seconds", value).apply()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = graceSeconds == value,
                            onClick = {
                                graceSeconds = value
                                prefs.edit().putInt("lock_grace_seconds", value).apply()
                            }
                        )
                        Text(graceLabels[index], modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }

        Text(
            stringResource(R.string.wizard_privacy_change_later_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ───────── Step 5 — AI Model ─────────

@Composable
private fun Step5AIModel() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val deviceTier = remember { DeviceProfiler.getProfile(context).tier }
    val storageInfo = remember { StorageManager.getStorageInfo(context) }
    val freeBytes = storageInfo.deviceFreeBytes
    val requiredBytes = 2_700_000_000L
    val hasSpace = freeBytes >= requiredBytes
    val downloadState by GemmaModelManager.downloadState.collectAsState()
    var modelDownloaded by remember { mutableStateOf(GemmaRunner.isModelDownloaded(context)) }
    // Mirror the on/off pref so a Switch can drive it directly. When the
    // model is already on disk (e.g. carried over from a backup, or the
    // user re-ran the wizard) the wizard previously showed only a green
    // "already downloaded" card with no way to flip AI on — so the user
    // landed on the finish screen still thinking AI wasn't usable.
    var aiEnabled by remember { mutableStateOf(GemmaRunner.isEnabled(context)) }

    LaunchedEffect(downloadState) {
        if (downloadState is GemmaModelManager.DownloadState.Complete) {
            modelDownloaded = true
            // Auto-enable on first successful download — keeps the post-
            // download flow obvious. User can always flip it off below.
            if (!aiEnabled) {
                GemmaRunner.setEnabled(context, true)
                aiEnabled = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StepHeader(
            title = stringResource(R.string.wizard_step5_title),
            subtitle = stringResource(R.string.wizard_step5_subtitle)
        )

        // Storage line
        Text(
            stringResource(
                R.string.wizard_step5_storage,
                StorageManager.formatSize(requiredBytes),
                StorageManager.formatSize(freeBytes)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (hasSpace) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
        )

        // Verdict + action
        when {
            modelDownloaded -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2E7D32).copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(24.dp), tint = Color(0xFF2E7D32))
                        Text(
                            stringResource(R.string.wizard_step5_already_downloaded),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                // Explicit on/off so the user can flip AI without leaving
                // the wizard. Same SharedPref the Settings → AI toggle
                // drives, so state stays in sync.
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            val next = !aiEnabled
                            if (next) {
                                GemmaRunner.resetCrashFlag(context)
                                GemmaRunner.resetVisionCrashFlag(context)
                            } else {
                                GemmaRunner.unload()
                            }
                            GemmaRunner.setEnabled(context, next)
                            aiEnabled = next
                        }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.wizard_step5_ai_toggle_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(
                                if (aiEnabled) R.string.wizard_step5_ai_toggle_on
                                else R.string.wizard_step5_ai_toggle_off
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(checked = aiEnabled, onCheckedChange = null)
                }
            }
            deviceTier == DeviceTier.LOW -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                        Text(
                            stringResource(R.string.wizard_step5_low_tier),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            !hasSpace -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                        Text(
                            stringResource(R.string.wizard_step5_no_space),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            else -> {
                val ds = downloadState
                if (ds is GemmaModelManager.DownloadState.Downloading) {
                    val pct = if (ds.totalBytes > 0) (ds.progressBytes.toFloat() / ds.totalBytes) else 0f
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                        Text(
                            "${(pct * 100).toInt()}% — ${StorageManager.formatSize(ds.progressBytes)} / ${StorageManager.formatSize(ds.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = { GemmaModelManager.cancelDownload(context) }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                } else if (ds is GemmaModelManager.DownloadState.Error) {
                    Text(ds.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        GemmaRunner.setEnabled(context, true)
                        GemmaModelManager.startDownload(context)
                    }) { Text(stringResource(R.string.action_retry)) }
                } else {
                    Button(
                        onClick = {
                            GemmaRunner.setEnabled(context, true)
                            GemmaModelManager.startDownload(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.wizard_step5_download))
                    }
                    Text(
                        stringResource(R.string.wizard_step5_skip_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ───────── Step 6 — Finish ─────────

@Composable
private fun Step6Finish() {
    val context = LocalContext.current
    val aiEnabled = remember { com.privateai.camera.bridge.GemmaRunner.isEnabled(context) }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.wizard_step6_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.wizard_step6_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        // Reassure users who skipped the AI download that they didn't lose
        // anything — the app works fully without it, and the toggle is one
        // tap away in the new Settings → AI section.
        if (!aiEnabled) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.wizard_step6_ai_off_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

// ───────── Informational pages (steps 6–11) ─────────

/**
 * Pure-information page describing one major Privora module. No actions —
 * just a tall icon + title + body. The wizard steps through these so users
 * leave knowing the surface area is bigger than the home grid suggests.
 */
private data class InfoPage(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    @androidx.annotation.StringRes val titleRes: Int,
    @androidx.annotation.StringRes val bodyRes: Int,
    val accent: Color
)

private object InfoPages {
    val VAULT = InfoPage(Icons.Default.Lock, R.string.wizard_info_vault_title, R.string.wizard_info_vault_body, Color(0xFFC62828))
    val HEALTH = InfoPage(Icons.Default.MonitorHeart, R.string.wizard_info_health_title, R.string.wizard_info_health_body, Color(0xFFAD1457))
    val BACKUP = InfoPage(Icons.Default.CloudSync, R.string.wizard_info_backup_title, R.string.wizard_info_backup_body, Color(0xFF1565C0))
    val AI = InfoPage(Icons.Default.AutoAwesome, R.string.wizard_info_ai_title, R.string.wizard_info_ai_body, Color(0xFF6A1B9A))
    val EMERGENCY = InfoPage(Icons.Default.Security, R.string.wizard_info_emergency_title, R.string.wizard_info_emergency_body, Color(0xFFD32F2F))
    val CALCULATOR = InfoPage(Icons.Default.Calculate, R.string.wizard_info_calculator_title, R.string.wizard_info_calculator_body, Color(0xFF37474F))
    val INTRUDER = InfoPage(Icons.Default.PhotoCamera, R.string.wizard_info_intruder_title, R.string.wizard_info_intruder_body, Color(0xFFE65100))
    val AUTHENTICATOR = InfoPage(Icons.Default.LockClock, R.string.wizard_info_authenticator_title, R.string.wizard_info_authenticator_body, Color(0xFF0097A7))
}

@Composable
private fun InfoPageContent(page: InfoPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(page.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(page.icon, null, Modifier.size(54.dp), tint = page.accent)
        }
        Text(
            stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

// ───────── Shared bits ─────────

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
