/**
 * Song/note editor: Tab → four spaces, and debounced autosave via stay=true save.
 *
 * Loaded from nav.html so it survives HTMX-boosted navigation
 * (allowScriptTags is false, so edit.html script tags are not executed
 * after a boost). Re-binds to the current form on htmx:afterSettle.
 */
(function () {
    'use strict';

    if (window._scriptyTextDocumentEditInit) return;
    window._scriptyTextDocumentEditInit = true;

    var SAVE_DELAY_MS = 900;
    var current = null; // editor state for the form currently in the DOM

    function initEditor() {
        var form = document.getElementById('text-document-form');
        if (!form) {
            if (current) {
                clearTimeout(current.timer);
                clearTimeout(current.statusTimer);
                current = null;
            }
            return;
        }
        if (current && current.form === form) {
            return;
        }
        if (current) {
            clearTimeout(current.timer);
            clearTimeout(current.statusTimer);
        }
        current = bindEditor(form);
    }

    function bindEditor(form) {
        var statusEl = document.getElementById('text-document-save-status');

        var idInput = form.querySelector('input[name="id"]');
        var projectIdInput = form.querySelector('input[name="projectId"]');
        var typeInput = form.querySelector('input[name="documentType"]');
        var titleInput = document.getElementById('title')
            || (form.elements && form.elements.namedItem('title'))
            || form.querySelector('input[name="title"]');

        var state = {
            form: form,
            timer: null,
            statusTimer: null,
            inFlight: false,
            pending: false,
            saveNow: saveNow,
            isDirty: isDirty,
            hasPending: function () { return state.pending || state.inFlight; }
        };

        function getTa() {
            return document.getElementById('text-document-content');
        }

        // --- Edit menu: local undo/redo history for the content textarea.
        // Distinct from the screenplay's server-side undo (undo-redo.js);
        // ids intentionally differ from nav-undo/nav-redo so that script
        // never treats these as project history buttons.
        var HISTORY_GROUP_MS = 800;
        var HISTORY_LIMIT = 200;
        var hist = { undo: [], redo: [], prev: null, lastEventAt: 0 };

        var editDropdown = document.getElementById('text-doc-edit-dropdown');
        var editToggle = editDropdown ? editDropdown.querySelector('.nav-dropdown-toggle') : null;
        var undoBtn = document.getElementById('text-doc-undo');
        var redoBtn = document.getElementById('text-doc-redo');
        var findOpenBtn = document.getElementById('text-doc-find-open');
        var findPanel = document.getElementById('text-doc-find-panel');
        var findInput = document.getElementById('text-doc-find-input');
        var replaceInput = document.getElementById('text-doc-replace-input');
        var findCountEl = document.getElementById('text-doc-find-count');
        var findCaseBtn = document.getElementById('text-doc-find-case');
        var findPrevBtn = document.getElementById('text-doc-find-prev');
        var findNextBtn = document.getElementById('text-doc-find-next');
        var replaceOneBtn = document.getElementById('text-doc-replace-one');
        var replaceAllBtn = document.getElementById('text-doc-replace-all');
        var findCloseBtn = document.getElementById('text-doc-find-close');
        var hasEditTools = !!(editDropdown && editToggle && findPanel && findInput && replaceInput);

        var fileDropdown = document.getElementById('text-doc-file-dropdown');
        var fileToggle = fileDropdown ? fileDropdown.querySelector('.nav-dropdown-toggle') : null;
        var newLink = document.getElementById('text-doc-new');
        var importBtn = document.getElementById('text-doc-import');
        var importForm = document.getElementById('text-doc-import-form');
        var importFileInput = document.getElementById('text-doc-import-file');
        var insertBtn = document.getElementById('text-doc-insert');
        var insertForm = document.getElementById('text-doc-insert-form');
        var downloadBtn = document.getElementById('text-doc-download');
        var printBtn = document.getElementById('text-doc-print');
        var deleteBtn = document.getElementById('text-doc-delete');
        var deleteForm = document.getElementById('text-doc-delete-form');
        var printSheet = document.getElementById('text-doc-print-sheet');
        var hasFileTools = !!(fileDropdown && fileToggle);

        var findMatches = [];
        var findIndex = -1;
        var findCaseSensitive = false;
        var findAnchor = 0;

        function taSnapshot() {
            var ta = getTa();
            if (!ta) return null;
            return { value: ta.value, start: ta.selectionStart, end: ta.selectionEnd };
        }

        function histPushUndo(snap) {
            if (!snap) return;
            hist.undo.push(snap);
            if (hist.undo.length > HISTORY_LIMIT) hist.undo.shift();
        }

        function histOnInput() {
            var snap = taSnapshot();
            if (!snap) return;
            if (hist.prev && snap.value === hist.prev.value) return;
            var now = Date.now();
            if (now - hist.lastEventAt > HISTORY_GROUP_MS) {
                histPushUndo(hist.prev);
            }
            hist.redo = [];
            hist.lastEventAt = now;
            hist.prev = snap;
        }

        function histBeforeProgrammatic() {
            histPushUndo(taSnapshot());
            hist.redo = [];
            hist.lastEventAt = 0;
        }

        function histAfterProgrammatic() {
            hist.prev = taSnapshot();
        }

        function histApply(snap) {
            var ta = getTa();
            if (!ta || !snap) return;
            ta.value = snap.value;
            try { ta.setSelectionRange(snap.start, snap.end); } catch (err) { /* ignore */ }
            hist.prev = snap;
            hist.lastEventAt = 0;
            ta.focus();
            scrollSelectionIntoView(ta);
            scheduleSave();
            if (findPanelOpen()) {
                computeMatches();
                clampFindIndex();
                updateFindCount('');
            }
        }

        function histUndo() {
            if (!hist.undo.length) return;
            var cur = taSnapshot();
            if (!cur) return;
            hist.redo.push(cur);
            histApply(hist.undo.pop());
        }

        function histRedo() {
            if (!hist.redo.length) return;
            var cur = taSnapshot();
            if (!cur) return;
            histPushUndo(cur);
            histApply(hist.redo.pop());
        }

        function scrollSelectionIntoView(ta) {
            var style = window.getComputedStyle(ta);
            var lineHeight = parseFloat(style.lineHeight);
            if (!lineHeight || isNaN(lineHeight)) {
                lineHeight = (parseFloat(style.fontSize) || 14) * 1.4;
            }
            var lineIndex = ta.value.slice(0, ta.selectionStart).split('\n').length - 1;
            var top = lineIndex * lineHeight;
            if (top < ta.scrollTop || top > ta.scrollTop + ta.clientHeight - lineHeight * 2) {
                ta.scrollTop = Math.max(0, top - ta.clientHeight / 2);
            }
        }

        // --- Find & Replace over the content textarea ---

        function findPanelOpen() {
            return !!(findPanel && !findPanel.hidden);
        }

        function escapeRegExp(s) {
            return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        }

        function computeMatches() {
            findMatches = [];
            var ta = getTa();
            var q = findInput ? findInput.value : '';
            if (!ta || !q) return;
            var haystack = findCaseSensitive ? ta.value : ta.value.toLowerCase();
            var needle = findCaseSensitive ? q : q.toLowerCase();
            var i = haystack.indexOf(needle);
            while (i !== -1 && findMatches.length < 10000) {
                findMatches.push(i);
                i = haystack.indexOf(needle, i + needle.length);
            }
        }

        function clampFindIndex() {
            if (!findMatches.length) {
                findIndex = -1;
            } else if (findIndex < 0 || findIndex >= findMatches.length) {
                findIndex = 0;
            }
        }

        function updateFindCount(message) {
            if (!findCountEl) return;
            if (message) {
                findCountEl.textContent = message;
                return;
            }
            if (!findInput || !findInput.value) {
                findCountEl.textContent = '';
                return;
            }
            findCountEl.textContent = findMatches.length
                ? (findIndex + 1) + ' of ' + findMatches.length
                : 'No matches';
        }

        function selectMatch(focusTextarea) {
            var ta = getTa();
            if (!ta || findIndex < 0 || findIndex >= findMatches.length) return;
            var start = findMatches[findIndex];
            var end = start + findInput.value.length;
            findAnchor = start;
            if (focusTextarea) ta.focus();
            try { ta.setSelectionRange(start, end); } catch (err) { /* ignore */ }
            scrollSelectionIntoView(ta);
            updateFindCount('');
        }

        function refreshMatchesFromAnchor() {
            computeMatches();
            if (!findMatches.length) {
                findIndex = -1;
                updateFindCount('');
                return;
            }
            findIndex = 0;
            for (var i = 0; i < findMatches.length; i++) {
                if (findMatches[i] >= findAnchor) {
                    findIndex = i;
                    break;
                }
            }
            selectMatch(false);
        }

        function stepMatch(delta) {
            if (!findMatches.length) {
                refreshMatchesFromAnchor();
                if (!findMatches.length) return;
                selectMatch(true);
                return;
            }
            findIndex = (findIndex + delta + findMatches.length) % findMatches.length;
            selectMatch(true);
        }

        function replaceCurrent() {
            var ta = getTa();
            if (!ta || !findInput.value) return;
            if (!findMatches.length || findIndex < 0) {
                refreshMatchesFromAnchor();
                if (!findMatches.length) return;
            }
            var start = findMatches[findIndex];
            var len = findInput.value.length;
            var seg = ta.value.substr(start, len);
            var same = findCaseSensitive
                ? seg === findInput.value
                : seg.toLowerCase() === findInput.value.toLowerCase();
            if (!same) {
                // Content moved since matches were computed; re-sync instead.
                refreshMatchesFromAnchor();
                return;
            }
            histBeforeProgrammatic();
            ta.setRangeText(replaceInput.value, start, start + len, 'end');
            histAfterProgrammatic();
            scheduleSave();
            computeMatches();
            if (!findMatches.length) {
                findIndex = -1;
                updateFindCount('');
                return;
            }
            var pos = ta.selectionStart;
            findIndex = 0;
            for (var i = 0; i < findMatches.length; i++) {
                if (findMatches[i] >= pos) {
                    findIndex = i;
                    break;
                }
            }
            selectMatch(true);
        }

        function replaceAllMatches() {
            var ta = getTa();
            var q = findInput ? findInput.value : '';
            if (!ta || !q) return;
            computeMatches();
            if (!findMatches.length) {
                updateFindCount('');
                return;
            }
            var count = findMatches.length;
            histBeforeProgrammatic();
            var re = new RegExp(escapeRegExp(q), findCaseSensitive ? 'g' : 'gi');
            ta.value = ta.value.replace(re, replaceInput.value.replace(/\$/g, '$$$$'));
            histAfterProgrammatic();
            scheduleSave();
            computeMatches();
            findIndex = -1;
            updateFindCount('Replaced ' + count + (count === 1 ? ' match' : ' matches'));
        }

        function openFindPanel() {
            if (!hasEditTools) return;
            var ta = getTa();
            findPanel.hidden = false;
            findAnchor = ta ? ta.selectionStart : 0;
            if (ta && ta.selectionStart !== ta.selectionEnd) {
                var sel = ta.value.slice(ta.selectionStart, ta.selectionEnd);
                if (sel && sel.length <= 200 && sel.indexOf('\n') === -1) {
                    findInput.value = sel;
                }
            }
            findInput.focus();
            findInput.select();
            refreshMatchesFromAnchor();
        }

        function closeFindPanel(focusTextarea) {
            if (!hasEditTools) return;
            findPanel.hidden = true;
            if (findCountEl) findCountEl.textContent = '';
            if (focusTextarea) {
                var ta = getTa();
                if (ta) ta.focus();
            }
        }

        function syncMenuState(dropdown) {
            if (dropdown === editDropdown) {
                if (undoBtn) undoBtn.disabled = !hist.undo.length;
                if (redoBtn) redoBtn.disabled = !hist.redo.length;
            } else if (dropdown === fileDropdown) {
                // Insert/Delete need a saved document id (autosave assigns one
                // after the first keystroke on a new song/note).
                var hasId = !!(idInput && idInput.value);
                if (insertBtn) insertBtn.disabled = !hasId;
                if (deleteBtn) deleteBtn.disabled = !hasId;
            }
        }

        function setDropdownOpen(dropdown, open) {
            if (!dropdown) return;
            var toggleBtn = dropdown.querySelector('.nav-dropdown-toggle');
            if (!toggleBtn) return;
            if (open) {
                // Only one of the editor's menus open at a time.
                if (dropdown !== editDropdown) setDropdownOpen(editDropdown, false);
                if (dropdown !== fileDropdown) setDropdownOpen(fileDropdown, false);
                syncMenuState(dropdown);
            }
            dropdown.classList.toggle('open', open);
            toggleBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
            clampMenu(dropdown);
        }

        function setMenuOpen(open) {
            setDropdownOpen(editDropdown, open);
        }

        // Menus are right-anchored; on narrow screens the toggle can sit near
        // the left edge, pushing the menu off-viewport. Shift it back if so.
        // Offsets are layout-based, immune to the dropdownIn scale animation.
        function clampMenu(dropdown) {
            var menu = dropdown ? dropdown.querySelector('.nav-dropdown-menu') : null;
            if (!menu) return;
            menu.style.marginRight = '';
            if (!dropdown.classList.contains('open')) return;
            var pad = 8;
            var left = dropdown.getBoundingClientRect().left + menu.offsetLeft;
            if (left >= pad) return;
            var roomRight = document.documentElement.clientWidth - pad - (left + menu.offsetWidth);
            var shift = Math.min(pad - left, Math.max(0, roomRight));
            if (shift > 0) menu.style.marginRight = '-' + shift + 'px';
        }

        function wireEditTools() {
            if (!hasEditTools) return;

            var isMac = typeof window.scriptyIsMac === 'function'
                ? window.scriptyIsMac()
                : /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(undoBtn, isMac ? '⌘Z' : 'Ctrl+Z');
                window.scriptySetMenuShortcut(redoBtn, isMac ? '⌘⇧Z' : 'Ctrl+Y');
                window.scriptySetMenuShortcut(findOpenBtn, isMac ? '⌘F' : 'Ctrl+F');
            }

            editToggle.addEventListener('click', function (e) {
                e.stopPropagation();
                setMenuOpen(!editDropdown.classList.contains('open'));
            });
            if (undoBtn) {
                undoBtn.addEventListener('click', function () {
                    setMenuOpen(false);
                    histUndo();
                });
            }
            if (redoBtn) {
                redoBtn.addEventListener('click', function () {
                    setMenuOpen(false);
                    histRedo();
                });
            }
            if (findOpenBtn) {
                findOpenBtn.addEventListener('click', function () {
                    setMenuOpen(false);
                    openFindPanel();
                });
            }

            findInput.addEventListener('input', refreshMatchesFromAnchor);
            if (findCaseBtn) {
                findCaseBtn.addEventListener('click', function () {
                    findCaseSensitive = !findCaseSensitive;
                    findCaseBtn.setAttribute('aria-pressed', findCaseSensitive ? 'true' : 'false');
                    refreshMatchesFromAnchor();
                });
            }
            if (findPrevBtn) findPrevBtn.addEventListener('click', function () { stepMatch(-1); });
            if (findNextBtn) findNextBtn.addEventListener('click', function () { stepMatch(1); });
            if (replaceOneBtn) replaceOneBtn.addEventListener('click', replaceCurrent);
            if (replaceAllBtn) replaceAllBtn.addEventListener('click', replaceAllMatches);
            if (findCloseBtn) findCloseBtn.addEventListener('click', function () { closeFindPanel(false); });

            findPanel.addEventListener('keydown', function (e) {
                if (e.key === 'Escape') {
                    e.preventDefault();
                    closeFindPanel(true);
                    return;
                }
                if (e.key === 'Enter') {
                    e.preventDefault();
                    if (e.target === replaceInput) {
                        replaceCurrent();
                    } else if (e.target === findInput) {
                        stepMatch(e.shiftKey ? -1 : 1);
                    }
                }
            });
        }

        // Runs fn once pending edits are saved, so server-side actions
        // (insert into script) see the latest content. Falls back after 2s.
        function flushThen(fn) {
            if (!isDirty() && !state.hasPending()) {
                fn();
                return;
            }
            saveNow(false);
            var started = Date.now();
            var timer = setInterval(function () {
                if ((!state.inFlight && !state.pending) || Date.now() - started > 2000) {
                    clearInterval(timer);
                    fn();
                }
            }, 100);
        }

        function downloadTxt() {
            var ta = getTa();
            var title = (titleInput && titleInput.value.trim()) || 'Untitled';
            var blob = new Blob([ta ? ta.value : ''], { type: 'text/plain;charset=utf-8' });
            var url = URL.createObjectURL(blob);
            var a = document.createElement('a');
            a.href = url;
            a.download = title.replace(/[\\/:*?"<>|]+/g, '-') + '.txt';
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
        }

        function wireFileMenu() {
            if (!hasFileTools) return;

            fileToggle.addEventListener('click', function (e) {
                e.stopPropagation();
                setDropdownOpen(fileDropdown, !fileDropdown.classList.contains('open'));
            });

            if (newLink) {
                newLink.addEventListener('click', function () {
                    setDropdownOpen(fileDropdown, false);
                });
            }

            if (importBtn && importForm && importFileInput) {
                importBtn.addEventListener('click', function () {
                    setDropdownOpen(fileDropdown, false);
                    importFileInput.click();
                });
                importFileInput.addEventListener('change', function () {
                    if (!importFileInput.files || !importFileInput.files.length) return;
                    flushThen(function () { importForm.submit(); });
                });
            }

            if (insertBtn && insertForm) {
                insertBtn.addEventListener('click', function () {
                    setDropdownOpen(fileDropdown, false);
                    if (!idInput || !idInput.value) return;
                    insertForm.querySelector('input[name="id"]').value = idInput.value;
                    flushThen(function () { insertForm.submit(); });
                });
            }

            if (downloadBtn) {
                downloadBtn.addEventListener('click', function () {
                    setDropdownOpen(fileDropdown, false);
                    downloadTxt();
                });
            }

            if (printBtn) {
                printBtn.addEventListener('click', function () {
                    setDropdownOpen(fileDropdown, false);
                    window.print();
                });
            }

            if (deleteBtn && deleteForm) {
                deleteBtn.addEventListener('click', function () {
                    setDropdownOpen(fileDropdown, false);
                    if (!idInput || !idInput.value) return;
                    var message = deleteBtn.getAttribute('data-confirm')
                        || 'Delete this document? This cannot be undone.';
                    if (!window.confirm(message)) return;
                    deleteForm.querySelector('input[name="id"]').value = idInput.value;
                    deleteForm.submit();
                });
            }
        }

        // Print sheet: the textarea clips in print, so mirror title + content
        // into a print-only element (used by the Print menu item and Cmd+P).
        state.prepPrint = function () {
            if (!printSheet) return;
            printSheet.textContent = '';
            var heading = document.createElement('h1');
            heading.className = 'text-document-print-title';
            heading.textContent = (titleInput && titleInput.value.trim()) || 'Untitled';
            var body = document.createElement('div');
            body.className = 'text-document-print-body';
            var ta = getTa();
            body.textContent = ta ? ta.value : '';
            printSheet.appendChild(heading);
            printSheet.appendChild(body);
            document.body.classList.add('text-document-printing');
        };

        state.clearPrint = function () {
            document.body.classList.remove('text-document-printing');
        };

        state.onGlobalKeydown = function (e) {
            if (e.key === 'Escape') {
                if (editDropdown && editDropdown.classList.contains('open')) {
                    setDropdownOpen(editDropdown, false);
                    return;
                }
                if (fileDropdown && fileDropdown.classList.contains('open')) {
                    setDropdownOpen(fileDropdown, false);
                    return;
                }
                if (hasEditTools && findPanelOpen() && !findPanel.contains(document.activeElement)) {
                    closeFindPanel(false);
                }
                return;
            }
            if (!hasEditTools) return;
            if ((e.metaKey || e.ctrlKey) && !e.altKey && !e.shiftKey && (e.key || '').toLowerCase() === 'f') {
                e.preventDefault();
                openFindPanel();
            }
        };

        state.onDocumentClick = function (e) {
            [editDropdown, fileDropdown].forEach(function (dropdown) {
                if (dropdown && dropdown.classList.contains('open') && !dropdown.contains(e.target)) {
                    setDropdownOpen(dropdown, false);
                }
            });
        };

        form.addEventListener('keydown', function (e) {
            var isS = (e.key === 's' || e.key === 'S');
            var isMod = (e.metaKey || e.ctrlKey) && !e.altKey && !e.shiftKey;
            if (isS && isMod) {
                e.preventDefault();
                saveNow(false);
                return;
            }

            var target = e.target;
            if (!target || target.id !== 'text-document-content') {
                return;
            }
            if ((e.metaKey || e.ctrlKey) && !e.altKey) {
                var key = (e.key || '').toLowerCase();
                if (key === 'z') {
                    e.preventDefault();
                    if (e.shiftKey) {
                        histRedo();
                    } else {
                        histUndo();
                    }
                    return;
                }
                if (key === 'y' && !e.shiftKey && e.ctrlKey) {
                    e.preventDefault();
                    histRedo();
                    return;
                }
            }
            if (e.key !== 'Tab' || e.metaKey || e.ctrlKey || e.altKey) {
                return;
            }
            e.preventDefault();
            var start = target.selectionStart;
            var end = target.selectionEnd;
            var value = target.value;
            histBeforeProgrammatic();
            target.value = value.slice(0, start) + '    ' + value.slice(end);
            target.selectionStart = target.selectionEnd = start + 4;
            histAfterProgrammatic();
            scheduleSave();
        });

        function onFieldEdit(e) {
            var t = e.target;
            if (!t || (t.name !== 'title' && t.name !== 'content')) {
                return;
            }
            if (t.name === 'content') {
                histOnInput();
                if (findPanelOpen()) {
                    computeMatches();
                    clampFindIndex();
                    updateFindCount('');
                }
            }
            scheduleSave();
        }

        form.addEventListener('input', onFieldEdit);
        form.addEventListener('change', onFieldEdit);
        if (titleInput) {
            titleInput.addEventListener('input', onFieldEdit);
            titleInput.addEventListener('change', onFieldEdit);
            titleInput.addEventListener('keydown', function (e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    titleInput.blur();
                }
            });
        }

        function snapshotKey() {
            return [
                idInput ? idInput.value : '',
                titleInput ? titleInput.value : '',
                getTa() ? getTa().value : ''
            ].join('\u0001');
        }

        var lastSavedKey = snapshotKey();

        function formatLocalDateTime(date) {
            var pad = function (num) { return (num < 10 ? '0' : '') + num; };
            return date.getFullYear() + '-' +
                pad(date.getMonth() + 1) + '-' +
                pad(date.getDate()) + 'T' +
                pad(date.getHours()) + ':' +
                pad(date.getMinutes()) + ':' +
                pad(date.getSeconds());
        }

        function updateLastEditedTimestamp(updatedAtStr) {
            var container = document.getElementById('text-document-last-edited');
            if (!container) return;
            var timeEl = container.querySelector('.last-edited-time');
            if (timeEl) {
                var timestamp = updatedAtStr || formatLocalDateTime(new Date());
                timeEl.setAttribute('data-timestamp', timestamp);
                if (window.scriptyUpdateLastEditedTimes) {
                    window.scriptyUpdateLastEditedTimes();
                }
            }
            container.style.display = '';
        }

        function isDirty() {
            return snapshotKey() !== lastSavedKey;
        }

        function hasSomethingToSave() {
            var title = titleInput ? titleInput.value.trim() : '';
            var content = getTa() ? (getTa().value || '').trim() : '';
            var hasId = idInput && idInput.value;
            return !!(hasId || title || content);
        }

        function ensureTitleForSave() {
            if (!titleInput) {
                return;
            }
            var currentTa = getTa();
            if (!titleInput.value.trim() && currentTa && (currentTa.value || '').trim()) {
                titleInput.value = 'Untitled';
            }
        }

        function setStatus(text, stateName) {
            if (!statusEl) {
                return;
            }
            clearTimeout(state.statusTimer);
            statusEl.textContent = text || '';
            statusEl.dataset.state = stateName || '';
            statusEl.hidden = !text;
            if (stateName === 'saved') {
                state.statusTimer = setTimeout(function () {
                    if (statusEl.dataset.state === 'saved') {
                        statusEl.hidden = true;
                        statusEl.textContent = '';
                        statusEl.dataset.state = '';
                    }
                }, 2500);
            }
        }

        function scheduleSave() {
            if (!hasSomethingToSave()) {
                return;
            }
            state.pending = true;
            setStatus('Saving…', 'saving');
            clearTimeout(state.timer);
            state.timer = setTimeout(function () {
                saveNow(false);
            }, SAVE_DELAY_MS);
        }

        function buildBody() {
            ensureTitleForSave();
            var body = new URLSearchParams();
            if (idInput && idInput.value) {
                body.set('id', idInput.value);
            }
            if (projectIdInput) {
                body.set('projectId', projectIdInput.value);
            }
            if (typeInput) {
                body.set('documentType', typeInput.value);
            }
            body.set('title', titleInput ? titleInput.value : '');
            var currentTa = getTa();
            body.set('content', currentTa ? currentTa.value : '');
            body.set('stay', 'true');
            return body;
        }

        function saveNow(keepalive) {
            clearTimeout(state.timer);
            if (state.inFlight) {
                state.pending = true;
                return;
            }
            if (!hasSomethingToSave()) {
                state.pending = false;
                setStatus('', '');
                return;
            }
            var key = snapshotKey();
            if (key === lastSavedKey) {
                state.pending = false;
                setStatus('Saved', 'saved');
                return;
            }

            state.inFlight = true;
            state.pending = false;
            setStatus('Saving…', 'saving');

            var action = form.getAttribute('action') || '/project/documents/save';
            var fetchOpts = {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                    'Accept': 'application/json'
                },
                body: buildBody().toString(),
                credentials: 'same-origin'
            };
            if (keepalive) {
                fetchOpts.keepalive = true;
            }

            fetch(action, fetchOpts)
                .then(function (res) {
                    if (!res.ok) {
                        return res.json().then(function (errData) {
                            throw new Error(errData.error || (errData.errors && errData.errors.join(', ')) || 'save failed');
                        }).catch(function (e) {
                            throw new Error(e.message || 'save failed');
                        });
                    }
                    return res.json();
                })
                .then(function (data) {
                    if (!data.success) {
                        throw new Error(data.error || (data.errors && data.errors.join(', ')) || 'validation');
                    }
                    var titleAtSend = titleInput ? titleInput.value : '';
                    var currentTa = getTa();
                    var contentAtSend = currentTa ? currentTa.value : '';
                    applySaved(data.id);
                    if ((titleInput ? titleInput.value : '') === titleAtSend && currentTa && currentTa.value === contentAtSend) {
                        lastSavedKey = snapshotKey();
                        state.pending = false;
                    } else {
                        // Id is saved; keep dirty so trailing keystrokes flush next.
                        lastSavedKey = [
                            idInput ? idInput.value : '',
                            titleAtSend,
                            contentAtSend
                        ].join('\u0001');
                        state.pending = true;
                    }
                    setStatus('Saved', 'saved');
                    updateLastEditedTimestamp(data.updatedAt);
                })
                .catch(function (err) {
                    setStatus(err.message === 'validation' || (err.message && err.message.indexOf(':') !== -1) ? 'Validation error' : 'Couldn’t save', 'error');
                    state.pending = true;
                })
                .finally(function () {
                    state.inFlight = false;
                    if (state.pending && isDirty()) {
                        scheduleSave();
                    } else if (!isDirty()) {
                        state.pending = false;
                    }
                });
        }

        function applySaved(locationOrId) {
            var wasNew = idInput && !idInput.value;
            var id = idInput && idInput.value;
            if (locationOrId) {
                if (typeof locationOrId === 'number' || (typeof locationOrId === 'string' && !isNaN(locationOrId) && locationOrId.indexOf('/') === -1)) {
                    id = String(locationOrId);
                } else {
                    try {
                        var url = new URL(locationOrId, window.location.href);
                        var fromQuery = url.searchParams.get('id');
                        if (fromQuery) {
                            id = fromQuery;
                        }
                    } catch (err) { /* ignore */ }
                }
            }
            if (!id) {
                return;
            }
            if (idInput) {
                idInput.value = id;
            }
            var title = titleInput ? titleInput.value.trim() : '';
            if (title) {
                document.title = 'Scripty - ' + title;
            }
            if (wasNew) {
                try {
                    history.replaceState(null, '', '/project/documents/edit?id=' + encodeURIComponent(id));
                } catch (err) { /* ignore */ }
            }
        }

        wireEditTools();
        wireFileMenu();
        hist.prev = taSnapshot();

        return state;
    }

    // Delegated document-level handlers for the current editor's Edit menu
    // and find panel (registered once; per-page elements re-bind via bindEditor).
    document.addEventListener('keydown', function (e) {
        if (current && current.onGlobalKeydown) {
            current.onGlobalKeydown(e);
        }
    });
    document.addEventListener('click', function (e) {
        if (current && current.onDocumentClick) {
            current.onDocumentClick(e);
        }
    });
    window.addEventListener('beforeprint', function () {
        if (current && current.prepPrint) {
            current.prepPrint();
        }
    });
    window.addEventListener('afterprint', function () {
        if (current && current.clearPrint) {
            current.clearPrint();
        }
    });

    window.addEventListener('beforeunload', function () {
        if (!current) {
            return;
        }
        if (!current.isDirty() && !current.hasPending()) {
            return;
        }
        // Best-effort flush via keepalive fetch; no leave-confirmation
        // prompt since edits auto-save.
        current.saveNow(true);
    });

    document.addEventListener('visibilitychange', function () {
        if (current && document.visibilityState === 'hidden'
                && (current.isDirty() || current.hasPending())) {
            current.saveNow(true);
        }
    });

    // Boosted navigation away from the editor skips beforeunload;
    // flush pending edits before htmx replaces the page.
    document.body.addEventListener('htmx:beforeRequest', function () {
        if (current && (current.isDirty() || current.hasPending())) {
            current.saveNow(true);
        }
    });

    // Re-bind after boosted navigation swaps a new editor form in
    // (allowScriptTags is false, so this script only runs on hard loads).
    document.body.addEventListener('htmx:afterSettle', initEditor);
    document.body.addEventListener('htmx:afterSwap', initEditor);
    document.body.addEventListener('htmx:historyRestore', initEditor);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initEditor);
    } else {
        initEditor();
    }
})();
