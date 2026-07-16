/**
 * Notion-style block selection for the screenplay and song editors.
 *
 * Typing inside a block never highlights it — the highlight only appears once
 * the user is selecting *blocks* rather than text:
 *   - dragging out of the block the drag started in selects the whole range,
 *   - Cmd/Ctrl+A escalates from "all text in this block" to "all blocks",
 *   - Escape leaves the text and selects the block being edited.
 * Any click, keystroke, or edit drops the selection again.
 *
 * Loaded from nav.html so it survives HTMX-boosted navigation, and delegated on
 * document because both block lists are re-rendered wholesale after changes.
 */
(function () {
    'use strict';

    if (window._scriptyBlockSelectionInit) {
        return;
    }
    window._scriptyBlockSelectionInit = true;

    var SELECTED = 'block-selected';
    var SELECTING = 'scripty-block-selecting';

    // Each editor surface: the list that bounds a selection, and its rows.
    var SURFACES = [
        { list: '#project-script-body', row: '.block-row[data-block-id]' },
        { list: '.song-blocks-editor', row: '.song-block-row[data-block-id]' }
    ];

    var anchorRow = null;    // row the current mouse drag started in
    var dragSurface = null;
    var dragging = false;    // true once the drag has left the anchor row
    var selectedRows = [];

    function surfaceOf(node) {
        if (!node || !node.closest) {
            return null;
        }
        for (var i = 0; i < SURFACES.length; i++) {
            var list = node.closest(SURFACES[i].list);
            if (list) {
                return { list: list, rowSel: SURFACES[i].row };
            }
        }
        return null;
    }

    function rowsIn(surface) {
        return Array.prototype.slice.call(surface.list.querySelectorAll(surface.rowSel));
    }

    function rowOf(node, surface) {
        var row = node && node.closest ? node.closest(surface.rowSel) : null;
        return row && surface.list.contains(row) ? row : null;
    }

    function clearNativeSelection() {
        var sel = window.getSelection();
        if (sel && !sel.isCollapsed) {
            sel.removeAllRanges();
        }
    }

    function hasSelection() {
        return selectedRows.length > 0;
    }

    function clearSelection() {
        selectedRows.forEach(function (row) {
            row.classList.remove(SELECTED);
        });
        selectedRows = [];
        document.documentElement.classList.remove(SELECTING);
    }

    function selectRows(rows) {
        clearSelection();
        if (!rows.length) {
            return;
        }
        selectedRows = rows;
        rows.forEach(function (row) {
            row.classList.add(SELECTED);
        });
        // Whole blocks are highlighted now, so the browser's own text highlight
        // would only double up on it.
        document.documentElement.classList.add(SELECTING);
        clearNativeSelection();
    }

    // Leaving the text is what turns a text selection into a block selection.
    // In both editors this commits the pending edit through the existing blur
    // handlers, exactly as clicking away would.
    function leaveTextEditing(within) {
        var active = document.activeElement;
        if (!active || active === document.body) {
            return;
        }
        var editable = active.tagName === 'TEXTAREA' || active.tagName === 'INPUT'
            || active.isContentEditable;
        if (editable && (!within || within.contains(active))) {
            active.blur();
        }
    }

    function selectRange(surface, from, to) {
        var rows = rowsIn(surface);
        var a = rows.indexOf(from);
        var b = rows.indexOf(to);
        if (a < 0 || b < 0) {
            return;
        }
        selectRows(rows.slice(Math.min(a, b), Math.max(a, b) + 1));
    }

    // The row under the pointer, or the nearest one by vertical distance so a
    // drag through the gutter or past the last block still tracks.
    function rowAt(surface, clientX, clientY) {
        var el = document.elementFromPoint(clientX, clientY);
        var row = el ? rowOf(el, surface) : null;
        if (row) {
            return row;
        }
        var rows = rowsIn(surface);
        var best = null;
        var bestDist = Infinity;
        rows.forEach(function (candidate) {
            var rect = candidate.getBoundingClientRect();
            var dist = clientY < rect.top ? rect.top - clientY
                : (clientY > rect.bottom ? clientY - rect.bottom : 0);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        });
        return best;
    }

    function menusOpen() {
        return !!document.querySelector('.nav-dropdown-menu.open, .nav-dropdown.open, .song-block-menu-dropdown.open');
    }

    // --- drag across blocks ----------------------------------------------

    document.addEventListener('mousedown', function (e) {
        if (e.button !== 0) {
            return;
        }
        if (hasSelection()) {
            clearSelection();
        }
        var surface = surfaceOf(e.target);
        if (!surface) {
            return;
        }
        // The controls run their own drag (reorder) and click (menu) gestures.
        if (e.target.closest('button, a, input, select, .block-left-controls, .block-actions, .song-block-controls')) {
            return;
        }
        var row = rowOf(e.target, surface);
        if (!row) {
            return;
        }
        anchorRow = row;
        dragSurface = surface;
        dragging = false;
    }, true);

    document.addEventListener('mousemove', function (e) {
        if (!anchorRow || !e.buttons) {
            return;
        }
        var row = rowAt(dragSurface, e.clientX, e.clientY);
        if (!row) {
            return;
        }
        if (!dragging && row === anchorRow) {
            return; // still selecting text inside the one block
        }
        if (!dragging) {
            dragging = true;
            leaveTextEditing(dragSurface.list);
        }
        selectRange(dragSurface, anchorRow, row);
    });

    document.addEventListener('mouseup', function () {
        if (dragging) {
            // The screenplay opens a block for editing on click; this drag ended
            // in a block, so swallow that click.
            window.scriptySuppressBlockEditClick = true;
            setTimeout(function () { window.scriptySuppressBlockEditClick = false; }, 0);
            // The browser kept extending its own text selection under the block
            // highlight as the drag ran; drop it so a copy takes the blocks.
            clearNativeSelection();
        }
        anchorRow = null;
        dragSurface = null;
        dragging = false;
    });

    // --- keyboard ---------------------------------------------------------

    document.addEventListener('keydown', function (e) {
        var active = document.activeElement;
        var surface = surfaceOf(active) || surfaceOf(e.target);

        if ((e.metaKey || e.ctrlKey) && (e.key === 'a' || e.key === 'A')) {
            if (hasSelection()) {
                var selSurface = surfaceOf(selectedRows[0]);
                if (selSurface) {
                    e.preventDefault();
                    selectRows(rowsIn(selSurface));
                    return;
                }
            }
            if (!surface) {
                return;
            }
            var row = rowOf(active, surface);
            if (row && active.tagName === 'TEXTAREA') {
                var whole = active.value.length === 0
                    || (active.selectionStart === 0 && active.selectionEnd === active.value.length);
                if (!whole) {
                    return; // first press: let the browser select this block's text
                }
                e.preventDefault();
                leaveTextEditing(surface.list);
                selectRows(rowsIn(surface));
                return;
            }
            if (!row && !surface.list.contains(active)) {
                return;
            }
            e.preventDefault();
            selectRows(rowsIn(surface));
            return;
        }

        if (e.key === 'Escape') {
            if (menusOpen()) {
                return; // the editors close their own menus on Escape first
            }
            if (hasSelection()) {
                e.preventDefault();
                clearSelection();
                return;
            }
            if (!surface) {
                return;
            }
            var current = rowOf(active, surface);
            if (current && (active.tagName === 'TEXTAREA' || active.isContentEditable)) {
                e.preventDefault();
                leaveTextEditing(surface.list);
                selectRows([current]);
            }
            return;
        }

        // Any other keystroke means the user is back to writing.
        if (hasSelection() && !e.metaKey && !e.ctrlKey && !e.altKey
            && e.key !== 'Shift' && e.key !== 'Control' && e.key !== 'Alt' && e.key !== 'Meta') {
            clearSelection();
        }
    }, true);

    // Not focusin: both editors refocus a textarea of their own after an edit
    // commits, and treating that as "editing resumed" would drop the selection
    // the blur just made. Clicking and typing clear it above.
    document.addEventListener('input', clearSelection);

    // Leaving a block commits its edit, and the screenplay answers that by
    // swapping the block's content — so a swap must not drop the selection the
    // blur just made. Only rows that are actually gone leave it.
    document.body.addEventListener('htmx:afterSwap', function () {
        if (!hasSelection()) {
            return;
        }
        var live = selectedRows.filter(function (row) {
            return row.isConnected;
        });
        if (live.length === selectedRows.length) {
            return;
        }
        if (!live.length) {
            clearSelection();
            return;
        }
        selectedRows = live;
    });

    document.body.addEventListener('htmx:historyRestore', clearSelection);
})();
