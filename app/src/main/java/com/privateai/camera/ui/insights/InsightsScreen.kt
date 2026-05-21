// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.insights

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.privateai.camera.R
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.DuressManager
import com.privateai.camera.security.PinRateLimiter
import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.security.VaultLockManager
import com.privateai.camera.ui.onboarding.AuthMode
import com.privateai.camera.ui.onboarding.getAuthMode
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(onBack: (() -> Unit)? = null, initialTab: Int = 0, filterPersonId: String? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val crypto = remember { CryptoManager(context) }
    val repo = remember { InsightsRepository(File(context.filesDir, "vault/insights"), crypto) }

    val startUnlocked = remember { VaultLockManager.isUnlockedWithinGrace(context) && crypto.initialize() }
    var isLocked by remember { mutableStateOf(!startUnlocked) }
    var isDuressActive by remember { mutableStateOf(VaultLockManager.isDuressActive) }
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    // Auto-lock
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> VaultLockManager.markLeft()
                Lifecycle.Event.ON_START -> {
                    if (!isLocked && !VaultLockManager.isUnlockedWithinGrace(context)) {
                        isLocked = true; crypto.lock(); VaultLockManager.lock()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // PIN state
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    val currentAuthMode = remember { getAuthMode(context) }

    fun authenticate() {
        val activity = context as? FragmentActivity ?: return
        val bm = BiometricManager.from(context)
        val canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        if (!canAuth) { if (crypto.initialize()) { VaultLockManager.markUnlocked(); isLocked = false }; return }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (crypto.initialize()) { VaultLockManager.markUnlocked(); isLocked = false }
                }
            })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(context.getString(R.string.insights_unlock_title)).setSubtitle(context.getString(R.string.insights_unlock_subtitle))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build())
    }

    var isLockedOut by remember { mutableStateOf(PinRateLimiter.remainingLockoutMs(context) > 0) }
    var lockoutRemainingMs by remember { mutableStateOf(PinRateLimiter.remainingLockoutMs(context)) }

    LaunchedEffect(isLockedOut) {
        if (isLockedOut) {
            while (true) {
                val remaining = PinRateLimiter.remainingLockoutMs(context)
                if (remaining <= 0) { isLockedOut = false; lockoutRemainingMs = 0L; break }
                lockoutRemainingMs = remaining
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    fun checkPin(pin: String) {
        if (DuressManager.isEnabled(context) && DuressManager.isDuressPin(context, pin)) {
            isDuressActive = true
            VaultLockManager.activateDuress()
            VaultLockManager.markUnlocked(); isLocked = false
            Thread { DuressManager.executeDuress(context, crypto) }.start()
            return
        }

        if (!PinRateLimiter.canAttempt(context)) {
            pinInput = ""
            isLockedOut = true
            lockoutRemainingMs = PinRateLimiter.remainingLockoutMs(context)
            return
        }

        if (com.privateai.camera.security.AppPinManager.verify(context, pin)) {
            PinRateLimiter.recordSuccess(context)
            if (crypto.initialize()) { isDuressActive = false; VaultLockManager.clearDuress(); VaultLockManager.markUnlocked(); isLocked = false; pinInput = ""; pinError = null }
            return
        }

        PinRateLimiter.recordFailure(context)
        val remaining = PinRateLimiter.remainingLockoutMs(context)
        if (remaining > 0) {
            isLockedOut = true
            lockoutRemainingMs = remaining
            pinError = null
        } else {
            pinError = context.getString(R.string.insights_incorrect_pin)
        }
        pinInput = ""
    }

    LaunchedEffect(Unit) {
        if (isLocked && currentAuthMode == AuthMode.PHONE_LOCK) authenticate()
    }

    if (isLocked) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(stringResource(R.string.insights_title)) }, navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
            })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.insights_locked), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
                Spacer(Modifier.height(24.dp))
                if (currentAuthMode == AuthMode.APP_PIN) {
                    if (isLockedOut) {
                        val seconds = (lockoutRemainingMs / 1000).toInt()
                        Text(
                            stringResource(R.string.pin_locked_out, "%d:%02d".format(seconds / 60, seconds % 60)),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        OutlinedTextField(
                            value = pinInput, onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) { pinInput = it; pinError = null } },
                            label = { Text(stringResource(R.string.insights_enter_pin)) }, modifier = Modifier.width(200.dp), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (pinInput.length >= 4) checkPin(pinInput) }),
                            visualTransformation = PasswordVisualTransformation(), isError = pinError != null,
                            supportingText = { pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                        Button(onClick = { if (pinInput.length >= 4) checkPin(pinInput) }, enabled = pinInput.length >= 4, modifier = Modifier.width(200.dp)) { Text(stringResource(R.string.action_unlock)) }
                    }
                } else {
                    Button(onClick = { authenticate() }, modifier = Modifier.width(200.dp)) { Text(stringResource(R.string.action_unlock)) }
                }
            }
        }
        return
    }

    // Unlocked — show tabs. Vitals + Medications moved to the top-level Health
    // feature; Schedule is its own top-level Reminders feature. Insights now
    // hosts only the two cross-cutting trackers that don't belong in Health.
    val tabs = listOf(
        stringResource(R.string.tab_expenses) to Icons.Default.AttachMoney,
        stringResource(R.string.tab_habits) to Icons.Default.CheckCircle
    )

    // Shared profile filter — hoisted so all tabs respect the same selection.
    // All I/O is wrapped: composition must never throw, even immediately after a
    // duress wipe when the SQLCipher DB and vault files may be in flux.
    var profiles by remember {
        val initial = if (isDuressActive) {
            // Duress mode shows nothing anyway; skip orphan cleanup entirely so we
            // don't touch a database that may be mid-rebuild.
            emptyList()
        } else {
            try {
                val all = try { repo.loadProfiles() } catch (_: Exception) { emptyList() }
                val validContactIds = try {
                    val db = com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)
                    val contactRepo = com.privateai.camera.security.ContactRepository(
                        java.io.File(context.filesDir, "vault/contacts"), crypto, db
                    )
                    contactRepo.listContacts().map { it.id }.toSet()
                } catch (_: Exception) { null }
                if (validContactIds != null) {
                    val orphans = all.filter { it.personId != null && it.personId !in validContactIds }
                    if (orphans.isNotEmpty()) {
                        try {
                            val healthEntries = repo.listHealthEntries()
                            orphans.forEach { orphan ->
                                healthEntries.filter { it.profileId == orphan.id }.forEach { repo.deleteHealthEntry(it.id) }
                            }
                            val cleaned = all.filterNot { orphans.contains(it) }
                            repo.saveProfiles(cleaned)
                            cleaned
                        } catch (_: Exception) { all }
                    } else all
                } else all
            } catch (_: Exception) { emptyList() }
        }
        mutableStateOf(initial)
    }
    val initialProfileId = remember(filterPersonId) {
        if (filterPersonId != null) profiles.find { it.personId == filterPersonId }?.id ?: SELF_PROFILE_ID
        else SELF_PROFILE_ID
    }
    var selectedProfileId by remember { mutableStateOf(initialProfileId) }

    // Contact picker dialog state for linking a new profile
    var showLinkDialog by remember { mutableStateOf(false) }

    if (showLinkDialog) {
        LinkProfileDialog(
            crypto = crypto,
            existingProfilePersonIds = profiles.mapNotNull { it.personId }.toSet(),
            onPicked = { contact ->
                val newProfile = com.privateai.camera.security.HealthProfile(
                    name = contact.name,
                    personId = contact.id
                )
                repo.saveProfiles(profiles + newProfile)
                profiles = repo.loadProfiles()
                selectedProfileId = newProfile.id
                showLinkDialog = false
            },
            onDismiss = { showLinkDialog = false }
        )
    }

    // Pull self contact name for the "Me" chip label
    val selfLabel = remember {
        try {
            val db = com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)
            val contactRepo = com.privateai.camera.security.ContactRepository(
                java.io.File(context.filesDir, "vault/contacts"), crypto, db
            )
            contactRepo.ensureSelfContact(context.getString(R.string.health_myself))
            contactRepo.getSelfContact()?.name ?: context.getString(R.string.health_myself)
        } catch (_: Exception) { context.getString(R.string.health_myself) }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.insights_title)) }, navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Profile filter chips — shown only on tabs that are actually profile-scoped.
            // Expenses (tab 0) is always for the self profile (no person linkage), so the
            // chip row is hidden there to avoid implying a filter that does nothing.
            // During duress, show only "Me" (no family profiles, no add button) — same as all other data hidden.
            val showProfileFilter = selectedTab != 0
            if (showProfileFilter) {
                ProfileFilter(
                    profiles = if (isDuressActive) emptyList() else profiles,
                    selectedProfileId = if (isDuressActive) SELF_PROFILE_ID else selectedProfileId,
                    onProfileSelected = { if (!isDuressActive) selectedProfileId = it },
                    onAddProfile = { if (!isDuressActive) showLinkDialog = true },
                    onRemoveProfile = { prof ->
                        if (isDuressActive) return@ProfileFilter
                        repo.listHealthEntries()
                            .filter { it.profileId == prof.id }
                            .forEach { repo.deleteHealthEntry(it.id) }
                        val remaining = profiles.filter { it.id != prof.id }
                        repo.saveProfiles(remaining)
                        profiles = remaining
                    },
                    selfLabel = selfLabel,
                    showAddButton = !isDuressActive
                )
            }

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, (title, icon) ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                        text = { Text(title) }, icon = { Icon(icon, null, Modifier.size(20.dp)) })
                }
            }

            Box(Modifier.fillMaxSize()) {
                if (isDuressActive) {
                    // Duress: show empty
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(stringResource(R.string.insights_no_data), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    when (selectedTab) {
                        // Expenses always uses self — pass SELF_PROFILE_ID so user-side filter is moot.
                        0 -> ExpensesTab(repo, selectedProfileId = SELF_PROFILE_ID)
                        1 -> HabitsTab(repo, selectedProfileId = selectedProfileId)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkProfileDialog(
    crypto: com.privateai.camera.security.CryptoManager,
    existingProfilePersonIds: Set<String>,
    onPicked: (com.privateai.camera.security.PrivateContact) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val contacts = remember {
        try {
            val db = com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)
            val contactRepo = com.privateai.camera.security.ContactRepository(
                java.io.File(context.filesDir, "vault/contacts"), crypto, db
            )
            contactRepo.listContacts().filter {
                it.id != com.privateai.camera.security.ContactRepository.SELF_CONTACT_ID &&
                        it.id !in existingProfilePersonIds
            }
        } catch (_: Exception) { emptyList() }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link a profile") },
        text = {
            if (contacts.isEmpty()) {
                Text("No contacts to link. Add someone in People first, or all your contacts are already linked.")
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    contacts.forEach { contact ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPicked(contact) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("👤", style = MaterialTheme.typography.titleMedium)
                            Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
