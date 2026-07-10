/**
 * Keyboard shortcuts for project toolbar View / Lists / Format actions.
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyToolbarShortcutsInit) return;
    window._scriptyToolbarShortcutsInit = true;

    function isTypingTarget(el) {
        if (typeof window.scriptyIsTypingContext === 'function') {
            return window.scriptyIsTypingContext(el);
        }
        if (!el) return false;
        var tag = el.tagName;
        return tag === 'TEXTAREA' || tag === 'INPUT' || !!el.isContentEditable;
    }

    function isProjectPage() {
        return !!document.querySelector('.project-script');
    }

    function isMac() {
        return window.scriptyIsMac
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
    }

    function formatHint(letter) {
        return isMac() ? '⌘' + letter : 'Ctrl+' + letter;
    }

    function syncFormatShortcutLabels() {
        if (!isProjectPage()) return;
        document.querySelectorAll('#project-text-format-dropdown .bulk-style-btn[data-bulk-style]').forEach(function (btn) {
            var style = (btn.getAttribute('data-bulk-style') || '').toUpperCase();
            var letter = style === 'BOLD' ? 'B' : style === 'ITALIC' ? 'I' : style === 'UNDERLINE' ? 'U' : '';
            if (!letter) return;
            var base = btn.getAttribute('data-base-title');
            if (!base) {
                base = (btn.getAttribute('aria-label') || btn.title || letter).replace(/\s*\([^)]*\)\s*$/, '');
                btn.setAttribute('data-base-title', base);
            }
            var hint = formatHint(letter);
            var title = base + ' (' + hint + ')';
            btn.title = title;
            btn.setAttribute('aria-label', title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(btn, hint);
            }
        });
    }

    function clickStyleButton(style) {
        var btn = document.querySelector(
            '#project-text-format-dropdown .bulk-style-btn[data-bulk-style="' + style + '"]'
        );
        if (!btn || window.scriptyCanEditScript === false) return false;
        btn.click();
        return true;
    }

    function handleViewOrListShortcut(key) {
        if (key === 'f') {
            if (typeof window.scriptyToggleFocusMode === 'function') {
                window.scriptyToggleFocusMode();
                return true;
            }
            return false;
        }
        if (key === 'o') {
            if (typeof window.scriptyToggleOutlineMode === 'function') {
                window.scriptyToggleOutlineMode();
                return true;
            }
            return false;
        }
        if (key === 'p') {
            if (typeof window.scriptyTogglePageViewMode === 'function') {
                window.scriptyTogglePageViewMode();
                return true;
            }
            return false;
        }
        if (key === 'l') {
            if (typeof window.scriptyToggleFountainOutline === 'function') {
                window.scriptyToggleFountainOutline();
                return true;
            }
            return false;
        }
        return false;
    }

    // View / Lists: ⌘⇧F focus, ⌘⇧O outline mode, ⌘⇧P page view, ⌘⇧L outline navigator.
    // Disabled while typing so they don't fight block editing.
    document.addEventListener('keydown', function (e) {
        if (!(e.metaKey || e.ctrlKey) || !e.shiftKey || e.altKey) return;
        if (!isProjectPage()) return;
        if (isTypingTarget(document.activeElement)) return;

        var key = (e.key || '').toLowerCase();
        if (!handleViewOrListShortcut(key)) return;
        e.preventDefault();
    });

    // Full width: ⌘\ / Ctrl+\ (avoid ⌘⇧W, which closes the browser window).
    document.addEventListener('keydown', function (e) {
        if (!(e.metaKey || e.ctrlKey) || e.altKey || e.shiftKey) return;
        if (!isProjectPage()) return;
        if (isTypingTarget(document.activeElement)) return;
        if (e.key !== '\\' && e.code !== 'Backslash') return;
        if (typeof window.scriptyToggleFullWidth !== 'function') return;
        e.preventDefault();
        window.scriptyToggleFullWidth();
    });

    // Format: ⌘B / ⌘I / ⌘U — works while typing (same as element-type shortcuts).
    document.addEventListener('keydown', function (e) {
        if (!(e.metaKey || e.ctrlKey) || e.altKey || e.shiftKey) return;
        if (!isProjectPage()) return;
        if (window.scriptyCanEditScript === false) return;

        var key = (e.key || '').toLowerCase();
        var style = key === 'b' ? 'BOLD' : key === 'i' ? 'ITALIC' : key === 'u' ? 'UNDERLINE' : null;
        if (!style) return;
        if (!clickStyleButton(style)) return;
        e.preventDefault();
    });

    document.body.addEventListener('htmx:afterSwap', syncFormatShortcutLabels);
    document.body.addEventListener('htmx:afterSettle', syncFormatShortcutLabels);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', syncFormatShortcutLabels);
    } else {
        syncFormatShortcutLabels();
    }
})();
