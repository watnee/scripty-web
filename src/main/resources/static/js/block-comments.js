/**
 * Threaded per-block comments.
 *
 * The block drag menu ("⋮⋮" → Comments) calls window.scriptyOpenBlockComments(row),
 * which opens a popover anchored to the block showing the discussion thread plus
 * an add-comment box. A small "💬 N" badge on each commented row mirrors the
 * count and also opens the thread.
 *
 * Talks to the inline JSON endpoints on BlockController:
 *   GET  /block/comments?id=            → { comments, count, canComment }
 *   GET  /block/commentCounts?projectId= → { counts: { blockId: n } }
 *   POST /block/addComment (id, body)    → { comment, count, blockId }
 *   POST /block/deleteComment (commentId)→ { count, blockId }
 * CSRF tokens are attached by csrf.js's fetch wrapper.
 */
(function () {
    'use strict';

    if (window._scriptyBlockCommentsInit) return;
    window._scriptyBlockCommentsInit = true;

    // Comment counts by block id, so badge repaints after HTMX swaps need no refetch.
    var countCache = Object.create(null);
    var popover = null;
    var activeBlockId = null;

    function projectId() {
        return typeof window.scriptyResolveProjectId === 'function'
            ? window.scriptyResolveProjectId()
            : null;
    }

    function esc(text) {
        var div = document.createElement('div');
        div.textContent = text == null ? '' : String(text);
        return div.innerHTML;
    }

    function formatTimestamp(iso) {
        if (!iso) return '';
        var date = new Date(iso);
        if (isNaN(date.getTime())) return iso;
        try {
            return date.toLocaleString(undefined, {
                month: 'short', day: 'numeric',
                hour: 'numeric', minute: '2-digit'
            });
        } catch (e) {
            return date.toLocaleString();
        }
    }

    function rowsForBlock(blockId) {
        return Array.prototype.slice.call(
            document.querySelectorAll('[data-block-id="' + blockId + '"]')
        );
    }

    // ---- Count badges -------------------------------------------------------

    function ensureBadge(row) {
        var dropdown = row.querySelector('.block-drag-menu-dropdown');
        if (!dropdown) return null;
        var badge = row.querySelector('.block-comment-indicator');
        if (!badge) {
            badge = document.createElement('button');
            badge.type = 'button';
            badge.className = 'block-comment-indicator';
            badge.setAttribute('title', 'View comments');
            badge.setAttribute('aria-label', 'View comments');
            badge.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                openForRow(row);
            });
            dropdown.insertAdjacentElement('afterend', badge);
        }
        return badge;
    }

    function paintRowBadge(row) {
        if (!row || !row.hasAttribute('data-block-id')) return;
        var blockId = row.getAttribute('data-block-id');
        var count = countCache[blockId] || 0;
        row.setAttribute('data-comment-count', String(count));
        if (count > 0) {
            var badge = ensureBadge(row);
            if (badge) {
                badge.textContent = '💬 ' + count;
                badge.hidden = false;
            }
        } else {
            var existing = row.querySelector('.block-comment-indicator');
            if (existing) existing.hidden = true;
            // Keep the hydrated drag-menu label in sync too.
            var label = row.querySelector('.block-drag-menu-comments-label');
            if (label) label.textContent = 'Comments';
        }
    }

    function paintAllBadges() {
        document.querySelectorAll('[data-block-id]').forEach(paintRowBadge);
    }

    function setCount(blockId, count) {
        countCache[blockId] = count;
        rowsForBlock(blockId).forEach(paintRowBadge);
    }

    function refreshCounts() {
        var pid = projectId();
        if (!pid) return;
        fetch('/block/commentCounts?projectId=' + encodeURIComponent(pid), {
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json' }
        }).then(function (r) {
            if (!r.ok) throw new Error('counts failed');
            return r.json();
        }).then(function (data) {
            countCache = Object.create(null);
            var counts = data && data.counts ? data.counts : {};
            Object.keys(counts).forEach(function (blockId) {
                countCache[blockId] = counts[blockId];
            });
            paintAllBadges();
        }).catch(function () { /* leave badges as-is */ });
    }

    // ---- Popover ------------------------------------------------------------

    function buildPopover() {
        var el = document.createElement('div');
        el.id = 'scripty-block-comments-popover';
        el.className = 'block-comments-popover';
        el.setAttribute('role', 'dialog');
        el.setAttribute('aria-label', 'Block comments');
        el.hidden = true;
        el.innerHTML =
            '<div class="block-comments-header">' +
                '<span class="block-comments-title">Comments</span>' +
                '<button type="button" class="block-comments-close" aria-label="Close comments">&times;</button>' +
            '</div>' +
            '<div class="block-comments-list" aria-live="polite"></div>' +
            '<form class="block-comments-form">' +
                '<textarea class="block-comments-input" rows="2" placeholder="Add a comment…" aria-label="Add a comment"></textarea>' +
                '<div class="block-comments-form-actions">' +
                    '<span class="block-comments-hint">⌘/Ctrl + Enter</span>' +
                    '<button type="submit" class="block-comments-submit">Comment</button>' +
                '</div>' +
            '</form>';
        document.body.appendChild(el);

        el.querySelector('.block-comments-close').addEventListener('click', closePopover);
        el.querySelector('.block-comments-form').addEventListener('submit', function (e) {
            e.preventDefault();
            submitComment();
        });
        el.querySelector('.block-comments-input').addEventListener('keydown', function (e) {
            if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
                e.preventDefault();
                submitComment();
            }
        });
        return el;
    }

    function renderComments(comments) {
        var list = popover.querySelector('.block-comments-list');
        list.innerHTML = '';
        if (!comments || !comments.length) {
            var empty = document.createElement('div');
            empty.className = 'block-comments-empty';
            empty.textContent = 'No comments yet.';
            list.appendChild(empty);
            return;
        }
        comments.forEach(function (c) {
            list.appendChild(renderComment(c));
        });
        list.scrollTop = list.scrollHeight;
    }

    function renderComment(c) {
        var item = document.createElement('div');
        item.className = 'block-comment';
        item.setAttribute('data-comment-id', c.id);

        var meta = document.createElement('div');
        meta.className = 'block-comment-meta';
        meta.innerHTML =
            '<span class="block-comment-author">' + esc(c.authorName) + '</span>' +
            '<span class="block-comment-time">' + esc(formatTimestamp(c.createdAt)) + '</span>';

        if (c.canDelete) {
            var del = document.createElement('button');
            del.type = 'button';
            del.className = 'block-comment-delete';
            del.setAttribute('aria-label', 'Delete comment');
            del.title = 'Delete comment';
            del.textContent = '×';
            del.addEventListener('click', function () {
                deleteComment(c.id, item);
            });
            meta.appendChild(del);
        }

        var body = document.createElement('div');
        body.className = 'block-comment-body';
        body.textContent = c.body;

        item.appendChild(meta);
        item.appendChild(body);
        return item;
    }

    function positionPopover(row) {
        // Dock the thread to the RIGHT of the block. Anchor to the script text
        // (.block-content) so the popover sits in the right margin beside the
        // block; fall back to the row when content isn't found.
        var anchor = row.querySelector('.block-content') || row;
        var rect = anchor.getBoundingClientRect();
        popover.hidden = false; // measure with layout applied
        var pw = popover.offsetWidth || 320;
        var ph = popover.offsetHeight || 260;
        var margin = 8;

        // Prefer the right side; flip to the left of the block only if the
        // popover would overflow the viewport, then clamp as a last resort.
        var left = rect.right + margin;
        if (left + pw > window.innerWidth - margin) {
            var flipped = rect.left - pw - margin;
            left = flipped >= margin
                ? flipped
                : Math.max(margin, window.innerWidth - pw - margin);
        }
        var top = rect.top;
        if (top + ph > window.innerHeight - margin) {
            top = Math.max(margin, window.innerHeight - ph - margin);
        }
        popover.style.left = Math.round(left) + 'px';
        popover.style.top = Math.round(top) + 'px';
    }

    function openForRow(row) {
        if (!row || !row.hasAttribute('data-block-id')) return;
        if (!popover) popover = buildPopover();
        activeBlockId = row.getAttribute('data-block-id');
        popover.dataset.blockId = activeBlockId;

        var list = popover.querySelector('.block-comments-list');
        list.innerHTML = '<div class="block-comments-empty">Loading…</div>';
        popover.querySelector('.block-comments-input').value = '';
        popover.hidden = false;
        positionPopover(row);

        fetch('/block/comments?id=' + encodeURIComponent(activeBlockId), {
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json' }
        }).then(function (r) {
            if (!r.ok) throw new Error('load failed');
            return r.json();
        }).then(function (data) {
            if (popover.hidden || popover.dataset.blockId !== activeBlockId) return;
            renderComments(data.comments);
            setCount(activeBlockId, data.count || 0);
            positionPopover(row);
            popover.querySelector('.block-comments-input').focus();
        }).catch(function () {
            list.innerHTML = '<div class="block-comments-empty">Could not load comments.</div>';
        });
    }

    function lastEditedBlockId() {
        var editable = window.scriptyLastFocusedEditable;
        if (!editable || !editable.isConnected) return null;
        var row = window.scriptyFindBlockRow
            ? window.scriptyFindBlockRow(editable)
            : (editable.closest && editable.closest('[data-block-id]'));
        return row ? row.getAttribute('data-block-id') : null;
    }

    // Toggle comments for whatever block the writer is on (keyboard shortcut / Tools menu).
    // Returns true when it acted, so callers can decide whether to swallow the key event.
    function toggleForActiveBlock(triggerEl) {
        var blockId = window.scriptyGetActiveBlockId
            ? window.scriptyGetActiveBlockId(triggerEl || null)
            : null;
        // Opening a toolbar dropdown blurs the block, and the menu is not "format chrome"
        // that scriptyGetActiveBlockId recovers from — fall back to the last edited block.
        if (!blockId) blockId = lastEditedBlockId();
        if (!blockId) return false;
        if (popover && !popover.hidden && String(popover.dataset.blockId) === String(blockId)) {
            closePopover();
            return true;
        }
        var row = document.querySelector('[data-block-id="' + blockId + '"]');
        if (!row) return false;
        openForRow(row);
        return true;
    }

    function submitComment() {
        if (!popover || popover.hidden || !activeBlockId) return;
        var input = popover.querySelector('.block-comments-input');
        var submit = popover.querySelector('.block-comments-submit');
        var body = input.value.trim();
        if (!body) {
            input.focus();
            return;
        }
        submit.disabled = true;

        var params = new URLSearchParams();
        params.set('id', activeBlockId);
        params.set('body', body);

        fetch('/block/addComment', {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json'
            },
            body: params.toString()
        }).then(function (r) {
            if (!r.ok) throw new Error('add failed');
            return r.json();
        }).then(function (data) {
            var list = popover.querySelector('.block-comments-list');
            var empty = list.querySelector('.block-comments-empty');
            if (empty) empty.remove();
            if (data.comment) {
                list.appendChild(renderComment(data.comment));
                list.scrollTop = list.scrollHeight;
            }
            input.value = '';
            if (typeof data.count === 'number') setCount(activeBlockId, data.count);
            input.focus();
        }).catch(function () {
            /* leave text in place so the writer can retry */
        }).finally(function () {
            submit.disabled = false;
        });
    }

    function deleteComment(commentId, itemEl) {
        var params = new URLSearchParams();
        params.set('commentId', commentId);
        fetch('/block/deleteComment', {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json'
            },
            body: params.toString()
        }).then(function (r) {
            if (!r.ok) throw new Error('delete failed');
            return r.json();
        }).then(function (data) {
            if (itemEl) itemEl.remove();
            var blockId = (data && data.blockId != null) ? String(data.blockId) : activeBlockId;
            if (data && typeof data.count === 'number') setCount(blockId, data.count);
            var list = popover && popover.querySelector('.block-comments-list');
            if (list && !list.querySelector('.block-comment')) {
                list.innerHTML = '<div class="block-comments-empty">No comments yet.</div>';
            }
        }).catch(function () { /* keep comment visible on failure */ });
    }

    function closePopover() {
        if (popover) {
            popover.hidden = true;
            popover.dataset.blockId = '';
        }
        activeBlockId = null;
    }

    // Close on outside click / Escape.
    document.addEventListener('click', function (e) {
        if (!popover || popover.hidden) return;
        if (popover.contains(e.target)) return;
        // The click that opened the popover keeps bubbling to here; ignore known triggers.
        if (e.target.closest && e.target.closest('.block-comment-indicator, [data-scripty-comments-trigger]')) return;
        closePopover();
    });
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && popover && !popover.hidden) {
            closePopover();
        }
    });
    window.addEventListener('resize', function () {
        if (popover && !popover.hidden && activeBlockId) {
            var row = document.querySelector('[data-block-id="' + activeBlockId + '"]');
            if (row) positionPopover(row);
        }
    });

    // Repaint badges after HTMX swaps bring in new/replaced rows (uses cache, no refetch).
    document.body.addEventListener('htmx:afterSettle', function () {
        paintAllBadges();
    });

    window.scriptyOpenBlockComments = openForRow;
    window.scriptyToggleBlockComments = toggleForActiveBlock;
    window.scriptyRefreshBlockCommentCounts = refreshCounts;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', refreshCounts);
    } else {
        refreshCounts();
    }
})();
