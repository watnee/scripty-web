/**
 * Per-element auto-capitalization toggles for the screenplay editor.
 *
 * Unlike the other view toggles, this preference is stored server-side: exports
 * (PDF/DOCX/EPUB/FDX/Fountain) bake the case into the file, so the server has to
 * know it. localStorage is used only as an optimistic mirror so the menu renders
 * correctly before the fetch lands.
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyAutoCapsInit) return;
    window._scriptyAutoCapsInit = true;

    var TYPES = ['scene', 'character', 'transition', 'shot'];
    var STORAGE_KEY = 'scripty-auto-caps';
    var ENDPOINT = '/api/preferences/capitalization';

    // Server-rendered on /project/show; falls back to all-on (historic behavior).
    var state = readCached();

    function defaults() {
        return { scene: true, character: true, transition: true, shot: true };
    }

    function normalize(raw) {
        var next = defaults();
        if (!raw || typeof raw !== 'object') return next;
        TYPES.forEach(function (type) {
            if (typeof raw[type] === 'boolean') next[type] = raw[type];
        });
        return next;
    }

    function readCached() {
        if (window.scriptyAutoCaps) return normalize(window.scriptyAutoCaps);
        try {
            return normalize(JSON.parse(localStorage.getItem(STORAGE_KEY)));
        } catch (err) {
            return defaults();
        }
    }

    function cache() {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
        } catch (err) { /* private mode — server copy is authoritative anyway */ }
    }

    function apply() {
        var root = document.documentElement;
        TYPES.forEach(function (type) {
            root.classList.toggle('scripty-no-caps-' + type, !state[type]);
        });
        document.querySelectorAll('.auto-caps-item').forEach(function (btn) {
            var type = btn.getAttribute('data-auto-caps');
            if (TYPES.indexOf(type) === -1) return;
            var on = !!state[type];
            btn.classList.toggle('is-active', on);
            btn.setAttribute('aria-checked', on ? 'true' : 'false');
        });
        try {
            window.dispatchEvent(new CustomEvent('scripty:auto-caps-changed', {
                detail: { caps: Object.assign({}, state) }
            }));
        } catch (err) { /* ignore */ }
    }

    function persist(type) {
        var payload = {};
        payload[type] = state[type];
        fetch(ENDPOINT, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify(payload)
        }).then(function (res) {
            if (!res.ok) throw new Error('HTTP ' + res.status);
            return res.json();
        }).then(function (saved) {
            state = normalize(saved);
            cache();
            apply();
        }).catch(function () {
            // Roll back so the menu never claims a preference the server rejected.
            state[type] = !state[type];
            cache();
            apply();
        });
    }

    window.scriptyGetAutoCaps = function () {
        return Object.assign({}, state);
    };

    window.scriptySetAutoCaps = function (type, on) {
        if (TYPES.indexOf(type) === -1) return;
        state[type] = !!on;
        cache();
        apply();
        persist(type);
    };

    function sync() {
        if (!document.getElementById('project-auto-caps-dropdown')) {
            // Off the editor: drop the classes so other pages render normally.
            TYPES.forEach(function (type) {
                document.documentElement.classList.remove('scripty-no-caps-' + type);
            });
            return;
        }
        state = readCached();
        apply();
    }

    document.body.addEventListener('click', function (e) {
        var btn = e.target && e.target.closest && e.target.closest('.auto-caps-item');
        if (!btn) return;
        var type = btn.getAttribute('data-auto-caps');
        if (TYPES.indexOf(type) === -1) return;
        window.scriptySetAutoCaps(type, !state[type]);
    });

    document.body.addEventListener('htmx:afterSwap', sync);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sync);
    } else {
        sync();
    }
})();
