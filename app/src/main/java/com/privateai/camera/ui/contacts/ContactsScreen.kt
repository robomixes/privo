// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.contacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.util.UUID
import androidx.compose.foundation.layout.aspectRatio
import com.privateai.camera.security.VaultRepository
import com.privateai.camera.security.VaultPhoto
import com.privateai.camera.security.VaultMediaType
import com.privateai.camera.security.FolderManager
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.privateai.camera.security.ContactRepository
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.PrivoraDatabase
import com.privateai.camera.R
import com.privateai.camera.security.AppPinManager
import com.privateai.camera.security.DuressManager
import com.privateai.camera.security.PinRateLimiter
import com.privateai.camera.security.PrivateContact
import com.privateai.camera.security.VaultLockManager
import com.privateai.camera.ui.onboarding.AuthMode
import com.privateai.camera.ui.onboarding.getAuthMode
import java.io.File
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults

private enum class ContactsPage { LOCKED, LIST, PROFILE, EDITOR }

/** Holds state that survives navigation (static singleton). */
private object ContactsNavState {
    var lastPage: ContactsPage? = null
    var lastContactId: String? = null

    fun save(page: ContactsPage, contactId: String?) {
        lastPage = page
        lastContactId = contactId
        // Refresh unlock timestamp so grace period covers navigation
        VaultLockManager.markUnlocked()
    }

    fun consume(): Pair<ContactsPage?, String?> {
        val p = lastPage
        val c = lastContactId
        lastPage = null
        lastContactId = null
        return p to c
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    onBack: (() -> Unit)? = null,
    onNavigateToInsights: ((personId: String?) -> Unit)? = null,
    onNavigateToNotes: ((personId: String?) -> Unit)? = null,
    onNavigateToVault: ((searchQuery: String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val crypto = remember { CryptoManager(context).also { it.initialize() } }
    val database = remember { PrivoraDatabase.getInstance(context, crypto) }
    val contactRepo = remember { ContactRepository(File(context.filesDir, "vault/contacts"), crypto, database) }

    val startUnlocked = remember {
        VaultLockManager.isUnlockedWithinGrace(context) && crypto.initialize()
    }
    // Page state survives recomposition via rememberSaveable (stores name string)
    var page by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver(
        save = { it.name },
        restore = { try { ContactsPage.valueOf(it) } catch (_: Exception) { ContactsPage.LIST } }
    )) { mutableStateOf(if (startUnlocked) ContactsPage.LIST else ContactsPage.LOCKED) }

    // Check if we should restore to profile from static state
    val restored = remember {
        val (p, c) = ContactsNavState.consume()
        p to c
    }
    LaunchedEffect(Unit) {
        if (restored.first == ContactsPage.PROFILE && restored.second != null) {
            if (startUnlocked) {
                page = ContactsPage.PROFILE
            }
        }
    }
    var isDuressActive by remember { mutableStateOf(VaultLockManager.isDuressActive) }
    var contacts by remember { mutableStateOf<List<PrivateContact>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var allGroups by remember { mutableStateOf<List<String>>(emptyList()) }
    var editingContact by remember { mutableStateOf<PrivateContact?>(null) }
    var savedContactId by rememberSaveable { mutableStateOf(restored.second) }

    // Restore editingContact from saved ID after navigation return (PROFILE only, not EDITOR for new contacts)
    LaunchedEffect(page, savedContactId, contacts) {
        if (editingContact == null && savedContactId != null && contacts.isNotEmpty() && page == ContactsPage.PROFILE) {
            editingContact = contacts.find { it.id == savedContactId }
        }
    }

    // Auto-load contacts if we returned but contacts are empty
    LaunchedEffect(page) {
        if (page != ContactsPage.LOCKED && contacts.isEmpty() && !isDuressActive) {
            if (!crypto.isUnlocked()) crypto.initialize()
            if (crypto.isUnlocked()) {
                contactRepo.ensureSelfContact(context.getString(R.string.health_myself))
                contacts = contactRepo.listContacts()
                allGroups = contactRepo.getGroups()
                if (page == ContactsPage.PROFILE) {
                    savedContactId?.let { id -> editingContact = contacts.find { it.id == id } }
                }
            }
        }
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<PrivateContact?>(null) }

    // Import from phone state
    var showImportDialog by remember { mutableStateOf(false) }
    var phoneContacts by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedImports by remember { mutableStateOf<Set<Int>>(emptySet()) }

    fun cleanPhoneNumber(phone: String): String = phone.replace(Regex("[\\s\\-()]+"), "")

    fun readPhoneContacts(ctx: Context): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val cursor = ctx.contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            val nameIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val phone = it.getString(phoneIdx) ?: ""
                result.add(name to phone)
            }
        }
        return result.distinctBy { it.first + it.second }
    }

    fun openImportDialog() {
        val fetched = readPhoneContacts(context)
        phoneContacts = fetched
        // Pre-deselect already-imported contacts
        val existingPhones = contactRepo.listContacts().map { cleanPhoneNumber(it.phone) }.toSet()
        selectedImports = fetched.indices.filter { idx ->
            val cleaned = cleanPhoneNumber(fetched[idx].second)
            cleaned.isNotEmpty() && cleaned !in existingPhones
        }.toSet()
        showImportDialog = true
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openImportDialog()
        } else {
            Toast.makeText(context, "Contacts permission is needed to import phone contacts", Toast.LENGTH_LONG).show()
        }
    }

    fun onImportClick() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            openImportDialog()
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    // Auto-lock with shared grace period
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> { VaultLockManager.markLeft() }
                Lifecycle.Event.ON_START -> {
                    if (page != ContactsPage.LOCKED && !VaultLockManager.isUnlockedWithinGrace(context)) {
                        page = ContactsPage.LOCKED; crypto.lock(); VaultLockManager.lock()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /** Cascade-delete a contact: removes linked HealthProfile + all their health entries. */
    fun deleteContactCascade(contactId: String) {
        if (contactId == com.privateai.camera.security.ContactRepository.SELF_CONTACT_ID) return
        try {
            val insightsRepo = com.privateai.camera.security.InsightsRepository(
                java.io.File(context.filesDir, "vault/insights"), crypto
            )
            val linkedProfiles = insightsRepo.loadProfiles().filter { it.personId == contactId }
            if (linkedProfiles.isNotEmpty()) {
                // Delete health entries for each linked profile
                val allEntries = insightsRepo.listHealthEntries()
                linkedProfiles.forEach { prof ->
                    allEntries.filter { it.profileId == prof.id }.forEach { insightsRepo.deleteHealthEntry(it.id) }
                }
                // Remove the profile records
                val remaining = insightsRepo.loadProfiles().filter { it.personId != contactId }
                insightsRepo.saveProfiles(remaining)
            }
        } catch (_: Exception) {}
        contactRepo.deleteContact(contactId)
    }

    fun refreshContacts() {
        if (isDuressActive) {
            contacts = emptyList()
            allGroups = emptyList()
            return
        }
        contactRepo.ensureSelfContact(context.getString(R.string.health_myself))
        val all = contactRepo.listContacts()
        contacts = when {
            searchQuery.isNotBlank() -> all.filter { c ->
                c.name.contains(searchQuery, ignoreCase = true) ||
                        c.phone.contains(searchQuery) ||
                        c.email.contains(searchQuery, ignoreCase = true)
            }
            selectedGroup != null -> all.filter { it.group == selectedGroup }
            else -> all
        }
        allGroups = contactRepo.getGroups()
    }

    fun authenticate() {
        val activity = context as? FragmentActivity ?: return
        val bm = BiometricManager.from(context)
        val canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS

        if (!canAuth) {
            if (crypto.initialize()) {
                VaultLockManager.markUnlocked(); refreshContacts(); page = ContactsPage.LIST
            }
            return
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (crypto.initialize()) {
                        VaultLockManager.markUnlocked(); refreshContacts(); page = ContactsPage.LIST
                    }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Contacts")
                .setSubtitle("Authenticate to access your contacts")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
        )
    }

    // Import from Phone dialog
    if (showImportDialog) {
        val existingPhones = remember(contacts) {
            contacts.map { cleanPhoneNumber(it.phone) }.toSet()
        }
        val importableCount = selectedImports.count { idx ->
            val cleaned = cleanPhoneNumber(phoneContacts.getOrNull(idx)?.second ?: "")
            cleaned.isEmpty() || cleaned !in existingPhones
        }

        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import from Phone") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    // Select All / Deselect All
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            selectedImports = phoneContacts.indices.filter { idx ->
                                val cleaned = cleanPhoneNumber(phoneContacts[idx].second)
                                cleaned.isEmpty() || cleaned !in existingPhones
                            }.toSet()
                        }) { Text("Select All") }
                        TextButton(onClick = {
                            selectedImports = emptySet()
                        }) { Text("Deselect All") }
                    }

                    HorizontalDivider()

                    if (phoneContacts.isEmpty()) {
                        Text(
                            "No contacts found on this device",
                            modifier = Modifier.padding(vertical = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                        ) {
                            items(phoneContacts.size) { idx ->
                                val (name, phone) = phoneContacts[idx]
                                val cleanedPhone = cleanPhoneNumber(phone)
                                val alreadyImported = cleanedPhone.isNotEmpty() && cleanedPhone in existingPhones

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !alreadyImported) {
                                            selectedImports = if (idx in selectedImports) {
                                                selectedImports - idx
                                            } else {
                                                selectedImports + idx
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = idx in selectedImports,
                                        onCheckedChange = if (alreadyImported) null else { checked ->
                                            selectedImports = if (checked) {
                                                selectedImports + idx
                                            } else {
                                                selectedImports - idx
                                            }
                                        },
                                        enabled = !alreadyImported
                                    )
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (alreadyImported) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                    else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (alreadyImported) "$phone - Already imported" else phone,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (alreadyImported) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedImports.forEach { idx ->
                            val (name, phone) = phoneContacts[idx]
                            val cleanedPhone = cleanPhoneNumber(phone)
                            if (cleanedPhone.isEmpty() || cleanedPhone !in existingPhones) {
                                contactRepo.saveContact(
                                    PrivateContact(
                                        name = name,
                                        phone = phone.trim()
                                    )
                                )
                            }
                        }
                        showImportDialog = false
                        refreshContacts()
                        Toast.makeText(context, "Imported $importableCount contact(s)", Toast.LENGTH_SHORT).show()
                    },
                    enabled = importableCount > 0
                ) {
                    Text("Import ($importableCount)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Delete dialog
    if (showDeleteDialog && contactToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; contactToDelete = null },
            title = { Text("Delete Contact") },
            text = { Text("Delete \"${contactToDelete!!.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    contactToDelete?.let { deleteContactCascade(it.id) }
                    showDeleteDialog = false
                    contactToDelete = null
                    refreshContacts()
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; contactToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // PIN input state for lock screen
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
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    fun checkPin(enteredPin: String) {
        if (DuressManager.isEnabled(context) && DuressManager.isDuressPin(context, enteredPin)) {
            isDuressActive = true
            VaultLockManager.activateDuress()
            VaultLockManager.markUnlocked()
            contacts = emptyList()
            allGroups = emptyList()
            page = ContactsPage.LIST
            pinInput = ""
            pinError = null
            scope.launch(Dispatchers.IO) { DuressManager.executeDuress(context, crypto) }
            return
        }

        if (!PinRateLimiter.canAttempt(context)) {
            pinInput = ""
            isLockedOut = true
            lockoutRemainingMs = PinRateLimiter.remainingLockoutMs(context)
            return
        }

        if (AppPinManager.verify(context, enteredPin)) {
            PinRateLimiter.recordSuccess(context)
            if (crypto.initialize()) {
                isDuressActive = false
                VaultLockManager.clearDuress()
                VaultLockManager.markUnlocked()
                refreshContacts()
                page = ContactsPage.LIST
                pinInput = ""
                pinError = null
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

    val currentAuthMode = remember { getAuthMode(context) }

    LaunchedEffect(Unit) {
        if (page == ContactsPage.LOCKED) {
            if (currentAuthMode == AuthMode.PHONE_LOCK) {
                authenticate()
            }
        } else {
            refreshContacts()
        }
    }

    when (page) {
        ContactsPage.LOCKED -> {
            Scaffold(topBar = {
                TopAppBar(
                    title = { Text("People") },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
            }) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Contacts are locked", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))

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
                                onValueChange = {
                                    if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                                        pinInput = it
                                        pinError = null
                                    }
                                },
                                label = { Text(stringResource(R.string.enter_pin)) },
                                modifier = Modifier.width(200.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { if (pinInput.length >= 4) checkPin(pinInput) }),
                                visualTransformation = PasswordVisualTransformation(),
                                isError = pinError != null,
                                supportingText = {
                                    if (pinError != null) Text(pinError!!, color = MaterialTheme.colorScheme.error)
                                }
                            )

                            Button(
                                onClick = { if (pinInput.length >= 4) checkPin(pinInput) },
                                enabled = pinInput.length >= 4,
                                modifier = Modifier.width(200.dp)
                            ) { Text(stringResource(R.string.unlock)) }
                        }
                    } else {
                        Button(
                            onClick = { authenticate() },
                            modifier = Modifier.width(200.dp)
                        ) { Text(stringResource(R.string.unlock)) }
                    }
                }
            }
        }

        ContactsPage.LIST -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("People") },
                        navigationIcon = {
                            if (onBack != null) IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { onImportClick() }) {
                                Icon(Icons.Default.ContactPhone, "Import from Phone")
                            }
                        }
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { editingContact = null; savedContactId = null; page = ContactsPage.EDITOR },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Add, "Add contact")
                    }
                }
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Pill-shaped search bar
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Search, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it; refreshContacts() },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 8.dp),
                                textStyle = TextStyle(
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Search contacts",
                                            style = TextStyle(fontSize = 16.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    Icons.Default.Close, "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { searchQuery = ""; refreshContacts() }
                                )
                            }
                        }
                    }

                    // Group filter chips
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedGroup == null,
                            onClick = { selectedGroup = null; refreshContacts() },
                            label = { Text("All") }
                        )
                        allGroups.forEach { group ->
                            FilterChip(
                                selected = selectedGroup == group,
                                onClick = {
                                    selectedGroup = if (selectedGroup == group) null else group
                                    refreshContacts()
                                },
                                label = { Text(group) }
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    if (contacts.isEmpty()) {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Person, null,
                                Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "No contacts yet",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                            Text(
                                "Tap + to add a contact",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val favorites = contacts.filter { it.isFavorite }.sortedBy { it.name.lowercase() }
                        val nonFavorites = contacts.filter { !it.isFavorite }.sortedBy { it.name.lowercase() }
                        val grouped = nonFavorites.groupBy {
                            val first = it.name.firstOrNull()?.uppercaseChar() ?: '#'
                            if (first.isLetter()) first.toString() else "#"
                        }.toSortedMap()

                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Favorites section
                            if (favorites.isNotEmpty()) {
                                item {
                                    Text(
                                        "Favorites",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                items(favorites, key = { it.id }) { contact ->
                                    ContactCard(
                                        contact = contact,
                                        onClick = { editingContact = contact; savedContactId = contact.id; page = ContactsPage.PROFILE },
                                        onLongClick = {
                                            // Self contact is non-deletable — ignore long press
                                            if (contact.id != com.privateai.camera.security.ContactRepository.SELF_CONTACT_ID) {
                                                contactToDelete = contact
                                                showDeleteDialog = true
                                            }
                                        },
                                        onCall = { dialPhone(context, contact.phone) },
                                        onSms = { sendSms(context, contact.phone) }
                                    )
                                }
                            }

                            // Alphabetical sections
                            grouped.forEach { (letter, sectionContacts) ->
                                item {
                                    Text(
                                        letter,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    )
                                }
                                items(sectionContacts, key = { it.id }) { contact ->
                                    ContactCard(
                                        contact = contact,
                                        onClick = { editingContact = contact; savedContactId = contact.id; page = ContactsPage.PROFILE },
                                        onLongClick = {
                                            // Self contact is non-deletable — ignore long press
                                            if (contact.id != com.privateai.camera.security.ContactRepository.SELF_CONTACT_ID) {
                                                contactToDelete = contact
                                                showDeleteDialog = true
                                            }
                                        },
                                        onCall = { dialPhone(context, contact.phone) },
                                        onSms = { sendSms(context, contact.phone) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ContactsPage.PROFILE -> {
            val contact = editingContact
            if (contact == null) { page = ContactsPage.LIST } else {
                val brandColor = MaterialTheme.colorScheme.primary
                Scaffold(topBar = {
                    TopAppBar(
                        title = { Text(contact.name) },
                        navigationIcon = { IconButton(onClick = { page = ContactsPage.LIST }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                        actions = { IconButton(onClick = { page = ContactsPage.EDITOR }) { Icon(Icons.Default.Edit, "Edit", tint = brandColor) } }
                    )
                }) { padding ->
                    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            val profileBmp = remember(contact.id) { contactRepo.loadProfilePhoto(contact.id) }
                            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(80.dp).clip(CircleShape).background(brandColor), contentAlignment = Alignment.Center) {
                                        if (profileBmp != null) {
                                            Image(profileBmp.asImageBitmap(), "Profile", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                                        } else {
                                            Text(contact.name.firstOrNull()?.uppercase() ?: "?", fontSize = 32.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text(contact.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    if (contact.group.isNotBlank()) Text(contact.group, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (contact.isFavorite) Text("★ Favorite", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFA000))
                                    Spacer(Modifier.height(20.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                        if (contact.phone.isNotBlank()) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                IconButton(onClick = { dialPhone(context, contact.phone) }, Modifier.size(56.dp).background(brandColor.copy(alpha = 0.12f), CircleShape)) { Icon(Icons.Default.Phone, "Call", Modifier.size(24.dp), tint = brandColor) }
                                                Spacer(Modifier.height(4.dp))
                                                Text("Call", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                IconButton(onClick = { sendSms(context, contact.phone) }, Modifier.size(56.dp).background(brandColor.copy(alpha = 0.12f), CircleShape)) { Icon(Icons.AutoMirrored.Filled.Message, "Message", Modifier.size(24.dp), tint = brandColor) }
                                                Spacer(Modifier.height(4.dp))
                                                Text("Message", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        if (contact.email.isNotBlank()) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                IconButton(onClick = { sendEmail(context, contact.email) }, Modifier.size(56.dp).background(brandColor.copy(alpha = 0.12f), CircleShape)) { Icon(Icons.Default.Email, "Email", Modifier.size(24.dp), tint = brandColor) }
                                                Spacer(Modifier.height(4.dp))
                                                Text("Email", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (contact.phone.isNotBlank() || contact.email.isNotBlank() || contact.notes.isNotBlank()) {
                            item {
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (contact.phone.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Phone, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(contact.phone) }
                                        if (contact.email.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Email, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(contact.email) }
                                        if (contact.notes.isNotBlank()) Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Notes, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(contact.notes, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                            }
                        }
                        item {
                            // Count linked notes and photos
                            val notesCount = remember(contact.id) {
                                try {
                                    val c = CryptoManager(context).also { it.initialize() }
                                    val noteRepo = com.privateai.camera.security.NoteRepository(java.io.File(context.filesDir, "vault/notes"), c)
                                    noteRepo.listNotes().count { it.personId == contact.id }
                                } catch (_: Exception) { 0 }
                            }
                            val photosCount = remember(contact.id) {
                                try {
                                    val c = CryptoManager(context).also { it.initialize() }
                                    val pi = com.privateai.camera.security.PhotoIndex(com.privateai.camera.security.PrivoraDatabase.getInstance(context, c))
                                    val identity = pi.findIdentityByPersonId(contact.id)
                                    if (identity != null) {
                                        val groups = pi.getFaceGroups()
                                        groups[identity.id]?.map { it.first }?.distinct()?.size ?: 0
                                    } else 0
                                } catch (_: Exception) { 0 }
                            }

                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Connected", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                                    // Health link
                                    Row(Modifier.fillMaxWidth().clickable {
                                        // Ensure a HealthProfile exists for this contact (auto-create on first tap)
                                        if (contact.id != com.privateai.camera.security.ContactRepository.SELF_CONTACT_ID) {
                                            try {
                                                val c = CryptoManager(context).also { it.initialize() }
                                                val repo = com.privateai.camera.security.InsightsRepository(java.io.File(context.filesDir, "vault/insights"), c)
                                                val existing = repo.loadProfiles()
                                                if (existing.none { it.personId == contact.id }) {
                                                    val newProfile = com.privateai.camera.security.HealthProfile(
                                                        name = contact.name,
                                                        personId = contact.id
                                                    )
                                                    repo.saveProfiles(existing + newProfile)
                                                }
                                            } catch (_: Exception) {}
                                        }
                                        ContactsNavState.save(ContactsPage.PROFILE, contact.id)
                                        onNavigateToInsights?.invoke(contact.id)
                                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Box(Modifier.size(36.dp).background(Color(0xFFE91E63).copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Favorite, null, Modifier.size(18.dp), tint = Color(0xFFE91E63))
                                        }
                                        val healthCount = remember(contact.id) {
                                            try {
                                                val c = CryptoManager(context).also { it.initialize() }
                                                val repo = com.privateai.camera.security.InsightsRepository(java.io.File(context.filesDir, "vault/insights"), c)
                                                val profiles = repo.loadProfiles()
                                                val linkedProfile = profiles.find { it.personId == contact.id }
                                                if (linkedProfile != null) repo.listHealthEntries().count { it.profileId == linkedProfile.id } else 0
                                            } catch (_: Exception) { 0 }
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text("Health", fontWeight = FontWeight.Medium)
                                            Text(if (healthCount > 0) "$healthCount entr${if (healthCount > 1) "ies" else "y"}" else "Track health for this person", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (healthCount > 0) {
                                            Box(Modifier.background(Color(0xFFE91E63).copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                                Text("$healthCount", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                                    // Notes link with count
                                    Row(Modifier.fillMaxWidth().clickable { ContactsNavState.save(ContactsPage.PROFILE, contact.id); onNavigateToNotes?.invoke(contact.id) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Box(Modifier.size(36.dp).background(Color(0xFF4E342E).copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.NoteAlt, null, Modifier.size(18.dp), tint = Color(0xFF4E342E))
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text("Notes", fontWeight = FontWeight.Medium)
                                            Text(if (notesCount > 0) "$notesCount note${if (notesCount > 1) "s" else ""}" else "No notes yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (notesCount > 0) {
                                            Box(Modifier.background(Color(0xFF4E342E).copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                                Text("$notesCount", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4E342E), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                                    // Face group link state (declared before Photos row so isLinked is available)
                                    var showLinkFaceDialog by remember { mutableStateOf(false) }
                                    var linkCheckKey by remember { mutableStateOf(0) }
                                    val linkedIdentity = remember(contact.id, linkCheckKey) {
                                        try {
                                            val c = CryptoManager(context).also { it.initialize() }
                                            val pi = com.privateai.camera.security.PhotoIndex(com.privateai.camera.security.PrivoraDatabase.getInstance(context, c))
                                            pi.findIdentityByPersonId(contact.id)
                                        } catch (_: Exception) { null }
                                    }
                                    var isLinked by remember(linkCheckKey) { mutableStateOf(linkedIdentity != null) }

                                    // Photos link — works if linked OR has profile photo
                                    val hasProfilePhoto = remember(contact.id, linkCheckKey) {
                                        try { val c = CryptoManager(context).also { it.initialize() }; ContactRepository(java.io.File(context.filesDir, "vault/contacts"), c, PrivoraDatabase.getInstance(context, c)).loadProfilePhoto(contact.id) != null } catch (_: Exception) { false }
                                    }
                                    val canShowPhotos = isLinked || hasProfilePhoto
                                    Row(Modifier.fillMaxWidth().clickable {
                                        if (canShowPhotos) {
                                            // Search by personId if linked, or by name + personId
                                            ContactsNavState.save(ContactsPage.PROFILE, contact.id)
                                            onNavigateToVault?.invoke("${contact.name} ${contact.id}")
                                        } else {
                                            android.widget.Toast.makeText(context, "Add a profile photo or link a face group", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Box(Modifier.size(36.dp).background(Color(0xFF1565C0).copy(alpha = if (canShowPhotos) 0.1f else 0.05f), CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Photo, null, Modifier.size(18.dp), tint = Color(0xFF1565C0).copy(alpha = if (canShowPhotos) 1f else 0.4f))
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text("Photos", fontWeight = FontWeight.Medium, color = if (canShowPhotos) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                            Text(
                                                when {
                                                    isLinked && photosCount > 0 -> "$photosCount photo${if (photosCount > 1) "s" else ""}"
                                                    isLinked -> "No photos yet"
                                                    hasProfilePhoto -> "AI-matched photos"
                                                    else -> "Add profile photo or link face group"
                                                },
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (photosCount > 0 && canShowPhotos) {
                                            Box(Modifier.background(Color(0xFF1565C0).copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                                Text("$photosCount", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isLinked) {
                                        // Show linked status with unlink option
                                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Box(Modifier.size(36.dp).background(Color(0xFF4CAF50).copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = Color(0xFF4CAF50))
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text("Face Group Linked", fontWeight = FontWeight.Medium, color = Color(0xFF4CAF50))
                                                Text(linkedIdentity?.name ?: "Unknown", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            // Relink button
                                            TextButton(onClick = { showLinkFaceDialog = true }) { Text("Change") }
                                            // Unlink button
                                            TextButton(onClick = {
                                                try {
                                                    val c = CryptoManager(context).also { it.initialize() }
                                                    val pi = com.privateai.camera.security.PhotoIndex(com.privateai.camera.security.PrivoraDatabase.getInstance(context, c))
                                                    pi.unlinkFaceGroupFromPerson(contact.id)
                                                    isLinked = false; linkCheckKey++
                                                } catch (_: Exception) {}
                                            }) { Text("Unlink", color = MaterialTheme.colorScheme.error) }
                                        }
                                    } else {
                                        // Show link button
                                        Row(Modifier.fillMaxWidth().clickable { showLinkFaceDialog = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Box(Modifier.size(36.dp).background(Color(0xFF1565C0).copy(alpha = 0.05f), CircleShape), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Person, null, Modifier.size(18.dp), tint = Color(0xFF1565C0).copy(alpha = 0.7f))
                                            }
                                            Column(Modifier.weight(1f)) { Text("Link Face Group", fontWeight = FontWeight.Medium); Text("Manually connect a face group", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    if (showLinkFaceDialog) {
                                        LinkFaceGroupDialog(
                                            contactId = contact.id,
                                            contactName = contact.name,
                                            onDismiss = { showLinkFaceDialog = false },
                                            onLinked = { showLinkFaceDialog = false; linkCheckKey++; isLinked = true }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        ContactsPage.EDITOR -> {
            ContactEditorScreen(
                contact = editingContact,
                allGroups = allGroups,
                contactRepo = contactRepo,
                onSave = { contact ->
                    contactRepo.saveContact(contact)
                    editingContact = contact
                    refreshContacts()
                    page = ContactsPage.PROFILE
                },
                onDelete = {
                    editingContact?.let { deleteContactCascade(it.id) }
                    refreshContacts()
                    page = ContactsPage.LIST
                },
                onBack = { page = ContactsPage.PROFILE }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactCard(
    contact: PrivateContact,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCall: () -> Unit,
    onSms: () -> Unit
) {
    val brandColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val profileBmp = remember(contact.id) {
        try { val c = CryptoManager(context).also { it.initialize() }; ContactRepository(java.io.File(context.filesDir, "vault/contacts"), c, PrivoraDatabase.getInstance(context, c)).loadProfilePhoto(contact.id) } catch (_: Exception) { null }
    }
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Avatar with profile photo
            Box(Modifier.size(48.dp).clip(CircleShape).background(brandColor), contentAlignment = Alignment.Center) {
                if (profileBmp != null) {
                    Image(profileBmp.asImageBitmap(), "Profile", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                } else {
                    Text(contact.name.firstOrNull()?.uppercase() ?: "?", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(contact.name, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (contact.isFavorite) { Spacer(Modifier.width(6.dp)); Icon(Icons.Default.Star, "Favorite", Modifier.size(16.dp), tint = brandColor) }
                }
                if (contact.phone.isNotBlank()) Text(contact.phone, style = TextStyle(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), maxLines = 1)
            }
            if (contact.phone.isNotBlank()) {
                IconButton(onClick = onCall, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Phone, "Call", Modifier.size(20.dp), tint = brandColor) }
                IconButton(onClick = onSms, modifier = Modifier.size(40.dp)) { Icon(Icons.AutoMirrored.Filled.Message, "SMS", Modifier.size(20.dp), tint = brandColor) }
            }
        }
        HorizontalDivider(Modifier.padding(start = 78.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactEditorScreen(
    contact: PrivateContact?,
    allGroups: List<String>,
    contactRepo: ContactRepository,
    onSave: (PrivateContact) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val isNew = contact == null
    var name by remember { mutableStateOf(contact?.name ?: "") }
    var phone by remember { mutableStateOf(contact?.phone ?: "") }
    var email by remember { mutableStateOf(contact?.email ?: "") }
    var group by remember { mutableStateOf(contact?.group ?: "") }
    var notes by remember { mutableStateOf(contact?.notes ?: "") }
    var isFavorite by remember { mutableStateOf(contact?.isFavorite ?: false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var newGroup by remember { mutableStateOf("") }
    var showGroupInput by remember { mutableStateOf(false) }
    var showPhotoPicker by remember { mutableStateOf(false) }
    var profilePhoto by remember { mutableStateOf<Bitmap?>(null) }

    val context = LocalContext.current
    val brandColor = MaterialTheme.colorScheme.primary
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Load existing profile photo
    LaunchedEffect(contact?.id) {
        contact?.id?.let { cId ->
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                profilePhoto = contactRepo.loadProfilePhoto(cId)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Contact") },
            text = { Text("Delete this contact permanently?") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Favorite toggle
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            "Favorite",
                            tint = if (isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Hide delete for the non-deletable "Myself" contact
                    if (!isNew && contact?.id != com.privateai.camera.security.ContactRepository.SELF_CONTACT_ID) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(
                        onClick = {
                            val saved = if (contact != null) {
                                contact.copy(
                                    name = name.trim(),
                                    phone = phone.trim(),
                                    email = email.trim(),
                                    group = group.trim(),
                                    notes = notes.trim(),
                                    isFavorite = isFavorite,
                                    modifiedAt = System.currentTimeMillis()
                                )
                            } else {
                                PrivateContact(
                                    name = name.trim(),
                                    phone = phone.trim(),
                                    email = email.trim(),
                                    group = group.trim(),
                                    notes = notes.trim(),
                                    isFavorite = isFavorite
                                )
                            }
                            onSave(saved)
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Check, "Save",
                            tint = if (name.isNotBlank()) brandColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Profile photo — reduced size, gradient placeholder
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape)
                        .then(
                            if (profilePhoto != null) Modifier
                            else Modifier.background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(brandColor.copy(alpha = 0.7f), brandColor)),
                                CircleShape
                            )
                        )
                        .clickable { showPhotoPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (profilePhoto != null) {
                        Image(profilePhoto!!.asImageBitmap(), "Profile", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                    } else {
                        Icon(Icons.Default.Person, null, Modifier.size(36.dp), tint = Color.White.copy(alpha = 0.8f))
                    }
                }
                // Camera badge with white border + shadow
                Box(
                    Modifier.align(Alignment.BottomCenter).offset(x = 24.dp, y = (-2).dp).size(26.dp)
                        .background(Color.White, CircleShape)
                        .border(1.5.dp, brandColor, CircleShape)
                        .clickable { showPhotoPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, null, Modifier.size(14.dp), tint = brandColor)
                }
                if (profilePhoto != null) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 2.dp).size(22.dp)
                            .background(Color.White, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.error, CircleShape)
                            .clickable { contact?.id?.let { contactRepo.deleteProfilePhoto(it) }; profilePhoto = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, "Remove", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // === CONTACT INFO SECTION ===
            Text("Contact Info", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

            // Name — outlined text field style
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") },
                leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(20.dp)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))

            // Phone — outlined with icon
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Phone") },
                leadingIcon = { Icon(Icons.Default.Phone, null, Modifier.size(20.dp)) },
                trailingIcon = {
                    if (phone.isNotBlank()) {
                        IconButton(onClick = { dialPhone(context, phone) }, Modifier.size(32.dp)) {
                            Icon(Icons.Default.Phone, "Call", Modifier.size(18.dp), tint = brandColor)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))

            // Email — outlined with icon
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, null, Modifier.size(20.dp)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(Modifier.height(16.dp))

            // === ORGANIZATION SECTION ===
            Text("Organization", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

            // Group — chips
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val commonGroups = (allGroups + listOf("Family", "Work", "Friends")).distinct()
                commonGroups.forEach { g ->
                    FilterChip(
                        selected = group == g,
                        onClick = { group = if (group == g) "" else g },
                        label = { Text(g) },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
                if (showGroupInput) {
                    OutlinedTextField(
                        value = newGroup, onValueChange = { newGroup = it },
                        placeholder = { Text("Group") },
                        singleLine = true,
                        modifier = Modifier.width(100.dp).height(48.dp),
                        shape = RoundedCornerShape(20.dp),
                        textStyle = TextStyle(fontSize = 14.sp)
                    )
                    IconButton(onClick = {
                        if (newGroup.isNotBlank()) { group = newGroup.trim(); newGroup = "" }
                        showGroupInput = false
                    }, Modifier.size(28.dp)) {
                        Icon(Icons.Default.Check, "Add", Modifier.size(16.dp), tint = brandColor)
                    }
                } else {
                    FilterChip(
                        selected = false,
                        onClick = { showGroupInput = true },
                        label = { Text("+ New") },
                        shape = RoundedCornerShape(20.dp),
                        border = FilterChipDefaults.filterChipBorder(borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), enabled = true, selected = false)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // Notes — multi-line expandable
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes") },
                leadingIcon = { Icon(Icons.Default.Notes, null, Modifier.size(20.dp)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(24.dp))

            // Photo picker dialog
            if (showPhotoPicker) {
                PhotoPickerForProfile(
                    onDismiss = { showPhotoPicker = false },
                    onPhotoPicked = { bitmap ->
                        showPhotoPicker = false
                        // Detect face and crop, or use full image if no face
                        scope.launch {
                            val contactId = contact?.id ?: UUID.randomUUID().toString()
                            kotlinx.coroutines.withContext(Dispatchers.IO) {
                                // Try face detection + crop. Track A1.2:
                                // switched from ML Kit to the ONNX
                                // FaceDetector. We pick the largest detected
                                // face (most likely the contact's), expand
                                // ~30% for breathing room, and crop.
                                val croppedFace = try {
                                    val faces = com.privateai.camera.bridge.FaceDetectorHolder.get(context).detect(bitmap)
                                    val biggest = faces.maxByOrNull { it.width * it.height }
                                    if (biggest != null) {
                                        val expand = (biggest.width * 0.3f).toInt()
                                        val left = (biggest.left - expand).coerceAtLeast(0)
                                        val top = (biggest.top - expand).coerceAtLeast(0)
                                        val right = (biggest.right + expand).coerceAtMost(bitmap.width)
                                        val bottom = (biggest.bottom + expand).coerceAtMost(bitmap.height)
                                        Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                                    } else null
                                } catch (_: Exception) { null }

                                val photoToSave = croppedFace ?: bitmap
                                // Scale to 300x300 max
                                val scale = 300f / maxOf(photoToSave.width, photoToSave.height)
                                val scaled = if (scale < 1f) Bitmap.createScaledBitmap(photoToSave, (photoToSave.width * scale).toInt(), (photoToSave.height * scale).toInt(), true) else photoToSave
                                contactRepo.saveProfilePhoto(contactId, scaled)
                                profilePhoto = scaled
                                // Clear unlinked status so auto-match can work with new photo
                                try {
                                    val cr = CryptoManager(context).also { it.initialize() }
                                    val pi = com.privateai.camera.security.PhotoIndex(com.privateai.camera.security.PrivoraDatabase.getInstance(context, cr))
                                    pi.clearUnlinkedStatus(contactId)
                                    // Try immediate auto-match
                                    val fe = try { com.privateai.camera.bridge.FaceEmbedder(context) } catch (_: Exception) { null }
                                    if (fe != null) {
                                        pi.autoNameFromContacts(contactRepo, fe)
                                        fe.release()
                                    }
                                } catch (_: Exception) {}
                                if (croppedFace != null && croppedFace !== scaled) croppedFace.recycle()
                            }
                        }
                    }
                )
            }

        }
    }
}

/* Old editor fields removed — replaced by structured OutlinedTextField layout above
// Phone field with icon prefix
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Phone, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    cursorBrush = SolidColor(brandColor),
                    decorationBox = { innerTextField ->
                        if (phone.isEmpty()) {
                            Text(
                                "Phone",
                                style = TextStyle(fontSize = 16.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                        innerTextField()
                    }
                )
                // Quick dial button
                if (phone.isNotBlank()) {
                    IconButton(
                        onClick = { dialPhone(context, phone) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Phone, "Call",
                            tint = brandColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Email field with icon prefix
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Email, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    cursorBrush = SolidColor(brandColor),
                    decorationBox = { innerTextField ->
                        if (email.isEmpty()) {
                            Text(
                                "Email",
                                style = TextStyle(fontSize = 16.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                        innerTextField()
                    }
                )
                // Quick email button
                if (email.isNotBlank()) {
                    IconButton(
                        onClick = { sendEmail(context, email) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Email, "Email",
                            tint = brandColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Group selector — chips
            Text(
                "Group",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                allGroups.forEach { g ->
                    FilterChip(
                        selected = group == g,
                        onClick = { group = if (group == g) "" else g },
                        label = { Text(g) }
                    )
                }
                if (showGroupInput) {
                    BasicTextField(
                        value = newGroup,
                        onValueChange = { newGroup = it },
                        modifier = Modifier
                            .width(100.dp)
                            .border(1.dp, brandColor, RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(brandColor),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )
                    IconButton(
                        onClick = {
                            if (newGroup.isNotBlank()) { group = newGroup.trim(); newGroup = "" }
                            showGroupInput = false
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Check, "Add group", Modifier.size(16.dp), tint = brandColor)
                    }
                } else {
                    FilterChip(
                        selected = false,
                        onClick = { showGroupInput = true },
                        label = { Text("+ New") }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Notes field — borderless, expandable
            BasicTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(vertical = 4.dp),
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                cursorBrush = SolidColor(brandColor),
                decorationBox = { innerTextField ->
                    if (notes.isEmpty()) {
                        Text(
                            "Notes",
                            style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}
Old editor fields end */

// --- Intent helpers ---

private fun dialPhone(context: Context, phone: String) {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
}

private fun sendSms(context: Context, phone: String) {
    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")))
}

private fun sendEmail(context: Context, email: String) {
    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
}

/**
 * Photo picker dialog for selecting a profile photo from the vault.
 * Shows all photos, user taps one → callback with the full bitmap.
 */
@Composable
private fun PhotoPickerForProfile(
    onDismiss: () -> Unit,
    onPhotoPicked: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<VaultPhoto>>(emptyList()) }
    var thumbnails by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val crypto = CryptoManager(context).also { it.initialize() }
        val vault = VaultRepository(context, crypto)
        val folderManager = FolderManager(context, crypto)
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val folderItems = folderManager.listAllFolders().flatMap { f -> vault.listFolderItems(folderManager.getFolderDir(f.id)) }
            val allPhotos = (vault.listAllPhotos() + folderItems).distinctBy { it.id }
                .filter { it.mediaType == VaultMediaType.PHOTO }.sortedByDescending { it.timestamp }
            val thumbs = mutableMapOf<String, Bitmap>()
            allPhotos.forEach { p -> vault.loadThumbnail(p)?.let { thumbs[p.id] = it } }
            photos = allPhotos
            thumbnails = thumbs
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Photo") },
        text = {
            if (isLoading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("Loading...")
                }
            } else if (photos.isEmpty()) {
                Text("No photos in vault")
            } else {
                Column(Modifier.height(400.dp).verticalScroll(rememberScrollState())) {
                    val itemSize = 100.dp
                    photos.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                            row.forEach { photo ->
                                val thumb = thumbnails[photo.id]
                                Box(
                                    Modifier.size(itemSize).clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            val crypto2 = CryptoManager(context).also { it.initialize() }
                                            val vault2 = VaultRepository(context, crypto2)
                                            val fullBitmap = vault2.loadFullPhoto(photo)
                                            if (fullBitmap != null) onPhotoPicked(fullBitmap)
                                            else onDismiss()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (thumb != null) {
                                        Image(thumb.asImageBitmap(), "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Icon(Icons.Default.Photo, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Dialog to manually link a face group to a person.
 * Shows all face groups with thumbnails, user taps one to link.
 */
@Composable
private fun LinkFaceGroupDialog(
    contactId: String,
    contactName: String,
    onDismiss: () -> Unit,
    onLinked: () -> Unit
) {
    val context = LocalContext.current
    var faceGroups by remember { mutableStateOf<Map<String, List<Triple<String, Int, com.privateai.camera.security.PhotoIndex.FaceEntry>>>>(emptyMap()) }
    var groupThumbs by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var confirmGroupId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val crypto = CryptoManager(context).also { it.initialize() }
                val vault = VaultRepository(context, crypto)
                val folderManager = com.privateai.camera.security.FolderManager(context, crypto)
                val pi = com.privateai.camera.security.PhotoIndex(com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto))
                val groups = pi.getFaceGroups()
                faceGroups = groups

                // Load thumbnails — include folder items, try multiple members
                val allItems = (vault.listAllPhotos() + folderManager.listAllFolders().flatMap { f -> vault.listFolderItems(folderManager.getFolderDir(f.id)) }).distinctBy { it.id }.associateBy { it.id }
                val thumbs = mutableMapOf<String, Bitmap>()
                groups.forEach { (gId, members) ->
                    for (m in members) {
                        allItems[m.first]?.let { photo ->
                            vault.loadThumbnail(photo)?.let { thumbs[gId] = it; return@forEach }
                        }
                    }
                }
                groupThumbs = thumbs
            } catch (_: Exception) {}
        }
        isLoading = false
    }

    // Confirm dialog — reuse the same PhotoIndex that loaded the groups
    confirmGroupId?.let { gId ->
        val crypto = remember { CryptoManager(context).also { it.initialize() } }
        val pi = remember { com.privateai.camera.security.PhotoIndex(com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto)) }
        val groupName = pi.getFaceGroupName(gId) ?: "Unknown"
        AlertDialog(
            onDismissRequest = { confirmGroupId = null },
            title = { Text("Link Face Group") },
            text = { Text("Connect face group \"$groupName\" to \"$contactName\"?\n\nThis will name the face group as \"$contactName\" so all photos of this face show under this person.") },
            confirmButton = {
                TextButton(onClick = {
                    pi.linkFaceGroupToPerson(gId, contactId, contactName)
                    confirmGroupId = null
                    onLinked()
                }) { Text("Link") }
            },
            dismissButton = { TextButton(onClick = { confirmGroupId = null }) { Text("Cancel") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Face Group") },
        text = {
            if (isLoading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { Text("Loading...") }
            } else if (faceGroups.isEmpty()) {
                Text("No face groups found. Index photos first.")
            } else {
                // Only show unnamed groups or groups not yet linked to another person
                val crypto2 = remember { CryptoManager(context).also { it.initialize() } }
                val pi2 = remember { com.privateai.camera.security.PhotoIndex(com.privateai.camera.security.PrivoraDatabase.getInstance(context, crypto2)) }
                // Show ALL groups — user can remap any group to this person
                val unlinkableGroups = faceGroups
                Column(Modifier.height(400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (unlinkableGroups.isEmpty()) {
                        Text("All face groups are already named. Use 'Merge' in Vault → Faces to combine groups.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    unlinkableGroups.forEach { (gId, members) ->
                        val thumb = groupThumbs[gId]
                        val photoCount = members.map { it.first }.distinct().size
                        val currentName = pi2.getFaceGroupName(gId) ?: "Unknown"

                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { confirmGroupId = gId }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                if (thumb != null) {
                                    Image(thumb.asImageBitmap(), "Face", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                                } else {
                                    Icon(Icons.Default.Person, null, Modifier.size(32.dp), tint = Color.White)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(currentName, fontWeight = FontWeight.Medium)
                                Text("$photoCount photos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
