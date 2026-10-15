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
import androidx.compose.ui.text.TextStyle
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
    val groups = VaultRepository.groups.value
    val group = groups.firstOrNull { it.name == groupName }

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editValue by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf(false) }

    var showAddRow by remember { mutableStateOf(false) }
    var addFieldName by remember { mutableStateOf("") }
    var addFieldValue by remember { mutableStateOf("") }
    var addNameError by remember { mutableStateOf(false) }
    var addValueError by remember { mutableStateOf(false) }

    val fieldShape = RoundedCornerShape(10.dp)

    // Neutral field colors (no pink!)
    val baseFieldColors = TextFieldDefaults.colors(
        focusedTextColor = DuckDuckGoTheme.colors.text.primary,
        unfocusedTextColor = DuckDuckGoTheme.colors.text.primary,
        disabledTextColor = DuckDuckGoTheme.colors.text.disabled,
        focusedLabelColor = DuckDuckGoTheme.colors.text.secondary,
        unfocusedLabelColor = DuckDuckGoTheme.colors.text.secondary,
        focusedPlaceholderColor = DuckDuckGoTheme.colors.text.secondary,
        unfocusedPlaceholderColor = DuckDuckGoTheme.colors.text.secondary,
        cursorColor = DuckDuckGoTheme.colors.brand.accentBlue,
        focusedContainerColor = DuckDuckGoTheme.colors.backgrounds.surface,
        unfocusedContainerColor = DuckDuckGoTheme.colors.backgrounds.surface,
        focusedIndicatorColor = DuckDuckGoTheme.colors.brand.accentBlue,
        unfocusedIndicatorColor = DuckDuckGoTheme.colors.text.secondary,
        errorTextColor = DuckDuckGoTheme.colors.text.primary,
        errorLabelColor = DuckDuckGoTheme.colors.text.destructive,
        errorIndicatorColor = DuckDuckGoTheme.colors.text.destructive,
        errorCursorColor = DuckDuckGoTheme.colors.text.destructive,
        errorContainerColor = DuckDuckGoTheme.colors.backgrounds.surface,
        errorPlaceholderColor = DuckDuckGoTheme.colors.text.secondary,
    )

    val editFieldColors = TextFieldDefaults.colors(
        focusedTextColor = DuckDuckGoTheme.colors.text.primary,
        unfocusedTextColor = DuckDuckGoTheme.colors.text.primary,
        cursorColor = DuckDuckGoTheme.colors.brand.accentBlue,
        focusedContainerColor = DuckDuckGoTheme.colors.backgrounds.surface,
        unfocusedContainerColor = DuckDuckGoTheme.colors.backgrounds.surface,
        focusedIndicatorColor = DuckDuckGoTheme.colors.brand.accentBlue,
        unfocusedIndicatorColor = DuckDuckGoTheme.colors.text.secondary,
        errorTextColor = DuckDuckGoTheme.colors.text.primary,
        errorCursorColor = DuckDuckGoTheme.colors.text.destructive,
        errorIndicatorColor = DuckDuckGoTheme.colors.text.destructive,
        errorContainerColor = DuckDuckGoTheme.colors.backgrounds.surface,
    )

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
                    text = groupName,
                    color = DuckDuckGoTheme.colors.text.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.automation_entries_count, group?.entries?.size ?: 0),
                    color = DuckDuckGoTheme.colors.text.secondary,
                    fontSize = 12.sp,
                )
            }
            IconButton(onClick = {
                showAddRow = !showAddRow
                if (!showAddRow) {
                    addFieldName = ""
                    addFieldValue = ""
                    addNameError = false
                    addValueError = false
                }
            }) {
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // ---------- Add new field form ----------
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
                        color = DuckDuckGoTheme.colors.text.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = addFieldName,
                        onValueChange = {
                            addFieldName = it
                            if (addNameError && it.isNotBlank()) addNameError = false
                        },
                        label = { Text("Field name") },
                        placeholder = {
                            Text(
                                text = "e.g. email",
                                color = DuckDuckGoTheme.colors.text.secondary,
                            )
                        },
                        textStyle = TextStyle(
                            color = DuckDuckGoTheme.colors.text.primary,
                            fontSize = 14.sp,
                        ),
                        isError = addNameError,
                        supportingText = if (addNameError) {
                            { Text("Field name is required", color = DuckDuckGoTheme.colors.text.destructive, fontSize = 11.sp) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        colors = baseFieldColors,
                    )

                    Spacer(Modifier.height(6.dp))

                    OutlinedTextField(
                        value = addFieldValue,
                        onValueChange = {
                            addFieldValue = it
                            if (addValueError && it.isNotBlank()) addValueError = false
                        },
                        label = { Text("Value") },
                        textStyle = TextStyle(
                            color = DuckDuckGoTheme.colors.text.primary,
                            fontSize = 14.sp,
                        ),
                        isError = addValueError,
                        supportingText = if (addValueError) {
                            { Text("Value is required", color = DuckDuckGoTheme.colors.text.destructive, fontSize = 11.sp) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        colors = baseFieldColors,
                    )

                    Spacer(Modifier.height(6.dp))

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
                                .clickable {
                                    showAddRow = false
                                    addFieldName = ""
                                    addFieldValue = ""
                                    addNameError = false
                                    addValueError = false
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Add",
                            color = DuckDuckGoTheme.colors.brand.accentBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable {
                                    addNameError = addFieldName.isBlank()
                                    addValueError = addFieldValue.isBlank()
                                    if (!addNameError && !addValueError) {
                                        VaultRepository.addEntryToGroup(groupName, addFieldName, addFieldValue)
                                        addFieldName = ""
                                        addFieldValue = ""
                                        showAddRow = false
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // ---------- Empty state ----------
            if (entries.isEmpty() && !showAddRow) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No entries yet. Tap + to add one.",
                        color = DuckDuckGoTheme.colors.text.secondary,
                        fontSize = 13.sp,
                    )
                }
            }

            // ---------- Entries ----------
            entries.forEachIndexed { index, entry ->
                val isEditing = editingIndex == index

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = DuckDuckGoTheme.colors.backgrounds.surface,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.fieldType.replaceFirstChar { it.uppercase() },
                            color = DuckDuckGoTheme.colors.text.secondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        if (!isEditing) {
                            IconButton(
                                onClick = {
                                    editingIndex = index
                                    editValue = entry.value
                                    editError = false
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    painter = painterResource(drawable.ic_edit_24),
                                    contentDescription = "Edit",
                                    tint = DuckDuckGoTheme.colors.icons.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    VaultRepository.deleteEntry(groupName, index)
                                    if (editingIndex == index) editingIndex = null
                                },
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

                    if (isEditing) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = editValue,
                            onValueChange = {
                                editValue = it
                                if (editError && it.isNotBlank()) editError = false
                            },
                            textStyle = TextStyle(
                                color = DuckDuckGoTheme.colors.text.primary,
                                fontSize = 14.sp,
                            ),
                            isError = editError,
                            supportingText = if (editError) {
                                { Text("Value is required", color = DuckDuckGoTheme.colors.text.destructive, fontSize = 11.sp) }
                            } else null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = fieldShape,
                            colors = editFieldColors,
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
                                    .clickable { editingIndex = null }
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
                                        editError = editValue.isBlank()
                                        if (!editError) {
                                            VaultRepository.updateEntryValue(groupName, index, editValue)
                                            editingIndex = null
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    } else {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = entry.value,
                            color = DuckDuckGoTheme.colors.text.primary,
                            fontSize = 14.sp,
                            maxLines = 2,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}