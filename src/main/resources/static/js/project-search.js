/**
 * Project script search (icon button + expandable field beside toolbar actions).
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

    function getToggle() {
        return document.getElementById('project-search-toggle');
    }

    function setOpen(open) {
        var searchDropdown = getDropdown();
        var searchInput = getInput();
        var toggle = getToggle();
        if (!searchDropdown || !searchInput) return;
        searchDropdown.classList.toggle('is-open', !!open);
        if (toggle) {
            toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
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
        setOpen(true);
        setTimeout(function () {
            searchInput.focus();
            searchInput.select();
        }, 0);
    }

    function closeSearch() {
        var searchInput = getInput();
        var toggle = getToggle();
        if (searchInput && searchInput.value) {
            // Keep open while a query is active so results stay filterable
            setOpen(true);
            searchInput.blur();
            return;
        }
        setOpen(false);
        if (searchInput) searchInput.blur();
        if (toggle) toggle.focus();
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
            if (query) setOpen(true);
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
        var toggle = getToggle();
        if (!searchInput && !toggle) return;
        var isMac = window.scriptyIsMac
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.userAgent);
        var searchShortcut = isMac ? ' (⌘F)' : ' (Ctrl+F)';
        if (searchInput) {
            searchInput.title = 'Search script' + searchShortcut;
            searchInput.setAttribute('aria-label', 'Search blocks, character names, or tags' + searchShortcut);
        }
        if (toggle) {
            toggle.title = 'Search script' + searchShortcut;
            toggle.setAttribute('aria-label', 'Search script' + searchShortcut);
        }
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

        var toggle = target.closest('#project-search-toggle');
        if (toggle) {
            e.preventDefault();
            if (isOpen() && !(getInput() && getInput().value)) {
                closeSearch();
            } else {
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

    document.addEventListener('keydown', function (e) {
        var searchInput = getInput();

        if (e.key === 'Escape' && searchInput && (document.activeElement === searchInput || isOpen())) {
            if (searchInput.value) {
                searchInput.value = '';
                performSearch();
                searchInput.focus();
            } else {
                closeSearch();
            }
            e.preventDefault();
            return;
        }

        // ⌘F / Ctrl+F always opens project search (even while editing a block).
        if (!(e.metaKey || e.ctrlKey) || e.altKey || e.shiftKey) return;
        if (e.key.toLowerCase() !== 'f') return;
        if (!searchInput) return;
        e.preventDefault();
        focusSearch();
    });

    function sync() {
        if (!getInput()) return;
        syncShortcutLabel();
        var input = getInput();
        if (input && input.value) {
            setOpen(true);
        } else if (!isOpen()) {
            setOpen(false);
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
