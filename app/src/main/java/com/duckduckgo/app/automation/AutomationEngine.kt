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

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        AutomationEngineContextHolder.context = appCtx
        VaultRepository.init(appCtx)
    }

    fun attachWebView(webView: WebView) {
        val appCtx = webView.context.applicationContext
        if (appContext == null) {
            appContext = appCtx
            AutomationEngineContextHolder.context = appCtx
        }
        VaultRepository.init(appCtx)
        currentWebView = WeakReference(webView)
    }

    fun detachWebView() {
        currentWebView = null
        _detectedFieldCount.postValue(0)
        lastDetectedFields = emptyList()
    }

    fun injectScanner() {
        val webView = currentWebView?.get()
        if (webView == null) {
            Log.d("AUTOMATION", "injectScanner: webView is NULL")
            return
        }

        // DIAGNOSTIC: probe whether the bridge is registered
        webView.evaluateJavascript("typeof window.AutomationBridge") { result ->
            mainHandler.post {
                appContext?.let {
                    Toast.makeText(it, "Bridge type: $result", Toast.LENGTH_LONG).show()
                }
            }
        }

        val js = scannerJs ?: loadScanner()
        if (js == null) {
            Log.e("AUTOMATION", "injectScanner: JS asset could not be loaded (appContext=$appContext)")
            return
        }
        Log.d("AUTOMATION", "injectScanner: evaluating JS (${js.length} chars)")
        webView.evaluateJavascript(js) { result ->
            Log.d("AUTOMATION", "injectScanner result: $result")
        }
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

    fun onFieldsDetected(json: String) {
        Log.d("AUTOMATION", "onFieldsDetected called: $json")
        mainHandler.post {
            appContext?.let {
                Toast.makeText(it, "Bridge: ${json.take(80)}", Toast.LENGTH_LONG).show()
            }
        }
        try {
            // Debug-only payload — don't try to parse as field list
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
            Log.d("AUTOMATION", "Parsed ${lastDetectedFields.size} fields")
            _detectedFieldCount.postValue(lastDetectedFields.size)
        } catch (e: Exception) {
            Log.d("AUTOMATION", "onFieldsDetected parse error: ${e.message}")
            _detectedFieldCount.postValue(0)
        }
    }

    private fun normalizeKey(raw: String): String {
        val cleaned = raw.lowercase().replace(Regex("[\\s_\\-]+"), "")
        return when (cleaned) {
            "fname", "givenname" -> "firstname"
            "lname", "surname", "familyname" -> "lastname"
            "name" -> "fullname"
            "user", "login" -> "username"
            "mail", "emailaddress" -> "email"
            "mobile", "tel", "telephone", "phonenumber", "contact" -> "phone"
            "pwd", "pass" -> "password"
            "street", "addr", "streetaddress" -> "address"
            "town" -> "city"
            "postal", "postalcode", "pincode", "zipcode", "pin" -> "zip"
            "birth", "birthdate", "dateofbirth", "birthday" -> "dob"
            else -> cleaned
        }
    }

    fun fillCurrentPage(groupName: String? = null) {
        val webView = currentWebView?.get() ?: return
        appContext?.let { VaultRepository.init(it) }

        val groups = VaultRepository.groups.value
        val targetGroup = if (groupName != null) {
            VaultRepository.getGroup(groupName)
        } else {
            groups.firstOrNull { it.pinned } ?: groups.firstOrNull()
        }
        val entries = targetGroup?.entries ?: return
        val fields = lastDetectedFields
        if (entries.isEmpty() || fields.isEmpty()) return

        val byType = entries.associate { normalizeKey(it.fieldType) to it.value }
        val fallback = entries.firstOrNull()?.value ?: ""

        val fillList = JSONArray()
        fields.forEach { field ->
            val key = normalizeKey(field.looksLike)
            val value = byType[key]
                ?: byType["text"]
                ?: fallback
            val obj = JSONObject()
            obj.put("index", field.index)
            obj.put("value", value)
            fillList.put(obj)
        }
        val arg = JSONObject.quote(fillList.toString())
        mainHandler.post {
            webView.evaluateJavascript(
                "(async () => { if (window.__automationFill) await window.__automationFill($arg); })();",
                null,
            )
        }
    }
}
