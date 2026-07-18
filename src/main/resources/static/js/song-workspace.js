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

    function projectKey() {
        // The workspace is always scoped to one project, so any song's
        // data-project-id identifies which set of open sections to remember.
        var input = document.querySelector('.song-workspace-title[data-project-id]');
        var id = input ? input.getAttribute('data-project-id') : null;
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
