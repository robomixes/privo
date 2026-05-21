// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.health

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalPharmacy
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.privateai.camera.R
import com.privateai.camera.security.AppPinManager
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.DuressManager
import com.privateai.camera.security.HealthProfile
import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.security.PinRateLimiter
import com.privateai.camera.security.VaultLockManager
import com.privateai.camera.ui.insights.ProfileFilter
import com.privateai.camera.ui.insights.SELF_PROFILE_ID
import com.privateai.camera.ui.onboarding.AuthMode
import com.privateai.camera.ui.onboarding.getAuthMode
import java.io.File

/**
 * Top-level Health feature. Three tabs share one profile filter:
 *
 *   Vitals       — weight / sleep / HR / BP / mood / temperature / steps
 *   Medications  — tracked meds + linked reminders
 *   Cycle        — period tracker + simple last-3-cycles prediction
 *
 * Mirrors [InsightsScreen] for auth + duress handling — those patterns are
 * proven in production. Profile filter uses the existing [ProfileFilter]
 * composable from `ui/insights/` package, imported across packages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    onBack: (() -> Unit)? = null,
    initialTab: Int = 0,
    filterPersonId: String? = null,
    onNavigateToPeople: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val crypto = remember { CryptoManager(context) }
    val repo = remember { InsightsRepository(File(context.filesDir, "vault/insights"), crypto) }

    val startUnlocked = remember { VaultLockManager.isUnlockedWithinGrace(context) && crypto.initialize() }
    var isLocked by remember { mutableStateOf(!startUnlocked) }
    var isDuressActive by remember { mutableStateOf(VaultLockManager.isDuressActive) }
    var selectedTab by remember { mutableIntStateOf(initialTab) }

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
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.health_unlock_title))
            .setSubtitle(context.getString(R.string.health_unlock_subtitle))
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
        if (AppPinManager.verify(context, pin)) {
            PinRateLimiter.recordSuccess(context)
            if (crypto.initialize()) { isDuressActive = false; VaultLockManager.clearDuress(); VaultLockManager.markUnlocked(); isLocked = false; pinInput = ""; pinError = null }
            return
        }
        PinRateLimiter.recordFailure(context)
        val remaining = PinRateLimiter.remainingLockoutMs(context)
        if (remaining > 0) { isLockedOut = true; lockoutRemainingMs = remaining; pinError = null }
        else { pinError = context.getString(R.string.insights_incorrect_pin) }
        pinInput = ""
    }

    LaunchedEffect(Unit) {
        if (isLocked && currentAuthMode == AuthMode.PHONE_LOCK) authenticate()
    }

    if (isLocked) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(stringResource(R.string.feature_health)) }, navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
            })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.health_locked), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
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
                            value = pinInput,
                            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) { pinInput = it; pinError = null } },
                            label = { Text(stringResource(R.string.health_enter_pin)) },
                            modifier = Modifier.width(200.dp), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (pinInput.length >= 4) checkPin(pinInput) }),
                            visualTransformation = PasswordVisualTransformation(),
                            isError = pinError != null,
                            supportingText = { pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                        Button(onClick = { if (pinInput.length >= 4) checkPin(pinInput) }, enabled = pinInput.length >= 4, modifier = Modifier.width(200.dp)) {
                            Text(stringResource(R.string.action_unlock))
                        }
                    }
                } else {
                    Button(onClick = { authenticate() }, modifier = Modifier.width(200.dp)) { Text(stringResource(R.string.action_unlock)) }
                }
            }
        }
        return
    }

    // Profiles — load with the same orphan-cleanup logic InsightsScreen uses.
    var profiles by remember {
        val initial = if (isDuressActive) emptyList()
        else try { repo.loadProfiles() } catch (_: Exception) { emptyList() }
        mutableStateOf(initial)
    }
    val initialProfileId = remember(filterPersonId) {
        if (filterPersonId != null) profiles.find { it.personId == filterPersonId }?.id ?: SELF_PROFILE_ID
        else SELF_PROFILE_ID
    }
    var selectedProfileId by remember { mutableStateOf(initialProfileId) }

    val refreshProfiles: () -> Unit = {
        profiles = try { repo.loadProfiles() } catch (_: Exception) { emptyList() }
    }

    var showLinkDialog by remember { mutableStateOf(false) }
    if (showLinkDialog) {
        // Inline contact picker — keeps this self-contained without exporting
        // InsightsScreen.LinkProfileDialog (which is private). Keep behavior in
        // sync if InsightsScreen's version evolves.
        LinkHealthProfileDialog(
            crypto = crypto,
            existingProfilePersonIds = profiles.mapNotNull { it.personId }.toSet(),
            onPicked = { contact ->
                val newProfile = HealthProfile(name = contact.name, personId = contact.id)
                repo.saveProfiles(profiles + newProfile)
                profiles = repo.loadProfiles()
                selectedProfileId = newProfile.id
                showLinkDialog = false
            },
            onDismiss = { showLinkDialog = false },
            onOpenPeople = onNavigateToPeople?.let { nav ->
                { showLinkDialog = false; nav() }
            }
        )
    }

    val selfLabel = remember {
        try {
            val db = com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)
            val contactRepo = com.privateai.camera.security.ContactRepository(
                File(context.filesDir, "vault/contacts"), crypto, db
            )
            contactRepo.ensureSelfContact(context.getString(R.string.health_myself))
            contactRepo.getSelfContact()?.name ?: context.getString(R.string.health_myself)
        } catch (_: Exception) { context.getString(R.string.health_myself) }
    }

    val tabs = listOf(
        stringResource(R.string.health_tab_vitals) to Icons.Default.FitnessCenter,
        stringResource(R.string.health_tab_meds) to Icons.Default.LocalPharmacy,
        stringResource(R.string.health_tab_cycle) to Icons.Default.CalendarMonth
    )

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.feature_health)) }, navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, (title, icon) ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                        text = { Text(title) }, icon = { Icon(icon, null, Modifier.size(20.dp)) })
                }
            }

            Box(Modifier.fillMaxSize()) {
                if (isDuressActive) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(stringResource(R.string.insights_no_data), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    when (selectedTab) {
                        0 -> VitalsTab(repo, selectedProfileId = selectedProfileId, onProfilesChanged = refreshProfiles)
                        1 -> MedicationsTab(repo, selectedProfileId = selectedProfileId)
                        2 -> CycleTab(repo, selectedProfileId = selectedProfileId)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkHealthProfileDialog(
    crypto: CryptoManager,
    existingProfilePersonIds: Set<String>,
    onPicked: (com.privateai.camera.security.PrivateContact) -> Unit,
    onDismiss: () -> Unit,
    onOpenPeople: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val contacts = remember {
        try {
            val db = com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)
            val contactRepo = com.privateai.camera.security.ContactRepository(
                File(context.filesDir, "vault/contacts"), crypto, db
            )
            contactRepo.listContacts().filter {
                it.id != com.privateai.camera.security.ContactRepository.SELF_CONTACT_ID &&
                        it.id !in existingProfilePersonIds
            }
        } catch (_: Exception) { emptyList() }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.health_link_profile_title)) },
        text = {
            if (contacts.isEmpty()) {
                Text(
                    stringResource(R.string.health_link_profile_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column(
                    Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    contacts.forEach { contact ->
                        androidx.compose.foundation.layout.Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPicked(contact) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("👤", style = MaterialTheme.typography.titleMedium)
                            Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        // Empty contact list → primary action becomes "Open People" so the
        // user has a one-tap path to add someone, instead of needing to back
        // out and navigate manually.
        confirmButton = {
            if (contacts.isEmpty() && onOpenPeople != null) {
                androidx.compose.material3.TextButton(onClick = onOpenPeople) {
                    Text(stringResource(R.string.health_link_profile_open_people))
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
