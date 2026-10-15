(function() {
    function debug(msg) {
        try {
            window.AutomationBridge.onFieldsDetected(JSON.stringify([{ debug: msg }]));
        } catch (e) {}
    }

    if (window.__automationScannerInstalled) {
        if (typeof window.__automationScan === 'function') {
            window.__automationScan();
        }
        return;
    }
    window.__automationScannerInstalled = true;

    debug("scanner loaded");

    function findLabelText(el) {
        var aria = el.getAttribute('aria-label');
        if (aria) return aria;
        var id = el.id;
        if (id) {
            try {
                var escapedId = (typeof CSS !== 'undefined' && CSS.escape) ? CSS.escape(id) : id.replace(/"/g, '\\"');
                var label = document.querySelector('label[for="' + escapedId + '"]');
                if (label) return label.textContent.trim();
            } catch (e) {}
        }
        var parent = el.closest && el.closest('label');
        if (parent) return parent.textContent.trim();
        return el.placeholder || '';
    }

    // Detect if a text input is a verification/OTP field (should NOT be auto-filled)
    function isOtpField(el, hint) {
        // Direct hint match
        if (/\b(otp|one.?time|verification code|verify code|sms code|email code|code)\b/.test(hint)) return true;
        // Sibling "Get code" / "Send code" / "Resend" button = OTP field
        try {
            var parent = el.parentElement;
            for (var depth = 0; depth < 4 && parent; depth++) {
                var buttons = parent.querySelectorAll('button, a, [role="button"]');
                for (var i = 0; i < buttons.length; i++) {
                    var t = (buttons[i].textContent || '').toLowerCase();
                    if (/get\s*code|send\s*code|resend|verify|get\s*otp/.test(t)) return true;
                }
                parent = parent.parentElement;
            }
        } catch (e) {}
        return false;
    }

    // Detect country code prefix (the small <select> next to phone input)
    function isCountryCodeSelect(el) {
        if (el.tagName.toLowerCase() !== 'select') return false;
        var text = el.textContent || '';
        // If options contain many country codes like "+49", "+1", etc.
        var codeCount = (text.match(/\+\d{1,3}/g) || []).length;
        return codeCount >= 3;
    }

    // Detect day/month/year DOB select pattern
    function detectDobSelect(el) {
        if (el.tagName.toLowerCase() !== 'select') return null;
        var hint = ((el.name || '') + ' ' + (el.id || '') + ' ' + findLabelText(el)).toLowerCase();
        var options = el.options;
        if (!options || options.length < 2) return null;
        var firstText = (options[1] && options[1].textContent) || '';
        // Year-like: 4-digit numbers
        if (/^\d{4}$/.test(firstText.trim())) return 'dobYear';
        // Day-like: 1-31
        if (/^\d{1,2}$/.test(firstText.trim()) && parseInt(firstText) <= 31) {
            // Check for month names in the same container
            var parent = el.parentElement;
            if (parent) {
                var allText = parent.textContent.toLowerCase();
                if (/jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec/.test(allText)) return 'dobMonth';
                if (/month/.test(hint)) return 'dobMonth';
                if (/year/.test(hint)) return 'dobYear';
                if (/day/.test(hint)) return 'dobDay';
            }
            return 'dobDay';
        }
        // Month name
        if (/jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec/i.test(firstText)) return 'dobMonth';
        if (/month/.test(hint)) return 'dobMonth';
        if (/year/.test(hint)) return 'dobYear';
        return null;
    }

    function classifyField(el) {
        var tag = el.tagName.toLowerCase();
        var hint = ((el.name || '') + ' ' + (el.id || '') + ' ' +
                    (el.placeholder || '') + ' ' +
                    (el.getAttribute('aria-label') || '') + ' ' +
                    findLabelText(el)).toLowerCase();

        // Skip fields: hidden, read-only, disabled — handled in scan()
        // OTP / verification fields — never fill
        if (tag === 'input' && isOtpField(el, hint)) return 'skip';

        // Captcha
        if (/captcha|recaptcha/.test(hint)) return 'skip';

        // Country code select (phone prefix) — treat as skip, user keeps their own
        if (isCountryCodeSelect(el)) return 'skip';

        // DOB split selects
        if (tag === 'select') {
            var dobType = detectDobSelect(el);
            if (dobType) return dobType;
        }

        // Country dropdown — native <select>
        if (tag === 'select' && /\bcountry|region|nation\b/.test(hint)) return 'country';

        // State/Province dropdown
        if (tag === 'select' && /\bstate|province|region\b/.test(hint)) return 'state';

        // Gender dropdown
        if (tag === 'select' && /\bgender|sex\b/.test(hint)) return 'gender';

        // Any other select — try generic matching
        if (tag === 'select') {
            // Try to figure out what it's for
            if (/email|mail/.test(hint)) return 'email';
            if (/phone|mobile/.test(hint)) return 'phone';
            if (/city|town/.test(hint)) return 'city';
            return 'selectUnknown';
        }

        // Input types
        if (el.type === 'email') return 'email';
        if (el.type === 'tel') return 'phone';
        if (el.type === 'password') {
            // Confirm password?
            if (/confirm|retype|repeat|re-?enter/.test(hint)) return 'confirmPassword';
            return 'password';
        }
        if (el.type === 'date') return 'dob';

        // Confirm password (typed as text sometimes)
        if (/confirm.*password|retype.*password|repeat.*password|re-?enter.*password/.test(hint)) return 'confirmPassword';
        if (/confirm.*email/.test(hint)) return 'confirmEmail';
        if (/confirm.*phone/.test(hint)) return 'confirmPhone';

        // Regular fields
        if (/first.*name|fname|given/.test(hint)) return 'firstName';
        if (/last.*name|lname|surname|family/.test(hint)) return 'lastName';
        if (/middle.*name|mname/.test(hint)) return 'middleName';
        if (/nick|alias/.test(hint)) return 'nickname';
        if (/user.*name|username|login/.test(hint)) return 'username';
        if (/full.*name|^name$|your.*name/.test(hint)) return 'fullName';
        if (/email|mail/.test(hint)) return 'email';
        if (/password|pwd/.test(hint)) return 'password';
        if (/phone|mobile|contact|tel/.test(hint)) return 'phone';
        if (/address|street|addr/.test(hint)) return 'address';
        if (/city|town/.test(hint)) return 'city';
        if (/zip|postal|pincode/.test(hint)) return 'zip';
        if (/country/.test(hint)) return 'country';
        if (/state|province/.test(hint)) return 'state';
        if (/dob|birth|birthday/.test(hint)) return 'dob';
        if (/gender|sex/.test(hint)) return 'gender';
        return 'unknown';
    }

    function setNativeValue(el, value) {
        try {
            var proto = Object.getPrototypeOf(el);
            var descriptor = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
            if (descriptor && descriptor.set) {
                descriptor.set.call(el, value);
            } else {
                el.value = value;
            }
        } catch (e) {
            el.value = value;
        }
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        el.dispatchEvent(new Event('blur', { bubbles: true }));
    }

    // Fill a native <select> by matching option text or value
    function fillSelect(el, value) {
        if (!value) return false;
        var lv = value.toString().toLowerCase().trim();
        var options = el.options;
        if (!options || options.length === 0) return false;

        // Exact text match
        for (var i = 0; i < options.length; i++) {
            var t = (options[i].textContent || '').trim().toLowerCase();
            if (t === lv) {
                el.selectedIndex = i;
                setNativeValue(el, options[i].value);
                return true;
            }
        }
        // Exact value match
        for (var j = 0; j < options.length; j++) {
            var v = (options[j].value || '').trim().toLowerCase();
            if (v === lv) {
                el.selectedIndex = j;
                setNativeValue(el, options[j].value);
                return true;
            }
        }
        // Partial text match (prefer shortest matching option)
        var bestIdx = -1;
        var bestLen = 999;
        for (var k = 0; k < options.length; k++) {
            var tk = (options[k].textContent || '').trim().toLowerCase();
            if (tk.indexOf(lv) !== -1 && tk.length < bestLen) {
                bestIdx = k;
                bestLen = tk.length;
            }
        }
        if (bestIdx >= 0) {
            el.selectedIndex = bestIdx;
            setNativeValue(el, options[bestIdx].value);
            return true;
        }
        return false;
    }

    function scan() {
        try {
            var fields = document.querySelectorAll('input, select, textarea');
            debug("scan found " + fields.length + " elements");

            var detected = [];
            for (var i = 0; i < fields.length; i++) {
                var el = fields[i];
                if (el.type === 'hidden' || el.disabled || el.readOnly) continue;
                var rect = el.getBoundingClientRect();
                if (rect.width === 0 && rect.height === 0) continue;
                detected.push({
                    index: i,
                    type: el.type || el.tagName.toLowerCase(),
                    name: el.name || '',
                    id: el.id || '',
                    looksLike: classifyField(el)
                });
            }
            debug("visible fields: " + detected.length);
            window.AutomationBridge.onFieldsDetected(JSON.stringify(detected));
        } catch (e) {
            debug("scan error: " + e.message);
        }
    }

    window.__automationScan = scan;

    window.__automationFill = async function(payload) {
    try {
        var items = typeof payload === 'string' ? JSON.parse(payload) : payload;
        var fields = document.querySelectorAll('input, select, textarea');
        var filled = 0;
        for (var i = 0; i < items.length; i++) {
            var item = items[i];
            var el = fields[item.index];
            if (!el) continue;
            if (item.value === undefined || item.value === null) continue;

            var tag = el.tagName.toLowerCase();
            if (tag === 'select') {
                if (fillSelect(el, item.value)) filled++;
            } else {
                el.focus();
                setNativeValue(el, item.value);
                el.blur();
                filled++;
            }
            // 30-150ms delay between fields (human-like)
            await new Promise(function(r) { setTimeout(r, 30 + Math.floor(Math.random() * 120)); });
        }
        // Report back to Kotlin how many fields we actually filled
        try {
            if (window.AutomationBridge && window.AutomationBridge.onFillComplete) {
                window.AutomationBridge.onFillComplete(filled);
            }
        } catch (e) {}
    } catch (e) {
        debug("fill error: " + e.message);
    }
};

    if (document.readyState === 'complete' || document.readyState === 'interactive') {
        setTimeout(scan, 800);
    } else {
        document.addEventListener('DOMContentLoaded', function() { setTimeout(scan, 800); });
    }
})();