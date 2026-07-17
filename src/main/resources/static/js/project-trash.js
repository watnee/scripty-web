/**
 * Deleted Projects (trash) page: row selection, bulk actions, and the
 * permanent-delete confirmation modal.
 *
 * Loaded at the bottom of project/trash.html. Safe to run more than once
 * (HTMX-boosted navigation re-runs page scripts) — init is guarded per element.
 */
(function () {
    'use strict';

    function initTrashPage() {
        var bulkForm = document.getElementById('trash-bulk-form');
        if (!bulkForm || bulkForm.dataset.scriptyTrashWired === '1') return;
        bulkForm.dataset.scriptyTrashWired = '1';

        var selectAll = document.getElementById('trash-select-all');
        var restoreSelectedBtn = document.getElementById('trash-restore-selected');
        var purgeSelectedBtn = document.getElementById('trash-purge-selected');
        var countEl = document.getElementById('trash-selection-count');

        var purgeForm = document.getElementById('trash-purge-form');
        var purgeIds = document.getElementById('trash-purge-ids');
        var modal = document.getElementById('trash-purge-modal');
        var modalMessage = document.getElementById('trash-purge-message');
        var cancelBtn = document.getElementById('trash-purge-cancel');
        var confirmBtn = document.getElementById('trash-purge-confirm');

        function rowChecks() {
            return Array.prototype.slice.call(document.querySelectorAll('.trash-select'));
        }

        function checkedIds() {
            return rowChecks().filter(function (c) { return c.checked; })
                .map(function (c) { return c.value; });
        }

        function refreshSelection() {
            var checks = rowChecks();
            var selected = checks.filter(function (c) { return c.checked; });
            var n = selected.length;
            if (restoreSelectedBtn) restoreSelectedBtn.disabled = n === 0;
            if (purgeSelectedBtn) purgeSelectedBtn.disabled = n === 0;
            if (countEl) countEl.textContent = n === 0 ? '' : (n === 1 ? '1 selected' : n + ' selected');
            if (selectAll) {
                selectAll.checked = n > 0 && n === checks.length;
                selectAll.indeterminate = n > 0 && n < checks.length;
            }
        }

        if (selectAll) {
            selectAll.addEventListener('change', function () {
                rowChecks().forEach(function (c) { c.checked = selectAll.checked; });
                refreshSelection();
            });
        }
        rowChecks().forEach(function (c) {
            c.addEventListener('change', refreshSelection);
        });

        // ----- Permanent-delete confirmation modal -----
        var pendingIds = [];

        function openModal(ids, message) {
            pendingIds = ids;
            if (modalMessage) modalMessage.textContent = message;
            if (modal) modal.hidden = false;
            if (confirmBtn) confirmBtn.focus();
        }

        function closeModal() {
            if (modal) modal.hidden = true;
            pendingIds = [];
        }

        function submitPurge(ids) {
            if (!purgeForm || !purgeIds || !ids.length) return;
            purgeIds.innerHTML = '';
            ids.forEach(function (id) {
                var input = document.createElement('input');
                input.type = 'hidden';
                input.name = 'ids';
                input.value = id;
                purgeIds.appendChild(input);
            });
            purgeForm.submit();
        }

        if (cancelBtn) cancelBtn.addEventListener('click', closeModal);
        if (confirmBtn) {
            confirmBtn.addEventListener('click', function () {
                var ids = pendingIds.slice();
                closeModal();
                submitPurge(ids);
            });
        }
        if (modal) {
            modal.addEventListener('click', function (e) {
                if (e.target === modal) closeModal();
            });
        }
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && modal && !modal.hidden) closeModal();
        });

        // Per-row "Delete permanently".
        document.querySelectorAll('.trash-purge-btn').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var id = btn.getAttribute('data-purge-id');
                var title = btn.getAttribute('data-purge-title') || 'this project';
                if (!id) return;
                openModal([id], 'Permanently delete "' + title + '"?');
            });
        });

        // Bulk "Delete selected permanently".
        if (purgeSelectedBtn) {
            purgeSelectedBtn.addEventListener('click', function () {
                var ids = checkedIds();
                if (!ids.length) return;
                var message = ids.length === 1
                    ? 'Permanently delete the selected project?'
                    : 'Permanently delete the ' + ids.length + ' selected projects?';
                openModal(ids, message);
            });
        }

        refreshSelection();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initTrashPage);
    } else {
        initTrashPage();
    }
    document.addEventListener('htmx:load', initTrashPage);
})();
