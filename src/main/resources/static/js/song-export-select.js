/**
 * Song selection for the Export menu, the Email button, and the Delete button on
 * the songs list.
 *
 * Selecting nothing acts on every song for Export and Email, so both still work
 * for anyone who never touches the checkboxes. Delete is the exception: it stays
 * disabled until songs are picked, so an empty selection can never wipe a project.
 * Selection follows the search filter: a hidden card is not part of "all", and
 * cannot be picked by "Select all".
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
            return link ? link.textContent.trim() : 'this song';
        }

        function refresh() {
            var visible = visibleCheckboxes();
            var chosen = visible.filter(function (cb) { return cb.checked; });

            if (scopeEl) {
                scopeEl.textContent = chosen.length
                    ? plural(chosen.length, 'song') + ' selected'
                    : 'All songs (' + visible.length + ')';
            }
            if (toggleBtn) {
                toggleBtn.textContent = chosen.length ? 'Export (' + chosen.length + ')' : 'Export';
                toggleBtn.title = chosen.length
                    ? 'Export ' + plural(chosen.length, 'selected song')
                    : 'Export every song in this project';
            }
            if (emailBtn) {
                emailBtn.textContent = chosen.length ? 'Email (' + chosen.length + ')' : 'Email';
                emailBtn.title = chosen.length
                    ? 'Email ' + plural(chosen.length, 'selected song') + ' in one message'
                    : 'Email every song in this project';
                // Nothing visible means "all songs" resolves to nothing to send.
                emailBtn.disabled = visible.length === 0;
            }
            if (deleteBtn) {
                deleteBtn.textContent = chosen.length ? 'Delete (' + chosen.length + ')' : 'Delete';
                deleteBtn.title = chosen.length
                    ? 'Delete ' + plural(chosen.length, 'selected song')
                    : 'Select songs to delete';
                deleteBtn.disabled = chosen.length === 0;
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

        // Filtering can hide a checked song; recount so the menu never promises
        // songs the export will not include.
        if (searchInput) {
            searchInput.addEventListener('input', function () { window.setTimeout(refresh, 0); });
        }

        if (emailBtn && emailForm) {
            emailBtn.addEventListener('click', function () {
                // Selecting nothing emails every visible song, matching Export.
                var ids = selectedIds();
                var sending = ids.length
                    ? ids
                    : visibleCheckboxes().map(function (cb) { return cb.value; });
                if (!sending.length) return;

                var label = sending.length === 1
                    ? '"' + titleOf(sending[0]) + '"'
                    : plural(sending.length, 'song');
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
                    : plural(ids.length, 'song');
                if (!window.confirm('Delete ' + label + '? This cannot be undone.')) return;

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
