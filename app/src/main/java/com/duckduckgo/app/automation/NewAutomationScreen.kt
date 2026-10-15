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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R.drawable

@Composable
fun NewAutomationScreen(
    editing: SavedAutomation? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var url by remember { mutableStateOf(editing?.url ?: "") }
    var groupName by remember { mutableStateOf(editing?.groupName ?: "") }
    var mode by remember { mutableStateOf(editing?.mode ?: "sequential") }

    var nameError by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf(false) }
    var groupError by remember { mutableStateOf(false) }

    val groups = VaultRepository.groups.value
    val shape = RoundedCornerShape(10.dp)

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
        errorTextColor = DuckDuckGoTheme.colors.text.primary,
        errorIndicatorColor = DuckDuckGoTheme.colors.text.destructive,
        errorContainerColor = DuckDuckGoTheme.colors.backgrounds.surface,
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
            Text(
                text = if (editing == null) "New Automation" else "Edit Automation",
                color = DuckDuckGoTheme.colors.text.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // Name
            Text(
                text = "Name",
                color = DuckDuckGoTheme.colors.text.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (nameError && it.isNotBlank()) nameError = false
                },
                placeholder = {
                    Text("e.g. Gmail signup", color = DuckDuckGoTheme.colors.text.secondary)
                },
                textStyle = TextStyle(color = DuckDuckGoTheme.colors.text.primary, fontSize = 14.sp),
                isError = nameError,
                supportingText = if (nameError) {
                    { Text("Name is required", color = DuckDuckGoTheme.colors.text.destructive, fontSize = 11.sp) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            )

            Spacer(Modifier.height(10.dp))

            // URL
            Text(
                text = "Website URL",
                color = DuckDuckGoTheme.colors.text.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    if (urlError && it.isNotBlank()) urlError = false
                },
                placeholder = {
                    Text("https://example.com/signup", color = DuckDuckGoTheme.colors.text.secondary)
                },
                textStyle = TextStyle(color = DuckDuckGoTheme.colors.text.primary, fontSize = 13.sp),
                isError = urlError,
                supportingText = if (urlError) {
                    { Text("URL is required", color = DuckDuckGoTheme.colors.text.destructive, fontSize = 11.sp) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
            )

            Spacer(Modifier.height(10.dp))

            // Data group
            Text(
                text = "Data group",
                color = DuckDuckGoTheme.colors.text.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))

            if (groups.isEmpty()) {
                Text(
                    text = "No data groups yet. Add one in My Data first.",
                    color = DuckDuckGoTheme.colors.text.destructive,
                    fontSize = 12.sp,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = DuckDuckGoTheme.colors.backgrounds.surface,
                            shape = RoundedCornerShape(10.dp),
                        ),
                ) {
                    groups.forEachIndexed { index, g ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    groupName = g.name
                                    groupError = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = groupName == g.name,
                                onClick = {
                                    groupName = g.name
                                    groupError = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = DuckDuckGoTheme.colors.brand.accentBlue,
                                ),
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = g.name,
                                    color = DuckDuckGoTheme.colors.text.primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "${g.entries.size} entries",
                                    color = DuckDuckGoTheme.colors.text.secondary,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        if (index < groups.size - 1) {
                            androidx.compose.material3.Divider(
                                color = DuckDuckGoTheme.colors.backgrounds.background,
                                thickness = 1.dp,
                            )
                        }
                    }
                }
                if (groupError) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Pick a data group",
                        color = DuckDuckGoTheme.colors.text.destructive,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Mode
            Text(
                text = "Fill mode",
                color = DuckDuckGoTheme.colors.text.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ModeChip(
                    label = "Series",
                    selected = mode == "sequential",
                    modifier = Modifier.weight(1f),
                    onClick = { mode = "sequential" },
                )
                Spacer(Modifier.width(8.dp))
                ModeChip(
                    label = "Random",
                    selected = mode == "random",
                    modifier = Modifier.weight(1f),
                    onClick = { mode = "random" },
                )
            }

            Spacer(Modifier.height(20.dp))

            // Save button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = DuckDuckGoTheme.colors.brand.accentBlue,
                        shape = shape,
                    )
                    .clickable {
                        nameError = name.isBlank()
                        urlError = url.isBlank()
                        groupError = groupName.isBlank()
                        if (!nameError && !urlError && !groupError) {
                            if (editing == null) {
                                SavedAutomationRepository.add(name, url, groupName, mode)
                            } else {
                                SavedAutomationRepository.update(editing.id, name, url, groupName, mode)
                            }
                            onSaved()
                        }
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (editing == null) "Save Automation" else "Update",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                color = if (selected) DuckDuckGoTheme.colors.brand.accentBlue
                else DuckDuckGoTheme.colors.backgrounds.surface,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else DuckDuckGoTheme.colors.text.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}