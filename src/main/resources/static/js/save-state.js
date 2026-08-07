/**
 * Where the words are: a small badge in the screenplay's toolbar saying whether
 * what has been typed has reached the server.
 *
 * Editing here saves itself, which is the right behaviour and the one that
 * leaves nothing to look at. A writer who has just typed a scene has no way to
 * tell a save that landed from one that is still in the air, and no way at all
 * to tell one the server refused — the page looks identical in all three cases.
 * The Apple client answers that with a cloud beside the title; this is the same
 * answer in the shape the web already uses for its other statuses.
 *
 * Three states and no more, because there are only three things worth knowing:
 * everything is saved, something is on its way, or something did not go. The
 * badge stays out of the way until it has something to say — before the first
 * save of a visit there is no news, and a badge announcing that would be one
 * more thing on a toolbar that is already full.
 *
 * Opening it says when the script was last in step with the server, how much is
 * still waiting where a connection has been lost, and offers to send it now
 * rather than waiting for the queue's own timer.
 *
 * Only the ordinary editing path is watched: block writes go through HTMX to
 * /block/…, and edits made without a connection go to the offline queue, which
 * counts itself. Undo and redo navigate, which ends the question by leaving.
 */
(function () {
    'use strict';

    if (window._scriptySaveStateInit) return;
    window._scriptySaveStateInit = true;

    var STATES = { saving: 'saving', saved: 'saved', error: 'error' };

    var state = null;
    var lastSavedAt = null;
    var inFlight = 0;
    var pendingCount = 0;
    var tick = null;

    function badge() {
        return document.getElementById('project-save-state');
    }

    function projectId() {
        return typeof window.scriptyResolveProjectId === 'function'
            ? window.scriptyResolveProjectId()
            : null;
    }

    /**
     * A block write, as opposed to every other request the page makes — and, in
     * particular, as opposed to a block *read*: opening a row for editing is a
     * GET to the same /block/ family, and treating that as a save had the badge
     * announcing "Saving…" for the act of putting a cursor in a line.
     */
    function isBlockWrite(detail) {
        var config = detail && detail.requestConfig;
        if (!config || typeof config.path !== 'string') return false;
        if (config.path.indexOf('/block/') !== 0) return false;
        var verb = String(config.verb || 'get').toLowerCase();
        return verb !== 'get';
    }

    function relativeTime(when) {
        var seconds = Math.floor((Date.now() - when) / 1000);
        if (seconds < 60) return 'just now';
        var minutes = Math.floor(seconds / 60);
        if (minutes < 60) return minutes + 'm ago';
        var hours = Math.floor(minutes / 60);
        if (hours < 24) return hours + 'h ago';
        return new Date(when).toLocaleString(undefined, { hour: 'numeric', minute: '2-digit' });
    }

    function setState(next) {
        state = next;
        render();
    }

    function render() {
        var el = badge();
        if (!el) return;
        if (!state) {
            el.hidden = true;
            return;
        }
        el.hidden = false;
        el.setAttribute('data-state', state);

        var label = el.querySelector('.project-save-state-label');
        var toggle = el.querySelector('.project-save-state-toggle');
        var text = state === STATES.saving ? 'Saving…'
            : state === STATES.error ? 'Not saved'
            : 'Saved';
        if (label) label.textContent = text;
        if (toggle) {
            toggle.setAttribute('aria-label', text + ' — where these words are');
            toggle.setAttribute('title', state === STATES.error
                ? 'The server refused a change. Open for what to do about it.'
                : 'Where these words are');
        }
        renderPanel();
    }

    function renderPanel() {
        var el = badge();
        if (!el) return;
        var detail = el.querySelector('.project-save-state-detail');
        var waiting = el.querySelector('.project-save-state-waiting');
        var sync = el.querySelector('.project-save-state-sync');
        if (!detail) return;

        if (state === STATES.error) {
            detail.textContent = 'The server refused the last change. What you typed is still on screen.';
        } else if (inFlight > 0) {
            detail.textContent = 'A change is on its way to the server.';
        } else if (lastSavedAt) {
            detail.textContent = 'In step with the server ' + relativeTime(lastSavedAt) + '.';
        } else {
            detail.textContent = 'Nothing has been changed yet this visit.';
        }

        if (waiting) {
            waiting.hidden = pendingCount === 0;
            waiting.textContent = pendingCount === 1
                ? '1 change is waiting on this device.'
                : pendingCount + ' changes are waiting on this device.';
        }
        if (sync) {
            sync.hidden = typeof window.scriptySyncPendingEdits !== 'function';
        }
    }

    /**
     * How much this device is still holding. Asked of the offline store rather
     * than counted here: the queue is the only thing that knows what it has, and
     * a second tally would be a second answer to disagree with.
     */
    function refreshPending() {
        var store = window.scriptyOfflineStore;
        var id = projectId();
        if (!store || !store.countPendingEdits || !id) return;
        store.countPendingEdits(id).then(function (count) {
            var was = pendingCount;
            pendingCount = count || 0;
            if (pendingCount > 0 && state !== STATES.error) {
                setState(STATES.saving);
            } else if (was > 0 && pendingCount === 0 && state === STATES.saving && inFlight === 0) {
                lastSavedAt = Date.now();
                setState(STATES.saved);
            } else {
                renderPanel();
            }
        }).catch(function () { /* the store is unavailable; the badge says nothing new */ });
    }

    function panelOpen() {
        var el = badge();
        var panel = el && el.querySelector('.project-save-state-panel');
        return !!(panel && !panel.hidden);
    }

    function setPanelOpen(open) {
        var el = badge();
        if (!el) return;
        var panel = el.querySelector('.project-save-state-panel');
        var toggle = el.querySelector('.project-save-state-toggle');
        if (!panel) return;
        panel.hidden = !open;
        if (toggle) toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
        if (open) {
            refreshPending();
            renderPanel();
        }
    }

    // ------------------------------------------------------------- wiring

    document.body.addEventListener('htmx:beforeRequest', function (event) {
        if (!isBlockWrite(event.detail)) return;
        inFlight++;
        setState(STATES.saving);
    });

    document.body.addEventListener('htmx:afterRequest', function (event) {
        var detail = event.detail || {};
        if (!isBlockWrite(detail)) return;
        inFlight = Math.max(0, inFlight - 1);
        if (detail.successful) {
            lastSavedAt = Date.now();
            // A queue with something in it is not finished, whatever this one
            // request did.
            setState(pendingCount > 0 ? STATES.saving : STATES.saved);
            refreshPending();
        } else {
            setState(STATES.error);
        }
    });

    document.body.addEventListener('htmx:sendError', function (event) {
        if (!isBlockWrite(event.detail)) return;
        inFlight = Math.max(0, inFlight - 1);
        // A request that never left is what going offline looks like from here.
        // The offline banner is the louder half of that answer; this is the
        // quiet half, and it stays until the queue drains.
        setState(STATES.saving);
        refreshPending();
    });

    document.addEventListener('click', function (event) {
        var el = badge();
        if (!el) return;
        var toggle = event.target.closest && event.target.closest('.project-save-state-toggle');
        if (toggle && el.contains(toggle)) {
            event.preventDefault();
            setPanelOpen(!panelOpen());
            return;
        }
        var sync = event.target.closest && event.target.closest('.project-save-state-sync');
        if (sync && el.contains(sync)) {
            event.preventDefault();
            if (typeof window.scriptySyncPendingEdits === 'function') {
                setState(STATES.saving);
                Promise.resolve(window.scriptySyncPendingEdits()).then(refreshPending, refreshPending);
            }
            return;
        }
        if (panelOpen() && !el.contains(event.target)) setPanelOpen(false);
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && panelOpen()) setPanelOpen(false);
    });

    // Coming back online is the moment the queue starts draining, and the
    // moment the badge's answer is most likely to be out of date.
    window.addEventListener('online', refreshPending);
    window.addEventListener('offline', refreshPending);

    function start() {
        if (!badge()) return;
        refreshPending();
        render();
        if (tick) clearInterval(tick);
        // Only while there is something to keep an eye on: a badge saying
        // "saved 4m ago" is worth keeping current, and nothing else here moves
        // on its own.
        tick = setInterval(function () {
            if (state) renderPanel();
            if (pendingCount > 0 || state === STATES.saving) refreshPending();
        }, 15000);
    }

    document.body.addEventListener('htmx:afterSettle', start);
    document.body.addEventListener('htmx:historyRestore', start);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start);
    } else {
        start();
    }

    window.scriptySaveState = {
        refresh: refreshPending,
        state: function () { return state; }
    };
})();
