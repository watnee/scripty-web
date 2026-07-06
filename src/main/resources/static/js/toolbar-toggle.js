(function () {
    var STORAGE_KEY = 'scripty-toolbar-hidden';

    function isHidden() {
        return localStorage.getItem(STORAGE_KEY) === 'true';
    }

    function apply(hidden) {
        document.documentElement.classList.toggle('scripty-toolbar-hidden', hidden);
        var btn = document.getElementById('nav-toolbar-toggle');
        if (btn) {
            btn.setAttribute('aria-pressed', hidden ? 'true' : 'false');
            btn.classList.toggle('is-active', hidden);
            btn.title = hidden ? 'Show toolbar' : 'Hide toolbar';
            btn.setAttribute('aria-label', btn.title);
        }
    }

    function sync() {
        if (document.getElementById('nav-toolbar-toggle')) {
            apply(isHidden());
        } else {
            document.documentElement.classList.remove('scripty-toolbar-hidden');
        }
    }

    var toggleBtn = document.getElementById('nav-toolbar-toggle');
    if (toggleBtn) {
        toggleBtn.addEventListener('click', function () {
            var next = !isHidden();
            localStorage.setItem(STORAGE_KEY, next ? 'true' : 'false');
            apply(next);
        });
    }

    document.body.addEventListener('htmx:afterSwap', sync);

    sync();
})();
