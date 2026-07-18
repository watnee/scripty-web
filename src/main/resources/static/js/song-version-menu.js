/**
 * Song version switcher: opens/closes the #song-edition-dropdown and its
 * "New version" / "Rename version" dialogs. Mirrors the screenplay edition
 * dropdown handling in project-toolbar-dropdowns.js, but scoped to the song
 * editor's ids so the two never collide.
 *
 * Loaded from nav.html so it survives HTMX-boosted navigation into the song
 * editor (edit.html script tags do not run under allowScriptTags=false).
 */
(function () {
    'use strict';

    if (window._scriptySongVersionMenuInit) return;
    window._scriptySongVersionMenuInit = true;

    function dropdown() {
        return document.getElementById('song-edition-dropdown');
    }

    function closeDropdown() {
        var d = dropdown();
        if (!d) return;
        d.classList.remove('open');
        var toggle = d.querySelector('.nav-dropdown-toggle');
        if (toggle) toggle.setAttribute('aria-expanded', 'false');
    }

    function openDialog(id) {
        var dialog = document.getElementById(id);
        if (!dialog) return;
        closeDropdown();
        if (typeof dialog.showModal === 'function') {
            dialog.showModal();
        } else {
            dialog.setAttribute('open', 'open');
        }
        var firstInput = dialog.querySelector('input[type="text"]');
        if (firstInput) {
            setTimeout(function () { firstInput.focus(); firstInput.select(); }, 0);
        }
    }

    function closeDialog(dialog) {
        if (!dialog) return;
        if (typeof dialog.close === 'function') {
            dialog.close();
        } else {
            dialog.removeAttribute('open');
        }
    }

    document.addEventListener('click', function (e) {
        if (!e.target || !e.target.closest) return;

        var toggle = e.target.closest('#song-edition-toggle');
        if (toggle) {
            e.preventDefault();
            e.stopPropagation();
            var d = dropdown();
            if (!d) return;
            var open = d.classList.contains('open');
            document.querySelectorAll('.nav-dropdown').forEach(function (other) {
                other.classList.remove('open');
            });
            d.classList.toggle('open', !open);
            toggle.setAttribute('aria-expanded', open ? 'false' : 'true');
            return;
        }

        if (e.target.closest('#song-edition-create-open')) {
            e.preventDefault();
            openDialog('song-edition-create-dialog');
            return;
        }

        var renameBtn = e.target.closest('.song-edition-rename-open');
        if (renameBtn) {
            e.preventDefault();
            var dialog = document.getElementById('song-edition-rename-dialog');
            if (dialog) {
                var idField = dialog.querySelector('input[name="editionId"]');
                var nameField = dialog.querySelector('input[name="name"]');
                if (idField) idField.value = renameBtn.getAttribute('data-edition-id') || '';
                if (nameField) nameField.value = renameBtn.getAttribute('data-edition-name') || '';
            }
            openDialog('song-edition-rename-dialog');
            return;
        }

        var cancel = e.target.closest('.script-edition-dialog-cancel');
        if (cancel) {
            e.preventDefault();
            closeDialog(cancel.closest('dialog'));
            return;
        }

        // Click outside the open dropdown closes it.
        var d2 = dropdown();
        if (d2 && d2.classList.contains('open') && !d2.contains(e.target)) {
            closeDropdown();
        }
    });

    // Clicking a <dialog>'s backdrop closes it.
    document.addEventListener('click', function (e) {
        if (e.target && e.target.tagName === 'DIALOG' && e.target.classList.contains('script-edition-dialog')) {
            closeDialog(e.target);
        }
    });

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && dropdown() && dropdown().classList.contains('open')) {
            closeDropdown();
        }
    });
})();
