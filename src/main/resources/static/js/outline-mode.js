/**
 * Outline mode toggle.
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyOutlineModeInit) return;
    window._scriptyOutlineModeInit = true;

    var STORAGE_KEY = 'scripty-outline-mode';

    function isOn() {
        return localStorage.getItem(STORAGE_KEY) === '1';
    }

    function shortcutHint() {
        var isMac = window.scriptyIsMac
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
        return isMac ? '⌘⇧O' : 'Ctrl+Shift+O';
    }

    function apply(on) {
        document.documentElement.classList.toggle('scripty-outline-mode', on);
        document.body.classList.toggle('outline-mode', on);
        var btn = document.getElementById('outline-mode-toggle');
        if (btn) {
            btn.setAttribute('aria-pressed', on ? 'true' : 'false');
            btn.setAttribute('aria-checked', on ? 'true' : 'false');
            btn.classList.toggle('is-active', on);
            var base = on ? 'Exit outline mode' : 'Show only scenes, sections, and synopses';
            btn.title = base + ' (' + shortcutHint() + ')';
            btn.setAttribute('aria-label', btn.title);
        }
        try {
            window.dispatchEvent(new CustomEvent('scripty:outline-mode-changed', { detail: { on: on } }));
        } catch (err) { /* ignore */ }
    }

    function sync() {
        if (document.getElementById('outline-mode-toggle')) {
            apply(isOn());
        } else {
            document.documentElement.classList.remove('scripty-outline-mode');
            document.body.classList.remove('outline-mode');
        }
    }

    window.scriptyIsOutlineMode = function () {
        return document.documentElement.classList.contains('scripty-outline-mode');
    };

    window.scriptySetOutlineMode = function (on, options) {
        var next = !!on;
        localStorage.setItem(STORAGE_KEY, next ? '1' : '0');
        apply(next);
        if (next && !(options && options.skipPeer) && typeof window.scriptySetPageViewMode === 'function') {
            window.scriptySetPageViewMode(false, { skipPeer: true });
        }
    };
    window.scriptyToggleOutlineMode = function () {
        window.scriptySetOutlineMode(!isOn());
    };

    document.body.addEventListener('click', function (e) {
        var btn = e.target && e.target.closest && e.target.closest('#outline-mode-toggle');
        if (!btn) return;
        window.scriptySetOutlineMode(!isOn());
    });

    document.body.addEventListener('htmx:afterSwap', sync);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sync);
    } else {
        sync();
    }
})();
