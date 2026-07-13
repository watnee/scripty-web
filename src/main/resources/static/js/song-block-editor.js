/**
 * Song editor blocks: enhances the song content textarea into per-line
 * block rows like the screenplay editor (Enter splits, Backspace/Delete
 * merge, arrows walk blocks, Alt+arrows / drag handle reorder).
 *
 * The original #text-document-content textarea stays in the DOM (hidden)
 * as the source of truth: every edit is serialized back into it and an
 * `input` event is dispatched so the existing autosave in
 * text-document-edit.js keeps working unchanged.
 *
 * Loaded from nav.html so it survives HTMX-boosted navigation
 * (allowScriptTags is false, so edit.html script tags are not executed
 * after a boost). Re-binds to the current page on htmx:afterSettle.
 */
(function () {
    'use strict';

    if (window._scriptySongBlockEditorInit) return;
    window._scriptySongBlockEditorInit = true;

    var current = null; // { ta, container }

    function initEditor() {
        var main = document.querySelector('main.text-document-edit-page--song');
        var ta = document.getElementById('text-document-content');
        if (!main || !ta || !main.contains(ta)) {
            current = null;
            return;
        }
        if (current && current.ta === ta && document.contains(current.container)) {
            return;
        }
        current = buildEditor(ta);
    }

    function buildEditor(ta) {
        var label = ta.closest('.text-document-content-label');
        var shell = ta.closest('.text-document-editor-shell');
        if (!label || !shell) {
            return null;
        }

        var placeholder = (ta.getAttribute('placeholder') || '').split('\n')[0];
        var container = document.createElement('div');
        container.className = 'song-block-editor';

        var lines = (ta.value || '').replace(/\r\n?/g, '\n').split('\n');
        lines.forEach(function (line) {
            container.appendChild(createRow(line));
        });

        label.parentNode.insertBefore(container, label.nextSibling);
        shell.classList.add('song-blocks-on');
        growAll();
        updatePlaceholder();

        function rowTextareas() {
            return container.querySelectorAll('.song-block-textarea');
        }

        function taOf(row) {
            return row ? row.querySelector('.song-block-textarea') : null;
        }

        function isRowTextarea(el) {
            return !!(el && el.classList && el.classList.contains('song-block-textarea'));
        }

        function grow(el) {
            el.style.height = 'auto';
            el.style.height = el.scrollHeight + 'px';
        }

        function growAll() {
            rowTextareas().forEach(grow);
        }

        function focusAt(el, pos) {
            el.focus();
            try {
                el.setSelectionRange(pos, pos);
            } catch (err) { /* ignore */ }
        }

        function updatePlaceholder() {
            var rows = rowTextareas();
            rows.forEach(function (el, i) {
                el.placeholder = (i === 0 && rows.length === 1) ? placeholder : '';
            });
        }

        function sync() {
            var values = [];
            rowTextareas().forEach(function (el) {
                values.push(el.value);
            });
            updatePlaceholder();
            var joined = values.join('\n');
            if (ta.value === joined) {
                return;
            }
            ta.value = joined;
            ta.dispatchEvent(new Event('input', { bubbles: true }));
        }

        function isSingleVisualLine(el) {
            var cs = window.getComputedStyle(el);
            var lineHeight = parseFloat(cs.lineHeight) || (parseFloat(cs.fontSize) || 16) * 1.6;
            return el.clientHeight < lineHeight * 1.8;
        }

        // All handlers are delegated to the container: the spellcheck
        // toggle remounts focused textareas via cloneNode, which would
        // silently drop per-textarea listeners.
        container.addEventListener('input', function (e) {
            if (!isRowTextarea(e.target)) return;
            grow(e.target);
            sync();
        });

        container.addEventListener('keydown', function (e) {
            var t = e.target;
            if (!isRowTextarea(t)) return;
            if (e.isComposing || e.keyCode === 229) return;
            // e.g. the spellcheck popup claims arrow keys while it is open
            if (e.defaultPrevented) return;
            var row = t.closest('.song-block-row');

            if (e.key === 'Enter' && !e.metaKey && !e.ctrlKey) {
                e.preventDefault();
                var tail = t.value.slice(t.selectionEnd);
                t.value = t.value.slice(0, t.selectionStart);
                var next = createRow(tail);
                row.after(next);
                grow(t);
                var nt = taOf(next);
                grow(nt);
                focusAt(nt, 0);
                sync();
                return;
            }

            if (e.key === 'Tab' && !e.metaKey && !e.ctrlKey && !e.altKey && !e.shiftKey) {
                e.preventDefault();
                var s = t.selectionStart;
                t.value = t.value.slice(0, s) + '    ' + t.value.slice(t.selectionEnd);
                t.selectionStart = t.selectionEnd = s + 4;
                grow(t);
                sync();
                return;
            }

            if (e.key === 'Backspace' && t.selectionStart === 0 && t.selectionEnd === 0) {
                var prev = row.previousElementSibling;
                if (!prev) return;
                e.preventDefault();
                var pt = taOf(prev);
                var pos = pt.value.length;
                pt.value += t.value;
                row.remove();
                grow(pt);
                focusAt(pt, pos);
                sync();
                return;
            }

            if (e.key === 'Delete' && t.selectionStart === t.value.length
                    && t.selectionEnd === t.value.length) {
                var nextRow = row.nextElementSibling;
                if (!nextRow) return;
                e.preventDefault();
                var keep = t.value.length;
                t.value += taOf(nextRow).value;
                nextRow.remove();
                grow(t);
                focusAt(t, keep);
                sync();
                return;
            }

            if ((e.key === 'ArrowUp' || e.key === 'ArrowDown')
                    && e.altKey && !e.metaKey && !e.ctrlKey && !e.shiftKey) {
                e.preventDefault();
                var sibling = e.key === 'ArrowUp'
                    ? row.previousElementSibling : row.nextElementSibling;
                if (!sibling) return;
                if (e.key === 'ArrowUp') {
                    sibling.before(row);
                } else {
                    sibling.after(row);
                }
                t.focus();
                sync();
                return;
            }

            if ((e.key === 'ArrowUp' || e.key === 'ArrowDown')
                    && !e.altKey && !e.metaKey && !e.ctrlKey && !e.shiftKey) {
                var jump = function () {
                    var target = e.key === 'ArrowUp'
                        ? row.previousElementSibling : row.nextElementSibling;
                    if (!target) return;
                    var tt = taOf(target);
                    focusAt(tt, Math.min(t.selectionStart, tt.value.length));
                };
                if (isSingleVisualLine(t)) {
                    e.preventDefault();
                    jump();
                    return;
                }
                // Wrapped block: let the caret walk visual lines first and
                // only jump rows once an arrow press no longer moves it.
                var start = t.selectionStart;
                var end = t.selectionEnd;
                setTimeout(function () {
                    if (document.activeElement !== t) return;
                    if (t.selectionStart !== start || t.selectionEnd !== end) return;
                    jump();
                }, 0);
            }
        });

        container.addEventListener('paste', function (e) {
            var t = e.target;
            if (!isRowTextarea(t)) return;
            var data = e.clipboardData || window.clipboardData;
            var text = data ? data.getData('text') : '';
            if (!text || !/[\r\n]/.test(text)) return;
            e.preventDefault();
            var pasted = String(text).replace(/\r\n?/g, '\n').split('\n');
            var row = t.closest('.song-block-row');
            var tail = t.value.slice(t.selectionEnd);
            t.value = t.value.slice(0, t.selectionStart) + pasted[0];
            var anchor = row;
            var lastTa = t;
            for (var i = 1; i < pasted.length; i++) {
                var nr = createRow(pasted[i]);
                anchor.after(nr);
                anchor = nr;
                lastTa = taOf(nr);
            }
            var caret = lastTa.value.length;
            lastTa.value += tail;
            growAll();
            focusAt(lastTa, caret);
            sync();
        });

        // Click in the empty space below the last block focuses it,
        // mirroring how the old full-height textarea behaved.
        container.addEventListener('mousedown', function (e) {
            if (e.target !== container) return;
            var rows = rowTextareas();
            var last = rows[rows.length - 1];
            if (!last) return;
            e.preventDefault();
            focusAt(last, last.value.length);
        });

        var dragRow = null;

        container.addEventListener('dragstart', function (e) {
            var handle = e.target.closest ? e.target.closest('.song-block-handle') : null;
            if (!handle) return;
            dragRow = handle.closest('.song-block-row');
            dragRow.classList.add('song-block-row--dragging');
            e.dataTransfer.effectAllowed = 'move';
            try {
                e.dataTransfer.setData('text/plain', '');
            } catch (err) { /* ignore */ }
        });

        container.addEventListener('dragover', function (e) {
            if (!dragRow) return;
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
            var over = e.target.closest ? e.target.closest('.song-block-row') : null;
            if (!over || over === dragRow) return;
            var rect = over.getBoundingClientRect();
            if (e.clientY < rect.top + rect.height / 2) {
                over.before(dragRow);
            } else {
                over.after(dragRow);
            }
        });

        container.addEventListener('drop', function (e) {
            if (dragRow) e.preventDefault();
        });

        container.addEventListener('dragend', function () {
            if (!dragRow) return;
            dragRow.classList.remove('song-block-row--dragging');
            dragRow = null;
            sync();
        });

        window.addEventListener('resize', function () {
            if (current && current.container === container && document.contains(container)) {
                growAll();
            }
        });

        return { ta: ta, container: container };
    }

    function createRow(text) {
        var row = document.createElement('div');
        row.className = 'song-block-row';

        var handle = document.createElement('button');
        handle.type = 'button';
        handle.className = 'song-block-handle';
        handle.draggable = true;
        handle.tabIndex = -1;
        handle.title = 'Drag to reorder';
        handle.setAttribute('aria-hidden', 'true');
        handle.textContent = '⋮⋮';

        var el = document.createElement('textarea');
        el.className = 'song-block-textarea';
        el.rows = 1;
        el.value = text;
        el.autocomplete = 'off';
        el.setAttribute('aria-label', 'Lyric line');
        var spellOn = typeof window.scriptyIsSpellcheckEnabled === 'function'
            ? window.scriptyIsSpellcheckEnabled() : true;
        el.setAttribute('spellcheck', spellOn ? 'true' : 'false');

        row.appendChild(handle);
        row.appendChild(el);
        return row;
    }

    document.body.addEventListener('htmx:afterSettle', initEditor);
    document.body.addEventListener('htmx:afterSwap', initEditor);
    document.body.addEventListener('htmx:historyRestore', initEditor);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initEditor);
    } else {
        initEditor();
    }
})();
