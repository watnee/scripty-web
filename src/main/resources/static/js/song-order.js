/**
 * Drag-to-reorder for the songs / notes list.
 *
 * Mirrors the song-block and screenplay-block drags: grab the "⋮⋮" handle, a
 * line marks the drop point, and the release POSTs the whole new order to
 * /api/document/reorder. The move is applied optimistically and rolled back if
 * the server rejects it. Arrow keys on a focused handle do the same move without
 * a mouse.
 *
 * Cards can be reordered from any sort mode; a successful drop switches the sort
 * select to "Custom order" so the list does not snap back to Last edited / A–Z.
 */
(function () {
    'use strict';

    var listEl = document.getElementById('text-documents-list');
    if (!listEl || listEl.getAttribute('data-can-reorder') !== 'true') {
        return;
    }

    var projectId = listEl.getAttribute('data-project-id');
    var dragCard = null;
    var saving = false;

    function cards() {
        return Array.prototype.slice.call(listEl.querySelectorAll('.text-document-card[data-doc-id]'));
    }

    function orderedIds() {
        return cards().map(function (card) {
            return parseInt(card.getAttribute('data-doc-id'), 10);
        });
    }

    function clearDropMarkers() {
        listEl.querySelectorAll('.drop-before, .drop-after').forEach(function (el) {
            el.classList.remove('drop-before', 'drop-after');
        });
    }

    // Hidden cards are filtered out by the search box. Skipping them keeps the
    // drop line on something the user can actually see; the hidden cards keep
    // their DOM position and ride along in the saved order.
    function findDropTarget(clientY) {
        var rows = cards().filter(function (card) {
            return card !== dragCard && !card.hidden;
        });
        if (!rows.length) {
            return null;
        }
        for (var i = 0; i < rows.length; i++) {
            var rect = rows[i].getBoundingClientRect();
            if (clientY < rect.top + rect.height / 2) {
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

    /** Re-stamps --card-i and data-sort-custom so the new order survives a re-sort. */
    function restampOrder() {
        cards().forEach(function (card, i) {
            card.style.animation = 'none';
            card.style.setProperty('--card-i', i);
            card.setAttribute('data-sort-custom', i);
        });
        if (window.scriptyDocumentSort) {
            window.scriptyDocumentSort.setMode('custom');
        }
    }

    /**
     * Persists the current DOM order. `restore` puts the cards back the way they
     * were if the server says no, so a rejected move never leaves the page
     * showing an order that was not saved.
     */
    function save(restore) {
        if (!projectId) {
            return;
        }
        saving = true;
        listEl.classList.add('text-documents-list--saving');
        fetch('/api/document/reorder?projectId=' + encodeURIComponent(projectId), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify({ orderedIds: orderedIds() })
        }).then(function (res) {
            if (!res.ok) {
                throw new Error('reorder failed: ' + res.status);
            }
        }).catch(function () {
            restore();
            restampOrder();
            window.alert('Could not save the new order. The list has been put back.');
        }).then(function () {
            saving = false;
            listEl.classList.remove('text-documents-list--saving');
        });
    }

    /** Snapshots the current order as an undo closure for save(). */
    function snapshot() {
        var before = cards();
        return function () {
            before.forEach(function (card) { listEl.appendChild(card); });
        };
    }

    function move(card, insertBeforeNode) {
        var restore = snapshot();
        listEl.insertBefore(card, insertBeforeNode);
        restampOrder();
        save(restore);
    }

    // --- mouse drag ------------------------------------------------------

    listEl.addEventListener('dragstart', function (e) {
        var handle = e.target.closest ? e.target.closest('.text-document-drag-handle') : null;
        var card = handle ? handle.closest('.text-document-card[data-doc-id]') : null;
        if (!card || saving) {
            return;
        }
        dragCard = card;
        card.classList.add('dragging');
        e.dataTransfer.effectAllowed = 'move';
        if (e.dataTransfer.setData) {
            e.dataTransfer.setData('text/plain', card.getAttribute('data-doc-id') || 'document');
        }
        if (e.dataTransfer.setDragImage) {
            e.dataTransfer.setDragImage(card, 24, 16);
        }
    });

    listEl.addEventListener('dragend', function () {
        if (dragCard) {
            dragCard.classList.remove('dragging');
        }
        clearDropMarkers();
        dragCard = null;
    });

    listEl.addEventListener('dragover', function (e) {
        if (!dragCard) {
            return;
        }
        e.preventDefault();
        if (e.dataTransfer) {
            e.dataTransfer.dropEffect = 'move';
        }
        applyDropMarker(e.clientY);
    });

    listEl.addEventListener('drop', function (e) {
        if (!dragCard) {
            return;
        }
        e.preventDefault();
        var target = applyDropMarker(e.clientY);
        clearDropMarkers();
        var card = dragCard;
        card.classList.remove('dragging');
        dragCard = null;
        if (!target || target.row === card) {
            return;
        }
        move(card, target.insertBefore ? target.row : target.row.nextSibling);
    });

    // --- keyboard --------------------------------------------------------
    //
    // Arrow up/down on a focused handle moves the card one slot, so reordering
    // works without a pointer (and on touch, where HTML5 drag does not fire).

    listEl.addEventListener('keydown', function (e) {
        if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') {
            return;
        }
        var handle = e.target.closest ? e.target.closest('.text-document-drag-handle') : null;
        var card = handle ? handle.closest('.text-document-card[data-doc-id]') : null;
        if (!card || saving) {
            return;
        }
        var visible = cards().filter(function (c) { return !c.hidden; });
        var at = visible.indexOf(card);
        var to = at + (e.key === 'ArrowUp' ? -1 : 1);
        if (at === -1 || to < 0 || to >= visible.length) {
            return;
        }
        e.preventDefault();
        move(card, e.key === 'ArrowUp' ? visible[to] : visible[to].nextSibling);
        handle.focus();
    });
})();
