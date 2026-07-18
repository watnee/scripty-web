/**
 * Page canvas zoom for screenplay page view.
 *
 * Scales the Letter sheet itself (not the type size) via --scripty-page-zoom,
 * so pagination stays honest: a zoomed page still holds exactly the same lines.
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyPageZoomInit) return;
    window._scriptyPageZoomInit = true;

    var STORAGE_KEY = 'scripty-page-zoom';
    var VAR_NAME = '--scripty-page-zoom';
    var FIT_VALUE = 'fit';
    var DEFAULT_ZOOM = 100;
    var MIN_ZOOM = 50;
    var MAX_ZOOM = 200;
    var STEP = 10;
    var fitTimer = null;

    function clamp(zoom) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.round(zoom)));
    }

    /** Stored value is either a percentage or the literal 'fit'. */
    function stored() {
        var raw = localStorage.getItem(STORAGE_KEY);
        if (raw === FIT_VALUE) return FIT_VALUE;
        var num = parseInt(raw, 10);
        return isNaN(num) ? DEFAULT_ZOOM : clamp(num);
    }

    function isFit() {
        return stored() === FIT_VALUE;
    }

    function getPagesWrap() {
        return document.querySelector('.project-script .screenplay-pages');
    }

    /**
     * Fit-to-width is resolved by measurement rather than arithmetic: the sheet's
     * unzoomed width depends on full-width mode, the mobile breakpoint and the
     * viewport, so we read it back at zoom 1 instead of hardcoding 10.5in.
     */
    function measureFitZoom() {
        var wrap = getPagesWrap();
        if (!wrap) return DEFAULT_ZOOM;
        var page = wrap.querySelector('.screenplay-page:not(.screenplay-page--measure)');
        if (!page) return DEFAULT_ZOOM;

        var previous = document.documentElement.style.getPropertyValue(VAR_NAME);
        document.documentElement.style.setProperty(VAR_NAME, '1');
        var baseWidth = page.getBoundingClientRect().width;
        if (previous) {
            document.documentElement.style.setProperty(VAR_NAME, previous);
        } else {
            document.documentElement.style.removeProperty(VAR_NAME);
        }

        if (!baseWidth) return DEFAULT_ZOOM;

        var wrapStyle = window.getComputedStyle(wrap);
        var available = wrap.clientWidth
            - (parseFloat(wrapStyle.paddingLeft) || 0)
            - (parseFloat(wrapStyle.paddingRight) || 0);
        if (available <= 0) return DEFAULT_ZOOM;

        // Floor, so a rounded-up fit never spills the sheet past the desk edge.
        return clamp(Math.floor((available / baseWidth) * 100));
    }

    function effectiveZoom() {
        return isFit() ? measureFitZoom() : stored();
    }

    function updateControls(zoom) {
        var label = document.getElementById('page-zoom-value');
        if (label) label.textContent = zoom + '%';

        var decrease = document.getElementById('page-zoom-decrease');
        var increase = document.getElementById('page-zoom-increase');
        if (decrease) decrease.disabled = zoom <= MIN_ZOOM;
        if (increase) increase.disabled = zoom >= MAX_ZOOM;

        var fitBtn = document.getElementById('page-zoom-fit');
        if (fitBtn) {
            var on = isFit();
            fitBtn.setAttribute('aria-pressed', on ? 'true' : 'false');
            fitBtn.classList.toggle('is-active', on);
        }

        var row = document.getElementById('page-zoom-control');
        if (row) {
            // Zoom only means anything while the page canvas is on screen.
            var active = !!(window.scriptyIsPageViewMode && window.scriptyIsPageViewMode());
            row.classList.toggle('is-disabled', !active);
            row.setAttribute('aria-disabled', active ? 'false' : 'true');
        }
    }

    function apply(options) {
        var zoom = effectiveZoom();
        document.documentElement.style.setProperty(VAR_NAME, String(zoom / 100));
        document.documentElement.classList.toggle('scripty-page-zoomed', zoom !== 100);
        updateControls(zoom);
        if (options && options.silent) return;
        try {
            window.dispatchEvent(new CustomEvent('scripty:page-zoom-changed', {
                detail: { zoom: zoom, fit: isFit() }
            }));
        } catch (err) { /* ignore */ }
    }

    function set(value) {
        localStorage.setItem(STORAGE_KEY, value === FIT_VALUE ? FIT_VALUE : String(clamp(value)));
        apply();
    }

    function nudge(delta) {
        // Stepping away from fit starts from whatever fit currently resolves to.
        set(clamp(effectiveZoom() + delta));
    }

    function sync() {
        if (document.getElementById('page-zoom-control')) {
            apply({ silent: true });
        } else {
            document.documentElement.style.removeProperty(VAR_NAME);
            document.documentElement.classList.remove('scripty-page-zoomed');
        }
    }

    window.scriptyGetPageZoom = effectiveZoom;
    window.scriptyIsPageZoomFit = isFit;
    window.scriptySetPageZoom = set;
    window.scriptyResetPageZoom = function () { set(DEFAULT_ZOOM); };
    window.scriptyFitPageZoom = function () { set(FIT_VALUE); };

    document.body.addEventListener('click', function (e) {
        if (!e.target || !e.target.closest) return;
        if (e.target.closest('#page-zoom-decrease')) { nudge(-STEP); return; }
        if (e.target.closest('#page-zoom-increase')) { nudge(STEP); return; }
        if (e.target.closest('#page-zoom-fit')) {
            // Second press on an active Fit returns to 100%.
            set(isFit() ? DEFAULT_ZOOM : FIT_VALUE);
        }
    });

    // Fit depends on the rendered sheet, so re-resolve it when the layout moves.
    function refitSoon(delay) {
        if (!isFit()) return;
        if (fitTimer) clearTimeout(fitTimer);
        fitTimer = setTimeout(function () {
            fitTimer = null;
            apply({ silent: true });
        }, delay);
    }

    window.addEventListener('resize', function () { refitSoon(150); });
    window.addEventListener('scripty:full-width-changed', function () { refitSoon(60); });
    window.addEventListener('scripty:page-view-mode-changed', function () {
        updateControls(effectiveZoom());
        refitSoon(60);
    });
    window.addEventListener('scripty:page-setup-changed', function () { refitSoon(60); });

    document.body.addEventListener('htmx:afterSwap', sync);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sync);
    } else {
        sync();
    }
})();
