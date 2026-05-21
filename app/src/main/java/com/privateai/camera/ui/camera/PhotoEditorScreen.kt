// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privateai.camera.security.VaultPhoto
import com.privateai.camera.security.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class EditorTab { CROP, ROTATE, TEXT, STICKER, FILTER }

private data class TextItem(
    val text: String,
    var xFrac: Float, // 0-1
    var yFrac: Float, // 0-1
    val color: Int,
    val sizePercent: Float
)

private data class StickerItem(
    val emoji: String,
    var xFrac: Float,
    var yFrac: Float,
    val sizePercent: Float
)

private val EMOJI_GROUPS = listOf(
    "Faces" to listOf("😀", "😂", "🥰", "😎", "🤩", "😱", "🤔", "😴", "🥳", "😈", "👻", "💀"),
    "Hearts" to listOf("❤️", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "💕", "💖", "💗", "💘"),
    "Hands" to listOf("👍", "👎", "👋", "🤝", "👏", "🙌", "✌️", "🤞", "🫶", "💪", "🙏", "☝️"),
    "Animals" to listOf("🐶", "🐱", "🐻", "🦁", "🐸", "🦋", "🐝", "🦄", "🐼", "🐨", "🦊", "🐯"),
    "Objects" to listOf("⭐", "🔥", "💎", "🎈", "🎉", "🎁", "📷", "🔒", "🛡️", "👑", "🏆", "💡"),
    "Nature" to listOf("🌸", "🌻", "🌈", "☀️", "🌙", "⚡", "❄️", "🍀", "🌊", "🌺", "🍄", "🌵"),
    "Food" to listOf("🍕", "🍔", "🍦", "🎂", "🍩", "🍿", "☕", "🍷", "🍑", "🍓", "🥑", "🌶️"),
    "Fun" to listOf("🎭", "🎪", "🎨", "🎬", "🎵", "🎸", "🪄", "🫧", "💫", "✨", "🌀", "🎯")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    photo: VaultPhoto,
    initialBitmap: Bitmap,
    vault: VaultRepository,
    /** Called after a successful save (NOT on back-without-save). The caller
     *  uses this to stamp `PhotoIndex.markUpdated` so the Vault's
     *  "Sort by updated date" mode sees the edit time. */
    onSaved: ((String) -> Unit)? = null,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var workingBitmap by remember { mutableStateOf(initialBitmap.copy(Bitmap.Config.ARGB_8888, true)) }
    var bitmapVersion by remember { mutableIntStateOf(0) }
    var currentTab by remember { mutableStateOf(EditorTab.ROTATE) }
    var hasChanges by remember { mutableStateOf(false) }

    // Text
    var texts by remember { mutableStateOf<List<TextItem>>(emptyList()) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var textColor by remember { mutableIntStateOf(android.graphics.Color.WHITE) }
    var textSizePct by remember { mutableFloatStateOf(5f) }

    // Stickers
    var stickers by remember { mutableStateOf<List<StickerItem>>(emptyList()) }
    var stickerSizePct by remember { mutableFloatStateOf(8f) }

    // Crop (fractions 0-1 of image)
    var cL by remember { mutableFloatStateOf(0.1f) }
    var cT by remember { mutableFloatStateOf(0.1f) }
    var cR by remember { mutableFloatStateOf(0.9f) }
    var cB by remember { mutableFloatStateOf(0.9f) }

    // Filter
    var activeFilter by remember { mutableStateOf("Original") }

    fun applyFilter(bmp: Bitmap, f: String): Bitmap {
        if (f == "Original") return bmp.copy(Bitmap.Config.ARGB_8888, true)
        val r = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val cm = when (f) {
            "B&W" -> ColorMatrix().apply { setSaturation(0f) }
            "Sepia" -> ColorMatrix().apply { setSaturation(0f); postConcat(ColorMatrix(floatArrayOf(1f,0f,0f,0f,40f, 0f,1f,0f,0f,20f, 0f,0f,1f,0f,-10f, 0f,0f,0f,1f,0f))) }
            "Contrast" -> ColorMatrix(floatArrayOf(1.5f,0f,0f,0f,-60f, 0f,1.5f,0f,0f,-60f, 0f,0f,1.5f,0f,-60f, 0f,0f,0f,1f,0f))
            "Bright" -> ColorMatrix(floatArrayOf(1.2f,0f,0f,0f,30f, 0f,1.2f,0f,0f,30f, 0f,0f,1.2f,0f,30f, 0f,0f,0f,1f,0f))
            "Dark" -> ColorMatrix(floatArrayOf(0.8f,0f,0f,0f,-20f, 0f,0.8f,0f,0f,-20f, 0f,0f,0.8f,0f,-20f, 0f,0f,0f,1f,0f))
            else -> null
        }
        if (cm != null) Canvas(r).drawBitmap(bmp, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return r
    }

    fun buildFinal(): Bitmap {
        var r = applyFilter(workingBitmap, activeFilter)
        val c = Canvas(r)
        for (t in texts) {
            c.drawText(t.text, t.xFrac * r.width, t.yFrac * r.height, Paint().apply {
                color = t.color; textSize = r.height * t.sizePercent / 100f
                isAntiAlias = true; isFakeBoldText = true
                setShadowLayer(6f, 3f, 3f, android.graphics.Color.BLACK)
            })
        }
        for (s in stickers) {
            c.drawText(s.emoji, s.xFrac * r.width, s.yFrac * r.height, Paint().apply {
                textSize = r.height * s.sizePercent / 100f; isAntiAlias = true
            })
        }
        return r
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add Text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = textInput, onValueChange = { textInput = it }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("Size: ${textSizePct.toInt()}%")
                    Slider(value = textSizePct, onValueChange = { textSizePct = it }, valueRange = 3f..15f)
                    Text("Color")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(android.graphics.Color.WHITE to Color.White, android.graphics.Color.BLACK to Color.Black,
                            android.graphics.Color.RED to Color.Red, android.graphics.Color.BLUE to Color.Blue,
                            android.graphics.Color.YELLOW to Color.Yellow, android.graphics.Color.GREEN to Color.Green
                        ).forEach { (ac, cc) ->
                            Box(Modifier.size(32.dp).background(cc, CircleShape)
                                .then(if (textColor == ac) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier.border(1.dp, Color.Gray, CircleShape))
                                .clickable { textColor = ac })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                if (textInput.isNotBlank()) { texts = texts + TextItem(textInput, 0.1f, 0.5f, textColor, textSizePct); hasChanges = true }
                showTextDialog = false; textInput = ""
            }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showTextDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Edit Photo") },
            navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { scope.launch { withContext(Dispatchers.IO) { vault.savePhoto(buildFinal(), photo.category) }; Toast.makeText(context, "Saved as copy", Toast.LENGTH_SHORT).show() } }) {
                    Icon(Icons.Default.ContentCopy, "Copy")
                }
                if (hasChanges) IconButton(onClick = { scope.launch { withContext(Dispatchers.IO) { vault.replacePhoto(photo, buildFinal()) }; onSaved?.invoke(photo.id); Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show(); onDone() } }) {
                    Icon(Icons.Default.Check, "Save", tint = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {

            // Single Canvas for image + all overlays + touch handling
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .pointerInput(currentTab, bitmapVersion) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()

                            // Calculate image rect within canvas (ContentScale.Fit)
                            val bmpW = workingBitmap.width.toFloat()
                            val bmpH = workingBitmap.height.toFloat()
                            val scale = minOf(w / bmpW, h / bmpH)
                            val imgW = bmpW * scale
                            val imgH = bmpH * scale
                            val imgLeft = (w - imgW) / 2
                            val imgTop = (h - imgH) / 2

                            // Convert touch to image-relative fraction (0-1)
                            val fx = ((change.position.x - imgLeft) / imgW).coerceIn(0f, 1f)
                            val fy = ((change.position.y - imgTop) / imgH).coerceIn(0f, 1f)
                            val dx = drag.x / imgW
                            val dy = drag.y / imgH

                            when (currentTab) {
                                EditorTab.CROP -> {
                                    val min = 0.15f
                                    val t = 0.07f
                                    val nL = kotlin.math.abs(fx - cL) < t
                                    val nR = kotlin.math.abs(fx - cR) < t
                                    val nT = kotlin.math.abs(fy - cT) < t
                                    val nB = kotlin.math.abs(fy - cB) < t

                                    when {
                                        nL && nT -> { cL = (cL + dx).coerceIn(0f, cR - min); cT = (cT + dy).coerceIn(0f, cB - min) }
                                        nR && nT -> { cR = (cR + dx).coerceIn(cL + min, 1f); cT = (cT + dy).coerceIn(0f, cB - min) }
                                        nL && nB -> { cL = (cL + dx).coerceIn(0f, cR - min); cB = (cB + dy).coerceIn(cT + min, 1f) }
                                        nR && nB -> { cR = (cR + dx).coerceIn(cL + min, 1f); cB = (cB + dy).coerceIn(cT + min, 1f) }
                                        nL -> cL = (cL + dx).coerceIn(0f, cR - min)
                                        nR -> cR = (cR + dx).coerceIn(cL + min, 1f)
                                        nT -> cT = (cT + dy).coerceIn(0f, cB - min)
                                        nB -> cB = (cB + dy).coerceIn(cT + min, 1f)
                                        else -> {
                                            val cw = cR - cL; val ch = cB - cT
                                            val nl = (cL + dx).coerceIn(0f, 1f - cw)
                                            val nt = (cT + dy).coerceIn(0f, 1f - ch)
                                            cL = nl; cR = nl + cw; cT = nt; cB = nt + ch
                                        }
                                    }
                                }
                                EditorTab.TEXT -> {
                                    if (texts.isNotEmpty()) {
                                        val closest = texts.minByOrNull { kotlin.math.sqrt((fx - it.xFrac).let { x -> x * x } + (fy - it.yFrac).let { y -> y * y }.toDouble()) }
                                        if (closest != null) {
                                            val idx = texts.indexOf(closest)
                                            texts = texts.toMutableList().also { it[idx] = closest.copy(xFrac = (closest.xFrac + dx).coerceIn(0f, 1f), yFrac = (closest.yFrac + dy).coerceIn(0.05f, 1f)) }
                                        }
                                    }
                                }
                                EditorTab.STICKER -> {
                                    if (stickers.isNotEmpty()) {
                                        val closest = stickers.minByOrNull { kotlin.math.sqrt((fx - it.xFrac).let { x -> x * x } + (fy - it.yFrac).let { y -> y * y }.toDouble()) }
                                        if (closest != null) {
                                            val idx = stickers.indexOf(closest)
                                            stickers = stickers.toMutableList().also { it[idx] = closest.copy(xFrac = (closest.xFrac + dx).coerceIn(0f, 1f), yFrac = (closest.yFrac + dy).coerceIn(0.05f, 1f)) }
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val bmpW = workingBitmap.width.toFloat()
                val bmpH = workingBitmap.height.toFloat()
                val scale = minOf(w / bmpW, h / bmpH)
                val imgW = bmpW * scale
                val imgH = bmpH * scale
                val imgLeft = (w - imgW) / 2
                val imgTop = (h - imgH) / 2

                // Draw image
                drawIntoCanvas { canvas ->
                    val displayBmp = if (activeFilter != "Original") applyFilter(workingBitmap, activeFilter) else workingBitmap
                    val destRect = android.graphics.RectF(imgLeft, imgTop, imgLeft + imgW, imgTop + imgH)
                    canvas.nativeCanvas.drawBitmap(displayBmp, null, destRect, null)
                    if (displayBmp !== workingBitmap) displayBmp.recycle()
                }

                // Draw text overlays
                for (t in texts) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            t.text, imgLeft + t.xFrac * imgW, imgTop + t.yFrac * imgH,
                            Paint().apply {
                                color = t.color; textSize = imgH * t.sizePercent / 100f
                                isAntiAlias = true; isFakeBoldText = true
                                setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
                            }
                        )
                    }
                }

                // Draw stickers
                for (s in stickers) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            s.emoji, imgLeft + s.xFrac * imgW, imgTop + s.yFrac * imgH,
                            Paint().apply { textSize = imgH * s.sizePercent / 100f; isAntiAlias = true }
                        )
                    }
                }

                // Draw crop overlay
                if (currentTab == EditorTab.CROP) {
                    val cl = imgLeft + cL * imgW
                    val ct = imgTop + cT * imgH
                    val cr = imgLeft + cR * imgW
                    val cb = imgTop + cB * imgH

                    // Dim outside
                    drawRect(Color.Black.copy(0.5f), Offset(imgLeft, imgTop), Size(imgW, (cT) * imgH))
                    drawRect(Color.Black.copy(0.5f), Offset(imgLeft, cb), Size(imgW, (1 - cB) * imgH))
                    drawRect(Color.Black.copy(0.5f), Offset(imgLeft, ct), Size(cL * imgW, cb - ct))
                    drawRect(Color.Black.copy(0.5f), Offset(cr, ct), Size((1 - cR) * imgW, cb - ct))

                    // Border
                    drawRect(Color.White, Offset(cl, ct), Size(cr - cl, cb - ct), style = Stroke(3f))

                    // Corner handles
                    val hs = 24f
                    listOf(Offset(cl, ct), Offset(cr - hs, ct), Offset(cl, cb - hs), Offset(cr - hs, cb - hs)).forEach {
                        drawRect(Color.White, it, Size(hs, hs))
                    }

                    // Grid lines (rule of thirds)
                    val thirdW = (cr - cl) / 3f
                    val thirdH = (cb - ct) / 3f
                    for (i in 1..2) {
                        drawLine(Color.White.copy(0.3f), Offset(cl + thirdW * i, ct), Offset(cl + thirdW * i, cb))
                        drawLine(Color.White.copy(0.3f), Offset(cl, ct + thirdH * i), Offset(cr, ct + thirdH * i))
                    }
                }
            }

            // Tab bar
            Row(Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                EditorTab.entries.forEach { tab ->
                    val icon = when (tab) { EditorTab.CROP -> Icons.Default.Crop; EditorTab.ROTATE -> Icons.Default.RotateRight; EditorTab.TEXT -> Icons.Default.TextFields; EditorTab.STICKER -> Icons.Default.EmojiEmotions; EditorTab.FILTER -> Icons.Default.Palette }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { currentTab = tab }.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Icon(icon, tab.name, tint = if (currentTab == tab) Color.White else Color.Gray, modifier = Modifier.size(24.dp))
                        Text(tab.name, color = if (currentTab == tab) Color.White else Color.Gray, fontSize = 11.sp)
                    }
                }
            }

            // Tools
            when (currentTab) {
                EditorTab.STICKER -> {
                    // Sticker picker — scrollable grid
                    Column(
                        Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(8.dp).height(150.dp).verticalScroll(rememberScrollState())
                    ) {
                        // Size slider
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Size:", color = Color.Gray, fontSize = 12.sp)
                            Slider(value = stickerSizePct, onValueChange = { stickerSizePct = it }, valueRange = 4f..20f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                            if (stickers.isNotEmpty()) {
                                TextButton(onClick = { stickers = stickers.dropLast(1); hasChanges = stickers.isNotEmpty() }) { Text("Undo", color = Color.White, fontSize = 12.sp) }
                            }
                        }
                        EMOJI_GROUPS.forEach { (group, emojis) ->
                            Text(group, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                emojis.forEach { emoji ->
                                    Box(
                                        Modifier.size(44.dp).clickable {
                                            stickers = stickers + StickerItem(emoji, 0.5f, 0.5f, stickerSizePct)
                                            hasChanges = true
                                        }.padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(emoji, fontSize = 28.sp)
                                    }
                                }
                            }
                        }
                        if (stickers.isNotEmpty()) {
                            Text("Drag stickers on image to move", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, start = 8.dp))
                        }
                    }
                }
                else -> {
                    Row(Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        when (currentTab) {
                            EditorTab.CROP -> {
                                Button(onClick = {
                                    try {
                                        val old = workingBitmap
                                        val x = (cL * old.width).toInt().coerceIn(0, old.width - 1)
                                        val y = (cT * old.height).toInt().coerceIn(0, old.height - 1)
                                        val cw = ((cR - cL) * old.width).toInt().coerceAtLeast(10).coerceAtMost(old.width - x)
                                        val ch = ((cB - cT) * old.height).toInt().coerceAtLeast(10).coerceAtMost(old.height - y)
                                        workingBitmap = Bitmap.createBitmap(old, x, y, cw, ch)
                                        hasChanges = true; bitmapVersion++
                                        cL = 0.1f; cT = 0.1f; cR = 0.9f; cB = 0.9f
                                    } catch (_: Exception) {}
                                }) { Text("Apply Crop") }
                                TextButton(onClick = { cL = 0.1f; cT = 0.1f; cR = 0.9f; cB = 0.9f }) { Text("Reset", color = Color.Gray) }
                            }
                            EditorTab.ROTATE -> {
                                IconButton(onClick = { val o = workingBitmap; workingBitmap = Bitmap.createBitmap(o, 0, 0, o.width, o.height, Matrix().apply { postRotate(-90f) }, true); hasChanges = true; bitmapVersion++ }) { Icon(Icons.Default.RotateLeft, "L", tint = Color.White) }
                                IconButton(onClick = { val o = workingBitmap; workingBitmap = Bitmap.createBitmap(o, 0, 0, o.width, o.height, Matrix().apply { postRotate(90f) }, true); hasChanges = true; bitmapVersion++ }) { Icon(Icons.Default.RotateRight, "R", tint = Color.White) }
                                IconButton(onClick = { val o = workingBitmap; workingBitmap = Bitmap.createBitmap(o, 0, 0, o.width, o.height, Matrix().apply { preScale(-1f, 1f) }, true); hasChanges = true; bitmapVersion++ }) { Icon(Icons.Default.FlipCameraAndroid, "FH", tint = Color.White) }
                                IconButton(onClick = { val o = workingBitmap; workingBitmap = Bitmap.createBitmap(o, 0, 0, o.width, o.height, Matrix().apply { preScale(1f, -1f) }, true); hasChanges = true; bitmapVersion++ }) { Icon(Icons.Default.FlipCameraAndroid, "FV", tint = Color.White, modifier = Modifier.graphicsLayer(rotationZ = 90f)) }
                            }
                            EditorTab.TEXT -> {
                                Button(onClick = { showTextDialog = true }) { Text("Add Text") }
                                if (texts.isNotEmpty()) {
                                    TextButton(onClick = { texts = texts.dropLast(1); hasChanges = texts.isNotEmpty() }) { Text("Undo", color = Color.White) }
                                    Text("Drag to move", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                            EditorTab.FILTER -> {
                                listOf("Original", "B&W", "Sepia", "Contrast", "Bright", "Dark").forEach { f ->
                                    Box(Modifier.background(if (activeFilter == f) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp)).clickable { activeFilter = f; hasChanges = true }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text(f, color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
