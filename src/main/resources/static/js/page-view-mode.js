/**
 * Screenplay page view mode (US Letter pagination).
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyPageViewModeInit) return;
    window._scriptyPageViewModeInit = true;

    var STORAGE_KEY = 'scripty-page-view-mode';
    var CLASS_NAME = 'scripty-page-view-mode';
    var PAGES_WRAP_CLASS = 'screenplay-pages';
    var PAGES_SETTLED_CLASS = 'screenplay-pages--settled';
    var PAGE_CLASS = 'screenplay-page';
    var PAGE_ACTIVE_CLASS = 'screenplay-page--active';
    var PAGE_BODY_CLASS = 'screenplay-page-body';
    var PAGE_NUMBER_CLASS = 'screenplay-page-number';
    var reflowTimer = null;
    var reflowDeadline = 0;
    var measuring = false;
    var enterAnimPending = false;
    var composing = false;
    var reflowPendingAfterCompose = false;

    // A debounce that restarts on every keystroke never fires while someone is
    // typing steadily, so an overfull page visibly spills past its bottom edge
    // until the typist pauses. Cap how long a pending reflow can be deferred.
    var REFLOW_MAX_WAIT = 700;

    // Top margins from the .screenplay-page-body > .block-row rules in
    // scripty.css, in em. Used only to seed the very first pagination, before
    // any row sits in a page body to measure a live value from.
    var MARGIN_EM_BY_TYPE = { SCENE: 2, DIALOGUE: 0, PARENTHETICAL: 0 };
    var DEFAULT_MARGIN_EM = 1;

    function isOn() {
        return localStorage.getItem(STORAGE_KEY) === '1';
    }

    function getToggleBtn() {
        return document.getElementById('page-view-mode-toggle');
    }

    function getSceneBlocks() {
        return document.querySelector('.project-script .scene-blocks');
    }

    function shortcutHint() {
        var isMac = window.scriptyIsMac
            ? window.scriptyIsMac()
            : /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
        return isMac ? '⌘⇧P' : 'Ctrl+Shift+P';
    }

    function apply(on) {
        document.documentElement.classList.toggle(CLASS_NAME, on);
        var btn = getToggleBtn();
        if (btn) {
            btn.setAttribute('aria-pressed', on ? 'true' : 'false');
            btn.setAttribute('aria-checked', on ? 'true' : 'false');
            btn.classList.toggle('is-active', on);
            var base = on ? 'Exit page view' : 'View screenplay as pages';
            var hint = shortcutHint();
            btn.title = base + ' (' + hint + ')';
            btn.setAttribute('aria-label', btn.title);
            if (typeof window.scriptySetMenuShortcut === 'function') {
                window.scriptySetMenuShortcut(btn, hint);
            }
        }
        if (on) {
            enterAnimPending = true;
            scheduleReflow(0);
        } else {
            enterAnimPending = false;
            unwrapPages();
            restoreWordPageEstimate();
        }
        try {
            window.dispatchEvent(new CustomEvent('scripty:page-view-mode-changed', { detail: { on: on } }));
        } catch (err) { /* ignore */ }
    }

    function sync() {
        if (getToggleBtn()) {
            var on = isOn();
            if (on && localStorage.getItem('scripty-outline-mode') === '1') {
                localStorage.setItem('scripty-outline-mode', '0');
                if (typeof window.scriptySetOutlineMode === 'function') {
                    window.scriptySetOutlineMode(false, { skipPeer: true });
                } else {
                    document.documentElement.classList.remove('scripty-outline-mode');
                    document.body.classList.remove('outline-mode');
                }
            }
            apply(on);
        } else {
            document.documentElement.classList.remove(CLASS_NAME);
            unwrapPages();
        }
    }

    function unwrapPages() {
        var sceneBlocks = getSceneBlocks();
        if (!sceneBlocks) {
            document.querySelectorAll('.' + PAGES_WRAP_CLASS).forEach(function (el) { el.remove(); });
            return;
        }
        var wrap = sceneBlocks.querySelector(':scope > .' + PAGES_WRAP_CLASS);
        if (!wrap) return;

        var fragment = document.createDocumentFragment();
        var pages = wrap.querySelectorAll(':scope > .' + PAGE_CLASS);
        for (var i = 0; i < pages.length; i++) {
            var body = pages[i].querySelector(':scope > .' + PAGE_BODY_CLASS);
            if (!body) continue;
            while (body.firstChild) {
                fragment.appendChild(body.firstChild);
            }
        }
        wrap.replaceWith(fragment);
    }

    function createPage(pageNum) {
        var page = document.createElement('div');
        page.className = PAGE_CLASS;
        page.setAttribute('data-page', String(pageNum));
        page.setAttribute('role', 'region');
        page.setAttribute('aria-label', 'Page ' + pageNum);

        var body = document.createElement('div');
        body.className = PAGE_BODY_CLASS;

        var number = document.createElement('div');
        number.className = PAGE_NUMBER_CLASS;
        number.setAttribute('aria-hidden', 'true');
        number.textContent = String(pageNum);

        page.appendChild(body);
        page.appendChild(number);
        return { page: page, body: body };
    }

    function measureUsableHeight(probeParent) {
        var probe = document.createElement('div');
        probe.className = PAGE_CLASS + ' screenplay-page--measure';
        probe.setAttribute('aria-hidden', 'true');
        var probeBody = document.createElement('div');
        probeBody.className = PAGE_BODY_CLASS;
        probe.appendChild(probeBody);
        probeParent.appendChild(probe);
        var pageStyle = window.getComputedStyle(probe);
        var padTop = parseFloat(pageStyle.paddingTop) || 0;
        var padBottom = parseFloat(pageStyle.paddingBottom) || 0;
        var borderTop = parseFloat(pageStyle.borderTopWidth) || 0;
        var borderBottom = parseFloat(pageStyle.borderBottomWidth) || 0;
        var totalHeight = probe.offsetHeight;
        probe.remove();
        var usable = totalHeight - padTop - padBottom - borderTop - borderBottom;
        return Math.max(usable, 200);
    }

    function isForcedBreak(el) {
        return el && el.getAttribute && el.getAttribute('data-block-type') === 'PAGE_BREAK';
    }

    function isChromeRow(el) {
        if (!el || !el.classList) return false;
        return el.classList.contains('project-script-select-row')
            || el.classList.contains('hide-in-reader-view')
            || el.classList.contains('filtered-out')
            || (!el.getAttribute('data-block-id') && el.querySelector && el.querySelector('.block-input-textarea, .create-below, .project-create-inline'));
    }

    function isSearchFilteredOut(el) {
        return !!(el && el.classList && el.classList.contains('filtered-out'));
    }

    function updateRealPageCount(count) {
        var el = document.getElementById('project-script-stats');
        if (!el) return;
        var pagesEl = el.querySelector('[data-stat="pages"]');
        if (pagesEl) pagesEl.textContent = String(count);
        el.title = 'Word count and page view count';
    }

    function restoreWordPageEstimate() {
        var el = document.getElementById('project-script-stats');
        if (el) {
            el.title = 'Word count and estimated pages (≈250 words/page)';
        }
        if (typeof window.scriptyRefreshScriptStats === 'function') {
            window.scriptyRefreshScriptStats();
        }
    }

    /**
     * Moving block rows during pagination blurs focused textareas/contenteditables
     * (caret disappears). Capture and restore the editing caret across reflows.
     */
    function captureEditState() {
        var active = document.activeElement;
        if (!active || !active.closest) return null;
        if (!active.closest('.project-script .block-row, .project-script tr[data-block-id]')) {
            return null;
        }

        var state = {
            el: active,
            selectionStart: null,
            selectionEnd: null,
            contentEditableOffset: null
        };

        if (typeof active.selectionStart === 'number' && typeof active.selectionEnd === 'number') {
            state.selectionStart = active.selectionStart;
            state.selectionEnd = active.selectionEnd;
            return state;
        }

        if (active.isContentEditable && window.getSelection) {
            var selection = window.getSelection();
            if (selection && selection.rangeCount && selection.anchorNode && active.contains(selection.anchorNode)) {
                try {
                    var pre = document.createRange();
                    pre.selectNodeContents(active);
                    pre.setEnd(selection.anchorNode, selection.anchorOffset);
                    state.contentEditableOffset = pre.toString().length;
                } catch (err) { /* ignore */ }
            }
        }

        return state;
    }

    function restoreContentEditableOffset(el, offset) {
        if (!el || offset == null || !window.getSelection) return;
        var remaining = Math.max(0, offset);
        var walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
        var node;
        var range = document.createRange();
        var placed = false;

        while ((node = walker.nextNode())) {
            var len = node.textContent.length;
            if (remaining <= len) {
                range.setStart(node, remaining);
                placed = true;
                break;
            }
            remaining -= len;
        }

        if (!placed) {
            range.selectNodeContents(el);
            range.collapse(false);
        } else {
            range.collapse(true);
        }

        var selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
    }

    function restoreEditState(state) {
        if (!state || !state.el || !state.el.isConnected) return;
        try {
            state.el.focus({ preventScroll: true });
        } catch (err) {
            try { state.el.focus(); } catch (err2) { return; }
        }

        if (typeof state.selectionStart === 'number' && typeof state.el.setSelectionRange === 'function') {
            try {
                var len = state.el.value != null ? state.el.value.length : 0;
                var start = Math.max(0, Math.min(state.selectionStart, len));
                var end = Math.max(start, Math.min(
                    state.selectionEnd != null ? state.selectionEnd : state.selectionStart,
                    len
                ));
                state.el.setSelectionRange(start, end);
            } catch (err) { /* ignore */ }
        } else if (state.contentEditableOffset != null) {
            restoreContentEditableOffset(state.el, state.contentEditableOffset);
        }

        if (typeof window.scriptyRepositionBlockCaretPreview === 'function') {
            try { window.scriptyRepositionBlockCaretPreview(); } catch (err) { /* ignore */ }
        }
    }

    function createWrap(sceneBlocks) {
        var wrap = document.createElement('div');
        wrap.className = PAGES_WRAP_CLASS;
        if (!enterAnimPending) {
            wrap.classList.add(PAGES_SETTLED_CLASS);
        }
        sceneBlocks.appendChild(wrap);
        return wrap;
    }

    /**
     * Put every row into a single page body so heights are measured at page
     * width (rows sitting in .scene-blocks are laid out at the wider continuous
     * width and would break onto the wrong pages).
     */
    function reseedWrap(sceneBlocks) {
        unwrapPages();
        var rows = Array.prototype.slice.call(sceneBlocks.children);
        var wrap = createWrap(sceneBlocks);
        var seed = createPage(1);
        wrap.appendChild(seed.page);
        for (var i = 0; i < rows.length; i++) {
            seed.body.appendChild(rows[i]);
        }
        return wrap;
    }

    function pageBodiesOf(wrap) {
        return wrap.querySelectorAll(':scope > .' + PAGE_CLASS + ' > .' + PAGE_BODY_CLASS);
    }

    /**
     * Ordered rows already living inside the wrap, or null when rows escaped it
     * (a freshly inserted create-below row, say) and a full reseed is needed to
     * recover document order.
     */
    function collectRows(sceneBlocks, wrap) {
        for (var i = 0; i < sceneBlocks.children.length; i++) {
            if (sceneBlocks.children[i] !== wrap) return null;
        }
        var rows = [];
        var bodies = pageBodiesOf(wrap);
        for (var b = 0; b < bodies.length; b++) {
            for (var c = 0; c < bodies[b].children.length; c++) {
                rows.push(bodies[b].children[c]);
            }
        }
        return rows;
    }

    /**
     * The `:first-child` and `.project-script-select-row + .block-row` rules in
     * scripty.css zero a row's top margin, so a row in that position cannot
     * report the margin it would carry mid-page.
     */
    function sitsAtPageBodyTop(el) {
        var parent = el.parentElement;
        if (!parent || !parent.classList || !parent.classList.contains(PAGE_BODY_CLASS)) return false;
        if (parent.firstElementChild === el) return true;
        var prev = el.previousElementSibling;
        return !!(prev && prev.classList && prev.classList.contains('project-script-select-row'));
    }

    /**
     * Measure every row in one batched read pass. Interleaving appendChild with
     * offsetHeight (as this used to) forces a synchronous layout per row, which
     * is what made typing stall on long scripts.
     */
    function measureRows(els) {
        var rows = [];
        var i;
        for (i = 0; i < els.length; i++) {
            var el = els[i];
            var filtered = isSearchFilteredOut(el);
            var style = window.getComputedStyle(el);
            rows.push({
                el: el,
                type: (el.getAttribute && el.getAttribute('data-block-type')) || '',
                forcedBreak: isForcedBreak(el),
                chrome: isChromeRow(el),
                filtered: filtered,
                // Search-filtered rows stay in the DOM (order-preserving) but
                // contribute no height while .filtered-out hides them.
                height: filtered ? 0 : (el.offsetHeight || 0),
                atTop: sitsAtPageBodyTop(el),
                rawMarginTop: parseFloat(style.marginTop) || 0,
                fontSize: parseFloat(style.fontSize) || 0
            });
        }

        // Prefer a live sample of each block type's mid-page top margin over the
        // hardcoded em values, so the CSS stays the single source of truth
        // whenever there is a row in a measurable position.
        var sampled = {};
        for (i = 0; i < rows.length; i++) {
            if (rows[i].filtered || rows[i].atTop || rows[i].rawMarginTop <= 0) continue;
            if (sampled[rows[i].type] == null) sampled[rows[i].type] = rows[i].rawMarginTop;
        }
        for (i = 0; i < rows.length; i++) {
            if (rows[i].filtered) {
                rows[i].marginTop = 0;
                continue;
            }
            if (sampled[rows[i].type] != null) {
                rows[i].marginTop = sampled[rows[i].type];
                continue;
            }
            var em = MARGIN_EM_BY_TYPE[rows[i].type];
            if (em == null) em = DEFAULT_MARGIN_EM;
            rows[i].marginTop = em * rows[i].fontSize;
        }
        return rows;
    }

    /** Greedy fill, computed purely from measurements — no layout reads. */
    function computeAssignment(rows, usableHeight) {
        var pages = [[]];
        var height = 0;

        function newPage() {
            pages.push([]);
            height = 0;
        }

        for (var i = 0; i < rows.length; i++) {
            var row = rows[i];

            if (row.forcedBreak) {
                // Page-break markers become the page boundary; hide their height
                // contribution.
                pages[pages.length - 1].push(row);
                if (i < rows.length - 1) newPage();
                continue;
            }

            var atPageTop = height === 0;
            var cost = row.height + (atPageTop ? 0 : row.marginTop);
            if (!atPageTop && height + cost > usableHeight + 0.5) {
                newPage();
                cost = row.height;
            }
            pages[pages.length - 1].push(row);
            height += cost;
        }
        return pages;
    }

    function groupHasVisibleContent(group) {
        for (var i = 0; i < group.length; i++) {
            if (group[i].forcedBreak || group[i].chrome) continue;
            return true;
        }
        return false;
    }

    /** Drop pages that only hold page-breaks or hidden chrome. */
    function mergeEmptyPages(pages) {
        // Leading empties fold forward so their rows keep document order.
        while (pages.length > 1 && !groupHasVisibleContent(pages[0])) {
            pages[1] = pages[0].concat(pages[1]);
            pages.shift();
        }
        while (pages.length > 1 && !groupHasVisibleContent(pages[pages.length - 1])) {
            pages[pages.length - 2] = pages[pages.length - 2].concat(pages[pages.length - 1]);
            pages.pop();
        }
        // Middle empties (e.g. search filtered out a whole page of blocks).
        for (var p = 1; p < pages.length - 1; ) {
            if (groupHasVisibleContent(pages[p])) {
                p += 1;
                continue;
            }
            pages[p - 1] = pages[p - 1].concat(pages[p]);
            pages.splice(p, 1);
        }
        return pages;
    }

    /**
     * Reconcile the existing page DOM toward `groups`, touching only rows whose
     * position actually changed. Returns true if anything moved — a reflow that
     * changes nothing must not disturb the caret at all.
     */
    function applyAssignment(wrap, groups) {
        var changed = false;
        var pages = wrap.querySelectorAll(':scope > .' + PAGE_CLASS);
        var bodies = [];
        var p, i;

        for (p = 0; p < groups.length; p++) {
            if (p < pages.length) {
                bodies.push(pages[p].querySelector(':scope > .' + PAGE_BODY_CLASS));
            } else {
                var made = createPage(p + 1);
                wrap.appendChild(made.page);
                bodies.push(made.body);
                changed = true;
            }
        }

        for (p = 0; p < groups.length; p++) {
            var body = bodies[p];
            var group = groups[p];
            for (i = 0; i < group.length; i++) {
                if (body.children[i] === group[i].el) continue;
                body.insertBefore(group[i].el, body.children[i] || null);
                changed = true;
            }
        }

        // Trim only after every page has claimed its rows — a row still sitting
        // past the end of page 3 may belong to page 5, which has yet to pull it.
        for (p = 0; p < groups.length; p++) {
            while (bodies[p].children.length > groups[p].length) {
                bodies[p].removeChild(bodies[p].lastChild);
                changed = true;
            }
        }

        pages = wrap.querySelectorAll(':scope > .' + PAGE_CLASS);
        for (p = pages.length - 1; p >= groups.length; p--) {
            pages[p].remove();
            changed = true;
        }

        if (changed) {
            var finalPages = wrap.querySelectorAll(':scope > .' + PAGE_CLASS);
            for (i = 0; i < finalPages.length; i++) {
                var num = i + 1;
                finalPages[i].setAttribute('data-page', String(num));
                finalPages[i].setAttribute('aria-label', 'Page ' + num);
                var label = finalPages[i].querySelector(':scope > .' + PAGE_NUMBER_CLASS);
                if (label) label.textContent = String(num);
            }
        }
        return changed;
    }

    function paginate() {
        if (measuring) return;
        if (!document.documentElement.classList.contains(CLASS_NAME)) return;
        // Re-parenting the focused textarea mid-composition cancels the pending
        // IME/dead-key input. Wait for compositionend.
        if (composing) {
            reflowPendingAfterCompose = true;
            return;
        }

        var sceneBlocks = getSceneBlocks();
        if (!sceneBlocks) return;

        var editState = captureEditState();
        measuring = true;
        try {
            var wrap = sceneBlocks.querySelector(':scope > .' + PAGES_WRAP_CLASS);
            var els = wrap ? collectRows(sceneBlocks, wrap) : null;
            if (!els) {
                wrap = reseedWrap(sceneBlocks);
                els = collectRows(sceneBlocks, wrap) || [];
            }

            if (!els.length) {
                wrap.remove();
                updateRealPageCount(0);
                return;
            }

            var usableHeight = measureUsableHeight(wrap);
            var groups = mergeEmptyPages(computeAssignment(measureRows(els), usableHeight));
            var changed = applyAssignment(wrap, groups);

            updateRealPageCount(groups.length);

            if (enterAnimPending) {
                enterAnimPending = false;
                // After enter animation finishes, mark settled so edit reflows stay still.
                window.setTimeout(function () {
                    var settledWrap = getSceneBlocks() && getSceneBlocks().querySelector(':scope > .' + PAGES_WRAP_CLASS);
                    if (settledWrap) settledWrap.classList.add(PAGES_SETTLED_CLASS);
                }, 520);
            }

            syncActivePage();
            if (changed) restoreEditState(editState);
        } finally {
            measuring = false;
        }
    }

    function clearActivePages() {
        document.querySelectorAll('.' + PAGE_CLASS + '.' + PAGE_ACTIVE_CLASS).forEach(function (el) {
            el.classList.remove(PAGE_ACTIVE_CLASS);
        });
    }

    function setActivePageFromEl(el) {
        clearActivePages();
        if (!el || !el.closest) return;
        var page = el.closest('.' + PAGE_CLASS);
        if (page && !page.classList.contains('screenplay-page--measure')) {
            page.classList.add(PAGE_ACTIVE_CLASS);
        }
    }

    function syncActivePage() {
        if (!window.scriptyIsPageViewMode()) {
            clearActivePages();
            return;
        }
        var active = document.activeElement;
        if (active && active.closest && active.closest('.project-script .' + PAGE_CLASS)) {
            setActivePageFromEl(active);
        }
    }

    function scheduleReflow(delay) {
        var wait = delay == null ? 80 : delay;
        var now = Date.now();
        if (!reflowTimer) {
            reflowDeadline = now + REFLOW_MAX_WAIT;
        } else if (now + wait > reflowDeadline) {
            // Steady typing keeps pushing the debounce out. Cap the deferral so
            // an overfull page reflows mid-burst, not only when the typist stops.
            wait = Math.max(0, reflowDeadline - now);
        }
        if (reflowTimer) clearTimeout(reflowTimer);
        reflowTimer = setTimeout(function () {
            reflowTimer = null;
            reflowDeadline = 0;
            paginate();
        }, wait);
    }

    window.scriptyIsPageViewMode = function () {
        return document.documentElement.classList.contains(CLASS_NAME);
    };

    window.scriptyReflowPageView = function () {
        if (window.scriptyIsPageViewMode()) scheduleReflow(0);
    };

    window.scriptySetPageViewMode = function (on, options) {
        var next = !!on;
        localStorage.setItem(STORAGE_KEY, next ? '1' : '0');
        apply(next);
        if (next && !(options && options.skipPeer) && typeof window.scriptySetOutlineMode === 'function') {
            window.scriptySetOutlineMode(false, { skipPeer: true });
        }
    };
    window.scriptyTogglePageViewMode = function () {
        window.scriptySetPageViewMode(!isOn());
    };

    document.body.addEventListener('click', function (e) {
        var btn = e.target && e.target.closest && e.target.closest('#page-view-mode-toggle');
        if (!btn) return;
        window.scriptySetPageViewMode(!isOn());
    });

    document.body.addEventListener('htmx:afterSwap', function () {
        if (window.scriptyIsPageViewMode()) scheduleReflow(50);
        else sync();
    });

    document.body.addEventListener('htmx:afterSettle', function () {
        if (window.scriptyIsPageViewMode()) scheduleReflow(50);
    });

    window.addEventListener('scripty:outline-mode-changed', function () {
        if (window.scriptyIsPageViewMode()) scheduleReflow(50);
    });

    window.addEventListener('scripty:text-size-changed', function () {
        if (window.scriptyIsPageViewMode()) scheduleReflow(50);
    });

    window.addEventListener('scripty:full-width-changed', function () {
        if (window.scriptyIsPageViewMode()) scheduleReflow(50);
    });

    window.addEventListener('resize', function () {
        if (window.scriptyIsPageViewMode()) scheduleReflow(150);
    });

    function pageBodyOverflows(page) {
        if (!page) return false;
        var body = page.querySelector(':scope > .' + PAGE_BODY_CLASS);
        if (!body) return false;
        return body.scrollHeight > body.clientHeight + 0.5;
    }

    /**
     * True when the page has shed enough content (a line or more) that rows from
     * the next page should move up. Without this, deleting text left a ragged
     * gap until the block lost focus.
     */
    function pageBodyCanPullUp(page) {
        if (!page) return false;
        var next = page.nextElementSibling;
        if (!next || !next.classList || !next.classList.contains(PAGE_CLASS)) return false;
        var body = page.querySelector(':scope > .' + PAGE_BODY_CLASS);
        if (!body) return false;
        // ~55 lines to a US Letter screenplay page, so this is roughly one line.
        var lineSlack = body.clientHeight / 55;
        return body.clientHeight - body.scrollHeight > lineSlack;
    }

    document.body.addEventListener('compositionstart', function () {
        composing = true;
    });

    document.body.addEventListener('compositionend', function () {
        composing = false;
        if (reflowPendingAfterCompose) {
            reflowPendingAfterCompose = false;
            if (window.scriptyIsPageViewMode()) scheduleReflow(60);
        }
    });

    document.body.addEventListener('input', function (e) {
        if (!window.scriptyIsPageViewMode()) return;
        if (e.isComposing || composing) return;
        var t = e.target;
        if (!t || !t.closest) return;
        if (!t.closest('.project-script .block-row')) return;
        // Avoid full unwrap/rewrap on every keystroke — that makes text jump.
        // Only reflow while typing when the page over- or underflows.
        window.requestAnimationFrame(function () {
            if (!t.isConnected) return;
            var page = t.closest('.' + PAGE_CLASS);
            if (!page || pageBodyOverflows(page) || pageBodyCanPullUp(page)) {
                scheduleReflow(200);
            }
        });
    });

    document.body.addEventListener('focusin', function (e) {
        if (!window.scriptyIsPageViewMode()) return;
        var t = e.target;
        if (!t || !t.closest) return;
        if (t.closest('.project-script .' + PAGE_CLASS)) {
            setActivePageFromEl(t);
        }
    });

    document.body.addEventListener('focusout', function (e) {
        if (!window.scriptyIsPageViewMode()) return;
        var t = e.target;
        // After editing, reflow so shortened blocks pull content up from later pages.
        if (t && t.closest && t.closest('.project-script .block-row') &&
            (t.name === 'content' || (t.classList && t.classList.contains('block-input-textarea')))) {
            scheduleReflow(80);
        }
        // Delay so focus moving within the same page doesn't flash.
        window.setTimeout(function () {
            var active = document.activeElement;
            if (!active || !active.closest || !active.closest('.project-script .' + PAGE_CLASS)) {
                clearActivePages();
            }
        }, 0);
    });

    document.body.addEventListener('pointerdown', function (e) {
        if (!window.scriptyIsPageViewMode()) return;
        var t = e.target;
        if (!t || !t.closest) return;
        var page = t.closest('.project-script .' + PAGE_CLASS);
        if (page) setActivePageFromEl(page);
    });

    // Re-paginate when block rows are added/removed outside HTMX settle paths.
    if (typeof MutationObserver !== 'undefined') {
        var moTimer = null;
        var observer = new MutationObserver(function () {
            if (!window.scriptyIsPageViewMode() || measuring) return;
            if (moTimer) clearTimeout(moTimer);
            moTimer = setTimeout(function () {
                moTimer = null;
                var sceneBlocks = getSceneBlocks();
                if (!sceneBlocks) return;
                // Only reflow if rows escaped the page wrappers (e.g. new create-below row).
                var loose = false;
                for (var i = 0; i < sceneBlocks.children.length; i++) {
                    var child = sceneBlocks.children[i];
                    if (!child.classList.contains(PAGES_WRAP_CLASS)) {
                        loose = true;
                        break;
                    }
                }
                if (loose) scheduleReflow(60);
            }, 80);
        });

        function observeScene() {
            var sceneBlocks = getSceneBlocks();
            if (sceneBlocks) {
                observer.observe(sceneBlocks, { childList: true, subtree: false });
            }
        }

        document.body.addEventListener('htmx:afterSettle', observeScene);
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', observeScene);
        } else {
            observeScene();
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sync);
    } else {
        sync();
    }
})();
