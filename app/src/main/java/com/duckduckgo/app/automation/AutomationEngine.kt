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
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

object AutomationEngine {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentWebView: WeakReference<WebView>? = null
    private var scannerJs: String? = null
    private var appContext: Context? = null

    private val _detectedFieldCount = MutableLiveData(0)
    val detectedFieldCount: LiveData<Int> get() = _detectedFieldCount

    data class DetectedField(
        val index: Int,
        val type: String,
        val name: String,
        val id: String,
        val looksLike: String,
    )

    @Volatile
    private var lastDetectedFields: List<DetectedField> = emptyList()

    // ---------- Toast helper ----------

    private fun showToast(message: String) {
        mainHandler.post {
            try {
                appContext?.let {
                    Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {}
        }
    }

    // ---------- Lifecycle ----------

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        AutomationEngineContextHolder.context = appCtx
        VaultRepository.init(appCtx)
        AutomationLog.init(appCtx)
    }

    fun attachWebView(webView: WebView) {
        val appCtx = webView.context.applicationContext
        if (appContext == null) {
            appContext = appCtx
            AutomationEngineContextHolder.context = appCtx
        }
        VaultRepository.init(appCtx)
        AutomationLog.init(appCtx)
        currentWebView = WeakReference(webView)
    }

    fun detachWebView() {
        currentWebView = null
        _detectedFieldCount.postValue(0)
        lastDetectedFields = emptyList()
    }

    // ---------- Scanner injection ----------

    fun injectScanner() {
        val webView = currentWebView?.get() ?: return
        val js = scannerJs ?: loadScanner() ?: return
        webView.evaluateJavascript(js, null)
    }

    private fun loadScanner(): String? {
        val ctx = appContext ?: return null
        return try {
            ctx.assets.open("automation/automation_scanner.js")
                .bufferedReader().use { it.readText() }
                .also { scannerJs = it }
        } catch (e: Exception) {
            Log.e("AUTOMATION", "loadScanner error: ${e.message}", e)
            null
        }
    }

    // ---------- Field detection callback ----------

    fun onFieldsDetected(json: String) {
        try {
            // Debug-only payloads from JS — ignore silently
            if (json.contains("\"debug\"")) return

            val arr = JSONArray(json)
            lastDetectedFields = (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                DetectedField(
                    index = obj.getInt("index"),
                    type = obj.optString("type"),
                    name = obj.optString("name"),
                    id = obj.optString("id"),
                    looksLike = obj.optString("looksLike"),
                )
            }
            _detectedFieldCount.postValue(lastDetectedFields.size)
        } catch (e: Exception) {
            Log.d("AUTOMATION", "onFieldsDetected parse error: ${e.message}")
            _detectedFieldCount.postValue(0)
        }
    }

    // ---------- Fill completion callback ----------

    fun onFillComplete(count: Int) {
        if (count > 0) {
            showToast("Filled $count fields")
        } else {
            showToast("No matching fields to fill")
        }
    }

    // ---------- Field key normalization ----------

    private fun normalizeKey(raw: String): String {
        val cleaned = raw.lowercase().replace(Regex("[\\s_\\-]+"), "")
        return when (cleaned) {
            "fname", "givenname" -> "firstname"
            "lname", "surname", "familyname" -> "lastname"
            "middlename", "mname" -> "middlename"
            "name" -> "fullname"
            "user", "login" -> "username"
            "mail", "emailaddress" -> "email"
            "mobile", "tel", "telephone", "phonenumber", "contact" -> "phone"
            "pwd", "pass" -> "password"
            "street", "addr", "streetaddress" -> "address"
            "town" -> "city"
            "postal", "postalcode", "pincode", "zipcode", "pin" -> "zip"
            "birth", "birthdate", "dateofbirth", "birthday" -> "dob"
            "confirmpassword", "retypepassword", "repeatpassword" -> "confirmpassword"
            "confirmemail" -> "confirmemail"
            "confirmphone" -> "confirmphone"
            "dobday" -> "dobday"
            "dobmonth" -> "dobmonth"
            "dobyear" -> "dobyear"
            "selectunknown" -> "selectunknown"
            else -> cleaned
        }
    }

    private fun splitDob(dob: String): Triple<String, String, String>? {
        val parts = dob.trim().split(Regex("[/\\-.]"))
        if (parts.size != 3) return null
        return try {
            if (parts[0].length == 4) {
                Triple(parts[2], parts[1], parts[0])
            } else {
                Triple(parts[0], parts[1], parts[2])
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveValue(
        fieldKey: String,
        byType: Map<String, String>,
        dobParts: Triple<String, String, String>?,
    ): String? {
        return when (fieldKey) {
            "skip" -> null
            "confirmpassword" -> byType["password"]
            "confirmemail" -> byType["email"]
            "confirmphone" -> byType["phone"]
            "dobday" -> dobParts?.first
            "dobmonth" -> dobParts?.second
            "dobyear" -> dobParts?.third
            else -> byType[fieldKey]
        }
    }

    // ---------- Main fill entry point ----------

    fun fillCurrentPage(groupName: String? = null) {
        val webView = currentWebView?.get() ?: run {
            showToast("No page loaded")
            return
        }
        appContext?.let {
            VaultRepository.init(it)
            AutomationLog.init(it)
        }

        // 1. Vault empty?
        val groups = VaultRepository.groups.value
        if (groups.isEmpty()) {
            showToast("No data found. Add some in My Data.")
            return
        }

        // 2. Group has entries?
        val targetGroup = if (groupName != null) {
            VaultRepository.getGroup(groupName)
        } else {
            groups.firstOrNull { it.pinned } ?: groups.firstOrNull()
        }
        if (targetGroup == null) {
            showToast("No data found for this group")
            return
        }
        val entries = targetGroup.entries
        if (entries.isEmpty()) {
            showToast("No data found in '${targetGroup.name}'")
            return
        }

        // 3. Any fields detected on the page?
        val fields = lastDetectedFields
        if (fields.isEmpty()) {
            showToast("No form fields detected on this page")
            return
        }

        // 4. Build the fill payload
        val byType = entries.associate { normalizeKey(it.fieldType) to it.value }
        val dobRaw = byType["dob"]
        val dobParts = if (dobRaw != null) splitDob(dobRaw) else null

        val usedEntries = mutableSetOf<VaultEntry>()

        val fillList = JSONArray()
        fields.forEach { field ->
            val key = normalizeKey(field.looksLike)
            val value = resolveValue(key, byType, dobParts) ?: return@forEach

            entries.firstOrNull { normalizeKey(it.fieldType) == key }?.let {
                usedEntries.add(it)
            }
            when (key) {
                "confirmpassword" -> entries.firstOrNull { normalizeKey(it.fieldType) == "password" }?.let { usedEntries.add(it) }
                "confirmemail" -> entries.firstOrNull { normalizeKey(it.fieldType) == "email" }?.let { usedEntries.add(it) }
                "confirmphone" -> entries.firstOrNull { normalizeKey(it.fieldType) == "phone" }?.let { usedEntries.add(it) }
            }

            val obj = JSONObject()
            obj.put("index", field.index)
            obj.put("value", value)
            fillList.put(obj)
        }

        if (fillList.length() == 0) {
            showToast("No matching fields to fill")
            return
        }

        // 5. Log the fill event
        val currentUrl = webView.url ?: "unknown"
        val host = try {
            Uri.parse(currentUrl).host ?: currentUrl
        } catch (_: Exception) {
            currentUrl
        }
        try {
            AutomationLog.add(
                website = host,
                groupName = targetGroup.name,
                fieldsFilled = fillList.length(),
                entriesUsed = usedEntries.toList(),
            )
        } catch (e: Exception) {
            Log.e("AUTOMATION", "Failed to write log: ${e.message}")
        }

        // 6. Send to JS
        val arg = JSONObject.quote(fillList.toString())
        mainHandler.post {
            webView.evaluateJavascript(
                "(async () => { if (window.__automationFill) await window.__automationFill($arg); })();",
                null,
            )
        }
    }
}