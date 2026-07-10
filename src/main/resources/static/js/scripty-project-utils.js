/**
 * Shared project/editor helpers used by nav and feature modules.
 *
 * Loaded early from nav.html (before the main editor IIFE) so callers can
 * rely on window.scripty* APIs during init.
 */
(function () {
    'use strict';

    if (window._scriptyProjectUtilsInit) return;
    window._scriptyProjectUtilsInit = true;

    function resolveProjectId() {
        if (window.scriptyProjectId) {
            return String(window.scriptyProjectId);
        }
        var params = new URLSearchParams(window.location.search);
        if (window.location.pathname.indexOf('/project/show') === 0 && params.has('id')) {
            return params.get('id');
        }
        if (params.has('projectId')) {
            return params.get('projectId');
        }
        var projectLink = document.querySelector('a[href*="/project/show?id="], a[href*="/project/show/"]');
        if (projectLink) {
            var href = projectLink.getAttribute('href');
            var match = href && href.match(/[\?&]id=(\d+)/);
            if (match) {
                return match[1];
            }
        }
        return null;
    }

    function resolveEditionId() {
        if (window.scriptyEditionId) {
            return String(window.scriptyEditionId);
        }
        var params = new URLSearchParams(window.location.search);
        if (params.has('editionId')) {
            return params.get('editionId');
        }
        var projectId = resolveProjectId();
        if (projectId) {
            try {
                var stored = localStorage.getItem('scripty-edition-project-' + projectId);
                if (stored) return stored;
            } catch (e) { /* ignore */ }
        }
        return null;
    }

    function calcDropPosition(dragRow, targetRow, clientY) {
        var dragOrder = parseInt(dragRow.getAttribute('data-block-order'), 10);
        var targetOrder = parseInt(targetRow.getAttribute('data-block-order'), 10);
        if (Number.isNaN(dragOrder) || Number.isNaN(targetOrder)) {
            return null;
        }
        var rect = targetRow.getBoundingClientRect();
        var effectiveHeight = Math.max(rect.height, 24);
        var insertBefore = clientY < rect.top + effectiveHeight / 2;
        var newOrder = insertBefore ? targetOrder : targetOrder + 1;
        if (dragOrder < newOrder) {
            newOrder--;
        }
        return newOrder;
    }

    function findBlockRow(el) {
        if (!el || !el.closest) return null;
        return el.closest('tr[data-block-id], .block-row[data-block-id]');
    }

    function getBlockRowId(row) {
        if (!row || !row.isConnected || row.classList.contains('filtered-out') || row.style.display === 'none') {
            return null;
        }
        var id = row.getAttribute('data-block-id');
        return id && /^\d+$/.test(id) ? id : null;
    }

    function reindexBlockOrders(container) {
        if (!container) return;
        container.querySelectorAll('[data-block-id]').forEach(function (row, index) {
            row.setAttribute('data-block-order', String(index + 1));
        });
    }

    function updateMoveButtonStates(container) {
        if (!container) return;
        var rows = container.querySelectorAll('[data-block-id]');
        rows.forEach(function (row, index) {
            var up = row.querySelector('.block-move-up');
            var down = row.querySelector('.block-move-down');
            if (up) up.style.display = index === 0 ? 'none' : '';
            if (down) down.style.display = index === rows.length - 1 ? 'none' : '';
        });
    }

    function runWhenHtmxIdle(fn) {
        if (!document.querySelector('.htmx-request')) {
            fn();
            return;
        }
        setTimeout(function () { runWhenHtmxIdle(fn); }, 50);
    }

    function getActiveBlockId(triggerEl) {
        function findEditingBlockRow() {
            var focused = document.activeElement;
            if (focused && focused.name === 'content') {
                var row = findBlockRow(focused);
                if (row && row.querySelector('.block-content form[hx-post*="/block/editInline"]')) {
                    return row;
                }
            }
            var form = document.querySelector(
                '.block-content form[hx-post*="/block/editInline"]:focus-within'
            );
            return form ? findBlockRow(form) : null;
        }

        function findCaretPreviewBlockRow() {
            var preview = window.scriptyBlockCaretPreviewActive;
            if (preview && preview.row && preview.row.isConnected) {
                return preview.row;
            }
            var openCreate = document.querySelector('.create-below-menu-dropdown.open');
            if (openCreate) {
                var pinnedId = openCreate.dataset.createBelowTargetId;
                if (pinnedId) {
                    var pinned = document.querySelector(
                        '.block-row[data-block-id="' + pinnedId + '"], tr[data-block-id="' + pinnedId + '"]'
                    );
                    if (pinned) return pinned;
                }
                var openRow = openCreate.closest('.block-row[data-block-id], tr[data-block-id]');
                if (openRow) return openRow;
            }
            return document.querySelector('.block-row.block-controls-revealed[data-block-id], tr.block-controls-revealed[data-block-id]');
        }

        function isFormatChrome(el) {
            return !!(el && el.closest && el.closest(
                '.bulk-type-btn, .bulk-align-btn, .bulk-style-btn, .text-align-toolbar-btn, .text-style-toolbar-btn, ' +
                '.text-align-dropdown, .text-style-dropdown, .text-format-actions, .element-type-actions, .project-script-toolbar'
            ));
        }

        function liveBlockRow(row) {
            if (!row) return null;
            if (row.isConnected) return row;
            var id = row.getAttribute && row.getAttribute('data-block-id');
            if (!id || !/^\d+$/.test(id)) return null;
            return document.querySelector(
                '.block-row[data-block-id="' + id + '"], tr[data-block-id="' + id + '"]'
            );
        }

        var lastFocusedEditable = window.scriptyLastFocusedEditable;
        var focusedEditableRow = (function () {
            if (!lastFocusedEditable || !lastFocusedEditable.isConnected) return null;
            if (document.activeElement === lastFocusedEditable) {
                return findBlockRow(lastFocusedEditable);
            }
            // Format toolbar clicks steal focus; recover the block that was being edited.
            if (isFormatChrome(document.activeElement) || isFormatChrome(triggerEl)) {
                return findBlockRow(lastFocusedEditable);
            }
            return null;
        })();

        var candidates = [
            findEditingBlockRow(),
            findCaretPreviewBlockRow(),
            findBlockRow(document.activeElement),
            focusedEditableRow,
            liveBlockRow(window.scriptyLastActiveBlockRow),
            window.scriptyHoveredRow,
            findBlockRow(triggerEl)
        ];
        for (var i = 0; i < candidates.length; i++) {
            var id = getBlockRowId(candidates[i]);
            if (id) return id;
        }
        return null;
    }

    window.scriptyResolveProjectId = resolveProjectId;
    window.scriptyResolveEditionId = resolveEditionId;
    window.scriptyCalcDropPosition = calcDropPosition;
    window.scriptyFindBlockRow = findBlockRow;
    window.scriptyGetBlockRowId = getBlockRowId;
    window.scriptyReindexBlockOrders = reindexBlockOrders;
    window.scriptyUpdateMoveButtonStates = updateMoveButtonStates;
    window.scriptyRunWhenHtmxIdle = runWhenHtmxIdle;
    window.scriptyGetActiveBlockId = getActiveBlockId;
})();
