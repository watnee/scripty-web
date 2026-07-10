/**
 * Header nav dropdowns (user, help, settings).
 *
 * History dropdown stays with undo/redo. Loaded from nav.html so handlers
 * survive HTMX-boosted navigation.
 */
(function () {
    'use strict';

    if (window._scriptyNavDropdownsInit) return;
    window._scriptyNavDropdownsInit = true;

    var DROPDOWN_IDS = ['user-dropdown', 'help-dropdown', 'settings-dropdown'];

    function closeAllNavDropdowns() {
        document.querySelectorAll('.nav-dropdown').forEach(function (d) {
            d.classList.remove('open');
        });
        document.querySelectorAll('.nav-dropdown-toggle').forEach(function (btn) {
            btn.setAttribute('aria-expanded', 'false');
        });
    }

    function wireDropdown(dropdownId) {
        var dropdown = document.getElementById(dropdownId);
        if (!dropdown) return;
        var toggleBtn = dropdown.querySelector('.nav-dropdown-toggle');
        if (!toggleBtn || toggleBtn.dataset.scriptyNavDropdownWired === '1') return;
        toggleBtn.dataset.scriptyNavDropdownWired = '1';

        toggleBtn.addEventListener('click', function (e) {
            e.stopPropagation();
            var isOpen = dropdown.classList.contains('open');
            closeAllNavDropdowns();
            toggleBtn.setAttribute('aria-expanded', isOpen ? 'false' : 'true');
            if (!isOpen) {
                dropdown.classList.add('open');
            }
        });
    }

    function initNavDropdowns() {
        DROPDOWN_IDS.forEach(wireDropdown);
    }

    document.addEventListener('click', function (e) {
        DROPDOWN_IDS.forEach(function (id) {
            var dropdown = document.getElementById(id);
            if (!dropdown || !dropdown.classList.contains('open')) return;
            if (!dropdown.contains(e.target)) {
                dropdown.classList.remove('open');
                var toggle = dropdown.querySelector('.nav-dropdown-toggle');
                if (toggle) toggle.setAttribute('aria-expanded', 'false');
            }
        });
    });

    window.scriptyInitNavDropdowns = initNavDropdowns;

    document.body.addEventListener('htmx:afterSwap', initNavDropdowns);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initNavDropdowns);
    } else {
        initNavDropdowns();
    }
})();
