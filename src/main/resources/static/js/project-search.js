/**
 * Project script search (inline field beside last-edited).
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyProjectSearchInit) return;
    window._scriptyProjectSearchInit = true;

    function getDropdown() {
        return document.getElementById('project-search-dropdown');
    }

    function getInput() {
        return document.getElementById('project-search');
    }

    function getClearBtn() {
        return document.getElementById('project-search-clear');
    }

    function focusSearch() {
        var searchInput = getInput();
        if (!searchInput) return;
        setTimeout(function () {
            searchInput.focus();
            searchInput.select();
        }, 0);
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

    function performSearch() {
        var searchInput = getInput();
        if (!searchInput) return;
        var query = searchInput.value.toLowerCase().trim();
        var clearBtn = getClearBtn();
        var searchDropdown = getDropdown();
        if (clearBtn) {
            clearBtn.hidden = !query;
        }
        if (searchDropdown) {
            searchDropdown.classList.toggle('has-value', !!query);
        }
        document.querySelectorAll('.project-script .scene-blocks .block-row[data-block-id]').forEach(function (row) {
            var matches = rowMatchesSearch(row, query);
            row.classList.toggle('filtered-out', !matches);
            // Clear legacy inline hides from older search runs.
            if (row.style.display === 'none') {
                row.style.display = '';
            }
        });
        if (typeof window.scriptyReflowPageView === 'function') {
            window.scriptyReflowPageView();
        }
    }

    function syncShortcutLabel() {
        var searchInput = getInput();
        if (!searchInput) return;
        var isMac = window.scriptyIsMac
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.userAgent);
        var searchShortcut = isMac ? ' (⌘F)' : ' (Ctrl+F)';
        searchInput.title = 'Search script' + searchShortcut;
        searchInput.setAttribute('aria-label', 'Search blocks, character names, or tags' + searchShortcut);
    }

    window.scriptyOpenProjectSearch = function () {
        focusSearch();
    };

    window.scriptyPerformProjectSearch = performSearch;

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

        var clearBtn = target.closest('#project-search-clear');
        if (clearBtn) {
            e.preventDefault();
            var input = getInput();
            if (!input) return;
            input.value = '';
            performSearch();
            input.focus();
        }
    });

    document.body.addEventListener('input', function (e) {
        if (e.target && e.target.id === 'project-search') {
            performSearch();
        }
    });

    document.addEventListener('keydown', function (e) {
        var searchInput = getInput();

        if (e.key === 'Escape' && searchInput && document.activeElement === searchInput) {
            if (searchInput.value) {
                searchInput.value = '';
                performSearch();
            } else {
                searchInput.blur();
            }
            return;
        }

        if (!(e.metaKey || e.ctrlKey) || e.altKey || e.shiftKey) return;
        if (e.key.toLowerCase() !== 'f') return;
        if (!searchInput) return;
        var active = document.activeElement;
        if (window.scriptyIsTypingContext && window.scriptyIsTypingContext(active)) {
            if (active !== searchInput) return;
        }
        e.preventDefault();
        focusSearch();
    });

    function sync() {
        if (!getInput()) return;
        syncShortcutLabel();
        performSearch();
    }

    document.body.addEventListener('htmx:afterSwap', sync);
    document.body.addEventListener('htmx:afterSettle', sync);
    window.addEventListener('beforeprint', performSearch);
    window.addEventListener('afterprint', performSearch);
    window.addEventListener('scripty:project-script-refreshed', performSearch);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sync);
    } else {
        sync();
    }
})();
