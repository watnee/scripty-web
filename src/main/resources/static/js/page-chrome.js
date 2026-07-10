/**
 * Page chrome helpers: stylesheet version, breadcrumbs, body classes, ? shortcut.
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation.
 */
(function () {
    'use strict';

    if (window._scriptyPageChromeInit) return;
    window._scriptyPageChromeInit = true;

    function syncStylesheetVersion() {
        var meta = document.querySelector('meta[name="app-asset-version"]');
        var link = document.querySelector('link[href*="/css/scripty.css"]');
        if (!meta || !link) return;
        var expected = meta.getAttribute('content');
        if (!expected) return;
        var href = link.getAttribute('href') || '';
        if (href.indexOf('v=' + expected) !== -1) return;
        link.href = '/css/scripty.css?v=' + expected;
    }

    function prepareBreadcrumbLinks() {
        document.querySelectorAll('nav[aria-label="Breadcrumb"] a[href]').forEach(function (link) {
            link.setAttribute('hx-boost', 'false');
        });
    }

    function syncBodyPageClasses() {
        var path = window.location.pathname || '';
        document.body.classList.toggle('has-toolbar-brand', path === '/project/show');
        document.body.classList.toggle('login-body', path === '/login');
        document.body.classList.toggle('landing-body', path === '/');
    }

    function initGlobalShortcuts() {
        if (window._scriptyGlobalShortcutsInit) return;
        window._scriptyGlobalShortcutsInit = true;

        document.addEventListener('keydown', function (e) {
            if (e.key !== '?' || e.metaKey || e.ctrlKey || e.altKey) return;
            var active = document.activeElement;
            if (typeof window.scriptyIsTypingContext === 'function') {
                if (window.scriptyIsTypingContext(active)) return;
            } else if (active) {
                var tag = active.tagName;
                if (tag === 'TEXTAREA' || tag === 'INPUT' || active.isContentEditable) return;
            }
            if (window.location.pathname === '/shortcuts') return;
            e.preventDefault();
            window.location.href = '/shortcuts';
        });
    }

    function initPageChrome() {
        syncBodyPageClasses();
        prepareBreadcrumbLinks();
        syncStylesheetVersion();
        initGlobalShortcuts();
    }

    window.scriptySyncStylesheetVersion = syncStylesheetVersion;
    window.scriptyPrepareBreadcrumbLinks = prepareBreadcrumbLinks;
    window.scriptySyncBodyPageClasses = syncBodyPageClasses;
    window.scriptyInitPageChrome = initPageChrome;

    document.body.addEventListener('htmx:afterSwap', initPageChrome);
    document.body.addEventListener('htmx:afterSettle', function () {
        syncBodyPageClasses();
        prepareBreadcrumbLinks();
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initPageChrome);
    } else {
        initPageChrome();
    }
})();
