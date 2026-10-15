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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R.drawable

@Composable
fun PasteDataScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var fieldName by remember { mutableStateOf("") }
    var valuesText by remember { mutableStateOf("") }

    var fieldNameError by remember { mutableStateOf(false) }
    var valuesError by remember { mutableStateOf(false) }

    val valueCount = valuesText
        .lines()
        .map { it.trim() }
        .count { it.isNotEmpty() }

    val shape = RoundedCornerShape(10.dp)

    val fieldColors = TextFieldDefaults.colors(
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
            Text(
                text = stringResource(R.string.automation_paste_title),
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
            // ---------- Field name ----------
            Text(
                text = "Field name",
                color = DuckDuckGoTheme.colors.text.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = fieldName,
                onValueChange = {
                    fieldName = it
                    if (fieldNameError && it.isNotBlank()) fieldNameError = false
                },
                placeholder = {
                    Text(
                        text = "e.g. Phone Number",
                        color = DuckDuckGoTheme.colors.text.secondary,
                    )
                },
                textStyle = TextStyle(
                    color = DuckDuckGoTheme.colors.text.primary,
                    fontSize = 14.sp,
                ),
                isError = fieldNameError,
                supportingText = if (fieldNameError) {
                    {
                        Text(
                            text = "Field name is required",
                            color = DuckDuckGoTheme.colors.text.destructive,
                            fontSize = 11.sp,
                        )
                    }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
            )

            Spacer(Modifier.height(10.dp))

            // ---------- Values ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Values (one per line)",
                    color = DuckDuckGoTheme.colors.text.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (valueCount > 0) {
                    Text(
                        text = stringResource(R.string.automation_values_count, valueCount),
                        color = DuckDuckGoTheme.colors.brand.accentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = valuesText,
                onValueChange = {
                    valuesText = it
                    if (valuesError && it.isNotBlank()) valuesError = false
                },
                placeholder = {
                    Text(
                        text = "+91 9876543210\n+91 9876543211\n+91 9876543212",
                        color = DuckDuckGoTheme.colors.text.secondary,
                    )
                },
                textStyle = TextStyle(
                    color = DuckDuckGoTheme.colors.text.primary,
                    fontSize = 14.sp,
                ),
                isError = valuesError,
                supportingText = if (valuesError) {
                    {
                        Text(
                            text = "Paste at least one value",
                            color = DuckDuckGoTheme.colors.text.destructive,
                            fontSize = 11.sp,
                        )
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                shape = shape,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Default,
                ),
            )

            Spacer(Modifier.height(16.dp))

            // ---------- Save button ----------
            val canSave = fieldName.isNotBlank() && valueCount > 0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (canSave) DuckDuckGoTheme.colors.brand.accentBlue
                        else DuckDuckGoTheme.colors.text.disabled,
                        shape = shape,
                    )
                    .clickable {
                        if (canSave) {
                            VaultRepository.addGroup(fieldName, valuesText)
                            onSaved()
                        } else {
                            // Show validation errors
                            fieldNameError = fieldName.isBlank()
                            valuesError = valueCount == 0
                        }
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (valueCount > 0) {
                        stringResource(R.string.automation_save_with_count, valueCount)
                    } else {
                        stringResource(R.string.automation_save)
                    },
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}