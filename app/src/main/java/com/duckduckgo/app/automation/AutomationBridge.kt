/*
 * Copyright (c) 2025 DuckDuckGo
 * ... license header ...
 */

package com.duckduckgo.app.automation

import android.content.Context
import android.webkit.JavascriptInterface
import androidx.annotation.Keep

/**
 * JS bridge exposed to every page as `window.AutomationBridge`.
 *
 * Anything annotated with @JavascriptInterface is callable from JavaScript running inside the
 * WebView. This is how the scanner tells the app "I found N fields on this page" and how the
 * filler reports back "I filled N fields".
 */
@Keep
class AutomationBridge {

    @Keep
    @JavascriptInterface
    fun onFieldsDetected(json: String) {
        AutomationEngine.onFieldsDetected(json)
    }

    /**
     * Called by the JS `__automationFill` function after it finishes filling fields.
     * `count` is the number of fields successfully filled.
     */
    @Keep
    @JavascriptInterface
    fun onFillComplete(count: Int) {
        AutomationEngine.onFillComplete(count)
    }
}

internal object AutomationEngineContextHolder {
    var context: Context? = null
}