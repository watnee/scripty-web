/**
 * Find & Replace for the project script editor (Edit → Find & Replace).
 *
 * Finds matches in block content client-side and highlights them; replace
 * actions persist through POST /block/findReplace (undo-able like other
 * script edits). Loaded from nav.html so handlers survive HTMX-boosted
 * navigation into /project/show.
 */
(function () {
    'use strict';

    if (window._scriptyFindReplaceInit) return;
    window._scriptyFindReplaceInit = true;

    var state = {
        open: false,
        matchCase: false,
        wholeWord: false,
        matches: [],
        current: -1,
        busy: false
    };
    var searchDebounce = null;

    function resolveProjectId() {
        return typeof window.scriptyResolveProjectId === 'function'
            ? window.scriptyResolveProjectId()
            : null;
    }

    function resolveEditionId() {
        return typeof window.scriptyResolveEditionId === 'function'
            ? window.scriptyResolveEditionId()
            : (window.scriptyEditionId || null);
    }

    function canEdit() {
        return window.scriptyCanEditScript !== false && !window.scriptyBlockEditLocked;
    }

    function isAvailable() {
        return !!document.getElementById('nav-find-replace') && !!resolveProjectId() && canEdit();
    }

    // ------------------------------------------------------------------
    // Panel

    function getPanel() {
        return document.getElementById('find-replace-panel');
    }

    function buildPanel() {
        var existing = getPanel();
        if (existing) return existing;

        var isMac = typeof window.scriptyIsMac === 'function'
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
        var enterHint = isMac ? '⏎' : 'Enter';

        var panel = document.createElement('div');
        panel.id = 'find-replace-panel';
        panel.className = 'find-replace-panel';
        panel.setAttribute('role', 'dialog');
        panel.setAttribute('aria-label', 'Find and replace');
        panel.hidden = true;
        panel.innerHTML =
            '<div class="fr-row">' +
                '<input type="text" id="fr-find" class="fr-input" placeholder="Find" autocomplete="off" ' +
                    'aria-label="Find text" title="Find (' + enterHint + ' next, Shift+' + enterHint + ' previous)" />' +
                '<button type="button" id="fr-match-case" class="fr-toggle" aria-pressed="false" ' +
                    'title="Match case" aria-label="Match case">Aa</button>' +
                '<button type="button" id="fr-whole-word" class="fr-toggle" aria-pressed="false" ' +
                    'title="Match whole word" aria-label="Match whole word"><u>ab</u></button>' +
                '<span id="fr-count" class="fr-count" aria-live="polite">0/0</span>' +
                '<button type="button" id="fr-prev" class="fr-nav-btn" title="Previous match" aria-label="Previous match">↑</button>' +
                '<button type="button" id="fr-next" class="fr-nav-btn" title="Next match" aria-label="Next match">↓</button>' +
                '<button type="button" id="fr-close" class="fr-close" title="Close (Esc)" aria-label="Close find and replace">×</button>' +
            '</div>' +
            '<div class="fr-row">' +
                '<input type="text" id="fr-replace" class="fr-input" placeholder="Replace with" autocomplete="off" ' +
                    'aria-label="Replace with" />' +
                '<button type="button" id="fr-replace-one" class="fr-action-btn" title="Replace current match">Replace</button>' +
                '<button type="button" id="fr-replace-all" class="fr-action-btn" title="Replace all matches">All</button>' +
            '</div>' +
            '<div id="fr-status" class="fr-status" aria-live="polite"></div>';
        document.body.appendChild(panel);
        wirePanel(panel);
        return panel;
    }

    function setStatus(text) {
        var status = document.getElementById('fr-status');
        if (status) {
            status.textContent = text || '';
            status.hidden = !text;
        }
    }

    function updateCount() {
        var count = document.getElementById('fr-count');
        if (!count) return;
        var total = state.matches.length;
        count.textContent = total ? (state.current + 1) + '/' + total : '0/0';
        var findInput = document.getElementById('fr-find');
        var noHits = !!(findInput && findInput.value && !total);
        count.classList.toggle('fr-no-matches', noHits);
        ['fr-prev', 'fr-next', 'fr-replace-one', 'fr-replace-all'].forEach(function (id) {
            var btn = document.getElementById(id);
            if (btn) btn.disabled = !total || state.busy;
        });
    }

    // ------------------------------------------------------------------
    // Matching + highlighting

    function contentElementFor(cell) {
        if (!cell) return null;
        // Skip blocks currently in inline-edit mode (textarea owns the text).
        if (cell.querySelector(':scope > form.block-inline-edit-form, :scope > form')) return null;
        var el = cell.querySelector(
            ':scope > h2.script-block-text, :scope > h3.script-block-text, ' +
            ':scope > p.script-block-text:not(.script-character-name), :scope > span.script-block-text, :scope > span:not([class])'
        );
        return el;
    }

    function eachContentElement(fn) {
        document.querySelectorAll('.project-script .scene-blocks .block-row[data-block-id]').forEach(function (row) {
            var el = contentElementFor(row.querySelector('.block-content'));
            if (el) fn(row, el);
        });
    }

    function clearHighlights() {
        document.querySelectorAll('mark.fr-match').forEach(function (mark) {
            var parent = mark.parentNode;
            if (!parent) return;
            parent.replaceChild(document.createTextNode(mark.textContent), mark);
            parent.normalize();
        });
        state.matches = [];
        state.current = -1;
    }

    function escapeRegex(text) {
        return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    var wordChar = null;
    try { wordChar = new RegExp('[\\p{L}\\p{N}_]', 'u'); } catch (err) { wordChar = /[A-Za-z0-9_]/; }

    function isWholeWordMatch(text, start, end) {
        var before = start > 0 ? text.charAt(start - 1) : '';
        var after = end < text.length ? text.charAt(end) : '';
        return (!before || !wordChar.test(before)) && (!after || !wordChar.test(after));
    }

    function findRanges(text, query) {
        var ranges = [];
        if (!query) return ranges;
        var re = new RegExp(escapeRegex(query), state.matchCase ? 'g' : 'gi');
        var m;
        while ((m = re.exec(text)) !== null) {
            var start = m.index;
            var end = start + m[0].length;
            if (!state.wholeWord || isWholeWordMatch(text, start, end)) {
                ranges.push({ start: start, end: end });
            }
            if (m.index === re.lastIndex) re.lastIndex++; // safety for empty match
        }
        return ranges;
    }

    function refreshMatches(preserveIndex) {
        var findInput = document.getElementById('fr-find');
        var query = findInput ? findInput.value : '';
        var previous = preserveIndex ? state.current : -1;
        clearHighlights();
        if (!query) {
            updateCount();
            return;
        }
        eachContentElement(function (row, el) {
            var text = el.textContent;
            var ranges = findRanges(text, query);
            if (!ranges.length) return;
            var fragment = document.createDocumentFragment();
            var cursor = 0;
            var occurrence = 0;
            ranges.forEach(function (range) {
                occurrence++;
                if (range.start > cursor) {
                    fragment.appendChild(document.createTextNode(text.slice(cursor, range.start)));
                }
                var mark = document.createElement('mark');
                mark.className = 'fr-match';
                mark.textContent = text.slice(range.start, range.end);
                fragment.appendChild(mark);
                state.matches.push({
                    row: row,
                    el: el,
                    mark: mark,
                    blockId: row.getAttribute('data-block-id'),
                    occurrence: occurrence
                });
                cursor = range.end;
            });
            if (cursor < text.length) {
                fragment.appendChild(document.createTextNode(text.slice(cursor)));
            }
            el.textContent = '';
            el.appendChild(fragment);
        });
        if (state.matches.length) {
            var index = previous >= 0 ? Math.min(previous, state.matches.length - 1) : 0;
            setCurrent(index, previous < 0);
        }
        updateCount();
    }

    function setCurrent(index, skipScroll) {
        if (!state.matches.length) {
            state.current = -1;
            updateCount();
            return;
        }
        if (state.current >= 0 && state.matches[state.current]) {
            state.matches[state.current].mark.classList.remove('fr-current');
        }
        state.current = ((index % state.matches.length) + state.matches.length) % state.matches.length;
        var match = state.matches[state.current];
        match.mark.classList.add('fr-current');
        if (!skipScroll) {
            match.mark.scrollIntoView({ block: 'center', behavior: 'smooth' });
        }
        updateCount();
    }

    function move(delta) {
        if (!state.matches.length) return;
        setCurrent(state.current + delta);
    }

    // ------------------------------------------------------------------
    // Replace (server-backed)

    function postReplace(params) {
        var body = new URLSearchParams();
        Object.keys(params).forEach(function (key) {
            if (params[key] !== null && params[key] !== undefined) {
                body.append(key, params[key]);
            }
        });
        return fetch('/block/findReplace', {
            method: 'POST',
            credentials: 'same-origin',
            cache: 'no-store',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body.toString()
        }).then(function (response) {
            if (!response.ok) throw new Error('Replace failed (' + response.status + ')');
            return response.json();
        });
    }

    function applyServerContents(blocks) {
        (blocks || []).forEach(function (entry) {
            var row = document.querySelector(
                '.project-script .scene-blocks .block-row[data-block-id="' + entry.id + '"]'
            );
            if (!row) return;
            var el = contentElementFor(row.querySelector('.block-content'));
            if (el) el.textContent = entry.content;
        });
    }

    function afterReplace() {
        var projectId = resolveProjectId();
        if (projectId && typeof window.scriptyUpdateUndoRedoButtons === 'function') {
            window.scriptyUpdateUndoRedoButtons(projectId);
        }
        if (typeof window.scriptyReflowPageView === 'function') {
            window.scriptyReflowPageView();
        }
    }

    function baseParams() {
        var findInput = document.getElementById('fr-find');
        var replaceInput = document.getElementById('fr-replace');
        return {
            projectId: resolveProjectId(),
            editionId: resolveEditionId(),
            find: findInput ? findInput.value : '',
            replace: replaceInput ? replaceInput.value : '',
            matchCase: state.matchCase,
            wholeWord: state.wholeWord
        };
    }

    function setBusy(busy) {
        state.busy = busy;
        updateCount();
    }

    function replaceCurrent() {
        if (state.busy || state.current < 0 || !state.matches[state.current]) return;
        var match = state.matches[state.current];
        var params = baseParams();
        if (!params.find || !params.projectId) return;
        params.blockId = match.blockId;
        params.occurrence = match.occurrence;
        var resumeAt = state.current;
        setBusy(true);
        postReplace(params).then(function (data) {
            applyServerContents(data.blocks);
            setBusy(false);
            refreshMatches(false);
            if (state.matches.length) {
                // The next match now occupies the replaced match's index
                // (setCurrent wraps to the start when it was the last one).
                setCurrent(resumeAt);
            }
            setStatus(data.replacements ? '' : 'Nothing replaced');
            afterReplace();
        }).catch(function (err) {
            setBusy(false);
            setStatus(err.message || 'Replace failed');
        });
    }

    function replaceAll() {
        if (state.busy || !state.matches.length) return;
        var params = baseParams();
        if (!params.find || !params.projectId) return;
        setBusy(true);
        postReplace(params).then(function (data) {
            applyServerContents(data.blocks);
            setBusy(false);
            refreshMatches(false);
            setStatus(data.replacements === 1
                ? 'Replaced 1 occurrence'
                : 'Replaced ' + data.replacements + ' occurrences');
            afterReplace();
        }).catch(function (err) {
            setBusy(false);
            setStatus(err.message || 'Replace failed');
        });
    }

    // ------------------------------------------------------------------
    // Open / close

    function openPanel() {
        if (!isAvailable()) return;
        var panel = buildPanel();
        panel.hidden = false;
        state.open = true;
        var findInput = document.getElementById('fr-find');
        var selection = window.getSelection ? String(window.getSelection()) : '';
        if (findInput) {
            if (selection && selection.indexOf('\n') === -1 && selection.trim()) {
                findInput.value = selection;
            }
            setTimeout(function () {
                findInput.focus();
                findInput.select();
            }, 0);
        }
        setStatus('');
        refreshMatches(false);
    }

    function closePanel() {
        var panel = getPanel();
        if (panel) panel.hidden = true;
        state.open = false;
        clearHighlights();
        updateCount();
        setStatus('');
    }

    function closeEditDropdown() {
        var dropdown = document.getElementById('history-dropdown');
        if (!dropdown) return;
        dropdown.classList.remove('open');
        var toggle = dropdown.querySelector('.nav-dropdown-toggle');
        if (toggle) toggle.setAttribute('aria-expanded', 'false');
    }

    // ------------------------------------------------------------------
    // Wiring

    function wirePanel(panel) {
        panel.addEventListener('click', function (e) {
            var target = e.target.closest ? e.target : null;
            if (!target) return;
            if (target.closest('#fr-close')) { closePanel(); return; }
            if (target.closest('#fr-prev')) { move(-1); return; }
            if (target.closest('#fr-next')) { move(1); return; }
            if (target.closest('#fr-replace-one')) { replaceCurrent(); return; }
            if (target.closest('#fr-replace-all')) { replaceAll(); return; }
            var caseBtn = target.closest('#fr-match-case');
            var wordBtn = target.closest('#fr-whole-word');
            if (caseBtn || wordBtn) {
                if (caseBtn) state.matchCase = !state.matchCase;
                if (wordBtn) state.wholeWord = !state.wholeWord;
                var btn = caseBtn || wordBtn;
                var pressed = caseBtn ? state.matchCase : state.wholeWord;
                btn.classList.toggle('fr-toggle-on', pressed);
                btn.setAttribute('aria-pressed', pressed ? 'true' : 'false');
                setStatus('');
                refreshMatches(false);
            }
        });

        panel.addEventListener('input', function (e) {
            if (e.target && e.target.id === 'fr-find') {
                setStatus('');
                if (searchDebounce) clearTimeout(searchDebounce);
                searchDebounce = setTimeout(function () {
                    refreshMatches(false);
                }, 150);
            }
        });

        panel.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                e.preventDefault();
                closePanel();
                return;
            }
            if (e.key !== 'Enter') return;
            e.preventDefault();
            if (e.target && e.target.id === 'fr-replace') {
                replaceCurrent();
            } else {
                move(e.shiftKey ? -1 : 1);
            }
        });
    }

    function syncMenuItem() {
        var menuItem = document.getElementById('nav-find-replace');
        if (!menuItem) return;
        var isMac = typeof window.scriptyIsMac === 'function'
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
        var hint = isMac ? '⌥⌘F' : 'Ctrl+H';
        if (typeof window.scriptySetMenuShortcut === 'function') {
            window.scriptySetMenuShortcut(menuItem, hint);
        }
        menuItem.title = 'Find and replace (' + hint + ')';
        menuItem.setAttribute('aria-label', 'Find and replace (' + hint + ')');
    }

    document.body.addEventListener('click', function (e) {
        if (!e.target || !e.target.closest) return;
        var menuItem = e.target.closest('#nav-find-replace');
        if (!menuItem) return;
        e.preventDefault();
        e.stopPropagation();
        closeEditDropdown();
        openPanel();
    });

    document.addEventListener('keydown', function (e) {
        if (!isAvailable()) return;
        var isMac = typeof window.scriptyIsMac === 'function'
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
        var wantsOpen = isMac
            ? (e.metaKey && e.altKey && !e.ctrlKey && !e.shiftKey && e.code === 'KeyF')
            : (e.ctrlKey && !e.metaKey && !e.altKey && !e.shiftKey && e.key.toLowerCase() === 'h');
        if (!wantsOpen) return;
        e.preventDefault();
        openPanel();
    });

    function sync() {
        syncMenuItem();
        if (!document.getElementById('nav-find-replace')) {
            // Navigated away from an editable project page.
            if (state.open) closePanel();
            return;
        }
        if (state.open) {
            refreshMatches(true);
        }
    }

    document.body.addEventListener('htmx:afterSettle', sync);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sync);
    } else {
        sync();
    }
})();
