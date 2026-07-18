/**
 * Project editor toolbar dropdowns (file, lists, view, tools, edition, share, text align/style).
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyProjectToolbarDropdownsInit) return;
    window._scriptyProjectToolbarDropdownsInit = true;

    var DROPDOWNS = [
        { id: 'project-file-dropdown', toggle: '.file-toolbar-btn' },
        { id: 'project-lists-dropdown', toggle: '.lists-toolbar-btn' },
        { id: 'project-auto-caps-dropdown', toggle: '.auto-caps-toolbar-btn', keepOpenOnItemClick: true },
        { id: 'project-element-type-dropdown', toggle: '.element-type-toolbar-btn' },
        { id: 'project-view-dropdown', toggle: '.view-toolbar-btn', keepOpenOnItemClick: true },
        { id: 'project-tools-dropdown', toggle: '.tools-toolbar-btn', keepOpenOnItemClick: true },
        { id: 'script-edition-dropdown', toggle: '.script-edition-toggle' },
        { id: 'project-docs-dropdown', toggle: '.docs-toolbar-btn' },
        { id: 'project-share-dropdown', toggle: '.project-share-toggle' },
        { id: 'project-text-format-dropdown', toggle: '.text-format-toolbar-btn', keepOpenOnItemClick: true }
    ];

    // Mirrors Block.HIGHLIGHTS; '' (no highlight) is the implicit default.
    var HIGHLIGHTS = ['YELLOW', 'GREEN', 'BLUE', 'RED', 'GRAY'];

    var ALIGN_ICONS = {
        LEFT: '<line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="12" x2="15" y2="12"></line><line x1="3" y1="18" x2="18" y2="18"></line>',
        CENTER: '<line x1="3" y1="6" x2="21" y2="6"></line><line x1="6" y1="12" x2="18" y2="12"></line><line x1="4" y1="18" x2="20" y2="18"></line>',
        RIGHT: '<line x1="3" y1="6" x2="21" y2="6"></line><line x1="9" y1="12" x2="21" y2="12"></line><line x1="6" y1="18" x2="21" y2="18"></line>'
    };

    function syncTextAlignToggleIcon(align) {
        var icon = document.querySelector('#project-text-format-dropdown .text-align-toolbar-icon');
        if (!icon || !ALIGN_ICONS[align]) return;
        icon.setAttribute('data-align-icon', align);
        icon.innerHTML = ALIGN_ICONS[align];
    }

    function normalizeAlign(value) {
        if (!value) return 'LEFT';
        var key = String(value).toUpperCase();
        return ALIGN_ICONS[key] ? key : 'LEFT';
    }

    function readBlockAlign(row) {
        if (!row) return 'LEFT';
        var content = row.querySelector('.block-content') || row;
        if (content.classList.contains('block-text-align-center')) return 'CENTER';
        if (content.classList.contains('block-text-align-right')) return 'RIGHT';
        if (content.classList.contains('block-text-align-left')) return 'LEFT';
        return 'LEFT';
    }

    function syncTextAlignMenu(align) {
        var current = normalizeAlign(align);
        syncTextAlignToggleIcon(current);
        var menu = document.querySelector('#project-text-format-dropdown .text-format-menu');
        if (!menu) return;
        menu.querySelectorAll('.bulk-align-btn').forEach(function (btn) {
            var btnAlign = normalizeAlign(btn.getAttribute('data-bulk-align'));
            var isActive = btnAlign === current;
            btn.classList.toggle('is-active', isActive);
            btn.setAttribute('aria-checked', isActive ? 'true' : 'false');
            btn.setAttribute('role', 'menuitemradio');
        });
    }

    function syncTextStyleButtons(row) {
        var content = row && (row.querySelector('.block-content') || row);
        var bold = !!(content && content.classList.contains('block-text-bold'));
        var italic = !!(content && content.classList.contains('block-text-italic'));
        var underline = !!(content && content.classList.contains('block-text-underline'));
        var anyOn = bold || italic || underline;
        var toggle = document.querySelector('#project-text-format-dropdown .text-format-toolbar-btn');
        if (toggle) {
            toggle.classList.toggle('is-active', anyOn);
            toggle.setAttribute('aria-pressed', anyOn ? 'true' : 'false');
        }
        document.querySelectorAll('#project-text-format-dropdown .bulk-style-btn').forEach(function (btn) {
            var style = (btn.getAttribute('data-bulk-style') || '').toUpperCase();
            var on = (style === 'BOLD' && bold)
                || (style === 'ITALIC' && italic)
                || (style === 'UNDERLINE' && underline);
            btn.classList.toggle('is-active', on);
            btn.setAttribute('aria-checked', on ? 'true' : 'false');
            btn.setAttribute('aria-pressed', on ? 'true' : 'false');
            btn.setAttribute('role', 'menuitemcheckbox');
        });
    }

    function readBlockFont(row) {
        if (!row) return '';
        var content = row.querySelector('.block-content') || row;
        if (content.classList.contains('block-font-courier-prime')) return 'COURIER_PRIME';
        if (content.classList.contains('block-font-arial')) return 'ARIAL';
        if (content.classList.contains('block-font-times-new-roman')) return 'TIMES_NEW_ROMAN';
        return '';
    }

    function syncTextFontMenu(font) {
        var current = font || '';
        var menu = document.querySelector('#project-text-format-dropdown .text-format-menu');
        if (!menu) return;
        menu.querySelectorAll('.bulk-font-btn').forEach(function (btn) {
            var btnFont = (btn.getAttribute('data-bulk-font') || '').toUpperCase();
            var isActive = btnFont === current;
            btn.classList.toggle('is-active', isActive);
            btn.setAttribute('aria-checked', isActive ? 'true' : 'false');
            btn.setAttribute('role', 'menuitemradio');
        });
    }

    function readBlockHighlight(row) {
        if (!row) return '';
        var content = row.querySelector('.block-content') || row;
        for (var i = 0; i < HIGHLIGHTS.length; i++) {
            if (content.classList.contains('block-highlight-' + HIGHLIGHTS[i].toLowerCase())) {
                return HIGHLIGHTS[i];
            }
        }
        return '';
    }

    function syncTextHighlightMenu(highlight) {
        var current = highlight || '';
        var menu = document.querySelector('#project-text-format-dropdown .text-format-menu');
        if (!menu) return;
        menu.querySelectorAll('.bulk-highlight-btn').forEach(function (btn) {
            var btnHighlight = (btn.getAttribute('data-bulk-highlight') || '').toUpperCase();
            var isActive = btnHighlight === current;
            btn.classList.toggle('is-active', isActive);
            btn.setAttribute('aria-checked', isActive ? 'true' : 'false');
            btn.setAttribute('role', 'menuitemradio');
        });
    }

    function syncFormatToolbarFromRow(row) {
        if (!row) return;
        syncTextAlignMenu(readBlockAlign(row));
        syncTextStyleButtons(row);
        syncTextFontMenu(readBlockFont(row));
        syncTextHighlightMenu(readBlockHighlight(row));
    }

    function closeAllDropdowns(exceptDropdown) {
        document.querySelectorAll('.nav-dropdown').forEach(function (d) {
            if (exceptDropdown && (d === exceptDropdown || d.contains(exceptDropdown))) {
                return;
            }
            d.classList.remove('open');
            var t = d.querySelector('.nav-dropdown-toggle');
            if (t) t.setAttribute('aria-expanded', 'false');
        });
    }

    function clampMenuWithinViewport(dropdown) {
        var menu = dropdown.querySelector('.nav-dropdown-menu');
        if (!menu) return;
        menu.style.marginLeft = '';
        if (!dropdown.classList.contains('open')) return;
        var pad = 8;
        // Offsets are layout-based, immune to the dropdownIn scale animation.
        var left = dropdown.getBoundingClientRect().left + menu.offsetLeft;
        var right = left + menu.offsetWidth;
        var overflow = right - (document.documentElement.clientWidth - pad);
        if (overflow > 0) {
            var shift = Math.min(overflow, Math.max(0, left - pad));
            if (shift > 0) menu.style.marginLeft = '-' + shift + 'px';
        }
    }

    function setOpen(dropdown, toggle, isOpen) {
        if (!dropdown || !toggle) return;
        dropdown.classList.toggle('open', isOpen);
        toggle.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        clampMenuWithinViewport(dropdown);
    }

    function findConfigForToggle(toggle) {
        if (!toggle) return null;
        for (var i = 0; i < DROPDOWNS.length; i++) {
            var cfg = DROPDOWNS[i];
            if (toggle.matches(cfg.toggle) || toggle.closest(cfg.toggle)) {
                var dropdown = document.getElementById(cfg.id);
                if (dropdown && dropdown.contains(toggle)) {
                    return { cfg: cfg, dropdown: dropdown, toggle: dropdown.querySelector(cfg.toggle) };
                }
            }
        }
        return null;
    }

    function resolveProjectId() {
        if (typeof window.scriptyResolveProjectId === 'function') {
            return window.scriptyResolveProjectId();
        }
        if (window.scriptyProjectId) return String(window.scriptyProjectId);
        var params = new URLSearchParams(window.location.search);
        if (window.location.pathname.indexOf('/project/show') === 0 && params.has('id')) {
            return params.get('id');
        }
        if (params.has('projectId')) return params.get('projectId');
        return null;
    }

    function syncShareLink() {
        var shareDropdown = document.getElementById('project-share-dropdown');
        if (!shareDropdown) return;
        var projectId = resolveProjectId();
        if (!projectId) return;
        var shareLinkInput = shareDropdown.querySelector('.project-share-link-input');
        if (!shareLinkInput) return;
        var link = window.location.origin + '/project/show?id=' + encodeURIComponent(projectId);
        var editionId = typeof window.scriptyResolveEditionId === 'function'
            ? window.scriptyResolveEditionId()
            : (window.scriptyEditionId || null);
        if (editionId) {
            link += '&editionId=' + encodeURIComponent(editionId);
        }
        shareLinkInput.value = link;
    }

    function markCopied(copyBtn) {
        copyBtn.textContent = 'Copied';
        copyBtn.classList.add('is-copied');
        window.setTimeout(function () {
            copyBtn.textContent = 'Copy';
            copyBtn.classList.remove('is-copied');
        }, 1500);
    }

    function copyShareLink(copyBtn) {
        var shareDropdown = document.getElementById('project-share-dropdown');
        if (!shareDropdown) return;
        var shareLinkInput = shareDropdown.querySelector('.project-share-link-input');
        var targetId = copyBtn.getAttribute('data-copy-target');
        var input = targetId ? document.getElementById(targetId) : shareLinkInput;
        var link = input ? input.value : '';
        if (!link) return;

        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(link).then(function () {
                markCopied(copyBtn);
            }).catch(function () {
                if (input) {
                    input.focus();
                    input.select();
                    document.execCommand('copy');
                    markCopied(copyBtn);
                }
            });
        } else if (input) {
            input.focus();
            input.select();
            document.execCommand('copy');
            markCopied(copyBtn);
        }
    }

    document.body.addEventListener('click', function (e) {
        var target = e.target;
        if (!target || !target.closest) return;

        var editionCreate = target.closest('#script-edition-create-open');
        if (editionCreate) {
            e.preventDefault();
            e.stopPropagation();
            var dialog = document.getElementById('script-edition-create-dialog');
            if (dialog && typeof dialog.showModal === 'function') {
                dialog.showModal();
                var nameInput = dialog.querySelector('input[name="name"]');
                if (nameInput) setTimeout(function () { nameInput.focus(); }, 0);
            }
            return;
        }

        var editionCancel = target.closest('.script-edition-dialog-cancel');
        if (editionCancel) {
            var cancelDialog = editionCancel.closest('dialog');
            if (cancelDialog) cancelDialog.close();
            return;
        }

        var copyBtn = target.closest('.project-share-copy-btn');
        if (copyBtn) {
            e.preventDefault();
            e.stopPropagation();
            copyShareLink(copyBtn);
            return;
        }

        var matched = findConfigForToggle(target.closest('.nav-dropdown-toggle'));
        if (matched) {
            e.stopPropagation();
            var wasOpen = matched.dropdown.classList.contains('open');
            closeAllDropdowns(matched.dropdown);
            setOpen(matched.dropdown, matched.toggle, !wasOpen);
            return;
        }

        var item = target.closest('.nav-dropdown-item');
        if (item) {
            var parentDropdown = item.closest('.nav-dropdown');
            if (parentDropdown) {
                var keepOpen = false;
                for (var i = 0; i < DROPDOWNS.length; i++) {
                    if (DROPDOWNS[i].id === parentDropdown.id && DROPDOWNS[i].keepOpenOnItemClick) {
                        keepOpen = true;
                        break;
                    }
                }
                if (!keepOpen) {
                    var curr = parentDropdown;
                    while (curr) {
                        var toggleBtn = curr.querySelector('.nav-dropdown-toggle');
                        if (toggleBtn) {
                            setOpen(curr, toggleBtn, false);
                        }
                        curr = curr.parentElement ? curr.parentElement.closest('.nav-dropdown') : null;
                    }
                }
                var alignItem = item.classList.contains('bulk-align-btn') ? item : null;
                if (alignItem && parentDropdown && parentDropdown.id === 'project-text-format-dropdown') {
                    syncTextAlignMenu(alignItem.getAttribute('data-bulk-align'));
                }
                var styleItem = item.classList.contains('bulk-style-btn') ? item : null;
                if (styleItem && parentDropdown && parentDropdown.id === 'project-text-format-dropdown') {
                    var next = !(styleItem.getAttribute('aria-checked') === 'true');
                    styleItem.classList.toggle('is-active', next);
                    styleItem.setAttribute('aria-checked', next ? 'true' : 'false');
                    styleItem.setAttribute('aria-pressed', next ? 'true' : 'false');
                    var anyOn = false;
                    parentDropdown.querySelectorAll('.bulk-style-btn').forEach(function (btn) {
                        if (btn.getAttribute('aria-checked') === 'true') anyOn = true;
                    });
                    var styleToggle = parentDropdown.querySelector('.text-format-toolbar-btn');
                    if (styleToggle) {
                        styleToggle.classList.toggle('is-active', anyOn);
                        styleToggle.setAttribute('aria-pressed', anyOn ? 'true' : 'false');
                    }
                }
            }
        }

        DROPDOWNS.forEach(function (cfg) {
            var dropdown = document.getElementById(cfg.id);
            if (!dropdown || !dropdown.classList.contains('open')) return;
            if (!dropdown.contains(target)) {
                setOpen(dropdown, dropdown.querySelector(cfg.toggle), false);
            }
        });
    });

    document.addEventListener('keydown', function (e) {
        if (e.key !== 'Escape') return;
        DROPDOWNS.forEach(function (cfg) {
            var dropdown = document.getElementById(cfg.id);
            if (!dropdown || !dropdown.classList.contains('open')) return;
            var toggle = dropdown.querySelector(cfg.toggle);
            setOpen(dropdown, toggle, false);
            if (toggle) toggle.focus();
        });
    });

    // Keep align/style toolbar in sync with the focused block (mirrors element-type).
    document.addEventListener('focusin', function (e) {
        if (!e.target || e.target.name !== 'content') return;
        var row = e.target.closest('.block-row, tr[data-block-id], tr:not([data-block-id])');
        if (!row || row.classList.contains('project-script-select-row')) return;
        syncFormatToolbarFromRow(row);
    });

    function sync() {
        syncShareLink();
        var activeId = window.scriptyGetActiveBlockId
            ? window.scriptyGetActiveBlockId(null)
            : null;
        if (activeId) {
            var row = document.querySelector(
                '.block-row[data-block-id="' + activeId + '"], tr[data-block-id="' + activeId + '"]'
            );
            if (row) syncFormatToolbarFromRow(row);
        }
    }

    document.body.addEventListener('htmx:afterSwap', sync);
    document.body.addEventListener('htmx:afterSettle', sync);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sync);
    } else {
        sync();
    }
})();
