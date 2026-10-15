(function() {
    if (window.__automationScannerInstalled) {
        if (typeof window.__automationScan === 'function') window.__automationScan();
        return;
    }
    window.__automationScannerInstalled = true;

    // ============================================================
    // State
    // ============================================================
    var currentVault = {};
    var currentRules = [];
    var teachMode = false;
    var lastCustomTrigger = null;
    var scanTimer = null;
    var fillGeneration = 0; // bumped on each new fill to cancel stale runs

    var SKIP_TYPES = { 'otp': true, 'checkbox': true, 'radio': true, 'unknown': true };

    // HTML spec autocomplete tokens → our internal field keys
    var AUTOCOMPLETE_MAP = {
        'email': 'email', 'username': 'username', 'current-password': 'password', 'new-password': 'password',
        'name': 'fullname', 'given-name': 'firstname', 'additional-name': 'middlename', 'family-name': 'lastname',
        'nickname': 'nickname', 'organization': 'company', 'organization-title': 'jobtitle',
        'street-address': 'address', 'address-line1': 'address', 'address-line2': 'address',
        'address-level1': 'state', 'address-level2': 'city', 'postal-code': 'zip', 'country': 'country',
        'country-name': 'country', 'tel': 'phone', 'tel-national': 'phonenational', 'tel-country-code': 'countrycode',
        'bday': 'dob', 'bday-day': 'dobday', 'bday-month': 'dobmonth', 'bday-year': 'dobyear',
        'sex': 'gender', 'url': 'website'
    };

    // ============================================================
    // DDG design tokens
    // ============================================================
    var HUD_STYLE_ID = '__auto_ddg_styles';

    function ensureHudStyles() {
        if (document.getElementById(HUD_STYLE_ID)) return;
        var style = document.createElement('style');
        style.id = HUD_STYLE_ID;
        style.textContent = [
            ':root {',
            '  --auto-bg: #F7F7F7;',
            '  --auto-surface: #FFFFFF;',
            '  --auto-text: #000000;',
            '  --auto-text-2: #666666;',
            '  --auto-line: #DDDDDD;',
            '  --auto-accent: #3969EF;',
            '  --auto-accent-soft: rgba(57,105,239,0.10);',
            '}',
            '@media (prefers-color-scheme: dark) {',
            '  :root {',
            '    --auto-bg: #1C1C1E;',
            '    --auto-surface: #25282A;',
            '    --auto-text: #FFFFFF;',
            '    --auto-text-2: #AAAAAA;',
            '    --auto-line: #3A3A3C;',
            '    --auto-accent: #7295F6;',
            '    --auto-accent-soft: rgba(114,149,246,0.18);',
            '  }',
            '}',
            '#__auto_teach_banner {',
            '  position: fixed; top: 0; left: 0; right: 0; z-index: 2147483646;',
            '  background: var(--auto-surface); color: var(--auto-text);',
            '  padding: 12px 14px; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;',
            '  font-size: 13px; display: flex; justify-content: space-between; align-items: center;',
            '  border-bottom: 1px solid var(--auto-line);',
            '  box-shadow: 0 2px 8px rgba(0,0,0,0.15);',
            '}',
            '#__auto_teach_banner .__auto_dot {',
            '  display: inline-block; width: 8px; height: 8px; border-radius: 50%;',
            '  background: var(--auto-accent); margin-right: 8px; vertical-align: middle;',
            '  animation: __auto_pulse 1.4s ease-in-out infinite;',
            '}',
            '@keyframes __auto_pulse { 0%{opacity:.4} 50%{opacity:1} 100%{opacity:.4} }',
            '#__auto_teach_banner .__auto_done {',
            '  background: var(--auto-accent); color: #FFFFFF; border: none;',
            '  border-radius: 8px; padding: 6px 14px; font-weight: 500; font-size: 13px; cursor: pointer;',
            '}',
            '#__auto_picker {',
            '  position: fixed; bottom: 0; left: 0; right: 0; z-index: 2147483647;',
            '  background: var(--auto-surface); color: var(--auto-text);',
            '  border-top-left-radius: 16px; border-top-right-radius: 16px;',
            '  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;',
            '  box-shadow: 0 -8px 24px rgba(0,0,0,0.25);',
            '  max-height: 60vh; display: flex; flex-direction: column;',
            '}',
            '#__auto_picker .__auto_head { padding: 14px 16px 8px 16px; border-bottom: 1px solid var(--auto-line); display: flex; align-items: center; justify-content: space-between; }',
            '#__auto_picker .__auto_title { font-size: 15px; font-weight: 600; }',
            '#__auto_picker .__auto_sub { font-size: 11px; color: var(--auto-text-2); margin-top: 2px; }',
            '#__auto_picker .__auto_close { background: transparent; border: none; color: var(--auto-text-2); font-size: 20px; line-height: 1; cursor: pointer; padding: 4px 8px; }',
            '#__auto_picker .__auto_search { margin: 10px 16px 4px 16px; padding: 10px 12px; background: var(--auto-bg); color: var(--auto-text); border: 1px solid var(--auto-line); border-radius: 10px; font-size: 14px; outline: none; }',
            '#__auto_picker .__auto_body { overflow-y: auto; padding: 8px 16px 16px 16px; }',
            '#__auto_picker .__auto_section { font-size: 11px; font-weight: 600; color: var(--auto-text-2); text-transform: uppercase; letter-spacing: 0.5px; margin: 12px 0 6px 0; }',
            '#__auto_picker .__auto_row { display: flex; flex-wrap: wrap; gap: 8px; }',
            '#__auto_picker .__auto_chip { background: var(--auto-bg); color: var(--auto-text); border: 1px solid var(--auto-line); border-radius: 10px; padding: 9px 12px; font-size: 13px; cursor: pointer; }',
            '#__auto_picker .__auto_chip:hover { background: var(--auto-accent-soft); border-color: var(--auto-accent); }',
            '#__auto_picker .__auto_custom { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--auto-line); }',
            '#__auto_picker .__auto_custom label { display: block; font-size: 11px; color: var(--auto-text-2); margin-bottom: 4px; }',
            '#__auto_picker .__auto_custom input { width: 100%; box-sizing: border-box; padding: 10px 12px; margin-bottom: 8px; background: var(--auto-bg); color: var(--auto-text); border: 1px solid var(--auto-line); border-radius: 10px; font-size: 14px; outline: none; }',
            '#__auto_picker .__auto_custom input:focus { border-color: var(--auto-accent); }',
            '#__auto_picker .__auto_custom button { width: 100%; background: var(--auto-accent); color: #FFFFFF; border: none; border-radius: 10px; padding: 12px; font-size: 14px; font-weight: 500; cursor: pointer; }',
            '#__auto_picker .__auto_custom button:disabled { opacity: 0.5; cursor: default; }',
            '.__auto_highlight { outline: 2px solid var(--auto-accent) !important; outline-offset: 1px !important; }',
        ].join('\n');
        document.head.appendChild(style);
    }

    // ============================================================
    // Utilities
    // ============================================================
    function sleep(ms) { return new Promise(function(r) { setTimeout(r, ms); }); }
    function randInt(min, max) { return min + Math.floor(Math.random() * (max - min + 1)); }

    function toast(msg) {
        try {
            if (window.AutomationBridge && window.AutomationBridge.showToast) {
                window.AutomationBridge.showToast(msg);
            }
        } catch (e) {}
    }

    function isVisible(el) {
        if (!el || !el.getBoundingClientRect) return false;
        var style = window.getComputedStyle(el);
        if (style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity) === 0) return false;
        var rect = el.getBoundingClientRect();
        return rect.width > 2 && rect.height > 2;
    }

    // ============================================================
    // DEEP TRAVERSAL — Shadow DOM + iframes
    // ============================================================

    /**
     * Collect elements matching the selector, piercing through:
     *  - regular DOM
     *  - shadow roots (open mode only)
     *  - same-origin iframes (recursively)
     */
    function queryAllDeep(selector, root, results) {
        root = root || document;
        results = results || [];

        try {
            var found = root.querySelectorAll(selector);
            for (var i = 0; i < found.length; i++) results.push(found[i]);
        } catch (e) {}

        // Recurse into shadow roots
        try {
            var allEls = root.querySelectorAll('*');
            for (var s = 0; s < allEls.length; s++) {
                var el = allEls[s];
                if (el.shadowRoot) {
                    queryAllDeep(selector, el.shadowRoot, results);
                }
            }
        } catch (e) {}

        return results;
    }

    function collectSameOriginFrames() {
        var frames = [];
        function walk(doc, depth) {
            if (!doc || depth > 3) return;
            try {
                var iframes = doc.querySelectorAll('iframe');
                for (var i = 0; i < iframes.length; i++) {
                    try {
                        var inner = iframes[i].contentDocument;
                        if (inner) {
                            frames.push(inner);
                            walk(inner, depth + 1);
                        }
                    } catch (e) {
                        // Cross-origin iframe — silently skip
                    }
                }
            } catch (e) {}
        }
        walk(document, 0);
        return frames;
    }

    // ============================================================
    // Framework detection — decides HOW to set the value
    // ============================================================

    function detectFramework(el) {
        try {
            // React: fiber key on element or nearest ancestor
            var node = el;
            for (var d = 0; d < 3 && node; d++) {
                if (node.__reactFiber$ || node.__reactProps$ || node._reactRootContainer) return 'react';
                node = node.parentElement;
            }
        } catch (e) {}

        try {
            // Vue 2/3
            if (el.__vue__ || el._vModifiers || (window.Vue && el.__vueParentComponent)) return 'vue';
            var root = document.querySelector('#app, #root, [data-v-app]');
            if (root && root.__vue_app__) return 'vue';
        } catch (e) {}

        try {
            // Angular: class-based markers
            var cls = (typeof el.className === 'string' ? el.className : '').toLowerCase();
            if (el.getAttribute && (el.getAttribute('ng-model') || el.getAttribute('ng-reflect-model'))) return 'angular';
            if (cls.indexOf('ng-') !== -1) return 'angular';
        } catch (e) {}

        try {
            // Svelte: namespaced properties
            if (el.__svelte_meta || el.__svelte) return 'svelte';
        } catch (e) {}

        return 'plain';
    }

    function setValueFrameworkAware(el, value) {
        if (!el) return;

        // Access native value setter (bypasses framework property overrides)
        var proto = Object.getPrototypeOf(el);
        var desc = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
        var nativeSetter = desc && desc.set;
        var proto2 = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype
                  : el instanceof HTMLSelectElement ? HTMLSelectElement.prototype
                  : HTMLInputElement.prototype;
        var desc2 = Object.getOwnPropertyDescriptor(proto2, 'value');
        if (desc2 && desc2.set) nativeSetter = desc2.set;

        // Apply value
        if (nativeSetter) {
            nativeSetter.call(el, value);
        } else {
            el.value = value;
        }

        // Dispatch framework events in the right order
        dispatchInputEvents(el);
    }

    function dispatchInputEvents(el) {
        var fire = function(type, Ctor, opts) {
            try { el.dispatchEvent(new Ctor(type, opts)); } catch (e) {}
        };

        // 1. Composition (React & IME)
        fire('compositionstart', CompositionEvent, { bubbles: true, cancelable: true, data: '' });

        // 2. Input events (most frameworks listen to this)
        fire('input', InputEvent, { bubbles: true, cancelable: true, inputType: 'insertText', data: null });

        // 3. Change event
        fire('change', Event, { bubbles: true });

        // 4. Composition end
        fire('compositionend', CompositionEvent, { bubbles: true, cancelable: true, data: '' });

        // 5. Blur/focus to trigger validators
        try {
            el.dispatchEvent(new FocusEvent('blur', { bubbles: false }));
        } catch (e) {}
    }

    // ============================================================
    // Human-like typing
    // ============================================================

    async function humanType(el, value) {
        if (!el || value == null) return;

        el.focus();
        // Clear existing value first
        setValueFrameworkAware(el, '');

        var current = '';
        for (var i = 0; i < value.length; i++) {
            var ch = value[i];
            current += ch;

            try { el.dispatchEvent(new KeyboardEvent('keydown', { key: ch, bubbles: true, cancelable: true })); } catch (e) {}
            try { el.dispatchEvent(new KeyboardEvent('keypress', { key: ch, charCode: ch.charCodeAt(0), bubbles: true, cancelable: true })); } catch (e) {}

            setValueFrameworkAware(el, current);

            try { el.dispatchEvent(new KeyboardEvent('keyup', { key: ch, bubbles: true, cancelable: true })); } catch (e) {}

            // Variable delay — mimics human typing rhythm
            await sleep(randInt(35, 110));
        }

        try { el.dispatchEvent(new FocusEvent('blur', { bubbles: false })); } catch (e) {}
    }

    // ============================================================
    // Selector + label
    // ============================================================
    function computeSelector(el) {
        if (!el || el.nodeType !== 1) return '';
        if (el.id) {
            try { return '#' + CSS.escape(el.id); } catch (e) {}
        }
        var path = [];
        var cur = el;
        var depth = 0;
        while (cur && cur.nodeType === 1 && cur !== document.body && depth < 6) {
            var tag = cur.tagName.toLowerCase();
            if (cur.id) {
                try { path.unshift(tag + '#' + CSS.escape(cur.id)); break; } catch (e) {}
            }
            var name = cur.getAttribute && cur.getAttribute('name');
            if (name) {
                path.unshift(tag + '[name="' + name.replace(/"/g, '\\"') + '"]');
                break;
            }
            var parent = cur.parentElement;
            if (parent) {
                var siblings = Array.prototype.filter.call(parent.children, function(c) { return c.tagName === cur.tagName; });
                if (siblings.length > 1) {
                    tag += ':nth-of-type(' + (siblings.indexOf(cur) + 1) + ')';
                }
            }
            path.unshift(tag);
            cur = parent;
            depth++;
        }
        return path.join(' > ');
    }

    function findVisualLabel(el) {
        if (!el) return '';
        var aria = el.getAttribute && el.getAttribute('aria-label');
        if (aria) return aria.trim();

        var ariaBy = el.getAttribute && el.getAttribute('aria-labelledby');
        if (ariaBy) {
            var refEl = document.getElementById(ariaBy);
            if (refEl) return refEl.textContent.trim();
        }

        if (el.id) {
            try {
                var lbl = document.querySelector('label[for="' + CSS.escape(el.id) + '"]');
                if (lbl) return lbl.textContent.trim();
            } catch (e) {}
        }

        var parentLbl = el.closest && el.closest('label');
        if (parentLbl) return parentLbl.textContent.trim();

        // Closest preceding sibling label
        var prev = el.previousElementSibling;
        for (var i = 0; i < 3 && prev; i++) {
            if (prev.tagName === 'LABEL' || prev.tagName === 'SPAN' || prev.tagName === 'DIV') {
                var t = prev.textContent.trim();
                if (t.length > 0 && t.length < 60) return t;
            }
            prev = prev.previousElementSibling;
        }

        var container = el.closest && el.closest('div, td, li, fieldset, section, form');
        if (container) {
            var childLbl = container.querySelector('label, .label, .form-label, legend, span');
            if (childLbl && childLbl !== el && childLbl.textContent.length < 60) {
                return childLbl.textContent.trim();
            }
        }

        return (el.placeholder || el.title || el.getAttribute('name') || '').trim();
    }

    // ============================================================
    // Control collection
    // ============================================================
    function collectInteractiveControls() {
        var raw = queryAllDeep(
            'input, select, textarea, [role="combobox"], [role="checkbox"], [role="radio"], .iti__selected-flag, .iti__flag-container'
        );

        var list = [];
        for (var i = 0; i < raw.length; i++) {
            var el = raw[i];
            if (el.type === 'hidden' || el.type === 'submit' || el.type === 'button' || el.disabled || el.readOnly) continue;
            if (!isVisible(el)) continue;
            list.push(el);
        }

        // Same-origin iframes
        var frames = collectSameOriginFrames();
        for (var f = 0; f < frames.length; f++) {
            try {
                var doc = frames[f];
                var frameEls = doc.querySelectorAll('input, select, textarea, [role="combobox"], [role="checkbox"], [role="radio"]');
                for (var j = 0; j < frameEls.length; j++) {
                    var fe = frameEls[j];
                    if (fe.type === 'hidden' || fe.disabled || fe.readOnly) continue;
                    if (!isVisible(fe)) continue;
                    list.push(fe);
                }
            } catch (e) {}
        }

        return list;
    }

    function isCustomDropdownTrigger(el) {
        if (!el || el.tagName === 'SELECT' || el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') return false;
        var role = (el.getAttribute('role') || '').toLowerCase();
        var hasPopup = el.getAttribute('aria-haspopup');
        var cls = (typeof el.className === 'string' ? el.className : '').toLowerCase();
        if (role === 'combobox' || role === 'listbox' || hasPopup === 'listbox' || hasPopup === 'true') return true;
        if (/iti__selected-flag|iti__flag-container|country-select|phone-code|select-trigger|ant-select|mui-select|custom-select/.test(cls)) return true;
        return false;
    }

    // ============================================================
    // Classification — with HTML autocomplete as ground truth
    // ============================================================
    function classifyElement(el, allControls, idx) {
        var tag = el.tagName.toLowerCase();
        var type = (el.type || '').toLowerCase();

        // 1. HTML autocomplete attribute — highest priority
        var auto = (el.getAttribute('autocomplete') || '').toLowerCase().trim();
        if (auto && auto !== 'off' && auto !== 'on') {
            var token = auto.split(/\s+/).pop();
            if (AUTOCOMPLETE_MAP[token]) return AUTOCOMPLETE_MAP[token];
        }

        var hint = (
            (el.name || '') + ' ' + (el.id || '') + ' ' + (el.placeholder || '') + ' ' +
            (typeof el.className === 'string' ? el.className : '') + ' ' +
            findVisualLabel(el)
        ).toLowerCase();

        if (type === 'checkbox' || el.getAttribute('role') === 'checkbox') return 'checkbox';
        if (type === 'radio' || el.getAttribute('role') === 'radio') {
            if (/male|female|gender|sex/.test(hint)) return 'gender';
            return 'radio';
        }
        if (/otp|one.?time|verification.*code|auth.*code|totp|sms.*code|email.*code|captcha/.test(hint)) return 'otp';
        if (/country.*code|dial.*code|phone.*code|isd|iti__|selected-flag|calling.*code|ccode|prefix/.test(hint)) return 'countrycode';

        if ((tag === 'select' || isCustomDropdownTrigger(el)) && idx + 1 < allControls.length) {
            var nextEl = allControls[idx + 1];
            var nextHint = ((nextEl.name || '') + ' ' + (nextEl.id || '') + ' ' + (nextEl.type || '') + ' ' + (nextEl.placeholder || '')).toLowerCase();
            if (nextEl.type === 'tel' || /phone|mobile|contact|tel/.test(nextHint)) {
                var txt = (el.textContent || el.value || '').trim();
                if (/^\+?\d{1,4}/.test(txt) || /country|code|flag|\+\d{1,3}/.test(hint + ' ' + txt.toLowerCase())) return 'countrycode';
            }
        }
        if (tag === 'input' && el.maxLength >= 2 && el.maxLength <= 4 && idx + 1 < allControls.length) {
            var ni = allControls[idx + 1];
            if (ni && (ni.type === 'tel' || /phone|mobile|tel/.test((ni.name || '') + (ni.placeholder || '')))) return 'countrycode';
        }
        if (type === 'tel' || /phone|mobile|contact|whatsapp|cell|tel/.test(hint)) {
            var prevEl = idx > 0 ? allControls[idx - 1] : null;
            var prevLooksLike = prevEl ? prevEl.getAttribute('data-auto-type') : '';
            if (prevLooksLike === 'countrycode' || el.maxLength === 10 || /10\s*digit/.test(hint)) return 'phonenational';
            if (/\+|international|country\s*code/.test(el.placeholder || '')) return 'phonee164';
            return 'phonenational';
        }
        if (type === 'email' || /email|e-mail|mail/.test(hint)) return 'email';
        if (type === 'password' || /password|passcode|pwd|pin/.test(hint)) {
            if (/confirm|retype|repeat|re-?enter/.test(hint)) return 'password';
            return 'password';
        }
        if (/first.*name|fname|given.*name|forename/.test(hint)) return 'firstname';
        if (/last.*name|lname|surname|family.*name/.test(hint)) return 'lastname';
        if (/middle.*name|mname/.test(hint)) return 'middlename';
        if (/user.*name|userid|login.*id|handle/.test(hint)) return 'username';
        if (/full.*name|your.*name|applicant.*name|\bname\b/.test(hint)) return 'fullname';
        if (/address|street|addr|flat|house|line1/.test(hint)) return 'address';
        if (/city|town|district|municipality/.test(hint)) return 'city';
        if (/state|province|region/.test(hint)) return 'state';
        if (/zip|postal|pincode|postcode/.test(hint)) return 'zip';
        if (/country|nation/.test(hint)) return 'country';
        if (type === 'date' || /dob|birth|birthday|birthdate/.test(hint)) return 'dob';
        if (/gender|sex/.test(hint)) return 'gender';
        return 'unknown';
    }

    // ============================================================
    // Action execution
    // ============================================================
    function dispatchRealClick(el) {
        if (!el) return;
        var opts = { bubbles: true, cancelable: true, view: window };
        try { el.dispatchEvent(new PointerEvent('pointerdown', opts)); } catch (e) {}
        try { el.dispatchEvent(new MouseEvent('mousedown', opts)); } catch (e) {}
        try { el.dispatchEvent(new PointerEvent('pointerup', opts)); } catch (e) {}
        try { el.dispatchEvent(new MouseEvent('mouseup', opts)); } catch (e) {}
        try { el.click(); } catch (e) {}
    }

    function setCheckboxState(el, targetChecked) {
        var isChecked = el.checked || el.getAttribute('aria-checked') === 'true';
        if (isChecked !== targetChecked) {
            var clickable = (el.id && document.querySelector('label[for="' + CSS.escape(el.id) + '"]')) ||
                            (el.closest && el.closest('label')) || el;
            dispatchRealClick(clickable);
        }
    }

    function selectRadioOption(el, targetValue) {
        var groupName = el.name;
        var radios = groupName
            ? document.querySelectorAll('input[type="radio"][name="' + groupName.replace(/"/g, '\\"') + '"]')
            : [el];
        var needle = (targetValue || '').toLowerCase().trim();
        for (var i = 0; i < radios.length; i++) {
            var r = radios[i];
            var lbl = (findVisualLabel(r) + ' ' + (r.value || '')).toLowerCase();
            if (!needle || lbl.indexOf(needle) !== -1 || needle.indexOf((r.value || '').toLowerCase()) !== -1) {
                var clickTarget = (r.id && document.querySelector('label[for="' + CSS.escape(r.id) + '"]')) ||
                                  (r.closest && r.closest('label')) || r;
                dispatchRealClick(clickTarget);
                return;
            }
        }
    }

    function selectNativeDropdown(selectEl, candidates) {
        if (!selectEl || !selectEl.options) return false;
        var normalized = candidates
            .filter(function(c) { return c && String(c).trim().length > 0; })
            .map(function(c) { return String(c).toLowerCase().trim(); });

        var bestIdx = -1;
        for (var cIdx = 0; cIdx < normalized.length; cIdx++) {
            var needle = normalized[cIdx];
            var needleDigits = needle.replace(/\D/g, '');
            for (var i = 0; i < selectEl.options.length; i++) {
                var opt = selectEl.options[i];
                var val = (opt.value || '').toLowerCase().trim();
                var txt = (opt.textContent || '').toLowerCase().trim();
                var dataCode = (opt.getAttribute('data-countryCode') || opt.getAttribute('data-dial-code') || '').toLowerCase();
                if (val === needle || txt === needle || dataCode === needle) { bestIdx = i; break; }
                if (needleDigits && (val.replace(/\D/g, '') === needleDigits || txt.indexOf('+' + needleDigits) !== -1 || txt.indexOf('(' + needleDigits + ')') !== -1)) { bestIdx = i; break; }
                if (needle.length >= 3 && txt.indexOf(needle) !== -1) { bestIdx = i; break; }
            }
            if (bestIdx !== -1) break;
        }
        if (bestIdx !== -1) {
            selectEl.selectedIndex = bestIdx;
            setValueFrameworkAware(selectEl, selectEl.options[bestIdx].value);
            return true;
        }
        return false;
    }

    async function selectCustomDropdown(triggerEl, candidates) {
        if (!triggerEl) return false;
        dispatchRealClick(triggerEl);
        await sleep(260);

        var normalized = candidates
            .filter(function(c) { return c && String(c).trim().length > 0; })
            .map(function(c) { return String(c).toLowerCase().trim(); });

        var searchBox = document.querySelector('.iti__search-input, .select2-search__field, input[role="combobox"][aria-expanded="true"], .ant-select-selection-search-input');
        if (searchBox && isVisible(searchBox) && normalized.length > 0) {
            setValueFrameworkAware(searchBox, normalized[0]);
            await sleep(180);
        }

        var optionNodes = document.querySelectorAll('[role="option"], .iti__country, li.select2-results__option, .ant-select-item-option, .MuiMenuItem-root, .dropdown-item, ul li');
        for (var cIdx = 0; cIdx < normalized.length; cIdx++) {
            var needle = normalized[cIdx];
            var needleDigits = needle.replace(/\D/g, '');
            for (var i = 0; i < optionNodes.length; i++) {
                var node = optionNodes[i];
                if (!isVisible(node)) continue;
                var txt = (node.textContent || '').toLowerCase().trim();
                var dialAttr = (node.getAttribute('data-dial-code') || node.getAttribute('data-country-code') || '').toLowerCase();
                if (dialAttr === needle || dialAttr === needleDigits || txt === needle) { dispatchRealClick(node); return true; }
                if (needleDigits && (txt.indexOf('+' + needleDigits) !== -1 || txt.indexOf('(' + needleDigits + ')') !== -1)) { dispatchRealClick(node); return true; }
                if (needle.length >= 3 && txt.indexOf(needle) !== -1) { dispatchRealClick(node); return true; }
            }
        }
        try { if (searchBox) searchBox.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, bubbles: true })); } catch (e) {}
        return false;
    }

    function resolveCandidatesForKey(key, vault) {
        var k = (key || '').toLowerCase();
        if (k === 'countrycode') return [vault.countrycode, vault.countrycodedigits, vault.country, vault.countryiso2, vault.countryiso3];
        if (k === 'country') return [vault.country, vault.countryiso2, vault.countryiso3, vault.countrycode];
        if (k === 'phonenational') return [vault.phonenational, vault.phone];
        if (k === 'phonee164') return [vault.phonee164, vault.phone, vault.phonenational];
        return [vault[k], vault.text, vault.fallback];
    }

    async function executeElementAction(el, targetKey, strategy, fixedValue, vault) {
        if (!el) return false;
        var tag = el.tagName.toLowerCase();
        var type = (el.type || '').toLowerCase();
        var candidates = fixedValue ? [fixedValue] : resolveCandidatesForKey(targetKey, vault);
        var primaryValue = (candidates.filter(Boolean)[0]) || '';

        if (targetKey === 'click_advance' || strategy === 'CLICK_NEXT') { dispatchRealClick(el); return true; }
        if (type === 'checkbox' || el.getAttribute('role') === 'checkbox' || strategy === 'CLICK_CHECK') {
            setCheckboxState(el, fixedValue ? fixedValue === 'true' : true);
            return true;
        }
        if (type === 'radio' || el.getAttribute('role') === 'radio') { selectRadioOption(el, primaryValue); return true; }
        if (tag === 'select') return selectNativeDropdown(el, candidates);
        if (isCustomDropdownTrigger(el) || strategy === 'CUSTOM_DROPDOWN') return await selectCustomDropdown(el, candidates);
        if (!primaryValue) return false;

        if ((targetKey.indexOf('phone') !== -1 || type === 'tel') && el.maxLength === 10 && primaryValue.length > 10) {
            primaryValue = primaryValue.replace(/\D/g, '').slice(-10);
        }
        if (targetKey === 'countrycode' && (type === 'number' || el.maxLength === 2 || el.maxLength === 3)) {
            primaryValue = primaryValue.replace(/\D/g, '');
        }

        var framework = detectFramework(el);
        if (framework === 'react' || framework === 'vue' || framework === 'angular' || framework === 'svelte') {
            await humanType(el, primaryValue);
        } else if (strategy === 'KEYSTROKE_STREAM') {
            await humanType(el, primaryValue);
        } else {
            setValueFrameworkAware(el, primaryValue);
        }
        return true;
    }

    function findElementByRule(rule) {
        if (rule.selector) {
            try { var bySel = document.querySelector(rule.selector); if (bySel && isVisible(bySel)) return bySel; } catch (e) {}
            try { var deep = queryAllDeep(rule.selector); if (deep[0] && isVisible(deep[0])) return deep[0]; } catch (e) {}
        }
        if (rule.idAttr) {
            var byId = document.getElementById(rule.idAttr);
            if (byId && isVisible(byId)) return byId;
        }
        if (rule.nameAttr) {
            try { var byName = document.querySelector('[name="' + rule.nameAttr.replace(/"/g, '\\"') + '"]'); if (byName && isVisible(byName)) return byName; } catch (e) {}
        }
        return null;
    }

    // ============================================================
    // Main execute — multi-pass with retries
    // ============================================================
    window.__automationExecute = async function(vaultJson, rulesJson) {
        var myGeneration = ++fillGeneration;
        var filledCount = 0;

        try {
            currentVault = typeof vaultJson === 'string' ? JSON.parse(vaultJson) : (vaultJson || {});
            currentRules = typeof rulesJson === 'string' ? JSON.parse(rulesJson) : (rulesJson || []);
            var handledElements = [];

            // Pass 1: apply learned rules
            for (var r = 0; r < currentRules.length; r++) {
                if (myGeneration !== fillGeneration) return;
                var rule = currentRules[r];
                var el = findElementByRule(rule);
                if (el) {
                    handledElements.push(el);
                    if (await executeElementAction(el, rule.targetKey, rule.strategy, rule.fixedValue, currentVault)) filledCount++;
                    await sleep(randInt(90, 160));
                }
            }

            // Pass 2: country codes first
            var controls = collectInteractiveControls();
            for (var i = 0; i < controls.length; i++) {
                if (myGeneration !== fillGeneration) return;
                var ctrl = controls[i];
                if (handledElements.indexOf(ctrl) !== -1) continue;
                var kind = classifyElement(ctrl, controls, i);
                if (kind === 'countrycode') {
                    handledElements.push(ctrl);
                    if (await executeElementAction(ctrl, 'countrycode', 'NATIVE_SETTER', '', currentVault)) filledCount++;
                    await sleep(randInt(120, 220));
                }
            }

            // Pass 3: everything else
            for (var j = 0; j < controls.length; j++) {
                if (myGeneration !== fillGeneration) return;
                var itemEl = controls[j];
                if (handledElements.indexOf(itemEl) !== -1) continue;
                var detectedType = classifyElement(itemEl, controls, j);
                if (SKIP_TYPES[detectedType]) continue;
                if (await executeElementAction(itemEl, detectedType, 'NATIVE_SETTER', '', currentVault)) filledCount++;
                await sleep(randInt(50, 120));
            }

            // Pass 4: retry for late-rendered fields (SPAs)
            await sleep(800);
            if (myGeneration === fillGeneration) {
                var lateControls = collectInteractiveControls();
                for (var k = 0; k < lateControls.length; k++) {
                    if (myGeneration !== fillGeneration) return;
                    var lateEl = lateControls[k];
                    if (handledElements.indexOf(lateEl) !== -1) continue;
                    var isEmpty = (lateEl.value || '').trim() === '';
                    if (!isEmpty) continue;
                    var lateType = classifyElement(lateEl, lateControls, k);
                    if (SKIP_TYPES[lateType]) continue;
                    if (await executeElementAction(lateEl, lateType, 'NATIVE_SETTER', '', currentVault)) filledCount++;
                    await sleep(randInt(50, 100));
                }
            }
        } catch (e) {
            toast('Automation error: ' + e.message);
        } finally {
            if (myGeneration === fillGeneration) {
                try {
                    if (window.AutomationBridge && window.AutomationBridge.onFillComplete) {
                        window.AutomationBridge.onFillComplete(filledCount);
                    }
                } catch (e) {}
            }
        }
    };

    window.__automationConfigure = function(vaultJson, rulesJson) {
        try {
            currentVault = typeof vaultJson === 'string' ? JSON.parse(vaultJson) : (vaultJson || {});
            currentRules = typeof rulesJson === 'string' ? JSON.parse(rulesJson) : (rulesJson || []);
            scan();
        } catch (e) {}
    };

    // ============================================================
    // Passive learning
    // ============================================================
    function matchValueAgainstVault(typedVal) {
        if (!typedVal || typedVal.length < 2) return null;
        var cleanTyped = typedVal.trim().toLowerCase();
        var keys = ['email','phonenational','phonee164','countrycode','firstname','lastname','fullname','username','address','city','state','zip','country','dob'];
        for (var i = 0; i < keys.length; i++) {
            var v = (currentVault[keys[i]] || '').trim().toLowerCase();
            if (v && v === cleanTyped) return keys[i];
        }
        return null;
    }

    document.addEventListener('change', function(e) {
        if (teachMode) return;
        var el = e.target;
        if (!el || !el.tagName) return;
        var tag = el.tagName.toLowerCase();
        var type = (el.type || '').toLowerCase();
        if (type === 'checkbox') { emitLearnedRule(el, 'checkbox', 'checkbox', 'CLICK_CHECK', String(!!el.checked)); return; }
        if (tag === 'select') {
            var selectedTxt = el.options && el.options[el.selectedIndex] ? el.options[el.selectedIndex].text : el.value;
            var matched = matchValueAgainstVault(el.value) || matchValueAgainstVault(selectedTxt);
            emitLearnedRule(el, 'select', matched || 'select_fixed', 'SELECT_OPTION', matched ? '' : el.value);
            return;
        }
        var matchedKey = matchValueAgainstVault(el.value);
        if (matchedKey) emitLearnedRule(el, 'text', matchedKey, 'NATIVE_SETTER', '');
    }, true);

    document.addEventListener('click', function(e) {
        if (teachMode) return;
        var target = e.target.closest ? e.target.closest('[role="combobox"], .iti__selected-flag, .iti__flag-container, [role="option"], .iti__country') : null;
        if (!target) return;
        if (isCustomDropdownTrigger(target)) lastCustomTrigger = target;
        else if (lastCustomTrigger && (target.getAttribute('role') === 'option' || target.classList.contains('iti__country'))) {
            var txt = (target.textContent || '').trim();
            var isCountry = /\+\d{1,3}/.test(txt);
            emitLearnedRule(lastCustomTrigger, 'custom_dropdown', isCountry ? 'countrycode' : 'custom_fixed', 'CUSTOM_DROPDOWN', isCountry ? '' : txt);
            lastCustomTrigger = null;
        }
    }, true);

    function emitLearnedRule(el, controlType, targetKey, strategy, fixedValue) {
        if (!window.AutomationBridge || !window.AutomationBridge.onRuleLearned) return;
        try {
            window.AutomationBridge.onRuleLearned(JSON.stringify({
                domain: window.location.hostname.replace(/^www\./, ''),
                selector: computeSelector(el),
                nameAttr: el.name || '',
                idAttr: el.id || '',
                visualLabel: findVisualLabel(el),
                controlType: controlType,
                targetKey: targetKey,
                strategy: strategy,
                fixedValue: fixedValue || ''
            }));
        } catch (e) {}
    }

    // ============================================================
    // Teach mode (same as before)
    // ============================================================
    var hudBanner = null;
    var hudPicker = null;
    var highlightedEl = null;

    window.__automationSetTeachMode = function(active, vaultJson) {
        teachMode = !!active;
        if (vaultJson) { try { currentVault = typeof vaultJson === 'string' ? JSON.parse(vaultJson) : vaultJson; } catch (e) {} }
        if (teachMode) { ensureHudStyles(); showTeachBanner(); toast('Teach mode on'); }
        else { removeTeachUi(); toast('Teach mode off'); }
    };

    function showTeachBanner() {
        removeTeachBanner();
        hudBanner = document.createElement('div');
        hudBanner.id = '__auto_teach_banner';
        hudBanner.innerHTML = '<div><span class="__auto_dot"></span>Teach mode · tap any field to bind</div><button class="__auto_done" id="__auto_teach_close">Done</button>';
        document.body.appendChild(hudBanner);
        document.getElementById('__auto_teach_close').onclick = function(ev) { ev.stopPropagation(); window.__automationSetTeachMode(false); };
    }

    function removeTeachBanner() { if (hudBanner && hudBanner.parentNode) hudBanner.parentNode.removeChild(hudBanner); hudBanner = null; }
    function removeTeachPicker() { if (hudPicker && hudPicker.parentNode) hudPicker.parentNode.removeChild(hudPicker); hudPicker = null; }
    function removeTeachUi() {
        removeTeachBanner();
        removeTeachPicker();
        if (highlightedEl) { highlightedEl.classList.remove('__auto_highlight'); highlightedEl = null; }
    }

    document.addEventListener('click', function(e) {
        if (!teachMode) return;
        if (hudBanner && hudBanner.contains(e.target)) return;
        if (hudPicker && hudPicker.contains(e.target)) return;
        e.preventDefault(); e.stopPropagation();
        if (highlightedEl) highlightedEl.classList.remove('__auto_highlight');
        var el = e.target.closest('input, select, textarea, button, [role="combobox"], [role="checkbox"], [role="radio"], .iti__selected-flag, a') || e.target;
        highlightedEl = el;
        el.classList.add('__auto_highlight');
        openTeachPicker(el);
    }, true);

    var FIELD_GROUPS = [
        { title: 'Contact', items: [
            { label: 'Email', key: 'email', strategy: 'NATIVE_SETTER' },
            { label: 'Phone (local)', key: 'phonenational', strategy: 'NATIVE_SETTER' },
            { label: 'Phone (E.164)', key: 'phonee164', strategy: 'NATIVE_SETTER' },
            { label: 'Country code', key: 'countrycode', strategy: 'SELECT_OPTION', customDropdown: 'CUSTOM_DROPDOWN' },
            { label: 'Country', key: 'country', strategy: 'SELECT_OPTION', customDropdown: 'CUSTOM_DROPDOWN' },
        ]},
        { title: 'Identity', items: [
            { label: 'Full name', key: 'fullname', strategy: 'NATIVE_SETTER' },
            { label: 'First name', key: 'firstname', strategy: 'NATIVE_SETTER' },
            { label: 'Middle name', key: 'middlename', strategy: 'NATIVE_SETTER' },
            { label: 'Last name', key: 'lastname', strategy: 'NATIVE_SETTER' },
            { label: 'Username', key: 'username', strategy: 'NATIVE_SETTER' },
            { label: 'Nickname', key: 'nickname', strategy: 'NATIVE_SETTER' },
            { label: 'Date of birth', key: 'dob', strategy: 'NATIVE_SETTER' },
            { label: 'Day (DOB)', key: 'dobday', strategy: 'SELECT_OPTION' },
            { label: 'Month (DOB)', key: 'dobmonth', strategy: 'SELECT_OPTION' },
            { label: 'Year (DOB)', key: 'dobyear', strategy: 'SELECT_OPTION' },
            { label: 'Gender', key: 'gender', strategy: 'SELECT_OPTION', customDropdown: 'CUSTOM_DROPDOWN' },
        ]},
        { title: 'Address', items: [
            { label: 'Address', key: 'address', strategy: 'NATIVE_SETTER' },
            { label: 'City', key: 'city', strategy: 'NATIVE_SETTER' },
            { label: 'State', key: 'state', strategy: 'NATIVE_SETTER' },
            { label: 'ZIP / Postal', key: 'zip', strategy: 'NATIVE_SETTER' },
        ]},
        { title: 'Security', items: [
            { label: 'Password', key: 'password', strategy: 'NATIVE_SETTER' },
            { label: 'Confirm password', key: 'confirmpassword', strategy: 'NATIVE_SETTER' },
        ]},
        { title: 'Work', items: [
            { label: 'Company', key: 'company', strategy: 'NATIVE_SETTER' },
            { label: 'Job title', key: 'jobtitle', strategy: 'NATIVE_SETTER' },
        ]},
        { title: 'Actions', items: [
            { label: 'Checkbox (check)', key: 'checkbox', strategy: 'CLICK_CHECK', fixed: 'true' },
            { label: 'Checkbox (uncheck)', key: 'checkbox', strategy: 'CLICK_CHECK', fixed: 'false' },
            { label: 'Click button', key: 'click_advance', strategy: 'CLICK_NEXT' },
            { label: 'Type slowly (keystrokes)', key: 'phonenational', strategy: 'KEYSTROKE_STREAM' },
        ]},
    ];

    function openTeachPicker(el) {
        ensureHudStyles();
        removeTeachPicker();
        var isCustomDrop = isCustomDropdownTrigger(el);
        var tagName = el.tagName ? el.tagName.toLowerCase() : 'element';

        hudPicker = document.createElement('div');
        hudPicker.id = '__auto_picker';

        var header = document.createElement('div');
        header.className = '__auto_head';
        header.innerHTML =
            '<div><div class="__auto_title">Bind field</div>' +
            '<div class="__auto_sub">Selected: ' + tagName + (el.id ? '#' + el.id : '') + '</div></div>' +
            '<button class="__auto_close" id="__auto_picker_close">×</button>';
        hudPicker.appendChild(header);

        var search = document.createElement('input');
        search.className = '__auto_search';
        search.placeholder = 'Search fields…';
        hudPicker.appendChild(search);

        var body = document.createElement('div');
        body.className = '__auto_body';
        hudPicker.appendChild(body);

        var customSection = document.createElement('div');
        customSection.className = '__auto_custom';
        customSection.innerHTML =
            '<div class="__auto_section">Custom binding</div>' +
            '<label>Field key</label>' +
            '<input id="__auto_custom_key" placeholder="e.g. email, otp" />' +
            '<label>Fixed value (optional)</label>' +
            '<input id="__auto_custom_value" placeholder="e.g. true or India" />' +
            '<button id="__auto_custom_save" disabled>Save custom binding</button>';
        body.appendChild(customSection);

        renderFieldGroups(body, search, el, isCustomDrop);

        search.addEventListener('input', function() { renderFieldGroups(body, search, el, isCustomDrop); });

        var keyInput = customSection.querySelector('#__auto_custom_key');
        var valInput = customSection.querySelector('#__auto_custom_value');
        var saveBtn = customSection.querySelector('#__auto_custom_save');
        function updateCustomBtn() { saveBtn.disabled = !keyInput.value.trim(); }
        keyInput.addEventListener('input', updateCustomBtn);
        valInput.addEventListener('input', updateCustomBtn);

        saveBtn.addEventListener('click', function(ev) {
            ev.stopPropagation();
            var key = keyInput.value.trim().toLowerCase().replace(/\s+/g, '');
            var val = valInput.value.trim();
            if (!key) return;
            var strategy = 'NATIVE_SETTER';
            if ((el.type || '').toLowerCase() === 'checkbox') strategy = 'CLICK_CHECK';
            else if (el.tagName && el.tagName.toLowerCase() === 'select') strategy = 'SELECT_OPTION';
            else if (isCustomDropdownTrigger(el)) strategy = 'CUSTOM_DROPDOWN';
            emitLearnedRule(el, tagName, key, strategy, val);
            toast('Bound as ' + key);
            removeTeachPicker();
            if (highlightedEl) { highlightedEl.classList.remove('__auto_highlight'); highlightedEl = null; }
        });

        document.getElementById('__auto_picker_close').onclick = function(ev) {
            ev.stopPropagation();
            removeTeachPicker();
            if (highlightedEl) { highlightedEl.classList.remove('__auto_highlight'); highlightedEl = null; }
        };

        document.body.appendChild(hudPicker);
    }

    function renderFieldGroups(body, searchInput, el, isCustomDrop) {
        var existing = body.querySelector('.__auto_groups');
        if (existing) existing.parentNode.removeChild(existing);

        var wrap = document.createElement('div');
        wrap.className = '__auto_groups';

        var query = (searchInput.value || '').trim().toLowerCase();

        FIELD_GROUPS.forEach(function(group) {
            var matching = group.items.filter(function(item) {
                if (!query) return true;
                return item.label.toLowerCase().indexOf(query) !== -1 || item.key.toLowerCase().indexOf(query) !== -1;
            });
            if (matching.length === 0) return;

            var section = document.createElement('div');
            section.className = '__auto_section';
            section.textContent = group.title;
            wrap.appendChild(section);

            var row = document.createElement('div');
            row.className = '__auto_row';

            matching.forEach(function(item) {
                var chip = document.createElement('button');
                chip.className = '__auto_chip';
                chip.textContent = item.label;
                chip.addEventListener('click', function(ev) {
                    ev.stopPropagation();
                    var strategy = item.strategy;
                    if (item.customDropdown && isCustomDrop) strategy = item.customDropdown;
                    emitLearnedRule(el, el.tagName ? el.tagName.toLowerCase() : 'input', item.key, strategy, item.fixed || '');
                    toast('Bound as ' + item.key);
                    removeTeachPicker();
                    if (highlightedEl) { highlightedEl.classList.remove('__auto_highlight'); highlightedEl = null; }
                });
                row.appendChild(chip);
            });

            wrap.appendChild(row);
        });

        var custom = body.querySelector('.__auto_custom');
        body.insertBefore(wrap, custom);
    }

    window.addEventListener('beforeunload', removeTeachUi);
    window.addEventListener('pagehide', removeTeachUi);

    // ============================================================
    // MutationObserver + scan
    // ============================================================
    var observer = new MutationObserver(function() {
        if (scanTimer) clearTimeout(scanTimer);
        scanTimer = setTimeout(scan, 500);
    });
    if (document.body) observer.observe(document.body, { childList: true, subtree: true });

    window.__automationScan = scan;

    function scan() {
        try {
            var controls = collectInteractiveControls();
            var detected = [];
            for (var i = 0; i < controls.length; i++) {
                var el = controls[i];
                var looksLike = classifyElement(el, controls, i);
                el.setAttribute('data-auto-type', looksLike);
                detected.push({
                    index: i,
                    selector: computeSelector(el),
                    type: el.type || el.tagName.toLowerCase(),
                    name: el.name || '',
                    id: el.id || '',
                    visualLabel: findVisualLabel(el),
                    looksLike: looksLike
                });
            }
            if (window.AutomationBridge && window.AutomationBridge.onFieldsDetected) {
                window.AutomationBridge.onFieldsDetected(JSON.stringify(detected));
            }
        } catch (e) {}
    }

    if (document.readyState === 'complete' || document.readyState === 'interactive') {
        setTimeout(scan, 600);
    } else {
        document.addEventListener('DOMContentLoaded', function() { setTimeout(scan, 600); });
    }
})();