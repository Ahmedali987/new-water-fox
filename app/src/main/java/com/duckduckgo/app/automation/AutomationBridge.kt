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
import androidx.annotation.Keep
import android.webkit.JavascriptInterface
import android.widget.Toast

/**
 * JS bridge exposed to every page as `window.AutomationBridge`.
 *
 * Anything annotated with @JavascriptInterface is callable from JavaScript running inside the
 * WebView. This is how the scanner tells the app "I found N fields on this page".
 */

 @Keep
class AutomationBridge {

    /**
     * Called from automation_scanner.js when it finishes scanning the page.
     * The payload is a JSON array of detected fields — see DetectedField in AutomationEngine.
     */
    @Keep
    @JavascriptInterface
    fun onFieldsDetected(json: String) {
        // Show a diagnostic toast so we can see in real time whether the JS actually reached us.
        Handler(Looper.getMainLooper()).post {
            try {
                val ctx = AutomationEngineContextHolder.context ?: return@post
                Toast.makeText(
                    ctx,
                    "Bridge received: ${json.take(80)}",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (_: Exception) {}
        }

        // Forward to the engine, which parses JSON and updates the LiveData the omnibar observes.
        AutomationEngine.onFieldsDetected(json)
    }
}

/**
 * Holds the Application context so the bridge (which has no context of its own) can show toasts.
 * AutomationEngine sets this from init() and attachWebView().
 */
internal object AutomationEngineContextHolder {
    var context: Context? = null
}
