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

    var SKIP_TYPES = { 'otp': true, 'checkbox': true, 'radio': true, 'unknown': true };

    // ============================================================
    // Utilities
    // ============================================================
    function sleep(ms) {
        return new Promise(function(resolve) { setTimeout(resolve, ms); });
    }

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
    // Selector builder — for persisting learned rules
    // ============================================================
    function computeSelector(el) {
        if (!el || el.nodeType !== 1) return '';
        if (el.id) {
            try { return '#' + CSS.escape(el.id); } catch (e) {}
        }
        var path = [];
        var cur = el;
        while (cur && cur.nodeType === 1 && cur !== document.body && path.length < 5) {
            var tag = cur.tagName.toLowerCase();
            if (cur.id) {
                try { path.unshift(tag + '#' + CSS.escape(cur.id)); break; } catch (e) {}
            }
            var name = cur.getAttribute('name');
            if (name) {
                path.unshift(tag + '[name="' + name.replace(/"/g, '\\"') + '"]');
                break;
            }
            var parent = cur.parentElement;
            if (parent) {
                var siblings = Array.prototype.filter.call(parent.children, function(c) {
                    return c.tagName === cur.tagName;
                });
                if (siblings.length > 1) {
                    var idx = siblings.indexOf(cur) + 1;
                    tag += ':nth-of-type(' + idx + ')';
                }
            }
            path.unshift(tag);
            cur = parent;
        }
        return path.join(' > ');
    }

    function findVisualLabel(el) {
        if (!el) return '';
        var aria = el.getAttribute('aria-label');
        if (aria) return aria.trim();
        var ariaBy = el.getAttribute('aria-labelledby');
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

        var container = el.closest && el.closest('div, td, li, fieldset, section');
        if (container) {
            var childLbl = container.querySelector('label, .label, .form-label, legend, span');
            if (childLbl && childLbl !== el && childLbl.textContent.length < 60) {
                return childLbl.textContent.trim();
            }
        }
        return (el.placeholder || el.title || '').trim();
    }

    // ============================================================
    // Interactive control collection
    // ============================================================
    function collectInteractiveControls() {
        var raw = document.querySelectorAll(
            'input, select, textarea, [role="combobox"], [role="checkbox"], [role="radio"], .iti__selected-flag, .iti__flag-container'
        );
        var list = [];
        for (var i = 0; i < raw.length; i++) {
            var el = raw[i];
            if (el.type === 'hidden' || el.type === 'submit' || el.type === 'button' || el.disabled || el.readOnly) continue;
            if (!isVisible(el)) continue;
            list.push(el);
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
    // Field classification
    // ============================================================
    function classifyElement(el, allControls, idx) {
        var tag = el.tagName.toLowerCase();
        var type = (el.type || '').toLowerCase();
        var hint = (
            (el.name || '') + ' ' +
            (el.id || '') + ' ' +
            (el.placeholder || '') + ' ' +
            (el.getAttribute('autocomplete') || '') + ' ' +
            (typeof el.className === 'string' ? el.className : '') + ' ' +
            findVisualLabel(el)
        ).toLowerCase();

        // 1. Checkbox / Radio
        if (type === 'checkbox' || el.getAttribute('role') === 'checkbox') return 'checkbox';
        if (type === 'radio' || el.getAttribute('role') === 'radio') {
            if (/male|female|gender|sex/.test(hint)) return 'gender';
            return 'radio';
        }

        // 2. OTP / verification — never auto-fill
        if (/otp|one.?time|verification.*code|auth.*code|totp|sms.*code|email.*code|captcha/.test(hint)) return 'otp';

        // 3. Country code (dropdown or small numeric input right before phone)
        if (/country.*code|dial.*code|phone.*code|isd|iti__|selected-flag|calling.*code|ccode|prefix/.test(hint)) {
            return 'countrycode';
        }
        if ((tag === 'select' || isCustomDropdownTrigger(el)) && idx + 1 < allControls.length) {
            var nextEl = allControls[idx + 1];
            var nextHint = ((nextEl.name || '') + ' ' + (nextEl.id || '') + ' ' + (nextEl.type || '') + ' ' + (nextEl.placeholder || '')).toLowerCase();
            if (nextEl.type === 'tel' || /phone|mobile|contact|tel/.test(nextHint)) {
                var txt = (el.textContent || el.value || '').trim();
                if (/^\+?\d{1,4}/.test(txt) || /country|code|flag|india|\+91|\+1/.test(hint + ' ' + txt.toLowerCase())) {
                    return 'countrycode';
                }
            }
        }
        if (tag === 'input' && (el.maxLength >= 2 && el.maxLength <= 4) && idx + 1 < allControls.length) {
            var nextInput = allControls[idx + 1];
            if (nextInput && (nextInput.type === 'tel' || /phone|mobile|tel/.test((nextInput.name || '') + (nextInput.placeholder || '')))) {
                return 'countrycode';
            }
        }

        // 4. Phone
        if (type === 'tel' || /phone|mobile|contact|whatsapp|cell|tel/.test(hint)) {
            var prevEl = idx > 0 ? allControls[idx - 1] : null;
            var prevLooksLike = prevEl ? prevEl.getAttribute('data-auto-type') : '';
            var hasFixedPrefix = false;
            if (el.parentElement) {
                var parentText = el.parentElement.textContent || '';
                if (/\+\d{1,3}/.test(parentText)) hasFixedPrefix = true;
            }
            if (prevLooksLike === 'countrycode' || hasFixedPrefix || el.maxLength === 10 || /10\s*digit/.test(hint)) {
                return 'phonenational';
            }
            if (/\+|international|country\s*code/.test(el.placeholder || '')) return 'phonee164';
            return 'phonenational';
        }

        // 5. Email
        if (type === 'email' || /email|e-mail|mail/.test(hint)) return 'email';

        // 6. Password / confirm password
        if (type === 'password' || /password|passcode|pwd|pin/.test(hint)) {
            if (/confirm|retype|repeat|re-?enter/.test(hint)) return 'password'; // no separate confirm key — Kotlin reuses password
            return 'password';
        }

        // 7. Name parts
        if (/first.*name|fname|given.*name|forename/.test(hint)) return 'firstname';
        if (/last.*name|lname|surname|family.*name/.test(hint)) return 'lastname';
        if (/middle.*name|mname/.test(hint)) return 'middlename';
        if (/user.*name|userid|login.*id|handle/.test(hint)) return 'username';
        if (/full.*name|your.*name|applicant.*name|\bname\b/.test(hint)) return 'fullname';

        // 8. Address parts
        if (/address|street|addr|flat|house|line1/.test(hint)) return 'address';
        if (/city|town|district|municipality/.test(hint)) return 'city';
        if (/state|province|region/.test(hint)) return 'state';
        if (/zip|postal|pincode|postcode/.test(hint)) return 'zip';
        if (/country|nation/.test(hint)) return 'country';

        // 9. DOB
        if (type === 'date' || /dob|birth|birthday|birthdate/.test(hint)) return 'dob';

        // 10. Gender
        if (/gender|sex/.test(hint)) return 'gender';

        return 'unknown';
    }

    // ============================================================
    // Value setters
    // ============================================================
    function setNativeTextValue(el, val) {
        if (!el) return;
        el.focus();
        var proto = Object.getPrototypeOf(el);
        var desc = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
        if (desc && desc.set) desc.set.call(el, val);
        else el.value = val;
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        el.dispatchEvent(new Event('blur', { bubbles: true }));
    }

    async function setKeystrokeStream(el, val) {
        if (!el) return;
        el.focus();
        setNativeTextValue(el, '');
        var current = '';
        for (var i = 0; i < val.length; i++) {
            var ch = val[i];
            current += ch;
            el.dispatchEvent(new KeyboardEvent('keydown', { key: ch, bubbles: true }));
            setNativeTextValue(el, current);
            el.dispatchEvent(new KeyboardEvent('keyup', { key: ch, bubbles: true }));
            await sleep(25);
        }
        el.dispatchEvent(new Event('blur', { bubbles: true }));
    }

    function dispatchRealClick(el) {
        if (!el) return;
        var opts = { bubbles: true, cancelable: true, view: window };
        try { el.dispatchEvent(new PointerEvent('pointerdown', opts)); } catch (e) {}
        try { el.dispatchEvent(new MouseEvent('mousedown', opts)); } catch (e) {}
        try { el.dispatchEvent(new PointerEvent('pointerup', opts)); } catch (e) {}
        try { el.dispatchEvent(new MouseEvent('mouseup', opts)); } catch (e) {}
        el.click();
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

    // ============================================================
    // Dropdown handlers
    // ============================================================
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
            selectEl.dispatchEvent(new Event('input', { bubbles: true }));
            selectEl.dispatchEvent(new Event('change', { bubbles: true }));
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

        // Try search input if the dropdown has one
        var searchBox = document.querySelector('.iti__search-input, .select2-search__field, input[role="combobox"][aria-expanded="true"], .ant-select-selection-search-input');
        if (searchBox && isVisible(searchBox) && normalized.length > 0) {
            setNativeTextValue(searchBox, normalized[0]);
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
                if (dialAttr === needle || dialAttr === needleDigits || txt === needle) {
                    dispatchRealClick(node); return true;
                }
                if (needleDigits && (txt.indexOf('+' + needleDigits) !== -1 || txt.indexOf('(' + needleDigits + ')') !== -1)) {
                    dispatchRealClick(node); return true;
                }
                if (needle.length >= 3 && txt.indexOf(needle) !== -1) {
                    dispatchRealClick(node); return true;
                }
            }
        }

        // Fallback: press Enter in case search narrowed to 1 result
        try {
            if (searchBox) searchBox.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, bubbles: true }));
        } catch (e) {}
        return false;
    }

    // ============================================================
    // Candidate resolver
    // ============================================================
    function resolveCandidatesForKey(key, vault) {
        var k = (key || '').toLowerCase();
        if (k === 'countrycode') return [vault.countrycode, vault.countrycodedigits, vault.country, vault.countryiso2, vault.countryiso3];
        if (k === 'country') return [vault.country, vault.countryiso2, vault.countryiso3, vault.countrycode];
        if (k === 'phonenational') return [vault.phonenational, vault.phone];
        if (k === 'phonee164') return [vault.phonee164, vault.phone, vault.phonenational];
        return [vault[k], vault.text, vault.fallback];
    }

    // ============================================================
    // Execute a single element action — returns true if filled
    // ============================================================
    async function executeElementAction(el, targetKey, strategy, fixedValue, vault) {
        if (!el) return false;
        var tag = el.tagName.toLowerCase();
        var type = (el.type || '').toLowerCase();
        var candidates = fixedValue ? [fixedValue] : resolveCandidatesForKey(targetKey, vault);
        var primaryValue = (candidates.filter(Boolean)[0]) || '';

        if (targetKey === 'click_advance' || strategy === 'CLICK_NEXT') {
            dispatchRealClick(el); return true;
        }
        if (type === 'checkbox' || el.getAttribute('role') === 'checkbox' || strategy === 'CLICK_CHECK') {
            var shouldCheck = fixedValue ? fixedValue === 'true' : true;
            setCheckboxState(el, shouldCheck); return true;
        }
        if (type === 'radio' || el.getAttribute('role') === 'radio') {
            selectRadioOption(el, primaryValue); return true;
        }
        if (tag === 'select') return selectNativeDropdown(el, candidates);
        if (isCustomDropdownTrigger(el) || strategy === 'CUSTOM_DROPDOWN') return await selectCustomDropdown(el, candidates);
        if (!primaryValue) return false;

        // Phone guard: strip to last 10 digits if input expects local number
        if ((targetKey.indexOf('phone') !== -1 || type === 'tel') && el.maxLength === 10 && primaryValue.length > 10) {
            primaryValue = primaryValue.replace(/\D/g, '').slice(-10);
        }
        // Country code text input: strip + and non-digits
        if (targetKey === 'countrycode' && (type === 'number' || el.maxLength === 2 || el.maxLength === 3)) {
            primaryValue = primaryValue.replace(/\D/g, '');
        }

        if (strategy === 'KEYSTROKE_STREAM') await setKeystrokeStream(el, primaryValue);
        else setNativeTextValue(el, primaryValue);
        return true;
    }

    // ============================================================
    // Rule lookup
    // ============================================================
    function findElementByRule(rule) {
        if (rule.selector) {
            try { var bySel = document.querySelector(rule.selector); if (bySel && isVisible(bySel)) return bySel; } catch (e) {}
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
    // Main execute — reports back fill count
    // ============================================================
    window.__automationExecute = async function(vaultJson, rulesJson) {
        var filledCount = 0;
        try {
            currentVault = typeof vaultJson === 'string' ? JSON.parse(vaultJson) : (vaultJson || {});
            currentRules = typeof rulesJson === 'string' ? JSON.parse(rulesJson) : (rulesJson || []);
            var handledElements = [];

            // Phase 1 — Learned rules
            for (var r = 0; r < currentRules.length; r++) {
                var rule = currentRules[r];
                var el = findElementByRule(rule);
                if (el) {
                    handledElements.push(el);
                    if (await executeElementAction(el, rule.targetKey, rule.strategy, rule.fixedValue, currentVault)) filledCount++;
                    await sleep(120);
                }
            }

            // Phase 2 — Country codes first
            var controls = collectInteractiveControls();
            for (var i = 0; i < controls.length; i++) {
                var ctrl = controls[i];
                if (handledElements.indexOf(ctrl) !== -1) continue;
                var kind = classifyElement(ctrl, controls, i);
                if (kind === 'countrycode') {
                    handledElements.push(ctrl);
                    if (await executeElementAction(ctrl, 'countrycode', 'NATIVE_SETTER', '', currentVault)) filledCount++;
                    await sleep(180);
                }
            }

            // Phase 3 — Everything else (skip unknown/otp/checkbox/radio)
            for (var j = 0; j < controls.length; j++) {
                var itemEl = controls[j];
                if (handledElements.indexOf(itemEl) !== -1) continue;
                var detectedType = classifyElement(itemEl, controls, j);
                if (SKIP_TYPES[detectedType]) continue;
                if (await executeElementAction(itemEl, detectedType, 'NATIVE_SETTER', '', currentVault)) filledCount++;
                await sleep(60);
            }
        } catch (e) {
            toast('Automation error: ' + e.message);
        } finally {
            try {
                if (window.AutomationBridge && window.AutomationBridge.onFillComplete) {
                    window.AutomationBridge.onFillComplete(filledCount);
                }
            } catch (e) {}
        }
    };

    // ============================================================
    // Configure — receives vault + learned rules from Kotlin
    // ============================================================
    window.__automationConfigure = function(vaultJson, rulesJson) {
        try {
            currentVault = typeof vaultJson === 'string' ? JSON.parse(vaultJson) : (vaultJson || {});
            currentRules = typeof rulesJson === 'string' ? JSON.parse(rulesJson) : (rulesJson || []);
            scan();
        } catch (e) {}
    };

    // ============================================================
    // Passive learning (records what user manually fills)
    // ============================================================
    function matchValueAgainstVault(typedVal) {
        if (!typedVal || typedVal.length < 2) return null;
        var cleanTyped = typedVal.trim().toLowerCase();
        var keys = ['email','phonenational','phonee164','countrycode','firstname','lastname','fullname','username','address','city','state','zip','country','dob'];
        for (var i = 0; i < keys.length; i++) {
            var k = keys[i];
            var v = (currentVault[k] || '').trim().toLowerCase();
            if (v && v === cleanTyped) return k;
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
        var payload = {
            domain: window.location.hostname.replace(/^www\./, ''),
            selector: computeSelector(el),
            nameAttr: el.name || '',
            idAttr: el.id || '',
            visualLabel: findVisualLabel(el),
            controlType: controlType,
            targetKey: targetKey,
            strategy: strategy,
            fixedValue: fixedValue || ''
        };
        window.AutomationBridge.onRuleLearned(JSON.stringify(payload));
    }

    // ============================================================
    // Teach mode HUD
    // ============================================================
    var hudBanner = null;
    var hudModal = null;

    window.__automationSetTeachMode = function(active, vaultJson) {
        teachMode = !!active;
        if (vaultJson) {
            try { currentVault = typeof vaultJson === 'string' ? JSON.parse(vaultJson) : vaultJson; } catch (e) {}
        }
        if (teachMode) { showTeachBanner(); toast('Teach Mode ON: Tap any field'); }
        else { removeTeachUi(); toast('Teach Mode OFF'); }
    };

    function showTeachBanner() {
        removeTeachUi();
        hudBanner = document.createElement('div');
        hudBanner.id = '__auto_teach_banner';
        hudBanner.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:2147483646;background:#111827;color:#10B981;padding:10px 14px;font:600 13px sans-serif;display:flex;justify-content:space-between;align-items:center;box-shadow:0 2px 10px rgba(0,0,0,0.4);';
        hudBanner.innerHTML = '<span>🎯 Teach Mode: Tap any input, dropdown, checkbox, or button</span><button id="__auto_teach_close" style="background:#EF4444;color:#fff;border:none;border-radius:6px;padding:4px 10px;font-weight:bold;">Done</button>';
        document.body.appendChild(hudBanner);
        document.getElementById('__auto_teach_close').onclick = function(ev) {
            ev.stopPropagation();
            window.__automationSetTeachMode(false);
        };
    }

    function removeTeachUi() {
        if (hudBanner && hudBanner.parentNode) hudBanner.parentNode.removeChild(hudBanner);
        if (hudModal && hudModal.parentNode) hudModal.parentNode.removeChild(hudModal);
        hudBanner = null;
        hudModal = null;
    }

    document.addEventListener('click', function(e) {
        if (!teachMode) return;
        if (hudBanner && hudBanner.contains(e.target)) return;
        if (hudModal && hudModal.contains(e.target)) return;
        e.preventDefault();
        e.stopPropagation();
        var el = e.target.closest('input, select, textarea, button, [role="combobox"], [role="checkbox"], [role="radio"], .iti__selected-flag, a') || e.target;
        el.style.outline = '2px dashed #10B981';
        openTeachPickerForElement(el);
    }, true);

    function openTeachPickerForElement(el) {
        if (hudModal && hudModal.parentNode) hudModal.parentNode.removeChild(hudModal);
        hudModal = document.createElement('div');
        hudModal.style.cssText = 'position:fixed;bottom:16px;left:12px;right:12px;z-index:2147483647;background:#1F2937;color:#F9FAFB;border-radius:12px;padding:14px;font:13px sans-serif;box-shadow:0 8px 24px rgba(0,0,0,0.5);border:1px solid #374151;';

        var options = [
            { label: 'Phone (10-Digit Local)', key: 'phonenational', strat: 'NATIVE_SETTER' },
            { label: 'Phone (+CountryCode)', key: 'phonee164', strat: 'NATIVE_SETTER' },
            { label: 'Country Code (+91/IN)', key: 'countrycode', strat: isCustomDropdownTrigger(el) ? 'CUSTOM_DROPDOWN' : 'SELECT_OPTION' },
            { label: 'Email', key: 'email', strat: 'NATIVE_SETTER' },
            { label: 'Full Name', key: 'fullname', strat: 'NATIVE_SETTER' },
            { label: 'First Name', key: 'firstname', strat: 'NATIVE_SETTER' },
            { label: 'Last Name', key: 'lastname', strat: 'NATIVE_SETTER' },
            { label: 'Username', key: 'username', strat: 'NATIVE_SETTER' },
            { label: 'Password', key: 'password', strat: 'NATIVE_SETTER' },
            { label: 'Address', key: 'address', strat: 'NATIVE_SETTER' },
            { label: 'City', key: 'city', strat: 'NATIVE_SETTER' },
            { label: 'ZIP / Pincode', key: 'zip', strat: 'NATIVE_SETTER' },
            { label: 'Country Name', key: 'country', strat: isCustomDropdownTrigger(el) ? 'CUSTOM_DROPDOWN' : 'NATIVE_SETTER' },
            { label: 'Date of Birth', key: 'dob', strat: 'NATIVE_SETTER' },
            { label: 'Always Check Box', key: 'checkbox', strat: 'CLICK_CHECK', fixed: 'true' },
            { label: 'Click Button (Next/Submit)', key: 'click_advance', strat: 'CLICK_NEXT' },
            { label: 'Keystroke-by-Keystroke Phone', key: 'phonenational', strat: 'KEYSTROKE_STREAM' }
        ];

        var html = '<div style="margin-bottom:8px;font-weight:bold;color:#93C5FD;">Bind Selected Element (' + el.tagName.toLowerCase() + '):</div><div style="display:flex;flex-wrap:wrap;gap:6px;max-height:210px;overflow-y:auto;">';
        options.forEach(function(opt, idx) {
            html += '<button data-idx="' + idx + '" style="background:#374151;color:#fff;border:1px solid #4B5563;border-radius:6px;padding:6px 10px;font-size:12px;">' + opt.label + '</button>';
        });
        html += '</div>';
        hudModal.innerHTML = html;
        document.body.appendChild(hudModal);

        var btns = hudModal.querySelectorAll('button[data-idx]');
        for (var i = 0; i < btns.length; i++) {
            btns[i].onclick = function(ev) {
                ev.stopPropagation();
                var chosen = options[parseInt(this.getAttribute('data-idx'), 10)];
                emitLearnedRule(el, el.tagName.toLowerCase(), chosen.key, chosen.strat, chosen.fixed || '');
                if (hudModal && hudModal.parentNode) hudModal.parentNode.removeChild(hudModal);
                hudModal = null;
            };
        }
    }

    // Clean up teach UI on navigation
    window.addEventListener('beforeunload', removeTeachUi);
    window.addEventListener('pagehide', removeTeachUi);

    // ============================================================
    // Mutation observer — re-scan for SPA / multi-step forms
    // ============================================================
    var observer = new MutationObserver(function() {
        if (scanTimer) clearTimeout(scanTimer);
        scanTimer = setTimeout(scan, 500);
    });
    if (document.body) observer.observe(document.body, { childList: true, subtree: true });

    window.__automationScan = scan;

    // ============================================================
    // Scan function
    // ============================================================
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