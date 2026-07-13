/**
 * Clears legacy toolbar-hidden preference (toggle UI removed).
 *
 * Loaded from nav.html so it still runs on HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyToolbarToggleInit) return;
    window._scriptyToolbarToggleInit = true;

    var STORAGE_KEY = 'scripty-toolbar-hidden';

    function clearHidden() {
        try {
            localStorage.removeItem(STORAGE_KEY);
        } catch (e) {}
        document.documentElement.classList.remove('scripty-toolbar-hidden');
    }

    document.body.addEventListener('htmx:afterSwap', clearHidden);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', clearHidden);
    } else {
        clearHidden();
    }
})();
