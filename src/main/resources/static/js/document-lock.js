/**
 * An editing lock for a song or a note.
 *
 * The screenplay has had one since it shipped: a finished draft that is being
 * read from — at a table read, on a phone in a rehearsal room — should not gain
 * a stray character because a thumb landed on it. A lyric is read from in
 * exactly those places and had no such switch, and a note is the shot list held
 * up on set and the production notes open on a stand. So the two of them get
 * the same lock the script has, on the same terms.
 *
 * Nothing here reaches the server. This is a choice about typing rather than
 * about the words, so it lives in localStorage beside the screenplay's own
 * `scripty-block-edit-locked-…`, and a locked song on one machine is not locked
 * on another. The key families are the Apple client's, so the two clients mean
 * the same thing by a locked song even though neither can read the other's
 * store: `scripty-song-edit-locked-…` and `scripty-note-edit-locked-…`.
 *
 * Scoped to one document rather than to the project — songs are finished one at
 * a time — and, for a song, to the edition when one is open, so locking the
 * performed lyric leaves the rewrite open. An edition with no lock of its own
 * inherits the document's, so opening a rewrite of a locked lyric does not hand
 * the keyboard back.
 *
 * One document is shown in three places at once (the list, the workspace, the
 * editor), and each of them can hold the switch, so every change re-applies to
 * the whole page rather than to the control that was pressed.
 *
 * Loaded from nav.html: the switch is on four pages, and it has to survive an
 * HTMX-boosted navigation between them.
 */
(function () {
    'use strict';

    if (window._scriptyDocumentLockInit) return;
    window._scriptyDocumentLockInit = true;

    var LOCKED_CLASS = 'is-edit-locked';

    function storage() {
        try {
            return window.localStorage;
        } catch (e) {
            return null;
        }
    }

    function keyFor(kind, scope, id) {
        return 'scripty-' + kind + '-edit-locked-' + scope + '-' + id;
    }

    /**
     * Whether this document is closed to typing. The edition answers when it has
     * an answer of its own; otherwise the document does.
     */
    function isLocked(kind, documentId, editionId) {
        var store = storage();
        if (!store || !documentId) return false;
        if (editionId) {
            var own = store.getItem(keyFor(kind, 'edition', editionId));
            if (own !== null) return own === 'true';
        }
        return store.getItem(keyFor(kind, 'document', documentId)) === 'true';
    }

    /** Written against the edition when one is open — see the note at the top. */
    function setLocked(kind, documentId, editionId, locked) {
        var store = storage();
        if (!store || !documentId) return;
        var key = editionId
            ? keyFor(kind, 'edition', editionId)
            : keyFor(kind, 'document', documentId);
        try {
            store.setItem(key, locked ? 'true' : 'false');
        } catch (e) { /* private browsing, or a full store */ }
    }

    // ------------------------------------------------- what is on the page

    function attr(node, name) {
        var value = node ? node.getAttribute(name) : null;
        return value === null || value === '' ? null : value;
    }

    /**
     * Everything on this page that can be locked, however it is drawn: a card in
     * a list, a section of a workspace, or the editor itself. Each one says what
     * document it is, what to close, and what to redraw.
     */
    function targets() {
        var found = [];

        // The songs or notes list: nothing to type into, but the row says
        // whether the document is locked and offers the switch.
        var list = document.getElementById('text-documents-list');
        if (list) {
            var listKind = attr(list, 'data-doc-noun') === 'note' ? 'note' : 'song';
            list.querySelectorAll('.text-document-card[data-doc-id]').forEach(function (card) {
                found.push({
                    kind: listKind,
                    documentId: attr(card, 'data-doc-id'),
                    editionId: null,
                    root: card,
                    fields: []
                });
            });
        }

        // A workspace stacks one editor per document; the lock belongs to the
        // card, as the reading does.
        document.querySelectorAll('.song-workspace-item[data-song-id]').forEach(function (card) {
            var note = card.querySelector('.note-workspace-content');
            if (note) {
                found.push({
                    kind: 'note',
                    documentId: attr(card, 'data-song-id'),
                    editionId: null,
                    root: card,
                    fields: [note]
                });
                return;
            }
            var editor = card.querySelector('.song-blocks-editor');
            found.push({
                kind: 'song',
                documentId: attr(card, 'data-song-id'),
                editionId: editor ? attr(editor, 'data-edition-id') : null,
                root: card,
                fields: lyricFields(card)
            });
        });

        // The song editor.
        var open = document.querySelector('.song-blocks-editor[data-document-id]');
        if (open && !open.closest('.song-workspace-item')) {
            found.push({
                kind: 'song',
                documentId: attr(open, 'data-document-id'),
                editionId: attr(open, 'data-edition-id'),
                root: open,
                fields: lyricFields(open),
                banner: document.querySelector('.text-document-editor-shell')
            });
        }

        // The note editor.
        var note = document.getElementById('text-document-content');
        if (note) {
            var id = document.querySelector('#text-document-form input[name="id"]');
            found.push({
                kind: 'note',
                documentId: id ? id.value : null,
                editionId: null,
                root: note.closest('.text-document-editor-shell') || note.parentElement,
                fields: [note],
                banner: document.querySelector('.text-document-editor-shell')
            });
        }
        return found.filter(function (target) { return !!target.documentId; });
    }

    function lyricFields(root) {
        return Array.prototype.slice.call(root.querySelectorAll('.song-block-textarea'));
    }

    // ------------------------------------------------------------ drawing

    /**
     * Closes or opens one document on screen.
     *
     * The fields are made read-only rather than disabled: a disabled field is
     * skipped by the caret and unreadable to a screen reader, and the point of a
     * locked lyric is that it can still be read.
     */
    function apply(target) {
        var locked = isLocked(target.kind, target.documentId, target.editionId);
        if (target.root) target.root.classList.toggle(LOCKED_CLASS, locked);
        target.fields.forEach(function (field) {
            // A document the server itself will not take edits for stays
            // read-only whatever the switch says.
            if (field.hasAttribute('data-server-readonly')) return;
            field.readOnly = locked;
        });
        var badge = target.root && target.root.querySelector('.text-document-locked-badge');
        if (badge) badge.hidden = !locked;
        if (target.banner) {
            // An editor page holds one document, and the controls that belong to
            // it are spread across the page rather than gathered inside it — the
            // note's formatting row sits in a toolbar above the field. So the
            // page itself carries the state, as the screenplay's lock does.
            document.documentElement.classList.toggle('scripty-document-edit-locked', locked);
            drawBanner(target, locked);
        }
        drawToggles(target, locked);
    }

    /**
     * The strip over a locked editor. A locked lyric looks exactly like an
     * unlocked one, so without this a click that does nothing has nothing to say
     * for itself. It is also the way out: clicking it unlocks.
     */
    function drawBanner(target, locked) {
        var shell = target.banner;
        var banner = shell.parentElement.querySelector('.document-lock-banner');
        if (!locked) {
            if (banner) banner.remove();
            return;
        }
        if (banner) return;
        banner = document.createElement('button');
        banner.type = 'button';
        banner.className = 'document-lock-banner';
        banner.setAttribute('aria-label', 'Editing is locked. Unlock editing.');
        banner.innerHTML =
            '<span class="document-lock-banner-icon" aria-hidden="true">&#128274;</span>' +
            '<strong>Locked</strong>' +
            '<span class="muted">— click to edit</span>';
        banner.addEventListener('click', function () {
            setLocked(target.kind, target.documentId, target.editionId, false);
            applyAll();
        });
        shell.parentElement.insertBefore(banner, shell);
    }

    /** Every switch that points at this document, wherever it is drawn. */
    function drawToggles(target, locked) {
        toggles().forEach(function (button) {
            var pointed = documentOf(button);
            if (!pointed
                || pointed.kind !== target.kind
                || pointed.documentId !== target.documentId) {
                return;
            }
            button.setAttribute('aria-pressed', locked ? 'true' : 'false');
            button.setAttribute('aria-checked', locked ? 'true' : 'false');
            var label = locked ? 'Unlock editing' : 'Lock editing';
            button.setAttribute('aria-label', label);
            button.setAttribute('title', locked
                ? 'Let this be typed into again'
                : 'Close this to typing on this device');
            var text = button.querySelector('[data-document-lock-label]');
            if (text) text.textContent = locked ? 'Unlock' : 'Lock';
        });
    }

    function toggles() {
        return Array.prototype.slice.call(document.querySelectorAll('[data-document-lock]'));
    }

    /**
     * Which document a switch belongs to: the card or editor it sits in, since
     * every one of them is inside the thing it locks.
     */
    function documentOf(button) {
        var all = targets();
        var match = null;
        all.forEach(function (target) {
            if (match || !target.root) return;
            if (target.root.contains(button)) match = target;
        });
        if (match) return match;
        // The editor's switch lives in a toolbar above the editor rather than
        // inside it, so the page's own document is the answer there.
        var editors = all.filter(function (target) { return !!target.banner; });
        return editors.length ? editors[0] : null;
    }

    function applyAll() {
        targets().forEach(apply);
        drawLockAll();
    }

    // -------------------------------------------------- lock the whole book

    /**
     * "Lock all" on a workspace asks each card for its own lock rather than
     * keeping a flag of its own: a writer who then unlocks the one number being
     * rewritten still has the rest of the book closed.
     */
    function drawLockAll() {
        var button = document.querySelector('[data-document-lock-all]');
        if (!button) return;
        var cards = workspaceTargets();
        button.hidden = !cards.length;
        var allLocked = cards.length > 0 && cards.every(function (target) {
            return isLocked(target.kind, target.documentId, target.editionId);
        });
        button.textContent = allLocked ? 'Unlock all' : 'Lock all';
        button.setAttribute('title', allLocked
            ? 'Let every one of these be typed into again'
            : 'Close every one of these to typing on this device');
    }

    function workspaceTargets() {
        return targets().filter(function (target) {
            return target.root && target.root.classList
                && target.root.classList.contains('song-workspace-item');
        });
    }

    // ------------------------------------------------------------- wiring

    document.addEventListener('click', function (event) {
        var all = event.target.closest && event.target.closest('[data-document-lock-all]');
        if (all) {
            event.preventDefault();
            var cards = workspaceTargets();
            var lock = !cards.every(function (target) {
                return isLocked(target.kind, target.documentId, target.editionId);
            });
            cards.forEach(function (target) {
                setLocked(target.kind, target.documentId, target.editionId, lock);
            });
            applyAll();
            return;
        }

        var button = event.target.closest && event.target.closest('[data-document-lock]');
        if (!button) return;
        event.preventDefault();
        var target = documentOf(button);
        if (!target) return;
        var locked = isLocked(target.kind, target.documentId, target.editionId);
        setLocked(target.kind, target.documentId, target.editionId, !locked);
        applyAll();
    });

    // Another tab of the same browser is the same device: a song locked in one
    // is locked in the other.
    window.addEventListener('storage', function (event) {
        if (event.key && event.key.indexOf('-edit-locked-') !== -1) applyAll();
    });

    ['htmx:afterSettle', 'htmx:afterSwap', 'htmx:historyRestore'].forEach(function (name) {
        document.body.addEventListener(name, applyAll);
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', applyAll);
    } else {
        applyAll();
    }

    window.scriptyDocumentLock = {
        isLocked: isLocked,
        setLocked: function (kind, documentId, editionId, locked) {
            setLocked(kind, documentId, editionId, locked);
            applyAll();
        },
        refresh: applyAll
    };
})();
