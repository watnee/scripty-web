/**
 * Edit screenplay: put the script back the way it is written in.
 *
 * There are four ways to be looking at a screenplay that is not the plain
 * writing column — page view, focus mode, outline mode, and the editing lock —
 * and they compose. A writer who turned two of them on an hour ago and now
 * wants to type has to work out which of the four is the one still in the way,
 * find its switch, and turn it off; and page view and the lock both take the
 * editing controls off the rows, so the way out is not where the writer is
 * looking. One item turns off whichever of them are on, which is the same thing
 * the Apple client's Edit Screenplay does.
 *
 * Each mode is turned off through its own switch rather than by clearing the
 * classes here: those modules own their storage, their menu state and, in page
 * view's case, a whole re-layout. The lock has no setter to call — it lives in
 * the toolbar script's own closure — so this presses its button, which is the
 * same path a writer would take and runs the flush of half-typed rows that
 * unlocking already does.
 *
 * Greyed when the plain column is already up, because a command that would do
 * nothing should say so before it is pressed rather than after.
 */
(function () {
    'use strict';

    if (window._scriptyEditScreenplayInit) return;
    window._scriptyEditScreenplayInit = true;

    function item() {
        return document.getElementById('nav-edit-screenplay');
    }

    function lockToggle() {
        return document.getElementById('nav-lock-toggle');
    }

    /** Everything currently standing between the writer and the words. */
    function modesOn() {
        var on = [];
        if (typeof window.scriptyIsPageViewMode === 'function' && window.scriptyIsPageViewMode()) {
            on.push('page view');
        }
        if (typeof window.scriptyIsFocusMode === 'function' && window.scriptyIsFocusMode()) {
            on.push('focus mode');
        }
        if (typeof window.scriptyIsOutlineMode === 'function' && window.scriptyIsOutlineMode()) {
            on.push('outline mode');
        }
        if (window.scriptyBlockEditLocked && lockToggle()) {
            on.push('the editing lock');
        }
        return on;
    }

    function sync() {
        var el = item();
        if (!el) return;
        var on = modesOn();
        var available = on.length > 0;
        el.classList.toggle('is-unavailable', !available);
        el.setAttribute('aria-disabled', available ? 'false' : 'true');
        el.title = available
            ? 'Turn off ' + readable(on) + ' and get back to writing'
            : 'The writing column is already up';
    }

    /** "page view and focus mode" — a list a person would say out loud. */
    function readable(names) {
        if (names.length === 1) return names[0];
        return names.slice(0, -1).join(', ') + ' and ' + names[names.length - 1];
    }

    function editScreenplay() {
        if (!modesOn().length) return;

        if (typeof window.scriptySetOutlineMode === 'function' && window.scriptyIsOutlineMode
                && window.scriptyIsOutlineMode()) {
            window.scriptySetOutlineMode(false);
        }
        if (typeof window.scriptySetPageViewMode === 'function' && window.scriptyIsPageViewMode
                && window.scriptyIsPageViewMode()) {
            window.scriptySetPageViewMode(false);
        }
        if (typeof window.scriptySetFocusMode === 'function' && window.scriptyIsFocusMode
                && window.scriptyIsFocusMode()) {
            window.scriptySetFocusMode(false);
        }
        // Last, and through its own button: unlocking flushes the rows that were
        // open when the lock went on, and that is the toolbar script's own job.
        if (window.scriptyBlockEditLocked && lockToggle()) {
            lockToggle().click();
        }

        // The lock's own work finishes asynchronously, so the state is read
        // again after it rather than assumed.
        window.setTimeout(sync, 0);
    }

    document.addEventListener('click', function (event) {
        var trigger = event.target.closest && event.target.closest('#nav-edit-screenplay');
        if (!trigger) return;
        event.preventDefault();
        editScreenplay();
    });

    ['scripty:page-view-mode-changed', 'scripty:outline-mode-changed'].forEach(function (name) {
        window.addEventListener(name, sync);
    });

    // Focus mode and the lock announce nothing, so the menu opening is when
    // their state is read — which is the only moment the answer is looked at.
    document.addEventListener('click', function (event) {
        if (event.target.closest && event.target.closest('#nav-view, .view-menu')) sync();
    }, true);

    document.body.addEventListener('htmx:afterSettle', sync);
    document.body.addEventListener('htmx:historyRestore', sync);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sync);
    } else {
        sync();
    }

    window.scriptyEditScreenplay = editScreenplay;
})();
