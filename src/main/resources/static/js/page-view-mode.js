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
    var measuring = false;
    var enterAnimPending = false;

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
        var bodyStyle = window.getComputedStyle(probeBody);
        // Top/bottom margins live on the body (in cqw); the page itself only
        // contributes its border. Read both so either can carry the padding.
        var padTop = (parseFloat(pageStyle.paddingTop) || 0) + (parseFloat(bodyStyle.paddingTop) || 0);
        var padBottom = (parseFloat(pageStyle.paddingBottom) || 0) + (parseFloat(bodyStyle.paddingBottom) || 0);
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

    function rowLayoutHeight(el) {
        if (!el || isSearchFilteredOut(el)) return 0;
        var height = el.offsetHeight || 0;
        try {
            var style = window.getComputedStyle(el);
            height += (parseFloat(style.marginTop) || 0) + (parseFloat(style.marginBottom) || 0);
        } catch (err) { /* ignore */ }
        return height;
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

    function paginate() {
        if (measuring) return;
        if (!document.documentElement.classList.contains(CLASS_NAME)) return;

        var sceneBlocks = getSceneBlocks();
        if (!sceneBlocks) return;

        var editState = captureEditState();
        measuring = true;
        try {
            unwrapPages();

            var children = Array.prototype.slice.call(sceneBlocks.children);
            if (!children.length) {
                updateRealPageCount(0);
                return;
            }

            var wrap = document.createElement('div');
            wrap.className = PAGES_WRAP_CLASS;
            if (!enterAnimPending) {
                wrap.classList.add(PAGES_SETTLED_CLASS);
            }
            sceneBlocks.appendChild(wrap);

            var usableHeight = measureUsableHeight(wrap);
            var pageNum = 0;
            var current = null;
            var currentHeight = 0;

            function startPage() {
                pageNum += 1;
                current = createPage(pageNum);
                wrap.appendChild(current.page);
                currentHeight = 0;
            }

            startPage();

            for (var i = 0; i < children.length; i++) {
                var child = children[i];
                var forcedBreak = isForcedBreak(child);

                current.body.appendChild(child);
                // Search-filtered rows stay in the DOM (order-preserving) but
                // contribute no height while .filtered-out hides them.
                var height = rowLayoutHeight(child);

                if (!forcedBreak && currentHeight > 0 && currentHeight + height > usableHeight + 0.5) {
                    startPage();
                    current.body.appendChild(child);
                    height = rowLayoutHeight(child);
                }

                if (forcedBreak) {
                    // Page-break markers become the page boundary; hide their height contribution.
                    if (currentHeight === 0 && current.body.children.length === 1) {
                        // Leading page break: keep an empty page then continue.
                    }
                    if (i < children.length - 1) {
                        startPage();
                    }
                    continue;
                }

                currentHeight += height;
            }

            // Drop leading/trailing pages that only hold page-breaks or hidden chrome.
            function pageHasVisibleContent(body) {
                if (!body) return false;
                for (var c = 0; c < body.children.length; c++) {
                    var row = body.children[c];
                    if (isForcedBreak(row)) continue;
                    if (isChromeRow(row)) continue;
                    if (row.getAttribute && row.getAttribute('data-block-id')) return true;
                    return true;
                }
                return false;
            }

            var pages = wrap.querySelectorAll(':scope > .' + PAGE_CLASS);
            // Leading empties
            while (pages.length > 1) {
                var firstBody = pages[0].querySelector(':scope > .' + PAGE_BODY_CLASS);
                if (pageHasVisibleContent(firstBody)) break;
                var nextBody = pages[1].querySelector(':scope > .' + PAGE_BODY_CLASS);
                if (firstBody && nextBody) {
                    while (firstBody.firstChild) {
                        nextBody.insertBefore(firstBody.firstChild, nextBody.firstChild);
                    }
                }
                pages[0].remove();
                pages = wrap.querySelectorAll(':scope > .' + PAGE_CLASS);
            }
            // Trailing empties
            while (pages.length > 1) {
                var last = pages[pages.length - 1];
                var lastBody = last.querySelector(':scope > .' + PAGE_BODY_CLASS);
                if (pageHasVisibleContent(lastBody)) break;
                var prev = pages[pages.length - 2];
                var prevBody = prev.querySelector(':scope > .' + PAGE_BODY_CLASS);
                if (lastBody && prevBody) {
                    while (lastBody.firstChild) {
                        prevBody.appendChild(lastBody.firstChild);
                    }
                }
                last.remove();
                pages = wrap.querySelectorAll(':scope > .' + PAGE_CLASS);
            }
            // Middle empties (e.g. search filtered out a whole page of blocks)
            for (var p = 1; p < pages.length - 1; ) {
                var midBody = pages[p].querySelector(':scope > .' + PAGE_BODY_CLASS);
                if (pageHasVisibleContent(midBody)) {
                    p += 1;
                    continue;
                }
                var mergeInto = pages[p - 1].querySelector(':scope > .' + PAGE_BODY_CLASS);
                if (midBody && mergeInto) {
                    while (midBody.firstChild) {
                        mergeInto.appendChild(midBody.firstChild);
                    }
                }
                pages[p].remove();
                pages = wrap.querySelectorAll(':scope > .' + PAGE_CLASS);
            }

            // Renumber after cleanup.
            var finalPages = wrap.querySelectorAll(':scope > .' + PAGE_CLASS);
            for (var n = 0; n < finalPages.length; n++) {
                var num = n + 1;
                finalPages[n].setAttribute('data-page', String(num));
                finalPages[n].setAttribute('aria-label', 'Page ' + num);
                var label = finalPages[n].querySelector(':scope > .' + PAGE_NUMBER_CLASS);
                if (label) label.textContent = String(num);
            }

            updateRealPageCount(finalPages.length);

            if (enterAnimPending) {
                enterAnimPending = false;
                // After enter animation finishes, mark settled so edit reflows stay still.
                window.setTimeout(function () {
                    var settledWrap = getSceneBlocks() && getSceneBlocks().querySelector(':scope > .' + PAGES_WRAP_CLASS);
                    if (settledWrap) settledWrap.classList.add(PAGES_SETTLED_CLASS);
                }, 520);
            }

            syncActivePage();
        } finally {
            measuring = false;
            restoreEditState(editState);
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
        if (reflowTimer) clearTimeout(reflowTimer);
        reflowTimer = setTimeout(function () {
            reflowTimer = null;
            paginate();
        }, delay == null ? 80 : delay);
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

    document.body.addEventListener('input', function (e) {
        if (!window.scriptyIsPageViewMode()) return;
        var t = e.target;
        if (!t || !t.closest) return;
        if (!t.closest('.project-script .block-row')) return;
        // Avoid full unwrap/rewrap on every keystroke — that makes text jump.
        // Only reflow while typing when the current page actually overflows.
        window.requestAnimationFrame(function () {
            if (!t.isConnected) return;
            var page = t.closest('.' + PAGE_CLASS);
            if (!page || pageBodyOverflows(page)) {
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
