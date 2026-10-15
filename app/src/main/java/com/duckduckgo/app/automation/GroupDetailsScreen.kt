/*
 * Copyright (c) 2025 DuckDuckGo
 * ... license header ...
 */

package com.duckduckgo.app.automation

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R.drawable

@Composable
fun GroupDetailScreen(
    groupName: String,
    onBack: () -> Unit,
) {
    // Trigger recomposition when the vault changes
    val groups = VaultRepository.groups.value
    val group = groups.firstOrNull { it.name == groupName }

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editValue by remember { mutableStateOf("") }
    var addFieldName by remember { mutableStateOf("") }
    var addFieldValue by remember { mutableStateOf("") }
    var showAddRow by remember { mutableStateOf(false) }

    val fieldColors = TextFieldDefaults.colors(
        focusedTextColor = DuckDuckGoTheme.colors.text.primary,
        unfocusedTextColor = DuckDuckGoTheme.colors.text.primary,
        focusedLabelColor = DuckDuckGoTheme.colors.text.secondary,
        unfocusedLabelColor = DuckDuckGoTheme.colors.text.secondary,
        focusedPlaceholderColor = DuckDuckGoTheme.colors.text.secondary,
        unfocusedPlaceholderColor = DuckDuckGoTheme.colors.text.secondary,
        cursorColor = DuckDuckGoTheme.colors.brand.accentBlue,
        focusedContainerColor = DuckDuckGoTheme.colors.backgrounds.surface,
        unfocusedContainerColor = DuckDuckGoTheme.colors.backgrounds.surface,
        focusedIndicatorColor = DuckDuckGoTheme.colors.brand.accentBlue,
        unfocusedIndicatorColor = DuckDuckGoTheme.colors.text.secondary,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuckDuckGoTheme.colors.backgrounds.background),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DuckDuckGoTheme.colors.backgrounds.surface)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
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
                    text = groupName,
                    color = DuckDuckGoTheme.colors.text.primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.automation_entries_count, group?.entries?.size ?: 0),
                    color = DuckDuckGoTheme.colors.text.secondary,
                    fontSize = 13.sp,
                )
            }
            IconButton(onClick = { showAddRow = !showAddRow }) {
                Icon(
                    painter = painterResource(drawable.ic_add_24),
                    contentDescription = "Add field",
                    tint = DuckDuckGoTheme.colors.icons.primary,
                )
            }
        }

        val entries = group?.entries ?: emptyList()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Add-new-field form
            if (showAddRow) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = DuckDuckGoTheme.colors.backgrounds.surface,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                ) {
                    Text(
                        text = "New field",
                        color = DuckDuckGoTheme.colors.text.secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addFieldName,
                        onValueChange = { addFieldName = it },
                        label = { Text("Field name (e.g. email)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addFieldValue,
                        onValueChange = { addFieldValue = it },
                        label = { Text("Value") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Cancel",
                            color = DuckDuckGoTheme.colors.text.secondary,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { showAddRow = false; addFieldName = ""; addFieldValue = "" }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Add",
                            color = DuckDuckGoTheme.colors.brand.accentBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable {
                                    if (addFieldName.isNotBlank() && addFieldValue.isNotBlank()) {
                                        VaultRepository.addEntryToGroup(groupName, addFieldName, addFieldValue)
                                        addFieldName = ""
                                        addFieldValue = ""
                                        showAddRow = false
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (entries.isEmpty() && !showAddRow) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No entries yet. Tap + to add one.",
                        color = DuckDuckGoTheme.colors.text.secondary,
                        fontSize = 14.sp,
                    )
                }
            }

            entries.forEachIndexed { index, entry ->
                val isEditing = editingIndex == index

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = DuckDuckGoTheme.colors.backgrounds.surface,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.fieldType.replaceFirstChar { it.uppercase() },
                                color = DuckDuckGoTheme.colors.text.secondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        if (!isEditing) {
                            IconButton(onClick = {
                                editingIndex = index
                                editValue = entry.value
                            }) {
                                Icon(
                                    painter = painterResource(drawable.ic_edit_24),
                                    contentDescription = "Edit",
                                    tint = DuckDuckGoTheme.colors.icons.primary,
                                    modifier = Modifier.width(20.dp),
                                )
                            }
                            IconButton(onClick = {
                                VaultRepository.deleteEntry(groupName, index)
                            }) {
                                Icon(
                                    painter = painterResource(drawable.ic_fire_24),
                                    contentDescription = "Delete",
                                    tint = DuckDuckGoTheme.colors.text.destructive,
                                    modifier = Modifier.width(20.dp),
                                )
                            }
                        }
                    }

                    if (isEditing) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = editValue,
                            onValueChange = { editValue = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = fieldColors,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Cancel",
                                color = DuckDuckGoTheme.colors.text.secondary,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clickable { editingIndex = null }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Save",
                                color = DuckDuckGoTheme.colors.brand.accentBlue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable {
                                        VaultRepository.updateEntryValue(groupName, index, editValue)
                                        editingIndex = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = entry.value,
                            color = DuckDuckGoTheme.colors.text.primary,
                            fontSize = 15.sp,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}