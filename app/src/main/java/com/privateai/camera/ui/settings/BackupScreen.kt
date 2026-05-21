// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.privateai.camera.R
import com.privateai.camera.security.BackupManager
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.service.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class BackupPage { MENU, EXPORT, IMPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: (() -> Unit)? = null, onImportComplete: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val crypto = remember { CryptoManager(context).also { it.initialize() } }
    val backupManager = remember { BackupManager(context, crypto) }

    var page by remember { mutableStateOf(BackupPage.MENU) }
    var isWorking by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressLabel by remember { mutableStateOf("") }

    // Export state
    var exportPassword by remember { mutableStateOf("") }
    var exportPasswordConfirm by remember { mutableStateOf("") }
    var showExportPassword by remember { mutableStateOf(false) }

    // Import state
    var importPassword by remember { mutableStateOf("") }
    var showImportPassword by remember { mutableStateOf(false) }
    var selectedBackupUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedBackupName by remember { mutableStateOf("") }

    // Refreshable stats — recalculated when statsVersion changes
    var statsVersion by remember { mutableStateOf(0) }
    val stats = remember(statsVersion) { backupManager.getBackupStats() }

    // Error dialog
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedBackupUri = uri
            selectedBackupName = uri.lastPathSegment?.substringAfterLast("/") ?: "backup.paicbackup"
        }
    }

    val passwordStrength: (String) -> String = { pw ->
        when {
            pw.length < 8 -> "Too short (min 8)"
            pw.length < 12 && pw.all { it.isLetterOrDigit() } -> "Weak"
            pw.length < 12 -> "Medium"
            else -> "Strong"
        }
    }

    // Error dialog
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Import Failed") },
            text = { Text(errorMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("OK") }
            }
        )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(when (page) {
                    BackupPage.MENU -> "Backup & Migration"
                    BackupPage.EXPORT -> "Export Backup"
                    BackupPage.IMPORT -> "Import Backup"
                })
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (isWorking) return@IconButton
                    when (page) {
                        BackupPage.MENU -> onBack?.invoke()
                        else -> page = BackupPage.MENU
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        )
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (page) {
                // ===== MENU =====
                BackupPage.MENU -> {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                                Text("Why backup?", style = MaterialTheme.typography.titleSmall)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Your data is encrypted with a key unique to this phone. " +
                                "If you change phones, uninstall the app, or factory reset, all data is lost forever. " +
                                "Create a backup to transfer everything to a new device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.backup_your_data), style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            // Render every module that has at least one item.
                            // Modules with zero items are hidden so a fresh
                            // device doesn't show a wall of "0 X / 0 Y / 0 Z".
                            // Order roughly mirrors the home grid prominence.
                            val rows: List<Pair<Int, Int>> = buildList {
                                if (stats.photoCount > 0) add(stats.photoCount to R.string.backup_summary_photos)
                                if (stats.videoCount > 0) add(stats.videoCount to R.string.backup_summary_videos)
                                if (stats.pdfCount > 0) add(stats.pdfCount to R.string.backup_summary_pdfs)
                                if (stats.fileCount > 0) add(stats.fileCount to R.string.backup_summary_files)
                                if (stats.folderCount > 0) add(stats.folderCount to R.string.backup_summary_folders)
                                if (stats.starredCount > 0) add(stats.starredCount to R.string.backup_summary_starred)
                                if (stats.ocrSidecarCount > 0) add(stats.ocrSidecarCount to R.string.backup_summary_ocr)
                                if (stats.noteCount > 0) add(stats.noteCount to R.string.backup_summary_notes)
                                if (stats.reminderCount > 0) add(stats.reminderCount to R.string.backup_summary_reminders)
                                if (stats.expenseCount > 0) add(stats.expenseCount to R.string.backup_summary_expenses)
                                if (stats.healthCount > 0) add(stats.healthCount to R.string.backup_summary_health)
                                if (stats.medicationCount > 0) add(stats.medicationCount to R.string.backup_summary_medications)
                                if (stats.cycleCount > 0) add(stats.cycleCount to R.string.backup_summary_cycle)
                                if (stats.habitCount > 0) add(stats.habitCount to R.string.backup_summary_habits)
                                if (stats.contactCount > 0) add(stats.contactCount to R.string.backup_summary_contacts)
                                if (stats.totpCount > 0) add(stats.totpCount to R.string.backup_summary_totp)
                                if (stats.passwordHintCount > 0) add(stats.passwordHintCount to R.string.backup_summary_passwords)
                                if (stats.trashCount > 0) add(stats.trashCount to R.string.backup_summary_trash)
                            }
                            if (rows.isEmpty()) {
                                Text(
                                    stringResource(R.string.backup_summary_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                rows.forEach { (count, key) ->
                                    Text(
                                        stringResource(key, count),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.backup_summary_size, StorageManager.formatSize(stats.totalSizeBytes)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Prefs / settings carried with the backup —
                            // user-facing reminder that toggles + UI prefs
                            // travel along with the encrypted blobs.
                            Text(
                                stringResource(R.string.backup_summary_includes_settings),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { page = BackupPage.EXPORT },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !stats.isEmpty
                    ) {
                        Icon(Icons.Default.CloudUpload, null, Modifier.size(20.dp))
                        Text("  Export Backup")
                    }

                    OutlinedButton(
                        onClick = { page = BackupPage.IMPORT },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, null, Modifier.size(20.dp))
                        Text("  Import Backup")
                    }
                }

                // ===== EXPORT =====
                BackupPage.EXPORT -> {
                    if (isWorking) {
                        Text("Creating backup...", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(progressLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(
                            "Create a password-protected backup containing your encryption key and all vault data.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                Text(
                                    "Write this password down! There is no way to recover your backup if you forget it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        OutlinedTextField(
                            value = exportPassword,
                            onValueChange = { exportPassword = it },
                            label = { Text("Backup password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showExportPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showExportPassword = !showExportPassword }) {
                                    Icon(
                                        if (showExportPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        "Toggle visibility"
                                    )
                                }
                            },
                            supportingText = {
                                if (exportPassword.isNotEmpty()) {
                                    val strength = passwordStrength(exportPassword)
                                    val strengthColor = when (strength) {
                                        "Too short (min 8)", "Weak" -> MaterialTheme.colorScheme.error
                                        "Medium" -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                    Text("Strength: $strength", color = strengthColor)
                                }
                            }
                        )

                        OutlinedTextField(
                            value = exportPasswordConfirm,
                            onValueChange = { exportPasswordConfirm = it },
                            label = { Text("Confirm password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showExportPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = exportPasswordConfirm.isNotEmpty() && exportPassword != exportPasswordConfirm,
                            supportingText = {
                                if (exportPasswordConfirm.isNotEmpty() && exportPassword != exportPasswordConfirm) {
                                    Text("Passwords don't match", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        )

                        val canExport = exportPassword.length >= 8 && exportPassword == exportPasswordConfirm

                        Button(
                            onClick = {
                                isWorking = true
                                progressLabel = "Wrapping encryption key..."
                                progress = 0f
                                scope.launch {
                                    try {
                                        val backupFile = withContext(Dispatchers.IO) {
                                            backupManager.exportBackup(exportPassword) { current, total, label ->
                                                progress = current.toFloat() / total.coerceAtLeast(1)
                                                progressLabel = label
                                            }
                                        }
                                        // Save to Downloads folder via MediaStore (works on Android 10+)
                                        var savedToDownloads = false
                                        withContext(Dispatchers.IO) {
                                            try {
                                                val values = android.content.ContentValues().apply {
                                                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, backupFile.name)
                                                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                                                    }
                                                }
                                                val resolver = context.contentResolver
                                                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                                                if (uri != null) {
                                                    resolver.openOutputStream(uri)?.use { out ->
                                                        backupFile.inputStream().use { inp -> inp.copyTo(out) }
                                                    }
                                                    savedToDownloads = true
                                                }
                                            } catch (_: Exception) {}
                                        }

                                        // Also share via intent
                                        val uri = FileProvider.getUriForFile(
                                            context, "${context.packageName}.fileprovider", backupFile
                                        )
                                        context.startActivity(Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "application/octet-stream"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }, "Save Backup"
                                        ))
                                        isWorking = false
                                        val msg = if (savedToDownloads)
                                            "Backup saved to Downloads (${StorageManager.formatSize(backupFile.length())})"
                                        else
                                            "Backup created (${StorageManager.formatSize(backupFile.length())})"
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        isWorking = false
                                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canExport
                        ) {
                            Text("Create Backup")
                        }

                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("How it works", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Your encryption key is wrapped with your password and stored inside the backup file. " +
                                    "Your photos, videos, and notes stay encrypted — they are never decrypted during backup. " +
                                    "This makes backup fast and secure.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ===== IMPORT =====
                BackupPage.IMPORT -> {
                    if (isWorking) {
                        Text("Restoring from backup...", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(progressLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(
                            "Restore from a .paicbackup file. This will install the encryption key and extract all vault data.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (!stats.isEmpty) {
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    Text(
                                        "This device has existing vault data. Importing will replace the encryption key. " +
                                        "Items already in the vault encrypted with the current key may become unreadable.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (selectedBackupUri != null) selectedBackupName else "Select backup file (.paicbackup)")
                        }

                        if (selectedBackupUri != null) {
                            OutlinedTextField(
                                value = importPassword,
                                onValueChange = { importPassword = it },
                                label = { Text("Backup password") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (showImportPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { showImportPassword = !showImportPassword }) {
                                        Icon(
                                            if (showImportPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            "Toggle visibility"
                                        )
                                    }
                                }
                            )

                            Button(
                                onClick = {
                                    isWorking = true
                                    progress = 0f
                                    progressLabel = "Reading backup..."
                                    scope.launch {
                                        try {
                                            val tempFile = withContext(Dispatchers.IO) {
                                                val temp = File(context.cacheDir, "import_backup.paicbackup")
                                                context.contentResolver.openInputStream(selectedBackupUri ?: return@withContext temp)?.use { input ->
                                                    temp.outputStream().use { output -> input.copyTo(output) }
                                                }
                                                temp
                                            }

                                            val (imported, skipped) = withContext(Dispatchers.IO) {
                                                backupManager.importBackup(tempFile, importPassword) { current, total, label ->
                                                    progress = current.toFloat() / total.coerceAtLeast(1)
                                                    progressLabel = label
                                                }
                                            }

                                            withContext(Dispatchers.IO) { tempFile.delete() }
                                            isWorking = false
                                            statsVersion++ // refresh data counts

                                            val summary = "Restored $imported files" +
                                                if (skipped > 0) " ($skipped already existed)" else ""

                                            if (onImportComplete != null) {
                                                onImportComplete(summary)
                                            } else {
                                                Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
                                                page = BackupPage.MENU
                                            }
                                        } catch (e: IllegalArgumentException) {
                                            isWorking = false
                                            errorMessage = if (e.message?.contains("password", ignoreCase = true) == true ||
                                                e.message?.contains("Wrong", ignoreCase = true) == true) {
                                                "Wrong password or the backup file is corrupted.\nPlease check your password and try again."
                                            } else if (e.message?.contains("key", ignoreCase = true) == true) {
                                                "This doesn't appear to be a valid backup file.\nMake sure you selected the correct .paicbackup file."
                                            } else {
                                                "Could not import this backup.\n${e.message}"
                                            }
                                        } catch (e: Exception) {
                                            isWorking = false
                                            errorMessage = "Something went wrong during import.\n${e.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = importPassword.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Restore")
                            }
                        }
                    }
                }
            }
        }
    }
}
