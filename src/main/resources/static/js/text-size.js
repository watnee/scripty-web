/**
 * Screenplay text size controls.
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyTextSizeInit) return;
    window._scriptyTextSizeInit = true;

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

    function sync() {
        var size = getSize();
        applySize(size);
        updateButtons(size);
        var isMac = window.scriptyIsMac ? window.scriptyIsMac() : /Mac|iPhone|iPod|iPad/i.test(navigator.userAgent);
        var modHint = isMac ? '⌘' : 'Ctrl';
        var decreaseBtn = document.getElementById('text-size-decrease');
        var increaseBtn = document.getElementById('text-size-increase');
        if (decreaseBtn) decreaseBtn.title = 'Decrease text size (' + modHint + '−)';
        if (increaseBtn) increaseBtn.title = 'Increase text size (' + modHint + '+)';
    }

    document.body.addEventListener('click', function (e) {
        var t = e.target && e.target.closest && e.target.closest('#text-size-decrease, #text-size-increase');
        if (!t) return;
        if (t.id === 'text-size-decrease') changeSize(-1);
        else if (t.id === 'text-size-increase') changeSize(1);
    });

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

    document.body.addEventListener('htmx:afterSwap', sync);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sync);
    } else {
        sync();
    }
})();
