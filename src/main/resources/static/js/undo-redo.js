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

    // --- Client-side text undo ----------------------------------------------
    //
    // The browser's own textarea undo stack dies whenever HTMX swaps the block
    // markup, and the notes editor has no server-side history at all. So we keep
    // our own per-field stack, keyed by block id (or the notes document) so it
    // survives a re-render of the element it belongs to.

    var STACK_LIMIT = 100;
    var COALESCE_MS = 600;
    var NOTES_TEXTAREA_ID = 'text-document-content';

    var stacks = new Map();
    var lastTextEditKey = null;
    // Set while restoring, so the input event we fire to wake autosave is not
    // mistaken for a fresh edit and pushed back onto the stack.
    var applyingStack = false;

    function stackKeyFor(el) {
        if (!el || el.tagName !== 'TEXTAREA') return null;
        if (el.id === NOTES_TEXTAREA_ID) return 'text-document';
        if (!el.closest) return null;
        var row = el.closest('.block-row[data-block-id]');
        return row ? 'block:' + row.getAttribute('data-block-id') : null;
    }

    function elementForKey(key) {
        if (!key) return null;
        if (key === 'text-document') return document.getElementById(NOTES_TEXTAREA_ID);
        var blockId = key.indexOf('block:') === 0 ? key.slice(6) : null;
        if (!blockId) return null;
        var selector = '.block-row[data-block-id="' + blockId.replace(/"/g, '\\"') + '"]';
        var row = document.querySelector(selector);
        return row ? row.querySelector('textarea') : null;
    }

    function snapshotOf(el) {
        return { value: el.value, start: el.selectionStart, end: el.selectionEnd };
    }

    /** entries[index] always mirrors the field's current value. */
    function getStack(key, el) {
        var stack = stacks.get(key);
        if (!stack) {
            stack = { entries: [snapshotOf(el)], index: 0, lastPush: 0 };
            stacks.set(key, stack);
        }
        return stack;
    }

    function captureEdit(el) {
        var key = stackKeyFor(el);
        if (!key) return;
        var stack = getStack(key, el);
        // Editing after an undo discards the redo branch, as every editor does.
        if (stack.index < stack.entries.length - 1) {
            stack.entries.length = stack.index + 1;
        }
        var now = Date.now();
        if (now - stack.lastPush < COALESCE_MS && stack.index > 0) {
            // Fold a burst of typing into one undo step.
            stack.entries[stack.index] = snapshotOf(el);
        } else {
            stack.entries.push(snapshotOf(el));
            if (stack.entries.length > STACK_LIMIT) {
                stack.entries.shift();
            }
            stack.index = stack.entries.length - 1;
        }
        stack.lastPush = now;
        lastTextEditKey = key;
    }

    function applyStack(el, key, direction) {
        var stack = stacks.get(key);
        if (!stack) return false;
        var target = stack.index + direction;
        if (target < 0 || target >= stack.entries.length) return false;
        stack.index = target;
        var entry = stack.entries[target];
        el.value = entry.value;
        try {
            el.setSelectionRange(entry.start, entry.end);
        } catch (e) { /* detached or unsupported field */ }
        // Never coalesce the next keystroke onto a restored state.
        stack.lastPush = 0;
        // Let autosave and autosize react as if the user had typed it.
        applyingStack = true;
        try {
            el.dispatchEvent(new Event('input', { bubbles: true }));
        } finally {
            applyingStack = false;
        }
        return true;
    }

    /**
     * Runs a text edit so it lands in both the native and our own undo stack:
     * execCommand('insertText') fires an input event, whereas assigning .value
     * fires nothing and silently wipes the browser's history for the field.
     */
    function replaceRange(el, start, end, text) {
        if (!el) return;
        el.focus();
        try {
            el.setSelectionRange(start, end);
        } catch (e) { /* unsupported field */ }
        var inserted = false;
        try {
            inserted = document.execCommand('insertText', false, text);
        } catch (e) {
            inserted = false;
        }
        if (!inserted) {
            var value = el.value;
            el.value = value.slice(0, start) + text + value.slice(end);
            el.setSelectionRange(start + text.length, start + text.length);
            el.dispatchEvent(new Event('input', { bubbles: true }));
        }
    }

    /**
     * Resolves the field an undo should act on: the focused one, or the one the
     * user was last typing in if an HTMX swap has since stolen focus.
     */
    function resolveTextTarget() {
        var active = document.activeElement;
        var activeKey = stackKeyFor(active);
        if (activeKey) return { el: active, key: activeKey };
        if (!lastTextEditKey) return null;
        var el = elementForKey(lastTextEditKey);
        if (!el) {
            // The field is gone (block saved back to display mode, or deleted).
            // Its stack is unusable, and for blocks the server checkpoint taken
            // at save time is now the correct undo target, so let it escalate.
            if (lastTextEditKey !== 'text-document') {
                stacks.delete(lastTextEditKey);
                lastTextEditKey = null;
            }
            return null;
        }
        return { el: el, key: lastTextEditKey };
    }

    document.addEventListener('input', function (e) {
        if (applyingStack) return;
        captureEdit(e.target);
    }, true);

    document.addEventListener('focusin', function (e) {
        // Seed a baseline so the first undo can get back to the pre-typing text.
        var key = stackKeyFor(e.target);
        if (key) getStack(key, e.target);
    }, true);

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

    /**
     * Routes an undo/redo to the narrowest thing that can service it: our own
     * text stack, then the browser's native field history, then project history.
     */
    function performHistoryAction(action) {
        var dropdown = document.getElementById('history-dropdown');
        if (dropdown) {
            dropdown.classList.remove('open');
            var toggle = dropdown.querySelector('.nav-dropdown-toggle');
            if (toggle) toggle.setAttribute('aria-expanded', 'false');
        }

        var target = resolveTextTarget();
        if (target) {
            target.el.focus();
            if (applyStack(target.el, target.key, action === 'undo' ? -1 : 1)) {
                return;
            }
            // Notes have no server-side history, so there is nothing coarser to
            // fall back to — escalating would revert the whole project instead.
            if (target.key === 'text-document') {
                showHistoryToast(action === 'undo' ? 'Nothing to undo' : 'Nothing to redo');
                return;
            }
        }

        var activeEl = document.activeElement;
        if (isInlineTextEdit(activeEl)) {
            activeEl.focus();
            document.execCommand(action);
            return;
        }

        performServerHistoryAction(action);
    }

    function performServerHistoryAction(action) {
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
        // Without the toolbar there is no project history, but a text field may
        // still have a stack of its own (the notes editor has no toolbar).
        if (!undoBtn || !redoBtn) {
            if (!resolveTextTarget()) return;
        }

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
        performHistoryAction(action);
    });

    window.scriptyAbortUndoStatusFetch = abortUndoStatusFetch;
    window.scriptyUpdateUndoRedoButtons = updateUndoRedoButtons;
    window.scriptyPerformHistoryAction = performHistoryAction;
    window.scriptyInitHistoryDropdown = initHistoryDropdown;
    window.scriptyShowHistoryToast = showHistoryToast;

    document.body.addEventListener('htmx:afterSwap', initHistoryDropdown);

    // A block mutation that does not swap the nav used to leave the Undo/Redo
    // items showing whatever state they had at page load. Re-poll after any
    // successful mutating request, debounced so a burst costs one round trip.
    var statusRefreshTimer = null;

    function scheduleUndoStatusRefresh() {
        if (statusRefreshTimer) clearTimeout(statusRefreshTimer);
        statusRefreshTimer = setTimeout(function () {
            statusRefreshTimer = null;
            var projectId = resolveProjectId();
            if (projectId) updateUndoRedoButtons(projectId);
        }, 250);
    }

    document.body.addEventListener('htmx:afterRequest', function (e) {
        var detail = e.detail || {};
        var config = detail.requestConfig || {};
        var verb = (config.verb || '').toUpperCase();
        if (verb === 'GET' || !detail.successful) return;
        var path = config.path || '';
        if (path.indexOf('/block/') !== 0 && path.indexOf('/project/') !== 0) return;
        scheduleUndoStatusRefresh();
    });

    window.scriptyRefreshUndoStatus = scheduleUndoStatusRefresh;
    window.scriptyReplaceRange = replaceRange;

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
