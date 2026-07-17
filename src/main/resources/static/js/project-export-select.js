/**
 * Project selection for the Download button on the projects list.
 *
 * Selecting nothing downloads every project the user can see, so a plain click
 * still works for anyone who never touches the checkboxes. Selection follows
 * the search filter: a hidden row is not part of "all", and cannot be picked by
 * "Select all". A single project downloads as one .scripty.json; multiple
 * download as one .zip (handled server-side).
 */
(function () {
    'use strict';

    function init() {
        var table = document.getElementById('table-projects');
        var downloadBtn = document.getElementById('project-list-download-btn');
        if (!table || !downloadBtn) return;
        if (table.dataset.projectExportWired === '1') return;
        table.dataset.projectExportWired = '1';

        var selectAll = document.getElementById('project-list-select-all');
        var scopeEl = document.getElementById('project-list-export-scope');
        var searchInput = document.getElementById('project-list-search');

        function visibleCheckboxes() {
            return Array.prototype.slice
                .call(table.querySelectorAll('.project-list-select-checkbox'))
                .filter(function (cb) {
                    var row = cb.closest('.project-list-row');
                    return row && !row.hidden;
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
                    ? plural(chosen.length, 'project') + ' selected'
                    : 'All projects (' + visible.length + ')';
            }
            if (downloadBtn) {
                downloadBtn.textContent = chosen.length
                    ? 'Download (' + chosen.length + ')'
                    : 'Download';
                downloadBtn.title = chosen.length
                    ? 'Download ' + plural(chosen.length, 'selected project')
                    : 'Download every project as .scripty.json files';
                // Nothing visible means "all projects" resolves to nothing.
                downloadBtn.classList.toggle('is-disabled', visible.length === 0);
                downloadBtn.setAttribute('aria-disabled', visible.length === 0 ? 'true' : 'false');
            }
            if (selectAll) {
                selectAll.checked = visible.length > 0 && chosen.length === visible.length;
                selectAll.indeterminate = chosen.length > 0 && chosen.length < visible.length;
            }
        }

        table.addEventListener('change', function (e) {
            if (e.target.classList.contains('project-list-select-checkbox')) refresh();
        });

        if (selectAll) {
            selectAll.addEventListener('change', function () {
                var check = selectAll.checked;
                visibleCheckboxes().forEach(function (cb) { cb.checked = check; });
                refresh();
            });
        }

        // Searching can hide a checked project; recount so the button never
        // promises projects the download will not include.
        if (searchInput) {
            searchInput.addEventListener('input', function () { window.setTimeout(refresh, 0); });
        }

        downloadBtn.addEventListener('click', function (e) {
            var visible = visibleCheckboxes();
            if (visible.length === 0) {
                e.preventDefault();
                return;
            }
            // No explicit selection downloads every visible project, so the
            // search filter is always respected.
            var ids = selectedIds();
            var sending = ids.length ? ids : visible.map(function (cb) { return cb.value; });

            var url = new URL(downloadBtn.getAttribute('href'), window.location.origin);
            url.searchParams.delete('ids');
            sending.forEach(function (id) { url.searchParams.append('ids', id); });
            downloadBtn.setAttribute('href', url.pathname + url.search);
        });

        refresh();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
