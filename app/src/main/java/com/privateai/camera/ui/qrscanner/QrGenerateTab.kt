// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.qrscanner

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrGenerateTab(
    onBack: (() -> Unit)?,
    onCodeGenerated: (QrHistoryItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var selectedType by remember { mutableStateOf(QrGeneratorType.PLAIN_TEXT) }
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var generatedContent by remember { mutableStateOf("") }

    // Input fields
    var textInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var wifiSecurity by remember { mutableStateOf("WPA") }
    var phoneInput by remember { mutableStateOf("") }
    var emailAddress by remember { mutableStateOf("") }
    var emailSubject by remember { mutableStateOf("") }
    var emailBody by remember { mutableStateOf("") }
    var smsNumber by remember { mutableStateOf("") }
    var smsMessage by remember { mutableStateOf("") }
    var vcardName by remember { mutableStateOf("") }
    var vcardPhone by remember { mutableStateOf("") }
    var vcardEmail by remember { mutableStateOf("") }
    var vcardOrg by remember { mutableStateOf("") }

    fun buildContent(): String? {
        return when (selectedType) {
            QrGeneratorType.PLAIN_TEXT -> textInput.takeIf { it.isNotBlank() }
            QrGeneratorType.URL -> urlInput.takeIf { it.isNotBlank() }?.let {
                if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
            }
            QrGeneratorType.WIFI -> if (wifiSsid.isNotBlank()) QrGenerator.formatWifi(wifiSsid, wifiPassword, wifiSecurity) else null
            QrGeneratorType.PHONE -> phoneInput.takeIf { it.isNotBlank() }?.let { QrGenerator.formatPhone(it) }
            QrGeneratorType.EMAIL -> emailAddress.takeIf { it.isNotBlank() }?.let { QrGenerator.formatEmail(it, emailSubject, emailBody) }
            QrGeneratorType.SMS -> smsNumber.takeIf { it.isNotBlank() }?.let { QrGenerator.formatSms(it, smsMessage) }
            QrGeneratorType.VCARD -> vcardName.takeIf { it.isNotBlank() }?.let { QrGenerator.formatVCard(it, vcardPhone, vcardEmail, vcardOrg) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Type selector chips
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QrGeneratorType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = {
                        selectedType = type
                        generatedBitmap = null
                    },
                    label = { Text(type.label) },
                    leadingIcon = {
                        Icon(getTypeIcon(type), null, Modifier.size(16.dp))
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Dynamic input form
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(16.dp)) {
                when (selectedType) {
                    QrGeneratorType.PLAIN_TEXT -> {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Text content") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6
                        )
                    }
                    QrGeneratorType.URL -> {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("URL") },
                            placeholder = { Text("https://example.com") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    QrGeneratorType.WIFI -> {
                        OutlinedTextField(
                            value = wifiSsid,
                            onValueChange = { wifiSsid = it },
                            label = { Text("Network Name (SSID)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = wifiPassword,
                            onValueChange = { wifiPassword = it },
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        // Security dropdown
                        var securityExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = securityExpanded,
                            onExpandedChange = { securityExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = wifiSecurity,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Security") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = securityExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = securityExpanded,
                                onDismissRequest = { securityExpanded = false }
                            ) {
                                listOf("WPA", "WEP", "nopass").forEach { sec ->
                                    DropdownMenuItem(
                                        text = { Text(if (sec == "nopass") "None" else sec) },
                                        onClick = {
                                            wifiSecurity = sec
                                            securityExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    QrGeneratorType.PHONE -> {
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text("Phone Number") },
                            placeholder = { Text("+1234567890") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    QrGeneratorType.EMAIL -> {
                        OutlinedTextField(
                            value = emailAddress,
                            onValueChange = { emailAddress = it },
                            label = { Text("Email Address") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = emailSubject,
                            onValueChange = { emailSubject = it },
                            label = { Text("Subject (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = emailBody,
                            onValueChange = { emailBody = it },
                            label = { Text("Body (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                    QrGeneratorType.SMS -> {
                        OutlinedTextField(
                            value = smsNumber,
                            onValueChange = { smsNumber = it },
                            label = { Text("Phone Number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = smsMessage,
                            onValueChange = { smsMessage = it },
                            label = { Text("Message (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                    QrGeneratorType.VCARD -> {
                        OutlinedTextField(
                            value = vcardName,
                            onValueChange = { vcardName = it },
                            label = { Text("Full Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = vcardPhone,
                            onValueChange = { vcardPhone = it },
                            label = { Text("Phone (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = vcardEmail,
                            onValueChange = { vcardEmail = it },
                            label = { Text("Email (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = vcardOrg,
                            onValueChange = { vcardOrg = it },
                            label = { Text("Organization (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Generate button
        Button(
            onClick = {
                val content = buildContent()
                if (content == null) {
                    Toast.makeText(context, "Please enter content first", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val bitmap = QrGenerator.generate(content)
                if (bitmap != null) {
                    generatedBitmap = bitmap
                    generatedContent = content
                    val item = QrHistoryItem(
                        rawValue = content,
                        displayValue = getDisplayValue(selectedType, content),
                        format = BarcodeType.FORMAT_QR_CODE,
                        valueType = getValueType(selectedType),
                        typeLabel = selectedType.label,
                        source = QrSource.GENERATED
                    )
                    onCodeGenerated(item)
                } else {
                    Toast.makeText(context, "Failed to generate QR code", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate QR Code")
        }

        // Generated QR display
        if (generatedBitmap != null) {
            Spacer(Modifier.height(20.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = generatedBitmap!!.asImageBitmap(),
                    contentDescription = "Generated QR Code",
                    modifier = Modifier.size(250.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Save to gallery
                IconButton(onClick = {
                    scope.launch {
                        saveQrToGallery(context, generatedBitmap!!)
                    }
                }) {
                    Icon(Icons.Default.SaveAlt, "Save to Gallery")
                }

                Spacer(Modifier.width(16.dp))

                // Share
                IconButton(onClick = {
                    scope.launch {
                        shareQrBitmap(context, generatedBitmap!!)
                    }
                }) {
                    Icon(Icons.Default.Share, "Share")
                }

                Spacer(Modifier.width(16.dp))

                // Copy content
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(generatedContent))
                    Toast.makeText(context, "Content copied", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, "Copy Content")
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

private fun getTypeIcon(type: QrGeneratorType): ImageVector {
    return when (type) {
        QrGeneratorType.PLAIN_TEXT -> Icons.Default.TextFields
        QrGeneratorType.URL -> Icons.Default.Link
        QrGeneratorType.WIFI -> Icons.Default.Wifi
        QrGeneratorType.PHONE -> Icons.Default.Phone
        QrGeneratorType.EMAIL -> Icons.Default.Email
        QrGeneratorType.SMS -> Icons.Default.Sms
        QrGeneratorType.VCARD -> Icons.Default.ContactPhone
    }
}

private fun getValueType(type: QrGeneratorType): Int {
    return when (type) {
        QrGeneratorType.URL -> BarcodeType.URL
        QrGeneratorType.WIFI -> BarcodeType.WIFI
        QrGeneratorType.PHONE -> BarcodeType.PHONE
        QrGeneratorType.EMAIL -> BarcodeType.EMAIL
        QrGeneratorType.SMS -> BarcodeType.SMS
        QrGeneratorType.VCARD -> BarcodeType.CONTACT_INFO
        QrGeneratorType.PLAIN_TEXT -> BarcodeType.TEXT
    }
}

private fun getDisplayValue(type: QrGeneratorType, content: String): String {
    return when (type) {
        QrGeneratorType.PLAIN_TEXT -> content.take(100)
        QrGeneratorType.URL -> content
        QrGeneratorType.WIFI -> content.substringAfter("S:").substringBefore(";")
        QrGeneratorType.PHONE -> content.removePrefix("tel:")
        QrGeneratorType.EMAIL -> content.substringAfter("TO:").substringBefore(";")
        QrGeneratorType.SMS -> content.removePrefix("smsto:").substringBefore(":")
        QrGeneratorType.VCARD -> content.lineSequence().find { it.startsWith("FN:") }?.removePrefix("FN:") ?: content.take(50)
    }
}

private suspend fun saveQrToGallery(context: Context, bitmap: Bitmap) {
    withContext(Dispatchers.IO) {
        try {
            val filename = "qr_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Privora")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val privDir = File(dir, "Privora")
                privDir.mkdirs()
                FileOutputStream(File(privDir, filename)).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "QR code saved to gallery", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private suspend fun shareQrBitmap(context: Context, bitmap: Bitmap) {
    withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "qr_share_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share QR Code"))
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
