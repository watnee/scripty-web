/**
 * Element type toolbar (Scene, Action, Dialogue, ...).
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (inline/page scripts are not executed when allowScriptTags
 * is false).
 *
 * Flow:
 * 1. mousedown: capture target block + content snapshot, prevent focus steal
 * 2. click: apply type using the snapshot (never trust a wiped textarea)
 * 3. single block: POST /block/setTypeAndContent and swap edit HTML in place
 * 4. multi select: POST /block/bulkSetType (page reload)
 *
 * Keyboard: ⌘/Ctrl+1–7 set the classic screenplay types (works while typing).
 */
(function() {
    'use strict';

    if (window._scriptyElementTypeInit) return;
    window._scriptyElementTypeInit = true;

    var TYPE_LABELS = {
        SCENE: 'Scene',
        ACTION: 'Action',
        TEXT: 'Text',
        CHARACTER: 'Character',
        DIALOGUE: 'Dialogue',
        DUAL_DIALOGUE: 'Dual',
        PARENTHETICAL: '(Paren)',
        TRANSITION: 'Transition',
        SHOT: 'Shot',
        LYRICS: 'Lyrics',
        CENTERED: 'Centered',
        SECTION: 'Section',
        SYNOPSIS: 'Synopsis',
        NOTE: 'Note',
        PAGE_BREAK: 'Page Break'
    };

    /** Final Draft–style digit map (matches Tab cycle order). */
    var TYPE_BY_DIGIT = {
        '1': 'SCENE',
        '2': 'ACTION',
        '3': 'CHARACTER',
        '4': 'PARENTHETICAL',
        '5': 'DIALOGUE',
        '6': 'TRANSITION',
        '7': 'SHOT'
    };
    var OUTLINE_TYPE_BY_DIGIT = {
        '1': 'SCENE',
        '2': 'SECTION',
        '3': 'SYNOPSIS'
    };
    var TYPE_SHORTCUT_DIGIT = {
        SCENE: '1',
        ACTION: '2',
        CHARACTER: '3',
        PARENTHETICAL: '4',
        DIALOGUE: '5',
        TRANSITION: '6',
        SHOT: '7'
    };

    var snapshot = null;
    var inFlight = false;

    function typeLabel(type) {
        if (type == null || type === '') return TYPE_LABELS.ACTION;
        var key = String(type).toUpperCase();
        if (TYPE_LABELS[key]) return TYPE_LABELS[key];
        return key.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, function(c) {
            return c.toUpperCase();
        });
    }
    window.scriptyBlockTypeLabel = typeLabel;

    function projectId() {
        if (typeof window.scriptyResolveProjectId === 'function') {
            return window.scriptyResolveProjectId() || '';
        }
        var params = new URLSearchParams(window.location.search);
        return params.get('id') || '';
    }

    function findRowById(blockId) {
        if (!blockId) return null;
        var selector = '.block-row[data-block-id="' + blockId + '"], tr[data-block-id="' + blockId + '"]';
        // Prefer a row that still has an open + type menu / edit form when
        // duplicate data-block-id nodes briefly exist after create swaps.
        var matches = document.querySelectorAll(selector);
        if (matches.length <= 1) return matches[0] || null;
        for (var i = 0; i < matches.length; i++) {
            if (matches[i].querySelector('.create-below-menu-dropdown.open')) return matches[i];
        }
        for (var j = 0; j < matches.length; j++) {
            if (matches[j].querySelector('.block-content form[hx-post*="/block/editInline"]')) {
                return matches[j];
            }
        }
        return matches[matches.length - 1];
    }

    function findEditForm(row) {
        return row
            ? row.querySelector('.block-content form[hx-post*="/block/editInline"]')
            : null;
    }

    function normalizeText(text) {
        if (text == null) return '';
        return String(text).replace(/\u00a0/g, '');
    }

    function readRowContent(row) {
        if (!row) return '';
        var textarea = row.querySelector('textarea[name="content"]');
        if (textarea && textarea.value != null && textarea.value !== '') {
            return textarea.value;
        }
        var preview = window.scriptyBlockCaretPreviewActive;
        if (preview && preview.row === row && preview.textEl) {
            return normalizeText(preview.textEl.textContent);
        }
        var display = row.querySelector('.reader-visible-text, .script-block-text:not(textarea)');
        if (display) {
            return normalizeText(display.textContent);
        }
        return textarea ? textarea.value : '';
    }

    function readCaret(row) {
        var textarea = row && row.querySelector('textarea[name="content"]');
        if (!textarea) return { start: 0, end: 0 };
        return {
            start: textarea.selectionStart || 0,
            end: textarea.selectionEnd || 0
        };
    }

    function selectedIds() {
        return window.scriptyGetSelectedBlockIds
            ? window.scriptyGetSelectedBlockIds()
            : [];
    }

    function activeBlockId() {
        return window.scriptyGetActiveBlockId
            ? window.scriptyGetActiveBlockId(null)
            : null;
    }

    function createRowFromEditable(el) {
        if (!el || el.name !== 'content' || el.tagName !== 'TEXTAREA') return null;
        var row = el.closest('.block-row:not([data-block-id]), tr:not([data-block-id])');
        if (!row || row.classList.contains('project-script-select-row')) return null;
        if (row.classList.contains('project-script-select-spacer')) return null;
        var textarea = row.querySelector('textarea[name="content"]');
        return textarea === el ? row : null;
    }

    function findCreateRow() {
        var focused = createRowFromEditable(document.activeElement);
        if (focused) return focused;

        // Keyboard activation focuses the type button; recover the create row
        // only when the event originated from the element toolbar.
        var active = document.activeElement;
        var onTypeControl = !!(active && active.closest &&
            active.closest('.bulk-type-btn, .element-type-actions, .project-script-toolbar'));
        if (!onTypeControl) return null;

        var last = window.scriptyLastFocusedEditable;
        if (last && last.isConnected) {
            return createRowFromEditable(last);
        }
        return null;
    }

    function clearTextSelection() {
        var selection = window.getSelection && window.getSelection();
        if (selection && !selection.isCollapsed) {
            selection.removeAllRanges();
        }
    }

    function beginWait() {
        if (window.scriptyBeginBlockTypeChangeWait) {
            window.scriptyBeginBlockTypeChangeWait();
        } else {
            window.scriptyBlockTypeChangePending = true;
        }
    }

    function finishWait() {
        if (window.scriptyFinishBlockTypeChangeWait) {
            window.scriptyFinishBlockTypeChangeWait();
        } else {
            window.scriptyBlockTypeChangePending = false;
        }
    }

    function isMacPlatform() {
        return window.scriptyIsMac
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
    }

    function shortcutLabelForDigit(digit) {
        return isMacPlatform() ? '⌘' + digit : 'Ctrl+' + digit;
    }

    function isOutlineModeOn() {
        return !!(window.scriptyIsOutlineMode && window.scriptyIsOutlineMode())
            || document.documentElement.classList.contains('scripty-outline-mode');
    }

    function typeForShortcutDigit(digit) {
        if (isOutlineModeOn()) {
            return OUTLINE_TYPE_BY_DIGIT[digit] || null;
        }
        return TYPE_BY_DIGIT[digit] || null;
    }

    function syncElementTypeShortcutLabels() {
        if (!document.querySelector('.project-script')) return;
        var outline = isOutlineModeOn();
        document.querySelectorAll('.bulk-type-btn[data-bulk-type]').forEach(function(btn) {
            var type = (btn.getAttribute('data-bulk-type') || '').toUpperCase();
            var digit = null;
            if (outline) {
                if (type === 'SCENE') digit = '1';
                else if (type === 'SECTION') digit = '2';
                else if (type === 'SYNOPSIS') digit = '3';
            } else {
                digit = TYPE_SHORTCUT_DIGIT[type] || null;
            }
            var baseTitle = btn.getAttribute('data-base-title') || btn.title || typeLabel(type);
            if (!btn.getAttribute('data-base-title')) {
                btn.setAttribute('data-base-title', baseTitle);
            }
            var hint = digit ? shortcutLabelForDigit(digit) : '';
            var title = hint ? baseTitle + ' (' + hint + ')' : baseTitle;
            btn.title = title;
            btn.setAttribute('aria-label', title);

            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(btn, hint);
            } else {
                var shortcutEl = btn.querySelector('.element-type-shortcut, .nav-dropdown-shortcut');
                if (digit) {
                    if (!shortcutEl) {
                        shortcutEl = document.createElement('span');
                        shortcutEl.className = 'element-type-shortcut nav-dropdown-shortcut';
                        shortcutEl.setAttribute('aria-hidden', 'true');
                        btn.appendChild(shortcutEl);
                    }
                    shortcutEl.textContent = hint;
                    shortcutEl.hidden = false;
                } else if (shortcutEl) {
                    shortcutEl.hidden = true;
                }
            }
        });
    }
    window.scriptySyncElementTypeShortcutLabels = syncElementTypeShortcutLabels;

    function syncToolbar(type) {
        var current = type ? String(type).toUpperCase() : '';
        document.querySelectorAll('.bulk-type-btn').forEach(function(btn) {
            var active = !!(current && btn.getAttribute('data-bulk-type') === current);
            btn.classList.toggle('is-active', active);
            btn.setAttribute('aria-pressed', active ? 'true' : 'false');
            btn.setAttribute('aria-checked', active ? 'true' : 'false');
        });
        var label = document.querySelector('#project-element-type-dropdown .element-type-toolbar-label');
        if (label) {
            label.textContent = 'Elements';
        }
        var toggle = document.querySelector('#project-element-type-dropdown .element-type-toolbar-btn');
        if (toggle) {
            var title = 'Elements' + (current ? ': ' + typeLabel(current) : '');
            toggle.title = title;
            toggle.setAttribute('aria-label', title);
        }
        syncElementTypeShortcutLabels();
    }
    window.scriptySyncElementTypeToolbar = syncToolbar;

    function applyTypeClass(blockContent, type) {
        if (!blockContent || !type) return;
        Array.from(blockContent.classList).forEach(function(cls) {
            if (cls.indexOf('block-type-') === 0) {
                blockContent.classList.remove(cls);
            }
        });
        blockContent.classList.add('block-type-' + type.toLowerCase());
        blockContent.classList.remove('script-block--dialogue', 'script-block--action');
        blockContent.classList.add(
            type === 'DIALOGUE' ? 'script-block--dialogue' : 'script-block--action'
        );

        var row = blockContent.closest('.block-row, tr[data-block-id]');
        if (!row) return;
        row.setAttribute('data-block-type', type);
        var label = row.querySelector('.block-element-label');
        if (label) {
            label.setAttribute('data-block-type', type);
            label.textContent = typeLabel(type);
        }
        syncToolbar(type);
    }
    window.scriptyApplyBlockTypeClass = applyTypeClass;

    function setCreateRowType(row, type) {
        if (!row || !type) return;
        var blockContent = row.querySelector('.block-content');
        applyTypeClass(blockContent, type);
        var form = row.querySelector('form');
        if (!form) return;
        var typeInput = form.querySelector('input[name="type"]');
        if (!typeInput) {
            typeInput = document.createElement('input');
            typeInput.type = 'hidden';
            typeInput.name = 'type';
            form.appendChild(typeInput);
        }
        typeInput.value = type;
    }

    function captureTarget(preferredId) {
        var ids = selectedIds();
        // When a specific block is requested (Tab cycle, Fountain detect, drag menu),
        // always target that block — ignore multi-select bulk mode.
        if (!preferredId && ids.length > 1) {
            return {
                mode: 'bulk',
                ids: ids,
                content: null,
                caret: { start: 0, end: 0 },
                personId: null,
                tags: null
            };
        }

        // Trailing create rows are often focused while a prior saved block is
        // still remembered as "active". Prefer the create row so type changes
        // apply to the last (unsaved) block the user is actually editing.
        if (ids.length === 0 && !preferredId) {
            var createRow = findCreateRow();
            if (createRow) {
                return { mode: 'create', row: createRow, ids: [], content: null };
            }
        }

        var blockId = preferredId
            || window.scriptyPendingCreateBelowTypeTargetId
            || (ids.length === 1 ? ids[0] : activeBlockId());
        if (!blockId) {
            return null;
        }

        var row = findRowById(blockId);
        if (!row) {
            var orphanCreate = findCreateRow();
            if (orphanCreate) {
                return { mode: 'create', row: orphanCreate, ids: [], content: null };
            }
            return null;
        }

        var form = findEditForm(row);
        var personId = null;
        var tags = null;
        if (form) {
            var formData = new FormData(form);
            personId = formData.get('personId') || null;
            tags = formData.get('tags');
            if (tags === '') tags = null;
        }

        return {
            mode: 'single',
            ids: [String(blockId)],
            content: readRowContent(row),
            caret: readCaret(row),
            personId: personId,
            tags: tags
        };
    }

    function growTextarea(textarea) {
        if (typeof window.scriptyGrowTextarea === 'function') {
            window.scriptyGrowTextarea(textarea);
            return;
        }
        if (!textarea) return;
        textarea.style.height = 'auto';
        textarea.style.height = textarea.scrollHeight + 'px';
    }

    function applySingle(type, captured) {
        var blockId = captured.ids[0];
        var fields = {
            id: blockId,
            type: type,
            projectId: projectId(),
            partial: 'project'
        };
        if (captured.content != null) {
            fields.content = captured.content;
        }
        if (captured.personId) {
            fields.personId = captured.personId;
        }
        if (captured.tags != null) {
            fields.tags = captured.tags;
        }

        beginWait();
        inFlight = true;

        return fetch('/block/setTypeAndContent', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams(fields).toString(),
            credentials: 'same-origin'
        }).then(function(response) {
            if (!response.ok) throw new Error('setTypeAndContent failed');
            return response.text();
        }).then(function(html) {
            var row = findRowById(blockId);
            var blockContent = row && row.querySelector('.block-content');
            if (!blockContent) return;

            applyTypeClass(blockContent, type);
            blockContent.innerHTML = html;
            if (typeof htmx !== 'undefined') {
                htmx.process(blockContent);
            }
            applyTypeClass(blockContent, type);

            if (window.scriptyClearBlockCaretPreview) {
                window.scriptyClearBlockCaretPreview();
            }
            sessionStorage.removeItem('activeBlockId');
            sessionStorage.removeItem('activeBlockCaretStart');
            sessionStorage.removeItem('activeBlockCaretEnd');

            var textarea = blockContent.querySelector('textarea[name="content"]');
            if (!textarea) return;
            growTextarea(textarea);
            textarea.focus({ preventScroll: true });
            var len = textarea.value.length;
            var start = Math.max(0, Math.min(captured.caret.start, len));
            var end = Math.max(start, Math.min(captured.caret.end, len));
            textarea.setSelectionRange(start, end);
        }).catch(function() {
            // Fallback: full navigation save
            var form = document.createElement('form');
            form.method = 'POST';
            form.action = '/block/setTypeAndContent';
            form.setAttribute('hx-boost', 'false');
            Object.keys(fields).forEach(function(name) {
                if (name === 'partial') return;
                var input = document.createElement('input');
                input.type = 'hidden';
                input.name = name;
                input.value = fields[name];
                form.appendChild(input);
            });
            if (window.scriptyAppendCsrfToForm) {
                window.scriptyAppendCsrfToForm(form);
            }
            document.body.appendChild(form);
            form.submit();
        }).finally(function() {
            inFlight = false;
            snapshot = null;
            finishWait();
        });
    }

    function applyBulk(type, captured) {
        beginWait();
        sessionStorage.setItem('activeBlockId', captured.ids[0]);

        function submit() {
            var form = document.createElement('form');
            form.method = 'POST';
            form.action = '/block/bulkSetType';
            form.setAttribute('hx-boost', 'false');
            [
                ['ids', captured.ids.join(',')],
                ['type', type],
                ['projectId', projectId()]
            ].forEach(function(pair) {
                var input = document.createElement('input');
                input.type = 'hidden';
                input.name = pair[0];
                input.value = pair[1];
                form.appendChild(input);
            });
            if (window.scriptyAppendCsrfToForm) {
                window.scriptyAppendCsrfToForm(form);
            }
            document.body.appendChild(form);
            form.submit();
        }

        if (typeof window.scriptyFlushOpenBlockEdits === 'function') {
            window.scriptyFlushOpenBlockEdits(submit, captured.ids);
        } else {
            submit();
        }
    }

    function onTypeButton(type, captured) {
        if (!type) return;

        if (!captured) {
            if (window.scriptyBulkSelectionAlert) {
                window.scriptyBulkSelectionAlert();
            } else {
                alert('Please select at least one block.');
            }
            finishWait();
            return;
        }

        if (captured.mode === 'create') {
            setCreateRowType(captured.row, type);
            snapshot = null;
            finishWait();
            return;
        }

        if (captured.mode === 'bulk') {
            applyBulk(type, captured);
            return;
        }

        applySingle(type, captured);
    }

    /** Programmatic type change for Tab cycling / Fountain detection / + menu. */
    window.scriptyApplyElementType = function(type, preferredBlockId) {
        if (!type) return;
        // Prefer the requested block even if a prior type change is still saving.
        // Dropping the call left the + menu looking broken after rapid picks.
        if (inFlight && preferredBlockId) {
            inFlight = false;
            snapshot = null;
            finishWait();
        } else if (inFlight) {
            return;
        }
        var captured = captureTarget(preferredBlockId || null);
        if (captured && (captured.mode === 'single' || captured.mode === 'bulk')) {
            beginWait();
        }
        onTypeButton(type, captured);
    };

    function getActions() {
        return document.getElementById('element-type-actions')
            || document.querySelector('.element-type-actions');
    }

    document.body.addEventListener('mousedown', function(e) {
        var btn = e.target.closest('.bulk-type-btn');
        if (!btn || !document.querySelector('.project-script')) return;

        clearTextSelection();
        e.preventDefault();

        if (inFlight) return;

        snapshot = captureTarget(null);
        if (snapshot && (snapshot.mode === 'single' || snapshot.mode === 'bulk')) {
            beginWait();
        }
    }, true);

    document.body.addEventListener('click', function(e) {
        var btn = e.target.closest('.bulk-type-btn');
        if (!btn || !document.querySelector('.project-script')) return;

        e.preventDefault();
        e.stopPropagation();

        if (inFlight) return;

        var type = btn.getAttribute('data-bulk-type');
        var captured = snapshot;
        snapshot = null;

        // If mousedown missed (keyboard activation), capture now.
        if (!captured) {
            captured = captureTarget(null);
            if (captured && captured.mode === 'single') {
                beginWait();
            }
        }

        onTypeButton(type, captured);
    });

    // Keep toolbar highlight in sync when focusing a block (saved or create).
    document.addEventListener('focusin', function(e) {
        if (!e.target || e.target.name !== 'content') return;
        var row = e.target.closest('.block-row, tr[data-block-id], tr:not([data-block-id])');
        if (!row || row.classList.contains('project-script-select-row')) return;
        syncToolbar(row.getAttribute('data-block-type'));
    });

    // ⌘/Ctrl+1–7 → set element type on the active/selected block(s).
    // Intentionally works while typing in a block textarea.
    document.addEventListener('keydown', function(e) {
        if (!(e.metaKey || e.ctrlKey) || e.altKey || e.shiftKey) return;
        if (window.scriptyCanEditScript === false) return;
        if (!document.querySelector('.project-script')) return;
        if (!getActions()) return;

        var digit = null;
        if (e.key >= '1' && e.key <= '7') {
            digit = e.key;
        } else if (e.code && /^Numpad[1-7]$/.test(e.code)) {
            digit = e.code.slice(-1);
        }
        if (!digit) return;

        var type = typeForShortcutDigit(digit);
        if (!type) return;

        e.preventDefault();
        if (typeof window.scriptyApplyElementType === 'function') {
            window.scriptyApplyElementType(type, null);
        }
    });

    document.body.addEventListener('htmx:afterSwap', syncElementTypeShortcutLabels);
    document.body.addEventListener('htmx:afterSettle', syncElementTypeShortcutLabels);
    window.addEventListener('scripty:outline-mode-changed', syncElementTypeShortcutLabels);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', syncElementTypeShortcutLabels);
    } else {
        syncElementTypeShortcutLabels();
    }
})();
