/**
 * Persist / restore window scroll position per page path.
 *
 * Complements editor-position.js, which owns the project editor
 * (/project/show) with its richer caret + scroll persistence. This module
 * covers the other scrollable pages (project list, trash, etc.) so that
 * reopening the app — or navigating back to a page — returns you to where
 * you left off instead of the top. Loaded from nav.html so it survives
 * HTMX-boosted navigation.
 */
(function () {
    'use strict';

    if (window._scriptyScrollPositionModuleInit) return;
    window._scriptyScrollPositionModuleInit = true;

    var saveTimer = null;

    // The editor page has its own richer persistence (caret + scroll); leave it alone.
    function isEditorPage() {
        return window.location.pathname.indexOf('/project/show') === 0;
    }

    // Pages whose window scroll we persist.
    function isTrackedPage() {
        return !isEditorPage();
    }

    function storageKey() {
        return 'scripty-scroll-position:' + window.location.pathname;
    }

    function currentScrollY() {
        return Math.round(window.scrollY || window.pageYOffset || 0);
    }

    function saveScrollPosition() {
        if (!isTrackedPage()) return;
        try {
            localStorage.setItem(storageKey(), String(currentScrollY()));
        } catch (e) {
            /* quota / private mode */
        }
    }

    function scheduleSaveScrollPosition(delayMs) {
        if (saveTimer) clearTimeout(saveTimer);
        saveTimer = setTimeout(function () {
            saveTimer = null;
            saveScrollPosition();
        }, delayMs == null ? 200 : delayMs);
    }

    function restoreScrollPosition() {
        if (!isTrackedPage()) return;
        // An explicit anchor target (#id) should win over a restored position.
        if (window.location.hash && window.location.hash.length > 1) return;

        var raw;
        try {
            raw = localStorage.getItem(storageKey());
        } catch (e) {
            return;
        }
        if (raw == null) return;

        var y = parseInt(raw, 10);
        if (isNaN(y) || y <= 0) return;

        // Scroll now (synchronously) — requestAnimationFrame is unreliable here:
        // when the app is reopened the document can still be backgrounded
        // (visibilityState "hidden"), in which case rAF callbacks never fire.
        window.scrollTo(0, y);

        // Re-assert shortly after in case late layout (fonts, list rendering,
        // images) grew the page and clamped the first scroll short. Only if the
        // user hasn't scrolled away in the meantime.
        setTimeout(function () {
            if (currentScrollY() < y) {
                window.scrollTo(0, y);
            }
        }, 150);
    }

    function initScrollPositionPersistence() {
        if (window._scriptyScrollPositionPersistenceInit) return;
        window._scriptyScrollPositionPersistenceInit = true;

        window.addEventListener('scroll', function () {
            if (!isTrackedPage()) return;
            scheduleSaveScrollPosition(200);
        }, { passive: true });

        document.addEventListener('visibilitychange', function () {
            if (document.hidden) saveScrollPosition();
        });

        window.addEventListener('pagehide', saveScrollPosition);
    }

    window.scriptySaveScrollPosition = saveScrollPosition;
    window.scriptyScheduleSaveScrollPosition = scheduleSaveScrollPosition;
    window.scriptyRestoreScrollPosition = restoreScrollPosition;
    window.scriptyInitScrollPositionPersistence = initScrollPositionPersistence;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initScrollPositionPersistence);
    } else {
        initScrollPositionPersistence();
    }
})();
