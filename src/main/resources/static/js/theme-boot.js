/**
 * Apply stored theme / spellcheck / Safari Reader flags before paint.
 *
 * Loaded early from nav.html (before body content paints when possible).
 */
(function () {
    'use strict';

    if (window._scriptyThemeBootInit) return;
    window._scriptyThemeBootInit = true;

    var storedTheme = localStorage.getItem('theme') || 'system';
    document.documentElement.setAttribute('data-theme-setting', storedTheme);
    if (storedTheme === 'dark') {
        document.documentElement.classList.add('-dark-theme');
    } else if (storedTheme === 'light') {
        document.documentElement.classList.add('-no-dark-theme');
    }

    var spellcheckEnabled = localStorage.getItem('spellcheck') !== 'false';
    document.documentElement.setAttribute('data-spellcheck', spellcheckEnabled ? 'true' : 'false');

    var href = window.location.href || '';
    if (window.location.protocol === 'safari-reader:' || href.indexOf('about:reader') === 0) {
        document.documentElement.classList.add('safari-reader-view');
    }
})();
