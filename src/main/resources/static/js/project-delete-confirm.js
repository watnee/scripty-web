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
        var pendingUrl = null;

        function closeModal() {
            modal.hidden = true;
            pendingUrl = null;
        }

        if (cancelBtn) cancelBtn.addEventListener('click', closeModal);
        if (confirmBtn) {
            confirmBtn.addEventListener('click', function () {
                if (pendingUrl) window.location.href = pendingUrl;
                closeModal();
            });
        }

        modal.addEventListener('click', function (e) {
            if (e.target === modal) closeModal();
        });

        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && !modal.hidden) closeModal();
        });

        document.addEventListener('click', function (e) {
            var btn = e.target.closest('.project-list-delete-btn[data-delete-url], .project-delete-btn[data-delete-url]');
            if (!btn) return;
            e.preventDefault();
            e.stopPropagation();

            pendingUrl = btn.dataset.deleteUrl;
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
