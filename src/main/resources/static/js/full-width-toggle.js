/**
 * Full page width toggle for the screenplay editor.
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyFullWidthInit) return;
    window._scriptyFullWidthInit = true;

    var STORAGE_KEY = 'scripty-screenplay-full-width';
    var CLASS_NAME = 'scripty-screenplay-full-width';

    function isOn() {
        return localStorage.getItem(STORAGE_KEY) === 'true';
    }

    function shortcutHint() {
        var isMac = window.scriptyIsMac
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
        return isMac ? '⌘\\' : 'Ctrl+\\';
    }

    function apply(on) {
        document.documentElement.classList.toggle(CLASS_NAME, on);
        var btn = document.getElementById('nav-full-width-toggle');
        if (btn) {
            btn.setAttribute('aria-pressed', on ? 'true' : 'false');
            btn.setAttribute('aria-checked', on ? 'true' : 'false');
            btn.classList.toggle('is-active', on);
            var base = on ? 'Use standard screenplay width' : 'Use full page width';
            btn.title = base + ' (' + shortcutHint() + ')';
            btn.setAttribute('aria-label', btn.title);
        }
        try {
            window.dispatchEvent(new CustomEvent('scripty:full-width-changed', { detail: { on: on } }));
        } catch (err) { /* ignore */ }
    }

    function sync() {
        if (document.getElementById('nav-full-width-toggle')) {
            apply(isOn());
        } else {
            document.documentElement.classList.remove(CLASS_NAME);
        }
    }

    window.scriptyIsFullWidth = isOn;
    window.scriptySetFullWidth = function (on) {
        var next = !!on;
        localStorage.setItem(STORAGE_KEY, next ? 'true' : 'false');
        apply(next);
    };
    window.scriptyToggleFullWidth = function () {
        window.scriptySetFullWidth(!isOn());
    };

    document.body.addEventListener('click', function (e) {
        var btn = e.target && e.target.closest && e.target.closest('#nav-full-width-toggle');
        if (!btn) return;
        window.scriptySetFullWidth(!isOn());
    });

    document.body.addEventListener('htmx:afterSwap', sync);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sync);
    } else {
        sync();
    }
})();
