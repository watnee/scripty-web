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

        // Sync Import
        var importBtn = document.getElementById('nav-import');
        if (importBtn) {
            var importHint = isMacPlatform ? '⌘⇧I' : 'Ctrl+Shift+I';
            importBtn.title = 'Import (' + importHint + ')';
            importBtn.setAttribute('aria-label', importBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(importBtn, importHint);
            }
        }

        // Sync Title page
        var titlePageBtn = document.querySelector('a[href*="/project/titlePage"]');
        if (titlePageBtn) {
            var titlePageHint = isMacPlatform ? '⌘⇧T' : 'Ctrl+Shift+T';
            titlePageBtn.title = 'Title page (' + titlePageHint + ')';
            titlePageBtn.setAttribute('aria-label', titlePageBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(titlePageBtn, titlePageHint);
            }
        }

        // Sync exports
        var pdfBtn = document.querySelector('a[href*="format=pdf"]');
        if (pdfBtn) {
            var pdfHint = isMacPlatform ? '⌘⇧1' : 'Ctrl+Shift+1';
            pdfBtn.title = 'PDF (' + pdfHint + ')';
            pdfBtn.setAttribute('aria-label', pdfBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(pdfBtn, pdfHint);
            }
        }
        var docxBtn = document.querySelector('a[href*="format=docx"]');
        if (docxBtn) {
            var docxHint = isMacPlatform ? '⌘⇧2' : 'Ctrl+Shift+2';
            docxBtn.title = 'Word (.docx) (' + docxHint + ')';
            docxBtn.setAttribute('aria-label', docxBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(docxBtn, docxHint);
            }
        }
        var fdxBtn = document.querySelector('a[href*="format=fdx"]');
        if (fdxBtn) {
            var fdxHint = isMacPlatform ? '⌘⇧3' : 'Ctrl+Shift+3';
            fdxBtn.title = 'Final Draft (.fdx) (' + fdxHint + ')';
            fdxBtn.setAttribute('aria-label', fdxBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(fdxBtn, fdxHint);
            }
        }
        var fountainBtn = document.querySelector('a[href*="format=fountain"]');
        if (fountainBtn) {
            var fountainHint = isMacPlatform ? '⌘⇧4' : 'Ctrl+Shift+4';
            fountainBtn.title = 'Fountain (' + fountainHint + ')';
            fountainBtn.setAttribute('aria-label', fountainBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(fountainBtn, fountainHint);
            }
        }

        // Sync Print
        var printBtn = document.getElementById('nav-print');
        if (printBtn) {
            var printHint = isMacPlatform ? '⌘P' : 'Ctrl+P';
            printBtn.title = 'Print (' + printHint + ')';
            printBtn.setAttribute('aria-label', printBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(printBtn, printHint);
            }
        }

        // Sync Character list
        var charListBtn = document.getElementById('nav-character-list-toggle');
        if (charListBtn) {
            var charListHint = isMacPlatform ? '⌘⇧C' : 'Ctrl+Shift+C';
            charListBtn.title = 'Character list (' + charListHint + ')';
            charListBtn.setAttribute('aria-label', charListBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(charListBtn, charListHint);
            }
        }

        // Sync Location list
        var locListBtn = document.getElementById('nav-location-list-toggle');
        if (locListBtn) {
            var locListHint = isMacPlatform ? '⌘⇧A' : 'Ctrl+Shift+A';
            locListBtn.title = 'Location list (' + locListHint + ')';
            locListBtn.setAttribute('aria-label', locListBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(locListBtn, locListHint);
            }
        }

        // Sync Song list
        var songListBtn = document.getElementById('nav-song-list-toggle');
        if (songListBtn) {
            var songListHint = isMacPlatform ? '⌘⇧M' : 'Ctrl+Shift+M';
            songListBtn.title = 'Song list (' + songListHint + ')';
            songListBtn.setAttribute('aria-label', songListBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(songListBtn, songListHint);
            }
        }

        // Sync Read Script
        var readScriptBtn = document.querySelector('a[href*="/project/read"]');
        if (readScriptBtn) {
            var readScriptHint = isMacPlatform ? '⌘⇧X' : 'Ctrl+Shift+X';
            readScriptBtn.title = 'Read Script (' + readScriptHint + ')';
            readScriptBtn.setAttribute('aria-label', readScriptBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(readScriptBtn, readScriptHint);
            }
        }

        // Sync New version...
        var newVersionBtn = document.getElementById('script-edition-create-open');
        if (newVersionBtn) {
            var newVersionHint = isMacPlatform ? '⌘⇧J' : 'Ctrl+Shift+J';
            newVersionBtn.title = 'New version… (' + newVersionHint + ')';
            newVersionBtn.setAttribute('aria-label', newVersionBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(newVersionBtn, newVersionHint);
            }
        }

        // Sync Snapshot history
        var versionHistoryBtn = document.getElementById('nav-version-history');
        if (versionHistoryBtn) {
            var versionHistoryHint = isMacPlatform ? '⌘⇧H' : 'Ctrl+Shift+H';
            versionHistoryBtn.title = 'Snapshot history (' + versionHistoryHint + ')';
            versionHistoryBtn.setAttribute('aria-label', versionHistoryBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(versionHistoryBtn, versionHistoryHint);
            }
        }

        // Sync Focus Mode
        var focusBtn = document.getElementById('focus-toggle');
        if (focusBtn) {
            var focusHint = isMacPlatform ? '⌘⇧F' : 'Ctrl+Shift+F';
            focusBtn.title = 'Focus mode (' + focusHint + ')';
            focusBtn.setAttribute('aria-label', focusBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(focusBtn, focusHint);
            }
        }

        // Sync Outline Mode
        var outlineModeBtn = document.getElementById('outline-mode-toggle');
        if (outlineModeBtn) {
            var outlineModeHint = isMacPlatform ? '⌘⇧O' : 'Ctrl+Shift+O';
            outlineModeBtn.title = 'Outline mode (' + outlineModeHint + ')';
            outlineModeBtn.setAttribute('aria-label', outlineModeBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(outlineModeBtn, outlineModeHint);
            }
        }

        // Sync Page View Mode
        var pageViewModeBtn = document.getElementById('page-view-mode-toggle');
        if (pageViewModeBtn) {
            var pageViewModeHint = isMacPlatform ? '⌘⇧P' : 'Ctrl+Shift+P';
            pageViewModeBtn.title = 'Page view (' + pageViewModeHint + ')';
            pageViewModeBtn.setAttribute('aria-label', pageViewModeBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(pageViewModeBtn, pageViewModeHint);
            }
        }

        // Sync Full Width
        var fullWidthBtn = document.getElementById('nav-full-width-toggle');
        if (fullWidthBtn) {
            var fullWidthHint = isMacPlatform ? '⌘\\' : 'Ctrl+\\';
            fullWidthBtn.title = 'Full width (' + fullWidthHint + ')';
            fullWidthBtn.setAttribute('aria-label', fullWidthBtn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(fullWidthBtn, fullWidthHint);
            }
        }

        // Sync Bookmarks
        var bookmarksToggle = document.getElementById('nav-bookmarks-toggle');
        if (bookmarksToggle) {
            var bookmarksHint = isMacPlatform ? '⌘⇧B' : 'Ctrl+Shift+B';
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(bookmarksToggle, bookmarksHint);
            }
        }

        // Sync Pins
        var pinsToggle = document.getElementById('nav-pins-toggle');
        if (pinsToggle) {
            var pinsHint = isMacPlatform ? '⌘⇧N' : 'Ctrl+Shift+N';
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(pinsToggle, pinsHint);
            }
        }

        // Sync Select (Block Controls)
        var selectToggle = document.getElementById('nav-select-toggle');
        if (selectToggle) {
            var selectHint = isMacPlatform ? '⌘⇧V' : 'Ctrl+Shift+V';
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(selectToggle, selectHint);
            }
        }

        // Sync Element Labels
        var elementLabelsToggle = document.getElementById('nav-element-labels-toggle');
        if (elementLabelsToggle) {
            var elementLabelsHint = isMacPlatform ? '⌘⇧U' : 'Ctrl+Shift+U';
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(elementLabelsToggle, elementLabelsHint);
            }
        }

        // Sync Lock version
        var lockToggle = document.getElementById('nav-lock-toggle');
        if (lockToggle) {
            var lockHint = isMacPlatform ? '⌘⇧Q' : 'Ctrl+Shift+Q';
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(lockToggle, lockHint);
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
        if (key === 'b') {
            var bookmarksToggle = document.getElementById('nav-bookmarks-toggle');
            if (bookmarksToggle) {
                bookmarksToggle.click();
                return true;
            }
            return false;
        }
        if (key === 'n') {
            var pinsToggle = document.getElementById('nav-pins-toggle');
            if (pinsToggle) {
                pinsToggle.click();
                return true;
            }
            return false;
        }
        if (key === 'v') {
            var selectToggle = document.getElementById('nav-select-toggle');
            if (selectToggle) {
                selectToggle.click();
                return true;
            }
            return false;
        }
        if (key === 'u') {
            var elementLabelsToggle = document.getElementById('nav-element-labels-toggle');
            if (elementLabelsToggle) {
                elementLabelsToggle.click();
                return true;
            }
            return false;
        }
        if (key === 'q') {
            var lockToggle = document.getElementById('nav-lock-toggle');
            if (lockToggle) {
                lockToggle.click();
                return true;
            }
            return false;
        }
        if (key === 'c') {
            var charListBtn = document.getElementById('nav-character-list-toggle');
            if (charListBtn) {
                charListBtn.click();
                return true;
            }
            return false;
        }
        if (key === 'a') {
            var locListBtn = document.getElementById('nav-location-list-toggle');
            if (locListBtn) {
                locListBtn.click();
                return true;
            }
            return false;
        }
        if (key === 'm') {
            var songListBtn = document.getElementById('nav-song-list-toggle');
            if (songListBtn) {
                songListBtn.click();
                return true;
            }
            return false;
        }
        if (key === 'i') {
            var importBtn = document.getElementById('nav-import');
            if (importBtn) {
                importBtn.click();
                return true;
            }
            return false;
        }
        if (key === 't') {
            var titlePageBtn = document.querySelector('a[href*="/project/titlePage"]');
            if (titlePageBtn) {
                titlePageBtn.click();
                return true;
            }
            return false;
        }
        if (key === 'x') {
            var readScriptBtn = document.querySelector('a[href*="/project/read"]');
            if (readScriptBtn) {
                readScriptBtn.click();
                return true;
            }
            return false;
        }
        if (key === 'j') {
            var newVersionBtn = document.getElementById('script-edition-create-open');
            if (newVersionBtn) {
                newVersionBtn.click();
                return true;
            }
            return false;
        }
        if (key === 'h') {
            var versionHistoryBtn = document.getElementById('nav-version-history');
            if (versionHistoryBtn) {
                versionHistoryBtn.click();
                return true;
            }
            return false;
        }
        if (key === '1') {
            var pdfBtn = document.querySelector('a[href*="format=pdf"]');
            if (pdfBtn) {
                pdfBtn.click();
                return true;
            }
            return false;
        }
        if (key === '2') {
            var docxBtn = document.querySelector('a[href*="format=docx"]');
            if (docxBtn) {
                docxBtn.click();
                return true;
            }
            return false;
        }
        if (key === '3') {
            var fdxBtn = document.querySelector('a[href*="format=fdx"]');
            if (fdxBtn) {
                fdxBtn.click();
                return true;
            }
            return false;
        }
        if (key === '4') {
            var fountainBtn = document.querySelector('a[href*="format=fountain"]');
            if (fountainBtn) {
                fountainBtn.click();
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

    // Print: ⌘P / Ctrl+P
    document.addEventListener('keydown', function (e) {
        if (!(e.metaKey || e.ctrlKey) || e.altKey || e.shiftKey) return;
        if (!isProjectPage()) return;
        if (isTypingTarget(document.activeElement)) return;
        var key = (e.key || '').toLowerCase();
        if (key !== 'p') return;
        e.preventDefault();
        var printBtn = document.getElementById('nav-print');
        if (printBtn) {
            printBtn.click();
        } else {
            window.print();
        }
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
