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
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R.drawable

@Composable
fun AutomationListScreen(
    onBack: () -> Unit,
    onStart: (SavedAutomation) -> Unit,
    onEdit: (SavedAutomation) -> Unit,
    onNew: () -> Unit,
) {
    val items = SavedAutomationRepository.automations.value
    var deleteId by remember { mutableStateOf<Long?>(null) }

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Automations",
                    color = DuckDuckGoTheme.colors.text.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${items.size} saved",
                    color = DuckDuckGoTheme.colors.text.secondary,
                    fontSize = 12.sp,
                )
            }
            IconButton(onClick = onNew) {
                Icon(
                    painter = painterResource(drawable.ic_add_24),
                    contentDescription = "New automation",
                    tint = DuckDuckGoTheme.colors.icons.primary,
                )
            }
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No automations yet.",
                        color = DuckDuckGoTheme.colors.text.secondary,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Tap + to create one.",
                        color = DuckDuckGoTheme.colors.text.secondary,
                        fontSize = 12.sp,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                items.forEach { a ->
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
                                painter = painterResource(drawable.ic_automation_24),
                                contentDescription = null,
                                tint = DuckDuckGoTheme.colors.brand.accentBlue,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = a.name,
                                    color = DuckDuckGoTheme.colors.text.primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = a.url,
                                    color = DuckDuckGoTheme.colors.text.secondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${a.groupName} · ${a.mode.replaceFirstChar { it.uppercase() }}",
                                    color = DuckDuckGoTheme.colors.text.secondary,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            IconButton(
                                onClick = { onEdit(a) },
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
                                onClick = { deleteId = a.id },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    painter = painterResource(drawable.ic_fire_24),
                                    contentDescription = "Delete",
                                    tint = DuckDuckGoTheme.colors.text.destructive,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Start",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .background(
                                        color = DuckDuckGoTheme.colors.brand.accentBlue,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .clickable { onStart(a) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    deleteId?.let { id ->
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
                    text = "Delete this automation?",
                    color = DuckDuckGoTheme.colors.text.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
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
                                SavedAutomationRepository.delete(id)
                                deleteId = null
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}