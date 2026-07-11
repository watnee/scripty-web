/**
 * Song/note editor: Tab → four spaces, and debounced autosave via stay=true save.
 */
(function () {
    'use strict';

    var SAVE_DELAY_MS = 900;
    var form = document.getElementById('text-document-form');
    var ta = document.getElementById('text-document-content');
    var statusEl = document.getElementById('text-document-save-status');
    if (!form || !ta) {
        return;
    }

    var idInput = form.querySelector('input[name="id"]');
    var projectIdInput = form.querySelector('input[name="projectId"]');
    var typeInput = form.querySelector('input[name="documentType"]');
    var titleInput = document.getElementById('title')
        || (form.elements && form.elements.namedItem('title'))
        || form.querySelector('input[name="title"]');

    var timer = null;
    var inFlight = false;
    var pending = false;
    var lastSavedKey = snapshotKey();
    var statusTimer = null;

    ta.addEventListener('keydown', function (e) {
        if (e.key !== 'Tab' || e.metaKey || e.ctrlKey || e.altKey) {
            return;
        }
        e.preventDefault();
        var start = ta.selectionStart;
        var end = ta.selectionEnd;
        var value = ta.value;
        ta.value = value.slice(0, start) + '    ' + value.slice(end);
        ta.selectionStart = ta.selectionEnd = start + 4;
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

    window.addEventListener('beforeunload', function (e) {
        if (!isDirty() && !inFlight && !pending) {
            return;
        }
        // Best-effort flush; browsers may cancel async work on unload.
        saveNow(true);
        e.preventDefault();
        e.returnValue = '';
    });

    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'hidden' && (isDirty() || pending)) {
            saveNow(true);
        }
    });

    function snapshotKey() {
        return [
            idInput ? idInput.value : '',
            titleInput ? titleInput.value : '',
            ta.value
        ].join('\u0001');
    }

    function isDirty() {
        return snapshotKey() !== lastSavedKey;
    }

    function hasSomethingToSave() {
        var title = titleInput ? titleInput.value.trim() : '';
        var content = (ta.value || '').trim();
        var hasId = idInput && idInput.value;
        return !!(hasId || title || content);
    }

    function ensureTitleForSave() {
        if (!titleInput) {
            return;
        }
        if (!titleInput.value.trim() && (ta.value || '').trim()) {
            titleInput.value = 'Untitled';
        }
    }

    function setStatus(text, state) {
        if (!statusEl) {
            return;
        }
        clearTimeout(statusTimer);
        statusEl.textContent = text || '';
        statusEl.dataset.state = state || '';
        statusEl.hidden = !text;
        if (state === 'saved') {
            statusTimer = setTimeout(function () {
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
        pending = true;
        setStatus('Saving…', 'saving');
        clearTimeout(timer);
        timer = setTimeout(function () {
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
        body.set('content', ta.value);
        body.set('stay', 'true');
        return body;
    }

    function saveNow(keepalive) {
        clearTimeout(timer);
        if (inFlight) {
            pending = true;
            return;
        }
        if (!hasSomethingToSave()) {
            pending = false;
            setStatus('', '');
            return;
        }
        var key = snapshotKey();
        if (key === lastSavedKey) {
            pending = false;
            setStatus('Saved', 'saved');
            return;
        }

        inFlight = true;
        pending = false;
        setStatus('Saving…', 'saving');

        var action = form.getAttribute('action') || '/project/documents/save';
        var fetchOpts = {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                'Accept': 'text/html'
            },
            body: buildBody().toString(),
            credentials: 'same-origin',
            // Follow the stay=true redirect so we can read the saved id from res.url.
            // redirect:'manual' yields an opaque redirect with no Location in browsers.
            redirect: 'follow'
        };
        if (keepalive) {
            fetchOpts.keepalive = true;
        }

        fetch(action, fetchOpts)
            .then(function (res) {
                if (!res.ok) {
                    throw new Error('save failed');
                }
                var finalUrl = res.url || '';
                if (finalUrl.indexOf('/project/documents/edit') === -1) {
                    // Validation error returns the edit form without redirecting.
                    throw new Error('validation');
                }
                return { location: finalUrl };
            })
            .then(function (result) {
                var titleAtSend = titleInput ? titleInput.value : '';
                var contentAtSend = ta.value;
                applySaved(result.location);
                if ((titleInput ? titleInput.value : '') === titleAtSend && ta.value === contentAtSend) {
                    lastSavedKey = snapshotKey();
                    pending = false;
                } else {
                    // Id is saved; keep dirty so trailing keystrokes flush next.
                    lastSavedKey = [
                        idInput ? idInput.value : '',
                        titleAtSend,
                        contentAtSend
                    ].join('\u0001');
                    pending = true;
                }
                setStatus('Saved', 'saved');
            })
            .catch(function () {
                setStatus('Couldn’t save', 'error');
                pending = true;
            })
            .finally(function () {
                inFlight = false;
                if (pending && isDirty()) {
                    scheduleSave();
                } else if (!isDirty()) {
                    pending = false;
                }
            });
    }

    function applySaved(location) {
        var wasNew = idInput && !idInput.value;
        var id = idInput && idInput.value;
        if (location) {
            try {
                var url = new URL(location, window.location.href);
                var fromQuery = url.searchParams.get('id');
                if (fromQuery) {
                    id = fromQuery;
                }
            } catch (err) { /* ignore */ }
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
})();
