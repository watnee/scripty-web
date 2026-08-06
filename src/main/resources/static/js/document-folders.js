/**
 * Folders on the songs and notes lists: the chip bar that narrows the list to
 * one folder, the per-card control that files a row, and the three buttons that
 * add, rename and remove a folder.
 *
 * Nothing here writes `card.hidden` itself. The chips only remember which
 * folder is showing and ask the page's own search function to re-run — that
 * function is the one place a card's visibility is decided, so a folder and a
 * search term narrow together instead of fighting.
 *
 * Works on both lists without knowing which it is on: every form it posts was
 * rendered with this page's `projectId` and `type` already in it.
 */
(function () {
    'use strict';

    function init() {
        var bar = document.getElementById('text-document-folders');
        var listEl = document.getElementById('text-documents-list');
        if (!bar) return;
        if (bar.dataset.folderWired === '1') return;
        bar.dataset.folderWired = '1';

        var chips = Array.prototype.slice.call(
            bar.querySelectorAll('.text-document-folder-chip'));
        var renameBtn = document.getElementById('text-document-folder-rename');
        var deleteBtn = document.getElementById('text-document-folder-delete');
        var newBtn = document.getElementById('text-document-folder-new');
        var createForm = document.getElementById('text-document-folder-create-form');
        var renameForm = document.getElementById('text-document-folder-rename-form');
        var deleteForm = document.getElementById('text-document-folder-delete-form');
        var moveForm = document.getElementById('text-document-folder-move-form');

        // '' means All. 'none' means the unfiled ones. Anything else is a
        // folder id, as a string, because that is what the cards carry.
        var activeFilter = '';
        var activeName = '';

        window.scriptyDocumentFolders = {
            activeFilter: function () { return activeFilter; },
            activeName: function () { return activeName || 'this folder'; }
        };

        function applyFilter(value, name) {
            activeFilter = value === 'all' ? '' : value;
            activeName = value === 'none' ? 'Unfiled' : (name || '');
            chips.forEach(function (chip) {
                var mine = chip.getAttribute('data-folder-filter') === value;
                chip.classList.toggle('is-active', mine);
                chip.setAttribute('aria-pressed', mine ? 'true' : 'false');
            });
            // Only a real folder can be renamed or removed; "All" and the
            // unfiled pile are not folders, they are ways of looking.
            var isFolder = !!activeFilter && activeFilter !== 'none';
            if (renameBtn) renameBtn.hidden = !isFolder;
            if (deleteBtn) deleteBtn.hidden = !isFolder;
            if (window.scriptyDocumentSearch) {
                window.scriptyDocumentSearch.refresh();
            }
        }

        chips.forEach(function (chip) {
            chip.addEventListener('click', function () {
                applyFilter(chip.getAttribute('data-folder-filter'),
                            chip.getAttribute('data-folder-name'));
            });
        });

        if (newBtn && createForm) {
            newBtn.addEventListener('click', function () {
                var name = window.prompt('Name the folder:', '');
                if (name === null) return;
                name = name.trim();
                if (!name) return;
                createForm.querySelector('input[name="name"]').value = name;
                createForm.submit();
            });
        }

        if (renameBtn && renameForm) {
            renameBtn.addEventListener('click', function () {
                if (!activeFilter || activeFilter === 'none') return;
                var name = window.prompt('Rename the folder to:', activeName);
                if (name === null) return;
                name = name.trim();
                if (!name || name === activeName) return;
                renameForm.querySelector('input[name="id"]').value = activeFilter;
                renameForm.querySelector('input[name="name"]').value = name;
                renameForm.submit();
            });
        }

        if (deleteBtn && deleteForm) {
            deleteBtn.addEventListener('click', function () {
                if (!activeFilter || activeFilter === 'none') return;
                // Says what removing it does *not* do, because that is the only
                // thing anyone hesitates over here.
                if (!window.confirm('Remove the folder "' + activeName
                        + '"? Everything in it stays in the list.')) return;
                deleteForm.querySelector('input[name="id"]').value = activeFilter;
                deleteForm.submit();
            });
        }

        // Filing one row. The shared move form takes a list of ids, so a single
        // card sends a list of one.
        if (listEl && moveForm) {
            listEl.addEventListener('change', function (e) {
                var select = e.target.closest
                    ? e.target.closest('.text-document-folder-select')
                    : null;
                if (!select) return;
                submitMove(select.value, [select.getAttribute('data-doc-id')]);
            });
        }

        // Filing the ticked rows. Lives here rather than in the selection
        // script because it is a folder control; what it borrows from there is
        // only which rows are ticked.
        var bulkSelect = document.getElementById('songs-folder-selected');
        if (bulkSelect && moveForm) {
            bulkSelect.addEventListener('change', function () {
                var choice = bulkSelect.value;
                // The first option is a label, not a destination.
                if (!choice) return;
                var ids = window.scriptyDocumentSelection
                    ? window.scriptyDocumentSelection.selectedIds()
                    : [];
                // Put the control back to its label whatever happens, so it
                // never reads as though a folder is "current".
                bulkSelect.value = '';
                if (!ids.length) return;
                submitMove(choice === 'none' ? '' : choice, ids);
            });
        }

        function submitMove(folderId, ids) {
            moveForm.querySelector('input[name="folderId"]').value = folderId || '';
            Array.prototype.slice.call(moveForm.querySelectorAll('input[name="id"]'))
                .forEach(function (old) { old.remove(); });
            ids.forEach(function (id) {
                if (!id) return;
                var field = document.createElement('input');
                field.type = 'hidden';
                field.name = 'id';
                field.value = id;
                moveForm.appendChild(field);
            });
            moveForm.submit();
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
