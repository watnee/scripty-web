/**
 * Focus mode toggle.
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyFocusModeInit) return;
    window._scriptyFocusModeInit = true;

    var STORAGE_KEY = 'scripty-focus-mode';

    function isOn() {
        return localStorage.getItem(STORAGE_KEY) === '1';
    }

    function apply(on) {
        document.body.classList.toggle('focus-mode', on);
        var btn = document.getElementById('focus-toggle');
        if (btn) {
            btn.setAttribute('aria-pressed', on ? 'true' : 'false');
            btn.title = on ? 'Exit focus mode' : 'Hide distractions while writing';
            btn.setAttribute('aria-label', btn.title);
        }
    }

    function sync() {
        if (document.getElementById('focus-toggle')) {
            apply(isOn());
        } else {
            document.body.classList.remove('focus-mode');
        }
    }

    document.body.addEventListener('click', function (e) {
        var btn = e.target && e.target.closest && e.target.closest('#focus-toggle');
        if (!btn) return;
        var next = !isOn();
        localStorage.setItem(STORAGE_KEY, next ? '1' : '0');
        apply(next);
    });

    document.body.addEventListener('htmx:afterSwap', sync);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sync);
    } else {
        sync();
    }
})();
