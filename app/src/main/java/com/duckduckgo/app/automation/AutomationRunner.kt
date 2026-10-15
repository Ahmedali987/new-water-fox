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
import androidx.compose.runtime.mutableStateOf

/**
 * Tracks which automation is currently "armed" (started from the list).
 *
 * When the browser loads a URL matching the armed automation, [AutomationEngine.maybeAutoFill]
 * triggers an automatic fill using the automation's group and rotation mode.
 */
object AutomationRunner {

    val active = mutableStateOf<SavedAutomation?>(null)

    fun start(automation: SavedAutomation) {
        active.value = automation
    }

    fun stop() {
        active.value = null
    }

    fun isActiveFor(url: String): Boolean {
        val a = active.value ?: return false
        if (url.isBlank()) return false
        return urlMatches(url, a.url)
    }

    private fun urlMatches(current: String, target: String): Boolean {
        val c = current.lowercase().trim()
        val t = target.lowercase().trim()
        if (t.isBlank()) return false
        return try {
            val cHost = Uri.parse(c).host ?: c
            val tHost = Uri.parse(t).host ?: t
            cHost == tHost || c.contains(tHost) || tHost.contains(cHost)
        } catch (_: Exception) {
            c.contains(t) || t.contains(c)
        }
    }
}