(function () {
    var STORAGE_KEY = 'scripty-outline-mode';

    function isOn() {
        return localStorage.getItem(STORAGE_KEY) === '1';
    }

    function apply(on) {
        document.documentElement.classList.toggle('scripty-outline-mode', on);
        document.body.classList.toggle('outline-mode', on);
        var btn = document.getElementById('outline-mode-toggle');
        if (btn) {
            btn.setAttribute('aria-pressed', on ? 'true' : 'false');
            btn.classList.toggle('is-active', on);
            btn.title = on ? 'Exit outline mode' : 'Show only scenes, sections, and synopses';
            btn.setAttribute('aria-label', btn.title);
        }
        try {
            window.dispatchEvent(new CustomEvent('scripty:outline-mode-changed', { detail: { on: on } }));
        } catch (err) { /* ignore */ }
    }

    function sync() {
        if (document.getElementById('outline-mode-toggle')) {
            apply(isOn());
        } else {
            document.documentElement.classList.remove('scripty-outline-mode');
            document.body.classList.remove('outline-mode');
        }
    }

    window.scriptyIsOutlineMode = function () {
        return document.documentElement.classList.contains('scripty-outline-mode');
    };

    window.scriptySetOutlineMode = function (on, options) {
        var next = !!on;
        localStorage.setItem(STORAGE_KEY, next ? '1' : '0');
        apply(next);
        if (next && !(options && options.skipPeer) && typeof window.scriptySetPageViewMode === 'function') {
            window.scriptySetPageViewMode(false, { skipPeer: true });
        }
    };

    var toggleBtn = document.getElementById('outline-mode-toggle');
    if (toggleBtn) {
        toggleBtn.addEventListener('click', function () {
            window.scriptySetOutlineMode(!isOn());
        });
    }

    document.body.addEventListener('htmx:afterSwap', sync);

    sync();
})();
