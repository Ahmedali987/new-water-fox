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

    function classifyField(el) {
        var hint = ((el.name || '') + ' ' + (el.id || '') + ' ' +
                    (el.placeholder || '') + ' ' +
                    (el.getAttribute('aria-label') || '') + ' ' +
                    findLabelText(el)).toLowerCase();
        if (el.type === 'email') return 'email';
        if (el.type === 'tel') return 'phone';
        if (el.type === 'password') return 'password';
        if (el.type === 'date') return 'dob';
        if (/email|mail/.test(hint)) return 'email';
        if (/phone|mobile|contact|tel/.test(hint)) return 'phone';
        if (/first.*name|fname|given/.test(hint)) return 'firstName';
        if (/last.*name|lname|surname|family/.test(hint)) return 'lastName';
        if (/user.*name|username|login/.test(hint)) return 'username';
        if (/full.*name|^name$|your.*name/.test(hint)) return 'fullName';
        if (/password|pwd/.test(hint)) return 'password';
        if (/address|street|addr/.test(hint)) return 'address';
        if (/city|town/.test(hint)) return 'city';
        if (/zip|postal|pincode/.test(hint)) return 'zip';
        if (/country/.test(hint)) return 'country';
        if (/dob|birth/.test(hint)) return 'dob';
        return 'unknown';
    }

    function setNativeValue(el, value) {
        var proto = Object.getPrototypeOf(el);
        var descriptor = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
        if (descriptor && descriptor.set) {
            descriptor.set.call(el, value);
        } else {
            el.value = value;
        }
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        el.dispatchEvent(new Event('blur', { bubbles: true }));
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
            for (var i = 0; i < items.length; i++) {
                var item = items[i];
                var el = fields[item.index];
                if (!el) continue;
                el.focus();
                setNativeValue(el, item.value);
            }
            debug("filled " + items.length + " fields");
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
