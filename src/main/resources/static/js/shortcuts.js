/**
 * Shared keyboard shortcut helpers for Scripty.
 */
(function () {
    'use strict';

    function isMac() {
        return /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
    }

    function isTypingContext(el) {
        if (!el) return false;
        var tag = el.tagName;
        if (tag === 'TEXTAREA') return true;
        if (tag === 'INPUT') {
            var type = (el.type || 'text').toLowerCase();
            return type !== 'button' && type !== 'submit' && type !== 'checkbox' &&
                type !== 'radio' && type !== 'range' && type !== 'file';
        }
        return !!el.isContentEditable;
    }

    function modLabel() {
        return isMac() ? '⌘' : 'Ctrl';
    }

    window.scriptyIsMac = isMac;
    window.scriptyModLabel = modLabel;
    window.scriptyIsTypingContext = isTypingContext;
})();
