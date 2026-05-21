// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.privateai.camera.R
import com.privateai.camera.bridge.ScannerAi

/**
 * Bottom sheet shown after the scanner saves a PDF to the vault. Renders the
 * Smart Scanner suggestion (type / filename / destination / key fields) so
 * the user can review + accept or skip.
 *
 * Destination is a picker — defaults to the Scan album (the SCAN category)
 * so accepting doesn't silently move files out of where the user expects.
 * Picker options: Stay in Scan album / one of the existing folders / the
 * AI-suggested folder name (highlighted) / "Create new folder…".
 *
 * Title is editable; extracted fields are read-only informational for v1.
 *
 * [onAccept] receives `null` for `folder` when the user picked "Stay in
 * Scan album", otherwise the target folder name (existing or new).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartScanSheet(
    loading: Boolean,
    suggestion: ScannerAi.Suggestion?,
    existingFolders: List<String>,
    onAccept: (title: String, folder: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Editable filename, pre-filled with the AI suggestion. Re-keyed on
    // suggestion change so a second scan in the same session doesn't show
    // stale text.
    var titleText by remember(suggestion?.title) { mutableStateOf(suggestion?.title ?: "") }
    // Destination: null = stay in Scan album (default), otherwise the
    // target folder name (existing or new). DOESN'T default to the AI
    // suggestion — that's surfaced as an option in the picker dialog
    // but not auto-applied without confirmation.
    var destinationFolder by remember(suggestion) { mutableStateOf<String?>(null) }
    var showFolderPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.smart_scan_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.smart_scan_analyzing), style = MaterialTheme.typography.bodyMedium)
                }
            } else if (suggestion == null) {
                Text(
                    stringResource(R.string.smart_scan_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.smart_scan_close))
                }
            } else {
                // Detected type chip
                val typeLabel = when (suggestion.type) {
                    ScannerAi.DocType.RECEIPT -> stringResource(R.string.smart_scan_type_receipt)
                    ScannerAi.DocType.BUSINESS_CARD -> stringResource(R.string.smart_scan_type_business_card)
                    ScannerAi.DocType.ID -> stringResource(R.string.smart_scan_type_id)
                    ScannerAi.DocType.INVOICE -> stringResource(R.string.smart_scan_type_invoice)
                    ScannerAi.DocType.RECIPE -> stringResource(R.string.smart_scan_type_recipe)
                    ScannerAi.DocType.HANDWRITTEN_NOTE -> stringResource(R.string.smart_scan_type_handwritten)
                    ScannerAi.DocType.GENERIC -> stringResource(R.string.smart_scan_type_generic)
                }
                Text(
                    stringResource(R.string.smart_scan_detected, typeLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it.take(60) },
                    label = { Text(stringResource(R.string.smart_scan_filename)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Destination picker — tap the row to open a radio-list dialog.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showFolderPicker = true }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.smart_scan_save_to),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        destinationFolder ?: stringResource(R.string.smart_scan_scan_album),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.smart_scan_tap_to_change),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (suggestion.fields.isNotEmpty()) {
                    Text(
                        stringResource(R.string.smart_scan_extracted_fields),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    suggestion.fields.forEach { (key, value) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                key.replace('_', ' ').replaceFirstChar { it.uppercase() } + ":",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.smart_scan_skip))
                    }
                    Button(onClick = { onAccept(titleText, destinationFolder) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.smart_scan_apply))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    // Folder picker dialog — radio list with the Scan album, AI suggestion,
    // every existing folder, and a "Create new folder…" inline editor.
    if (showFolderPicker) {
        FolderPickerDialog(
            current = destinationFolder,
            aiSuggested = suggestion?.folder?.takeIf { it.isNotBlank() },
            existingFolders = existingFolders,
            onPick = { picked ->
                destinationFolder = picked
                showFolderPicker = false
            },
            onDismiss = { showFolderPicker = false }
        )
    }
}

/**
 * Radio-list picker for the Smart Scanner destination.
 * Selection options, in order:
 *   1. Scan album (default — represented as `null`)
 *   2. AI-suggested folder (only if non-blank AND not already in existing list)
 *   3. Every existing folder name
 *   4. "Create new folder…" → reveals a free-text field
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPickerDialog(
    current: String?,
    aiSuggested: String?,
    existingFolders: List<String>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val aiNew = aiSuggested?.takeIf { ai ->
        existingFolders.none { it.equals(ai, ignoreCase = true) }
    }
    var creatingNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.smart_scan_pick_folder)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Row 1: Scan album (default)
                FolderRadioRow(
                    label = stringResource(R.string.smart_scan_scan_album),
                    selected = current == null && !creatingNew,
                    onClick = { creatingNew = false; onPick(null) }
                )
                // Row 2: AI suggestion (if any + not already in existing folders)
                if (aiNew != null) {
                    FolderRadioRow(
                        label = stringResource(R.string.smart_scan_ai_suggested, aiNew),
                        selected = !creatingNew && current.equals(aiNew, ignoreCase = true),
                        onClick = { creatingNew = false; onPick(aiNew) }
                    )
                }
                // Existing folders
                existingFolders.forEach { name ->
                    FolderRadioRow(
                        label = name,
                        selected = !creatingNew && current.equals(name, ignoreCase = true),
                        onClick = { creatingNew = false; onPick(name) }
                    )
                }
                // Create new folder…
                FolderRadioRow(
                    label = stringResource(R.string.smart_scan_create_new_folder),
                    selected = creatingNew,
                    onClick = { creatingNew = true }
                )
                if (creatingNew) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(40) },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.smart_scan_new_folder_placeholder)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (creatingNew) {
                TextButton(
                    onClick = { onPick(newName.trim().takeIf { it.isNotBlank() }) },
                    enabled = newName.trim().isNotBlank()
                ) { Text(stringResource(R.string.smart_scan_use_folder)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.smart_scan_close)) }
            }
        },
        dismissButton = if (creatingNew) {
            { TextButton(onClick = { creatingNew = false }) { Text(stringResource(R.string.smart_scan_cancel)) } }
        } else null
    )
}

@Composable
private fun FolderRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
