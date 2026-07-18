/**
 * Song editor Tools menu: live word count over the lyric textareas.
 *
 * The Tools dropdown itself opens/closes via nav-dropdowns.js, and the word-count
 * visibility toggle (#nav-word-count-toggle) is handled by word-count-toggle.js
 * (shared with the screenplay). This file only keeps #song-word-count's total in
 * sync with the lyric block textareas. Loaded from nav.html so it survives
 * HTMX-boosted navigation (edit.html script tags do not run after a boost).
 */
(function () {
    'use strict';

    if (window._scriptySongToolsMenuInit) return;
    window._scriptySongToolsMenuInit = true;

    var WORD_RE = /\S+/g;
    var observer = null;

    function formatWordCount(n) {
        return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    }

    function countWords() {
        var total = 0;
        document.querySelectorAll('.song-block-textarea').forEach(function (ta) {
            var matches = (ta.value || '').match(WORD_RE);
            if (matches) total += matches.length;
        });
        return total;
    }

    function render() {
        var el = document.getElementById('song-word-count');
        if (!el) return;
        var wordsEl = el.querySelector('[data-stat="words"]');
        if (wordsEl) wordsEl.textContent = formatWordCount(countWords());
    }

    var timer = null;
    function scheduleRender() {
        if (timer) return;
        timer = setTimeout(function () {
            timer = null;
            render();
        }, 200);
    }

    // Watch the lyric container so adding/removing lines (however song-blocks.js
    // mutates the DOM) keeps the total current, not just typing.
    function observeBlocks() {
        var container = document.getElementById('song-blocks');
        if (!container) return;
        if (observer) observer.disconnect();
        observer = new MutationObserver(scheduleRender);
        observer.observe(container, { childList: true, subtree: true });
    }

    function sync() {
        observeBlocks();
        render();
    }

    document.body.addEventListener('input', function (e) {
        var target = e.target;
        if (target && target.classList && target.classList.contains('song-block-textarea')) {
            scheduleRender();
        }
    });

    document.body.addEventListener('htmx:afterSettle', sync);
    document.body.addEventListener('htmx:afterSwap', sync);
    document.body.addEventListener('htmx:historyRestore', sync);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', sync);
    } else {
        sync();
    }
})();
