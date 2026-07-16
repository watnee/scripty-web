/**
 * Song editor Edit menu: Undo, Redo, and Search over lyric lines.
 *
 * The screenplay equivalents (undo-redo.js, project-search.js) are bound to the
 * project toolbar's ids, so the song editor gets its own copy here against
 * /song/block/{undo,redo,undoRedoStatus} and the #song-blocks rows.
 *
 * Loaded from nav.html so it survives HTMX-boosted navigation into the song
 * editor (allowScriptTags is false, so edit.html script tags do not run).
 */
(function () {
    'use strict';

    if (window._scriptySongEditMenuInit) return;
    window._scriptySongEditMenuInit = true;

    function dropdown() {
        return document.getElementById('song-history-dropdown');
    }

    function documentId() {
        var d = dropdown();
        return d ? d.getAttribute('data-document-id') : null;
    }

    function searchDropdown() {
        return document.getElementById('song-search-dropdown');
    }

    function searchInput() {
        return document.getElementById('song-search');
    }

    function isMac() {
        return typeof window.scriptyIsMac === 'function'
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
    }

    function closeEditDropdown() {
        var d = dropdown();
        if (!d) return;
        d.classList.remove('open');
        var toggle = d.querySelector('.nav-dropdown-toggle');
        if (toggle) toggle.setAttribute('aria-expanded', 'false');
    }

    // --- undo / redo -----------------------------------------------------

    function setButtonState(btn, available) {
        if (!btn) return;
        btn.disabled = false;
        btn.classList.toggle('is-unavailable', !available);
        btn.setAttribute('aria-disabled', available ? 'false' : 'true');
    }

    function refreshButtons() {
        var undoBtn = document.getElementById('song-nav-undo');
        var redoBtn = document.getElementById('song-nav-redo');
        var docId = documentId();
        if (!undoBtn || !redoBtn || !docId) return;

        fetch('/song/block/undoRedoStatus?documentId=' + encodeURIComponent(docId), {
            cache: 'no-store',
            credentials: 'same-origin'
        })
            .then(function (res) {
                if (!res.ok) return Promise.reject(res);
                return res.json();
            })
            .then(function (data) {
                setButtonState(undoBtn, data.canUndo);
                setButtonState(redoBtn, data.canRedo);
            })
            .catch(function () {
                setButtonState(undoBtn, false);
                setButtonState(redoBtn, false);
            });
    }

    function replaceList(html) {
        // song-blocks.js owns the textareas, so it does the swap: it re-baselines
        // the saved content and re-measures row heights.
        if (typeof window.scriptySongBlocksReplaceList === 'function') {
            window.scriptySongBlocksReplaceList(html);
        }
        performSearch();
    }

    function performHistoryAction(action) {
        closeEditDropdown();
        var docId = documentId();
        if (!docId) return;

        // Save whatever line is being edited first, so its text is part of the
        // snapshot the server is about to restore over.
        var active = document.activeElement;
        if (active && active.classList && active.classList.contains('song-block-textarea')) {
            active.blur();
        }

        fetch('/song/block/' + action, {
            method: 'POST',
            credentials: 'same-origin',
            cache: 'no-store',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
            body: 'documentId=' + encodeURIComponent(docId)
        })
            .then(function (res) {
                if (!res.ok) return Promise.reject(res);
                return res.text();
            })
            .then(replaceList)
            .catch(function () { /* leave the list as-is */ })
            .then(refreshButtons);
    }

    // --- search ----------------------------------------------------------

    function setSearchOpen(open) {
        var sd = searchDropdown();
        var input = searchInput();
        var menuItem = document.getElementById('song-nav-search');
        if (!sd || !input) return;
        sd.classList.toggle('is-open', !!open);
        if (menuItem) {
            menuItem.setAttribute('aria-expanded', open ? 'true' : 'false');
        }
        input.tabIndex = open ? 0 : -1;
        if (!open && !input.value) {
            sd.classList.remove('has-value');
        }
    }

    function isSearchOpen() {
        var sd = searchDropdown();
        return !!(sd && sd.classList.contains('is-open'));
    }

    function focusSearch() {
        var input = searchInput();
        if (!input) return;
        closeEditDropdown();
        setSearchOpen(true);
        setTimeout(function () {
            input.focus();
            input.select();
        }, 0);
    }

    function closeSearch() {
        var input = searchInput();
        var menuItem = document.getElementById('song-nav-search');
        if (input && input.value) {
            // Keep the field open while a query is filtering the lines.
            setSearchOpen(true);
            input.blur();
            return;
        }
        setSearchOpen(false);
        if (input) input.blur();
        if (menuItem) menuItem.focus();
    }

    function performSearch() {
        var input = searchInput();
        if (!input) return;
        var query = input.value.toLowerCase().trim();
        var clearBtn = document.getElementById('song-search-clear');
        var sd = searchDropdown();
        if (clearBtn) {
            clearBtn.hidden = !query;
        }
        if (sd) {
            sd.classList.toggle('has-value', !!query);
            if (query) setSearchOpen(true);
        }
        document.querySelectorAll('#song-blocks .song-block-row[data-block-id]').forEach(function (row) {
            var ta = row.querySelector('.song-block-textarea');
            var text = ta ? ta.value.toLowerCase() : '';
            row.classList.toggle('filtered-out', !!query && text.indexOf(query) === -1);
        });
    }

    // --- wiring ----------------------------------------------------------

    function syncLabels() {
        var undoBtn = document.getElementById('song-nav-undo');
        var redoBtn = document.getElementById('song-nav-redo');
        var searchItem = document.getElementById('song-nav-search');
        var mac = isMac();
        var hints = {
            undo: mac ? '⌘Z' : 'Ctrl+Z',
            redo: mac ? '⌘⇧Z' : 'Ctrl+Y',
            search: mac ? '⌘F' : 'Ctrl+F'
        };
        [[undoBtn, 'Undo', hints.undo], [redoBtn, 'Redo', hints.redo], [searchItem, 'Search lyrics', hints.search]]
            .forEach(function (entry) {
                var el = entry[0];
                if (!el) return;
                if (typeof window.scriptySetMenuShortcut === 'function') {
                    window.scriptySetMenuShortcut(el, entry[2]);
                }
                el.title = entry[1] + ' (' + entry[2] + ')';
                el.setAttribute('aria-label', entry[1] + ' (' + entry[2] + ')');
            });
        var input = searchInput();
        if (input) {
            input.title = 'Search lyrics (' + hints.search + ')';
            input.setAttribute('aria-label', 'Search lyrics (' + hints.search + ')');
        }
    }

    document.addEventListener('click', function (e) {
        if (!e.target || !e.target.closest) return;

        var toggle = e.target.closest('#song-nav-edit');
        if (toggle) {
            e.preventDefault();
            e.stopPropagation();
            var d = dropdown();
            if (!d) return;
            var open = d.classList.contains('open');
            document.querySelectorAll('.nav-dropdown').forEach(function (other) {
                other.classList.remove('open');
            });
            d.classList.toggle('open', !open);
            toggle.setAttribute('aria-expanded', open ? 'false' : 'true');
            return;
        }

        if (e.target.closest('#song-nav-undo')) {
            e.preventDefault();
            performHistoryAction('undo');
            return;
        }
        if (e.target.closest('#song-nav-redo')) {
            e.preventDefault();
            performHistoryAction('redo');
            return;
        }
        if (e.target.closest('#song-nav-search')) {
            e.preventDefault();
            e.stopPropagation();
            var input = searchInput();
            if (isSearchOpen() && !(input && input.value)) {
                closeEditDropdown();
                closeSearch();
            } else {
                focusSearch();
            }
            return;
        }
        if (e.target.closest('#song-search-clear')) {
            e.preventDefault();
            var clearInput = searchInput();
            if (!clearInput) return;
            clearInput.value = '';
            performSearch();
            clearInput.focus();
            return;
        }

        var d2 = dropdown();
        if (d2 && !d2.contains(e.target)) {
            closeEditDropdown();
        }
        var sd = searchDropdown();
        if (sd && isSearchOpen() && !sd.contains(e.target)) {
            var openInput = searchInput();
            if (openInput && !openInput.value) {
                setSearchOpen(false);
            }
        }
    });

    document.addEventListener('input', function (e) {
        if (e.target && e.target.id === 'song-search') {
            performSearch();
        }
        // A line's own text changed: keep an active filter honest.
        if (e.target && e.target.classList && e.target.classList.contains('song-block-textarea')) {
            var input = searchInput();
            if (input && input.value) performSearch();
        }
    });

    document.addEventListener('keydown', function (e) {
        if (!dropdown()) return; // not the song editor

        var input = searchInput();
        if (e.key === 'Escape' && input && (document.activeElement === input || isSearchOpen())) {
            if (input.value) {
                input.value = '';
                performSearch();
                input.focus();
            } else {
                closeSearch();
            }
            e.preventDefault();
            return;
        }

        if (!(e.metaKey || e.ctrlKey) || e.altKey) return;
        var key = e.key.toLowerCase();

        if (key === 'f' && !e.shiftKey && input) {
            e.preventDefault();
            focusSearch();
            return;
        }

        var action = null;
        if (key === 'z') {
            action = e.shiftKey ? 'redo' : 'undo';
        } else if (key === 'y' && (e.ctrlKey || !isMac())) {
            action = 'redo';
        }
        if (!action) return;
        // Inside a line, hand ⌘Z to the browser so typing undo still works;
        // the menu (and ⌘Z outside a line) walks the server-side history.
        var active = document.activeElement;
        if (active && active.classList && active.classList.contains('song-block-textarea')) {
            return;
        }
        e.preventDefault();
        performHistoryAction(action);
    });

    function init() {
        if (!dropdown()) return;
        syncLabels();
        var input = searchInput();
        setSearchOpen(!!(input && input.value));
        performSearch();
        refreshButtons();
    }

    document.body.addEventListener('htmx:afterSettle', init);
    document.body.addEventListener('htmx:afterSwap', init);
    document.body.addEventListener('htmx:historyRestore', init);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
