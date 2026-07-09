(function () {
    var STORAGE_KEY = 'scripty-page-view-mode';
    var CLASS_NAME = 'scripty-page-view-mode';
    var PAGES_WRAP_CLASS = 'screenplay-pages';
    var PAGE_CLASS = 'screenplay-page';
    var PAGE_BODY_CLASS = 'screenplay-page-body';
    var PAGE_NUMBER_CLASS = 'screenplay-page-number';
    var reflowTimer = null;
    var measuring = false;

    function isOn() {
        return localStorage.getItem(STORAGE_KEY) === '1';
    }

    function getToggleBtn() {
        return document.getElementById('page-view-mode-toggle');
    }

    function getSceneBlocks() {
        return document.querySelector('.project-script .scene-blocks');
    }

    function apply(on) {
        document.documentElement.classList.toggle(CLASS_NAME, on);
        var btn = getToggleBtn();
        if (btn) {
            btn.setAttribute('aria-pressed', on ? 'true' : 'false');
            btn.classList.toggle('is-active', on);
            btn.title = on ? 'Exit page view' : 'View screenplay as pages';
            btn.setAttribute('aria-label', btn.title);
        }
        if (on) {
            scheduleReflow(0);
        } else {
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
            || (!el.getAttribute('data-block-id') && el.querySelector && el.querySelector('.block-input-textarea, .create-below, .project-create-inline'));
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

    function paginate() {
        if (measuring) return;
        if (!document.documentElement.classList.contains(CLASS_NAME)) return;

        var sceneBlocks = getSceneBlocks();
        if (!sceneBlocks) return;

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
                var height = child.offsetHeight || 0;

                if (!forcedBreak && currentHeight > 0 && currentHeight + height > usableHeight + 0.5) {
                    startPage();
                    current.body.appendChild(child);
                    height = child.offsetHeight || 0;
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
        } finally {
            measuring = false;
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

    var toggleBtn = getToggleBtn();
    if (toggleBtn) {
        toggleBtn.addEventListener('click', function () {
            window.scriptySetPageViewMode(!isOn());
        });
    }

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

    document.body.addEventListener('input', function (e) {
        if (!window.scriptyIsPageViewMode()) return;
        var t = e.target;
        if (!t || !t.closest) return;
        if (t.closest('.project-script .block-row')) scheduleReflow(200);
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

    sync();
})();
