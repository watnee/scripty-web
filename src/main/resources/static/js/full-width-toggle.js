(function () {
    var STORAGE_KEY = 'scripty-screenplay-full-width';
    var CLASS_NAME = 'scripty-screenplay-full-width';

    function isOn() {
        return localStorage.getItem(STORAGE_KEY) === 'true';
    }

    function apply(on) {
        document.documentElement.classList.toggle(CLASS_NAME, on);
        var btn = document.getElementById('nav-full-width-toggle');
        if (btn) {
            btn.setAttribute('aria-pressed', on ? 'true' : 'false');
            btn.classList.toggle('is-active', on);
            btn.title = on ? 'Use standard screenplay width' : 'Use full page width';
            btn.setAttribute('aria-label', btn.title);
        }
        try {
            window.dispatchEvent(new CustomEvent('scripty:full-width-changed', { detail: { on: on } }));
        } catch (err) { /* ignore */ }
    }

    function sync() {
        if (document.getElementById('nav-full-width-toggle')) {
            apply(isOn());
        } else {
            document.documentElement.classList.remove(CLASS_NAME);
        }
    }

    var toggleBtn = document.getElementById('nav-full-width-toggle');
    if (toggleBtn) {
        toggleBtn.addEventListener('click', function () {
            var next = !isOn();
            localStorage.setItem(STORAGE_KEY, next ? 'true' : 'false');
            apply(next);
        });
    }

    document.body.addEventListener('htmx:afterSwap', sync);

    sync();
})();
