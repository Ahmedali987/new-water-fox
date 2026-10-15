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
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Central orchestrator for the automation feature.
 *
 * Responsibilities:
 *  - Inject the JS scanner into each page and receive detected fields.
 *  - Learn reusable rules per website (teach mode).
 *  - Rotate through user's data pools per website (sequential / random).
 *  - Fill forms on demand or automatically when an automation is armed.
 *  - Expose reactive state so Compose UIs can reflect teach mode, rotation mode, etc.
 */
object AutomationEngine {

    private const val TAG = "AUTOMATION"
    private const val AUTO_FILL_DELAY_MS = 1200L

    // ==================================================================================
    // Threading
    // ==================================================================================
    private val mainHandler = Handler(Looper.getMainLooper())

    // ==================================================================================
    // WebView
    // ==================================================================================
    private var currentWebView: WeakReference<WebView>? = null
    private var scannerJs: String? = null
    private var appContext: Context? = null

    // ==================================================================================
    // Reactive state (observed by Compose)
    // ==================================================================================
    val isTeachModeActive = mutableStateOf(false)
    val rotationModeLabel = mutableStateOf("Rotation: 1-by-1")

    private val _detectedFieldCount = MutableLiveData(0)
    val detectedFieldCount: LiveData<Int> get() = _detectedFieldCount

    // Guards against overlapping fills on the same page
    private val isFilling = AtomicBoolean(false)

    // ==================================================================================
    // Field model
    // ==================================================================================
    data class DetectedField(
        val index: Int,
        val selector: String,
        val type: String,
        val name: String,
        val id: String,
        val visualLabel: String,
        val looksLike: String,
    )

    @Volatile
    private var lastDetectedFields: List<DetectedField> = emptyList()

    // ==================================================================================
    // Country lookup (phone number decomposition)
    // ==================================================================================
    data class CountryInfo(
        val dialCodeDigits: String,
        val iso2: String,
        val iso3: String,
        val countryName: String,
    )

    private val countryTable = listOf(
        CountryInfo("91", "IN", "IND", "India"),
        CountryInfo("1", "US", "USA", "United States"),
        CountryInfo("44", "GB", "GBR", "United Kingdom"),
        CountryInfo("971", "AE", "ARE", "United Arab Emirates"),
        CountryInfo("966", "SA", "SAU", "Saudi Arabia"),
        CountryInfo("65", "SG", "SGP", "Singapore"),
        CountryInfo("61", "AU", "AUS", "Australia"),
        CountryInfo("49", "DE", "DEU", "Germany"),
        CountryInfo("33", "FR", "FRA", "France"),
        CountryInfo("81", "JP", "JPN", "Japan"),
        CountryInfo("880", "BD", "BGD", "Bangladesh"),
        CountryInfo("92", "PK", "PAK", "Pakistan"),
        CountryInfo("977", "NP", "NPL", "Nepal"),
        CountryInfo("94", "LK", "LKA", "Sri Lanka"),
    )

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        AutomationEngineContextHolder.context = appCtx
        VaultRepository.init(appCtx)
        AutomationLog.init(appCtx)
        SiteProfileRepository.init(appCtx)
        SavedAutomationRepository.init(appCtx)
        syncRotationLabel()
    }

    fun attachWebView(webView: WebView) {
        val appCtx = webView.context.applicationContext
        if (appContext == null) {
            appContext = appCtx
            AutomationEngineContextHolder.context = appCtx
        }
        VaultRepository.init(appCtx)
        AutomationLog.init(appCtx)
        SiteProfileRepository.init(appCtx)
        SavedAutomationRepository.init(appCtx)
        currentWebView = WeakReference(webView)
        syncRotationLabel()
    }

    fun detachWebView() {
        // Cancel any pending auto-fill
        mainHandler.removeCallbacksAndMessages(null)
        isFilling.set(false)
        currentWebView = null
        _detectedFieldCount.postValue(0)
        lastDetectedFields = emptyList()
        isTeachModeActive.value = false
    }

    // ==================================================================================
    // Domain helper
    // ==================================================================================

    fun currentDomain(): String {
        val url = currentWebView?.get()?.url
        return SiteProfileRepository.extractDomain(url)
    }

    // ==================================================================================
    // Scanner injection
    // ==================================================================================

    fun injectScanner() {
        val webView = currentWebView?.get() ?: return
        val js = scannerJs ?: loadScanner() ?: return

        mainHandler.post {
            val domain = SiteProfileRepository.extractDomain(webView.url)
            val vaultJson = JSONObject.quote(
                buildVaultDictionaryJson(
                    groupName = null,
                    domain = domain,
                    advanceRotation = false,
                ),
            )
            val rulesJson = JSONObject.quote(SiteProfileRepository.getRulesJsonForDomain(domain))

            webView.evaluateJavascript(js) {
                webView.evaluateJavascript(
                    "if (window.__automationConfigure) window.__automationConfigure($vaultJson, $rulesJson);",
                    null,
                )
            }
        }
    }

    private fun loadScanner(): String? {
        val ctx = appContext ?: return null
        return try {
            ctx.assets.open("automation/automation_scanner.js")
                .bufferedReader().use { it.readText() }
                .also { scannerJs = it }
        } catch (e: Exception) {
            Log.e(TAG, "loadScanner error: ${e.message}", e)
            null
        }
    }

    // ==================================================================================
    // Field detection
    // ==================================================================================

    fun onFieldsDetected(json: String) {
        try {
            if (json.contains("\"debug\"")) return

            val arr = JSONArray(json)
            lastDetectedFields = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DetectedField(
                    index = obj.optInt("index", i),
                    selector = obj.optString("selector"),
                    type = obj.optString("type"),
                    name = obj.optString("name"),
                    id = obj.optString("id"),
                    visualLabel = obj.optString("visualLabel"),
                    looksLike = obj.optString("looksLike"),
                )
            }
            _detectedFieldCount.postValue(lastDetectedFields.size)
            Log.d(TAG, "Detected ${lastDetectedFields.size} fields")
        } catch (e: Exception) {
            Log.e(TAG, "onFieldsDetected parse error: ${e.message}")
            _detectedFieldCount.postValue(0)
        }
    }

    // ==================================================================================
    // Fill completion (JS → Kotlin)
    // ==================================================================================

    fun onFillComplete(count: Int) {
        isFilling.set(false)
        if (count > 0) {
            showToast("Filled $count fields")
        } else {
            showToast("No matching fields to fill")
        }
    }

    // ==================================================================================
    // Learning (teach mode)
    // ==================================================================================

    fun onRuleLearned(json: String) {
        try {
            val obj = JSONObject(json)
            val domain = obj.optString("domain").ifBlank { currentDomain() }
            if (domain.isBlank()) return

            val rule = LearnedRule(
                selector = obj.optString("selector"),
                nameAttr = obj.optString("nameAttr"),
                idAttr = obj.optString("idAttr"),
                visualLabel = obj.optString("visualLabel"),
                controlType = obj.optString("controlType", "text"),
                targetKey = obj.optString("targetKey"),
                strategy = obj.optString("strategy", "NATIVE_SETTER"),
                fixedValue = obj.optString("fixedValue"),
            )
            SiteProfileRepository.saveOrUpdateRule(domain, rule)
            showToast("Learned [${rule.targetKey}] for $domain")
        } catch (e: Exception) {
            Log.e(TAG, "onRuleLearned error: ${e.message}")
        }
    }

    fun toggleTeachMode() {
        val webView = currentWebView?.get() ?: run {
            showToast("No page loaded")
            return
        }
        isTeachModeActive.value = !isTeachModeActive.value
        val active = isTeachModeActive.value

        mainHandler.post {
            val domain = SiteProfileRepository.extractDomain(webView.url)
            val vaultJson = JSONObject.quote(
                buildVaultDictionaryJson(
                    groupName = null,
                    domain = domain,
                    advanceRotation = false,
                ),
            )
            webView.evaluateJavascript(
                "if (window.__automationSetTeachMode) window.__automationSetTeachMode($active, $vaultJson);",
                null,
            )
        }
        showToast(if (active) "Teach mode ON — tap a field to bind it" else "Teach mode OFF")
    }

    // ==================================================================================
    // Rotation management
    // ==================================================================================

    fun toggleRotationMode() {
        val newMode = SiteProfileRepository.toggleRotationMode()
        syncRotationLabel()
        val label = if (newMode == RotationMode.SEQUENTIAL) {
            "Rotation: 1-by-1 (Top to Bottom)"
        } else {
            "Rotation: Random (Non-Repeating)"
        }
        showToast(label)
    }

    private fun syncRotationLabel() {
        rotationModeLabel.value = when (SiteProfileRepository.rotationMode) {
            RotationMode.SEQUENTIAL -> "Rotation: 1-by-1"
            RotationMode.RANDOM -> "Rotation: Random"
        }
    }

    fun resetCurrentSiteRotation() {
        val domain = currentDomain()
        if (domain.isBlank()) return
        SiteProfileRepository.resetUsageForSite(domain)
        showToast("Reset counter to #1 for $domain")
    }

    fun clearCurrentSiteProfile() {
        val domain = currentDomain()
        if (domain.isBlank()) return
        SiteProfileRepository.clearDomain(domain)
        showToast("Cleared learned rules & counter for $domain")
        injectScanner()
    }

    // ==================================================================================
    // Field key normalization
    // ==================================================================================

    private fun normalizeKey(raw: String): String {
        val cleaned = raw.lowercase().replace(Regex("[\\s_\\-]+"), "")
        return when (cleaned) {
            "fname", "givenname", "first" -> "firstname"
            "lname", "surname", "familyname", "last" -> "lastname"
            "middlename", "mname" -> "middlename"
            "name", "yourname", "fullname" -> "fullname"
            "user", "login", "userid", "username" -> "username"
            "mail", "emailaddress", "email" -> "email"
            "mobile", "tel", "telephone", "phonenumber", "contact", "cell", "phone" -> "phone"
            "countrycode", "dialcode", "isd", "phonecode" -> "countrycode"
            "pwd", "pass", "password" -> "password"
            "confirmpassword", "retypepassword", "repeatpassword" -> "confirmpassword"
            "confirmemail" -> "confirmemail"
            "confirmphone" -> "confirmphone"
            "street", "addr", "streetaddress", "line1", "address" -> "address"
            "town", "district", "city" -> "city"
            "province", "region", "state" -> "state"
            "postal", "postalcode", "pincode", "zipcode", "pin", "zip" -> "zip"
            "birth", "birthdate", "dateofbirth", "birthday", "dob" -> "dob"
            "sex", "gender" -> "gender"
            "country", "nation" -> "country"
            "dobday" -> "dobday"
            "dobmonth" -> "dobmonth"
            "dobyear" -> "dobyear"
            else -> cleaned
        }
    }

    // ==================================================================================
    // Phone / country decomposition
    // ==================================================================================

    private fun decomposePhoneAndCountry(rawMap: MutableMap<String, String>) {
        try {
            val rawPhone = rawMap["phone"] ?: ""
            val rawCountry = rawMap["country"] ?: ""
            val rawCode = rawMap["countrycode"] ?: ""

            var matchedCountry: CountryInfo? = null

            if (rawCountry.isNotBlank()) {
                matchedCountry = countryTable.firstOrNull {
                    it.countryName.equals(rawCountry, ignoreCase = true) ||
                        it.iso2.equals(rawCountry, ignoreCase = true) ||
                        it.iso3.equals(rawCountry, ignoreCase = true)
                }
            }

            if (matchedCountry == null && rawCode.isNotBlank()) {
                val codeDigits = rawCode.filter { it.isDigit() }
                matchedCountry = countryTable.firstOrNull { it.dialCodeDigits == codeDigits }
            }

            val digitsOnly = rawPhone.filter { it.isDigit() }
            val hasPlus = rawPhone.trim().startsWith("+") || rawPhone.trim().startsWith("00")

            var nationalNumber = digitsOnly
            if (digitsOnly.length > 10) {
                val strippedZero = digitsOnly.trimStart('0')
                if (strippedZero.length == 10) {
                    nationalNumber = strippedZero
                } else {
                    val sortedCountries = countryTable.sortedByDescending { it.dialCodeDigits.length }
                    for (c in sortedCountries) {
                        if (strippedZero.startsWith(c.dialCodeDigits) &&
                            (strippedZero.length - c.dialCodeDigits.length) in 7..11
                        ) {
                            if (matchedCountry == null) matchedCountry = c
                            nationalNumber = strippedZero.removePrefix(c.dialCodeDigits)
                            break
                        }
                    }
                }
            } else if (digitsOnly.length == 11 && digitsOnly.startsWith("0")) {
                nationalNumber = digitsOnly.substring(1)
            }

            val finalCountry = matchedCountry ?: countryTable.first()
            val dialDigits = finalCountry.dialCodeDigits

            if (nationalNumber.isNotEmpty()) {
                rawMap["phonenational"] = nationalNumber
                rawMap["phonee164"] = "+$dialDigits$nationalNumber"
                rawMap["phonedigits"] = "$dialDigits$nationalNumber"
                rawMap["phone"] = if (hasPlus) "+$dialDigits$nationalNumber" else nationalNumber
            }

            rawMap["countrycode"] = "+$dialDigits"
            rawMap["countrycodedigits"] = dialDigits
            rawMap["countryiso2"] = finalCountry.iso2
            rawMap["countryiso3"] = finalCountry.iso3
            if (rawMap["country"].isNullOrBlank()) {
                rawMap["country"] = finalCountry.countryName
            }

            // Name decomposition
            val fullName = rawMap["fullname"]
            if (!fullName.isNullOrBlank()) {
                val parts = fullName.trim().split(Regex("\\s+"))
                if (rawMap["firstname"].isNullOrBlank() && parts.isNotEmpty()) {
                    rawMap["firstname"] = parts.first()
                }
                if (rawMap["lastname"].isNullOrBlank() && parts.size > 1) {
                    rawMap["lastname"] = parts.drop(1).joinToString(" ")
                }
            } else if (!rawMap["firstname"].isNullOrBlank() || !rawMap["lastname"].isNullOrBlank()) {
                rawMap["fullname"] = listOfNotNull(rawMap["firstname"], rawMap["lastname"])
                    .joinToString(" ").trim()
            }

            // DOB decomposition for split day/month/year selects
            val dob = rawMap["dob"]
            if (!dob.isNullOrBlank()) {
                val parts = dob.trim().split(Regex("[/\\-.]"))
                if (parts.size == 3) {
                    if (parts[0].length == 4) {
                        rawMap["dobyear"] = parts[0]
                        rawMap["dobmonth"] = parts[1]
                        rawMap["dobday"] = parts[2]
                    } else {
                        rawMap["dobday"] = parts[0]
                        rawMap["dobmonth"] = parts[1]
                        rawMap["dobyear"] = parts[2]
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "decompose error: ${e.message}")
        }
    }

    // ==================================================================================
    // Vault dictionary builder (per-domain rotation)
    // ==================================================================================

    /**
     * Groups multiple entries of the same fieldType into pools (e.g., 10 emails, 10 usernames)
     * and selects the next unused item for the current domain.
     */
    private fun buildVaultDictionaryJson(
        groupName: String?,
        domain: String,
        advanceRotation: Boolean,
    ): String {
        appContext?.let {
            VaultRepository.init(it)
            SiteProfileRepository.init(it)
        }
        val groups = VaultRepository.groups.value
        val targetGroup = if (groupName != null) {
            VaultRepository.getGroup(groupName)
        } else {
            groups.firstOrNull { it.pinned } ?: groups.firstOrNull()
        }

        val groupedPools = mutableMapOf<String, MutableList<String>>()
        targetGroup?.entries?.forEach { entry ->
            val normKey = normalizeKey(entry.fieldType)
            groupedPools.getOrPut(normKey) { mutableListOf() }.add(entry.value)
        }

        val maxPoolSize = groupedPools.values.maxOfOrNull { it.size } ?: 1
        val groupKeyName = targetGroup?.name ?: "default"

        val chosenRowIndex = SiteProfileRepository.nextIndexForSite(
            domain = domain,
            groupName = groupKeyName,
            fieldKey = "row_cursor",
            poolSize = maxPoolSize,
            advanceCursor = advanceRotation,
        )

        val selectedMap = mutableMapOf<String, String>()
        val allValuesArray = JSONArray()

        groupedPools.forEach { (key, pool) ->
            if (pool.isNotEmpty()) {
                val itemValue = pool[chosenRowIndex % pool.size]
                selectedMap[key] = itemValue
                pool.forEach { v -> allValuesArray.put("$key:$v") }
            }
        }

        val fallbackList = groupedPools["text"] ?: targetGroup?.entries?.map { it.value } ?: emptyList()
        if (fallbackList.isNotEmpty()) {
            selectedMap["fallback"] = fallbackList[chosenRowIndex % fallbackList.size]
        }

        decomposePhoneAndCountry(selectedMap)

        if (advanceRotation && maxPoolSize > 1) {
            val previewVal = selectedMap["email"]
                ?: selectedMap["username"]
                ?: selectedMap["phonenational"]
                ?: selectedMap["fallback"]
                ?: ""
            val modeTag = if (SiteProfileRepository.rotationMode == RotationMode.SEQUENTIAL) {
                "1-by-1"
            } else {
                "Random"
            }
            showToast("[$domain] Item #${chosenRowIndex + 1}/$maxPoolSize ($modeTag): $previewVal")
        }

        val json = JSONObject()
        selectedMap.forEach { (k, v) -> json.put(k, v) }
        json.put("__allPoolValues", allValuesArray)
        return json.toString()
    }

    // ==================================================================================
    // Learned rule matching
    // ==================================================================================

    private fun matchLearnedRule(domain: String, field: DetectedField): LearnedRule? {
        val profile = SiteProfileRepository.getProfile(domain) ?: return null
        return profile.rules.firstOrNull { rule ->
            (rule.idAttr.isNotEmpty() && rule.idAttr == field.id) ||
                (rule.nameAttr.isNotEmpty() && rule.nameAttr == field.name) ||
                (rule.visualLabel.isNotEmpty() && rule.visualLabel.equals(field.visualLabel, ignoreCase = true)) ||
                (rule.selector.isNotEmpty() && rule.selector == field.selector)
        }
    }

    // ==================================================================================
    // Fill
    // ==================================================================================

    fun fillCurrentPage(groupName: String? = null) {
        val webView = currentWebView?.get() ?: run {
            showToast("No page loaded")
            return
        }
        if (isFilling.get()) {
            Log.d(TAG, "Fill already in progress, skipping")
            return
        }
        isFilling.set(true)

        appContext?.let {
            VaultRepository.init(it)
            SiteProfileRepository.init(it)
        }

        // Validate before doing anything
        val groups = VaultRepository.groups.value
        if (groups.isEmpty()) {
            isFilling.set(false)
            showToast("No data found. Add some in My Data.")
            return
        }
        if (lastDetectedFields.isEmpty()) {
            isFilling.set(false)
            showToast("No form fields detected on this page")
            return
        }

        val targetGroup = if (groupName != null) {
            VaultRepository.getGroup(groupName)
        } else {
            groups.firstOrNull { it.pinned } ?: groups.firstOrNull()
        }
        if (targetGroup == null || targetGroup.entries.isEmpty()) {
            isFilling.set(false)
            showToast("Selected group has no data")
            return
        }

        mainHandler.post {
            val domain = SiteProfileRepository.extractDomain(webView.url)
            val vaultJson = JSONObject.quote(
                buildVaultDictionaryJson(
                    groupName = groupName,
                    domain = domain,
                    advanceRotation = true,
                ),
            )
            val rulesJson = JSONObject.quote(SiteProfileRepository.getRulesJsonForDomain(domain))

            webView.evaluateJavascript(
                "(async () => { if (window.__automationExecute) await window.__automationExecute($vaultJson, $rulesJson); })();",
                null,
            )

            // Safety net: if JS never calls onFillComplete, release the lock after 10s
            mainHandler.postDelayed({
                if (isFilling.get()) {
                    isFilling.set(false)
                    Log.w(TAG, "Fill lock released by timeout")
                }
            }, 10_000L)
        }
    }

    // ==================================================================================
    // Auto-fill on page load (called from DuckDuckGoWebView.onPageFinished)
    // ==================================================================================

    fun maybeAutoFill(url: String?) {
        val auto = AutomationRunner.active.value ?: return
        if (!AutomationRunner.isActiveFor(url ?: "")) return

        val webView = currentWebView?.get() ?: return
        mainHandler.postDelayed({
            if (AutomationRunner.active.value?.id == auto.id) {
                fillCurrentPage(auto.groupName)
                AutomationRunner.stop()
            }
        }, AUTO_FILL_DELAY_MS)
        Log.d(TAG, "Auto-fill scheduled for ${auto.name} on $url")
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private fun showToast(message: String) {
        mainHandler.post {
            try {
                appContext?.let {
                    Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {}
        }
    }

    fun getDetectedFieldsJson(): String {
        val arr = JSONArray()
        lastDetectedFields.forEach { f ->
            val o = JSONObject()
            o.put("index", f.index)
            o.put("selector", f.selector)
            o.put("type", f.type)
            o.put("name", f.name)
            o.put("id", f.id)
            o.put("visualLabel", f.visualLabel)
            o.put("looksLike", f.looksLike)
            arr.put(o)
        }
        return arr.toString()
    }
}