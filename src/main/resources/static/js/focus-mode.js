(function () {
    var STORAGE_KEY = 'scripty-focus-mode';

    function isOn() {
        return localStorage.getItem(STORAGE_KEY) === '1';
    }

    function apply(on) {
        document.body.classList.toggle('focus-mode', on);
        var btn = document.getElementById('focus-toggle');
        if (btn) {
            btn.setAttribute('aria-pressed', on ? 'true' : 'false');
            btn.title = on ? 'Exit focus mode' : 'Hide distractions while writing';
        }
    }

    function sync() {
        if (document.getElementById('focus-toggle')) {
            apply(isOn());
        } else {
            document.body.classList.remove('focus-mode');
        }
    }

    var toggleBtn = document.getElementById('focus-toggle');
    if (toggleBtn) {
        toggleBtn.addEventListener('click', function () {
            var next = !isOn();
            localStorage.setItem(STORAGE_KEY, next ? '1' : '0');
            apply(next);
        });
    }

    // Boosted navigation swaps the body content but keeps the body element,
    // so drop the class when landing on a page without the toggle.
    document.body.addEventListener('htmx:afterSwap', sync);

    sync();
})();
