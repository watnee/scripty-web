(function () {
    // Block content is stored and rendered as plain text, so styles are applied
    // with Unicode characters (math alphanumerics, combining marks) that survive
    // saving and display without any markup.

    function buildRangeMap(map, firstChar, lastChar, targetStart) {
        var first = firstChar.charCodeAt(0);
        var last = lastChar.charCodeAt(0);
        for (var i = 0; i <= last - first; i++) {
            map[String.fromCharCode(first + i)] = String.fromCodePoint(targetStart + i);
        }
        return map;
    }

    function reverse(map) {
        var rev = {};
        Object.keys(map).forEach(function (key) { rev[map[key]] = key; });
        return rev;
    }

    var BOLD = {};
    buildRangeMap(BOLD, 'A', 'Z', 0x1D5D4); // mathematical sans-serif bold
    buildRangeMap(BOLD, 'a', 'z', 0x1D5EE);
    buildRangeMap(BOLD, '0', '9', 0x1D7EC);
    var BOLD_REV = reverse(BOLD);

    var ITALIC = {};
    buildRangeMap(ITALIC, 'A', 'Z', 0x1D608); // mathematical sans-serif italic
    buildRangeMap(ITALIC, 'a', 'z', 0x1D622);
    var ITALIC_REV = reverse(ITALIC);

    var SUPERSCRIPT = {
        '0': '⁰', '1': '¹', '2': '²', '3': '³', '4': '⁴',
        '5': '⁵', '6': '⁶', '7': '⁷', '8': '⁸', '9': '⁹',
        '+': '⁺', '-': '⁻', '=': '⁼', '(': '⁽', ')': '⁾',
        'a': 'ᵃ', 'b': 'ᵇ', 'c': 'ᶜ', 'd': 'ᵈ', 'e': 'ᵉ',
        'f': 'ᶠ', 'g': 'ᵍ', 'h': 'ʰ', 'i': 'ⁱ', 'j': 'ʲ',
        'k': 'ᵏ', 'l': 'ˡ', 'm': 'ᵐ', 'n': 'ⁿ', 'o': 'ᵒ',
        'p': 'ᵖ', 'r': 'ʳ', 's': 'ˢ', 't': 'ᵗ', 'u': 'ᵘ',
        'v': 'ᵛ', 'w': 'ʷ', 'x': 'ˣ', 'y': 'ʸ', 'z': 'ᶻ',
        'A': 'ᴬ', 'B': 'ᴮ', 'D': 'ᴰ', 'E': 'ᴱ', 'G': 'ᴳ',
        'H': 'ᴴ', 'I': 'ᴵ', 'J': 'ᴶ', 'K': 'ᴷ', 'L': 'ᴸ',
        'M': 'ᴹ', 'N': 'ᴺ', 'O': 'ᴼ', 'P': 'ᴾ', 'R': 'ᴿ',
        'T': 'ᵀ', 'U': 'ᵁ', 'V': 'ⱽ', 'W': 'ᵂ'
    };
    var SUPERSCRIPT_REV = reverse(SUPERSCRIPT);

    var SUBSCRIPT = {
        '0': '₀', '1': '₁', '2': '₂', '3': '₃', '4': '₄',
        '5': '₅', '6': '₆', '7': '₇', '8': '₈', '9': '₉',
        '+': '₊', '-': '₋', '=': '₌', '(': '₍', ')': '₎',
        'a': 'ₐ', 'e': 'ₑ', 'h': 'ₕ', 'i': 'ᵢ', 'j': 'ⱼ',
        'k': 'ₖ', 'l': 'ₗ', 'm': 'ₘ', 'n': 'ₙ', 'o': 'ₒ',
        'p': 'ₚ', 'r': 'ᵣ', 's': 'ₛ', 't': 'ₜ', 'u': 'ᵤ',
        'v': 'ᵥ', 'x': 'ₓ'
    };
    var SUBSCRIPT_REV = reverse(SUBSCRIPT);

    var UNDERLINE_MARK = '̲';
    var STRIKE_MARK = '̶';
    var COMBINING = new RegExp('[' + UNDERLINE_MARK + STRIKE_MARK + ']', 'g');

    function chars(text) {
        return Array.from(text);
    }

    function mapChars(text, map) {
        return chars(text).map(function (ch) { return map[ch] || ch; }).join('');
    }

    function hasMapped(text, revMap) {
        return chars(text).some(function (ch) { return revMap[ch] !== undefined; });
    }

    function toggleMapped(text, map, revMap) {
        return hasMapped(text, revMap) ? mapChars(text, revMap) : mapChars(text, map);
    }

    function toggleMark(text, mark) {
        if (text.indexOf(mark) !== -1) {
            return text.split(mark).join('');
        }
        return chars(text.replace(COMBINING, '')).map(function (ch) {
            return ch === '\n' ? ch : ch + mark;
        }).join('');
    }

    function mapCase(text, upper) {
        return chars(text).map(function (ch) {
            var caseFn = function (c) { return upper ? c.toUpperCase() : c.toLowerCase(); };
            if (BOLD_REV[ch]) return BOLD[caseFn(BOLD_REV[ch])] || caseFn(BOLD_REV[ch]);
            if (ITALIC_REV[ch]) return ITALIC[caseFn(ITALIC_REV[ch])] || caseFn(ITALIC_REV[ch]);
            return caseFn(ch);
        }).join('');
    }

    var TRANSFORMS = {
        bold: function (text) { return toggleMapped(text, BOLD, BOLD_REV); },
        italic: function (text) { return toggleMapped(text, ITALIC, ITALIC_REV); },
        underline: function (text) { return toggleMark(text, UNDERLINE_MARK); },
        strikethrough: function (text) { return toggleMark(text, STRIKE_MARK); },
        uppercase: function (text) { return mapCase(text, true); },
        lowercase: function (text) { return mapCase(text, false); },
        superscript: function (text) { return toggleMapped(text, SUPERSCRIPT, SUPERSCRIPT_REV); },
        subscript: function (text) { return toggleMapped(text, SUBSCRIPT, SUBSCRIPT_REV); }
    };

    var lastTextarea = null;

    document.addEventListener('focusin', function (e) {
        if (e.target.tagName === 'TEXTAREA') {
            lastTextarea = e.target;
        }
    });

    function findTarget() {
        var candidates = [];
        var active = document.activeElement;
        if (active && active.tagName === 'TEXTAREA') candidates.push(active);
        if (lastTextarea && document.body.contains(lastTextarea)) candidates.push(lastTextarea);
        // focus tracking can miss (e.g. autofocus fires before listeners, or
        // the window was unfocused), so consider every textarea as well
        document.querySelectorAll('textarea').forEach(function (t) { candidates.push(t); });
        for (var k = 0; k < candidates.length; k++) {
            if (candidates[k].selectionStart !== candidates[k].selectionEnd) return candidates[k];
        }
        return candidates[0] || null;
    }

    function applyStyle(style) {
        var transform = TRANSFORMS[style];
        var el = findTarget();
        if (!transform || !el) {
            alert('Click into a block and highlight some text first.');
            return;
        }
        var start = el.selectionStart;
        var end = el.selectionEnd;
        if (start === end) {
            alert('Highlight some text in the block first.');
            return;
        }
        var selected = el.value.substring(start, end);
        var styled = transform(selected);
        if (styled === selected) return;
        el.focus();
        el.setSelectionRange(start, end);
        // execCommand keeps the browser undo stack and fires the input event
        // that triggers the block's autosave, but it is deprecated and can
        // silently do nothing — fall back to setRangeText when it does
        var before = el.value;
        document.execCommand('insertText', false, styled);
        if (el.value === before) {
            el.setRangeText(styled, start, end, 'end');
            el.dispatchEvent(new Event('input', { bubbles: true }));
        }
        el.setSelectionRange(start, start + styled.length);
    }

    function init() {
        var dropdown = document.getElementById('text-style-dropdown');
        if (!dropdown) return;

        var toggleBtn = dropdown.querySelector('.nav-dropdown-toggle');

        toggleBtn.addEventListener('click', function (e) {
            e.stopPropagation();
            var isOpen = dropdown.classList.contains('open');
            document.querySelectorAll('.nav-dropdown').forEach(function (d) { d.classList.remove('open'); });
            toggleBtn.setAttribute('aria-expanded', String(!isOpen));
            if (!isOpen) {
                dropdown.classList.add('open');
            }
        });

        document.addEventListener('click', function (e) {
            if (!dropdown.contains(e.target)) {
                dropdown.classList.remove('open');
                toggleBtn.setAttribute('aria-expanded', 'false');
            }
        });

        dropdown.querySelectorAll('[data-text-style]').forEach(function (item) {
            item.addEventListener('click', function (e) {
                e.stopPropagation();
                dropdown.classList.remove('open');
                toggleBtn.setAttribute('aria-expanded', 'false');
                applyStyle(item.getAttribute('data-text-style'));
            });
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
