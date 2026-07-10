/**
 * Page chrome helpers: stylesheet version, breadcrumbs, body classes,
 * ? / Songs / Drafts keyboard shortcuts.
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

    function isTypingTarget(el) {
        if (typeof window.scriptyIsTypingContext === 'function') {
            return window.scriptyIsTypingContext(el);
        }
        if (!el) return false;
        var tag = el.tagName;
        return tag === 'TEXTAREA' || tag === 'INPUT' || !!el.isContentEditable;
    }

    function syncSongsDraftsShortcutLabels() {
        var isMac = window.scriptyIsMac
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
        var songsShortcut = isMac ? ' (⌘⇧S)' : ' (Ctrl+Shift+S)';
        var draftsShortcut = isMac ? ' (⌘⇧D)' : ' (Ctrl+Shift+D)';
        var songsBtn = document.querySelector('a.songs-toolbar-btn');
        var draftsBtn = document.querySelector('a.drafts-toolbar-btn');
        if (songsBtn) {
            songsBtn.title = 'Songs' + songsShortcut;
            songsBtn.setAttribute('aria-label', 'Songs' + songsShortcut);
        }
        if (draftsBtn) {
            draftsBtn.title = 'Drafts' + draftsShortcut;
            draftsBtn.setAttribute('aria-label', 'Drafts' + draftsShortcut);
        }
    }

    function initGlobalShortcuts() {
        if (window._scriptyGlobalShortcutsInit) return;
        window._scriptyGlobalShortcutsInit = true;

        document.addEventListener('keydown', function (e) {
            if (e.key !== '?' || e.metaKey || e.ctrlKey || e.altKey) return;
            if (isTypingTarget(document.activeElement)) return;
            if (window.location.pathname === '/shortcuts') return;
            e.preventDefault();
            window.location.href = '/shortcuts';
        });

        // ⌘⇧S / Ctrl+Shift+S → Songs, ⌘⇧D / Ctrl+Shift+D → Drafts (project page).
        document.addEventListener('keydown', function (e) {
            if (!(e.metaKey || e.ctrlKey) || !e.shiftKey || e.altKey) return;
            var key = (e.key || '').toLowerCase();
            if (key !== 's' && key !== 'd') return;
            if (isTypingTarget(document.activeElement)) return;
            var link = document.querySelector(
                key === 's' ? 'a.songs-toolbar-btn' : 'a.drafts-toolbar-btn'
            );
            if (!link || !link.getAttribute('href')) return;
            e.preventDefault();
            window.location.href = link.href;
        });
    }

    function initPageChrome() {
        syncBodyPageClasses();
        prepareBreadcrumbLinks();
        syncStylesheetVersion();
        syncSongsDraftsShortcutLabels();
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
        syncSongsDraftsShortcutLabels();
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initPageChrome);
    } else {
        initPageChrome();
    }
})();
