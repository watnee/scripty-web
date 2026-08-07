/**
 * Hold a control to keep it going, the way a held key repeats.
 *
 * A step back is almost never one step. A writer who has typed a paragraph into
 * the wrong element, or a verse into the wrong song, wants the last half-dozen
 * changes gone — and a control that gives exactly one of them per press turns
 * that into six clicks in the same square centimetre, each one a fresh chance to
 * miss and hit whatever is beside it. Every keyboard in the world answers a held
 * key by repeating it, and that is the gesture this borrows: press for one step,
 * hold to keep going. The Apple client says it the same way.
 *
 * One step at a time, always. A song's undo is a round trip that ends in the
 * whole lyric being read back, and ten of those in flight together would leave
 * whichever answered last on screen rather than whichever was asked for last. So
 * a step is awaited before the next is asked for, and the run goes at whatever
 * pace the history can keep up with.
 *
 * The click a release fires is left alone after a plain press — that is the tap,
 * and the control's own handler is what serves it — and swallowed after a hold,
 * whose steps have already given the writer everything they asked for. The
 * swallow happens on the document in the capture phase because the controls here
 * are wired with onclick and with delegated listeners, and only capturing on the
 * way down beats both.
 */
(function () {
    'use strict';

    if (window._scriptyHoldRepeatInit) return;
    window._scriptyHoldRepeatInit = true;

    /**
     * How long a press has to last before it stops being a click. The system's
     * own long press, which is what every held control is measured against.
     */
    var HOLD_DELAY = 500;

    /**
     * The pause between steps once a run is going. A held key's repeat rate,
     * near enough — fast enough to feel like rewinding, slow enough that a
     * finger lifted a moment late has not taken back a paragraph. It is the gap
     * *after* each step lands rather than instead of one, so a history that
     * answers slowly sets its own pace.
     */
    var REPEAT_GAP = 140;

    /** What the tooltip gains, since nothing else on screen says a control repeats. */
    var HINT = ' — hold to keep going';

    /** The control whose next click is the tail of a hold, and so is not a click. */
    var swallow = null;

    document.addEventListener('click', function (event) {
        if (!swallow) return;
        var target = event.target;
        if (target !== swallow && !(swallow.contains && swallow.contains(target))) return;
        swallow = null;
        event.preventDefault();
        event.stopImmediatePropagation();
    }, true);

    // A hold whose click never arrives — the control was swapped out from under
    // it, the pointer was cancelled by the browser — must not leave a swallow
    // waiting to eat somebody else's click. The next press clears it, which is
    // the only moment that matters: nothing between two presses can be the tail
    // of the first one.
    document.addEventListener('pointerdown', function () {
        swallow = null;
    }, true);

    /**
     * Whether a control has anything left to give. The same question its own
     * dimming asks, so a run ends at the bottom of the stack whether or not the
     * pointer has lifted.
     */
    function offeredByDefault(element) {
        return !element.disabled
            && !element.classList.contains('is-unavailable')
            && element.isConnected;
    }

    /**
     * @param {Element} element the control to make repeat
     * @param {Function} step one step; may return a promise, and may resolve
     *        false to say it did nothing, which ends the run
     * @param {Function} [offered] whether another step is worth taking
     */
    function attach(element, step, offered) {
        if (!element || element._scriptyHoldRepeat) return;
        element._scriptyHoldRepeat = true;
        offered = offered || function () { return offeredByDefault(element); };

        var waiting = null;
        var running = false;
        var walked = false;

        function stop() {
            if (waiting) {
                clearTimeout(waiting);
                waiting = null;
            }
            running = false;
        }

        function take() {
            if (!running) return;
            if (!offered()) {
                stop();
                return;
            }
            walked = true;
            var result;
            try {
                result = step();
            } catch (e) {
                stop();
                return;
            }
            Promise.resolve(result).then(function (done) {
                if (!running || done === false) {
                    stop();
                    return;
                }
                waiting = setTimeout(take, REPEAT_GAP);
            }, function () {
                stop();
            });
        }

        // Nothing on screen says a control repeats, and the tooltip is where a
        // writer looks. Written on hover rather than at attach time because
        // these titles are rewritten with their keyboard shortcut afterwards,
        // and a hint added before that is a hint thrown away.
        element.addEventListener('pointerenter', function () {
            var title = element.getAttribute('title');
            if (!title || title.indexOf(HINT) !== -1) return;
            element.setAttribute('title', title + HINT);
        });

        element.addEventListener('pointerdown', function (event) {
            // The primary button only: a right-click is a menu, not a press.
            if (event.button !== 0) return;
            walked = false;
            stop();
            if (!offered()) return;
            running = true;
            waiting = setTimeout(function () {
                waiting = null;
                take();
            }, HOLD_DELAY);
        });

        // A press that wanders off the control on its way up is one taken back,
        // the way a button treats one — but only as far as the click goes: steps
        // a hold has already taken stand.
        ['pointerup', 'pointercancel', 'pointerleave'].forEach(function (name) {
            element.addEventListener(name, function () {
                stop();
                if (walked) swallow = element;
            });
        });
    }

    window.scriptyHoldToRepeat = attach;
})();
