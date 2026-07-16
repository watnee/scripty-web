/**
 * Song block editor: add / edit / delete / reorder lyric lines.
 *
 * Each row is a lyric block persisted server-side. Content edits save on blur
 * (POST /song/block/edit, 204); structural changes (add, delete, move) return
 * the refreshed #song-blocks list which we swap in, re-focusing the marked row.
 *
 * Loaded from nav.html so it survives HTMX-boosted navigation. Uses event
 * delegation on document, so it keeps working after the list is re-rendered.
 */
(function () {
    'use strict';

    if (window._scriptySongBlocksInit) {
        return;
    }
    window._scriptySongBlocksInit = true;

    // Serialize edit saves so a following structural change sees fresh content.
    var pendingEdit = Promise.resolve();

    function editorEl(node) {
        return node ? node.closest('.song-blocks-editor[data-document-id]') : null;
    }

    function currentEditor() {
        return document.querySelector('.song-blocks-editor[data-document-id]');
    }

    function rowTextarea(node) {
        var row = node.closest('.song-block-row');
        return row ? row.querySelector('.song-block-textarea') : null;
    }

    function autoGrow(ta) {
        if (!ta) {
            return;
        }
        // Measuring at zero width (pre-layout) would wrap the text into many
        // lines and lock in a huge height; wait until the row has real width.
        if (ta.offsetWidth === 0) {
            return;
        }
        ta.style.height = 'auto';
        ta.style.height = ta.scrollHeight + 'px';
    }

    function markSaved(root) {
        (root || document).querySelectorAll('.song-block-textarea').forEach(function (ta) {
            ta.__lastSaved = ta.value;
            autoGrow(ta);
        });
    }

    function focusMarked(root) {
        var ta = root.querySelector('.song-block-textarea[data-autofocus="true"]');
        if (ta) {
            ta.focus();
            var v = ta.value;
            try {
                ta.setSelectionRange(v.length, v.length);
            } catch (e) { /* ignore */ }
            autoGrow(ta);
        }
    }

    function post(url, params) {
        var body = new URLSearchParams(params);
        return fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
            body: body.toString(),
            credentials: 'same-origin'
        }).then(function (res) {
            if (!res.ok) {
                return Promise.reject(res);
            }
            return res.text();
        });
    }

    function replaceList(html) {
        var list = document.getElementById('song-blocks');
        if (!list) {
            return;
        }
        var tmp = document.createElement('div');
        tmp.innerHTML = html.trim();
        var fresh = tmp.querySelector('#song-blocks') || tmp.firstElementChild;
        if (!fresh) {
            return;
        }
        list.replaceWith(fresh);
        markSaved(fresh);
        focusMarked(fresh);
    }

    function saveEdit(ta) {
        var id = ta.getAttribute('data-block-id');
        if (!id || ta.__lastSaved === ta.value) {
            return Promise.resolve();
        }
        var sent = ta.value;
        var p = post('/song/block/edit', { id: id, content: sent }).then(function () {
            ta.__lastSaved = sent;
        }).catch(function () { /* keep dirty; retried on next blur */ });
        pendingEdit = p;
        return p;
    }

    function structural(url, params) {
        return pendingEdit.then(function () {
            return post(url, params);
        }).then(replaceList).catch(function () { /* leave list as-is on error */ });
    }

    function documentId() {
        var ed = currentEditor();
        return ed ? ed.getAttribute('data-document-id') : null;
    }

    // --- events ---------------------------------------------------------

    document.addEventListener('input', function (e) {
        if (e.target && e.target.classList && e.target.classList.contains('song-block-textarea')) {
            autoGrow(e.target);
        }
    });

    document.addEventListener('focusout', function (e) {
        var t = e.target;
        if (t && t.classList && t.classList.contains('song-block-textarea') && editorEl(t)) {
            saveEdit(t);
        }
    });

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && document.querySelector('.song-block-menu-dropdown.open')) {
            closeMenus(null);
            return;
        }
        var t = e.target;
        if (!t || !t.classList || !t.classList.contains('song-block-textarea') || !editorEl(t)) {
            return;
        }
        if (e.key === 'Enter' && !e.shiftKey) {
            // Enter splits to a new line below, mirroring the screenplay editor.
            e.preventDefault();
            var id = t.getAttribute('data-block-id');
            if (id) {
                t.__lastSaved = t.value; // createBelow persists this content atomically
                structural('/song/block/createBelow', { id: id, content: t.value });
            }
        }
    });

    // --- drag to reorder -------------------------------------------------
    //
    // Mirrors the screenplay block drag: grab the "⋮⋮" handle, a line marks the
    // drop point, and the release posts the new index. Handlers are delegated on
    // document because #song-blocks is replaced wholesale after every change.

    var dragRow = null;
    // A drag ends with a click on the handle in some browsers; swallow it so the
    // menu does not pop open right after a drop.
    var dragJustEnded = false;

    function blockList() {
        return document.getElementById('song-blocks');
    }

    function songRow(node) {
        var row = node && node.closest ? node.closest('.song-block-row[data-block-id]') : null;
        var list = blockList();
        return row && list && list.contains(row) ? row : null;
    }

    function clearDropMarkers() {
        var list = blockList();
        if (!list) {
            return;
        }
        list.querySelectorAll('.drop-before, .drop-after').forEach(function (el) {
            el.classList.remove('drop-before', 'drop-after');
        });
    }

    function findDropTarget(clientY) {
        var list = blockList();
        if (!list) {
            return null;
        }
        var rows = Array.prototype.filter.call(
            list.querySelectorAll('.song-block-row[data-block-id]'),
            function (row) { return row !== dragRow; }
        );
        if (!rows.length) {
            return null;
        }
        for (var i = 0; i < rows.length; i++) {
            var rect = rows[i].getBoundingClientRect();
            var effectiveHeight = Math.max(rect.height, 24);
            if (clientY < rect.top + effectiveHeight / 2) {
                return { row: rows[i], insertBefore: true };
            }
        }
        return { row: rows[rows.length - 1], insertBefore: false };
    }

    function applyDropMarker(clientY) {
        clearDropMarkers();
        var target = findDropTarget(clientY);
        if (target) {
            target.row.classList.add(target.insertBefore ? 'drop-before' : 'drop-after');
        }
        return target;
    }

    // The index the dragged row lands on once it is pulled out of the list and
    // reinserted next to the target — the zero-based order /song/block/moveTo wants.
    function dropIndex(target) {
        var list = blockList();
        var ids = Array.prototype.map.call(
            list.querySelectorAll('.song-block-row[data-block-id]'),
            function (row) { return row.getAttribute('data-block-id'); }
        );
        var dragId = dragRow.getAttribute('data-block-id');
        var targetId = target.row.getAttribute('data-block-id');
        ids.splice(ids.indexOf(dragId), 1);
        var at = ids.indexOf(targetId) + (target.insertBefore ? 0 : 1);
        ids.splice(at, 0, dragId);
        return ids.indexOf(dragId);
    }

    document.addEventListener('dragstart', function (e) {
        var handle = e.target.closest ? e.target.closest('.song-block-menu-toggle') : null;
        var row = handle ? songRow(handle) : null;
        if (!row) {
            return;
        }
        dragRow = row;
        closeMenus(null);
        row.classList.add('dragging');
        e.dataTransfer.effectAllowed = 'move';
        if (e.dataTransfer.setData) {
            e.dataTransfer.setData('text/plain', row.getAttribute('data-block-id') || 'song-block');
        }
        if (e.dataTransfer.setDragImage) {
            var rect = row.getBoundingClientRect();
            e.dataTransfer.setDragImage(row, 24, Math.min(16, rect.height / 2));
        }
    });

    document.addEventListener('dragend', function () {
        if (dragRow) {
            dragRow.classList.remove('dragging');
        }
        clearDropMarkers();
        dragRow = null;
        dragJustEnded = true;
        setTimeout(function () { dragJustEnded = false; }, 0);
    });

    document.addEventListener('dragover', function (e) {
        if (!dragRow || !blockList() || !blockList().contains(e.target)) {
            return;
        }
        e.preventDefault();
        if (e.dataTransfer) {
            e.dataTransfer.dropEffect = 'move';
        }
        applyDropMarker(e.clientY);
    });

    document.addEventListener('drop', function (e) {
        if (!dragRow || !blockList() || !blockList().contains(e.target)) {
            return;
        }
        e.preventDefault();
        var target = applyDropMarker(e.clientY);
        clearDropMarkers();
        if (!target || target.row === dragRow) {
            return;
        }
        var id = dragRow.getAttribute('data-block-id');
        var position = dropIndex(target);
        var currentIndex = Array.prototype.indexOf.call(
            blockList().querySelectorAll('.song-block-row[data-block-id]'), dragRow
        );
        if (!id || position === currentIndex) {
            return;
        }
        // Move the row now so the drop reads as instant; the refreshed list that
        // comes back from the server replaces it either way.
        if (target.insertBefore) {
            target.row.parentNode.insertBefore(dragRow, target.row);
        } else {
            target.row.parentNode.insertBefore(dragRow, target.row.nextSibling);
        }
        structural('/song/block/moveTo', { id: id, position: position });
    });

    function closeMenus(except) {
        document.querySelectorAll('.song-block-menu-dropdown.open').forEach(function (d) {
            if (d === except) {
                return;
            }
            d.classList.remove('open');
            var t = d.querySelector('.song-block-menu-toggle');
            if (t) {
                t.setAttribute('aria-expanded', 'false');
            }
        });
    }

    document.addEventListener('click', function (e) {
        // Toggle the per-line options ("⋮⋮") menu, mirroring the screenplay drag menu.
        var toggle = e.target.closest('.song-block-menu-toggle');
        if (toggle && editorEl(toggle)) {
            e.preventDefault();
            if (dragJustEnded) {
                return;
            }
            var dropdown = toggle.closest('.song-block-menu-dropdown');
            var willOpen = dropdown && !dropdown.classList.contains('open');
            closeMenus(willOpen ? dropdown : null);
            if (dropdown) {
                dropdown.classList.toggle('open', willOpen);
                toggle.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
            }
            return;
        }
        // A click anywhere else dismisses any open menu before handling actions.
        closeMenus(null);

        var btn = e.target.closest('[data-action]');
        if (!btn || !editorEl(btn)) {
            return;
        }
        var action = btn.getAttribute('data-action');
        if (action === 'append') {
            var docId = documentId();
            if (docId) {
                structural('/song/block/append', { documentId: docId });
            }
            return;
        }
        var ta = rowTextarea(btn);
        var id = ta ? ta.getAttribute('data-block-id') : null;
        if (!id) {
            return;
        }
        if (action === 'add-below') {
            if (ta) {
                ta.__lastSaved = ta.value;
            }
            structural('/song/block/createBelow', { id: id, content: ta ? ta.value : '' });
        } else if (action === 'delete') {
            structural('/song/block/delete', { id: id });
        } else if (action === 'up') {
            structural('/song/block/moveUp', { id: id });
        } else if (action === 'down') {
            structural('/song/block/moveDown', { id: id });
        }
    });

    function growAll() {
        var ed = currentEditor();
        if (ed) {
            ed.querySelectorAll('.song-block-textarea').forEach(autoGrow);
        }
    }

    function init() {
        var ed = currentEditor();
        if (!ed) {
            return;
        }
        markSaved(ed);
        // Re-measure once layout has settled, in case the rows had no width yet.
        requestAnimationFrame(growAll);
    }

    document.body.addEventListener('htmx:afterSettle', init);
    document.body.addEventListener('htmx:afterSwap', init);
    document.body.addEventListener('htmx:historyRestore', init);
    window.addEventListener('load', growAll);
    window.addEventListener('resize', growAll);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
