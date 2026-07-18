/**
 * Project script search and find & replace (Edit → Search / Find & Replace,
 * plus the expandable field in the toolbar).
 *
 * Two modes share one field, because they want opposite things from the page:
 *   filter  — hides non-matching rows (the original search behaviour)
 *   replace — leaves every row visible and walks match-by-match with highlights
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyProjectSearchInit) return;
    window._scriptyProjectSearchInit = true;

    // Cue content mirrors the character list, so replace skips it unless asked.
    var CUE_TYPES = ['CHARACTER', 'DUAL_DIALOGUE'];
    // Only show-mode elements; a block being edited has a textarea instead.
    var TEXT_SELECTOR = '.block-content > p.script-block-text,'
        + ' .block-content > h2.script-block-text,'
        + ' .block-content > h3.script-block-text';
    var RESTORE_KEY = 'scriptyFindReplaceState';

    var matches = [];
    var currentIndex = -1;

    function getDropdown() {
        return document.getElementById('project-search-dropdown');
    }

    function getInput() {
        return document.getElementById('project-search');
    }

    function getClearBtn() {
        return document.getElementById('project-search-clear');
    }

    function getMenuItem() {
        return document.getElementById('nav-search');
    }

    function getReplaceInput() {
        return document.getElementById('project-replace');
    }

    function getMode() {
        var dropdown = getDropdown();
        return (dropdown && dropdown.getAttribute('data-mode')) === 'replace' ? 'replace' : 'filter';
    }

    function isReplaceMode() {
        return getMode() === 'replace';
    }

    function optionChecked(id) {
        var el = document.getElementById(id);
        return !!(el && el.checked);
    }

    function setMode(mode) {
        var dropdown = getDropdown();
        if (!dropdown) return;
        var replace = mode === 'replace';
        dropdown.setAttribute('data-mode', replace ? 'replace' : 'filter');
        ['project-replace-wrap', 'project-replace-options', 'project-search-nav', 'project-search-count']
            .forEach(function (id) {
                var el = document.getElementById(id);
                if (el) el.hidden = !replace;
            });
        // Leaving replace mode must drop its highlights; entering it must drop the filter.
        clearHighlights();
        if (replace) {
            clearFilter();
        }
        performSearch();
    }

    function closeEditDropdown() {
        var historyDropdown = document.getElementById('history-dropdown');
        if (!historyDropdown) return;
        historyDropdown.classList.remove('open');
        var toggle = historyDropdown.querySelector('.nav-dropdown-toggle');
        if (toggle) toggle.setAttribute('aria-expanded', 'false');
    }

    function setOpen(open) {
        var searchDropdown = getDropdown();
        var searchInput = getInput();
        var menuItem = getMenuItem();
        if (!searchDropdown || !searchInput) return;
        searchDropdown.classList.toggle('is-open', !!open);
        if (menuItem) {
            menuItem.setAttribute('aria-expanded', open ? 'true' : 'false');
        }
        searchInput.tabIndex = open ? 0 : -1;
        if (!open && !searchInput.value) {
            searchDropdown.classList.remove('has-value');
        }
    }

    function isOpen() {
        var searchDropdown = getDropdown();
        return !!(searchDropdown && searchDropdown.classList.contains('is-open'));
    }

    function focusSearch() {
        var searchInput = getInput();
        if (!searchInput) return;
        closeEditDropdown();
        setOpen(true);
        setTimeout(function () {
            searchInput.focus();
            searchInput.select();
        }, 0);
    }

    function closeSearch() {
        var searchInput = getInput();
        var menuItem = getMenuItem();
        if (searchInput && searchInput.value) {
            // Keep open while a query is active so results stay filterable
            setOpen(true);
            searchInput.blur();
            return;
        }
        setOpen(false);
        if (searchInput) searchInput.blur();
        if (menuItem) menuItem.focus();
    }

    function rowMatchesSearch(row, query) {
        var blockId = row.getAttribute('data-block-id');
        if (!blockId) return query === '';
        if (!query) return true;
        var contentCell = row.querySelector('.block-content');
        var blockContentText = contentCell ? contentCell.textContent.toLowerCase() : '';
        var characterNameEl = row.querySelector(
            '.script-character-name, .reader-visible-character-name, .character-name'
        );
        var characterNameText = characterNameEl ? characterNameEl.textContent.toLowerCase() : '';
        var tagsAttr = row.getAttribute('data-tags') || '';
        return blockContentText.includes(query)
            || characterNameText.includes(query)
            || tagsAttr.toLowerCase().includes(query);
    }

    function blockRows() {
        return document.querySelectorAll('.project-script .scene-blocks .block-row[data-block-id]');
    }

    function reflow() {
        if (typeof window.scriptyReflowPageView === 'function') {
            window.scriptyReflowPageView();
        }
    }

    function clearFilter() {
        blockRows().forEach(function (row) {
            row.classList.remove('filtered-out');
            if (row.style.display === 'none') {
                row.style.display = '';
            }
        });
    }

    function performSearch() {
        var searchInput = getInput();
        if (!searchInput) return;
        var clearBtn = getClearBtn();
        var searchDropdown = getDropdown();
        var raw = searchInput.value;
        if (clearBtn) {
            clearBtn.hidden = !raw;
        }
        if (searchDropdown) {
            searchDropdown.classList.toggle('has-value', !!raw.trim());
            if (raw.trim()) setOpen(true);
        }

        if (isReplaceMode()) {
            refreshMatches();
            reflow();
            return;
        }

        var query = raw.toLowerCase().trim();
        blockRows().forEach(function (row) {
            var matched = rowMatchesSearch(row, query);
            row.classList.toggle('filtered-out', !matched);
            // Clear legacy inline hides from older search runs.
            if (row.style.display === 'none') {
                row.style.display = '';
            }
        });
        reflow();
    }

    // --- Find & replace -----------------------------------------------------

    function escapeRegExp(value) {
        return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    function buildFindRegex() {
        var searchInput = getInput();
        var term = searchInput ? searchInput.value : '';
        if (!term) return null;
        var source = escapeRegExp(term);
        if (optionChecked('project-search-whole-word')) {
            source = '\\b' + source + '\\b';
        }
        var flags = optionChecked('project-search-match-case') ? 'g' : 'gi';
        try {
            return new RegExp(source, flags);
        } catch (e) {
            return null;
        }
    }

    function isCueRow(row) {
        return CUE_TYPES.indexOf(row.getAttribute('data-block-type')) !== -1;
    }

    /** Restores every marked element to the plain text it held before highlighting. */
    function clearHighlights() {
        document.querySelectorAll('[data-search-original]').forEach(function (el) {
            el.textContent = el.getAttribute('data-search-original');
            el.removeAttribute('data-search-original');
        });
        matches = [];
        currentIndex = -1;
    }

    function highlight(el, ranges) {
        var text = el.textContent;
        // Stash the pristine text so re-running find never marks up its own marks.
        el.setAttribute('data-search-original', text);
        var frag = document.createDocumentFragment();
        var pos = 0;
        ranges.forEach(function (range) {
            if (range.start > pos) {
                frag.appendChild(document.createTextNode(text.slice(pos, range.start)));
            }
            var mark = document.createElement('mark');
            mark.className = 'project-search-hit';
            mark.textContent = text.slice(range.start, range.end);
            range.mark = mark;
            frag.appendChild(mark);
            pos = range.end;
        });
        if (pos < text.length) {
            frag.appendChild(document.createTextNode(text.slice(pos)));
        }
        el.textContent = '';
        el.appendChild(frag);
    }

    function refreshMatches(preserveIndex) {
        var previous = preserveIndex ? currentIndex : -1;
        clearHighlights();
        var regex = buildFindRegex();
        if (!regex) {
            updateCounter();
            return;
        }
        var includeCues = optionChecked('project-search-include-cues');
        blockRows().forEach(function (row) {
            if (!includeCues && isCueRow(row)) return;
            var el = row.querySelector(TEXT_SELECTOR);
            if (!el) return;
            var text = el.textContent;
            if (!text) return;
            var ranges = [];
            var m;
            regex.lastIndex = 0;
            while ((m = regex.exec(text)) !== null) {
                ranges.push({
                    start: m.index,
                    end: m.index + m[0].length,
                    row: row,
                    blockId: row.getAttribute('data-block-id'),
                    occurrence: ranges.length
                });
                if (m[0].length === 0) regex.lastIndex++;
            }
            if (!ranges.length) return;
            highlight(el, ranges);
            matches = matches.concat(ranges);
        });
        if (matches.length) {
            currentIndex = previous >= 0 && previous < matches.length ? previous : 0;
            markCurrent(false);
        }
        updateCounter();
    }

    function updateCounter() {
        var counter = document.getElementById('project-search-count');
        if (!counter) return;
        if (!matches.length) {
            var searchInput = getInput();
            counter.textContent = searchInput && searchInput.value ? 'No results' : '';
        } else {
            counter.textContent = (currentIndex + 1) + ' of ' + matches.length;
        }
    }

    function markCurrent(scroll) {
        matches.forEach(function (match, index) {
            if (!match.mark) return;
            match.mark.classList.toggle('is-current', index === currentIndex);
        });
        var current = matches[currentIndex];
        if (scroll && current && current.mark && current.mark.scrollIntoView) {
            current.mark.scrollIntoView({ block: 'center', behavior: 'smooth' });
        }
        updateCounter();
    }

    function step(delta) {
        if (!matches.length) return;
        currentIndex = (currentIndex + delta + matches.length) % matches.length;
        markCurrent(true);
    }

    function postForm(url, params) {
        var body = new URLSearchParams();
        Object.keys(params).forEach(function (key) {
            body.append(key, params[key]);
        });
        return fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body.toString(),
            credentials: 'same-origin'
        });
    }

    function replaceCurrent() {
        var match = matches[currentIndex];
        var replaceInput = getReplaceInput();
        var searchInput = getInput();
        if (!match || !replaceInput || !searchInput) return;
        var index = currentIndex;
        postForm('/block/replaceOne', {
            id: match.blockId,
            find: searchInput.value,
            replace: replaceInput.value,
            matchCase: optionChecked('project-search-match-case'),
            wholeWord: optionChecked('project-search-whole-word'),
            occurrence: match.occurrence
        }).then(function (response) {
            if (!response.ok) throw new Error('replaceOne failed: ' + response.status);
            return response.text();
        }).then(function (html) {
            var content = match.row.querySelector('.block-content');
            if (content) {
                // Same swap contract as saveEditInline, so the row re-renders from the server.
                content.innerHTML = html;
                content.removeAttribute('data-search-original');
            }
            // One match disappeared; keep the cursor where it was so repeated
            // Replace clicks walk forward naturally.
            refreshMatches(false);
            if (matches.length) {
                currentIndex = Math.min(index, matches.length - 1);
                markCurrent(true);
            }
            reflow();
            // Plain fetch, so the HTMX hook that refreshes Undo/Redo never fires.
            if (typeof window.scriptyRefreshUndoStatus === 'function') {
                window.scriptyRefreshUndoStatus();
            }
        }).catch(function (err) {
            console.error(err);
        });
    }

    function replaceAll() {
        var searchInput = getInput();
        var replaceInput = getReplaceInput();
        if (!searchInput || !replaceInput || !matches.length) return;
        var ids = [];
        matches.forEach(function (match) {
            if (ids.indexOf(match.blockId) === -1) ids.push(match.blockId);
        });
        var projectId = typeof window.scriptyResolveProjectId === 'function'
            ? window.scriptyResolveProjectId() : null;

        // Replace All redirects and re-renders the body, so carry the find state
        // across the navigation the way the history toast does.
        try {
            sessionStorage.setItem(RESTORE_KEY, JSON.stringify({
                find: searchInput.value,
                replace: replaceInput.value,
                matchCase: optionChecked('project-search-match-case'),
                wholeWord: optionChecked('project-search-whole-word'),
                includeCues: optionChecked('project-search-include-cues')
            }));
        } catch (e) { /* private mode - state restore is best effort */ }

        var form = document.createElement('form');
        form.method = 'POST';
        form.action = '/block/bulkReplace';
        form.style.display = 'none';
        var fields = {
            ids: ids.join(','),
            find: searchInput.value,
            replace: replaceInput.value,
            matchCase: optionChecked('project-search-match-case'),
            wholeWord: optionChecked('project-search-whole-word'),
            includeCharacterCues: optionChecked('project-search-include-cues')
        };
        if (projectId) fields.projectId = projectId;
        Object.keys(fields).forEach(function (name) {
            var input = document.createElement('input');
            input.type = 'hidden';
            input.name = name;
            input.value = fields[name];
            form.appendChild(input);
        });
        if (typeof window.scriptyAppendCsrfToForm === 'function') {
            window.scriptyAppendCsrfToForm(form);
        }
        document.body.appendChild(form);
        form.submit();
    }

    function restoreAfterReplaceAll() {
        var saved;
        try {
            saved = sessionStorage.getItem(RESTORE_KEY);
            sessionStorage.removeItem(RESTORE_KEY);
        } catch (e) {
            return;
        }
        if (!saved) return;
        var state;
        try {
            state = JSON.parse(saved);
        } catch (e) {
            return;
        }
        var searchInput = getInput();
        var replaceInput = getReplaceInput();
        if (!searchInput || !replaceInput) return;
        searchInput.value = state.find || '';
        replaceInput.value = state.replace || '';
        setChecked('project-search-match-case', state.matchCase);
        setChecked('project-search-whole-word', state.wholeWord);
        setChecked('project-search-include-cues', state.includeCues);
        setOpen(true);
        setMode('replace');
    }

    function setChecked(id, value) {
        var el = document.getElementById(id);
        if (el) el.checked = !!value;
    }

    function syncShortcutLabel() {
        var searchInput = getInput();
        var menuItem = getMenuItem();
        if (!searchInput && !menuItem) return;
        var isMac = window.scriptyIsMac
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.userAgent);
        var searchHint = isMac ? '⌘F' : 'Ctrl+F';
        var searchShortcut = ' (' + searchHint + ')';
        if (searchInput) {
            searchInput.title = 'Search script' + searchShortcut;
            searchInput.setAttribute('aria-label', 'Search blocks, character names, or tags' + searchShortcut);
        }
        if (menuItem) {
            if (!menuItem.querySelector('.nav-dropdown-item-label') &&
                !menuItem.querySelector('.nav-dropdown-shortcut, .element-type-shortcut')) {
                var labelEl = document.createElement('span');
                labelEl.className = 'nav-dropdown-item-label';
                labelEl.textContent = (menuItem.textContent || 'Search').trim() || 'Search';
                menuItem.textContent = '';
                menuItem.appendChild(labelEl);
            }
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(menuItem, searchHint);
            }
            menuItem.title = 'Search script' + searchShortcut;
            menuItem.setAttribute('aria-label', 'Search script' + searchShortcut);
        }
        var replaceItem = document.getElementById('nav-find-replace');
        if (replaceItem) {
            var replaceHint = isMac ? '⌘⌥F' : 'Ctrl+Alt+F';
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(replaceItem, replaceHint);
            }
            replaceItem.title = 'Find and replace (' + replaceHint + ')';
            replaceItem.setAttribute('aria-label', 'Find and replace (' + replaceHint + ')');
        }
    }

    window.scriptyOpenProjectSearch = function () {
        focusSearch();
    };

    window.scriptyPerformProjectSearch = performSearch;

    function openFindReplace() {
        closeEditDropdown();
        setOpen(true);
        setMode('replace');
        var searchInput = getInput();
        if (!searchInput) return;
        setTimeout(function () {
            searchInput.focus();
            searchInput.select();
        }, 0);
    }

    window.scriptyOpenFindReplace = openFindReplace;

    document.body.addEventListener('click', function (e) {
        var target = e.target;
        if (!target || !target.closest) return;

        var tagBadge = target.closest('.block-tag.clickable');
        if (tagBadge) {
            var searchInput = getInput();
            if (searchInput) {
                searchInput.value = tagBadge.textContent.trim();
                performSearch();
                focusSearch();
            }
            return;
        }

        var menuItem = target.closest('#nav-search');
        if (menuItem) {
            e.preventDefault();
            e.stopPropagation();
            if (isReplaceMode()) {
                setMode('filter');
                focusSearch();
            } else if (isOpen() && !(getInput() && getInput().value)) {
                closeEditDropdown();
                closeSearch();
            } else {
                focusSearch();
            }
            return;
        }

        if (target.closest('#nav-find-replace')) {
            e.preventDefault();
            e.stopPropagation();
            openFindReplace();
            return;
        }

        if (target.closest('#project-search-next')) {
            e.preventDefault();
            step(1);
            return;
        }

        if (target.closest('#project-search-prev')) {
            e.preventDefault();
            step(-1);
            return;
        }

        if (target.closest('#project-replace-one')) {
            e.preventDefault();
            replaceCurrent();
            return;
        }

        if (target.closest('#project-replace-all')) {
            e.preventDefault();
            replaceAll();
            return;
        }

        var clearBtn = target.closest('#project-search-clear');
        if (clearBtn) {
            e.preventDefault();
            var input = getInput();
            if (!input) return;
            input.value = '';
            performSearch();
            input.focus();
            return;
        }

        var searchDropdown = getDropdown();
        if (searchDropdown && isOpen() && !searchDropdown.contains(target)) {
            var inputEl = getInput();
            if (inputEl && !inputEl.value) {
                setOpen(false);
            }
        }
    });

    document.body.addEventListener('input', function (e) {
        if (e.target && e.target.id === 'project-search') {
            performSearch();
        }
    });

    document.body.addEventListener('change', function (e) {
        if (!e.target || !e.target.id) return;
        if (e.target.id === 'project-search-match-case'
            || e.target.id === 'project-search-whole-word'
            || e.target.id === 'project-search-include-cues') {
            performSearch();
        }
    });

    document.addEventListener('keydown', function (e) {
        var searchInput = getInput();

        if (e.key === 'Escape' && searchInput && (document.activeElement === searchInput || isOpen())) {
            if (isReplaceMode()) {
                setMode('filter');
                closeSearch();
            } else if (searchInput.value) {
                searchInput.value = '';
                performSearch();
                searchInput.focus();
            } else {
                closeSearch();
            }
            e.preventDefault();
            return;
        }

        // Enter walks matches while the find field has focus in replace mode.
        if (e.key === 'Enter' && isReplaceMode() && searchInput
            && (document.activeElement === searchInput || document.activeElement === getReplaceInput())) {
            e.preventDefault();
            step(e.shiftKey ? -1 : 1);
            return;
        }

        if (!(e.metaKey || e.ctrlKey)) return;
        var key = e.key.toLowerCase();

        // ⌘G / ⌘⇧G steps through matches from anywhere on the page.
        if (key === 'g' && !e.altKey && matches.length) {
            e.preventDefault();
            step(e.shiftKey ? -1 : 1);
            return;
        }

        if (key !== 'f' || !searchInput) return;

        // ⌘⌥F opens find & replace; ⌘F opens plain search. Both preempt the
        // browser's own find, and both work while editing a block.
        if (e.altKey && !e.shiftKey) {
            e.preventDefault();
            openFindReplace();
            return;
        }
        if (e.altKey || e.shiftKey) return;
        e.preventDefault();
        if (isReplaceMode()) setMode('filter');
        focusSearch();
    });

    function sync() {
        if (!getInput()) return;
        syncShortcutLabel();
        restoreAfterReplaceAll();
        var input = getInput();
        if (input && input.value) {
            setOpen(true);
        } else if (!isOpen()) {
            setOpen(false);
        }
        if (isReplaceMode()) {
            // A swap re-rendered rows and wiped the marks; re-find but stay on
            // the match the user was looking at.
            refreshMatches(true);
            reflow();
            return;
        }
        performSearch();
    }

    document.body.addEventListener('htmx:afterSwap', sync);
    document.body.addEventListener('htmx:afterSettle', sync);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sync);
    } else {
        sync();
    }
})();
