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

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

data class SavedAutomation(
    val id: Long,
    val name: String,
    val url: String,
    val groupName: String,
    val mode: String,          // "sequential" or "random"
    val createdAt: Long,
)

object SavedAutomationRepository {
    private const val PREFS_NAME = "automation_saved"
    private const val KEY_LIST = "list"
    private var prefs: SharedPreferences? = null

    val automations = mutableStateOf<List<SavedAutomation>>(emptyList())

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        automations.value = load()
    }

    fun add(name: String, url: String, groupName: String, mode: String) {
        val a = SavedAutomation(
            id = System.currentTimeMillis(),
            name = name.trim(),
            url = url.trim(),
            groupName = groupName,
            mode = mode,
            createdAt = System.currentTimeMillis(),
        )
        automations.value = automations.value + a
        save()
    }

    fun update(id: Long, name: String, url: String, groupName: String, mode: String) {
        automations.value = automations.value.map {
            if (it.id == id) it.copy(
                name = name.trim(),
                url = url.trim(),
                groupName = groupName,
                mode = mode,
            ) else it
        }
        save()
    }

    fun delete(id: Long) {
        automations.value = automations.value.filterNot { it.id == id }
        save()
    }

    fun get(id: Long): SavedAutomation? = automations.value.firstOrNull { it.id == id }

    private fun save() {
        val arr = JSONArray()
        automations.value.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("name", it.name)
            o.put("url", it.url)
            o.put("groupName", it.groupName)
            o.put("mode", it.mode)
            o.put("createdAt", it.createdAt)
            arr.put(o)
        }
        prefs?.edit()?.putString(KEY_LIST, arr.toString())?.apply()
    }

    private fun load(): List<SavedAutomation> {
        val raw = prefs?.getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                SavedAutomation(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    url = o.getString("url"),
                    groupName = o.getString("groupName"),
                    mode = o.getString("mode"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}