/**
 * Project script search (magnifying glass beside last-edited).
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

    function getToggle(dropdown) {
        return dropdown ? dropdown.querySelector('.project-search-toggle') : null;
    }

    function getInput() {
        return document.getElementById('project-search');
    }

    function getClearBtn() {
        return document.getElementById('project-search-clear');
    }

    function closeAllDropdowns() {
        document.querySelectorAll('.nav-dropdown').forEach(function (d) {
            d.classList.remove('open');
            var t = d.querySelector('.nav-dropdown-toggle');
            if (t) t.setAttribute('aria-expanded', 'false');
        });
    }

    function setSearchOpen(isOpen) {
        var searchDropdown = getDropdown();
        var searchToggle = getToggle(searchDropdown);
        var searchInput = getInput();
        if (!searchDropdown || !searchToggle) return;
        searchDropdown.classList.toggle('open', isOpen);
        searchToggle.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        if (isOpen && searchInput) {
            setTimeout(function () {
                searchInput.focus();
                searchInput.select();
            }, 0);
        }
    }

    function rowMatchesSearch(row, query) {
        var blockId = row.getAttribute('data-block-id');
        if (!blockId) return query === '';
        if (!query) return true;
        var contentCell = row.querySelector('.block-content');
        var blockContentText = contentCell ? contentCell.textContent.toLowerCase() : '';
        var tagsAttr = row.getAttribute('data-tags') || '';
        return blockContentText.includes(query) || tagsAttr.toLowerCase().includes(query);
    }

    function performSearch() {
        var searchInput = getInput();
        if (!searchInput) return;
        var query = searchInput.value.toLowerCase().trim();
        var clearBtn = getClearBtn();
        var searchToggle = getToggle(getDropdown());
        if (clearBtn) {
            clearBtn.hidden = !query;
        }
        if (searchToggle) {
            searchToggle.classList.toggle('is-active', !!query);
        }
        document.querySelectorAll('.project-script .scene-blocks .block-row[data-block-id]').forEach(function (row) {
            row.style.display = rowMatchesSearch(row, query) ? '' : 'none';
        });
    }

    function syncShortcutLabel() {
        var searchToggle = getToggle(getDropdown());
        if (!searchToggle) return;
        var isMac = window.scriptyIsMac
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.userAgent);
        var searchShortcut = isMac ? ' (⌘F)' : ' (Ctrl+F)';
        searchToggle.title = 'Search script' + searchShortcut;
        searchToggle.setAttribute('aria-label', 'Search script' + searchShortcut);
    }

    window.scriptyOpenProjectSearch = function () {
        closeAllDropdowns();
        setSearchOpen(true);
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
                setSearchOpen(true);
            }
            return;
        }

        var toggle = target.closest('.project-search-toggle');
        if (toggle) {
            var searchDropdown = getDropdown();
            if (!searchDropdown || !searchDropdown.contains(toggle)) return;
            e.preventDefault();
            e.stopPropagation();
            var isOpen = searchDropdown.classList.contains('open');
            closeAllDropdowns();
            if (!isOpen) {
                setSearchOpen(true);
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

        var openDropdown = getDropdown();
        if (openDropdown && openDropdown.classList.contains('open') && !openDropdown.contains(target)) {
            setSearchOpen(false);
        }
    });

    document.body.addEventListener('input', function (e) {
        if (e.target && e.target.id === 'project-search') {
            performSearch();
        }
    });

    document.addEventListener('keydown', function (e) {
        var searchDropdown = getDropdown();
        var searchToggle = getToggle(searchDropdown);

        if (e.key === 'Escape' && searchDropdown && searchDropdown.classList.contains('open')) {
            setSearchOpen(false);
            if (searchToggle) searchToggle.focus();
            return;
        }

        if (!(e.metaKey || e.ctrlKey) || e.altKey || e.shiftKey) return;
        if (e.key.toLowerCase() !== 'f') return;
        if (!getInput()) return;
        var active = document.activeElement;
        if (window.scriptyIsTypingContext && window.scriptyIsTypingContext(active)) {
            if (active !== getInput()) return;
        }
        e.preventDefault();
        closeAllDropdowns();
        setSearchOpen(true);
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
