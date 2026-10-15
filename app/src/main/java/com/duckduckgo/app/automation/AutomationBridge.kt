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
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.annotation.Keep

/**
 * JS ↔ Kotlin bridge exposed to every page as `window.AutomationBridge`.
 *
 * Every public @JavascriptInterface method must:
 *  - Be lightweight (runs on the WebView JS thread).
 *  - Post heavy work to the main thread if it touches UI, SharedPreferences, or Compose state.
 */
@Keep
class AutomationBridge {

    /**
     * Called by the scanner after it finishes scanning a page.
     * Payload: JSON array of detected fields (index, type, name, id, visualLabel, looksLike).
     */
    @Keep
    @JavascriptInterface
    fun onFieldsDetected(json: String) {
        AutomationEngine.onFieldsDetected(json)
    }

    /**
     * Called when the scanner learns a rule (either passively via user interaction
     * or actively via teach mode).
     * Payload: JSON object with { domain, selector, nameAttr, idAttr, visualLabel,
     *                            controlType, targetKey, strategy, fixedValue }.
     */
    @Keep
    @JavascriptInterface
    fun onRuleLearned(json: String) {
        AutomationEngine.onRuleLearned(json)
    }

    /**
     * Called by the scanner's `__automationExecute` after it finishes filling.
     * `count` is the number of fields actually filled.
     *
     * This releases the Kotlin fill lock and shows a confirmation toast.
     */
    @Keep
    @JavascriptInterface
    fun onFillComplete(count: Int) {
        AutomationEngine.onFillComplete(count)
    }

    /**
     * Lets JS show a native toast without needing its own UI thread plumbing.
     * Used by the scanner for error/info messages inside the WebView.
     */
    @Keep
    @JavascriptInterface
    fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                val ctx = AutomationEngineContextHolder.context ?: return@post
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
    }
}

/**
 * Holds the Application context so the bridge (which has no context of its own)
 * can show toasts and route to the engine. AutomationEngine sets this from init().
 */
internal object AutomationEngineContextHolder {
    @Volatile
    var context: Context? = null
}