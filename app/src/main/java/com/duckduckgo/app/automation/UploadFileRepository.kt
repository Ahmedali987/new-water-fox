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

data class UploadedFile(
    val id: Long,
    val originalName: String,
    val displayName: String,
    val contentPreview: String,
    val lineCount: Int,
    val uploadedAt: Long,
)

object UploadedFileRepository {
    private const val PREFS_NAME = "automation_uploaded_files"
    private const val KEY_FILES = "files"

    private var prefs: SharedPreferences? = null
    val files = mutableStateOf<List<UploadedFile>>(emptyList())

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        files.value = load()
    }

    fun add(
        originalName: String,
        displayName: String,
        content: String,
    ): UploadedFile {
        val lines = content.lines().filter { it.trim().isNotEmpty() }
        val file = UploadedFile(
            id = System.currentTimeMillis(),
            originalName = originalName,
            displayName = displayName,
            contentPreview = content.take(2000),
            lineCount = lines.size,
            uploadedAt = System.currentTimeMillis(),
        )
        files.value = listOf(file) + files.value
        save()
        return file
    }

    fun rename(id: Long, newName: String) {
        files.value = files.value.map {
            if (it.id == id) it.copy(displayName = newName.trim()) else it
        }
        save()
    }

    fun delete(id: Long) {
        files.value = files.value.filterNot { it.id == id }
        save()
    }

    fun get(id: Long): UploadedFile? = files.value.firstOrNull { it.id == id }

    private fun save() {
        val arr = JSONArray()
        files.value.forEach { f ->
            val o = JSONObject()
            o.put("id", f.id)
            o.put("originalName", f.originalName)
            o.put("displayName", f.displayName)
            o.put("contentPreview", f.contentPreview)
            o.put("lineCount", f.lineCount)
            o.put("uploadedAt", f.uploadedAt)
            arr.put(o)
        }
        prefs?.edit()?.putString(KEY_FILES, arr.toString())?.apply()
    }

    private fun load(): List<UploadedFile> {
        val raw = prefs?.getString(KEY_FILES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                UploadedFile(
                    id = o.getLong("id"),
                    originalName = o.getString("originalName"),
                    displayName = o.optString("displayName", o.getString("originalName")),
                    contentPreview = o.optString("contentPreview"),
                    lineCount = o.optInt("lineCount", 0),
                    uploadedAt = o.optLong("uploadedAt", 0L),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}