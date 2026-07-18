/**
 * Scripty selection context menu: Cut / Copy / Paste on right-click (or
 * long-press) over highlighted text.
 *
 * Only takes over the native menu when there is an actual selection, so a
 * plain right-click in an empty area still gets the browser's own menu.
 * Editable targets (textarea/input) get Cut/Copy/Paste; read-only text on the
 * page gets Copy alone.
 */
(function() {
    'use strict';

    if (window._scriptySelectionMenuInit) return;
    window._scriptySelectionMenuInit = true;

    var menuEl = null;
    var menuIndex = -1;
    var menuTarget = null;     // editable element, or null for page text
    var menuRange = null;      // { start, end } for editable targets
    var menuText = '';         // the highlighted text
    var openedAt = 0;

    function canReadClipboard() {
        return !!(navigator.clipboard && typeof navigator.clipboard.readText === 'function');
    }

    function isEditableTarget(el) {
        if (!el) return false;
        if (el.disabled || el.readOnly) return false;
        if (el.tagName === 'TEXTAREA') return true;
        return el.tagName === 'INPUT' &&
            /^(text|search|url|tel|email|password|number)$/i.test(el.type || 'text');
    }

    // Selection inside an editable field, or null when nothing is highlighted.
    function editableSelection(el) {
        if (typeof el.selectionStart !== 'number') return null;
        var start = Math.min(el.selectionStart, el.selectionEnd);
        var end = Math.max(el.selectionStart, el.selectionEnd);
        if (start === end) return null;
        return { start: start, end: end, text: (el.value || '').slice(start, end) };
    }

    function pageSelectionText() {
        var sel = window.getSelection && window.getSelection();
        if (!sel || sel.isCollapsed) return '';
        return String(sel.toString() || '');
    }

    function icon(kind) {
        var open = '<svg class="scripty-spell-ico" viewBox="0 0 16 16" width="14" height="14" ' +
            'aria-hidden="true" focusable="false" fill="none" stroke="currentColor" ' +
            'stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round">';
        if (kind === 'cut') {
            // scissors
            return open + '<circle cx="4" cy="12" r="1.7"/><circle cx="12" cy="12" r="1.7"/>' +
                '<path d="M5.2 10.8 12.5 2.5M10.8 10.8 3.5 2.5"/></svg>';
        }
        if (kind === 'copy') {
            // two stacked pages
            return open + '<rect x="5.5" y="5.5" width="8" height="8" rx="1.2"/>' +
                '<path d="M10.5 3.5h-7a1 1 0 0 0-1 1v7"/></svg>';
        }
        if (kind === 'paste') {
            // clipboard
            return open + '<path d="M6 3H4.6a1 1 0 0 0-1 1v8.4a1 1 0 0 0 1 1h6.8a1 1 0 0 0 1-1V4a1 1 0 0 0-1-1H10"/>' +
                '<rect x="6" y="1.9" width="4" height="2.2" rx=".7"/></svg>';
        }
        // select all — dashed frame
        return open + '<path d="M3 5.5V4a1 1 0 0 1 1-1h1.5M10.5 3H12a1 1 0 0 1 1 1v1.5' +
            'M13 10.5V12a1 1 0 0 1-1 1h-1.5M5.5 13H4a1 1 0 0 1-1-1v-1.5"/></svg>';
    }

    function ensureMenu() {
        if (menuEl) return menuEl;
        menuEl = document.createElement('ul');
        menuEl.id = 'scripty-selection-menu';
        // Reuses the spellcheck popup styling so both menus look identical.
        menuEl.className = 'scripty-selection-menu scripty-spell-suggestions hide-in-reader-view';
        menuEl.setAttribute('role', 'menu');
        menuEl.hidden = true;
        document.body.appendChild(menuEl);

        // mousedown (not click) so the field keeps its selection while we act.
        menuEl.addEventListener('mousedown', function(e) {
            e.preventDefault();
            e.stopPropagation();
            var li = e.target.closest('li[data-action]');
            if (!li || li.classList.contains('is-disabled')) return;
            runAction(li.getAttribute('data-action'));
        });
        return menuEl;
    }

    function hideMenu() {
        if (!menuEl) return;
        menuEl.hidden = true;
        menuEl.innerHTML = '';
        menuIndex = -1;
        menuTarget = null;
        menuRange = null;
        menuText = '';
        openedAt = 0;
    }

    function clampMenu(el) {
        var rect = el.getBoundingClientRect();
        var vw = document.documentElement.clientWidth;
        var vh = document.documentElement.clientHeight;
        var dx = 0, dy = 0;
        if (rect.right > vw - 8) dx = (vw - 8) - rect.right;
        if (rect.left + dx < 8) dx = 8 - rect.left;
        if (rect.bottom > vh - 8) dy = (vh - 8) - rect.bottom;
        if (rect.top + dy < 8) dy = 8 - rect.top;
        if (dx) el.style.left = Math.round((parseFloat(el.style.left) || 0) + dx) + 'px';
        if (dy) el.style.top = Math.round((parseFloat(el.style.top) || 0) + dy) + 'px';
    }

    function row(action, label, disabled) {
        return '<li role="menuitem" class="scripty-spell-action' +
            (disabled ? ' is-disabled" aria-disabled="true"' : '"') +
            ' data-action="' + action + '">' + icon(action) +
            '<span class="scripty-spell-action-label">' + label + '</span></li>';
    }

    function showMenu(x, y) {
        var el = ensureMenu();
        var parts = [];
        if (menuTarget) {
            parts.push(row('cut', 'Cut', false));
            parts.push(row('copy', 'Copy', false));
            parts.push(row('paste', 'Paste', !canReadClipboard()));
            parts.push('<li class="scripty-spell-sep" role="presentation" aria-hidden="true"></li>');
            parts.push(row('select-all', 'Select All', false));
        } else {
            parts.push(row('copy', 'Copy', false));
        }
        el.innerHTML = parts.join('');
        el.style.left = Math.round(x + window.scrollX) + 'px';
        el.style.top = Math.round(y + window.scrollY) + 'px';
        el.style.minWidth = '';
        menuIndex = -1;
        el.hidden = false;
        clampMenu(el);
        openedAt = Date.now();
    }

    function navigableItems() {
        if (!menuEl) return [];
        return Array.prototype.filter.call(
            menuEl.querySelectorAll('li[data-action]'),
            function(li) { return !li.classList.contains('is-disabled'); }
        );
    }

    // --- Actions ---

    function writeClipboard(text) {
        if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
            return navigator.clipboard.writeText(text).catch(function() {
                return legacyCopy(text);
            });
        }
        return Promise.resolve(legacyCopy(text));
    }

    // Fallback for browsers/contexts without the async clipboard API.
    function legacyCopy(text) {
        var helper = document.createElement('textarea');
        helper.value = text;
        helper.setAttribute('aria-hidden', 'true');
        helper.style.cssText = 'position:fixed;top:-1000px;left:-1000px;opacity:0;';
        document.body.appendChild(helper);
        helper.select();
        try {
            document.execCommand('copy');
        } catch (e) { /* nothing more we can do */ }
        document.body.removeChild(helper);
    }

    // Replace the stored selection. insertText keeps native undo history and
    // fires `input` on its own; the splice fallback has to do both by hand.
    function replaceSelection(el, range, text) {
        el.focus();
        el.setSelectionRange(range.start, range.end);
        var inserted = false;
        try {
            inserted = document.execCommand('insertText', false, text);
        } catch (e) {
            inserted = false;
        }
        if (!inserted) {
            var value = el.value || '';
            el.value = value.slice(0, range.start) + text + value.slice(range.end);
            var caret = range.start + text.length;
            el.setSelectionRange(caret, caret);
            el.dispatchEvent(new Event('input', { bubbles: true }));
        }
        if (typeof window.scriptyGrowTextarea === 'function') {
            window.scriptyGrowTextarea(el);
        }
    }

    function runAction(action) {
        var el = menuTarget;
        var range = menuRange;
        var text = menuText;
        hideMenu();

        if (action === 'copy') {
            writeClipboard(text);
            if (el) el.focus();
            return;
        }
        if (action === 'cut' && el && range) {
            writeClipboard(text).then(function() {
                replaceSelection(el, range, '');
            });
            return;
        }
        if (action === 'paste' && el && range) {
            if (!canReadClipboard()) return;
            navigator.clipboard.readText().then(function(clip) {
                if (clip == null) return;
                replaceSelection(el, range, String(clip));
            }).catch(function() {
                // Permission denied or empty clipboard — leave the text as-is.
                el.focus();
                el.setSelectionRange(range.start, range.end);
            });
            return;
        }
        if (action === 'select-all' && el) {
            el.focus();
            el.setSelectionRange(0, (el.value || '').length);
        }
    }

    // --- Events ---

    document.addEventListener('contextmenu', function(e) {
        // Another handler already claimed this one — the block-selection menu in
        // nav.html (reader view) or the spellcheck suggestions popup. Both run in
        // the capture phase and preventDefault, so a second menu would stack.
        if (e.defaultPrevented) return;

        var editable = e.target.closest && e.target.closest('textarea, input');
        if (isEditableTarget(editable)) {
            var sel = editableSelection(editable);
            if (!sel) return;   // no highlight — let the browser menu through
            menuTarget = editable;
            menuRange = { start: sel.start, end: sel.end };
            menuText = sel.text;
        } else {
            var pageText = pageSelectionText();
            if (!pageText.trim()) return;
            menuTarget = null;
            menuRange = null;
            menuText = pageText;
        }
        e.preventDefault();
        e.stopPropagation();
        showMenu(e.clientX, e.clientY);
    });

    document.addEventListener('keydown', function(e) {
        if (!menuEl || menuEl.hidden) return;
        var items = navigableItems();
        if (e.key === 'Escape') {
            e.preventDefault();
            var restore = menuTarget, range = menuRange;
            hideMenu();
            if (restore && range) {
                restore.focus();
                restore.setSelectionRange(range.start, range.end);
            }
            return;
        }
        if (e.key === 'ArrowDown' && items.length) {
            e.preventDefault();
            menuIndex = Math.min(items.length - 1, menuIndex + 1);
        } else if (e.key === 'ArrowUp' && items.length) {
            e.preventDefault();
            menuIndex = Math.max(0, menuIndex - 1);
        } else if (e.key === 'Enter' && menuIndex >= 0 && items[menuIndex]) {
            e.preventDefault();
            e.stopImmediatePropagation();
            runAction(items[menuIndex].getAttribute('data-action'));
            return;
        } else {
            return;
        }
        items.forEach(function(li, i) {
            li.classList.toggle('is-active', i === menuIndex);
        });
    }, true);

    document.addEventListener('mousedown', function(e) {
        if (!menuEl || menuEl.hidden) return;
        if (menuEl.contains(e.target)) return;
        hideMenu();
    }, true);

    document.addEventListener('scroll', function() {
        if (menuEl && !menuEl.hidden && Date.now() - openedAt > 150) hideMenu();
    }, true);

    window.addEventListener('resize', hideMenu);
    window.addEventListener('blur', hideMenu);

    if (document.body) {
        document.body.addEventListener('htmx:afterSwap', hideMenu);
    }
})();
