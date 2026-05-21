// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.privateai.camera.R
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.NoteRepository
import com.privateai.camera.security.SecureNote
import com.privateai.camera.security.VaultCategory
import com.privateai.camera.security.VaultLockManager
import com.privateai.camera.security.VaultPhoto
import com.privateai.camera.security.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Alternative home layout: bottom NavigationBar with 4 direct tabs + "More" overflow.
 * First 4 enabled features become tabs. Remaining features accessible via "More" sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabsLayout(
    visibleFeatures: List<FeatureItem>,
    onFeatureClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onAssistantClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val aiStatus by com.privateai.camera.bridge.rememberAiStatus()
    val aiReady = aiStatus.isReady
    // Session flag — true when ANY session is active (normal unlock OR duress)
    // so the lock button is shown and the user can always re-lock to escape duress.
    var hasActiveSession by remember { mutableStateOf(VaultLockManager.isUnlockedWithinGrace(context)) }
    // Treat duress mode as "locked" for data rendering — no real data leaks through greeting / recent activity / AI tip
    var isVaultUnlocked by remember {
        mutableStateOf(VaultLockManager.isUnlockedWithinGrace(context) && !VaultLockManager.isDuressActive)
    }

    // General vault unlock — same logic as Grid layout
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
            if (crypto.initialize()) { VaultLockManager.markUnlocked(); hasActiveSession = true; isVaultUnlocked = true }
            return
        }
        val prompt = androidx.biometric.BiometricPrompt(
            activity, androidx.core.content.ContextCompat.getMainExecutor(context),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    if (crypto.initialize()) { VaultLockManager.markUnlocked(); hasActiveSession = true; isVaultUnlocked = true }
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

    if (showPinDialog) {
        VaultPinDialog(
            crypto = crypto,
            onUnlocked = { isDuress ->
                showPinDialog = false
                hasActiveSession = true
                isVaultUnlocked = !isDuress
            },
            onDismiss = { showPinDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name_home)) },
                actions = {
                    // ✨ AI Assistant — visible when AI available + not duress.
                    // Mirrors the grid layout: when the Settings "Allow
                    // Assistant without unlocking vault" toggle is on, the
                    // icon is also shown while the vault is locked (the
                    // assistant itself enforces the locked-vault read gate;
                    // surfacing it lets the user start chatting without
                    // unlocking first).
                    val assistantUnlockedAccess = remember(isVaultUnlocked) {
                        com.privateai.camera.ui.settings.isAssistantUnlockedAccessEnabled(context)
                    }
                    if ((isVaultUnlocked || assistantUnlockedAccess)
                        && aiReady
                        && !com.privateai.camera.security.VaultLockManager.isDuressActive
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
                    // Lock/Unlock — always visible. Locked → tap to authenticate. Unlocked → tap to re-lock.
                    if (hasActiveSession) {
                        IconButton(onClick = {
                            VaultLockManager.lock()
                            hasActiveSession = false
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
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
        // bottomBar removed — app-level Scaffold in PrivateAICameraApp provides persistent tabs
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Greeting — include self contact name when vault is unlocked
            GreetingHeader(isVaultUnlocked = isVaultUnlocked)

            // Daily tip — above recent activity for visibility
            DailyTipCard(isVaultUnlocked = isVaultUnlocked)

            // Recent activity + today's reminders (only when unlocked)
            if (isVaultUnlocked) {
                RecentActivityCard(onFeatureClick = onFeatureClick)
            } else {
                LockedActivityCard()
            }
        }
    }
}

@Composable
private fun GreetingHeader(isVaultUnlocked: Boolean) {
    val context = LocalContext.current
    val greeting = remember { getTimeBasedGreeting(context) }
    var selfName by remember { mutableStateOf<String?>(null) }

    // Only pull the self name when unlocked (contacts DB requires crypto)
    LaunchedEffect(isVaultUnlocked) {
        if (!isVaultUnlocked) {
            selfName = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val crypto = com.privateai.camera.security.CryptoManager(context).also { it.initialize() }
                if (crypto.isUnlocked()) {
                    val db = com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)
                    val contactRepo = com.privateai.camera.security.ContactRepository(
                        File(context.filesDir, "vault/contacts"), crypto, db
                    )
                    contactRepo.ensureSelfContact(context.getString(R.string.health_myself))
                    selfName = contactRepo.getSelfContact()?.name
                }
            } catch (_: Exception) {}
        }
    }

    val name = selfName
    Column {
        Text(
            greeting,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!name.isNullOrBlank() && name != context.getString(R.string.health_myself)) {
            Text(
                name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DailyTipCard(isVaultUnlocked: Boolean) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Locked → always use static pool. Unlocked + AI available → AI tip.
    val aiStatus by com.privateai.camera.bridge.rememberAiStatus()
    val canUseAi = isVaultUnlocked && aiStatus.isReady

    // Initial state
    val initialCategory = remember { pickCategoryForHour() }
    val initialAi = if (canUseAi) getCachedAiTipForCategory(context, initialCategory) else null
    var tipText by remember { mutableStateOf(initialAi?.text ?: getTodayTip(context)) }
    var tipLabelRes by remember { mutableStateOf(initialAi?.labelRes ?: R.string.home_tip_of_the_day) }
    var isAiTip by remember { mutableStateOf(initialAi != null) }
    var currentCategory by remember { mutableStateOf(initialCategory) }
    var isLoading by remember { mutableStateOf(false) }
    var hourLangKey by remember { mutableStateOf(if (canUseAi) getHourLanguageKey(context) else "") }

    LaunchedEffect(canUseAi) {
        if (!canUseAi) {
            tipText = getTodayTip(context)
            tipLabelRes = R.string.home_tip_of_the_day
            isAiTip = false
            return@LaunchedEffect
        }
        isLoading = true
        generateAiTip(context, currentCategory)?.let {
            tipText = it.text
            tipLabelRes = it.labelRes
            isAiTip = true
        }
        isLoading = false
        // Poll every 60s for hour/language change → auto-refresh with new hour's category
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            val newKey = getHourLanguageKey(context)
            if (newKey != hourLangKey) {
                hourLangKey = newKey
                currentCategory = pickCategoryForHour()
                isLoading = true
                generateAiTip(context, currentCategory)?.let {
                    tipText = it.text
                    tipLabelRes = it.labelRes
                    isAiTip = true
                }
                isLoading = false
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            Modifier
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                        )
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                if (isAiTip) Icons.Default.AutoAwesome else Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(22.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(tipLabelRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tipText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            // "Next" button — only when AI tip is active
            if (isAiTip) {
                IconButton(
                    onClick = {
                        if (isLoading) return@IconButton
                        scope.launch {
                            isLoading = true
                            currentCategory = nextCategory(currentCategory)
                            generateAiTip(context, currentCategory)?.let {
                                tipText = it.text
                                tipLabelRes = it.labelRes
                            }
                            isLoading = false
                        }
                    },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.action_next),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedActivityCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Text(
                stringResource(R.string.home_vault_is_locked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A today-reminder entry for the landing card. */
private data class TodayRemItem(val title: String, val time: String, val isDone: Boolean)

@Composable
private fun RecentActivityCard(onFeatureClick: (String) -> Unit) {
    val context = LocalContext.current
    var photoCount by remember { mutableStateOf(0) }
    var noteCount by remember { mutableStateOf(0) }
    var latestPhotoThumb by remember { mutableStateOf<Bitmap?>(null) }
    var latestNoteTitle by remember { mutableStateOf<String?>(null) }
    var todayReminders by remember { mutableStateOf<List<TodayRemItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val crypto = CryptoManager(context).also { it.initialize() }
                val vault = VaultRepository(context, crypto)
                val noteRepo = NoteRepository(File(context.filesDir, "vault/notes"), crypto)
                val insightsRepo = com.privateai.camera.security.InsightsRepository(
                    File(context.filesDir, "vault/insights"), crypto
                )

                val allPhotos = VaultCategory.entries
                    .filter { it != VaultCategory.FILES }
                    .flatMap { vault.listPhotos(it) }
                photoCount = allPhotos.size

                val latestPhoto = allPhotos.maxByOrNull { it.timestamp }
                latestPhotoThumb = latestPhoto?.let { vault.loadThumbnail(it) }

                noteCount = noteRepo.noteCount()
                latestNoteTitle = noteRepo.listNotes()
                    .maxByOrNull { it.modifiedAt }?.title
                    ?.takeIf { it.isNotBlank() }

                // Today's reminders
                val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                val today = dateFmt.format(java.util.Date())
                val cal = java.util.Calendar.getInstance()
                val todayDow = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
                    java.util.Calendar.MONDAY -> 1; java.util.Calendar.TUESDAY -> 2
                    java.util.Calendar.WEDNESDAY -> 3; java.util.Calendar.THURSDAY -> 4
                    java.util.Calendar.FRIDAY -> 5; java.util.Calendar.SATURDAY -> 6
                    else -> 7
                }
                val schedules = insightsRepo.listScheduleItems().filter { it.enabled }
                val todayLog = insightsRepo.loadScheduleLog(today)

                val recurring = schedules
                    .filter { !it.isOneShot && (it.daysOfWeek.isEmpty() || todayDow in it.daysOfWeek) }
                    .flatMap { item -> item.timesOfDay.map { time ->
                        val done = todayLog.entries.any { it.scheduleId == item.id && it.time == time && it.state == com.privateai.camera.security.LogState.DONE }
                        TodayRemItem(item.title, time, done)
                    }}
                val oneShots = schedules
                    .filter { it.isOneShot && it.oneShotAt != null && dateFmt.format(java.util.Date(it.oneShotAt!!)) == today }
                    .map { item ->
                        val t = timeFmt.format(java.util.Date(item.oneShotAt!!))
                        val done = todayLog.entries.any { it.scheduleId == item.id && (it.time == t || it.time == "ONESHOT") && it.state == com.privateai.camera.security.LogState.DONE }
                        TodayRemItem(item.title, t, done)
                    }
                todayReminders = (recurring + oneShots).sortedBy { it.time }.take(5)
            } catch (_: Exception) {
                // Silently fail — keep defaults
            }
        }
    }

    // Section header
    Text(
        stringResource(R.string.home_recent_activity),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(8.dp))

    // Today's reminders — individual mini-cards
    if (todayReminders.isNotEmpty()) {
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
                        .clickable { onFeatureClick("reminders") }
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
                            textDecoration = if (rem.isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
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
        Spacer(Modifier.height(4.dp))
    }

    // Latest photo — larger preview with stack hint
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onFeatureClick("vault") }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Photo stack effect — slightly larger thumbnail with layered background
            Box(contentAlignment = Alignment.Center) {
                // Stack shadow layers
                Box(
                    Modifier.size(68.dp)
                        .padding(start = 6.dp, top = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                )
                Box(
                    Modifier.size(68.dp)
                        .padding(start = 3.dp, top = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                )
                // Main thumbnail
                Box(
                    Modifier.size(68.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    val thumb = latestPhotoThumb
                    if (thumb != null) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                        )
                    } else {
                        Icon(Icons.Default.Photo, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_latest_photo),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.home_photos_count, photoCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Latest note — its own mini-card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onFeatureClick("notes") }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.NoteAlt, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    latestNoteTitle ?: stringResource(R.string.home_latest_note),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    stringResource(R.string.home_notes_count, noteCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// MoreSheetItem moved to PrivoraBottomTabs.kt
