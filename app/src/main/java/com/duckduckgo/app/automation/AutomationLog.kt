/*
 * Copyright (c) 2025 DuckDuckGo
 * ... license header ...
 */

package com.duckduckgo.app.automation

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

data class AutomationLogEntry(
    val id: Long,
    val website: String,
    val groupName: String,
    val fieldsFilled: Int,
    val timestamp: Long,
    val entriesUsed: List<VaultEntry>,
)

object AutomationLog {
    private const val PREFS_NAME = "automation_logs"
    private const val KEY_LOGS = "logs"
    private const val MAX_LOGS = 200

    private var prefs: SharedPreferences? = null
    val logs = mutableStateOf<List<AutomationLogEntry>>(emptyList())

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        logs.value = load()
    }

    fun add(
        website: String,
        groupName: String,
        fieldsFilled: Int,
        entriesUsed: List<VaultEntry>,
    ) {
        val entry = AutomationLogEntry(
            id = System.currentTimeMillis(),
            website = website.take(200),
            groupName = groupName,
            fieldsFilled = fieldsFilled,
            timestamp = System.currentTimeMillis(),
            entriesUsed = entriesUsed,
        )
        logs.value = (listOf(entry) + logs.value).take(MAX_LOGS)
        save()
    }

    fun delete(id: Long) {
        logs.value = logs.value.filterNot { it.id == id }
        save()
    }

    fun clearAll() {
        logs.value = emptyList()
        save()
    }

    private fun save() {
        val arr = JSONArray()
        logs.value.forEach { log ->
            val obj = JSONObject()
            obj.put("id", log.id)
            obj.put("website", log.website)
            obj.put("groupName", log.groupName)
            obj.put("fieldsFilled", log.fieldsFilled)
            obj.put("timestamp", log.timestamp)
            val entriesArr = JSONArray()
            log.entriesUsed.forEach { e ->
                val eo = JSONObject()
                eo.put("fieldType", e.fieldType)
                eo.put("value", e.value)
                entriesArr.put(eo)
            }
            obj.put("entriesUsed", entriesArr)
            arr.put(obj)
        }
        prefs?.edit()?.putString(KEY_LOGS, arr.toString())?.apply()
    }

    private fun load(): List<AutomationLogEntry> {
        val raw = prefs?.getString(KEY_LOGS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val entriesArr = obj.optJSONArray("entriesUsed") ?: JSONArray()
                val entries = (0 until entriesArr.length()).map { j ->
                    val eo = entriesArr.getJSONObject(j)
                    VaultEntry(eo.getString("fieldType"), eo.getString("value"))
                }
                AutomationLogEntry(
                    id = obj.getLong("id"),
                    website = obj.getString("website"),
                    groupName = obj.getString("groupName"),
                    fieldsFilled = obj.getInt("fieldsFilled"),
                    timestamp = obj.getLong("timestamp"),
                    entriesUsed = entries,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}