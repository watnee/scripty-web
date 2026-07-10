/**
 * Settings dropdown theme picker (system / light / dark).
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation.
 */
(function () {
    'use strict';

    if (window._scriptyThemeToggleInit) return;
    window._scriptyThemeToggleInit = true;

    function applyTheme(theme, items) {
        var root = document.documentElement;
        root.setAttribute('data-theme-setting', theme);

        if (theme === 'dark') {
            root.classList.add('-dark-theme');
            root.classList.remove('-no-dark-theme');
        } else if (theme === 'light') {
            root.classList.add('-no-dark-theme');
            root.classList.remove('-dark-theme');
        } else {
            root.classList.remove('-dark-theme');
            root.classList.remove('-no-dark-theme');
        }

        if (!items || !items.length) return;
        items.forEach(function (item) {
            var isActive = item.getAttribute('data-theme') === theme;
            item.classList.toggle('active', isActive);
            item.setAttribute('aria-checked', isActive ? 'true' : 'false');
        });
    }

    function initThemeToggle() {
        var dropdown = document.getElementById('settings-dropdown');
        if (!dropdown) return;

        var items = dropdown.querySelectorAll('[data-theme]');
        if (!items.length) return;

        items.forEach(function (item) {
            if (item.dataset.scriptyThemeWired === '1') return;
            item.dataset.scriptyThemeWired = '1';
            item.addEventListener('click', function (e) {
                e.preventDefault();
                var selectedTheme = item.getAttribute('data-theme');
                localStorage.setItem('theme', selectedTheme);
                applyTheme(selectedTheme, items);
                dropdown.classList.remove('open');
                var toggleBtn = dropdown.querySelector('.nav-dropdown-toggle');
                if (toggleBtn) toggleBtn.setAttribute('aria-expanded', 'false');
            });
        });

        var currentTheme = localStorage.getItem('theme') || 'system';
        applyTheme(currentTheme, items);
    }

    window.scriptyInitThemeToggle = initThemeToggle;

    document.body.addEventListener('htmx:afterSwap', initThemeToggle);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initThemeToggle);
    } else {
        initThemeToggle();
    }
})();
