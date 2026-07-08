/**
 * Backspace/Delete in an empty block textarea deletes the block (saved blocks
 * via /block/deleteInline, unsaved create rows are just removed) and keeps
 * focus in a neighboring block. Also prevents blank content from being saved
 * and Backspace from triggering browser back-navigation.
 * Loaded on the screenplay page with a cache-busted asset version.
 */
(function() {
    'use strict';

    function isEditable(el) {
        if (!el) return false;
        var tagName = el.tagName;
        return tagName === 'TEXTAREA' ||
            (tagName === 'INPUT' && ['text', 'search', 'url', 'tel', 'email', 'password', 'number'].indexOf(el.type) !== -1) ||
            el.isContentEditable;
    }

    function isBlockContentTextarea(el) {
        return !!el && el.tagName === 'TEXTAREA' && el.name === 'content' &&
            !!el.closest('.block-content, .block-row[data-block-id], tr[data-block-id]');
    }

    function findAnyBlockRow(el) {
        if (!el) return null;
        var savedRow = el.closest('tr[data-block-id], .block-row[data-block-id]');
        if (savedRow) return savedRow;
        return el.closest('.scene-blocks .block-row, #table-blocks tr, tbody tr');
    }

    function isCreateBlockTextarea(el) {
        if (!el || el.tagName !== 'TEXTAREA') return false;
        var row = findAnyBlockRow(el);
        return !!row && !row.hasAttribute('data-block-id');
    }

    function isEmptyBlockTextarea(textarea) {
        return textarea.value.trim() === '' &&
            textarea.selectionStart === textarea.selectionEnd;
    }

    function findAdjacentBlockRow(row, direction) {
        if (!row) return null;
        var isUp = direction === 'moveUp';
        var sibling = isUp ? row.previousElementSibling : row.nextElementSibling;
        while (sibling) {
            if (sibling.classList.contains('block-row') || sibling.hasAttribute('data-block-id')) return sibling;
            sibling = isUp ? sibling.previousElementSibling : sibling.nextElementSibling;
        }
        return null;
    }

    function findFocusTargetRow(row, key) {
        var primaryDirection = key === 'Delete' ? 'moveDown' : 'moveUp';
        var fallbackDirection = primaryDirection === 'moveDown' ? 'moveUp' : 'moveDown';
        return findAdjacentBlockRow(row, primaryDirection) || findAdjacentBlockRow(row, fallbackDirection);
    }

    function focusBlockContentEnd(row) {
        if (!row) return;
        var blockContent = row.querySelector('.block-content');
        if (!blockContent) return;
        var existingTa = blockContent.querySelector('textarea[name="content"]');
        if (existingTa) {
            existingTa.focus({ preventScroll: true });
            var len = existingTa.value.length;
            existingTa.setSelectionRange(len, len);
            return;
        }
        if (!row.hasAttribute('data-block-id')) return;
        var blockId = row.getAttribute('data-block-id');
        if (window.scriptyIsOffline && window.scriptyIsOffline() && window.scriptyOpenBlockEditOffline) {
            window.scriptyOpenBlockEditOffline(blockContent);
            var ta = blockContent.querySelector('textarea[name="content"]');
            if (!ta) return;
            ta.focus({ preventScroll: true });
            var len = ta.value.length;
            ta.setSelectionRange(len, len);
            return;
        }
        fetch('/block/editInline?id=' + encodeURIComponent(blockId), {
            credentials: 'same-origin',
            cache: 'no-store'
        }).then(function(response) {
            return response.text();
        }).then(function(html) {
            if (!blockContent.isConnected) return;
            blockContent.innerHTML = html;
            if (typeof htmx !== 'undefined') {
                htmx.process(blockContent);
            }
            var ta = blockContent.querySelector('textarea[name="content"]');
            if (!ta) return;
            ta.focus({ preventScroll: true });
            var len = ta.value.length;
            ta.setSelectionRange(len, len);
        }).catch(function() {
            blockContent.click();
        });
    }

    function removeEmptyCreateRow(row) {
        if (!row || row.hasAttribute('data-block-id')) return false;
        var container = row.closest('.scene-blocks, #table-blocks, tbody');
        if (!container) return false;
        var rowSelector = container.matches('tbody') ? 'tr' : '.block-row';
        var savedBlocks = container.querySelectorAll(rowSelector + '[data-block-id]').length;
        if (savedBlocks === 0 && container.querySelectorAll(rowSelector).length <= 1) return false;
        row.remove();
        return true;
    }

    function revertEmptySavedBlockEdit(form) {
        if (!form || form.dataset.reverting === 'true') return;
        var row = form.closest('[data-block-id]');
        var blockContent = form.closest('.block-content');
        if (!row || !blockContent) return;
        if (!row.isConnected || row.dataset.deleting === 'true') return;
        var textarea = form.querySelector('textarea[name="content"]');
        if (!textarea || textarea.value.trim() !== '') return;

        if (window.scriptyIsOffline && window.scriptyIsOffline() && window.scriptyRevertBlockEditOffline) {
            form.dataset.reverting = 'true';
            window.scriptyRevertBlockEditOffline(form).finally(function () {
                delete form.dataset.reverting;
            });
            return;
        }

        form.dataset.reverting = 'true';
        var blockId = row.getAttribute('data-block-id');
        fetch('/block/showInline?id=' + encodeURIComponent(blockId), {
            credentials: 'same-origin',
            cache: 'no-store'
        }).then(function(response) {
            return response.text();
        }).then(function(html) {
            if (blockContent.isConnected) {
                blockContent.innerHTML = html;
                if (typeof htmx !== 'undefined') {
                    htmx.process(blockContent);
                }
            }
        }).catch(function() {
            /* keep edit form if revert fails */
        }).finally(function() {
            delete form.dataset.reverting;
        });
    }

    function onEmptyBlockDeleteKey(ta, key) {
        var row = findAnyBlockRow(ta);
        if (!row) return;
        var focusTarget = findFocusTargetRow(row, key);

        if (!row.hasAttribute('data-block-id')) {
            if (removeEmptyCreateRow(row)) {
                focusBlockContentEnd(focusTarget);
            }
            return;
        }

        if (row.dataset.deleting === 'true') return;
        row.dataset.deleting = 'true';
        fetch('/block/deleteInline?id=' + encodeURIComponent(row.getAttribute('data-block-id')), {
            method: 'POST',
            credentials: 'same-origin'
        }).then(function(response) {
            if (!response.ok) throw new Error('delete failed');
            row.remove();
            focusBlockContentEnd(focusTarget);
        }).catch(function() {
            delete row.dataset.deleting;
            ta.focus({ preventScroll: true });
        });
    }

    function installBlockEmptyGuard() {
        if (window._scriptyBlockEmptyGuardInstalled) return;
        window._scriptyBlockEmptyGuardInstalled = true;

        document.addEventListener('keydown', function(e) {
            if (e.key !== 'Backspace' && e.key !== 'Delete') return;
            var ta = e.target;
            if (!isBlockContentTextarea(ta) || !isEmptyBlockTextarea(ta)) return;

            e.preventDefault();
            e.stopImmediatePropagation();
            onEmptyBlockDeleteKey(ta, e.key);
        }, true);

        document.addEventListener('beforeinput', function(e) {
            var ta = e.target;
            if (!isBlockContentTextarea(ta)) return;
            if (e.inputType !== 'deleteContentBackward' && e.inputType !== 'deleteContentForward') return;
            if (ta.value.trim() !== '' || ta.selectionStart !== ta.selectionEnd) return;

            e.preventDefault();
        }, true);

        document.addEventListener('keydown', function(e) {
            if (e.key !== 'Escape') return;
            var ta = e.target;
            if (!isBlockContentTextarea(ta) || !isCreateBlockTextarea(ta)) return;
            if (ta.value.trim() !== '') return;

            e.preventDefault();
            var row = findAnyBlockRow(ta);
            var focusTarget = findFocusTargetRow(row, 'Backspace');
            if (removeEmptyCreateRow(row)) {
                focusBlockContentEnd(focusTarget);
            }
        });

        document.addEventListener('keydown', function(e) {
            if (e.key !== 'Backspace') return;
            if (isEditable(e.target)) return;
            e.preventDefault();
        });

        document.body.addEventListener('htmx:beforeRequest', function(e) {
            var elt = e.detail.elt;
            if (!elt || elt.tagName !== 'FORM') return;
            var path = (e.detail.pathInfo && e.detail.pathInfo.requestPath) || '';
            var textarea = elt.querySelector('textarea[name="content"]');
            if (!textarea || textarea.value.trim() !== '') return;

            if (path.indexOf('/block/editInline') !== -1 ||
                path.indexOf('/block/createInline') !== -1 ||
                path.indexOf('/block/createBelowInline') !== -1) {
                e.preventDefault();
            }
        });

        document.addEventListener('focusin', function(e) {
            document.querySelectorAll('.block-content form[hx-post*="/block/editInline"]').forEach(function(form) {
                if (form.contains(e.target)) return;
                revertEmptySavedBlockEdit(form);
            });
        });

        document.addEventListener('focusout', function(e) {
            var ta = e.target;
            if (!isBlockContentTextarea(ta)) return;
            var form = ta.closest('form[hx-post*="/block/editInline"]');
            if (!form || ta.value.trim() !== '') return;
            setTimeout(function() {
                if (form.contains(document.activeElement)) return;
                revertEmptySavedBlockEdit(form);
            }, 0);
        });
    }

    window.scriptyInstallBlockEmptyGuard = installBlockEmptyGuard;
    installBlockEmptyGuard();
})();
