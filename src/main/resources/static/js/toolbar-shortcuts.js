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
        var isMacPlatform = isMac();

        // Sync style buttons
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

        // Sync alignment buttons
        document.querySelectorAll('#project-text-format-dropdown .bulk-align-btn[data-bulk-align]').forEach(function (btn) {
            var align = (btn.getAttribute('data-bulk-align') || '').toUpperCase();
            var letter = align === 'LEFT' ? 'L' : align === 'CENTER' ? 'E' : align === 'RIGHT' ? 'R' : '';
            if (!letter) return;
            var base = btn.getAttribute('data-base-title');
            if (!base) {
                base = (btn.getAttribute('aria-label') || btn.title || align.toLowerCase()).replace(/\s*\([^)]*\)\s*$/, '');
                btn.setAttribute('data-base-title', base);
            }
            var hint = (isMacPlatform ? '⌘⇧' : 'Ctrl+Shift+') + letter;
            var title = base + ' (' + hint + ')';
            btn.title = title;
            btn.setAttribute('aria-label', title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(btn, hint);
            }
        });

        // Sync spellcheck and word count toggle labels
        var spellcheckHint = isMacPlatform ? '⌘⇧K' : 'Ctrl+Shift+K';
        var wordCountHint = isMacPlatform ? '⌘⇧Y' : 'Ctrl+Shift+Y';

        var spellcheckBtn = document.getElementById('nav-spellcheck-toggle');
        if (spellcheckBtn) {
            spellcheckBtn.title = 'Spellcheck (' + spellcheckHint + ')';
            spellcheckBtn.setAttribute('aria-label', spellcheckBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(spellcheckBtn, spellcheckHint);
            }
        }

        var wordCountBtn = document.getElementById('nav-word-count-toggle');
        if (wordCountBtn) {
            wordCountBtn.title = 'Word count (' + wordCountHint + ')';
            wordCountBtn.setAttribute('aria-label', wordCountBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(wordCountBtn, wordCountHint);
            }
        }
    }

    function clickStyleButton(style) {
        var btn = document.querySelector(
            '#project-text-format-dropdown .bulk-style-btn[data-bulk-style="' + style + '"]'
        );
        if (!btn || window.scriptyCanEditScript === false) return false;
        btn.click();
        return true;
    }

    function clickAlignButton(align) {
        var btn = document.querySelector(
            '#project-text-format-dropdown .bulk-align-btn[data-bulk-align="' + align + '"]'
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
        if (key === 'g') {
            if (typeof window.scriptyToggleFountainOutline === 'function') {
                window.scriptyToggleFountainOutline();
                return true;
            }
            return false;
        }
        if (key === 'k') {
            if (typeof window.toggleSpellcheck === 'function') {
                window.toggleSpellcheck();
                return true;
            }
            return false;
        }
        if (key === 'y') {
            var wcBtn = document.getElementById('nav-word-count-toggle');
            if (wcBtn) {
                wcBtn.click();
                return true;
            }
            return false;
        }
        return false;
    }

    // View / Lists: ⌘⇧F focus, ⌘⇧O outline mode, ⌘⇧P page view, ⌘⇧G outline navigator, ⌘⇧K spellcheck, ⌘⇧Y word count.
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

    // Format Style: ⌘B / ⌘I / ⌘U — works while typing (same as element-type shortcuts).
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

    // Format Alignment: ⌘⇧L / ⌘⇧E / ⌘⇧R — works while typing
    document.addEventListener('keydown', function (e) {
        if (!(e.metaKey || e.ctrlKey) || !e.shiftKey || e.altKey) return;
        if (!isProjectPage()) return;
        if (window.scriptyCanEditScript === false) return;

        var key = (e.key || '').toLowerCase();
        var align = key === 'l' ? 'LEFT' : key === 'e' ? 'CENTER' : key === 'r' ? 'RIGHT' : null;
        if (!align) return;
        if (!clickAlignButton(align)) return;
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
