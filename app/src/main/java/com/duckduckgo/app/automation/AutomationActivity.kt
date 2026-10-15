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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

class AutomationActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_URL = "AUTOMATION_OPEN_URL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init all singletons (each init is idempotent)
        VaultRepository.init(applicationContext)
        AutomationLog.init(applicationContext)
        SavedAutomationRepository.init(applicationContext)
        SiteProfileRepository.init(applicationContext)
        UploadedFileRepository.init(applicationContext)
        AutomationEngine.init(applicationContext)

        setContent {
            DuckDuckGoTheme {
                AutomationNavHost(
                    onBack = { finish() },
                    onOpenUrl = { url ->
                        val result = Intent().apply {
                            putExtra(EXTRA_OPEN_URL, url)
                        }
                        setResult(Activity.RESULT_OK, result)
                        finish()
                    },
                )
            }
        }
    }
}