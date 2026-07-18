/**
 * Songs workspace: every song in a project stacked as an expandable block
 * editor on one page (/project/documents/songs/workspace).
 *
 * The lyric editing itself is song-blocks.js — each section wraps its own
 * .song-blocks-editor, which that script now scopes per editor. This file only
 * owns the page-level chrome: expand/collapse (remembered per project), the
 * filter box, and inline title renames.
 *
 * Loaded from nav.html so it survives HTMX-boosted navigation.
 */
(function () {
    'use strict';

    if (window._scriptySongWorkspaceInit) {
        return;
    }
    window._scriptySongWorkspaceInit = true;

    var STORAGE_PREFIX = 'scripty.songWorkspace.open.';

    function list() {
        return document.getElementById('song-workspace-list');
    }

    function items() {
        var el = list();
        return el ? Array.prototype.slice.call(el.querySelectorAll('.song-workspace-item')) : [];
    }

    function projectId() {
        var el = list();
        return el ? el.getAttribute('data-project-id') : null;
    }

    function projectKey() {
        // The workspace is always scoped to one project, so its id identifies
        // which set of open sections to remember.
        var id = projectId();
        return id ? STORAGE_PREFIX + id : null;
    }

    function readOpenIds() {
        var key = projectKey();
        if (!key) {
            return [];
        }
        try {
            var raw = window.localStorage.getItem(key);
            var parsed = raw ? JSON.parse(raw) : [];
            return Array.isArray(parsed) ? parsed : [];
        } catch (e) {
            return [];
        }
    }

    function writeOpenIds(ids) {
        var key = projectKey();
        if (!key) {
            return;
        }
        try {
            window.localStorage.setItem(key, JSON.stringify(ids));
        } catch (e) { /* private mode / quota — collapse state is disposable */ }
    }

    function rememberOpen() {
        writeOpenIds(items()
            .filter(isOpen)
            .map(function (item) { return item.getAttribute('data-song-id'); })
            .filter(Boolean));
    }

    function isOpen(item) {
        var body = item.querySelector('.song-workspace-body');
        return !!body && !body.hidden;
    }

    function setOpen(item, open) {
        var body = item.querySelector('.song-workspace-body');
        var toggle = item.querySelector('.song-workspace-toggle');
        if (!body || !toggle) {
            return;
        }
        body.hidden = !open;
        toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
        item.classList.toggle('song-workspace-item--open', open);
        if (open) {
            // The rows had no width while hidden, so autoGrow skipped them;
            // song-blocks.js exposes its measure pass for exactly this.
            if (typeof window.scriptySongBlocksGrow === 'function') {
                window.scriptySongBlocksGrow();
            }
        }
    }

    // --- expand / collapse ------------------------------------------------

    document.addEventListener('click', function (e) {
        var toggle = e.target.closest ? e.target.closest('.song-workspace-toggle') : null;
        if (toggle) {
            var item = toggle.closest('.song-workspace-item');
            if (item) {
                setOpen(item, !isOpen(item));
                rememberOpen();
            }
            return;
        }
        var expandAll = e.target.closest ? e.target.closest('#song-workspace-expand-all') : null;
        var collapseAll = e.target.closest ? e.target.closest('#song-workspace-collapse-all') : null;
        if (expandAll || collapseAll) {
            items().forEach(function (item) {
                if (item.hidden) {
                    return; // filtered out — leave it as it was
                }
                setOpen(item, !!expandAll);
            });
            rememberOpen();
        }
    });

    // --- filter -----------------------------------------------------------

    function applyFilter(term) {
        var needle = (term || '').trim().toLowerCase();
        var matches = 0;
        items().forEach(function (item) {
            var title = item.getAttribute('data-song-title') || '';
            var hit = !needle || title.indexOf(needle) !== -1;
            item.hidden = !hit;
            if (hit) {
                matches++;
            }
        });
        var empty = document.getElementById('song-workspace-no-matches');
        if (empty) {
            empty.hidden = matches !== 0;
        }
    }

    document.addEventListener('input', function (e) {
        if (e.target && e.target.id === 'song-workspace-search') {
            applyFilter(e.target.value);
        }
    });

    // --- reorder songs ----------------------------------------------------
    //
    // Mirrors the lyric-line drag in song-blocks.js: grab the "⋮⋮" handle, a
    // line marks the drop point, and the release persists the whole order.
    // Handlers are delegated on document so they survive list re-renders.
    //
    // song-blocks.js runs its own drag handlers on document, but it only ever
    // starts a drag from .song-block-menu-toggle and bails when its own dragRow
    // is null, so the two gestures never see each other's events.

    var dragItem = null;

    function canReorder() {
        var el = list();
        return !!el && el.getAttribute('data-can-reorder') === 'true';
    }

    /** Order currently shown, which is what the server should store. */
    function currentOrder() {
        return items()
            .map(function (item) { return parseInt(item.getAttribute('data-song-id'), 10); })
            .filter(function (id) { return !isNaN(id); });
    }

    function announce(message) {
        var el = document.getElementById('song-workspace-status');
        if (el) {
            el.textContent = message || '';
        }
    }

    /** Put the sections back in `snapshot` order after a rejected reorder. */
    function restoreItems(snapshot) {
        var el = list();
        if (el) {
            snapshot.forEach(function (item) { el.appendChild(item); });
        }
    }

    /**
     * Persists the order now shown. A reorder that fails server-side must not
     * leave the page showing an order that was never saved, so the sections go
     * back where they were and the user is told.
     */
    function persistOrder(previous) {
        var id = projectId();
        var ordered = currentOrder();
        if (!id || !ordered.length) {
            return;
        }
        announce('');
        // window.fetch is CSRF-patched in csrf.js. The endpoint only produces
        // HAL, so asking for plain application/json here earns a 406.
        fetch('/api/document/reorder?projectId=' + encodeURIComponent(id), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/hal+json' },
            body: JSON.stringify({ orderedIds: ordered }),
            credentials: 'same-origin'
        }).then(function (res) {
            if (!res.ok) {
                return Promise.reject(res);
            }
        }).catch(function () {
            if (previous) {
                restoreItems(previous);
            }
            announce('Could not save the new song order. Put back as it was.');
        });
    }

    function clearDropMarkers() {
        items().forEach(function (item) {
            item.classList.remove('song-workspace-drop-before', 'song-workspace-drop-after');
        });
    }

    function findDropTarget(clientY) {
        var others = items().filter(function (item) {
            return item !== dragItem && !item.hidden;
        });
        for (var i = 0; i < others.length; i++) {
            var rect = others[i].getBoundingClientRect();
            if (clientY < rect.top + rect.height / 2) {
                return { item: others[i], insertBefore: true };
            }
        }
        return others.length
            ? { item: others[others.length - 1], insertBefore: false }
            : null;
    }

    function applyDropMarker(clientY) {
        clearDropMarkers();
        var target = findDropTarget(clientY);
        if (target) {
            target.item.classList.add(target.insertBefore
                ? 'song-workspace-drop-before'
                : 'song-workspace-drop-after');
        }
        return target;
    }

    document.addEventListener('dragstart', function (e) {
        var handle = e.target.closest ? e.target.closest('.song-workspace-drag-handle') : null;
        if (!handle || !canReorder()) {
            return;
        }
        dragItem = handle.closest('.song-workspace-item');
        if (!dragItem) {
            return;
        }
        dragItem.classList.add('song-workspace-item--dragging');
        e.dataTransfer.effectAllowed = 'move';
        if (e.dataTransfer.setData) {
            e.dataTransfer.setData('text/plain', dragItem.getAttribute('data-song-id') || 'song');
        }
        if (e.dataTransfer.setDragImage) {
            // Drag the header, not the whole expanded song — an open section can
            // be taller than the viewport and its ghost would swamp the page.
            var header = dragItem.querySelector('.song-workspace-item-header');
            e.dataTransfer.setDragImage(header || dragItem, 24, 16);
        }
    });

    document.addEventListener('dragend', function () {
        if (dragItem) {
            dragItem.classList.remove('song-workspace-item--dragging');
        }
        clearDropMarkers();
        dragItem = null;
    });

    document.addEventListener('dragover', function (e) {
        if (!dragItem) {
            return;
        }
        e.preventDefault();
        if (e.dataTransfer) {
            e.dataTransfer.dropEffect = 'move';
        }
        applyDropMarker(e.clientY);
    });

    document.addEventListener('drop', function (e) {
        if (!dragItem) {
            return;
        }
        e.preventDefault();
        var target = applyDropMarker(e.clientY);
        clearDropMarkers();
        if (!target || target.item === dragItem) {
            return;
        }
        var previous = items();
        // Move now so the drop reads as instant; the order is persisted after.
        if (target.insertBefore) {
            target.item.parentNode.insertBefore(dragItem, target.item);
        } else {
            target.item.parentNode.insertBefore(dragItem, target.item.nextSibling);
        }
        persistOrder(previous);
    });

    /** Keyboard equivalent of the drag, so ordering does not need a pointer. */
    function moveByKeyboard(handle, delta) {
        var item = handle.closest('.song-workspace-item');
        var all = items().filter(function (i) { return !i.hidden; });
        var at = all.indexOf(item);
        var to = at + delta;
        if (at === -1 || to < 0 || to >= all.length) {
            return false;
        }
        var previous = items();
        if (delta < 0) {
            all[to].parentNode.insertBefore(item, all[to]);
        } else {
            all[to].parentNode.insertBefore(item, all[to].nextSibling);
        }
        persistOrder(previous);
        handle.focus(); // the move must not cost the user their place
        return true;
    }

    document.addEventListener('keydown', function (e) {
        var handle = e.target.closest ? e.target.closest('.song-workspace-drag-handle') : null;
        if (!handle || !canReorder() || e.metaKey || e.ctrlKey || e.altKey) {
            return;
        }
        if (e.key === 'ArrowUp' && moveByKeyboard(handle, -1)) {
            e.preventDefault();
        } else if (e.key === 'ArrowDown' && moveByKeyboard(handle, 1)) {
            e.preventDefault();
        }
    });

    // --- inline rename ----------------------------------------------------

    function saveTitle(input) {
        var id = input.getAttribute('data-song-id');
        var projectId = input.getAttribute('data-project-id');
        var title = input.value.trim();
        if (!id || !projectId || !title || input.__lastSaved === title) {
            return;
        }
        var body = new URLSearchParams({
            id: id, projectId: projectId, type: 'SONG', title: title
        });
        // window.fetch is CSRF-patched in csrf.js.
        fetch('/project/documents/rename', {
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
            input.__lastSaved = title;
            var item = input.closest('.song-workspace-item');
            if (item) {
                item.setAttribute('data-song-title', title.toLowerCase());
            }
        }).catch(function () { /* keep dirty; retried on next blur */ });
    }

    document.addEventListener('focusout', function (e) {
        var t = e.target;
        if (t && t.classList && t.classList.contains('song-workspace-title')) {
            saveTitle(t);
        }
    });

    document.addEventListener('keydown', function (e) {
        var t = e.target;
        if (!t || !t.classList || !t.classList.contains('song-workspace-title')) {
            return;
        }
        if (e.key === 'Enter') {
            e.preventDefault();
            t.blur(); // focusout saves
        }
    });

    // --- init -------------------------------------------------------------

    function init() {
        if (!list()) {
            return;
        }
        var open = readOpenIds();
        items().forEach(function (item) {
            var input = item.querySelector('.song-workspace-title');
            if (input) {
                input.__lastSaved = input.value.trim();
            }
            setOpen(item, open.indexOf(item.getAttribute('data-song-id')) !== -1);
        });
        var search = document.getElementById('song-workspace-search');
        if (search && search.value) {
            applyFilter(search.value);
        }
    }

    document.body.addEventListener('htmx:afterSettle', init);
    document.body.addEventListener('htmx:historyRestore', init);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
