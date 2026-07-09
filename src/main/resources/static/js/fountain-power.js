/**
 * Fountain screenplay power-user features:
 * - Tab / Shift+Tab element type cycling
 * - Smart next-element type on Enter
 * - Live Fountain syntax detection (force markers + heuristics)
 * - Parse multi-line Fountain/plain text into typed blocks (external paste)
 * - Character cue autocomplete from project cast
 * - Scene heading autocomplete from prior scenes
 * - Scene location autocomplete (reuse places from prior headings)
 * - Scene time-of-day autocomplete (DAY, NIGHT, …)
 * - Scene / section / synopsis / bookmark outline navigator
 * - Character list sidebar
 * - Location list sidebar
 */
(function() {
    'use strict';

    if (window._scriptyFountainPowerInit) return;
    window._scriptyFountainPowerInit = true;

    var OUTLINE_TABS = ['combined', 'scenes', 'bookmarks'];
    var OUTLINE_TAB_STORAGE = 'scripty-fountain-outline-tab';
    var OUTLINE_BOOKMARK_MARK =
        '<span class="fountain-outline-bookmark-mark" aria-hidden="true" title="Bookmarked">' +
        '<svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="currentColor" stroke-linecap="round" stroke-linejoin="round">' +
        '<path d="m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z"></path>' +
        '</svg></span>';

    var TAB_CYCLE = [
        'SCENE', 'ACTION', 'TEXT', 'CHARACTER', 'DIALOGUE', 'PARENTHETICAL',
        'TRANSITION', 'SHOT', 'DUAL_DIALOGUE', 'LYRICS', 'CENTERED',
        'SECTION', 'SYNOPSIS', 'NOTE', 'PAGE_BREAK'
    ];

    var SCENE_HEADING = /^(?:INT\.?|EXT\.?|EST\.?|INT\.?\/EXT\.?|I\/E\.?)\s+.+/i;
    var SCENE_PREFIX = /^(?:INT\.?|EXT\.?|EST\.?|INT\.?\/EXT\.?|I\/E\.?)\b/i;
    var SCENE_PREFIX_SUGGESTIONS = [
        { name: 'INT. ' },
        { name: 'EXT. ' },
        { name: 'EST. ' },
        { name: 'INT./EXT. ' },
        { name: 'I/E. ' }
    ];
    var SCENE_TIME_SUGGESTIONS = [
        { name: 'DAY' },
        { name: 'NIGHT' },
        { name: 'DAWN' },
        { name: 'DUSK' },
        { name: 'MORNING' },
        { name: 'AFTERNOON' },
        { name: 'EVENING' },
        { name: 'CONTINUOUS' },
        { name: 'LATER' },
        { name: 'MOMENTS LATER' },
        { name: 'SAME TIME' },
        { name: 'THE NEXT DAY' }
    ];
    var TRANSITION = /^[A-Z][A-Z0-9 ]+ TO:$/;
    var SHOT = /^(?:ANGLE ON|ANOTHER ANGLE|CLOSE ON|CLOSE UP|CLOSEUP|C\.U\.?|CU|POV|INSERT|BACK TO SCENE|BACK TO|TIGHT ON|WIDER(?: SHOT)?|TRACKING|CRANE|AERIAL|ESTABLISHING|FAVOR ON|REVERSE ANGLE)\b.*/i;

    var characterCache = { projectId: null, entries: [], loadedAt: 0 };
    var sceneCache = { projectId: null, entries: [], loadedAt: 0 };
    var autocompleteEl = null;
    var autocompleteIndex = -1;
    var autocompleteTextarea = null;
    var autocompleteKind = null; // 'character' | 'scene' (includes location + time-of-day)
    var outlineEl = null;
    var characterListEl = null;
    var locationListEl = null;

    function projectId() {
        if (typeof window.scriptyResolveProjectId === 'function') {
            return window.scriptyResolveProjectId() || '';
        }
        var params = new URLSearchParams(window.location.search);
        return params.get('id') || '';
    }

    function typeLabel(type) {
        return window.scriptyBlockTypeLabel
            ? window.scriptyBlockTypeLabel(type)
            : (type || 'Action');
    }

    function isBlockContentTextarea(el) {
        return !!el && el.tagName === 'TEXTAREA' && el.name === 'content' && !!el.closest('.block-content');
    }

    function findAnyBlockRow(el) {
        return el ? el.closest('.block-row, tr[data-block-id], tr:not([data-block-id])') : null;
    }

    function isCreateRow(row) {
        return !!row && !row.hasAttribute('data-block-id');
    }

    function rowType(row) {
        if (!row) return 'ACTION';
        return (row.getAttribute('data-block-type') || 'ACTION').toUpperCase();
    }

    function previousSavedRow(row) {
        if (!row) return null;
        var prev = row.previousElementSibling;
        while (prev) {
            if (prev.classList && prev.classList.contains('block-row') && prev.hasAttribute('data-block-id')) {
                return prev;
            }
            if (prev.tagName === 'TR' && prev.hasAttribute('data-block-id')) {
                return prev;
            }
            prev = prev.previousElementSibling;
        }
        return null;
    }

    /** After Character/Dual cue → Dialogue; otherwise default to Action. */
    function nextTypeAfter(type) {
        var upper = (type || 'ACTION').toUpperCase();
        if (upper === 'CHARACTER' || upper === 'DUAL_DIALOGUE') {
            return 'DIALOGUE';
        }
        return 'ACTION';
    }
    window.scriptyNextFountainType = nextTypeAfter;

    function cycleType(current, backward) {
        var upper = (current || 'ACTION').toUpperCase();
        var idx = TAB_CYCLE.indexOf(upper);
        if (idx < 0) idx = TAB_CYCLE.indexOf('ACTION');
        if (backward) {
            idx = (idx - 1 + TAB_CYCLE.length) % TAB_CYCLE.length;
        } else {
            idx = (idx + 1) % TAB_CYCLE.length;
        }
        return TAB_CYCLE[idx];
    }

    function setCreateRowType(row, type) {
        if (!row || !type) return;
        if (window.scriptyApplyBlockTypeClass) {
            var blockContent = row.querySelector('.block-content');
            window.scriptyApplyBlockTypeClass(blockContent, type);
        } else {
            row.setAttribute('data-block-type', type);
            var label = row.querySelector('.block-element-label');
            if (label) {
                label.setAttribute('data-block-type', type);
                label.textContent = typeLabel(type);
            }
        }
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
        if (window.scriptySyncElementTypeToolbar) {
            window.scriptySyncElementTypeToolbar(type);
        }
    }
    window.scriptySetCreateRowType = setCreateRowType;

    function applyTypeToActiveBlock(type) {
        if (!type) return;
        var textarea = document.activeElement;
        if (!isBlockContentTextarea(textarea)) return;
        var row = findAnyBlockRow(textarea);
        if (!row) return;

        if (isCreateRow(row)) {
            setCreateRowType(row, type);
            return;
        }

        var blockId = row.getAttribute('data-block-id');
        if (typeof window.scriptyApplyElementType === 'function') {
            window.scriptyApplyElementType(type, blockId);
            return;
        }

        var btn = document.querySelector('.bulk-type-btn[data-bulk-type="' + type + '"]');
        if (btn) {
            btn.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
            btn.click();
        }
    }

    /**
     * Detect Fountain element type from typed content.
     * Returns { type, content } when a conversion should apply, else null.
     */
    function detectFountain(raw) {
        if (raw == null) return null;
        var text = String(raw).replace(/\u00a0/g, '');
        var trimmed = text.trim();
        if (!trimmed) return null;

        // Multi-line: only force-marker prefixes on first line
        var firstLine = trimmed.split('\n')[0].trim();

        if (/^={3,}$/.test(trimmed)) {
            return { type: 'PAGE_BREAK', content: '===' };
        }
        if (trimmed.startsWith('[[') && trimmed.endsWith(']]')) {
            return { type: 'NOTE', content: trimmed.slice(2, -2).trim() };
        }
        if (firstLine.startsWith('#') && !firstLine.startsWith('##')) {
            return { type: 'SECTION', content: trimmed.replace(/^#+\s*/, '') };
        }
        if (firstLine.startsWith('#') && firstLine.match(/^#+/)) {
            return { type: 'SECTION', content: trimmed.replace(/^#+\s*/, '') };
        }
        if (firstLine.startsWith('=') && !firstLine.startsWith('==')) {
            return { type: 'SYNOPSIS', content: trimmed.replace(/^=+\s*/, '') };
        }
        if (firstLine.startsWith('~')) {
            return { type: 'LYRICS', content: trimmed.replace(/^~\s*/, '') };
        }
        if (firstLine.startsWith('.') && !firstLine.startsWith('..')) {
            return { type: 'SCENE', content: trimmed.replace(/^\.\s*/, '') };
        }
        if (firstLine.startsWith('@')) {
            var cue = firstLine.slice(1).trim().replace(/\s*\^\s*$/, '');
            var dual = /\^\s*$/.test(firstLine);
            return {
                type: dual ? 'DUAL_DIALOGUE' : 'CHARACTER',
                content: cue.toUpperCase()
            };
        }
        if (firstLine.startsWith('>') && firstLine.endsWith('<') && firstLine.length > 2) {
            return { type: 'CENTERED', content: firstLine.slice(1, -1).trim() };
        }
        if (firstLine.startsWith('>')) {
            return { type: 'TRANSITION', content: firstLine.slice(1).trim() };
        }
        if (SCENE_HEADING.test(firstLine)) {
            return { type: 'SCENE', content: firstLine };
        }
        if (TRANSITION.test(firstLine)) {
            return { type: 'TRANSITION', content: firstLine };
        }
        if (SHOT.test(firstLine)) {
            return { type: 'SHOT', content: firstLine };
        }
        if (/^\([^)]*\)$/.test(firstLine) || (firstLine.startsWith('(') && !firstLine.includes('\n'))) {
            var paren = firstLine.startsWith('(')
                ? (firstLine.endsWith(')') ? firstLine.slice(1, -1).trim() : firstLine.slice(1).trim())
                : firstLine;
            return { type: 'PARENTHETICAL', content: paren };
        }

        // Character cue heuristic: single ALL-CAPS line, short, not a scene/transition
        if (trimmed === firstLine && isCharacterCueLine(firstLine)) {
            var dualCue = /\^\s*$/.test(firstLine);
            var name = firstLine.replace(/^@/, '').replace(/\s*\^\s*$/, '').trim();
            return {
                type: dualCue ? 'DUAL_DIALOGUE' : 'CHARACTER',
                content: name
            };
        }

        return null;
    }
    window.scriptyDetectFountain = detectFountain;

    function isCharacterCueLine(line) {
        if (!line || line.length > 60) return false;
        if (/[.?!]$/.test(line)) return false;
        if (SCENE_HEADING.test(line) || TRANSITION.test(line) || SHOT.test(line)) return false;
        var core = line.replace(/^@/, '').replace(/\s*\^\s*$/, '').trim();
        if (!core) return false;
        // Allow parenthetical extensions: JOE (V.O.)
        var base = core.replace(/\s*\([^)]*\)\s*$/, '').trim();
        if (!/^[A-Z0-9][A-Z0-9 \-'.]*$/.test(base)) return false;
        if (base.split(/\s+/).length > 5) return false;
        // Must look intentionally cued (has letter and is uppercase-ish)
        if (!/[A-Z]/.test(base)) return false;
        return base === base.toUpperCase();
    }

    /**
     * Parse plain / Fountain text into typed blocks (mirrors server Fountain import).
     * Used when pasting text that did not come from Scripty's structured clipboard.
     */
    function parseFountainToBlocks(fountainText) {
        if (fountainText == null || fountainText === '') return [];
        var lines = String(fountainText).replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n');
        var blocks = [];
        var mode = 'ACTION';
        var pendingCharacter = null;
        var dialogueBuffer = [];
        var inBoneyard = false;

        function flushDialogue() {
            if (!dialogueBuffer.length) return;
            blocks.push({
                type: 'DIALOGUE',
                content: dialogueBuffer.join('\n').trim(),
                characterName: pendingCharacter || ''
            });
            dialogueBuffer = [];
        }

        function normalizeCharacterName(line) {
            return line.replace(/\^(\*)?/g, '').replace(/^@/, '').trim();
        }

        for (var i = 0; i < lines.length; i++) {
            var rawLine = lines[i];
            var trimmed = rawLine.trim();

            if (inBoneyard) {
                if (trimmed.indexOf('*/') !== -1) inBoneyard = false;
                continue;
            }
            if (trimmed.indexOf('/*') === 0) {
                if (trimmed.indexOf('*/') === -1) inBoneyard = true;
                continue;
            }

            if (trimmed.indexOf('[[') === 0 && trimmed.slice(-2) === ']]') {
                flushDialogue();
                mode = 'ACTION';
                pendingCharacter = null;
                blocks.push({
                    type: 'NOTE',
                    content: trimmed.slice(2, -2).trim(),
                    characterName: ''
                });
                continue;
            }

            if (!trimmed) {
                flushDialogue();
                mode = 'ACTION';
                pendingCharacter = null;
                continue;
            }

            if (/^={3,}$/.test(trimmed)) {
                flushDialogue();
                mode = 'ACTION';
                pendingCharacter = null;
                blocks.push({ type: 'PAGE_BREAK', content: '===', characterName: '' });
                continue;
            }

            if (trimmed.charAt(0) === '#') {
                flushDialogue();
                mode = 'ACTION';
                pendingCharacter = null;
                blocks.push({
                    type: 'SECTION',
                    content: trimmed.replace(/^#+/, '').trim(),
                    characterName: ''
                });
                continue;
            }

            if (trimmed.charAt(0) === '=' && trimmed.indexOf('==') !== 0) {
                flushDialogue();
                mode = 'ACTION';
                pendingCharacter = null;
                blocks.push({
                    type: 'SYNOPSIS',
                    content: trimmed.slice(1).trim(),
                    characterName: ''
                });
                continue;
            }

            if (trimmed.charAt(0) === '~') {
                flushDialogue();
                mode = 'ACTION';
                pendingCharacter = null;
                blocks.push({
                    type: 'LYRICS',
                    content: trimmed.slice(1).trim(),
                    characterName: ''
                });
                continue;
            }

            if (trimmed.charAt(0) === '.' && trimmed.indexOf('..') !== 0) {
                flushDialogue();
                mode = 'ACTION';
                pendingCharacter = null;
                blocks.push({
                    type: 'SCENE',
                    content: trimmed.slice(1).trim(),
                    characterName: ''
                });
                continue;
            }

            if (SCENE_HEADING.test(trimmed)) {
                flushDialogue();
                mode = 'ACTION';
                pendingCharacter = null;
                blocks.push({ type: 'SCENE', content: trimmed, characterName: '' });
                continue;
            }

            if (trimmed.charAt(0) === '>' && trimmed.slice(-1) === '<' && trimmed.length > 2) {
                flushDialogue();
                mode = 'ACTION';
                pendingCharacter = null;
                blocks.push({
                    type: 'CENTERED',
                    content: trimmed.slice(1, -1).trim(),
                    characterName: ''
                });
                continue;
            }

            if (trimmed.charAt(0) === '>') {
                flushDialogue();
                mode = 'ACTION';
                pendingCharacter = null;
                blocks.push({
                    type: 'TRANSITION',
                    content: trimmed.slice(1).trim(),
                    characterName: ''
                });
                continue;
            }

            if (TRANSITION.test(trimmed)) {
                flushDialogue();
                mode = 'ACTION';
                pendingCharacter = null;
                blocks.push({ type: 'TRANSITION', content: trimmed, characterName: '' });
                continue;
            }

            if (SHOT.test(trimmed)) {
                flushDialogue();
                mode = 'ACTION';
                pendingCharacter = null;
                blocks.push({ type: 'SHOT', content: trimmed, characterName: '' });
                continue;
            }

            if ((mode === 'CHARACTER' || mode === 'DIALOGUE') && trimmed.charAt(0) === '(') {
                flushDialogue();
                var parenContent = trimmed.slice(-1) === ')'
                    ? trimmed.slice(1, -1).trim()
                    : trimmed.slice(1).trim();
                blocks.push({ type: 'PARENTHETICAL', content: parenContent, characterName: '' });
                mode = 'DIALOGUE';
                continue;
            }

            if (mode === 'ACTION' && isCharacterCueLine(trimmed)) {
                flushDialogue();
                pendingCharacter = normalizeCharacterName(trimmed);
                var cueType = /\^\s*$/.test(trimmed) ? 'DUAL_DIALOGUE' : 'CHARACTER';
                blocks.push({
                    type: cueType,
                    content: pendingCharacter,
                    characterName: pendingCharacter
                });
                mode = 'CHARACTER';
                continue;
            }

            if (mode === 'CHARACTER' || mode === 'DIALOGUE') {
                dialogueBuffer.push(rawLine.replace(/\s+$/, ''));
                mode = 'DIALOGUE';
                continue;
            }

            flushDialogue();
            pendingCharacter = null;
            blocks.push({
                type: 'ACTION',
                content: trimmed.charAt(0) === '!' ? trimmed.slice(1) : trimmed,
                characterName: ''
            });
            mode = 'ACTION';
        }

        flushDialogue();
        return blocks;
    }
    window.scriptyParseFountainToBlocks = parseFountainToBlocks;

    function applyDetectionToTextarea(textarea, opts) {
        opts = opts || {};
        if (!isBlockContentTextarea(textarea)) return false;
        var row = findAnyBlockRow(textarea);
        if (!row) return false;

        var detected = detectFountain(textarea.value);
        if (!detected) return false;

        var current = rowType(row);
        // Don't fight an explicit non-ACTION type unless force marker or create row
        var forceMarker = /^[.>@~#=\[\]]|^={3,}$/.test(textarea.value.trim())
            || (textarea.value.trim().startsWith('[['));
        if (!opts.force && !isCreateRow(row) && current !== 'ACTION' && !forceMarker) {
            return false;
        }

        if (detected.content !== textarea.value) {
            var start = textarea.selectionStart;
            textarea.value = detected.content;
            var pos = Math.min(start, detected.content.length);
            try {
                textarea.setSelectionRange(pos, pos);
            } catch (err) { /* ignore */ }
            if (typeof window.scriptyGrowTextarea === 'function') {
                window.scriptyGrowTextarea(textarea);
            }
        }

        if (isCreateRow(row)) {
            setCreateRowType(row, detected.type);
            return true;
        }

        if (current !== detected.type) {
            applyTypeToActiveBlock(detected.type);
        }
        return true;
    }
    window.scriptyApplyFountainDetection = applyDetectionToTextarea;

    function prepareCreateRow(createRow, fromType) {
        if (!createRow) return;
        var next = nextTypeAfter(fromType || 'ACTION');
        setCreateRowType(createRow, next);
    }
    window.scriptyPrepareCreateRowType = prepareCreateRow;

    // --- Character autocomplete ---

    function characterEntries() {
        return characterCache.entries || [];
    }

    function isAutocompleteOpen() {
        return !!(autocompleteEl && !autocompleteEl.hidden);
    }
    window.scriptyIsCharacterAutocompleteOpen = isAutocompleteOpen;

    function extractCharacterList(data) {
        if (!data) return [];
        if (Array.isArray(data)) return data;
        if (data._embedded) {
            return data._embedded.personResourceList
                || data._embedded.persons
                || data._embedded.characterViewModels
                || Object.values(data._embedded)[0]
                || [];
        }
        return [];
    }

    function entryFromItem(item) {
        if (!item) return null;
        var body = item.content && typeof item.content === 'object' ? item.content : item;
        var name = body.name || body.fullName;
        if (!name) return null;
        var id = body.id != null ? body.id : item.id;
        return { id: id != null ? id : null, name: String(name).trim() };
    }

    function upsertEntry(entries, entry) {
        if (!entry || !entry.name) return;
        var upper = entry.name.toUpperCase();
        for (var i = 0; i < entries.length; i++) {
            if (entries[i].name.toUpperCase() === upper) {
                if (entries[i].id == null && entry.id != null) {
                    entries[i].id = entry.id;
                }
                return;
            }
        }
        entries.push(entry);
    }

    function loadCharacters(force) {
        var pid = projectId();
        if (!pid) return Promise.resolve([]);
        var now = Date.now();
        if (!force && characterCache.projectId === pid && now - characterCache.loadedAt < 60000) {
            return Promise.resolve(characterEntries());
        }
        return fetch('/api/character?projectId=' + encodeURIComponent(pid), {
            credentials: 'same-origin',
            headers: { Accept: 'application/hal+json, application/json' }
        }).then(function(r) {
            if (!r.ok) throw new Error('character list failed');
            return r.json();
        }).then(function(data) {
            var entries = [];
            extractCharacterList(data).forEach(function(item) {
                upsertEntry(entries, entryFromItem(item));
            });
            // Also harvest character cues already in the script
            document.querySelectorAll('.block-row[data-block-type="CHARACTER"], .block-row[data-block-type="DUAL_DIALOGUE"]').forEach(function(row) {
                var text = row.querySelector('.script-block-text, textarea[name="content"]');
                var cue = text ? (text.value != null ? text.value : text.textContent) : '';
                cue = String(cue || '').replace(/\s*\^\s*$/, '').trim();
                if (cue) upsertEntry(entries, { id: null, name: cue });
            });
            entries.sort(function(a, b) {
                return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
            });
            characterCache = { projectId: pid, entries: entries, loadedAt: now };
            return entries;
        }).catch(function() {
            return characterEntries();
        });
    }

    function ensureAutocompleteEl() {
        if (autocompleteEl) return autocompleteEl;
        autocompleteEl = document.createElement('ul');
        autocompleteEl.id = 'fountain-char-autocomplete';
        autocompleteEl.className = 'fountain-char-autocomplete hide-in-reader-view';
        autocompleteEl.setAttribute('role', 'listbox');
        autocompleteEl.hidden = true;
        document.body.appendChild(autocompleteEl);
        return autocompleteEl;
    }

    function hideAutocomplete() {
        if (!autocompleteEl) return;
        autocompleteEl.hidden = true;
        autocompleteEl.innerHTML = '';
        autocompleteIndex = -1;
        autocompleteTextarea = null;
        autocompleteKind = null;
    }

    function showAutocomplete(textarea, matches, kind) {
        var el = ensureAutocompleteEl();
        if (!matches.length) {
            hideAutocomplete();
            return;
        }
        autocompleteTextarea = textarea;
        autocompleteKind = kind || 'character';
        el.innerHTML = matches.map(function(entry, i) {
            return '<li role="option" data-index="' + i + '"' +
                (entry.id != null ? ' data-person-id="' + escapeHtml(String(entry.id)) + '"' : '') +
                ' data-name="' + escapeHtml(entry.name) + '"' +
                (i === autocompleteIndex ? ' aria-selected="true" class="is-active"' : '') +
                '>' + escapeHtml(entry.name) + '</li>';
        }).join('');
        var rect = textarea.getBoundingClientRect();
        el.style.left = Math.round(rect.left + window.scrollX) + 'px';
        el.style.top = Math.round(rect.bottom + window.scrollY + 4) + 'px';
        el.style.minWidth = Math.max(160, Math.round(rect.width * 0.6)) + 'px';
        el.hidden = false;
    }

    function escapeHtml(s) {
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function filterCharacters(query, entries) {
        var q = (query || '').trim().toUpperCase();
        if (!q) return entries.slice(0, 8);
        var prefix = [];
        var contains = [];
        entries.forEach(function(entry) {
            var upper = entry.name.toUpperCase();
            if (upper.indexOf(q) === 0) prefix.push(entry);
            else if (upper.indexOf(q) !== -1) contains.push(entry);
        });
        return prefix.concat(contains).slice(0, 8);
    }

    function maybeShowCharacterAutocomplete(textarea) {
        var row = findAnyBlockRow(textarea);
        if (!row) return false;
        var type = rowType(row);
        if (type !== 'CHARACTER' && type !== 'DUAL_DIALOGUE' && type !== 'ACTION') return false;

        var value = textarea.value;
        // Only autocomplete single-line cues
        if (value.indexOf('\n') !== -1) {
            hideAutocomplete();
            return true;
        }
        var forcedCue = value.trim().charAt(0) === '@';
        var query = value.replace(/^@/, '').replace(/\s*\^\s*$/, '').trim();
        // ACTION: require @ force marker or at least 2 characters of a cue
        if (type === 'ACTION' && !forcedCue && query.length < 2) {
            hideAutocomplete();
            return false;
        }
        // Prefer scene suggestions when ACTION looks like a scene heading stub
        if (type === 'ACTION' && !forcedCue && looksLikeSceneTyping(value, type)) {
            return false;
        }

        loadCharacters(false).then(function(entries) {
            if (document.activeElement !== textarea) return;
            if (autocompleteKind === 'scene' && isAutocompleteOpen()) return;
            var matches = filterCharacters(query, entries);
            // Don't suggest exact current value alone
            if (matches.length === 1 && matches[0].name.toUpperCase() === query.toUpperCase()) {
                hideAutocomplete();
                return;
            }
            autocompleteIndex = matches.length ? 0 : -1;
            showAutocomplete(textarea, matches, 'character');
        });
        return true;
    }

    function maybeShowAutocomplete(textarea) {
        var row = findAnyBlockRow(textarea);
        if (!row) {
            hideAutocomplete();
            return;
        }
        var type = rowType(row);
        var value = textarea.value || '';
        var forcedCue = value.trim().charAt(0) === '@';

        if (type === 'SCENE' || (!forcedCue && looksLikeSceneTyping(value, type))) {
            if (maybeShowSceneAutocomplete(textarea)) return;
        }
        if (type === 'CHARACTER' || type === 'DUAL_DIALOGUE' || type === 'ACTION') {
            if (maybeShowCharacterAutocomplete(textarea)) return;
        }
        hideAutocomplete();
    }

    function sceneEntries() {
        return sceneCache.entries || [];
    }

    function harvestSceneHeadings() {
        var entries = [];
        document.querySelectorAll('.block-row[data-block-type="SCENE"]').forEach(function(row) {
            var text = row.querySelector('.script-block-text, textarea[name="content"]');
            var heading = text ? (text.value != null ? text.value : text.textContent) : '';
            heading = String(heading || '').replace(/^\.\s*/, '').replace(/\u00a0/g, ' ').trim();
            if (heading) upsertEntry(entries, { id: null, name: heading });
        });
        entries.sort(function(a, b) {
            return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
        });
        return entries;
    }

    function loadScenes(force) {
        var pid = projectId();
        var now = Date.now();
        if (!force && sceneCache.projectId === pid && now - sceneCache.loadedAt < 15000) {
            return sceneEntries();
        }
        var entries = harvestSceneHeadings();
        sceneCache = { projectId: pid, entries: entries, loadedAt: now };
        return entries;
    }

    function parseSceneTimeContext(query) {
        var raw = String(query || '').replace(/\u00a0/g, ' ');
        var m = raw.match(/^(.*)\s+-\s*(.*)$/);
        if (!m) return null;
        var base = m[1].replace(/\s+$/, '');
        if (!SCENE_PREFIX.test(base)) return null;
        // Need a location after the INT./EXT. prefix before offering times
        if (!/^(?:INT\.?\/EXT\.?|I\/E\.?|INT\.?|EXT\.?|EST\.?)\s+\S/i.test(base)) return null;
        return { base: base, timeQuery: m[2] || '' };
    }

    function parseSceneLocationContext(query) {
        var raw = String(query || '').replace(/\u00a0/g, ' ');
        // Time-of-day mode owns the query once " - " is present
        if (parseSceneTimeContext(raw)) return null;
        var m = raw.match(/^(INT\.?\/EXT\.?|I\/E\.?|INT\.?|EXT\.?|EST\.?)\s+(.*)$/i);
        if (!m) return null;
        var prefix = raw.match(/^(INT\.?\/EXT\.?|I\/E\.?|INT\.?|EXT\.?|EST\.?)\s+/i);
        if (!prefix) return null;
        return { prefix: prefix[0], locationQuery: m[2] || '' };
    }

    function extractSceneLocation(heading) {
        var raw = String(heading || '').replace(/\u00a0/g, ' ').trim();
        if (!raw) return null;
        var withoutTime = raw.replace(/\s+-\s+.+$/, '').trim();
        var m = withoutTime.match(/^(?:INT\.?\/EXT\.?|I\/E\.?|INT\.?|EXT\.?|EST\.?)\s+(.+)$/i);
        if (!m) return null;
        var loc = m[1].trim();
        return loc || null;
    }

    function harvestSceneLocations(entries) {
        var locations = [];
        (entries || []).forEach(function(entry) {
            var loc = extractSceneLocation(entry.name);
            if (loc) upsertEntry(locations, { name: loc });
        });
        locations.sort(function(a, b) {
            return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
        });
        return locations;
    }

    function harvestSceneTimes(entries) {
        var times = SCENE_TIME_SUGGESTIONS.slice();
        (entries || []).forEach(function(entry) {
            var m = String(entry.name || '').match(/\s+-\s+(.+)$/);
            if (m && m[1].trim()) upsertEntry(times, { name: m[1].trim() });
        });
        return times;
    }

    function filterSceneTimes(query, times) {
        var q = (query || '').trim().toUpperCase();
        if (!q) return times.slice(0, 8);
        // Prefix-only: "NI" should hit NIGHT, not MORNING/EVENING
        var matches = [];
        times.forEach(function(entry) {
            if (entry.name.toUpperCase().indexOf(q) === 0) matches.push(entry);
        });
        return matches.slice(0, 8);
    }

    function buildSceneTimeSuggestions(ctx, entries) {
        if (!ctx) return [];
        var times = filterSceneTimes(ctx.timeQuery, harvestSceneTimes(entries));
        return times.map(function(t) {
            return { name: ctx.base + ' - ' + t.name };
        });
    }

    function buildSceneLocationSuggestions(ctx, entries) {
        if (!ctx) return [];
        var matches = filterCharacters(ctx.locationQuery, harvestSceneLocations(entries));
        return matches.map(function(loc) {
            return { name: ctx.prefix + loc.name };
        });
    }

    function filterScenes(query, entries) {
        var q = (query || '').trim().toUpperCase();
        var timeCtx = parseSceneTimeContext(query);
        var locationCtx = parseSceneLocationContext(query);
        var prefixMatches = filterCharacters(q, SCENE_PREFIX_SUGGESTIONS);
        // Drop prefix suggestions once the query already looks like a full heading
        // or the writer is filling in location / time-of-day
        if (timeCtx || locationCtx || SCENE_HEADING.test(query.trim()) || (q.length > 4 && /\s/.test(q))) {
            prefixMatches = [];
        }
        var timeMatches = buildSceneTimeSuggestions(timeCtx, entries);
        var locationMatches = buildSceneLocationSuggestions(locationCtx, entries);
        var sceneMatches = filterCharacters(q, entries);
        var combined = [];
        timeMatches.concat(locationMatches).concat(prefixMatches).concat(sceneMatches).forEach(function(entry) {
            upsertEntry(combined, entry);
        });
        return combined.slice(0, 8);
    }

    function looksLikeSceneTyping(value, type) {
        var trimmed = (value || '').trim();
        if (!trimmed) return type === 'SCENE';
        if (trimmed.charAt(0) === '.') return true;
        if (type === 'SCENE') return true;
        if (SCENE_PREFIX.test(trimmed)) return true;
        // Short INT/EXT stubs while still on ACTION (before live detect flips type)
        if (/^(?:I|IN|INT|INT\.|E|EX|EXT|EXT\.|ES|EST|EST\.|I\/|I\/E|I\/E\.|INT\.?\/|INT\.?\/E|INT\.?\/EX|INT\.?\/EXT|INT\.?\/EXT\.?)$/i.test(trimmed)) {
            return true;
        }
        return false;
    }

    function maybeShowSceneAutocomplete(textarea) {
        var row = findAnyBlockRow(textarea);
        if (!row) return false;
        var type = rowType(row);
        if (type !== 'SCENE' && type !== 'ACTION') return false;

        var value = textarea.value;
        if (value.indexOf('\n') !== -1) {
            hideAutocomplete();
            return true;
        }
        if (!looksLikeSceneTyping(value, type)) return false;

        var query = value.replace(/^\.\s*/, '').replace(/\u00a0/g, ' ');
        var entries = loadScenes(false);
        var matches = filterScenes(query, entries);
        var q = query.trim();
        if (matches.length === 1 && matches[0].name.toUpperCase() === q.toUpperCase()) {
            hideAutocomplete();
            return true;
        }
        // ACTION with only a short stub and no prior scenes: still show INT./EXT. prefixes
        if (!matches.length) {
            hideAutocomplete();
            return type === 'SCENE';
        }
        autocompleteIndex = matches.length ? 0 : -1;
        showAutocomplete(textarea, matches, 'scene');
        return true;
    }

    function setBlockPersonId(textarea, personId) {
        if (personId == null || personId === '') return;
        var form = textarea.closest('form');
        if (!form) return;
        var input = form.querySelector('input[name="personId"]');
        if (!input) {
            input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'personId';
            form.appendChild(input);
        }
        input.value = String(personId);
    }

    function acceptAutocomplete(textarea, name, personId) {
        if (!name) return;
        var kind = autocompleteKind;
        if (kind === 'scene') {
            hideAutocomplete();
            textarea.value = name;
            var row = findAnyBlockRow(textarea);
            if (row && (isCreateRow(row) || rowType(row) === 'ACTION' || rowType(row) === 'SCENE')) {
                if (isCreateRow(row) || rowType(row) === 'ACTION') {
                    setCreateRowType(row, 'SCENE');
                }
                if (!isCreateRow(row) && rowType(row) !== 'SCENE') {
                    applyTypeToActiveBlock('SCENE');
                }
            }
            sceneCache.loadedAt = 0;
            if (typeof window.scriptyGrowTextarea === 'function') {
                window.scriptyGrowTextarea(textarea);
            }
            try {
                textarea.focus({ preventScroll: true });
                textarea.setSelectionRange(name.length, name.length);
            } catch (err) { /* ignore */ }
            // After a location pick (heading without time), offer time-of-day next
            if (SCENE_HEADING.test(name.trim()) && !/\s+-\s*\S/.test(name)) {
                var withTimeSep = name.replace(/\s*$/, '') + ' - ';
                textarea.value = withTimeSep;
                try {
                    textarea.setSelectionRange(withTimeSep.length, withTimeSep.length);
                } catch (err2) { /* ignore */ }
                if (typeof window.scriptyGrowTextarea === 'function') {
                    window.scriptyGrowTextarea(textarea);
                }
                maybeShowSceneAutocomplete(textarea);
                return;
            }
            // After a prefix stub (e.g. "INT. "), keep suggesting locations / prior scenes
            if (/\s$/.test(name) && !SCENE_HEADING.test(name.trim())) {
                maybeShowSceneAutocomplete(textarea);
            }
            return;
        }

        var dual = /\^\s*$/.test(textarea.value);
        textarea.value = dual ? name + ' ^' : name;
        hideAutocomplete();
        setBlockPersonId(textarea, personId);
        var row = findAnyBlockRow(textarea);
        if (row && (isCreateRow(row) || rowType(row) === 'ACTION' || rowType(row) === 'CHARACTER' || rowType(row) === 'DUAL_DIALOGUE')) {
            var nextType = dual ? 'DUAL_DIALOGUE' : 'CHARACTER';
            if (isCreateRow(row) || rowType(row) === 'ACTION') {
                setCreateRowType(row, nextType);
            }
            if (!isCreateRow(row) && rowType(row) !== nextType) {
                applyTypeToActiveBlock(nextType);
            }
        }
        if (typeof window.scriptyGrowTextarea === 'function') {
            window.scriptyGrowTextarea(textarea);
        }
        try {
            textarea.focus({ preventScroll: true });
            textarea.setSelectionRange(textarea.value.length, textarea.value.length);
        } catch (err) { /* ignore */ }
    }

    // --- Outline navigator ---

    var outlineActiveTab = 'combined';

    function blockRowText(row) {
        var textEl = row.querySelector('.script-block-text, textarea[name="content"]');
        var text = textEl
            ? (textEl.value != null ? textEl.value : textEl.textContent)
            : '';
        text = String(text || '').replace(/\u00a0/g, ' ').trim() || '(Untitled)';
        return text.length > 80 ? text.slice(0, 77) + '…' : text;
    }

    function normalizeOutlineTab(tab) {
        return OUTLINE_TABS.indexOf(tab) >= 0 ? tab : 'combined';
    }

    function readOutlineTab() {
        try {
            return normalizeOutlineTab(localStorage.getItem(OUTLINE_TAB_STORAGE));
        } catch (err) {
            return 'combined';
        }
    }

    function persistOutlineTab(tab) {
        try {
            localStorage.setItem(OUTLINE_TAB_STORAGE, tab);
        } catch (err) { /* ignore */ }
    }

    function isOutlineStructuralType(type) {
        return type === 'SCENE' || type === 'SECTION' || type === 'SYNOPSIS';
    }

    function collectOutlineItems() {
        var items = [];
        document.querySelectorAll('.project-script .block-row[data-block-id]').forEach(function(row) {
            var type = rowType(row);
            if (!isOutlineStructuralType(type)) return;
            items.push({
                id: row.getAttribute('data-block-id'),
                type: type,
                text: blockRowText(row),
                scene: true,
                bookmarked: row.getAttribute('data-bookmarked') === 'true'
            });
        });
        return items;
    }

    function collectBookmarkItems() {
        var items = [];
        document.querySelectorAll('.project-script .block-row[data-block-id][data-bookmarked="true"]').forEach(function(row) {
            var type = rowType(row) || 'ACTION';
            items.push({
                id: row.getAttribute('data-block-id'),
                type: type,
                text: blockRowText(row),
                scene: isOutlineStructuralType(type),
                bookmarked: true
            });
        });
        return items;
    }

    function collectCombinedItems() {
        var items = [];
        document.querySelectorAll('.project-script .block-row[data-block-id]').forEach(function(row) {
            var type = rowType(row) || 'ACTION';
            var isScene = isOutlineStructuralType(type);
            var bookmarked = row.getAttribute('data-bookmarked') === 'true';
            if (!isScene && !bookmarked) return;
            items.push({
                id: row.getAttribute('data-block-id'),
                type: type,
                text: blockRowText(row),
                scene: isScene,
                bookmarked: bookmarked
            });
        });
        return items;
    }

    function outlineItemHtml(item, sceneNum) {
        var classes = ['fountain-outline-item'];
        if (item.scene) {
            classes.push('fountain-outline-item--' + String(item.type || '').toLowerCase());
        } else {
            classes.push('fountain-outline-item--bookmark');
        }
        if (item.bookmarked) classes.push('is-bookmarked');

        var num = '';
        if (item.scene && item.type === 'SCENE' && sceneNum != null) {
            num = sceneNum + '. ';
        }

        var typeHint = '';
        if (!item.scene || item.type === 'SECTION' || item.type === 'SYNOPSIS') {
            typeHint = '<span class="fountain-outline-type">' +
                escapeHtml(typeLabel(item.type)) + '</span>';
        }

        var mark = item.bookmarked ? OUTLINE_BOOKMARK_MARK : '';

        return '<li class="' + classes.join(' ') + '">' +
            '<a href="#block-' + escapeHtml(item.id) + '" data-outline-block-id="' +
            escapeHtml(item.id) + '">' +
            '<span class="fountain-outline-num">' + escapeHtml(num) + '</span>' +
            '<span class="fountain-outline-text">' + escapeHtml(item.text) + '</span>' +
            typeHint +
            mark +
            '</a></li>';
    }

    function renderOutlineItems(items, emptyMessage) {
        var list = outlineEl.querySelector('.fountain-outline-list');
        var empty = outlineEl.querySelector('.fountain-outline-empty');
        if (!items.length) {
            list.innerHTML = '';
            empty.textContent = emptyMessage;
            empty.hidden = false;
            return;
        }
        empty.hidden = true;
        var sceneCount = 0;
        list.innerHTML = items.map(function(item) {
            var sceneNum = null;
            if (item.scene && item.type === 'SCENE') {
                sceneCount += 1;
                sceneNum = sceneCount;
            }
            return outlineItemHtml(item, sceneNum);
        }).join('');
    }

    function syncOutlineTabs() {
        if (!outlineEl) return;
        outlineEl.querySelectorAll('[data-outline-tab]').forEach(function(btn) {
            var active = btn.getAttribute('data-outline-tab') === outlineActiveTab;
            btn.classList.toggle('is-active', active);
            btn.setAttribute('aria-selected', active ? 'true' : 'false');
        });
    }

    function setOutlineTab(tab) {
        outlineActiveTab = normalizeOutlineTab(tab);
        persistOutlineTab(outlineActiveTab);
        syncOutlineTabs();
        refreshOutline();
    }

    function ensureOutline() {
        if (outlineEl) return outlineEl;
        outlineActiveTab = readOutlineTab();
        outlineEl = document.createElement('aside');
        outlineEl.id = 'fountain-outline';
        outlineEl.className = 'fountain-outline hide-in-reader-view sidebar menu';
        outlineEl.setAttribute('aria-label', 'Script outline');
        outlineEl.innerHTML =
            '<div class="fountain-outline-header">' +
            '<strong>Outline</strong>' +
            '<button type="button" class="fountain-outline-close" aria-label="Close outline" title="Close outline">×</button>' +
            '</div>' +
            '<div class="fountain-outline-tabs" role="tablist" aria-label="Outline views">' +
            '<button type="button" class="fountain-outline-tab" role="tab" data-outline-tab="combined" aria-selected="true">Combined</button>' +
            '<button type="button" class="fountain-outline-tab" role="tab" data-outline-tab="scenes" aria-selected="false">Scenes</button>' +
            '<button type="button" class="fountain-outline-tab" role="tab" data-outline-tab="bookmarks" aria-selected="false">Bookmarks</button>' +
            '</div>' +
            '<ol class="fountain-outline-list"></ol>' +
            '<p class="fountain-outline-empty muted">No scenes, sections, synopses, or bookmarks yet.</p>';
        document.body.appendChild(outlineEl);

        outlineEl.querySelector('.fountain-outline-close').addEventListener('click', function() {
            setOutlineOpen(false);
        });
        outlineEl.addEventListener('click', function(e) {
            var tabBtn = e.target.closest('[data-outline-tab]');
            if (tabBtn && outlineEl.contains(tabBtn)) {
                e.preventDefault();
                setOutlineTab(tabBtn.getAttribute('data-outline-tab'));
                return;
            }
            var link = e.target.closest('[data-outline-block-id]');
            if (!link) return;
            e.preventDefault();
            var id = link.getAttribute('data-outline-block-id');
            var row = document.querySelector('.block-row[data-block-id="' + id + '"]');
            if (!row) return;
            row.scrollIntoView({ behavior: 'smooth', block: 'center' });
            row.classList.add('fountain-outline-flash');
            setTimeout(function() {
                row.classList.remove('fountain-outline-flash');
            }, 1200);
            var content = row.querySelector('.block-content');
            if (content && !window.scriptyBlockEditLocked) {
                content.click();
            }
        });
        syncOutlineTabs();
        return outlineEl;
    }

    function refreshOutline() {
        if (!outlineEl || outlineEl.hidden) return;
        var tab = normalizeOutlineTab(outlineActiveTab);
        if (tab === 'bookmarks') {
            renderOutlineItems(collectBookmarkItems(), 'No bookmarks yet.');
            return;
        }
        if (tab === 'scenes') {
            renderOutlineItems(collectOutlineItems(), 'No scenes, sections, or synopses yet.');
            return;
        }
        renderOutlineItems(collectCombinedItems(), 'No scenes, sections, synopses, or bookmarks yet.');
    }
    window.scriptyRefreshFountainOutline = refreshOutline;

    function syncListsToolbarActive() {
        var listsBtn = document.querySelector('#project-lists-dropdown .lists-toolbar-btn');
        if (!listsBtn) return;
        var anyOpen =
            (outlineEl && !outlineEl.hidden) ||
            (characterListEl && !characterListEl.hidden) ||
            (locationListEl && !locationListEl.hidden);
        listsBtn.classList.toggle('is-active', !!anyOpen);
        listsBtn.setAttribute('aria-pressed', anyOpen ? 'true' : 'false');
    }

    function setOutlineOpen(open) {
        var el = ensureOutline();
        el.hidden = !open;
        document.documentElement.classList.toggle('fountain-outline-open', open);
        var btn = document.getElementById('nav-outline-toggle');
        if (btn) {
            btn.setAttribute('aria-pressed', open ? 'true' : 'false');
            btn.classList.toggle('is-active', open);
        }
        try {
            localStorage.setItem('scripty-fountain-outline', open ? 'true' : 'false');
        } catch (err) { /* ignore */ }
        if (open) {
            if (characterListEl && !characterListEl.hidden) setCharacterListOpen(false);
            if (locationListEl && !locationListEl.hidden) setLocationListOpen(false);
            syncOutlineTabs();
            refreshOutline();
        }
        syncListsToolbarActive();
        // Outline padding shifts script layout; keep the test caret aligned.
        requestAnimationFrame(function() {
            requestAnimationFrame(function() {
                if (typeof window.scriptyRepositionBlockCaretPreview === 'function') {
                    window.scriptyRepositionBlockCaretPreview();
                }
            });
        });
    }

    function toggleOutline() {
        var el = ensureOutline();
        setOutlineOpen(!!el.hidden);
    }
    window.scriptyToggleFountainOutline = toggleOutline;

    // --- Character list sidebar ---

    function ensureCharacterList() {
        if (characterListEl) return characterListEl;
        characterListEl = document.createElement('aside');
        characterListEl.id = 'fountain-character-list';
        characterListEl.className = 'fountain-character-list hide-in-reader-view sidebar menu';
        characterListEl.setAttribute('aria-label', 'Character list');
        characterListEl.innerHTML =
            '<div class="fountain-character-list-header">' +
            '<strong>Characters</strong>' +
            '<button type="button" class="fountain-character-list-close" aria-label="Close character list" title="Close character list">×</button>' +
            '</div>' +
            '<ol class="fountain-character-list-items"></ol>' +
            '<p class="fountain-character-list-empty muted">No characters yet.</p>' +
            '<p class="fountain-character-list-footer">' +
            '<a class="fountain-character-list-manage" href="#">Manage characters</a>' +
            '</p>';
        document.body.appendChild(characterListEl);

        characterListEl.querySelector('.fountain-character-list-close').addEventListener('click', function() {
            setCharacterListOpen(false);
        });
        return characterListEl;
    }

    function characterListItemHtml(entry) {
        var name = escapeHtml(entry.name);
        if (entry.id != null) {
            return '<li class="fountain-character-list-item">' +
                '<a href="/character/show?id=' + encodeURIComponent(String(entry.id)) + '">' +
                '<span class="fountain-character-list-name">' + name + '</span>' +
                '</a></li>';
        }
        return '<li class="fountain-character-list-item fountain-character-list-item--cue">' +
            '<span class="fountain-character-list-name">' + name + '</span>' +
            '</li>';
    }

    function refreshCharacterList() {
        if (!characterListEl || characterListEl.hidden) return;
        var list = characterListEl.querySelector('.fountain-character-list-items');
        var empty = characterListEl.querySelector('.fountain-character-list-empty');
        var manage = characterListEl.querySelector('.fountain-character-list-manage');
        var pid = projectId();
        if (manage) {
            manage.href = pid
                ? '/character/list?projectId=' + encodeURIComponent(pid)
                : '/character/list';
        }
        loadCharacters(true).then(function(entries) {
            if (!characterListEl || characterListEl.hidden) return;
            if (!entries.length) {
                list.innerHTML = '';
                empty.hidden = false;
                return;
            }
            empty.hidden = true;
            list.innerHTML = entries.map(characterListItemHtml).join('');
        });
    }
    window.scriptyRefreshFountainCharacterList = refreshCharacterList;

    function setCharacterListOpen(open) {
        var el = ensureCharacterList();
        el.hidden = !open;
        document.documentElement.classList.toggle('fountain-character-list-open', open);
        var btn = document.getElementById('nav-character-list-toggle');
        if (btn) {
            btn.setAttribute('aria-pressed', open ? 'true' : 'false');
            btn.classList.toggle('is-active', open);
        }
        try {
            localStorage.setItem('scripty-fountain-character-list', open ? 'true' : 'false');
        } catch (err) { /* ignore */ }
        if (open) {
            // Avoid stacking both side panels on the right.
            if (outlineEl && !outlineEl.hidden) setOutlineOpen(false);
            if (locationListEl && !locationListEl.hidden) setLocationListOpen(false);
            refreshCharacterList();
        }
        syncListsToolbarActive();
        requestAnimationFrame(function() {
            requestAnimationFrame(function() {
                if (typeof window.scriptyRepositionBlockCaretPreview === 'function') {
                    window.scriptyRepositionBlockCaretPreview();
                }
            });
        });
    }

    function toggleCharacterList() {
        var el = ensureCharacterList();
        setCharacterListOpen(!!el.hidden);
    }
    window.scriptyToggleFountainCharacterList = toggleCharacterList;

    // --- Location list sidebar ---

    function collectLocationItems() {
        var locations = [];
        document.querySelectorAll('.project-script .block-row[data-block-type="SCENE"][data-block-id]').forEach(function(row) {
            var textEl = row.querySelector('.script-block-text, textarea[name="content"]');
            var heading = textEl
                ? (textEl.value != null ? textEl.value : textEl.textContent)
                : '';
            heading = String(heading || '').replace(/^\.\s*/, '').replace(/\u00a0/g, ' ').trim();
            var loc = extractSceneLocation(heading);
            if (!loc) return;
            var upper = loc.toUpperCase();
            for (var i = 0; i < locations.length; i++) {
                if (locations[i].name.toUpperCase() === upper) {
                    if (!locations[i].id) {
                        locations[i].id = row.getAttribute('data-block-id');
                    }
                    locations[i].count += 1;
                    return;
                }
            }
            locations.push({
                name: loc,
                id: row.getAttribute('data-block-id'),
                count: 1
            });
        });
        locations.sort(function(a, b) {
            return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
        });
        return locations;
    }

    function ensureLocationList() {
        if (locationListEl) return locationListEl;
        locationListEl = document.createElement('aside');
        locationListEl.id = 'fountain-location-list';
        locationListEl.className = 'fountain-location-list hide-in-reader-view sidebar menu';
        locationListEl.setAttribute('aria-label', 'Location list');
        locationListEl.innerHTML =
            '<div class="fountain-location-list-header">' +
            '<strong>Locations</strong>' +
            '<button type="button" class="fountain-location-list-close" aria-label="Close location list" title="Close location list">×</button>' +
            '</div>' +
            '<ol class="fountain-location-list-items"></ol>' +
            '<p class="fountain-location-list-empty muted">No locations yet.</p>';
        document.body.appendChild(locationListEl);

        locationListEl.querySelector('.fountain-location-list-close').addEventListener('click', function() {
            setLocationListOpen(false);
        });
        locationListEl.addEventListener('click', function(e) {
            var link = e.target.closest('[data-location-block-id]');
            if (!link || !locationListEl.contains(link)) return;
            e.preventDefault();
            var id = link.getAttribute('data-location-block-id');
            var row = document.querySelector('.block-row[data-block-id="' + id + '"]');
            if (!row) return;
            row.scrollIntoView({ behavior: 'smooth', block: 'center' });
            row.classList.add('fountain-outline-flash');
            setTimeout(function() {
                row.classList.remove('fountain-outline-flash');
            }, 1200);
            var content = row.querySelector('.block-content');
            if (content && !window.scriptyBlockEditLocked) {
                content.click();
            }
        });
        return locationListEl;
    }

    function locationListItemHtml(entry) {
        var name = escapeHtml(entry.name);
        var count = entry.count > 1
            ? '<span class="fountain-location-list-count">' + entry.count + '</span>'
            : '';
        if (entry.id != null) {
            return '<li class="fountain-location-list-item">' +
                '<a href="#block-' + escapeHtml(String(entry.id)) + '" data-location-block-id="' +
                escapeHtml(String(entry.id)) + '">' +
                '<span class="fountain-location-list-name">' + name + '</span>' +
                count +
                '</a></li>';
        }
        return '<li class="fountain-location-list-item">' +
            '<span class="fountain-location-list-name">' + name + '</span>' +
            count +
            '</li>';
    }

    function refreshLocationList() {
        if (!locationListEl || locationListEl.hidden) return;
        var list = locationListEl.querySelector('.fountain-location-list-items');
        var empty = locationListEl.querySelector('.fountain-location-list-empty');
        var entries = collectLocationItems();
        if (!entries.length) {
            list.innerHTML = '';
            empty.hidden = false;
            return;
        }
        empty.hidden = true;
        list.innerHTML = entries.map(locationListItemHtml).join('');
    }
    window.scriptyRefreshFountainLocationList = refreshLocationList;

    function setLocationListOpen(open) {
        var el = ensureLocationList();
        el.hidden = !open;
        document.documentElement.classList.toggle('fountain-location-list-open', open);
        var btn = document.getElementById('nav-location-list-toggle');
        if (btn) {
            btn.setAttribute('aria-pressed', open ? 'true' : 'false');
            btn.classList.toggle('is-active', open);
        }
        try {
            localStorage.setItem('scripty-fountain-location-list', open ? 'true' : 'false');
        } catch (err) { /* ignore */ }
        if (open) {
            if (outlineEl && !outlineEl.hidden) setOutlineOpen(false);
            if (characterListEl && !characterListEl.hidden) setCharacterListOpen(false);
            refreshLocationList();
        }
        syncListsToolbarActive();
        requestAnimationFrame(function() {
            requestAnimationFrame(function() {
                if (typeof window.scriptyRepositionBlockCaretPreview === 'function') {
                    window.scriptyRepositionBlockCaretPreview();
                }
            });
        });
    }

    function toggleLocationList() {
        var el = ensureLocationList();
        setLocationListOpen(!!el.hidden);
    }
    window.scriptyToggleFountainLocationList = toggleLocationList;

    /** Words the custom spellchecker should not flag (cast names, locations, scene tokens). */
    window.scriptyGetSpellAllowlist = function() {
        var words = [];
        characterEntries().forEach(function(entry) {
            if (entry && entry.name) words.push(entry.name);
        });
        collectLocationItems().forEach(function(entry) {
            if (entry && entry.name) words.push(entry.name);
        });
        sceneEntries().forEach(function(entry) {
            if (entry && entry.name) words.push(entry.name);
        });
        document.querySelectorAll('.project-script .script-character-name, .project-script .reader-visible-character-name').forEach(function(el) {
            var name = (el.textContent || '').trim();
            if (name) words.push(name);
        });
        return words;
    };

    // --- Event wiring ---

    document.addEventListener('keydown', function(e) {
        if (!document.querySelector('.project-script')) return;
        var textarea = e.target;
        if (!isBlockContentTextarea(textarea)) return;

        // Autocomplete navigation
        if (autocompleteEl && !autocompleteEl.hidden) {
            var options = autocompleteEl.querySelectorAll('li');
            if (e.key === 'ArrowDown' && options.length) {
                e.preventDefault();
                autocompleteIndex = Math.min(options.length - 1, autocompleteIndex + 1);
                options.forEach(function(li, i) {
                    li.classList.toggle('is-active', i === autocompleteIndex);
                    li.setAttribute('aria-selected', i === autocompleteIndex ? 'true' : 'false');
                });
                return;
            }
            if (e.key === 'ArrowUp' && options.length) {
                e.preventDefault();
                autocompleteIndex = Math.max(0, autocompleteIndex - 1);
                options.forEach(function(li, i) {
                    li.classList.toggle('is-active', i === autocompleteIndex);
                    li.setAttribute('aria-selected', i === autocompleteIndex ? 'true' : 'false');
                });
                return;
            }
            if ((e.key === 'Enter' || e.key === 'Tab') && !e.shiftKey && autocompleteIndex >= 0 && options[autocompleteIndex]) {
                e.preventDefault();
                e.stopImmediatePropagation();
                var chosen = options[autocompleteIndex];
                acceptAutocomplete(
                    textarea,
                    chosen.getAttribute('data-name') || chosen.textContent,
                    chosen.getAttribute('data-person-id')
                );
                return;
            }
            if (e.key === 'Escape') {
                e.preventDefault();
                e.stopImmediatePropagation();
                hideAutocomplete();
                return;
            }
        }

        // Tab / Shift+Tab: cycle Fountain element types
        if (e.key === 'Tab' && !e.altKey && !e.metaKey && !e.ctrlKey) {
            e.preventDefault();
            e.stopImmediatePropagation();
            var row = findAnyBlockRow(textarea);
            var next = cycleType(rowType(row), e.shiftKey);
            applyTypeToActiveBlock(next);
            return;
        }

        // Enter: run Fountain detection before create/save handlers
        if (e.key === 'Enter' && !e.shiftKey && !e.repeat) {
            var detected = detectFountain(textarea.value);
            if (detected) {
                applyDetectionToTextarea(textarea, { force: true });
            }
            hideAutocomplete();
            var currentRow = findAnyBlockRow(textarea);
            if (currentRow) {
                var typeForNext = detected ? detected.type : rowType(currentRow);
                window.scriptyPendingCreateType = nextTypeAfter(typeForNext);
            }
        }
    }, true);

    document.addEventListener('input', function(e) {
        if (!document.querySelector('.project-script')) return;
        var textarea = e.target;
        if (!isBlockContentTextarea(textarea)) return;

        var value = textarea.value;
        var trimmed = value.trim();
        // Live-detect force markers immediately
        if (/^[.@>~#=]/.test(trimmed) || trimmed.startsWith('[[') || /^={3,}$/.test(trimmed)) {
            applyDetectionToTextarea(textarea, { force: false });
        }

        maybeShowAutocomplete(textarea);
    });

    document.addEventListener('focusout', function(e) {
        if (!isBlockContentTextarea(e.target)) return;
        // Delay so autocomplete click can fire
        setTimeout(function() {
            if (autocompleteEl && autocompleteEl.contains(document.activeElement)) return;
            hideAutocomplete();
        }, 150);
    });

    document.addEventListener('click', function(e) {
        if (!autocompleteEl || autocompleteEl.hidden) return;
        var li = e.target.closest('#fountain-char-autocomplete li');
        if (li) {
            e.preventDefault();
            var textarea = autocompleteTextarea || document.activeElement;
            if (!isBlockContentTextarea(textarea)) {
                textarea = document.querySelector('.block-content textarea[name="content"]:focus')
                    || document.querySelector('.block-row:not([data-block-id]) textarea[name="content"]');
            }
            if (textarea) {
                acceptAutocomplete(
                    textarea,
                    li.getAttribute('data-name') || li.textContent,
                    li.getAttribute('data-person-id')
                );
            }
            return;
        }
        if (!e.target.closest('#fountain-char-autocomplete')) {
            hideAutocomplete();
        }
    });

    // After HTMX swaps, refresh outline / character list and apply pending create type
    document.body.addEventListener('htmx:afterSwap', function() {
        sceneCache.loadedAt = 0;
        characterCache.loadedAt = 0;
        refreshOutline();
        refreshCharacterList();
        refreshLocationList();
        applyPendingCreateType();
    });

    function applyPendingCreateType() {
        var pending = window.scriptyPendingCreateType;
        if (!pending) return;
        var createRow = document.querySelector(
            '.project-script .block-row:not([data-block-id]) textarea[name="content"]'
        );
        if (createRow) {
            setCreateRowType(createRow.closest('.block-row'), pending);
        }
        window.scriptyPendingCreateType = null;
    }
    window.scriptyApplyPendingCreateType = applyPendingCreateType;

    // Observe DOM for create rows inserted via fetch (nav.html)
    var observer = new MutationObserver(function(mutations) {
        var needsOutline = false;
        mutations.forEach(function(m) {
            m.addedNodes.forEach(function(node) {
                if (node.nodeType !== 1) return;
                if (node.matches && node.matches('.block-row:not([data-block-id])')) {
                    if (window.scriptyPendingCreateType) {
                        setCreateRowType(node, window.scriptyPendingCreateType);
                        window.scriptyPendingCreateType = null;
                    } else {
                        var prev = previousSavedRow(node);
                        if (prev) prepareCreateRow(node, rowType(prev));
                    }
                }
                if (node.querySelector && node.querySelector('.block-row:not([data-block-id])')) {
                    var nested = node.querySelector('.block-row:not([data-block-id])');
                    if (window.scriptyPendingCreateType) {
                        setCreateRowType(nested, window.scriptyPendingCreateType);
                        window.scriptyPendingCreateType = null;
                    }
                }
                if (node.matches && (node.matches('.block-row[data-block-id]') || node.querySelector('.block-row[data-block-id]'))) {
                    needsOutline = true;
                    sceneCache.loadedAt = 0;
                    characterCache.loadedAt = 0;
                }
            });
            if (m.removedNodes.length) needsOutline = true;
        });
        if (needsOutline) {
            refreshOutline();
            refreshCharacterList();
            refreshLocationList();
        }
    });

    function startObserver() {
        var root = document.querySelector('.project-script') || document.body;
        observer.observe(root, { childList: true, subtree: true });
    }

    function initOutlineButton() {
        var btn = document.getElementById('nav-outline-toggle');
        if (!btn) return;
        // Prevent focus steal so the script caret / test caret stays put.
        btn.addEventListener('mousedown', function(e) {
            if (e.button !== 0) return;
            e.preventDefault();
        });
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            toggleOutline();
        });
        var preferOpen = false;
        try {
            preferOpen = localStorage.getItem('scripty-fountain-outline') === 'true';
        } catch (err) { /* ignore */ }
        if (preferOpen) setOutlineOpen(true);
        else ensureOutline().hidden = true;
    }

    function initCharacterListButton() {
        var btn = document.getElementById('nav-character-list-toggle');
        if (!btn) return;
        btn.addEventListener('mousedown', function(e) {
            if (e.button !== 0) return;
            e.preventDefault();
        });
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            toggleCharacterList();
        });
        var preferOpen = false;
        try {
            preferOpen = localStorage.getItem('scripty-fountain-character-list') === 'true';
        } catch (err) { /* ignore */ }
        if (preferOpen) setCharacterListOpen(true);
        else ensureCharacterList().hidden = true;
    }

    function initLocationListButton() {
        var btn = document.getElementById('nav-location-list-toggle');
        if (!btn) return;
        btn.addEventListener('mousedown', function(e) {
            if (e.button !== 0) return;
            e.preventDefault();
        });
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            toggleLocationList();
        });
        var preferOpen = false;
        try {
            preferOpen = localStorage.getItem('scripty-fountain-location-list') === 'true';
        } catch (err) { /* ignore */ }
        if (preferOpen) setLocationListOpen(true);
        else ensureLocationList().hidden = true;
    }

    // Expose apply helper used by Tab cycling when element-type.js is present
    window.scriptyApplyFountainType = function(type, preferredBlockId) {
        if (typeof window.scriptyApplyElementType === 'function') {
            window.scriptyApplyElementType(type, preferredBlockId || null);
            return;
        }
        var ta = document.activeElement;
        var row = isBlockContentTextarea(ta) ? findAnyBlockRow(ta) : null;
        if (row && isCreateRow(row)) setCreateRowType(row, type);
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function() {
            startObserver();
            initOutlineButton();
            initCharacterListButton();
            initLocationListButton();
            loadCharacters(true);
        });
    } else {
        startObserver();
        initOutlineButton();
        initCharacterListButton();
        initLocationListButton();
        loadCharacters(true);
    }
})();
