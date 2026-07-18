/**
 * Parenthetical paren guard.
 *
 * Parentheticals are normally stored bare ("under her breath") and the
 * surrounding "(" / ")" are drawn by CSS pseudo-elements, so the same block
 * renders correctly everywhere. But nothing forces that on save, and imported
 * or legacy blocks can carry literal parens in their content — those rendered
 * as "((under her breath))" on screen while every export path (see the
 * `startsWith("(") && endsWith(")")` guard in PdfExportServiceImpl and friends)
 * correctly emitted a single pair.
 *
 * CSS cannot test text content, so mark the offenders with
 * data-has-parens="true" and let the stylesheet skip its pseudo-elements.
 * Loaded from nav.html so it survives HTMX-boosted navigation.
 */
(function () {
    'use strict';

    if (window._scriptyParenGuardInit) return;
    window._scriptyParenGuardInit = true;

    var SELECTOR = '.block-content.block-type-parenthetical';

    function textOf(content) {
        // The textarea holds the raw stored value while editing; the rendered
        // <p>/<span> holds it otherwise. Prefer whichever is present.
        var field = content.querySelector('textarea, .block-input-textarea');
        if (field && typeof field.value === 'string') return field.value;
        var display = content.querySelector('.script-block-text, .reader-visible-text');
        return display ? display.textContent || '' : '';
    }

    function mark(content) {
        var text = textOf(content).trim();
        var wrapped = text.length >= 2 && text.charAt(0) === '(' && text.charAt(text.length - 1) === ')';
        content.setAttribute('data-has-parens', wrapped ? 'true' : 'false');
    }

    function sync(root) {
        var scope = root && root.querySelectorAll ? root : document;
        var nodes = scope.querySelectorAll(SELECTOR);
        for (var i = 0; i < nodes.length; i++) {
            mark(nodes[i]);
        }
        // A swapped-in node can itself be the block-content.
        if (scope.matches && scope.matches(SELECTOR)) mark(scope);
    }

    window.scriptySyncParentheticalParens = sync;

    document.body.addEventListener('htmx:afterSwap', function (e) {
        sync(e && e.target);
    });
    document.body.addEventListener('htmx:afterSettle', function (e) {
        sync(e && e.target);
    });

    // Keep the marker honest while typing, so the parens do not double up the
    // moment a user types their own "(".
    document.body.addEventListener('input', function (e) {
        var content = e.target && e.target.closest && e.target.closest(SELECTOR);
        if (content) mark(content);
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { sync(document); });
    } else {
        sync(document);
    }
})();
