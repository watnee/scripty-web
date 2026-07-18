/**
 * Page navigator for screenplay page view.
 *
 * A floating pager showing "Page N of M" with prev/next and a jump-to-page
 * field. The bar is built in JS rather than in a template so it stays available
 * on every surface that turns page view on, without duplicated markup.
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyPageNavigatorInit) return;
    window._scriptyPageNavigatorInit = true;

    var BAR_ID = 'screenplay-page-nav';
    var PAGE_SELECTOR = '.project-script .screenplay-page:not(.screenplay-page--measure)';
    var current = 1;
    var total = 0;
    var spyFrame = null;
    var suppressSpyUntil = 0;

    function pages() {
        return Array.prototype.slice.call(document.querySelectorAll(PAGE_SELECTOR));
    }

    function inPageView() {
        return !!(window.scriptyIsPageViewMode && window.scriptyIsPageViewMode());
    }

    function buildBar() {
        var bar = document.createElement('div');
        bar.id = BAR_ID;
        bar.className = 'screenplay-page-nav';
        bar.setAttribute('role', 'navigation');
        bar.setAttribute('aria-label', 'Page navigation');
        bar.hidden = true;

        bar.innerHTML =
            '<button type="button" class="screenplay-page-nav-btn" data-page-nav="prev"' +
            ' aria-label="Previous page" title="Previous page">' +
            '<svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2"' +
            ' fill="none" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
            '<polyline points="15 18 9 12 15 6"></polyline></svg></button>' +
            '<span class="screenplay-page-nav-field">' +
            '<label class="screenplay-page-nav-label" for="screenplay-page-nav-input">Page</label>' +
            '<input id="screenplay-page-nav-input" class="screenplay-page-nav-input" type="text"' +
            ' inputmode="numeric" autocomplete="off" value="1" aria-label="Go to page">' +
            '<span class="screenplay-page-nav-total">of <span data-page-nav="total">0</span></span>' +
            '</span>' +
            '<button type="button" class="screenplay-page-nav-btn" data-page-nav="next"' +
            ' aria-label="Next page" title="Next page">' +
            '<svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2"' +
            ' fill="none" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
            '<polyline points="9 18 15 12 9 6"></polyline></svg></button>';

        document.body.appendChild(bar);
        return bar;
    }

    function getBar() {
        return document.getElementById(BAR_ID) || buildBar();
    }

    function render() {
        var bar = getBar();
        var show = inPageView() && total > 0;
        bar.hidden = !show;
        if (!show) return;

        var input = bar.querySelector('.screenplay-page-nav-input');
        // Don't fight the user while they are typing a destination.
        if (input && document.activeElement !== input) input.value = String(current);

        var totalEl = bar.querySelector('[data-page-nav="total"]');
        if (totalEl) totalEl.textContent = String(total);

        var prev = bar.querySelector('[data-page-nav="prev"]');
        var next = bar.querySelector('[data-page-nav="next"]');
        if (prev) prev.disabled = current <= 1;
        if (next) next.disabled = current >= total;
    }

    function refresh() {
        total = pages().length;
        if (current > total) current = total || 1;
        observePages();
        render();
    }

    function goTo(pageNum) {
        var all = pages();
        if (!all.length) return;
        var index = Math.max(1, Math.min(all.length, pageNum)) - 1;
        var target = all[index];
        if (!target) return;

        current = index + 1;
        // Scrolling fires the spy; ignore it briefly so an explicit jump wins.
        suppressSpyUntil = performance.now() + 700;
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
        render();
    }

    /**
     * Scroll-spy: the current page is the last one whose top has passed just
     * below the viewport top, so a page counts as "current" as soon as its
     * leading edge is in view rather than when it dominates the screen.
     */
    function updateFromScroll() {
        if (!inPageView()) return;
        if (performance.now() < suppressSpyUntil) return;

        var all = pages();
        if (!all.length) return;

        var threshold = window.innerHeight * 0.28;
        var found = 1;
        for (var i = 0; i < all.length; i++) {
            if (all[i].getBoundingClientRect().top <= threshold) found = i + 1;
            else break;
        }

        if (found !== current) {
            current = found;
            render();
        }
    }

    function onScroll() {
        if (spyFrame) return;
        spyFrame = window.requestAnimationFrame(function () {
            spyFrame = null;
            updateFromScroll();
        });
    }

    /**
     * The spy is driven by an IntersectionObserver rather than a scroll listener:
     * this page does not emit window scroll events in every layout (the script
     * body can be scrolled by containers that swallow them), whereas observer
     * callbacks fire off the compositor regardless of who did the scrolling.
     */
    var observer = typeof IntersectionObserver !== 'undefined'
        ? new IntersectionObserver(onScroll, {
            threshold: [0, 0.01, 0.25, 0.5, 0.75, 1]
        })
        : null;

    function observePages() {
        if (!observer) return;
        observer.disconnect();
        pages().forEach(function (page) { observer.observe(page); });
    }

    window.scriptyGoToPage = goTo;
    window.scriptyGetCurrentPage = function () { return current; };
    window.scriptyGetPageCount = function () { return total; };
    /** Force a resync — for callers that move the script without a scroll event. */
    window.scriptySyncPageFromScroll = updateFromScroll;

    document.body.addEventListener('click', function (e) {
        if (!e.target || !e.target.closest) return;
        if (e.target.closest('[data-page-nav="prev"]')) { goTo(current - 1); return; }
        if (e.target.closest('[data-page-nav="next"]')) { goTo(current + 1); }
    });

    document.body.addEventListener('keydown', function (e) {
        var input = e.target;
        if (!input || !input.classList || !input.classList.contains('screenplay-page-nav-input')) return;
        if (e.key === 'Enter') {
            e.preventDefault();
            var value = parseInt(input.value, 10);
            if (!isNaN(value)) goTo(value);
            else render();
            input.blur();
        } else if (e.key === 'Escape') {
            render();
            input.blur();
        }
    });

    document.body.addEventListener('blur', function (e) {
        if (e.target && e.target.classList && e.target.classList.contains('screenplay-page-nav-input')) {
            render();
        }
    }, true);

    // The paginator owns page identity; re-read the count whenever it reflows.
    window.addEventListener('scripty:pages-paginated', function (e) {
        total = (e.detail && e.detail.count) || pages().length;
        if (current > total) current = total || 1;
        // Pagination rebuilds the page elements, so the old observations are dead.
        observePages();
        render();
        updateFromScroll();
    });

    window.addEventListener('scripty:page-view-mode-changed', function () {
        if (!inPageView()) {
            total = 0;
            current = 1;
        }
        refresh();
    });

    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll, { passive: true });
    // Capture phase catches scrolls inside nested containers, which do not bubble.
    document.addEventListener('scroll', onScroll, { passive: true, capture: true });
    document.body.addEventListener('htmx:afterSettle', refresh);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', refresh);
    } else {
        refresh();
    }
})();
