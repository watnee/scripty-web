/**
 * Help Center tabs, search, and keyboard-shortcut OS labels.
 * Also powers the Help dropdown feature finder (available on every page).
 * Runs from initAll so it works after HTMX-boosted navigation
 * (inline scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    var HELP_FEATURES = [
        { id: 'welcome', title: 'Welcome to Scripty', keywords: 'introduction screenplay overview' },
        { id: 'hierarchy', title: 'The Scripty Hierarchy', keywords: 'project scene block structure' },
        { id: 'default-project', title: 'Default Project', keywords: 'home dashboard settings' },
        { id: 'install-app', title: 'Install as an App', keywords: 'pwa home screen offline ios android' },
        { id: 'inline-editing', title: 'Inline Editing & Auto-Save', keywords: 'edit block save typing' },
        { id: 'offline-editing', title: 'Offline Editing', keywords: 'offline sync cache airplane' },
        { id: 'adding-blocks', title: 'Adding Blocks', keywords: 'insert create plus enter' },
        { id: 'fountain-features', title: 'Fountain Power Features', keywords: 'tab element autocomplete outline scene list page view import export pdf fountain paste shortcut keyboard' },
        { id: 'songs-drafts', title: 'Songs & Notes', keywords: 'lyrics notes music documents' },
        { id: 'drag-drop', title: 'Drag & Drop Reordering', keywords: 'move reorder handle' },
        { id: 'bookmarks-pins', title: 'Bookmarks & Pinned Blocks', keywords: 'star pin favorite outline bookmark list panel' },
        { id: 'bulk-actions', title: 'Bulk Tagging & Deletions', keywords: 'select checkbox tags delete' },
        { id: 'reader-view', title: 'Safari Reader & Read Script', keywords: 'reader read-only distraction free' },
        { id: 'text-size', title: 'Text Size Controls', keywords: 'font zoom larger smaller' },
        { id: 'spellcheck', title: 'Spellcheck & Suggestions', keywords: 'spelling typo dictionary' },
        { id: 'undo-redo', title: 'Undo, Redo & Snapshot History', keywords: 'history undo redo clock' },
        { id: 'snapshot-history', title: 'Snapshot History', keywords: 'version restore backup revision publish share team' },
        { id: 'casting', title: 'Actor Casting', keywords: 'actors casting roles' },
        { id: 'characters', title: 'Characters & Roles', keywords: 'character dialogue profile' },
        { id: 'teams', title: 'Team Collaboration', keywords: 'share collaborate members' },
        { id: 'shortcuts', title: 'Keyboard Shortcuts', keywords: 'keyboard shortcut keys hotkeys', href: '/shortcuts' }
    ];

    function initShortcutLabels() {
        var isMac = window.scriptyIsMac
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
        var modLabel = isMac ? '⌘' : 'Ctrl';

        document.querySelectorAll('.kb-mod-z').forEach(function (el) {
            el.textContent = isMac ? '⌘ Z' : 'Ctrl + Z';
        });
        document.querySelectorAll('.kb-mod-shift-z').forEach(function (el) {
            el.textContent = isMac ? '⌘ ⇧ Z' : 'Ctrl + Y';
        });
        document.querySelectorAll('.kb-mod-f').forEach(function (el) {
            el.textContent = isMac ? '⌘ F' : 'Ctrl + F';
        });
        document.querySelectorAll('.kb-mod-g').forEach(function (el) {
            el.textContent = isMac ? '⌘ G' : 'Ctrl + G';
        });
        document.querySelectorAll('.kb-mod-shift-f').forEach(function (el) {
            el.textContent = isMac ? '⌘ ⇧ F' : 'Ctrl + Shift + F';
        });
        document.querySelectorAll('.kb-mod-shift-o').forEach(function (el) {
            el.textContent = isMac ? '⌘ ⇧ O' : 'Ctrl + Shift + O';
        });
        document.querySelectorAll('.kb-mod-shift-p').forEach(function (el) {
            el.textContent = isMac ? '⌘ ⇧ P' : 'Ctrl + Shift + P';
        });
        document.querySelectorAll('.kb-mod-shift-l').forEach(function (el) {
            el.textContent = isMac ? '⌘ ⇧ L' : 'Ctrl + Shift + L';
        });
        document.querySelectorAll('.kb-mod-alt-m').forEach(function (el) {
            el.textContent = isMac ? '⌘ ⌥ M' : 'Ctrl + Alt + M';
        });
        document.querySelectorAll('.kb-mod-backslash').forEach(function (el) {
            el.textContent = isMac ? '⌘ \\' : 'Ctrl + \\';
        });
        document.querySelectorAll('.kb-mod-b').forEach(function (el) {
            el.textContent = isMac ? '⌘ B' : 'Ctrl + B';
        });
        document.querySelectorAll('.kb-mod-i').forEach(function (el) {
            el.textContent = isMac ? '⌘ I' : 'Ctrl + I';
        });
        document.querySelectorAll('.kb-mod-u').forEach(function (el) {
            el.textContent = isMac ? '⌘ U' : 'Ctrl + U';
        });
        document.querySelectorAll('.kb-mod-shift-s').forEach(function (el) {
            el.textContent = isMac ? '⌘ ⇧ S' : 'Ctrl + Shift + S';
        });
        document.querySelectorAll('.kb-mod-shift-d').forEach(function (el) {
            el.textContent = isMac ? '⌘ ⇧ D' : 'Ctrl + Shift + D';
        });
        document.querySelectorAll('.kb-mod-c').forEach(function (el) {
            el.textContent = isMac ? '⌘ C' : 'Ctrl + C';
        });
        document.querySelectorAll('.kb-mod-x').forEach(function (el) {
            el.textContent = isMac ? '⌘ X' : 'Ctrl + X';
        });
        document.querySelectorAll('.kb-mod-v').forEach(function (el) {
            el.textContent = isMac ? '⌘ V' : 'Ctrl + V';
        });
        document.querySelectorAll('.kb-mod-plus').forEach(function (el) {
            el.textContent = isMac ? '⌘ +' : 'Ctrl + +';
        });
        document.querySelectorAll('.kb-mod-minus').forEach(function (el) {
            el.textContent = isMac ? '⌘ −' : 'Ctrl + −';
        });
        document.querySelectorAll('.kb-mod-period').forEach(function (el) {
            el.textContent = isMac ? '⌘ .' : 'Ctrl + .';
        });
        for (var digit = 1; digit <= 7; digit++) {
            (function (d) {
                document.querySelectorAll('.kb-mod-' + d).forEach(function (el) {
                    el.textContent = isMac ? '⌘ ' + d : 'Ctrl + ' + d;
                });
                document.querySelectorAll('.kb-mod-alt-' + d).forEach(function (el) {
                    el.textContent = isMac ? '⌘ ⌥ ' + d : 'Ctrl + Alt + ' + d;
                });
            })(digit);
        }
        ['t', 'u', 'y', 'm', 'x', 'o', 'n', 'b', 'f'].forEach(function (letter) {
            document.querySelectorAll('.kb-mod-alt-' + letter).forEach(function (el) {
                var upper = letter.toUpperCase();
                // Help prose sometimes uses the letter alone inside a longer sentence.
                if ((el.textContent || '').trim().length <= 1) {
                    el.textContent = upper;
                } else {
                    el.textContent = isMac ? '⌘ ⌥ ' + upper : 'Ctrl + Alt + ' + upper;
                }
            });
        });
        document.querySelectorAll('.kb-ctrl').forEach(function (el) {
            el.textContent = modLabel;
        });
    }

    function featureHref(feature) {
        if (feature.href) return feature.href;
        return '/help#' + feature.id;
    }

    function matchesFeature(feature, query) {
        if (!query) return false;
        var haystack = (feature.title + ' ' + (feature.keywords || '') + ' ' + feature.id).toLowerCase();
        return query.split(/\s+/).every(function (token) {
            return token && haystack.indexOf(token) !== -1;
        });
    }

    function initHelpFeatureSearch() {
        var dropdown = document.getElementById('help-dropdown');
        var searchInput = document.getElementById('help-feature-search');
        var resultsEl = document.getElementById('help-feature-search-results');
        var emptyEl = document.getElementById('help-feature-search-empty');
        if (!dropdown || !searchInput || !resultsEl || !emptyEl) return;
        if (searchInput.dataset.scriptyHelpFeatureWired === '1') return;
        searchInput.dataset.scriptyHelpFeatureWired = '1';

        function renderResults(query) {
            resultsEl.innerHTML = '';
            if (!query) {
                resultsEl.hidden = true;
                emptyEl.hidden = true;
                return;
            }

            var matches = HELP_FEATURES.filter(function (feature) {
                return matchesFeature(feature, query);
            });

            if (matches.length === 0) {
                resultsEl.hidden = true;
                emptyEl.hidden = false;
                return;
            }

            emptyEl.hidden = true;
            resultsEl.hidden = false;
            matches.forEach(function (feature) {
                var link = document.createElement('a');
                link.className = 'nav-dropdown-item';
                link.href = featureHref(feature);
                link.setAttribute('role', 'menuitem');
                link.textContent = feature.title;
                resultsEl.appendChild(link);
            });
        }

        searchInput.addEventListener('input', function () {
            renderResults(this.value.trim().toLowerCase());
        });

        searchInput.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                if (this.value) {
                    this.value = '';
                    renderResults('');
                    e.stopPropagation();
                    return;
                }
                dropdown.classList.remove('open');
                var toggle = dropdown.querySelector('.nav-dropdown-toggle');
                if (toggle) toggle.setAttribute('aria-expanded', 'false');
                this.blur();
                return;
            }
            if (e.key === 'Enter') {
                var first = resultsEl.querySelector('.nav-dropdown-item');
                if (first) {
                    e.preventDefault();
                    first.click();
                }
            }
            if (e.key === 'ArrowDown') {
                var firstItem = resultsEl.querySelector('.nav-dropdown-item');
                if (firstItem) {
                    e.preventDefault();
                    firstItem.focus();
                }
            }
        });

        // Keep the dropdown open while interacting with the search field.
        searchInput.addEventListener('click', function (e) {
            e.stopPropagation();
        });

        var toggleBtn = dropdown.querySelector('.nav-dropdown-toggle');
        if (toggleBtn && toggleBtn.dataset.scriptyHelpSearchFocus !== '1') {
            toggleBtn.dataset.scriptyHelpSearchFocus = '1';
            toggleBtn.addEventListener('click', function () {
                window.setTimeout(function () {
                    if (!dropdown.classList.contains('open')) return;
                    searchInput.value = '';
                    renderResults('');
                    searchInput.focus();
                    if (typeof searchInput.select === 'function') searchInput.select();
                }, 0);
            });
        }
    }

    function initHelpCenter() {
        initHelpFeatureSearch();

        var tabsContainer = document.getElementById('help-tabs-container');
        if (!tabsContainer) {
            initShortcutLabels();
            return;
        }

        var tabButtons = tabsContainer.querySelectorAll('.help-tab-btn');
        var contentSections = document.querySelectorAll('.help-content-section');
        var searchInput = document.getElementById('help-search');
        var searchResultsSection = document.getElementById('search-results-section');
        var searchResultsGrid = document.getElementById('search-results-grid');
        var searchNoResults = document.getElementById('search-no-results');

        if (!searchInput || !searchResultsSection || !searchResultsGrid || !searchNoResults) {
            initShortcutLabels();
            return;
        }

        function switchTab(tabId) {
            tabButtons.forEach(function (btn) {
                if (btn.getAttribute('data-tab') === tabId) {
                    btn.classList.add('active');
                } else {
                    btn.classList.remove('active');
                }
            });
            contentSections.forEach(function (section) {
                if (section.id === tabId) {
                    section.classList.add('active');
                } else {
                    section.classList.remove('active');
                }
            });
        }

        tabButtons.forEach(function (btn) {
            btn.onclick = function () {
                searchInput.value = '';
                searchResultsSection.style.display = 'none';
                tabsContainer.style.display = 'flex';
                switchTab(this.getAttribute('data-tab'));
            };
        });

        var cards = [];
        document.querySelectorAll('.help-card').forEach(function (card) {
            var titleEl = card.querySelector('h3');
            cards.push({
                element: card,
                title: titleEl ? titleEl.textContent : '',
                html: card.innerHTML,
                keywords: (card.getAttribute('data-keywords') || '').toLowerCase(),
                text: card.textContent.toLowerCase()
            });
        });

        function runHelpSearch(query) {
            query = (query || '').trim().toLowerCase();

            if (query === '') {
                searchResultsSection.style.display = 'none';
                tabsContainer.style.display = 'flex';
                var activeBtn = tabsContainer.querySelector('.help-tab-btn.active');
                if (activeBtn) {
                    switchTab(activeBtn.getAttribute('data-tab'));
                }
                return;
            }

            tabsContainer.style.display = 'none';
            contentSections.forEach(function (s) {
                s.classList.remove('active');
            });
            searchResultsSection.style.display = 'block';

            searchResultsGrid.innerHTML = '';
            var matchCount = 0;

            cards.forEach(function (card) {
                var isMatch = card.title.toLowerCase().includes(query) ||
                    card.keywords.includes(query) ||
                    card.text.includes(query);

                if (isMatch) {
                    matchCount++;
                    var cardClone = document.createElement('div');
                    cardClone.className = 'help-card';
                    cardClone.innerHTML = card.html;
                    searchResultsGrid.appendChild(cardClone);
                }
            });

            searchNoResults.style.display = matchCount === 0 ? 'block' : 'none';
        }

        searchInput.oninput = function () {
            runHelpSearch(this.value);
        };

        function focusHelpHash() {
            var hash = (window.location.hash || '').replace(/^#/, '');
            if (!hash) return;
            var target = document.getElementById(hash);
            if (!target || !target.classList.contains('help-card')) return;
            var section = target.closest('.help-content-section');
            if (section && section.id) {
                searchInput.value = '';
                searchResultsSection.style.display = 'none';
                tabsContainer.style.display = 'flex';
                switchTab(section.id);
            }
            window.setTimeout(function () {
                target.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }, 50);
        }

        // Support /help?q=undo from the feature finder or shared links.
        try {
            var params = new URLSearchParams(window.location.search || '');
            var q = params.get('q');
            if (q) {
                searchInput.value = q;
                runHelpSearch(q);
            } else {
                focusHelpHash();
            }
        } catch (err) {
            focusHelpHash();
        }

        window.addEventListener('hashchange', focusHelpHash);

        initShortcutLabels();
    }

    window.scriptyInitHelpCenter = initHelpCenter;
})();
