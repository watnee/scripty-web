/**
 * Help Center tabs, search, and keyboard-shortcut OS labels.
 * Runs from initAll so it works after HTMX-boosted navigation
 * (inline scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

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
        document.querySelectorAll('.kb-ctrl').forEach(function (el) {
            el.textContent = modLabel;
        });
    }

    function initHelpCenter() {
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

        searchInput.oninput = function () {
            var query = this.value.trim().toLowerCase();

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
        };

        initShortcutLabels();
    }

    window.scriptyInitHelpCenter = initHelpCenter;
})();
