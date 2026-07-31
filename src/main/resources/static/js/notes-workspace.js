/**
 * The notes workspace: every note in a project on one page
 * (/project/documents/songs/workspace?type=NOTES).
 *
 * Everything a note pane shares with a song pane — expanding, collapsing,
 * filtering, reordering, renaming — is song-workspace.js, which reads the
 * list's `data-doc-noun` and needs nothing from here. What is left, and all
 * this file does, is the one thing a note has that a song does not: a body of
 * prose in a single textarea, saving itself as it is typed.
 *
 * It saves the way the single-note editor saves — a form-encoded POST to
 * /project/documents/save with stay=true, answered as JSON — rather than
 * inventing a workspace endpoint. A note edited here and a note edited there
 * are the same write.
 */
(function () {
    'use strict';

    if (window._scriptyNotesWorkspaceInit) {
        return;
    }
    window._scriptyNotesWorkspaceInit = true;

    /**
     * Long enough that ordinary typing does not fire a request per word, short
     * enough that a writer who looks away is already saved. The same 900ms the
     * single-note editor waits.
     */
    var SAVE_DELAY = 900;

    function list() {
        return document.getElementById('song-workspace-list');
    }

    function panes() {
        var el = list();
        return el ? Array.prototype.slice.call(el.querySelectorAll('.note-workspace-item')) : [];
    }

    function statusEl(pane) {
        return pane ? pane.querySelector('.note-workspace-status') : null;
    }

    function setStatus(pane, text) {
        var el = statusEl(pane);
        if (el) {
            el.textContent = text || '';
        }
    }

    /**
     * The count in the header, which is the only thing a collapsed pane says
     * about its note. Kept in step as the writer types, or it would report the
     * length the page was loaded at for the rest of the session.
     */
    function refreshWordCount(pane, text) {
        var countEl = pane.querySelector('.note-workspace-word-count');
        if (!countEl) {
            return;
        }
        var trimmed = (text || '').trim();
        var words = trimmed ? trimmed.split(/\s+/).length : 0;
        countEl.textContent = words;
        var label = countEl.nextElementSibling;
        if (label) {
            label.textContent = words === 1 ? 'word' : 'words';
        }
    }

    /**
     * Sends what is in the textarea.
     *
     * The title is sent alongside it because the save endpoint takes the whole
     * document: leaving it out would save the note under an empty name. It is
     * read at send time rather than captured earlier, so a rename made while
     * this was queued goes with it — the inline rename in song-workspace.js
     * posts separately, and the last of the two to land must not undo the other.
     */
    function save(pane) {
        var textarea = pane.querySelector('.note-workspace-content');
        var titleInput = pane.querySelector('.note-workspace-title');
        if (!textarea || textarea.readOnly) {
            return;
        }
        var id = textarea.getAttribute('data-song-id');
        var projectId = textarea.getAttribute('data-project-id');
        if (!id || !projectId) {
            return;
        }
        var content = textarea.value;
        if (content === textarea.__lastSaved) {
            return;
        }

        setStatus(pane, 'Saving…');
        var body = new URLSearchParams({
            id: id,
            projectId: projectId,
            documentType: 'NOTES',
            title: titleInput ? titleInput.value : '',
            content: content,
            stay: 'true'
        });
        // window.fetch is CSRF-patched in csrf.js.
        fetch('/project/documents/save', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                'Accept': 'application/json'
            },
            body: body.toString(),
            credentials: 'same-origin'
        }).then(function (res) {
            if (!res.ok) {
                return Promise.reject(res);
            }
            return res.json();
        }).then(function (data) {
            if (!data || !data.success) {
                return Promise.reject(data);
            }
            // What went, not what is there now: the writer may have typed on
            // while this was in flight, and marking those keystrokes saved
            // would strand them.
            textarea.__lastSaved = content;
            if (textarea.value !== content) {
                schedule(pane);
                setStatus(pane, '');
            } else {
                setStatus(pane, 'Saved');
            }
        }).catch(function () {
            // Left dirty on purpose, so the next keystroke — or the flush on
            // the way out of the page — tries again.
            setStatus(pane, 'Couldn’t save');
        });
    }

    function schedule(pane) {
        window.clearTimeout(pane.__saveTimer);
        pane.__saveTimer = window.setTimeout(function () { save(pane); }, SAVE_DELAY);
    }

    document.addEventListener('input', function (e) {
        var textarea = e.target;
        if (!textarea || !textarea.classList
                || !textarea.classList.contains('note-workspace-content')) {
            return;
        }
        var pane = textarea.closest('.note-workspace-item');
        if (!pane) {
            return;
        }
        refreshWordCount(pane, textarea.value);
        setStatus(pane, '');
        schedule(pane);
    });

    // Leaving the textarea is a stronger signal than the timer: send now rather
    // than making the writer wait out a debounce they have already finished.
    document.addEventListener('focusout', function (e) {
        var textarea = e.target;
        if (textarea && textarea.classList
                && textarea.classList.contains('note-workspace-content')) {
            var pane = textarea.closest('.note-workspace-item');
            if (pane) {
                window.clearTimeout(pane.__saveTimer);
                save(pane);
            }
        }
    });

    /**
     * The last paragraph, on the way out of the page. Without this, a note
     * typed into and then navigated away from inside the debounce loses
     * whatever came after the previous save.
     */
    function flushAll() {
        panes().forEach(function (pane) {
            window.clearTimeout(pane.__saveTimer);
            save(pane);
        });
    }

    window.addEventListener('pagehide', flushAll);
    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'hidden') {
            flushAll();
        }
    });

    function init() {
        panes().forEach(function (pane) {
            var textarea = pane.querySelector('.note-workspace-content');
            if (textarea) {
                // What the server already holds, so an untouched note is never
                // sent back to it unchanged.
                textarea.__lastSaved = textarea.value;
            }
        });
    }

    document.body.addEventListener('htmx:afterSettle', init);
    document.body.addEventListener('htmx:historyRestore', init);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
