/*
 * Copyright (c) 2025 DuckDuckGo
 * ... license header ...
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R.drawable

object MockFormGenerator {
    val defaultFields: List<Pair<String, String>> = listOf(
        "email" to "user${(1000..9999).random()}@example.com",
        "confirmEmail" to "user${(1000..9999).random()}@example.com",
        "password" to "SecurePass${(100..999).random()}!",
        "confirmPassword" to "SecurePass${(100..999).random()}!",
        "username" to "user_${(10000..99999).random()}",
        "fullName" to "John Doe",
        "surname" to "Doe",
        "middleName" to "Michael",
        "nickname" to "Johnny",
        "address" to "123 Main Street, Mumbai, 400001",
        "gender" to "Male",
        "dob" to "15/05/1990",
    )
}

@Composable
fun GenerateDataScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var groupName by remember { mutableStateOf("") }

    // Mutable state list so we can edit/add/remove
    val fields = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            addAll(MockFormGenerator.defaultFields)
        }
    }

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

    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuckDuckGoTheme.colors.backgrounds.background),
    ) {
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
            Text(
                text = stringResource(R.string.automation_generate_title),
                color = DuckDuckGoTheme.colors.text.primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.automation_generate_desc),
                color = DuckDuckGoTheme.colors.text.secondary,
                fontSize = 14.sp,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text(stringResource(R.string.automation_field_name_label)) },
                placeholder = {
                    Text(
                        text = stringResource(R.string.automation_generate_name_placeholder),
                        color = DuckDuckGoTheme.colors.text.secondary,
                    )
                },
                textStyle = TextStyle(color = DuckDuckGoTheme.colors.text.primary, fontSize = 15.sp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.automation_generate_preview),
                color = DuckDuckGoTheme.colors.text.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(8.dp))

            // Each editable field row
            fields.forEachIndexed { index, (key, value) ->
                var currentKey by remember { mutableStateOf(key) }
                var currentValue by remember { mutableStateOf(value) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = DuckDuckGoTheme.colors.backgrounds.surface,
                            shape = shape,
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = currentKey,
                            onValueChange = {
                                currentKey = it
                                fields[index] = it to currentValue
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = fieldColors,
                            textStyle = TextStyle(color = DuckDuckGoTheme.colors.text.secondary, fontSize = 12.sp),
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = currentValue,
                            onValueChange = {
                                currentValue = it
                                fields[index] = currentKey to it
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = fieldColors,
                            textStyle = TextStyle(color = DuckDuckGoTheme.colors.text.primary, fontSize = 14.sp),
                        )
                    }
                    IconButton(onClick = { fields.removeAt(index) }) {
                        Icon(
                            painter = painterResource(drawable.ic_fire_24),
                            contentDescription = "Remove",
                            tint = DuckDuckGoTheme.colors.text.destructive,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(4.dp))

            // Add new field button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        fields.add("newField" to "")
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(drawable.ic_add_24),
                    contentDescription = null,
                    tint = DuckDuckGoTheme.colors.brand.accentBlue,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Add field",
                    color = DuckDuckGoTheme.colors.brand.accentBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val name = groupName.ifBlank {
                        "Form ${System.currentTimeMillis() % 10000}"
                    }
                    val text = fields.joinToString("\n") { (k, v) -> "$k: $v" }
                    VaultRepository.addGroup(name, text)
                    onSaved()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DuckDuckGoTheme.colors.brand.accentBlue,
                    contentColor = Color.White,
                ),
                shape = shape,
            ) {
                Text(
                    text = stringResource(R.string.automation_generate_save),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}