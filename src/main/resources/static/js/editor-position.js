/**
 * Persist / restore editor scroll + caret position per project.
 *
 * Uses window.scriptyPendingBlockCaret (owned by the main editor IIFE) when
 * restoring an editing caret. Loaded from nav.html for HTMX survival.
 */
(function () {
    'use strict';

    if (window._scriptyEditorPositionModuleInit) return;
    window._scriptyEditorPositionModuleInit = true;

    var editorPositionRestoredForProjectId = null;
    var editorPositionSaveTimer = null;

    function resolveProjectId() {
        return typeof window.scriptyResolveProjectId === 'function'
            ? window.scriptyResolveProjectId()
            : null;
    }

    function findBlockRow(el) {
        return typeof window.scriptyFindBlockRow === 'function'
            ? window.scriptyFindBlockRow(el)
            : (el && el.closest ? el.closest('tr[data-block-id], .block-row[data-block-id]') : null);
    }

    function getBlockRowId(row) {
        return typeof window.scriptyGetBlockRowId === 'function'
            ? window.scriptyGetBlockRowId(row)
            : (row && row.getAttribute ? row.getAttribute('data-block-id') : null);
    }

    function isProjectShowPage() {
        return window.location.pathname.indexOf('/project/show') === 0;
    }

    function editorPositionStorageKey(projectId) {
        return 'scripty-editor-position-project-' + projectId;
    }

    function readEditorPosition(projectId) {
        if (!projectId) return null;
        try {
            var raw = localStorage.getItem(editorPositionStorageKey(projectId));
            if (!raw) return null;
            var parsed = JSON.parse(raw);
            return parsed && typeof parsed === 'object' ? parsed : null;
        } catch (e) {
            return null;
        }
    }

    function writeEditorPosition(projectId, position) {
        if (!projectId || !position) return;
        try {
            localStorage.setItem(editorPositionStorageKey(projectId), JSON.stringify(position));
        } catch (e) {
            /* quota / private mode */
        }
    }

    function captureEditorPosition() {
        if (!isProjectShowPage()) return null;
        var projectId = resolveProjectId();
        if (!projectId) return null;

        var blockId = null;
        var caretStart = null;
        var caretEnd = null;
        var active = document.activeElement;
        if (active && active.tagName === 'TEXTAREA' && active.name === 'content') {
            var activeRow = findBlockRow(active);
            blockId = getBlockRowId(activeRow);
            if (blockId) {
                caretStart = active.selectionStart;
                caretEnd = active.selectionEnd;
            }
        }
        if (!blockId && window.scriptyGetActiveBlockId) {
            blockId = window.scriptyGetActiveBlockId(null);
        }

        return {
            blockId: blockId,
            caretStart: caretStart,
            caretEnd: caretEnd,
            editing: caretStart != null,
            scrollY: window.scrollY,
            updatedAt: Date.now()
        };
    }

    function saveEditorPosition() {
        var projectId = resolveProjectId();
        if (!projectId || !isProjectShowPage()) return;
        var position = captureEditorPosition();
        if (!position) return;
        var prev = readEditorPosition(projectId);
        if (!position.blockId && prev && prev.blockId) {
            position.blockId = prev.blockId;
            position.caretStart = prev.caretStart;
            position.caretEnd = prev.caretEnd;
        } else if (position.blockId && position.caretStart == null && prev &&
                   String(prev.blockId) === String(position.blockId) && prev.caretStart != null) {
            position.caretStart = prev.caretStart;
            position.caretEnd = prev.caretEnd;
        }
        writeEditorPosition(projectId, position);
    }

    function scheduleSaveEditorPosition(delayMs) {
        if (editorPositionSaveTimer) clearTimeout(editorPositionSaveTimer);
        editorPositionSaveTimer = setTimeout(function () {
            editorPositionSaveTimer = null;
            saveEditorPosition();
        }, delayMs == null ? 200 : delayMs);
    }

    function rememberActiveBlockCaret(blockId) {
        sessionStorage.removeItem('activeBlockCaretStart');
        sessionStorage.removeItem('activeBlockCaretEnd');
        if (!blockId) return;
        var row = document.querySelector(
            '.block-row[data-block-id="' + blockId + '"], tr[data-block-id="' + blockId + '"]'
        );
        if (!row) return;
        var textarea = row.querySelector('textarea[name="content"]');
        if (!textarea) return;
        sessionStorage.setItem('activeBlockCaretStart', String(textarea.selectionStart));
        sessionStorage.setItem('activeBlockCaretEnd', String(textarea.selectionEnd));
        saveEditorPosition();
    }

    function focusRestoredBlock(row, caretStartRaw, caretEndRaw, scrollY) {
        if (!row) return;
        var blockContent = row.querySelector('.block-content');
        if (!blockContent) return;

        var textEl = blockContent.querySelector('.reader-visible-text, .script-block-text:not(textarea)');
        var offset = caretStartRaw != null ? parseInt(caretStartRaw, 10) : null;
        if (textEl && offset != null && !isNaN(offset)) {
            window.scriptyPendingBlockCaret = {
                blockContent: blockContent,
                textEl: textEl,
                offset: Math.max(0, offset),
                endOffset: caretEndRaw != null ? Math.max(0, parseInt(caretEndRaw, 10)) : null
            };
        }

        blockContent.click();

        setTimeout(function () {
            var textarea = row.querySelector('textarea[name="content"]');
            if (textarea) {
                if (typeof window.scriptyGrowTextarea === 'function') {
                    window.scriptyGrowTextarea(textarea);
                }
                textarea.focus({ preventScroll: true });
                if (caretStartRaw != null) {
                    var len = textarea.value.length;
                    var start = Math.max(0, Math.min(parseInt(caretStartRaw, 10), len));
                    var end = Math.max(start, Math.min(parseInt(caretEndRaw != null ? caretEndRaw : caretStartRaw, 10), len));
                    textarea.setSelectionRange(start, end);
                } else {
                    var endLen = textarea.value.length;
                    textarea.setSelectionRange(endLen, endLen);
                }
            }
            if (typeof scrollY === 'number') {
                window.scrollTo(0, scrollY);
            }
        }, 100);
    }

    function restoreActiveBlock() {
        var activeBlockId = sessionStorage.getItem('activeBlockId');
        var caretStartRaw = sessionStorage.getItem('activeBlockCaretStart');
        var caretEndRaw = sessionStorage.getItem('activeBlockCaretEnd');
        if (activeBlockId) {
            sessionStorage.removeItem('activeBlockId');
            sessionStorage.removeItem('activeBlockCaretStart');
            sessionStorage.removeItem('activeBlockCaretEnd');
            var row = document.querySelector('tr[data-block-id="' + activeBlockId + '"], .block-row[data-block-id="' + activeBlockId + '"]');
            focusRestoredBlock(row, caretStartRaw, caretEndRaw, null);
        }
    }

    function restoreEditorPosition() {
        if (!isProjectShowPage()) {
            editorPositionRestoredForProjectId = null;
            return;
        }
        var projectId = resolveProjectId();
        if (!projectId) return;
        if (editorPositionRestoredForProjectId === String(projectId)) return;
        editorPositionRestoredForProjectId = String(projectId);

        if (sessionStorage.getItem('activeBlockId')) {
            restoreActiveBlock();
            return;
        }

        var position = readEditorPosition(projectId);
        if (!position) return;

        if (typeof position.scrollY === 'number') {
            window.scrollTo(0, position.scrollY);
        }

        if (!position.editing || !position.blockId || position.caretStart == null ||
            window.scriptyBlockEditLocked) {
            return;
        }

        var row = document.querySelector(
            'tr[data-block-id="' + position.blockId + '"], .block-row[data-block-id="' + position.blockId + '"]'
        );
        if (!row) return;
        focusRestoredBlock(
            row,
            String(position.caretStart),
            position.caretEnd != null ? String(position.caretEnd) : String(position.caretStart),
            typeof position.scrollY === 'number' ? position.scrollY : null
        );
    }

    function initEditorPositionPersistence() {
        if (window._scriptyEditorPositionPersistenceInit) return;
        window._scriptyEditorPositionPersistenceInit = true;

        window.addEventListener('scroll', function () {
            if (!isProjectShowPage()) return;
            scheduleSaveEditorPosition(200);
        }, { passive: true });

        document.addEventListener('selectionchange', function () {
            if (!isProjectShowPage()) return;
            var active = document.activeElement;
            if (!active || active.tagName !== 'TEXTAREA' || active.name !== 'content') return;
            scheduleSaveEditorPosition(300);
        });

        document.addEventListener('focusin', function (e) {
            if (!isProjectShowPage()) return;
            if (e.target && e.target.tagName === 'TEXTAREA' && e.target.name === 'content') {
                scheduleSaveEditorPosition(0);
            }
        });

        document.addEventListener('visibilitychange', function () {
            if (document.hidden && isProjectShowPage()) {
                saveEditorPosition();
            }
        });

        window.addEventListener('pagehide', function () {
            if (isProjectShowPage()) {
                saveEditorPosition();
            }
        });
    }

    window.scriptySaveEditorPosition = saveEditorPosition;
    window.scriptyScheduleSaveEditorPosition = scheduleSaveEditorPosition;
    window.scriptyRememberActiveBlockCaret = rememberActiveBlockCaret;
    window.scriptyRestoreEditorPosition = restoreEditorPosition;
    window.scriptyRestoreActiveBlock = restoreActiveBlock;
    window.scriptyInitEditorPositionPersistence = initEditorPositionPersistence;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initEditorPositionPersistence);
    } else {
        initEditorPositionPersistence();
    }
})();
