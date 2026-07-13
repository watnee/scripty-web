/**
 * Harden HTMX against executing scripts embedded in swapped HTML fragments.
 * Server templates already escape user text with th:text; this is defense in depth.
 */
(function () {
    'use strict';
    if (typeof htmx === 'undefined' || !htmx.config) {
        return;
    }
    htmx.config.allowScriptTags = false;
    htmx.config.allowEval = false;
})();
