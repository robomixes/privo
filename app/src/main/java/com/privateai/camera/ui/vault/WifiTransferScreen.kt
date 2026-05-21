// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.vault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.privateai.camera.R
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.VaultCategory
import com.privateai.camera.security.VaultRepository
import com.privateai.camera.service.WifiTransferServer
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

private const val TAG = "WifiTransfer"

private val ALLOWED_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "webp", "heic", "gif",
    "mp4", "mov", "mkv", "webm",
    "pdf",
    "docx", "xlsx", "pptx", "txt", "csv"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiTransferScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // Config
    val maxFileMB = remember {
        context.getSharedPreferences("wifi_transfer", android.content.Context.MODE_PRIVATE)
            .getInt("max_file_mb", 100)
    }
    val maxFileBytes = maxFileMB.toLong() * 1024 * 1024

    // Generate PIN + port. Force Locale.ROOT (Western digits 0-9) instead of
    // %04d's default-locale formatting — otherwise on phones whose locale uses
    // Arabic-Indic digits (Arabic/Persian/Bengali/etc.) the displayed PIN
    // becomes "٠٤٢١" which the receiving phone (likely on a different locale)
    // can't enter into the upload form. Same applies to the IP-address render
    // below.
    val pin = remember { String.format(java.util.Locale.ROOT, "%04d", (1000..9999).random()) }
    val port = remember { (8000..9999).random() }
    val localIp = remember { getLocalIpAddress(context) }
    val url = "http://$localIp:$port"

    // QR code bitmap
    val qrBitmap = remember(url) { generateQrCode(url, 400) }

    // Transfer stats
    var filesReceived by remember { mutableIntStateOf(0) }
    var totalBytes by remember { mutableLongStateOf(0L) }

    // Vault for saving — all received files go to a "Received" folder
    val crypto = remember { CryptoManager(context).also { it.initialize() } }
    val vault = remember { VaultRepository(context, crypto) }
    val folderManager = remember { com.privateai.camera.security.FolderManager(context, crypto) }
    val receivedFolder = remember {
        // Get or create "Received" folder
        val existing = folderManager.listRootFolders().find { it.name == "Received" }
        existing ?: folderManager.createFolder("Received", null)
    }
    val receivedDir = remember { folderManager.getFolderDir(receivedFolder.id) }

    // Start server + auto-stop on exit
    val server = remember {
        WifiTransferServer(
            port = port,
            pin = pin,
            maxFileSizeBytes = maxFileBytes,
            allowedExtensions = ALLOWED_EXTENSIONS,
            onFileReceived = { fileName, bytes, mimeType ->
                try {
                    val ext = fileName.substringAfterLast('.', "").lowercase()
                    // ALL files go to the Received folder regardless of type
                    when {
                        mimeType.startsWith("image/") -> {
                            // Pull full EXIF metadata BEFORE BitmapFactory
                            // strips it (date, dimensions, orientation, GPS).
                            // The vault stores it as an encrypted sidecar so
                            // the saved JPEG itself stays metadata-free.
                            val meta = com.privateai.camera.util.ExifUtils.readMetadata(bytes)
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) {
                                vault.savePhotoToFolder(bitmap, receivedDir, metadata = meta)
                                bitmap.recycle()
                                null
                            } else "Failed to decode image"
                        }
                        mimeType.startsWith("video/") -> {
                            val tempFile = File(context.cacheDir, "transfer_${System.currentTimeMillis()}.$ext")
                            tempFile.writeBytes(bytes)
                            vault.saveVideo(tempFile, VaultCategory.VIDEO)
                            vault.listPhotos(VaultCategory.VIDEO).firstOrNull()?.let {
                                vault.moveToFolder(it, receivedDir)
                            }
                            null
                        }
                        else -> {
                            // PDFs, docs, spreadsheets, etc. → encrypt directly to Received folder
                            val encFile = File(receivedDir, "${System.currentTimeMillis()}_$fileName.file.enc")
                            crypto.encryptToFile(bytes, encFile)
                            null
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Save failed: ${e.message}")
                    e.message
                }
            },
            onStatsUpdate = { files, total ->
                filesReceived = files
                totalBytes = total
            }
        )
    }

    DisposableEffect(Unit) {
        try {
            server.start()
            Log.i(TAG, "Server started on $url (PIN: $pin)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server: ${e.message}")
        }
        onDispose {
            server.stop()
            Log.i(TAG, "Server stopped")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wifi_transfer_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status icon
            Icon(
                Icons.Default.Wifi,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            if (localIp == "0.0.0.0") {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        "Could not detect Wi-Fi IP address. Make sure you're connected to a Wi-Fi network.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Text(
                stringResource(R.string.wifi_transfer_instructions),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // QR Code
            if (qrBitmap != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR code for $url",
                        modifier = Modifier.size(200.dp).padding(12.dp)
                    )
                }
            }

            // URL (tappable to copy)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                onClick = {
                    clipboard.setText(AnnotatedString(url))
                    Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text(
                    url,
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // PIN
            Text(
                stringResource(R.string.wifi_transfer_pin_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                pin,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(8.dp))

            // Transfer stats
            if (filesReceived > 0) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$filesReceived", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.wifi_transfer_files), style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${"%.1f".format(totalBytes / (1024.0 * 1024.0))} MB", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.wifi_transfer_total), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Size limit info
            Text(
                stringResource(R.string.wifi_transfer_limit, maxFileMB),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Stop button
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                Text("  " + stringResource(R.string.wifi_transfer_stop))
            }
        }
    }
}

/** Get the device's local Wi-Fi IP address. Tries multiple methods for reliability. */
@Suppress("DEPRECATION")
private fun getLocalIpAddress(context: android.content.Context): String {
    // Method 1: enumerate network interfaces (most reliable, no permissions needed)
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
        // Prefer wlan0 (Wi-Fi) over other interfaces
        val wlanIface = interfaces.firstOrNull { it.name.startsWith("wlan") }
        if (wlanIface != null) {
            wlanIface.inetAddresses?.toList()?.forEach { addr ->
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    val ip = addr.hostAddress
                    if (ip != null && ip != "0.0.0.0") {
                        Log.i(TAG, "IP from wlan interface: $ip")
                        return ip
                    }
                }
            }
        }
        // Fallback: any non-loopback IPv4
        interfaces.forEach { iface ->
            if (iface.isLoopback || !iface.isUp) return@forEach
            iface.inetAddresses?.toList()?.forEach { addr ->
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    val ip = addr.hostAddress
                    if (ip != null && ip != "0.0.0.0") {
                        Log.i(TAG, "IP from ${iface.name}: $ip")
                        return ip
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "NetworkInterface enumeration failed: ${e.message}")
    }

    // Method 2: WifiManager (deprecated on Android 12+ but still works on most devices)
    try {
        val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? WifiManager
        val ip = wifiManager?.connectionInfo?.ipAddress ?: 0
        if (ip != 0) {
            // Force Locale.ROOT so the IP shows Western digits regardless of
            // the device locale (Arabic-Indic digits would break the URL).
            val ipStr = String.format(java.util.Locale.ROOT, "%d.%d.%d.%d",
                ip and 0xff, (ip shr 8) and 0xff, (ip shr 16) and 0xff, (ip shr 24) and 0xff)
            Log.i(TAG, "IP from WifiManager: $ipStr")
            return ipStr
        }
    } catch (e: Exception) {
        Log.e(TAG, "WifiManager failed: ${e.message}")
    }

    Log.e(TAG, "Could not determine local IP address")
    return "0.0.0.0"
}

/** Generate a QR code bitmap from text. */
private fun generateQrCode(text: String, size: Int): Bitmap? {
    return try {
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        bitmap
    } catch (e: Exception) {
        Log.e(TAG, "QR generation failed: ${e.message}")
        null
    }
}
