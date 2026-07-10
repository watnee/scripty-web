/**
 * Project editor toolbar dropdowns (file, lists, view, edition, share).
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
        { id: 'project-view-dropdown', toggle: '.view-toolbar-btn', keepOpenOnItemClick: true },
        { id: 'script-edition-dropdown', toggle: '.script-edition-toggle' },
        { id: 'project-share-dropdown', toggle: '.project-share-toggle' }
    ];

    function closeAllDropdowns() {
        document.querySelectorAll('.nav-dropdown').forEach(function (d) {
            d.classList.remove('open');
            var t = d.querySelector('.nav-dropdown-toggle');
            if (t) t.setAttribute('aria-expanded', 'false');
        });
    }

    function setOpen(dropdown, toggle, isOpen) {
        if (!dropdown || !toggle) return;
        dropdown.classList.toggle('open', isOpen);
        toggle.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
    }

    function findConfigForToggle(toggle) {
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
        if (shareLinkInput && !shareLinkInput.value) {
            shareLinkInput.value = window.location.origin + '/project/show?id=' + projectId;
        }
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
            closeAllDropdowns();
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
                if (!keepOpen && parentDropdown.querySelector('.file-toolbar-btn, .lists-toolbar-btn, .view-toolbar-btn, .script-edition-toggle, .project-share-toggle')) {
                    setOpen(parentDropdown, parentDropdown.querySelector('.nav-dropdown-toggle'), false);
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

    function sync() {
        syncShareLink();
    }

    document.body.addEventListener('htmx:afterSwap', sync);
    document.body.addEventListener('htmx:afterSettle', sync);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sync);
    } else {
        sync();
    }
})();
