(function () {
    var STORAGE_KEY = 'scripty-text-size';
    var DEFAULT_SIZE = 100;
    var MIN_SIZE = 80;
    var MAX_SIZE = 140;
    var STEP = 10;

    function getSize() {
        var stored = localStorage.getItem(STORAGE_KEY);
        var size = stored ? parseInt(stored, 10) : DEFAULT_SIZE;
        if (isNaN(size)) return DEFAULT_SIZE;
        return Math.min(MAX_SIZE, Math.max(MIN_SIZE, size));
    }

    function applySize(size) {
        var main = document.querySelector('main');
        if (main) main.style.fontSize = size + '%';
        try {
            window.dispatchEvent(new CustomEvent('scripty:text-size-changed', { detail: { size: size } }));
        } catch (err) { /* ignore */ }
    }

    function saveSize(size) {
        localStorage.setItem(STORAGE_KEY, size);
    }

    function updateButtons(size) {
        var decreaseBtn = document.getElementById('text-size-decrease');
        var increaseBtn = document.getElementById('text-size-increase');
        if (decreaseBtn) decreaseBtn.disabled = size <= MIN_SIZE;
        if (increaseBtn) increaseBtn.disabled = size >= MAX_SIZE;
    }

    function changeSize(delta) {
        var next = delta > 0
            ? Math.min(MAX_SIZE, getSize() + STEP)
            : Math.max(MIN_SIZE, getSize() - STEP);
        if (next === getSize()) return;
        saveSize(next);
        applySize(next);
        updateButtons(next);
    }

    applySize(getSize());
    updateButtons(getSize());

    var decreaseBtn = document.getElementById('text-size-decrease');
    var increaseBtn = document.getElementById('text-size-increase');
    var isMac = window.scriptyIsMac ? window.scriptyIsMac() : /Mac|iPhone|iPod|iPad/i.test(navigator.userAgent);
    var modHint = isMac ? '⌘' : 'Ctrl';

    if (decreaseBtn) {
        decreaseBtn.title = 'Decrease text size (' + modHint + '−)';
        decreaseBtn.addEventListener('click', function () {
            changeSize(-1);
        });
    }

    if (increaseBtn) {
        increaseBtn.title = 'Increase text size (' + modHint + '+)';
        increaseBtn.addEventListener('click', function () {
            changeSize(1);
        });
    }

    document.addEventListener('keydown', function (e) {
        if (!(e.metaKey || e.ctrlKey) || e.altKey) return;
        var active = document.activeElement;
        if (window.scriptyIsTypingContext && window.scriptyIsTypingContext(active)) return;
        var key = e.key;
        if (key === '+' || key === '=') {
            e.preventDefault();
            changeSize(1);
        } else if (key === '-' || key === '_') {
            e.preventDefault();
            changeSize(-1);
        }
    });
})();
