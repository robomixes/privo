// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.privateai.camera.R
import com.privateai.camera.service.TesseractDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → OCR languages. Shows every supported Tesseract language
 * with a download / delete control. Each .traineddata file is fetched on
 * demand from the tesseract-ocr/tessdata_fast GitHub releases — see
 * [TesseractDataManager] for the storage details.
 *
 * Why a dedicated screen: ML Kit text recognition (which Tesseract
 * replaces — Track A1.3) was Latin-only. Tesseract supports Arabic, CJK,
 * Hebrew, Cyrillic, Devanagari, Thai etc., but each language is a 1-7 MB
 * file we don't want to bundle in the APK. The user chooses what they
 * need.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrLanguagesScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Refresh trigger — bump when we install / delete so the row state
    // re-reads from disk. Cheap (a few stat calls per row).
    var refreshKey by remember { mutableStateOf(0) }
    val installed = remember(refreshKey) { TesseractDataManager.installedLanguages(context).toSet() }

    // Download progress per language code: pair of (bytesDone, bytesTotal).
    // totalBytes = -1 means "indeterminate" (server didn't send Content-Length).
    // Null entry = not currently downloading.
    val progress = remember { mutableStateMapOf<String, Pair<Long, Long>>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_ocr_languages)) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
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
        ) {
            // Short header explaining what's going on. Useful because a
            // user who's never run an OCR feature won't know why this
            // screen exists at all.
            Text(
                stringResource(R.string.settings_ocr_languages_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            HorizontalDivider()

            LazyColumn(Modifier.fillMaxSize()) {
                items(TesseractDataManager.SUPPORTED_LANGUAGES) { lang ->
                    val isInstalled = lang.code in installed
                    val prog = progress[lang.code]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                lang.displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                if (isInstalled) {
                                    val bytes = TesseractDataManager.sizeBytes(context, lang.code)
                                    stringResource(R.string.settings_ocr_lang_installed, formatBytes(bytes))
                                } else if (prog != null) {
                                    val (done, total) = prog
                                    if (total > 0) "${formatBytes(done)} / ${formatBytes(total)}"
                                    else formatBytes(done)
                                } else {
                                    stringResource(R.string.settings_ocr_lang_not_installed, lang.sizeApprox)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (prog != null) {
                                Spacer(Modifier.height(6.dp))
                                val (done, total) = prog
                                if (total > 0) {
                                    LinearProgressIndicator(
                                        progress = { done.toFloat() / total.toFloat() },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        when {
                            prog != null -> {
                                // Spinner while in-flight; row is otherwise
                                // inert (cancellation requires more state
                                // tracking than v1 of this screen needs).
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                            isInstalled -> {
                                Icon(
                                    Icons.Default.CheckCircle, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                IconButton(onClick = {
                                    TesseractDataManager.delete(context, lang.code)
                                    refreshKey++
                                }) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            else -> {
                                IconButton(onClick = {
                                    progress[lang.code] = 0L to -1L
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            TesseractDataManager.download(context, lang.code) { done, total ->
                                                progress[lang.code] = done to total
                                            }
                                        }
                                        progress.remove(lang.code)
                                        if (ok) refreshKey++
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        stringResource(R.string.settings_ocr_lang_download),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
