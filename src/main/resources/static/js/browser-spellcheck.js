/**
 * Browser native spellcheck toggle (distinct from Typo.js custom spellcheck).
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation.
 */
(function () {
    'use strict';

    if (window._scriptyBrowserSpellcheckInit) return;
    window._scriptyBrowserSpellcheckInit = true;

    function isSpellcheckEnabled() {
        return localStorage.getItem('spellcheck') !== 'false';
    }

    function updateSpellcheckToggleButton(state) {
        var btn = document.querySelector('.spellcheck-toggle-btn');
        if (!btn) return;
        var title = state ? 'Spellcheck: Enabled (click to disable)' : 'Spellcheck: Disabled (click to enable)';
        btn.title = title;
        btn.setAttribute('aria-label', title);
        btn.setAttribute('aria-pressed', state ? 'true' : 'false');
    }

    function applySpellcheckToElement(el, state) {
        if (!(el.tagName === 'TEXTAREA' || (el.tagName === 'INPUT' && el.type === 'text'))) {
            return;
        }
        el.setAttribute('spellcheck', state ? 'true' : 'false');
        el.spellcheck = !!state;
    }

    function remountSpellcheckField(el, state) {
        if (!el || !el.parentNode) return el;
        var wasFocused = document.activeElement === el;
        var start = wasFocused ? el.selectionStart : null;
        var end = wasFocused ? el.selectionEnd : null;
        var clone = el.cloneNode(true);
        applySpellcheckToElement(clone, state);
        if (el.tagName === 'TEXTAREA') {
            clone.value = el.value;
        }
        window.scriptySuppressSpellcheckRemountSave = true;
        try {
            el.parentNode.replaceChild(clone, el);
            if (wasFocused) {
                clone.focus();
                try {
                    if (start != null && end != null && typeof clone.setSelectionRange === 'function') {
                        clone.setSelectionRange(start, end);
                    }
                } catch (err) { /* ignore */ }
                if (typeof window.scriptyGrowTextarea === 'function') {
                    window.scriptyGrowTextarea(clone);
                } else if (clone.tagName === 'TEXTAREA') {
                    clone.style.height = 'auto';
                    clone.style.height = clone.scrollHeight + 'px';
                }
            }
        } finally {
            setTimeout(function () {
                window.scriptySuppressSpellcheckRemountSave = false;
            }, 0);
        }
        return clone;
    }

    function updateAllSpellcheckElements(options) {
        var state = isSpellcheckEnabled();
        var remountFocused = !!(options && options.remountFocused);
        var active = document.activeElement;
        document.querySelectorAll('textarea, input[type="text"]').forEach(function (el) {
            if (remountFocused && el === active) {
                remountSpellcheckField(el, state);
            } else {
                applySpellcheckToElement(el, state);
            }
        });
        return state;
    }

    function toggleSpellcheck() {
        var newState = !isSpellcheckEnabled();
        localStorage.setItem('spellcheck', newState ? 'true' : 'false');
        document.documentElement.setAttribute('data-spellcheck', newState ? 'true' : 'false');
        updateAllSpellcheckElements({ remountFocused: true });
        updateSpellcheckToggleButton(newState);
    }

    function initSpellcheck() {
        var state = isSpellcheckEnabled();
        updateAllSpellcheckElements();
        updateSpellcheckToggleButton(state);
    }

    function syncSpellcheckAfterHtmx() {
        updateAllSpellcheckElements();
    }

    window.scriptyIsSpellcheckEnabled = isSpellcheckEnabled;
    window.scriptyApplySpellcheck = applySpellcheckToElement;
    window.scriptyUpdateAllSpellcheckElements = updateAllSpellcheckElements;
    window.toggleSpellcheck = toggleSpellcheck;
    window.scriptyInitBrowserSpellcheck = initSpellcheck;

    document.body.addEventListener('htmx:afterOnLoad', syncSpellcheckAfterHtmx);
    document.body.addEventListener('htmx:afterSwap', syncSpellcheckAfterHtmx);
    document.body.addEventListener('htmx:afterSettle', syncSpellcheckAfterHtmx);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initSpellcheck);
    } else {
        initSpellcheck();
    }
})();
