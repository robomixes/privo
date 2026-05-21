// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.totp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.privateai.camera.R
import com.privateai.camera.bridge.Totp
import com.privateai.camera.security.AppPinManager
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.DuressManager
import com.privateai.camera.security.PinRateLimiter
import com.privateai.camera.security.TotpEntry
import com.privateai.camera.security.TotpPrefs
import com.privateai.camera.security.TotpRepository
import com.privateai.camera.security.VaultLockManager
import com.privateai.camera.ui.onboarding.AuthMode
import com.privateai.camera.ui.onboarding.getAuthMode
import com.privateai.camera.ui.qrscanner.QrGenerator
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private enum class TotpPage { LOCKED, LIST, ADD, EDIT, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotpListScreen(
    onBack: (() -> Unit)? = null,
    onScanQr: (() -> Unit)? = null,
    seedFromUri: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val crypto = remember { CryptoManager(context) }
    val repo = remember { TotpRepository(context, crypto) }

    val startUnlocked = remember {
        VaultLockManager.isUnlockedWithinGrace(context) && crypto.initialize()
    }
    var page by remember { mutableStateOf(if (startUnlocked) TotpPage.LIST else TotpPage.LOCKED) }
    var isDuressActive by remember { mutableStateOf(VaultLockManager.isDuressActive) }
    var entries by remember { mutableStateOf<List<TotpEntry>>(emptyList()) }
    var hideUntilTap by remember { mutableStateOf(TotpPrefs.hideUntilTap(context)) }
    var qrEntry by remember { mutableStateOf<TotpEntry?>(null) }
    var editingEntry by remember { mutableStateOf<TotpEntry?>(null) }
    // Pending entry awaiting duplicate-conflict resolution.
    var duplicatePending by remember { mutableStateOf<Pair<TotpEntry, TotpEntry>?>(null) }

    fun refresh() {
        entries = if (isDuressActive) emptyList() else repo.list()
    }

    fun trySaveNew(entry: TotpEntry) {
        val existing = repo.list().firstOrNull { it.secret.contentEquals(entry.secret) }
        if (existing != null) {
            duplicatePending = entry to existing
        } else {
            repo.add(entry)
            refresh()
            page = TotpPage.LIST
        }
    }

    qrEntry?.let { e ->
        ShowQrDialog(entry = e, onDismiss = { qrEntry = null })
    }

    duplicatePending?.let { (incoming, existing) ->
        DuplicateDialog(
            incoming = incoming,
            existing = existing,
            onReplace = {
                repo.delete(existing.id)
                repo.add(incoming)
                duplicatePending = null
                refresh()
                page = TotpPage.LIST
            },
            onKeepBoth = {
                repo.add(incoming)
                duplicatePending = null
                refresh()
                page = TotpPage.LIST
            },
            onCancel = { duplicatePending = null }
        )
    }

    // Auto-lock with shared grace period — same pattern as VaultScreen / NotesScreen.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> VaultLockManager.markLeft()
                Lifecycle.Event.ON_START -> {
                    if (page != TotpPage.LOCKED && !VaultLockManager.isUnlockedWithinGrace(context)) {
                        page = TotpPage.LOCKED
                        crypto.lock()
                        VaultLockManager.lock()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(page) { if (page == TotpPage.LIST) refresh() }

    // Deep-link seed from a scanned QR. Mirror the nav-route arg into local
    // state so we can *clear* it after the first Add-screen visit consumes
    // it. Without this, every subsequent tap of the + FAB would pre-fill
    // the form with the previously scanned URI (the nav route still carries
    // the original ?uri=... arg until the user navigates away).
    var pendingSeed by remember(seedFromUri) { mutableStateOf(seedFromUri) }
    LaunchedEffect(pendingSeed, page) {
        if (!pendingSeed.isNullOrBlank() && page == TotpPage.LIST) {
            page = TotpPage.ADD
        }
    }

    when (page) {
        TotpPage.LOCKED -> LockGate(
            onBack = onBack,
            crypto = crypto,
            onUnlock = {
                isDuressActive = VaultLockManager.isDuressActive
                refresh()
                page = TotpPage.LIST
            },
            onDuressActivated = {
                isDuressActive = true
                entries = emptyList()
                page = TotpPage.LIST
                scope.launch(Dispatchers.IO) { DuressManager.executeDuress(context, crypto) }
            }
        )
        TotpPage.LIST -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.feature_authenticator)) },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { page = TotpPage.SETTINGS }) {
                            Icon(Icons.Default.Settings, stringResource(R.string.settings_title))
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { page = TotpPage.ADD }) {
                    Icon(Icons.Default.Add, stringResource(R.string.totp_add))
                }
            }
        ) { padding ->
            if (entries.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.LockClock,
                        null,
                        Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.totp_empty_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.totp_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        TotpEntryCard(
                            entry = entry,
                            hideUntilTapDefault = hideUntilTap,
                            onCopy = { code ->
                                copyToClipboard(context, code)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.totp_copied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onShowQr = { qrEntry = entry },
                            onEdit = { editingEntry = entry; page = TotpPage.EDIT },
                            onDelete = {
                                repo.delete(entry.id)
                                refresh()
                            }
                        )
                    }
                }
            }
        }
        TotpPage.ADD -> AddTotpScreen(
            initialUri = pendingSeed,
            existingEntry = null,
            onBack = {
                // Consume the seed so the next + FAB tap opens a blank form,
                // even if the nav route still has the original ?uri= arg.
                pendingSeed = null
                page = TotpPage.LIST
            },
            onSave = { entry ->
                pendingSeed = null
                trySaveNew(entry)
            },
            onScanQr = onScanQr
        )
        TotpPage.EDIT -> {
            val target = editingEntry
            if (target == null) {
                page = TotpPage.LIST
            } else {
                AddTotpScreen(
                    initialUri = null,
                    existingEntry = target,
                    onBack = { editingEntry = null; page = TotpPage.LIST },
                    onSave = { updated ->
                        repo.update(updated)
                        editingEntry = null
                        refresh()
                        page = TotpPage.LIST
                    },
                    onScanQr = null
                )
            }
        }
        TotpPage.SETTINGS -> SettingsPage(
            hideUntilTap = hideUntilTap,
            onHideUntilTapChange = { v ->
                hideUntilTap = v
                TotpPrefs.setHideUntilTap(context, v)
            },
            onBack = { page = TotpPage.LIST }
        )
    }
}

// ─── Lock screen ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockGate(
    onBack: (() -> Unit)?,
    crypto: CryptoManager,
    onUnlock: () -> Unit,
    onDuressActivated: () -> Unit
) {
    val context = LocalContext.current
    val authMode = remember { getAuthMode(context) }

    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var isLockedOut by remember { mutableStateOf(PinRateLimiter.remainingLockoutMs(context) > 0) }
    var lockoutRemainingMs by remember { mutableStateOf(PinRateLimiter.remainingLockoutMs(context)) }

    LaunchedEffect(isLockedOut) {
        if (isLockedOut) {
            while (true) {
                val remaining = PinRateLimiter.remainingLockoutMs(context)
                if (remaining <= 0) { isLockedOut = false; lockoutRemainingMs = 0L; break }
                lockoutRemainingMs = remaining
                delay(1000L)
            }
        }
    }

    fun checkPin(entered: String) {
        if (DuressManager.isEnabled(context) && DuressManager.isDuressPin(context, entered)) {
            VaultLockManager.activateDuress()
            VaultLockManager.markUnlocked()
            pinInput = ""
            pinError = null
            onDuressActivated()
            return
        }
        if (!PinRateLimiter.canAttempt(context)) {
            pinInput = ""
            isLockedOut = true
            lockoutRemainingMs = PinRateLimiter.remainingLockoutMs(context)
            return
        }
        if (AppPinManager.verify(context, entered)) {
            PinRateLimiter.recordSuccess(context)
            if (crypto.initialize()) {
                VaultLockManager.clearDuress()
                VaultLockManager.markUnlocked()
                pinInput = ""
                pinError = null
                onUnlock()
            }
            return
        }
        PinRateLimiter.recordFailure(context)
        val remaining = PinRateLimiter.remainingLockoutMs(context)
        if (remaining > 0) {
            isLockedOut = true
            lockoutRemainingMs = remaining
            pinError = null
        } else {
            pinError = context.getString(R.string.incorrect_pin)
        }
        pinInput = ""
    }

    fun authenticateBio() {
        val activity = context as? FragmentActivity ?: return
        val bm = BiometricManager.from(context)
        val canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
        if (!canAuth) {
            if (crypto.initialize()) {
                VaultLockManager.markUnlocked()
                onUnlock()
            }
            return
        }
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (crypto.initialize()) {
                        VaultLockManager.markUnlocked()
                        onUnlock()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.totp_unlock_title))
                .setSubtitle(context.getString(R.string.totp_unlock_subtitle))
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
        )
    }

    LaunchedEffect(Unit) {
        if (authMode == AuthMode.PHONE_LOCK) authenticateBio()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.feature_authenticator)) },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.LockClock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.totp_locked),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp)
            )
            Spacer(Modifier.height(24.dp))

            if (authMode == AuthMode.APP_PIN) {
                if (isLockedOut) {
                    val seconds = (lockoutRemainingMs / 1000).toInt()
                    Text(
                        stringResource(R.string.pin_locked_out, "%d:%02d".format(seconds / 60, seconds % 60)),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                                pinInput = it
                                pinError = null
                            }
                        },
                        label = { Text(stringResource(R.string.enter_pin)) },
                        modifier = Modifier.width(220.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (pinInput.length >= 4) checkPin(pinInput) }),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pinError != null,
                        supportingText = {
                            if (pinError != null) Text(pinError!!, color = MaterialTheme.colorScheme.error)
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { if (pinInput.length >= 4) checkPin(pinInput) },
                        enabled = pinInput.length >= 4,
                        modifier = Modifier.width(220.dp)
                    ) { Text(stringResource(R.string.unlock)) }
                }
            } else {
                Button(onClick = { authenticateBio() }, modifier = Modifier.width(220.dp)) {
                    Text(stringResource(R.string.unlock))
                }
            }
        }
    }
}

// ─── Entry card with countdown ring ───────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TotpEntryCard(
    entry: TotpEntry,
    hideUntilTapDefault: Boolean,
    onCopy: (String) -> Unit,
    onShowQr: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var revealed by remember(entry.id, hideUntilTapDefault) { mutableStateOf(!hideUntilTapDefault) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Tick once per second; cheap, only this composable subscribes.
    LaunchedEffect(entry.id) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(500L)
        }
    }

    val nowSec = nowMs / 1000L
    val code = remember(entry, nowSec / entry.period) {
        Totp.generate(entry.secret, nowSec, entry.period, entry.digits, entry.algo)
    }
    val secondsRemaining by remember {
        derivedStateOf { Totp.secondsRemaining(nowMs / 1000L, entry.period) }
    }
    val progress by animateFloatAsState(
        targetValue = secondsRemaining.toFloat() / entry.period.toFloat(),
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "totp-ring"
    )
    val ringColor = when {
        secondsRemaining <= 5 -> Color(0xFFD32F2F)
        secondsRemaining <= 10 -> Color(0xFFEF6C00)
        else -> MaterialTheme.colorScheme.primary
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.totp_delete_title)) },
            text = { Text(stringResource(R.string.totp_delete_body, entry.displayName())) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (!revealed) revealed = true else onCopy(code)
                },
                onLongClick = { menuOpen = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Issuer-initial avatar — colored circle with the first letter of the
            // issuer (or label, when no issuer is set). Color is derived from a
            // stable hash of the issuer string so the same service always gets
            // the same color across launches.
            val avatarSource = entry.issuer?.takeIf { it.isNotBlank() } ?: entry.label
            val initial = avatarSource.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'
            val avatarColor = remember(avatarSource) { avatarColorFor(avatarSource) }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initial.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                if (!entry.issuer.isNullOrBlank()) {
                    Text(
                        entry.issuer,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    entry.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                val display = if (revealed) prettyCode(code) else "•••  •••"
                Text(
                    display,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = if (revealed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Countdown ring + seconds-left label
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                CountdownRing(progress = progress, color = ringColor)
                Text("$secondsRemaining", style = MaterialTheme.typography.labelMedium, color = ringColor)
            }

            Spacer(Modifier.width(4.dp))

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.totp_copy)) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = { menuOpen = false; onCopy(code) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.totp_edit)) },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuOpen = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.totp_show_qr)) },
                        leadingIcon = { Icon(Icons.Default.QrCode2, null) },
                        onClick = { menuOpen = false; onShowQr() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menuOpen = false; confirmDelete = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownRing(progress: Float, color: Color) {
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .size(36.dp)
            .drawBehind {
                val stroke = 4.dp.toPx()
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke)
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke)
                )
            }
    )
}

// ─── Add screen (manual entry + Scan QR shortcut) ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTotpScreen(
    initialUri: String?,
    existingEntry: TotpEntry?,
    onBack: () -> Unit,
    onSave: (TotpEntry) -> Unit,
    onScanQr: (() -> Unit)?
) {
    // Seed priority: existing entry (edit mode) > scanned otpauth URI > blank.
    val seed = remember(initialUri) { initialUri?.let { Totp.parseUri(it) } }
    // If the scanner passed us a URI but it failed to parse, tell the user so
    // they don't stare at an empty form thinking the scan went through. Used
    // to be silent — confusing, especially after a long otpauth QR scan.
    val ctxForToast = LocalContext.current
    LaunchedEffect(initialUri) {
        if (!initialUri.isNullOrBlank() && seed == null) {
            Toast.makeText(
                ctxForToast,
                "Could not read that QR — enter the details manually",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val isEditing = existingEntry != null

    var label by remember { mutableStateOf(existingEntry?.label ?: seed?.label ?: "") }
    var issuer by remember { mutableStateOf(existingEntry?.issuer ?: seed?.issuer ?: "") }
    var secret by remember {
        mutableStateOf(
            existingEntry?.let { Totp.base32Encode(it.secret) }
                ?: seed?.let { Totp.base32Encode(it.secret) }
                ?: ""
        )
    }
    var period by remember { mutableStateOf((existingEntry?.period ?: seed?.period ?: 30).toString()) }
    var digits by remember { mutableStateOf((existingEntry?.digits ?: seed?.digits ?: 6).toString()) }
    var algo by remember { mutableStateOf(existingEntry?.algo ?: seed?.algo ?: Totp.Algo.SHA1) }
    var error by remember { mutableStateOf<String?>(null) }

    fun trySave() {
        if (label.isBlank()) {
            error = "label_blank"
            return
        }
        val secretBytes = try {
            Totp.base32Decode(secret)
        } catch (_: Exception) {
            error = "secret_invalid"
            return
        }
        if (secretBytes.isEmpty()) {
            error = "secret_invalid"
            return
        }
        val periodInt = period.toIntOrNull()?.takeIf { it > 0 } ?: 30
        val digitsInt = digits.toIntOrNull()?.coerceIn(6, 10) ?: 6
        onSave(
            TotpEntry(
                // Preserve id + createdAt when editing so the encrypted file path
                // and sort position stay stable.
                id = existingEntry?.id ?: UUID.randomUUID().toString(),
                label = label.trim(),
                issuer = issuer.trim().ifBlank { null },
                secret = secretBytes,
                period = periodInt,
                digits = digitsInt,
                algo = algo,
                createdAt = existingEntry?.createdAt ?: System.currentTimeMillis()
            )
        )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(stringResource(
                    if (isEditing) R.string.totp_edit_title else R.string.totp_add_title
                ))
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Scan QR shortcut — host wires this to the QR scanner route.
            // Hidden in edit mode (changing the secret is intentional, not a fresh-add).
            if (!isEditing && onScanQr != null) {
                OutlinedButton(
                    onClick = onScanQr,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.totp_scan_qr))
                }
                Text(
                    stringResource(R.string.totp_or_enter_manually),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = issuer,
                onValueChange = { issuer = it },
                label = { Text(stringResource(R.string.totp_issuer)) },
                placeholder = { Text("GitHub") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it; if (error == "label_blank") error = null },
                label = { Text(stringResource(R.string.totp_label_account)) },
                placeholder = { Text("alice@example.com") },
                singleLine = true,
                isError = error == "label_blank",
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it; if (error == "secret_invalid") error = null },
                label = { Text(stringResource(R.string.totp_secret)) },
                placeholder = { Text("JBSWY3DPEHPK3PXP") },
                singleLine = true,
                isError = error == "secret_invalid",
                supportingText = {
                    if (error == "secret_invalid") {
                        Text(stringResource(R.string.totp_secret_invalid), color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(stringResource(R.string.totp_secret_hint))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Advanced: digits / period / algorithm. Defaults work for >99% of services.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = period,
                    onValueChange = { period = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.totp_period)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = digits,
                    onValueChange = { digits = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text(stringResource(R.string.totp_digits)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            // Algorithm picker
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.totp_algorithm))
                listOf(Totp.Algo.SHA1, Totp.Algo.SHA256, Totp.Algo.SHA512).forEach { a ->
                    OutlinedButton(
                        onClick = { algo = a },
                        colors = if (a == algo) {
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        } else {
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        }
                    ) { Text(a.name) }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = { trySave() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.totp_save))
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────

private fun copyToClipboard(context: Context, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("TOTP", value))
}

/** "123456" → "123 456" (or "1234 5678" for 8-digit, etc.). */
private fun prettyCode(code: String): String =
    if (code.length <= 6) {
        val mid = code.length / 2
        code.substring(0, mid) + "  " + code.substring(mid)
    } else {
        val mid = code.length / 2
        code.substring(0, mid) + "  " + code.substring(mid)
    }

/**
 * Deterministic color for an issuer-initial avatar — same string always picks
 * the same color, so the user builds visual memory of "GitHub is green."
 * Palette is muted/saturated enough to read white text clearly.
 */
private val AVATAR_PALETTE = listOf(
    Color(0xFF1565C0), // blue
    Color(0xFF2E7D32), // green
    Color(0xFFC62828), // red
    Color(0xFF6A1B9A), // purple
    Color(0xFFE65100), // orange
    Color(0xFF00838F), // teal
    Color(0xFFAD1457), // pink
    Color(0xFF4E342E), // brown
    Color(0xFF37474F), // blue-grey
    Color(0xFF558B2F)  // light-green
)

private fun avatarColorFor(seed: String): Color {
    if (seed.isEmpty()) return AVATAR_PALETTE[0]
    val hash = seed.fold(0) { acc, c -> acc * 31 + c.code }
    val idx = (hash % AVATAR_PALETTE.size + AVATAR_PALETTE.size) % AVATAR_PALETTE.size
    return AVATAR_PALETTE[idx]
}

/**
 * Show-as-QR dialog for transferring an entry to another device.
 *
 * The QR encodes the original `otpauth://` URI (full secret + parameters).
 * Anyone who scans this gets full, permanent access to the account's TOTP
 * codes — that's why the dialog leads with the warning before revealing the
 * code and never persists it to disk.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowQrDialog(entry: TotpEntry, onDismiss: () -> Unit) {
    var revealed by remember(entry.id) { mutableStateOf(false) }
    val uri = remember(entry) {
        Totp.buildUri(
            label = entry.label,
            issuer = entry.issuer,
            secret = entry.secret,
            period = entry.period,
            digits = entry.digits,
            algo = entry.algo
        )
    }
    val qrBitmap = remember(revealed, uri) {
        if (revealed) QrGenerator.generate(uri, sizePx = 600) else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.totp_show_qr_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.totp_show_qr_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    entry.displayName(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (revealed && qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.totp_show_qr_title),
                        modifier = Modifier.size(240.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        stringResource(R.string.totp_show_qr_close_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.totp_show_qr_tap_to_reveal),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (revealed) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.totp_show_qr_done)) }
            } else {
                TextButton(onClick = { revealed = true }) {
                    Text(stringResource(R.string.totp_show_qr_reveal))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ─── Duplicate-on-add dialog ──────────────────────────────────────────────

@Composable
private fun DuplicateDialog(
    incoming: TotpEntry,
    existing: TotpEntry,
    onReplace: () -> Unit,
    onKeepBoth: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.totp_duplicate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.totp_duplicate_body, existing.displayName()))
                Text(
                    stringResource(R.string.totp_duplicate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onReplace) { Text(stringResource(R.string.totp_duplicate_replace)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onKeepBoth) { Text(stringResource(R.string.totp_duplicate_keep_both)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

// ─── Settings page ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(
    hideUntilTap: Boolean,
    onHideUntilTapChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.totp_setting_hide_default),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.totp_setting_hide_default_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = hideUntilTap,
                    onCheckedChange = onHideUntilTapChange
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.totp_about_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                stringResource(R.string.totp_about_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
