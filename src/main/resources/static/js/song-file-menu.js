/**
 * Song editor File menu: Import picks a file and posts it.
 *
 * Open/close is handled by nav-dropdowns.js; export links and Print need no
 * script. Loaded from nav.html so it survives HTMX-boosted navigation
 * (allowScriptTags is false, so edit.html script tags are not executed
 * after a boost).
 */
(function () {
    'use strict';

    if (window._scriptySongFileMenuInit) return;
    window._scriptySongFileMenuInit = true;

    function initSongFileMenu() {
        var btn = document.getElementById('song-nav-import');
        var file = document.getElementById('song-import-file');
        var form = document.getElementById('song-import-form');
        if (!btn || !file || !form) return;
        if (btn.dataset.scriptySongImportWired === '1') return;
        btn.dataset.scriptySongImportWired = '1';

        btn.addEventListener('click', function () {
            file.click();
        });
        file.addEventListener('change', function () {
            if (!file.files || !file.files.length) return;
            form.submit();
        });
    }

    document.body.addEventListener('htmx:afterSettle', initSongFileMenu);
    document.body.addEventListener('htmx:afterSwap', initSongFileMenu);
    document.body.addEventListener('htmx:historyRestore', initSongFileMenu);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initSongFileMenu);
    } else {
        initSongFileMenu();
    }
})();
