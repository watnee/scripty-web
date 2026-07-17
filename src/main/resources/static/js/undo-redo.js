/**
 * Project undo/redo buttons, history dropdown, and ⌘Z / Ctrl+Z shortcuts.
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation.
 */
(function () {
    'use strict';

    if (window._scriptyUndoRedoInit) return;
    window._scriptyUndoRedoInit = true;

    function resolveProjectId() {
        return typeof window.scriptyResolveProjectId === 'function'
            ? window.scriptyResolveProjectId()
            : null;
    }

    function resolveEditionId() {
        return typeof window.scriptyResolveEditionId === 'function'
            ? window.scriptyResolveEditionId()
            : null;
    }

    function isInlineTextEdit(el) {
        if (typeof window.scriptyIsInlineTextEdit === 'function') {
            return window.scriptyIsInlineTextEdit(el);
        }
        if (!el) return false;
        var tag = el.tagName;
        var editable = tag === 'TEXTAREA' ||
            (tag === 'INPUT' && ['text', 'search', 'url', 'tel', 'email', 'password', 'number'].indexOf(el.type) !== -1) ||
            el.isContentEditable;
        if (!editable) return false;
        if (!el.closest || !el.closest('.block-content, .scene-name-wrap, .project-breadcrumb-name-wrap, .project-header-name-wrap')) {
            return false;
        }
        var row = el.closest('.scene-blocks .block-row, #table-blocks tr, tbody tr');
        if (row && !row.hasAttribute('data-block-id') && tag === 'TEXTAREA') {
            return false;
        }
        return true;
    }

    function abortUndoStatusFetch() {
        if (window._undoStatusAbort) {
            window._undoStatusAbort.abort();
            window._undoStatusAbort = null;
        }
    }

    function setHistoryButtonState(btn, available) {
        btn.disabled = false;
        btn.classList.toggle('is-unavailable', !available);
        btn.setAttribute('aria-disabled', available ? 'false' : 'true');
    }

    function updateUndoRedoButtons(projectId) {
        var undoBtn = document.getElementById('nav-undo');
        var redoBtn = document.getElementById('nav-redo');
        if (!undoBtn || !redoBtn || !projectId) return;

        abortUndoStatusFetch();
        var controller = new AbortController();
        window._undoStatusAbort = controller;

        var statusUrl = '/project/undoRedoStatus?projectId=' + projectId;
        var editionId = resolveEditionId();
        if (editionId) {
            statusUrl += '&editionId=' + encodeURIComponent(editionId);
        }

        fetch(statusUrl, {
            cache: 'no-store',
            credentials: 'same-origin',
            signal: controller.signal
        })
            .then(function (response) { return response.json(); })
            .then(function (data) {
                if (window._undoStatusAbort !== controller) return;
                setHistoryButtonState(undoBtn, data.canUndo);
                setHistoryButtonState(redoBtn, data.canRedo);
            })
            .catch(function (err) {
                if (err && err.name === 'AbortError') return;
                setHistoryButtonState(undoBtn, false);
                setHistoryButtonState(redoBtn, false);
            })
            .finally(function () {
                if (window._undoStatusAbort === controller) {
                    window._undoStatusAbort = null;
                }
            });
    }

    var HISTORY_TOAST_KEY = 'scriptyHistoryToast';
    var toastHideTimer = null;

    function showHistoryToast(message) {
        if (!message) return;
        var toast = document.getElementById('scripty-editor-toast');
        if (!toast) {
            toast = document.createElement('div');
            toast.id = 'scripty-editor-toast';
            toast.className = 'scripty-editor-toast';
            toast.setAttribute('role', 'status');
            toast.setAttribute('aria-live', 'polite');
            document.body.appendChild(toast);
        }
        toast.textContent = message;
        toast.hidden = false;
        toast.classList.add('is-visible');
        if (toastHideTimer) {
            clearTimeout(toastHideTimer);
        }
        toastHideTimer = setTimeout(function () {
            toast.classList.remove('is-visible');
            toast.hidden = true;
        }, 3200);
    }

    function historyToastMessage(action, data) {
        var delta = data && typeof data.blockDelta === 'number' ? data.blockDelta : 0;
        if (delta > 0) {
            return 'Restored ' + delta + (delta === 1 ? ' block' : ' blocks');
        }
        return action === 'undo' ? 'Change undone' : 'Change redone';
    }

    // The action triggers a full navigation, so carry the confirmation across the
    // reload in sessionStorage and surface it once the fresh page has loaded.
    function stashHistoryToast(action, data) {
        try {
            sessionStorage.setItem(HISTORY_TOAST_KEY, historyToastMessage(action, data));
        } catch (e) { /* storage unavailable — skip the toast */ }
    }

    function drainHistoryToast() {
        var message = null;
        try {
            message = sessionStorage.getItem(HISTORY_TOAST_KEY);
            if (message !== null) {
                sessionStorage.removeItem(HISTORY_TOAST_KEY);
            }
        } catch (e) {
            return;
        }
        if (message) {
            showHistoryToast(message);
        }
    }

    function performHistoryAction(action) {
        var dropdown = document.getElementById('history-dropdown');
        if (dropdown) {
            dropdown.classList.remove('open');
            var toggle = dropdown.querySelector('.nav-dropdown-toggle');
            if (toggle) toggle.setAttribute('aria-expanded', 'false');
        }

        var activeEl = document.activeElement;
        if (isInlineTextEdit(activeEl)) {
            activeEl.focus();
            document.execCommand(action);
            return;
        }

        var projectId = resolveProjectId();
        if (!projectId) return;
        var editionId = resolveEditionId();
        var actionUrl = '/project/' + action + '?projectId=' + projectId;
        if (editionId) {
            actionUrl += '&editionId=' + encodeURIComponent(editionId);
        }

        fetch(actionUrl, {
            method: 'POST',
            credentials: 'same-origin',
            cache: 'no-store',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
        }).then(function (response) { return response.json(); })
            .then(function (data) {
                if (data.success) {
                    stashHistoryToast(action, data);
                    if (data.moveOnly) {
                        window.location.reload();
                    } else {
                        var showUrl = '/project/show?id=' + projectId;
                        if (editionId) {
                            showUrl += '&editionId=' + encodeURIComponent(editionId);
                        }
                        window.location.href = showUrl;
                    }
                } else {
                    updateUndoRedoButtons(projectId);
                }
            })
            .catch(function () {
                updateUndoRedoButtons(projectId);
            });
    }

    function initHistoryDropdown() {
        var dropdown = document.getElementById('history-dropdown');
        var undoBtn = document.getElementById('nav-undo');
        var redoBtn = document.getElementById('nav-redo');

        if (!dropdown && !undoBtn && !redoBtn) return;

        if (undoBtn && !undoBtn.classList.contains('history-action-btn')) {
            undoBtn.classList.add('history-action-btn');
        }
        if (redoBtn && !redoBtn.classList.contains('history-action-btn')) {
            redoBtn.classList.add('history-action-btn');
        }

        var isMac = typeof window.scriptyIsMac === 'function'
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
        var undoHint = isMac ? '⌘Z' : 'Ctrl+Z';
        var redoHint = isMac ? '⌘⇧Z' : 'Ctrl+Y';
        var undoShortcut = ' (' + undoHint + ')';
        var redoShortcut = ' (' + redoHint + ')';

        if (undoBtn) {
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(undoBtn, undoHint);
            }
            undoBtn.title = 'Undo' + undoShortcut;
            undoBtn.setAttribute('aria-label', 'Undo' + undoShortcut);
            undoBtn.onclick = function (e) {
                e.preventDefault();
                performHistoryAction('undo');
            };
        }

        if (redoBtn) {
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(redoBtn, redoHint);
            }
            redoBtn.title = 'Redo' + redoShortcut;
            redoBtn.setAttribute('aria-label', 'Redo' + redoShortcut);
            redoBtn.onclick = function (e) {
                e.preventDefault();
                performHistoryAction('redo');
            };
        }

        var projectId = resolveProjectId();
        if (!dropdown) {
            updateUndoRedoButtons(projectId);
            return;
        }

        var toggleBtn = dropdown.querySelector('.nav-dropdown-toggle');
        if (!toggleBtn) {
            updateUndoRedoButtons(projectId);
            return;
        }

        if (toggleBtn.dataset.scriptyHistoryWired !== '1') {
            toggleBtn.dataset.scriptyHistoryWired = '1';
            toggleBtn.addEventListener('click', function (e) {
                e.stopPropagation();
                var isOpen = dropdown.classList.contains('open');
                document.querySelectorAll('.nav-dropdown').forEach(function (d) {
                    d.classList.remove('open');
                });
                toggleBtn.setAttribute('aria-expanded', isOpen ? 'false' : 'true');
                if (!isOpen) {
                    dropdown.classList.add('open');
                }
            });

            document.addEventListener('click', function (e) {
                if (!dropdown.contains(e.target)) {
                    dropdown.classList.remove('open');
                    toggleBtn.setAttribute('aria-expanded', 'false');
                }
            });
        }

        if (projectId) {
            dropdown.style.display = 'inline-block';
            updateUndoRedoButtons(projectId);
        } else if (dropdown.style.display !== 'inline-block') {
            dropdown.style.display = 'none';
        }
    }

    document.addEventListener('keydown', function (e) {
        var undoBtn = document.getElementById('nav-undo');
        var redoBtn = document.getElementById('nav-redo');
        if (!undoBtn || !redoBtn) return;

        var isMac = typeof window.scriptyIsMac === 'function'
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
        var hasCmd = e.metaKey;
        var hasCtrl = e.ctrlKey;
        var hasValidModifier = hasCmd || hasCtrl;

        if (!hasValidModifier) return;

        var action = null;
        if (e.key.toLowerCase() === 'z') {
            action = e.shiftKey ? 'redo' : 'undo';
        } else if (e.key.toLowerCase() === 'y') {
            if (hasCtrl || (hasCmd && !isMac)) {
                action = 'redo';
            }
        }

        if (!action) return;
        e.preventDefault();
        var activeEl = document.activeElement;
        if (isInlineTextEdit(activeEl)) {
            activeEl.focus();
            document.execCommand(action);
        } else {
            performHistoryAction(action);
        }
    });

    window.scriptyAbortUndoStatusFetch = abortUndoStatusFetch;
    window.scriptyUpdateUndoRedoButtons = updateUndoRedoButtons;
    window.scriptyPerformHistoryAction = performHistoryAction;
    window.scriptyInitHistoryDropdown = initHistoryDropdown;
    window.scriptyShowHistoryToast = showHistoryToast;

    document.body.addEventListener('htmx:afterSwap', initHistoryDropdown);

    function onInitialLoad() {
        initHistoryDropdown();
        drainHistoryToast();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', onInitialLoad);
    } else {
        onInitialLoad();
    }
})();
