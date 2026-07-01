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

    applySize(getSize());
    updateButtons(getSize());

    var decreaseBtn = document.getElementById('text-size-decrease');
    var increaseBtn = document.getElementById('text-size-increase');

    if (decreaseBtn) {
        decreaseBtn.addEventListener('click', function () {
            var next = Math.max(MIN_SIZE, getSize() - STEP);
            saveSize(next);
            applySize(next);
            updateButtons(next);
        });
    }

    if (increaseBtn) {
        increaseBtn.addEventListener('click', function () {
            var next = Math.min(MAX_SIZE, getSize() + STEP);
            saveSize(next);
            applySize(next);
            updateButtons(next);
        });
    }
})();
