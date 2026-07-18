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

    // Notes stay plain text end to end — these prefixes are never parsed or
    // rendered as markup, they just save the user typing them by hand. They do
    // survive verbatim into blocks when a note is inserted into a script.
    var LIST_RE = /^(\s*)([-*]|\d+\.)(\s+)/;
    var HEADING_RE = /^(#{1,6})\s+/;
    var INDENT = '    ';

    /**
     * Applies a text edit through execCommand so the browser's undo stack (and
     * the shared one in undo-redo.js) both see it. Assigning .value fires no
     * input event and silently wipes the field's history.
     */
    function replaceRange(el, start, end, text) {
        if (typeof window.scriptyReplaceRange === 'function') {
            window.scriptyReplaceRange(el, start, end, text);
            return;
        }
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

    function lineBounds(value, pos) {
        var start = value.lastIndexOf('\n', pos - 1) + 1;
        var end = value.indexOf('\n', pos);
        return { start: start, end: end === -1 ? value.length : end };
    }

    function isOrderedMarker(marker) {
        return /^\d+\.$/.test(marker);
    }

    /**
     * Rewrites ordered-list numbering so inserting or removing an item does not
     * leave 1. 2. 2. 3. behind. Only touches lines whose number is wrong.
     */
    function renumberOrderedLists(el) {
        var value = el.value;
        var caret = el.selectionStart;
        var lines = value.split('\n');
        var counters = {};
        var out = [];
        var offset = 0;
        var delta = 0;
        var changed = false;

        for (var i = 0; i < lines.length; i++) {
            var line = lines[i];
            var next = line;
            var match = LIST_RE.exec(line);
            if (!match) {
                // A non-list line ends every run, so numbering restarts after it.
                counters = {};
            } else {
                var indent = match[1].length;
                if (isOrderedMarker(match[2])) {
                    Object.keys(counters).forEach(function (key) {
                        if (Number(key) > indent) delete counters[key];
                    });
                    counters[indent] = (counters[indent] || 0) + 1;
                    var expected = counters[indent] + '.';
                    if (match[2] !== expected) {
                        next = match[1] + expected + match[3] + line.slice(match[0].length);
                        changed = true;
                    }
                } else {
                    delete counters[indent];
                }
            }
            if (offset + line.length < caret) {
                delta += next.length - line.length;
            }
            out.push(next);
            offset += line.length + 1;
        }

        if (!changed) return;
        replaceRange(el, 0, value.length, out.join('\n'));
        var pos = caret + delta;
        try {
            el.setSelectionRange(pos, pos);
        } catch (e) { /* caret landed outside the field */ }
    }

    /**
     * Splits a line into its indent, whichever list/heading prefix it already
     * carries, and the text after it. A line holds at most one prefix, so
     * applying a new one replaces the old rather than stacking on top of it.
     */
    function splitPrefix(line) {
        var indent = (/^\s*/.exec(line) || [''])[0];
        var rest = line.slice(indent.length);
        var list = LIST_RE.exec(line);
        if (list) {
            return {
                indent: indent,
                kind: isOrderedMarker(list[2]) ? 'ordered' : 'bullet',
                body: line.slice(list[0].length)
            };
        }
        var heading = HEADING_RE.exec(rest);
        if (heading) {
            return { indent: indent, kind: 'h' + heading[1].length, body: rest.slice(heading[0].length) };
        }
        return { indent: indent, kind: null, body: rest };
    }

    function applyPrefix(el, kind, marker) {
        var value = el.value;
        var bounds = lineBounds(value, el.selectionStart);
        var parts = splitPrefix(value.slice(bounds.start, bounds.end));
        // Pressing the same control again clears the prefix.
        var next = parts.kind === kind
            ? parts.indent + parts.body
            : parts.indent + marker + ' ' + parts.body;
        replaceRange(el, bounds.start, bounds.end, next);
    }

    /** Toggles a heading prefix on the caret's line. */
    function toggleHeading(el, level) {
        applyPrefix(el, 'h' + level, '######'.slice(0, level));
    }

    /** Toggles a bullet or numbered prefix on the caret's line. */
    function toggleList(el, ordered) {
        applyPrefix(el, ordered ? 'ordered' : 'bullet', ordered ? '1.' : '-');
        renumberOrderedLists(el);
    }

    window.scriptyNoteToggleList = toggleList;
    window.scriptyNoteToggleHeading = toggleHeading;

    /** Labels the notes toolbar with the platform's own modifier keys. */
    function syncNoteToolbarHints() {
        var toolbar = document.querySelector('.note-format-toolbar');
        if (!toolbar || toolbar.dataset.scriptyHintsSynced === '1') return;
        var isMac = typeof window.scriptyIsMac === 'function'
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
        var mod = isMac ? '⌘' : 'Ctrl+';
        var alt = isMac ? '⌥' : 'Alt+';
        var hints = {
            'note-undo': isMac ? '⌘Z' : 'Ctrl+Z',
            'note-redo': isMac ? '⌘⇧Z' : 'Ctrl+Y'
        };
        Object.keys(hints).forEach(function (id) {
            var el = document.getElementById(id);
            if (!el) return;
            el.title = el.title.split(' (')[0] + ' (' + hints[id] + ')';
            el.setAttribute('aria-label', el.title);
        });
        ['h1', 'h2', 'h3'].forEach(function (level, index) {
            var el = toolbar.querySelector('[data-note-format="' + level + '"]');
            if (!el) return;
            var hint = mod + alt + (index + 1);
            el.title = 'Heading ' + (index + 1) + ' (' + hint + ')';
            el.setAttribute('aria-label', el.title);
        });
        toolbar.dataset.scriptyHintsSynced = '1';
    }

    document.addEventListener('click', function (e) {
        var target = e.target && e.target.closest ? e.target : null;
        if (!target) return;

        var historyBtn = target.closest('#note-undo, #note-redo');
        if (historyBtn) {
            e.preventDefault();
            if (typeof window.scriptyPerformHistoryAction === 'function') {
                window.scriptyPerformHistoryAction(historyBtn.id === 'note-redo' ? 'redo' : 'undo');
            }
            return;
        }

        var btn = target.closest('[data-note-format]');
        if (!btn) return;
        var textarea = document.getElementById('text-document-content');
        if (!textarea) return;
        e.preventDefault();
        var action = btn.getAttribute('data-note-format');
        if (action === 'bullet') {
            toggleList(textarea, false);
        } else if (action === 'ordered') {
            toggleList(textarea, true);
        } else if (/^h[1-6]$/.test(action)) {
            toggleHeading(textarea, Number(action.slice(1)));
        }
        // replaceRange fires an input event, which the form's own listener
        // already turns into a debounced save.
    });

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
        syncNoteToolbarHints();
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

            var start = target.selectionStart;
            var end = target.selectionEnd;
            var value = target.value;
            var bounds = lineBounds(value, start);
            var line = value.slice(bounds.start, bounds.end);
            var listMatch = LIST_RE.exec(line);

            // ⌘⌥1/2/3 toggle heading levels. Read e.code, not e.key: Alt+digit
            // produces symbols like "¡" on a Mac layout.
            if ((e.metaKey || e.ctrlKey) && e.altKey && !e.shiftKey) {
                var level = { Digit1: 1, Digit2: 2, Digit3: 3 }[e.code];
                if (level) {
                    e.preventDefault();
                    toggleHeading(target, level);
                    scheduleSave();
                    return;
                }
            }

            if (e.metaKey || e.ctrlKey || e.altKey) {
                return;
            }

            if (e.key === 'Tab') {
                e.preventDefault();
                if (e.shiftKey) {
                    // Outdent one level from the start of the line.
                    var outdented = line.replace(/^ {1,4}/, '');
                    if (outdented !== line) {
                        replaceRange(target, bounds.start, bounds.end, outdented);
                        if (listMatch) renumberOrderedLists(target);
                        scheduleSave();
                    }
                    return;
                }
                if (listMatch) {
                    // Inside a list Tab nests the item rather than inserting spaces.
                    replaceRange(target, bounds.start, bounds.start, INDENT);
                    renumberOrderedLists(target);
                } else {
                    replaceRange(target, start, end, INDENT);
                }
                scheduleSave();
                return;
            }

            if (e.key === 'Enter' && !e.shiftKey && start === end && listMatch) {
                e.preventDefault();
                var body = line.slice(listMatch[0].length);
                if (!body.trim()) {
                    // Enter on an empty item exits the list instead of nesting deeper.
                    replaceRange(target, bounds.start, bounds.end, '');
                    scheduleSave();
                    return;
                }
                var marker = isOrderedMarker(listMatch[2])
                    ? (parseInt(listMatch[2], 10) + 1) + '.'
                    : listMatch[2];
                replaceRange(target, start, end, '\n' + listMatch[1] + marker + listMatch[3]);
                if (isOrderedMarker(listMatch[2])) {
                    renumberOrderedLists(target);
                }
                scheduleSave();
            }
        });

        function onFieldEdit(e) {
            var t = e.target;
            if (!t || (t.name !== 'title' && t.name !== 'content')) {
                return;
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

        return state;
    }

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
