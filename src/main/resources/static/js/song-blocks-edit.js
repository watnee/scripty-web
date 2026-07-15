/**
 * Song block editor. Songs are edited as reorderable "blocks" (one lyric line each),
 * mirroring the screenplay editor. Structural changes (add / delete / move) are HTMX
 * posts that re-render the whole #song-block-list; inline content edits swap just the
 * edited cell. Drag-to-reorder reuses the shared window.scriptyInitBlockDragDrop from nav,
 * pointed at the song move endpoint.
 */
(function () {
    'use strict';

    var MOVE_URL = '/project/documents/block/moveTo';
    var EDIT_URL = '/project/documents/block/editInline';
    var RENAME_URL = '/project/documents/rename';

    var editor;
    var canEdit = false;
    var projectId = null;
    var documentId = null;

    function init() {
        editor = document.querySelector('.song-editor');
        if (!editor) {
            return;
        }
        canEdit = editor.getAttribute('data-can-edit') === 'true';
        documentId = valueOf('song-document-id') || editor.getAttribute('data-document-id');
        projectId = valueOf('song-project-id') || editor.getAttribute('data-project-id');
        if (projectId) {
            // Let shared nav helpers (drag → moveTo) resolve the project without a URL param.
            window.scriptyProjectId = projectId;
        }

        var list = document.getElementById('song-block-list');
        if (list) {
            initList(list);
        }

        bindDelegatedEditClicks();
        bindTitleRename();

        document.body.addEventListener('htmx:afterSwap', onAfterSwap);
    }

    function valueOf(id) {
        var el = document.getElementById(id);
        return el && el.value ? el.value : null;
    }

    function initList(list) {
        if (window.scriptyReindexBlockOrders) {
            window.scriptyReindexBlockOrders(list);
        }
        if (window.scriptyUpdateMoveButtonStates) {
            window.scriptyUpdateMoveButtonStates(list);
        }
        if (canEdit && window.scriptyInitBlockDragDrop) {
            window.scriptyInitBlockDragDrop(list, { projectId: projectId, moveUrl: MOVE_URL });
        }
    }

    function onAfterSwap(event) {
        var target = event.target;
        if (!target) {
            return;
        }
        if (target.id === 'song-block-list') {
            initList(target);
            focusNewBlock(target);
            showSaved();
        } else if (target.classList && target.classList.contains('song-block-content')) {
            // Either an edit form was just shown (focus it) or content was saved (span, ignore).
            var ta = target.querySelector('textarea');
            if (ta) {
                focusTextarea(ta);
            } else {
                showSaved();
            }
        }
    }

    /** Click a show-state line to edit it (fire the custom trigger the cell listens for). */
    function bindDelegatedEditClicks() {
        document.addEventListener('click', function (e) {
            if (!canEdit) {
                return;
            }
            var cell = e.target.closest ? e.target.closest('.song-block-content') : null;
            if (!cell || !editor.contains(cell)) {
                return;
            }
            if (cell.querySelector('textarea')) {
                return; // already editing
            }
            cell.dispatchEvent(new CustomEvent('scripty-edit-block'));
        });
    }

    function focusNewBlock(list) {
        if (!canEdit) {
            return;
        }
        var row = list.querySelector('[data-focus-new="true"]');
        if (!row) {
            return;
        }
        // The focused line is rendered directly in edit mode; just place the caret in it.
        var ta = row.querySelector('textarea');
        if (ta) {
            focusTextarea(ta);
        }
    }

    function focusTextarea(ta) {
        ta.focus();
        // Put the caret at the end.
        var v = ta.value;
        ta.value = '';
        ta.value = v;
    }

    /** Enter (no Shift) inside a line: save it, then add a fresh line below and focus it. */
    window.songBlockEnter = function (textarea) {
        var form = textarea.closest('form');
        var row = textarea.closest('.song-block-row');
        if (!form || !row) {
            return;
        }
        var idInput = form.querySelector('input[name="id"]');
        var id = idInput ? idInput.value : null;
        if (!id) {
            return;
        }
        var body = new URLSearchParams();
        body.append('id', id);
        body.append('content', textarea.value);
        fetch(EDIT_URL, {
            method: 'POST',
            credentials: 'same-origin',
            cache: 'no-store',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body.toString()
        }).then(function () {
            var addBtn = row.querySelector('.song-block-add');
            if (addBtn) {
                addBtn.click(); // HTMX: re-renders the list and focuses the new line.
            }
        }).catch(function () {
            var addBtn = row.querySelector('.song-block-add');
            if (addBtn) {
                addBtn.click();
            }
        });
    };

    /** Persist the song title via the dedicated rename endpoint (never the content-clobbering save). */
    function bindTitleRename() {
        var title = document.getElementById('title');
        if (!title || !canEdit) {
            return;
        }
        var lastSaved = title.value;
        function save() {
            var value = title.value.trim();
            if (!value || value === lastSaved || !documentId || !projectId) {
                return;
            }
            lastSaved = value;
            var body = new URLSearchParams();
            body.append('id', documentId);
            body.append('projectId', projectId);
            body.append('type', 'SONG');
            body.append('title', value);
            fetch(RENAME_URL, {
                method: 'POST',
                credentials: 'same-origin',
                cache: 'no-store',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: body.toString()
            }).then(function () {
                showSaved();
            }).catch(function () { /* keep editing; next blur retries */ });
        }
        title.addEventListener('change', save);
        title.addEventListener('blur', save);
    }

    function showSaved() {
        var status = document.getElementById('text-document-save-status');
        if (!status) {
            return;
        }
        status.hidden = false;
        status.textContent = 'Saved';
        status.dataset.state = 'saved';
        clearTimeout(showSaved._t);
        showSaved._t = setTimeout(function () {
            status.hidden = true;
            status.textContent = '';
        }, 1500);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
