/**
 * Project list delete confirmation modal.
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation.
 */
(function () {
    'use strict';

    if (window._scriptyProjectDeleteInit) return;
    window._scriptyProjectDeleteInit = true;

    function initProjectDeleteConfirm() {
        var modal = document.getElementById('project-delete-modal');
        if (!modal || modal.dataset.scriptyDeleteWired === '1') return;
        modal.dataset.scriptyDeleteWired = '1';

        var nameEl = document.getElementById('project-delete-name');
        var cancelBtn = document.getElementById('project-delete-cancel');
        var confirmBtn = document.getElementById('project-delete-confirm');
        var pendingId = null;

        function closeModal() {
            modal.hidden = true;
            pendingId = null;
        }

        /**
         * Deleting is a POST, so the confirm builds a form rather than
         * navigating. hx-boost is off on it: the response is a redirect to the
         * project list carrying a flash message, which needs a full load.
         */
        function submitDelete(id) {
            var form = document.createElement('form');
            form.method = 'post';
            form.action = '/project/delete';
            form.setAttribute('hx-boost', 'false');
            form.style.display = 'none';

            var input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'id';
            input.value = id;
            form.appendChild(input);

            if (window.scriptyAppendCsrfToForm) window.scriptyAppendCsrfToForm(form);
            document.body.appendChild(form);
            form.submit();
        }

        if (cancelBtn) cancelBtn.addEventListener('click', closeModal);
        if (confirmBtn) {
            confirmBtn.addEventListener('click', function () {
                var id = pendingId;
                closeModal();
                if (id) submitDelete(id);
            });
        }

        modal.addEventListener('click', function (e) {
            if (e.target === modal) closeModal();
        });

        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && !modal.hidden) closeModal();
        });

        document.addEventListener('click', function (e) {
            var btn = e.target.closest('.project-list-delete-btn[data-delete-id], .project-delete-btn[data-delete-id]');
            if (!btn) return;
            e.preventDefault();
            e.stopPropagation();

            pendingId = btn.dataset.deleteId;
            var row = btn.closest('.project-list-row, .project-title-container, h1');
            var titleEl = row ? row.querySelector('.project-list-title, span') : null;
            var title = titleEl && titleEl.textContent ? titleEl.textContent.trim() : 'this project';
            if (nameEl) nameEl.textContent = title;
            modal.hidden = false;
            if (confirmBtn) confirmBtn.focus();
        });
    }

    window.scriptyInitProjectDeleteConfirm = initProjectDeleteConfirm;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initProjectDeleteConfirm);
    } else {
        initProjectDeleteConfirm();
    }
})();
