/*
 * Copyright (c) 2025 DuckDuckGo
 * ... license header ...
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R.drawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FileUploadScreen(
    onBack: () -> Unit,
    onFileImported: (UploadedFile) -> Unit,
) {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(Dispatchers.Main) }

    var isUploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var renameId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<Long?>(null) }

    val files = UploadedFileRepository.files.value

    // Upload animation
    val infiniteTransition = rememberInfiniteTransition(label = "upload")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val shape = RoundedCornerShape(10.dp)
    val fieldColors = TextFieldDefaults.colors(
        focusedTextColor = DuckDuckGoTheme.colors.text.primary,
        unfocusedTextColor = DuckDuckGoTheme.colors.text.primary,
        cursorColor = DuckDuckGoTheme.colors.brand.accentBlue,
        focusedContainerColor = DuckDuckGoTheme.colors.backgrounds.surface,
        unfocusedContainerColor = DuckDuckGoTheme.colors.backgrounds.surface,
        focusedIndicatorColor = DuckDuckGoTheme.colors.brand.accentBlue,
        unfocusedIndicatorColor = DuckDuckGoTheme.colors.text.secondary,
        errorTextColor = DuckDuckGoTheme.colors.text.primary,
        errorIndicatorColor = DuckDuckGoTheme.colors.text.destructive,
        errorContainerColor = DuckDuckGoTheme.colors.backgrounds.surface,
    )

    // File picker
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isUploading = true
        uploadError = null

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val name = queryDisplayName(context, uri) ?: "uploaded_file.txt"
                    val text = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: ""
                    if (text.isBlank()) {
                        null to "File is empty or unreadable"
                    } else {
                        val file = UploadedFileRepository.add(
                            originalName = name,
                            displayName = name.substringBeforeLast('.'),
                            content = text,
                        )
                        file to null
                    }
                } catch (e: Exception) {
                    null to "Failed to read file: ${e.message}"
                }
            }

            val (file, error) = result
            isUploading = false
            if (file != null) {
                onFileImported(file)
            } else {
                uploadError = error ?: "Unknown error"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuckDuckGoTheme.colors.backgrounds.background),
    ) {
        // ---------- Top bar ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DuckDuckGoTheme.colors.backgrounds.surface)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(drawable.ic_arrow_left_24),
                    contentDescription = stringResource(R.string.automation_back),
                    tint = DuckDuckGoTheme.colors.icons.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Upload TXT File",
                    color = DuckDuckGoTheme.colors.text.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${files.size} uploaded",
                    color = DuckDuckGoTheme.colors.text.secondary,
                    fontSize = 12.sp,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // ---------- Upload button ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isUploading) DuckDuckGoTheme.colors.text.disabled
                        else DuckDuckGoTheme.colors.brand.accentBlue,
                        shape = shape,
                    )
                    .clickable(enabled = !isUploading) {
                        picker.launch("text/*")
                    }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(drawable.ic_downloads_24),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .scale(if (isUploading) pulseScale else 1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isUploading) "Uploading..." else "Choose file",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (isUploading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = DuckDuckGoTheme.colors.brand.accentBlue,
                    trackColor = DuckDuckGoTheme.colors.backgrounds.surface,
                )
            }

            uploadError?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = err,
                    color = DuckDuckGoTheme.colors.text.destructive,
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(20.dp))

            if (files.isEmpty() && !isUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No files uploaded yet.",
                        color = DuckDuckGoTheme.colors.text.secondary,
                        fontSize = 13.sp,
                    )
                }
            }

            // ---------- Files list ----------
            files.forEach { file ->
                val isRenaming = renameId == file.id

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = DuckDuckGoTheme.colors.backgrounds.surface,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(drawable.ic_document_download_24),
                            contentDescription = null,
                            tint = DuckDuckGoTheme.colors.brand.accentBlue,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.displayName,
                                color = DuckDuckGoTheme.colors.text.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${file.lineCount} lines",
                                color = DuckDuckGoTheme.colors.text.secondary,
                                fontSize = 11.sp,
                            )
                        }
                        if (!isRenaming) {
                            IconButton(
                                onClick = {
                                    renameId = file.id
                                    renameText = file.displayName
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    painter = painterResource(drawable.ic_edit_24),
                                    contentDescription = "Rename",
                                    tint = DuckDuckGoTheme.colors.icons.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            IconButton(
                                onClick = { deleteId = file.id },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    painter = painterResource(drawable.ic_fire_24),
                                    contentDescription = "Delete",
                                    tint = DuckDuckGoTheme.colors.text.destructive,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }

                    if (isRenaming) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = shape,
                            colors = fieldColors,
                            textStyle = TextStyle(
                                color = DuckDuckGoTheme.colors.text.primary,
                                fontSize = 14.sp,
                            ),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Cancel",
                                color = DuckDuckGoTheme.colors.text.secondary,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clickable { renameId = null }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Save",
                                color = DuckDuckGoTheme.colors.brand.accentBlue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable {
                                        if (renameText.isNotBlank()) {
                                            UploadedFileRepository.rename(file.id, renameText)
                                        }
                                        renameId = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Delete confirmation dialog
    deleteId?.let { id ->
        val file = files.firstOrNull { it.id == id }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { deleteId = null },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(
                        color = DuckDuckGoTheme.colors.backgrounds.surface,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { }
                    .padding(20.dp),
            ) {
                Text(
                    text = "Delete file?",
                    color = DuckDuckGoTheme.colors.text.primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = file?.displayName ?: "",
                    color = DuckDuckGoTheme.colors.text.secondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = "Cancel",
                        color = DuckDuckGoTheme.colors.brand.accentBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { deleteId = null }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Delete",
                        color = DuckDuckGoTheme.colors.text.destructive,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                UploadedFileRepository.delete(id)
                                deleteId = null
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Query the real filename from a content URI.
 */
private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && cursor.moveToFirst()) cursor.getString(nameIdx) else null
        } ?: uri.lastPathSegment
    } catch (_: Exception) {
        uri.lastPathSegment
    }
}