/**
 * Relative "last edited" timestamps on project list / headers.
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation.
 */
(function () {
    'use strict';

    if (window._scriptyLastEditedTimesInit) return;
    window._scriptyLastEditedTimesInit = true;

    function parseLastEditedTimestamp(timestamp) {
        if (!timestamp) return null;
        var normalized = timestamp.replace(/\.\d+/, '');
        var date = new Date(normalized);
        if (!isNaN(date.getTime())) return date;
        var match = normalized.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})/);
        if (!match) return null;
        date = new Date(+match[1], +match[2] - 1, +match[3], +match[4], +match[5], +match[6]);
        return isNaN(date.getTime()) ? null : date;
    }

    function updateLastEditedTimes() {
        document.querySelectorAll('.last-edited-time').forEach(function (el) {
            var timestamp = el.getAttribute('data-timestamp');
            var date = parseLastEditedTimestamp(timestamp);
            if (!date) return;

            var now = new Date();
            var diffMs = now - date;
            var diffSec = Math.floor(diffMs / 1000);
            var diffMin = Math.floor(diffSec / 60);
            var diffHr = Math.floor(diffMin / 60);
            var diffDays = Math.floor(diffHr / 24);

            var text = '';
            if (diffSec < 60) {
                text = 'just now';
            } else if (diffMin < 60) {
                text = diffMin + 'm ago';
            } else if (diffHr < 24) {
                text = diffHr + 'h ago';
            } else if (diffDays === 1) {
                text = 'yesterday';
            } else if (diffDays < 7) {
                text = diffDays + 'd ago';
            } else {
                text = date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
            }

            el.textContent = text;
        });
    }

    window.scriptyUpdateLastEditedTimes = updateLastEditedTimes;

    document.body.addEventListener('htmx:afterSwap', updateLastEditedTimes);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', updateLastEditedTimes);
    } else {
        updateLastEditedTimes();
    }
})();
