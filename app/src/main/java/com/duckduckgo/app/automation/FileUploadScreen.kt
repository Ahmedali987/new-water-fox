/*
 * Copyright (c) 2025 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.automation

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R.drawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_SLOTS = 3
private const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024L // 5MB
private const val PREVIEW_CHAR_LIMIT = 4000

data class FileSlot(
    val index: Int,
    val state: SlotState,
)

sealed interface SlotState {
    data object Empty : SlotState
    data class Uploading(val fileName: String, val progress: Float) : SlotState
    data class Loaded(
        val fileName: String,
        val lineCount: Int,
        val contentPreview: String,
        val analysis: SmartParser.ParsedData?,
    ) : SlotState
    data class Error(val message: String) : SlotState
}

@Composable
fun FileUploadScreen(
    onBack: () -> Unit,
    onGroupCreated: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeSlot by remember { mutableIntStateOf(0) }
    var slots by remember {
        mutableStateOf<List<SlotState>>(List(MAX_SLOTS) { SlotState.Empty })
    }

    // --- picker per slot is rebuilt each recomposition via remember ---
    var pickerSlotIndex by remember { mutableIntStateOf(-1) }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        val slot = pickerSlotIndex
        if (uri == null || slot < 0) {
            pickerSlotIndex = -1
            return@rememberLauncherForActivityResult
        }
        pickerSlotIndex = -1

        val name = queryDisplayName(context, uri) ?: "file_${slot + 1}.txt"

        // Move to Uploading state
        slots = slots.toMutableList().also {
            it[slot] = SlotState.Uploading(name, 0f)
        }
        activeSlot = slot

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val size = queryFileSize(context, uri)
                    if (size > MAX_FILE_SIZE_BYTES) {
                        return@withContext SlotState.Error("File too large (max 5 MB)")
                    }
                    // Simulate incremental progress while reading
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: return@withContext SlotState.Error("Could not open file")
                    val sb = StringBuilder()
                    val buf = CharArray(8192)
                    val reader = stream.bufferedReader()
                    var read: Int
                    var totalRead = 0
                    while (reader.read(buf).also { read = it } > 0) {
                        sb.append(buf, 0, read)
                        totalRead += read
                        if (size > 0) {
                            val p = (totalRead.toFloat() / size.toFloat()).coerceIn(0f, 0.95f)
                            withContext(Dispatchers.Main) {
                                slots = slots.toMutableList().also {
                                    it[slot] = SlotState.Uploading(name, p)
                                }
                            }
                        }
                    }
                    reader.close()

                    val text = sb.toString()
                    if (text.isBlank()) {
                        return@withContext SlotState.Error("File is empty")
                    }
                    val lines = text.lines().count { it.trim().isNotEmpty() }
                    val preview = text.take(PREVIEW_CHAR_LIMIT)
                    SlotState.Loaded(
                        fileName = name.substringBeforeLast('.'),
                        lineCount = lines,
                        contentPreview = preview,
                        analysis = null,
                    )
                } catch (e: Exception) {
                    SlotState.Error("Could not read file: ${e.message}")
                }
            }
            slots = slots.toMutableList().also { it[slot] = result }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuckDuckGoTheme.colors.backgrounds.background),
    ) {
        // ---- Top bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DuckDuckGoTheme.colors.backgrounds.surface)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(drawable.ic_arrow_left_24),
                    contentDescription = stringResource(R.string.automation_back),
                    tint = DuckDuckGoTheme.colors.icons.primary,
                )
            }
            Text(
                text = "Upload TXT File",
                color = DuckDuckGoTheme.colors.text.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        // ---- Tab bar (3 slots) ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DuckDuckGoTheme.colors.backgrounds.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            slots.forEachIndexed { index, state ->
                SlotTab(
                    modifier = Modifier.weight(1f),
                    index = index,
                    state = state,
                    isActive = activeSlot == index,
                    onClick = { activeSlot = index },
                )
            }
        }

        // ---- Active slot content ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            when (val state = slots[activeSlot]) {
                SlotState.Empty -> {
                    EmptySlotContent(
                        onPick = {
                            pickerSlotIndex = activeSlot
                            picker.launch("text/*")
                        },
                    )
                }

                is SlotState.Uploading -> {
                    UploadingContent(state)
                }

                is SlotState.Error -> {
                    ErrorContent(state, onRetry = {
                        slots = slots.toMutableList().also {
                            it[activeSlot] = SlotState.Empty
                        }
                    })
                }

                is SlotState.Loaded -> {
                    LoadedContent(
                        slot = state,
                        onRemove = {
                            slots = slots.toMutableList().also {
                                it[activeSlot] = SlotState.Empty
                            }
                        },
                        onAnalyzed = { parsed ->
                            slots = slots.toMutableList().also {
                                it[activeSlot] = state.copy(analysis = parsed)
                            }
                        },
                        onSaveAsGroup = { groupName ->
                            val text = state.analysis?.all?.joinToString("\n") { (k, v) -> "$k: $v" }
                                ?: state.contentPreview
                            onGroupCreated(groupName, text)
                        },
                    )
                }
            }
        }
    }
}

// =============================================================================
// Slot tab
// =============================================================================

@Composable
private fun SlotTab(
    modifier: Modifier = Modifier,
    index: Int,
    state: SlotState,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isActive) DuckDuckGoTheme.colors.brand.accentBlue
    else DuckDuckGoTheme.colors.backgrounds.background
    val fg = if (isActive) Color.White else DuckDuckGoTheme.colors.text.primary

    val title = when (state) {
        SlotState.Empty -> "File ${index + 1}"
        is SlotState.Uploading -> state.fileName
        is SlotState.Loaded -> state.fileName
        is SlotState.Error -> "Error"
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .background(bg, RoundedCornerShape(10.dp))
            .border(
                1.dp,
                if (isActive) DuckDuckGoTheme.colors.brand.accentBlue
                else DuckDuckGoTheme.colors.backgrounds.surface,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state is SlotState.Loaded) {
                Icon(
                    painter = painterResource(drawable.ic_document_download_24),
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = title,
                color = fg,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// =============================================================================
// Empty slot — dotted drop zone + button
// =============================================================================

@Composable
private fun EmptySlotContent(onPick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))

        DottedDropZone(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            onClick = onPick,
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    DuckDuckGoTheme.colors.brand.accentBlue,
                    RoundedCornerShape(10.dp),
                )
                .clickable(onClick = onPick)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(drawable.ic_downloads_24),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Choose file",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Supports .txt files · UTF-8 · max 5 MB",
            color = DuckDuckGoTheme.colors.text.secondary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun DottedDropZone(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val lineColor = DuckDuckGoTheme.colors.text.secondary
    val bgColor = DuckDuckGoTheme.colors.backgrounds.surface

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .drawBehind {
                val stroke = Stroke(
                    width = 2f * density,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(12f * density, 8f * density),
                        phase = 0f,
                    ),
                )
                val inset = 6f * density
                drawRoundRect(
                    color = lineColor,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(14f * density, 14f * density),
                    style = stroke,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(drawable.ic_document_download_24),
                contentDescription = null,
                tint = DuckDuckGoTheme.colors.brand.accentBlue,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Tap to select a file",
                color = DuckDuckGoTheme.colors.text.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "or choose from your device",
                color = DuckDuckGoTheme.colors.text.secondary,
                fontSize = 12.sp,
            )
        }
    }
}

// =============================================================================
// Uploading slot
// =============================================================================

@Composable
private fun UploadingContent(state: SlotState.Uploading) {
    val infiniteTransition = rememberInfiniteTransition(label = "upload")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                DuckDuckGoTheme.colors.backgrounds.surface,
                RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(drawable.ic_document_download_24),
                contentDescription = null,
                tint = DuckDuckGoTheme.colors.brand.accentBlue,
                modifier = Modifier
                    .size(24.dp)
                    .scale(pulse),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.fileName,
                    color = DuckDuckGoTheme.colors.text.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Uploading… ${(state.progress * 100).toInt()}%",
                    color = DuckDuckGoTheme.colors.text.secondary,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = DuckDuckGoTheme.colors.brand.accentBlue,
            trackColor = DuckDuckGoTheme.colors.backgrounds.background,
        )
    }
}

// =============================================================================
// Error slot
// =============================================================================

@Composable
private fun ErrorContent(state: SlotState.Error, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                DuckDuckGoTheme.colors.backgrounds.surface,
                RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
    ) {
        Text(
            text = "Upload failed",
            color = DuckDuckGoTheme.colors.text.destructive,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.message,
            color = DuckDuckGoTheme.colors.text.secondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .background(
                    DuckDuckGoTheme.colors.brand.accentBlue,
                    RoundedCornerShape(10.dp),
                )
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Try again",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// =============================================================================
// Loaded slot — analyzer + preview
// =============================================================================

@Composable
private fun LoadedContent(
    slot: SlotState.Loaded,
    onRemove: () -> Unit,
    onAnalyzed: (SmartParser.ParsedData) -> Unit,
    onSaveAsGroup: (String) -> Unit,
) {
    var isAnalyzing by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf(slot.fileName) }
    val scope = rememberCoroutineScope()

    // Header with file info + actions
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                DuckDuckGoTheme.colors.backgrounds.surface,
                RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(drawable.ic_document_download_24),
            contentDescription = null,
            tint = DuckDuckGoTheme.colors.brand.accentBlue,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = slot.fileName,
                color = DuckDuckGoTheme.colors.text.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${slot.lineCount} lines",
                color = DuckDuckGoTheme.colors.text.secondary,
                fontSize = 11.sp,
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
            Icon(
                painter = painterResource(drawable.ic_fire_24),
                contentDescription = "Remove",
                tint = DuckDuckGoTheme.colors.text.destructive,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    // Analyze button
    if (slot.analysis == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isAnalyzing) DuckDuckGoTheme.colors.text.disabled
                    else DuckDuckGoTheme.colors.brand.accentBlue,
                    RoundedCornerShape(10.dp),
                )
                .clickable(enabled = !isAnalyzing) {
                    isAnalyzing = true
                    scope.launch {
                        val parsed = withContext(Dispatchers.Default) {
                            SmartParser.parse(slot.contentPreview)
                        }
                        delay(400) // small pause so user sees the loader
                        isAnalyzing = false
                        onAnalyzed(parsed)
                    }
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isAnalyzing) "Analyzing…" else "Analyze content",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    // Analysis results
    slot.analysis?.let { analysis ->
        AnalysisResultCard(
            analysis = analysis,
            saveName = saveName,
            onNameChange = { saveName = it },
            onSave = { if (saveName.isNotBlank()) onSaveAsGroup(saveName) },
        )
    }
}

// =============================================================================
// Analysis result sections
// =============================================================================

@Composable
private fun AnalysisResultCard(
    analysis: SmartParser.ParsedData,
    saveName: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    if (analysis.isEmpty) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    DuckDuckGoTheme.colors.backgrounds.surface,
                    RoundedCornerShape(12.dp),
                )
                .padding(16.dp),
        ) {
            Text(
                text = "No structured data found in this file.",
                color = DuckDuckGoTheme.colors.text.secondary,
                fontSize = 13.sp,
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                DuckDuckGoTheme.colors.backgrounds.surface,
                RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
    ) {
        Text(
            text = "Found ${analysis.totalCount} items",
            color = DuckDuckGoTheme.colors.text.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))

        analysis.emailSection()
        analysis.phoneSection()
        analysis.nameSection()
        analysis.addressSection()
        analysis.dateSection()
        analysis.companySection()
        analysis.otherSection()

        Spacer(Modifier.height(14.dp))

        // Save name + save button
        androidx.compose.material3.OutlinedTextField(
            value = saveName,
            onValueChange = onNameChange,
            label = { Text("Save as group") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    DuckDuckGoTheme.colors.brand.accentBlue,
                    RoundedCornerShape(10.dp),
                )
                .clickable(onClick = onSave)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Save to My Data",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SmartParser.ParsedData.emailSection() {
    if (emails.isEmpty()) return
    ResultSection(title = "Emails", count = emails.size, values = emails)
}

@Composable
private fun SmartParser.ParsedData.phoneSection() {
    if (phones.isEmpty()) return
    ResultSection(title = "Phones", count = phones.size, values = phones)
}

@Composable
private fun SmartParser.ParsedData.nameSection() {
    if (names.isEmpty()) return
    ResultSection(title = "Names", count = names.size, values = names)
}

@Composable
private fun SmartParser.ParsedData.addressSection() {
    val list = addresses + cities + states + countries + zipCodes
    if (list.isEmpty()) return
    ResultSection(title = "Addresses", count = list.size, values = list)
}

@Composable
private fun SmartParser.ParsedData.dateSection() {
    if (dates.isEmpty()) return
    ResultSection(title = "Dates", count = dates.size, values = dates)
}

@Composable
private fun SmartParser.ParsedData.companySection() {
    val list = companyNames + jobTitles
    if (list.isEmpty()) return
    ResultSection(title = "Companies / Roles", count = list.size, values = list)
}

@Composable
private fun SmartParser.ParsedData.otherSection() {
    val list = urls + usernames.map { "@$it" } + currencies + ids
    if (list.isEmpty()) return
    ResultSection(title = "Other", count = list.size, values = list)
}

@Composable
private fun ResultSection(title: String, count: Int, values: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = DuckDuckGoTheme.colors.text.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "· $count",
                color = DuckDuckGoTheme.colors.text.secondary,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        values.take(6).forEach { value ->
            Text(
                text = "· $value",
                color = DuckDuckGoTheme.colors.text.secondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (values.size > 6) {
            Text(
                text = "…and ${values.size - 6} more",
                color = DuckDuckGoTheme.colors.text.secondary,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

// =============================================================================
// Utilities
// =============================================================================

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment
    } catch (_: Exception) {
        uri.lastPathSegment
    }
}

private fun queryFileSize(context: android.content.Context, uri: Uri): Long {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getLong(idx) else 0L
        } ?: 0L
    } catch (_: Exception) {
        0L
    }
}