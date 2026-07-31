/**
 * Row selection for the Export menu, the Email button, and the Archive and
 * Delete buttons — on the notes list as much as the songs list, since the
 * parity round gave both the same checkbox column.
 *
 * Every label this writes takes its noun from the list's own `data-doc-noun`,
 * so the same code says "3 songs selected" on one page and "3 notes selected"
 * on the other. Nothing here needs to know which page it is.
 *
 * Selecting nothing acts on every row for Export and Email, so both still work
 * for anyone who never touches the checkboxes. Archive and Delete are the
 * exceptions: both stay disabled until rows are picked, so an empty selection
 * can never empty a project. Selection follows the search filter: a hidden card
 * is not part of "all", and cannot be picked by "Select all".
 */
(function () {
    'use strict';

    function init() {
        var dropdown = document.getElementById('songs-export-dropdown');
        var listEl = document.getElementById('text-documents-list');
        if (!dropdown || !listEl) return;
        if (dropdown.dataset.songExportWired === '1') return;
        dropdown.dataset.songExportWired = '1';

        var selectAll = document.getElementById('text-documents-select-all');
        var scopeEl = document.getElementById('songs-export-scope');
        var toggleBtn = document.getElementById('songs-export-toggle');
        var searchInput = document.getElementById('text-documents-search');
        var emailBtn = document.getElementById('songs-email-selected');
        var emailForm = document.getElementById('songs-email-form');
        var deleteBtn = document.getElementById('songs-delete-selected');
        var deleteForm = document.getElementById('songs-delete-form');
        var archiveBtn = document.getElementById('songs-archive-selected');
        var archiveForm = document.getElementById('songs-archive-form');

        // "song" or "note" — whichever list this is. Everything below builds
        // its wording out of these two.
        var noun = listEl.getAttribute('data-doc-noun') || 'song';
        var nouns = noun + 's';

        function visibleCheckboxes() {
            return Array.prototype.slice
                .call(listEl.querySelectorAll('.text-document-select-checkbox'))
                .filter(function (cb) {
                    var card = cb.closest('.text-document-card');
                    return card && !card.hidden;
                });
        }

        function selectedIds() {
            return visibleCheckboxes()
                .filter(function (cb) { return cb.checked; })
                .map(function (cb) { return cb.value; });
        }

        function plural(n, word) {
            return n + ' ' + word + (n === 1 ? '' : 's');
        }

        function titleOf(id) {
            var cb = listEl.querySelector('.text-document-select-checkbox[value="' + id + '"]');
            var card = cb && cb.closest('.text-document-card');
            var link = card && card.querySelector('.text-document-card-title');
            return link ? link.textContent.trim() : 'this ' + noun;
        }

        function refresh() {
            var visible = visibleCheckboxes();
            var chosen = visible.filter(function (cb) { return cb.checked; });

            if (scopeEl) {
                scopeEl.textContent = chosen.length
                    ? plural(chosen.length, noun) + ' selected'
                    : 'All ' + nouns + ' (' + visible.length + ')';
            }
            if (toggleBtn) {
                toggleBtn.textContent = chosen.length ? 'Export (' + chosen.length + ')' : 'Export';
                toggleBtn.title = chosen.length
                    ? 'Export ' + plural(chosen.length, 'selected ' + noun)
                    : 'Export every ' + noun + ' in this project';
            }
            if (emailBtn) {
                emailBtn.textContent = chosen.length ? 'Email (' + chosen.length + ')' : 'Email';
                emailBtn.title = chosen.length
                    ? 'Email ' + plural(chosen.length, 'selected ' + noun) + ' in one message'
                    : 'Email every ' + noun + ' in this project';
                // Nothing visible means "all of them" resolves to nothing to send.
                emailBtn.disabled = visible.length === 0;
            }
            if (deleteBtn) {
                deleteBtn.textContent = chosen.length ? 'Delete (' + chosen.length + ')' : 'Delete';
                deleteBtn.title = chosen.length
                    ? 'Delete ' + plural(chosen.length, 'selected ' + noun)
                    : 'Select ' + nouns + ' to delete';
                deleteBtn.disabled = chosen.length === 0;
            }
            if (archiveBtn) {
                archiveBtn.textContent = chosen.length ? 'Archive (' + chosen.length + ')' : 'Archive';
                archiveBtn.title = chosen.length
                    ? 'Archive ' + plural(chosen.length, 'selected ' + noun)
                    : 'Select ' + nouns + ' to archive';
                archiveBtn.disabled = chosen.length === 0;
            }
            if (selectAll) {
                selectAll.checked = visible.length > 0 && chosen.length === visible.length;
                selectAll.indeterminate = chosen.length > 0 && chosen.length < visible.length;
            }
        }

        listEl.addEventListener('change', function (e) {
            if (e.target.classList.contains('text-document-select-checkbox')) refresh();
        });

        if (selectAll) {
            selectAll.addEventListener('change', function () {
                var check = selectAll.checked;
                visibleCheckboxes().forEach(function (cb) { cb.checked = check; });
                refresh();
            });
        }

        // Filtering can hide a checked row; recount so the menu never promises
        // documents the export will not include.
        if (searchInput) {
            searchInput.addEventListener('input', function () { window.setTimeout(refresh, 0); });
        }

        if (emailBtn && emailForm) {
            emailBtn.addEventListener('click', function () {
                // Selecting nothing emails every visible row, matching Export.
                var ids = selectedIds();
                var sending = ids.length
                    ? ids
                    : visibleCheckboxes().map(function (cb) { return cb.value; });
                if (!sending.length) return;

                var label = sending.length === 1
                    ? '"' + titleOf(sending[0]) + '"'
                    : plural(sending.length, noun);
                var address = window.prompt('Email ' + label + ' to:', '');
                if (address === null) return;
                address = address.trim();
                if (!address || address.indexOf('@') === -1) {
                    if (address) window.alert('Please enter a valid email address.');
                    return;
                }

                emailForm.querySelector('input[name="email"]').value = address;
                Array.prototype.slice.call(emailForm.querySelectorAll('input[name="id"]'))
                    .forEach(function (old) { old.remove(); });
                sending.forEach(function (id) {
                    var field = document.createElement('input');
                    field.type = 'hidden';
                    field.name = 'id';
                    field.value = id;
                    emailForm.appendChild(field);
                });
                emailForm.submit();
            });
        }

        if (deleteBtn && deleteForm) {
            deleteBtn.addEventListener('click', function () {
                var ids = selectedIds();
                if (!ids.length) return;

                var label = ids.length === 1
                    ? '"' + titleOf(ids[0]) + '"'
                    : plural(ids.length, noun);
                var pronoun = ids.length === 1 ? 'it' : 'them';
                if (!window.confirm('Move ' + label + ' to the trash? You can restore '
                        + pronoun + ' from there at any time.')) return;

                Array.prototype.slice.call(deleteForm.querySelectorAll('input[name="id"]'))
                    .forEach(function (old) { old.remove(); });
                ids.forEach(function (id) {
                    var field = document.createElement('input');
                    field.type = 'hidden';
                    field.name = 'id';
                    field.value = id;
                    deleteForm.appendChild(field);
                });
                deleteForm.submit();
            });
        }

        if (archiveBtn && archiveForm) {
            archiveBtn.addEventListener('click', function () {
                var ids = selectedIds();
                if (!ids.length) return;

                // No confirm dialog, unlike Delete: archiving loses nothing and
                // the archive page puts any of it back in one click.
                Array.prototype.slice.call(archiveForm.querySelectorAll('input[name="id"]'))
                    .forEach(function (old) { old.remove(); });
                ids.forEach(function (id) {
                    var field = document.createElement('input');
                    field.type = 'hidden';
                    field.name = 'id';
                    field.value = id;
                    archiveForm.appendChild(field);
                });
                archiveForm.submit();
            });
        }

        dropdown.addEventListener('click', function (e) {
            var link = e.target.closest('.song-export-link');
            if (!link) return;
            var url = new URL(link.getAttribute('href'), window.location.origin);
            url.searchParams.delete('ids');
            selectedIds().forEach(function (id) { url.searchParams.append('ids', id); });
            link.setAttribute('href', url.pathname + url.search);
        });

        refresh();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
