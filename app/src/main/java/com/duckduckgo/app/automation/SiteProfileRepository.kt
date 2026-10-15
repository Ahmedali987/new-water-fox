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
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

enum class RotationMode {
    SEQUENTIAL, // 1-by-1 from top to bottom
    RANDOM, // Random unused entry per website
}

data class LearnedRule(
    val selector: String,
    val nameAttr: String,
    val idAttr: String,
    val visualLabel: String,
    val controlType: String,
    val targetKey: String,
    val strategy: String,
    val fixedValue: String = "",
)

data class SiteProfile(
    val domain: String,
    val rules: List<LearnedRule>,
)

object SiteProfileRepository {
    private const val PREFS_NAME = "automation_site_profiles"
    private const val KEY_PROFILES = "profiles_json"
    private const val KEY_USAGE = "site_usage_json"
    private const val KEY_ROTATION_MODE = "rotation_mode"

    private var prefs: SharedPreferences? = null
    private val profiles = mutableMapOf<String, SiteProfile>()

    // Key: "domain|groupName|fieldKey" -> list of already used indices on that domain
    private val usedIndicesMap = mutableMapOf<String, MutableList<Int>>()

    var rotationMode: RotationMode = RotationMode.SEQUENTIAL
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedMode = prefs?.getString(KEY_ROTATION_MODE, RotationMode.SEQUENTIAL.name)
        rotationMode = runCatching { RotationMode.valueOf(savedMode!!) }.getOrDefault(RotationMode.SEQUENTIAL)
        loadAll()
        loadUsage()
    }

    fun toggleRotationMode(): RotationMode {
        rotationMode = if (rotationMode == RotationMode.SEQUENTIAL) {
            RotationMode.RANDOM
        } else {
            RotationMode.SEQUENTIAL
        }
        prefs?.edit()?.putString(KEY_ROTATION_MODE, rotationMode.name)?.apply()
        return rotationMode
    }

    /**
     * Picks the next index (0 until poolSize) for a specific domain + group + fieldKey.
     * Tracks used indices per website so each site progresses through your list independently.
     */
    fun nextIndexForSite(
        domain: String,
        groupName: String,
        fieldKey: String,
        poolSize: Int,
        advanceCursor: Boolean = true,
    ): Int {
        if (poolSize <= 1) return 0
        val cleanDomain = domain.lowercase().removePrefix("www.").ifBlank { "global" }
        val trackerKey = "$cleanDomain|${groupName.lowercase()}|${fieldKey.lowercase()}"
        val used = usedIndicesMap.getOrPut(trackerKey) { mutableListOf() }

        // Remove any out-of-bounds indices if the user deleted entries from the Vault
        used.removeAll { it >= poolSize || it < 0 }

        // If all items in the pool were already used on this site, reset cycle for this site
        if (used.size >= poolSize) {
            if (advanceCursor) {
                used.clear()
            } else {
                return used.lastOrNull() ?: 0
            }
        }

        val availableIndices = (0 until poolSize).filterNot { it in used }
        val chosenIndex = when (rotationMode) {
            RotationMode.SEQUENTIAL -> availableIndices.firstOrNull() ?: 0
            RotationMode.RANDOM -> availableIndices[Random.nextInt(availableIndices.size)]
        }

        if (advanceCursor) {
            used.add(chosenIndex)
            persistUsage()
        }
        return chosenIndex
    }

    fun getUsedCountForSite(
        domain: String,
        groupName: String,
        fieldKey: String = "row_cursor",
    ): Int {
        val cleanDomain = domain.lowercase().removePrefix("www.").ifBlank { "global" }
        val trackerKey = "$cleanDomain|${groupName.lowercase()}|${fieldKey.lowercase()}"
        return usedIndicesMap[trackerKey]?.size ?: 0
    }

    fun resetUsageForSite(domain: String) {
        val cleanDomain = domain.lowercase().removePrefix("www.").ifBlank { "global" }
        val keysToRemove = usedIndicesMap.keys.filter { it.startsWith("$cleanDomain|") }
        keysToRemove.forEach { usedIndicesMap.remove(it) }
        persistUsage()
    }

    fun extractDomain(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return try {
            Uri.parse(url).host?.lowercase()?.removePrefix("www.") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun getProfile(domain: String): SiteProfile? {
        if (domain.isBlank()) return null
        return profiles[domain.lowercase().removePrefix("www.")]
    }

    fun saveOrUpdateRule(
        domain: String,
        rule: LearnedRule,
    ) {
        val cleanDomain = domain.lowercase().removePrefix("www.")
        if (cleanDomain.isBlank()) return

        val existing = profiles[cleanDomain]?.rules?.toMutableList() ?: mutableListOf()
        val matchIndex = existing.indexOfFirst {
            (it.selector.isNotEmpty() && it.selector == rule.selector) ||
                (it.idAttr.isNotEmpty() && it.idAttr == rule.idAttr) ||
                (it.nameAttr.isNotEmpty() && it.nameAttr == rule.nameAttr && it.controlType == rule.controlType)
        }

        if (matchIndex >= 0) {
            existing[matchIndex] = rule
        } else {
            existing.add(rule)
        }

        profiles[cleanDomain] = SiteProfile(domain = cleanDomain, rules = existing)
        persist()
    }

    fun clearDomain(domain: String) {
        val cleanDomain = domain.lowercase().removePrefix("www.")
        profiles.remove(cleanDomain)
        resetUsageForSite(cleanDomain)
        persist()
    }

    fun getRulesJsonForDomain(domain: String): String {
        val profile = getProfile(domain) ?: return "[]"
        val arr = JSONArray()
        profile.rules.forEach { r ->
            val obj = JSONObject()
            obj.put("selector", r.selector)
            obj.put("nameAttr", r.nameAttr)
            obj.put("idAttr", r.idAttr)
            obj.put("visualLabel", r.visualLabel)
            obj.put("controlType", r.controlType)
            obj.put("targetKey", r.targetKey)
            obj.put("strategy", r.strategy)
            obj.put("fixedValue", r.fixedValue)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun persistUsage() {
        val root = JSONObject()
        usedIndicesMap.forEach { (key, list) ->
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            root.put(key, arr)
        }
        prefs?.edit()?.putString(KEY_USAGE, root.toString())?.apply()
    }

    private fun loadUsage() {
        val raw = prefs?.getString(KEY_USAGE, null) ?: return
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = root.optJSONArray(k) ?: continue
                val list = mutableListOf<Int>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getInt(i))
                }
                usedIndicesMap[k] = list
            }
        } catch (_: Exception) {
        }
    }

    private fun persist() {
        val root = JSONObject()
        profiles.forEach { (domain, profile) ->
            val arr = JSONArray()
            profile.rules.forEach { r ->
                val obj = JSONObject()
                obj.put("selector", r.selector)
                obj.put("nameAttr", r.nameAttr)
                obj.put("idAttr", r.idAttr)
                obj.put("visualLabel", r.visualLabel)
                obj.put("controlType", r.controlType)
                obj.put("targetKey", r.targetKey)
                obj.put("strategy", r.strategy)
                obj.put("fixedValue", r.fixedValue)
                arr.put(obj)
            }
            root.put(domain, arr)
        }
        prefs?.edit()?.putString(KEY_PROFILES, root.toString())?.apply()
    }

    private fun loadAll() {
        val raw = prefs?.getString(KEY_PROFILES, null) ?: return
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val domain = keys.next()
                val arr = root.optJSONArray(domain) ?: continue
                val rules = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    LearnedRule(
                        selector = obj.optString("selector"),
                        nameAttr = obj.optString("nameAttr"),
                        idAttr = obj.optString("idAttr"),
                        visualLabel = obj.optString("visualLabel"),
                        controlType = obj.optString("controlType", "text"),
                        targetKey = obj.optString("targetKey"),
                        strategy = obj.optString("strategy", "NATIVE_SETTER"),
                        fixedValue = obj.optString("fixedValue"),
                    )
                }
                profiles[domain] = SiteProfile(domain = domain, rules = rules)
            }
        } catch (_: Exception) {
        }
    }
}
