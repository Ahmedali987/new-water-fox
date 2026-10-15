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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

sealed interface AutomationScreen {
    object Home : AutomationScreen
    object MyData : AutomationScreen
    object AddData : AutomationScreen
    object PasteData : AutomationScreen
    object FileUpload : AutomationScreen
    object GenerateData : AutomationScreen
    object ViewLogs : AutomationScreen
    data class GroupDetail(val groupName: String) : AutomationScreen

    // Saved automations
    object AutomationList : AutomationScreen
    object NewAutomation : AutomationScreen
    data class EditAutomation(val id: Long) : AutomationScreen

    // Legacy single-shot flow
    object CreateAutomation : AutomationScreen
    data class Running(val groupName: String, val mode: String) : AutomationScreen
    data class Completed(val groupName: String, val mode: String, val count: Int) : AutomationScreen
}

@Composable
fun AutomationNavHost(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit = {},
) {
    // DDG's theme intentionally sets Material3 typography/colors to debug values
    // (cursive magenta). Override them back to sane defaults.
    MaterialTheme(
        typography = Typography(),
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = DuckDuckGoTheme.colors.brand.accentBlue,
            onPrimary = Color.White,
            secondary = DuckDuckGoTheme.colors.brand.accentBlue,
            onSecondary = Color.White,
            background = DuckDuckGoTheme.colors.backgrounds.background,
            onBackground = DuckDuckGoTheme.colors.text.primary,
            surface = DuckDuckGoTheme.colors.backgrounds.surface,
            onSurface = DuckDuckGoTheme.colors.text.primary,
            surfaceVariant = DuckDuckGoTheme.colors.backgrounds.surface,
            onSurfaceVariant = DuckDuckGoTheme.colors.text.secondary,
            outline = DuckDuckGoTheme.colors.text.secondary,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DuckDuckGoTheme.colors.backgrounds.background,
        ) {
            var current by remember { mutableStateOf<AutomationScreen>(AutomationScreen.Home) }

            BackHandler {
                when (current) {
                    AutomationScreen.Home -> onBack()
                    AutomationScreen.MyData -> current = AutomationScreen.Home
                    is AutomationScreen.GroupDetail -> current = AutomationScreen.MyData
                    AutomationScreen.AddData -> current = AutomationScreen.Home
                    AutomationScreen.PasteData -> current = AutomationScreen.AddData
                    AutomationScreen.FileUpload -> current = AutomationScreen.AddData
                    AutomationScreen.GenerateData -> current = AutomationScreen.AddData
                    AutomationScreen.ViewLogs -> current = AutomationScreen.Home

                    AutomationScreen.AutomationList -> current = AutomationScreen.Home
                    AutomationScreen.NewAutomation -> current = AutomationScreen.AutomationList
                    is AutomationScreen.EditAutomation -> current = AutomationScreen.AutomationList

                    AutomationScreen.CreateAutomation -> current = AutomationScreen.Home
                    is AutomationScreen.Running -> current = AutomationScreen.Home
                    is AutomationScreen.Completed -> current = AutomationScreen.Home
                }
            }

            when (val c = current) {
                // ================= Home =================
                AutomationScreen.Home -> AutomationHomeScreen(
                    onBack = onBack,
                    onMyDataClick = { current = AutomationScreen.MyData },
                    onAddDataClick = { current = AutomationScreen.AddData },
                    onCreateAutomationClick = { current = AutomationScreen.AutomationList },
                    onViewLogsClick = { current = AutomationScreen.ViewLogs },
                )

                // ================= My Data =================
                AutomationScreen.MyData -> MyDataScreen(
                    onBack = { current = AutomationScreen.Home },
                    onAddClick = { current = AutomationScreen.AddData },
                    onGroupClick = { name -> current = AutomationScreen.GroupDetail(name) },
                )

                // ================= Add Data Choice =================
                AutomationScreen.AddData -> AddDataChoiceScreen(
                    onBack = { current = AutomationScreen.Home },
                    onPasteClick = { current = AutomationScreen.PasteData },
                    onUploadClick = { current = AutomationScreen.FileUpload },
                    onGenerateClick = { current = AutomationScreen.GenerateData },
                )

                // ================= Paste Data =================
                AutomationScreen.PasteData -> PasteDataScreen(
                    onBack = { current = AutomationScreen.AddData },
                    onSaved = { current = AutomationScreen.Home },
                )

                // ================= File Upload =================
                AutomationScreen.FileUpload -> FileUploadScreen(
    onBack = { current = AutomationScreen.AddData },
    onGroupCreated = { groupName, parsedText ->
        VaultRepository.addGroup(groupName, parsedText)
        current = AutomationScreen.Home
    },
)

                // ================= Generate Data =================
                AutomationScreen.GenerateData -> GenerateDataScreen(
                    onBack = { current = AutomationScreen.AddData },
                    onSaved = { current = AutomationScreen.Home },
                )

                // ================= Logs =================
                AutomationScreen.ViewLogs -> ViewLogsScreen(
                    onBack = { current = AutomationScreen.Home },
                )

                // ================= Group Detail =================
                is AutomationScreen.GroupDetail -> GroupDetailScreen(
                    groupName = c.groupName,
                    onBack = { current = AutomationScreen.MyData },
                )

                // ================= Saved Automations =================
                AutomationScreen.AutomationList -> AutomationListScreen(
                    onBack = { current = AutomationScreen.Home },
                    onStart = { automation ->
                        // Arm the runner and tell the browser to open the URL
                        AutomationRunner.start(automation)
                        onOpenUrl(automation.url)
                        onBack()
                    },
                    onEdit = { automation ->
                        current = AutomationScreen.EditAutomation(automation.id)
                    },
                    onNew = { current = AutomationScreen.NewAutomation },
                )

                AutomationScreen.NewAutomation -> NewAutomationScreen(
                    editing = null,
                    onBack = { current = AutomationScreen.AutomationList },
                    onSaved = { current = AutomationScreen.AutomationList },
                )

                is AutomationScreen.EditAutomation -> {
                    val editing = SavedAutomationRepository.get(c.id)
                    if (editing != null) {
                        NewAutomationScreen(
                            editing = editing,
                            onBack = { current = AutomationScreen.AutomationList },
                            onSaved = { current = AutomationScreen.AutomationList },
                        )
                    } else {
                        // Automation disappeared (deleted elsewhere) — go back to list
                        current = AutomationScreen.AutomationList
                    }
                }

                // ================= Legacy single-shot flow =================
                AutomationScreen.CreateAutomation -> CreateAutomationScreen(
                    onBack = { current = AutomationScreen.Home },
                    onStart = { groupName, mode ->
                        current = AutomationScreen.Running(groupName, mode)
                    },
                )

                is AutomationScreen.Running -> AutomationRunningScreen(
                    groupName = c.groupName,
                    mode = c.mode,
                    onFinished = { count ->
                        current = AutomationScreen.Completed(c.groupName, c.mode, count)
                    },
                    onCancel = { current = AutomationScreen.Home },
                )

                is AutomationScreen.Completed -> AutomationCompletedScreen(
                    groupName = c.groupName,
                    mode = c.mode,
                    totalFilled = c.count,
                    onViewLogs = { current = AutomationScreen.ViewLogs },
                    onRunAgain = { current = AutomationScreen.CreateAutomation },
                    onBack = { current = AutomationScreen.Home },
                )
            }
        }
    }
}