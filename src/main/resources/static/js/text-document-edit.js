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
            if (e.key !== 'Tab' || e.metaKey || e.ctrlKey || e.altKey) {
                return;
            }
            e.preventDefault();
            var start = target.selectionStart;
            var end = target.selectionEnd;
            var value = target.value;
            target.value = value.slice(0, start) + '    ' + value.slice(end);
            target.selectionStart = target.selectionEnd = start + 4;
            scheduleSave();
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

    window.addEventListener('beforeunload', function (e) {
        if (!current) {
            return;
        }
        if (!current.isDirty() && !current.hasPending()) {
            return;
        }
        // Best-effort flush; browsers may cancel async work on unload.
        current.saveNow(true);
        e.preventDefault();
        e.returnValue = '';
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
