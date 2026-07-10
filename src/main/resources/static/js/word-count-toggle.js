/**
 * Word count / page stats visibility toggle.
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyWordCountToggleInit) return;
    window._scriptyWordCountToggleInit = true;

    var STORAGE_KEY = 'scripty-word-count-hidden';
    var CLASS_NAME = 'scripty-word-count-hidden';

    function isHidden() {
        return localStorage.getItem(STORAGE_KEY) === 'true';
    }

    function apply(hidden) {
        document.documentElement.classList.toggle(CLASS_NAME, hidden);
        var btn = document.getElementById('nav-word-count-toggle');
        if (btn) {
            btn.setAttribute('aria-pressed', hidden ? 'true' : 'false');
            btn.setAttribute('aria-checked', hidden ? 'false' : 'true');
            btn.classList.toggle('is-active', hidden);
            btn.title = hidden ? 'Show word count' : 'Hide word count';
            btn.setAttribute('aria-label', btn.title);
        }
        var viewBtn = document.querySelector('#project-view-dropdown .view-toolbar-btn');
        if (viewBtn) {
            var anyHidden = hidden
                || document.documentElement.classList.contains('scripty-hide-block-bookmarks')
                || document.documentElement.classList.contains('scripty-hide-block-pins')
                || document.documentElement.classList.contains('scripty-hide-block-select')
                || document.documentElement.classList.contains('scripty-hide-block-element-labels');
            viewBtn.classList.toggle('is-active', anyHidden);
        }
    }

    function sync() {
        if (document.getElementById('nav-word-count-toggle')) {
            apply(isHidden());
        } else {
            document.documentElement.classList.remove(CLASS_NAME);
        }
    }

    document.body.addEventListener('click', function (e) {
        var btn = e.target && e.target.closest && e.target.closest('#nav-word-count-toggle');
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
