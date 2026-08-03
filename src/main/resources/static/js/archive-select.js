/**
 * Row selection on the two archive pages — songs and notes, and screenplays.
 *
 * Its own script rather than a share of `song-export-select.js`, which is built
 * around the export dropdown and bails without one. What an archive needs is
 * the small half of that: tick rows, bring them back. No search to filter
 * against, no sort, no drag handle, and exactly one action — so the wiring here
 * is the wiring, not a pile of conditionals over controls this page has none of.
 *
 * Every label takes its noun from the list's own `data-doc-noun`, so the same
 * code says "3 songs selected" on one page, "3 notes" on another and
 * "3 screenplays" on the third, and nothing here has to know which it is on.
 *
 * Unlike Export and Email on the list, selecting nothing does *not* mean
 * everything: the button stays disabled until rows are picked, the same rule
 * Archive and Delete follow there. Emptying an archive is not something anyone
 * should be able to do by pressing one button they did not aim.
 */
(function () {
    'use strict';

    function init() {
        var listEl = document.getElementById('archive-documents-list')
            || document.getElementById('archive-projects-list');
        if (!listEl) return;
        if (listEl.dataset.archiveSelectWired === '1') return;
        listEl.dataset.archiveSelectWired = '1';

        var selectAll = document.getElementById('archive-select-all');
        var button = document.getElementById('archive-unarchive-selected');
        var form = document.getElementById('archive-unarchive-form');
        if (!button || !form) return;

        var noun = listEl.getAttribute('data-doc-noun') || 'item';

        function checkboxes() {
            return Array.prototype.slice
                .call(listEl.querySelectorAll('.text-document-select-checkbox'));
        }

        function selectedIds() {
            return checkboxes()
                .filter(function (cb) { return cb.checked; })
                .map(function (cb) { return cb.value; });
        }

        function plural(n, word) {
            return n + ' ' + word + (n === 1 ? '' : 's');
        }

        function refresh() {
            var all = checkboxes();
            var chosen = all.filter(function (cb) { return cb.checked; });

            button.disabled = chosen.length === 0;
            button.textContent = chosen.length
                ? 'Unarchive ' + chosen.length
                : 'Unarchive';
            button.title = chosen.length
                ? 'Bring ' + plural(chosen.length, 'selected ' + noun) + ' back'
                : 'Select ' + noun + 's to bring back';

            if (selectAll) {
                selectAll.checked = all.length > 0 && chosen.length === all.length;
                selectAll.indeterminate = chosen.length > 0 && chosen.length < all.length;
            }
        }

        listEl.addEventListener('change', function (e) {
            if (e.target.classList.contains('text-document-select-checkbox')) refresh();
        });

        if (selectAll) {
            selectAll.addEventListener('change', function () {
                var check = selectAll.checked;
                checkboxes().forEach(function (cb) { cb.checked = check; });
                refresh();
            });
        }

        button.addEventListener('click', function () {
            var ids = selectedIds();
            if (!ids.length) return;

            // No confirm dialog: nothing is lost coming back out of an archive,
            // and putting them aside again is one click per row.
            Array.prototype.slice.call(form.querySelectorAll('input[name="id"]'))
                .forEach(function (old) { old.remove(); });
            ids.forEach(function (id) {
                var field = document.createElement('input');
                field.type = 'hidden';
                field.name = 'id';
                field.value = id;
                form.appendChild(field);
            });
            form.submit();
        });

        refresh();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
