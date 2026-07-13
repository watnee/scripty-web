/**
 * Shared keyboard shortcut helpers for Scripty.
 */
(function () {
    'use strict';

    function isMac() {
        return /Mac|iPhone|iPod|iPad/i.test(navigator.platform || navigator.userAgent || '');
    }

    function isTypingContext(el) {
        if (typeof window.scriptyIsCharacterAutocompleteOpen === 'function' &&
            window.scriptyIsCharacterAutocompleteOpen()) {
            return true;
        }
        if (!el) return false;
        var tag = el.tagName;
        if (tag === 'TEXTAREA') return true;
        if (tag === 'INPUT') {
            var type = (el.type || 'text').toLowerCase();
            return type !== 'button' && type !== 'submit' && type !== 'checkbox' &&
                type !== 'radio' && type !== 'range' && type !== 'file';
        }
        if (el.closest && el.closest('#fountain-char-autocomplete')) return true;
        return !!el.isContentEditable;
    }

    function modLabel() {
        return isMac() ? '⌘' : 'Ctrl';
    }

    /**
     * Right-aligned shortcut hint inside a toolbar dropdown item.
     * Pass an empty/falsy hint to hide the span.
     */
    function setMenuShortcut(el, hint) {
        if (!el) return null;
        var shortcutEl = el.querySelector('.nav-dropdown-shortcut, .element-type-shortcut');
        if (!hint) {
            if (shortcutEl) {
                shortcutEl.textContent = '';
                shortcutEl.hidden = true;
            }
            return shortcutEl;
        }
        if (!shortcutEl) {
            shortcutEl = document.createElement('span');
            shortcutEl.className = 'nav-dropdown-shortcut';
            shortcutEl.setAttribute('aria-hidden', 'true');
            el.appendChild(shortcutEl);
        }
        shortcutEl.classList.add('nav-dropdown-shortcut');
        shortcutEl.textContent = hint;
        shortcutEl.hidden = false;
        return shortcutEl;
    }

    window.scriptyIsMac = isMac;
    window.scriptyModLabel = modLabel;
    window.scriptyIsTypingContext = isTypingContext;
    window.scriptySetMenuShortcut = setMenuShortcut;
})();
