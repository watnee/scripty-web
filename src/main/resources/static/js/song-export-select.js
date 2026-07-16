/**
 * Song selection for the Export menu on the songs list.
 *
 * Selecting nothing exports every song, so Export still works for anyone who
 * never touches the checkboxes. Selection follows the search filter: a hidden
 * card is not part of "all", and cannot be exported by "Select all".
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
