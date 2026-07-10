/**
 * Toolbar visibility toggle.
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyToolbarToggleInit) return;
    window._scriptyToolbarToggleInit = true;

    var STORAGE_KEY = 'scripty-toolbar-hidden';

    function isHidden() {
        return localStorage.getItem(STORAGE_KEY) === 'true';
    }

    function apply(hidden) {
        document.documentElement.classList.toggle('scripty-toolbar-hidden', hidden);
        var btn = document.getElementById('nav-toolbar-toggle');
        if (btn) {
            btn.setAttribute('aria-pressed', hidden ? 'true' : 'false');
            btn.classList.toggle('is-active', hidden);
            btn.title = hidden ? 'Show tools' : 'Hide tools';
            btn.setAttribute('aria-label', btn.title);
        }
    }

    function sync() {
        if (document.getElementById('nav-toolbar-toggle')) {
            apply(isHidden());
        } else {
            document.documentElement.classList.remove('scripty-toolbar-hidden');
        }
    }

    document.body.addEventListener('click', function (e) {
        var btn = e.target && e.target.closest && e.target.closest('#nav-toolbar-toggle');
        if (!btn) return;
        var next = !isHidden();
        localStorage.setItem(STORAGE_KEY, next ? 'true' : 'false');
        apply(next);
    });

    document.body.addEventListener('htmx:afterSwap', sync);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sync);
    } else {
        sync();
    }
})();
