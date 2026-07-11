/**
 * Scripty custom spellcheck: Typo.js dictionary + suggestion popup for script blocks.
 * Respects the global spellcheck toggle (localStorage / data-spellcheck).
 */
(function() {
    'use strict';

    var DICT_BASE = '/dictionaries';
    var DEBOUNCE_MS = 280;
    var MAX_SUGGESTIONS = 6;
    var WORD_RE = /[A-Za-z][A-Za-z']*/g;

    var SCREENPLAY_ALLOW = {
        INT: true, EXT: true, EST: true, I: true, E: true,
        DAY: true, NIGHT: true, MORNING: true, EVENING: true, AFTERNOON: true,
        CONTINUOUS: true, LATER: true, MOMENTS: true, SAME: true,
        FADE: true, CUT: true, DISSOLVE: true, SMASH: true, MATCH: true,
        TO: true, BLACK: true, WHITE: true, SUPER: true, TITLE: true,
        V: true, O: true, OS: true, VO: true, OC: true, CONT: true, CONTD: true,
        POV: true, MOS: true, SFX: true, FX: true, ADR: true
    };

    var dictionary = null;
    var dictLoading = null;
    var popupEl = null;
    var popupIndex = -1;
    var popupTarget = null;
    var popupRange = null;
    var checkTimers = new WeakMap();
    var lastErrors = new WeakMap();
    var ignoredWords = Object.create(null);

    function isEnabled() {
        if (typeof window.scriptyIsSpellcheckEnabled === 'function') {
            return window.scriptyIsSpellcheckEnabled();
        }
        try {
            return localStorage.getItem('spellcheck') !== 'false';
        } catch (e) {
            return true;
        }
    }

    function isScriptPage() {
        return !!document.querySelector('.project-script');
    }

    function isBlockTextarea(el) {
        return !!(el && el.tagName === 'TEXTAREA' && el.name === 'content' &&
            el.classList.contains('block-input-textarea') &&
            el.closest('.project-script, .table-blocks-all, #table-blocks'));
    }

    function escapeHtml(s) {
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function loadDictionary() {
        if (dictionary && dictionary.loaded) {
            return Promise.resolve(dictionary);
        }
        if (dictLoading) return dictLoading;
        if (typeof Typo === 'undefined') {
            return Promise.reject(new Error('Typo.js not loaded'));
        }
        dictLoading = Promise.all([
            fetch(DICT_BASE + '/en_US/en_US.aff').then(function(r) {
                if (!r.ok) throw new Error('aff ' + r.status);
                return r.text();
            }),
            fetch(DICT_BASE + '/en_US/en_US.dic').then(function(r) {
                if (!r.ok) throw new Error('dic ' + r.status);
                return r.text();
            })
        ]).then(function(parts) {
            dictionary = new Typo('en_US', parts[0], parts[1]);
            dictLoading = null;
            return dictionary;
        }).catch(function(err) {
            dictLoading = null;
            throw err;
        });
        return dictLoading;
    }

    function buildAllowlist() {
        var allow = Object.create(null);
        var key;
        for (key in SCREENPLAY_ALLOW) {
            if (SCREENPLAY_ALLOW.hasOwnProperty(key)) allow[key] = true;
        }
        for (key in ignoredWords) {
            if (ignoredWords.hasOwnProperty(key)) allow[key] = true;
        }
        if (typeof window.scriptyGetSpellAllowlist === 'function') {
            try {
                var extra = window.scriptyGetSpellAllowlist() || [];
                extra.forEach(function(word) {
                    String(word || '').split(/[^A-Za-z']+/).forEach(function(part) {
                        if (part.length > 1) allow[part.toUpperCase()] = true;
                    });
                });
            } catch (e) { /* ignore */ }
        }
        return allow;
    }

    function tokenize(text) {
        var tokens = [];
        var re = new RegExp(WORD_RE.source, 'g');
        var m;
        while ((m = re.exec(text)) !== null) {
            tokens.push({ word: m[0], start: m.index, end: m.index + m[0].length });
        }
        return tokens;
    }

    function isMisspelled(word, allow) {
        if (!dictionary || !dictionary.loaded) return false;
        if (!word || word.length < 2) return false;
        if (/^[A-Z]{1,3}$/.test(word) && allow[word]) return false;
        if (allow[word.toUpperCase()]) return false;
        // Possessives: check base
        var base = word.replace(/'s$/i, '').replace(/s'$/i, '');
        if (base !== word && allow[base.toUpperCase()]) return false;
        try {
            if (dictionary.check(word)) return false;
            if (base !== word && dictionary.check(base)) return false;
            // ALL CAPS cues often fail case-sensitive checks — try title/lower
            if (word === word.toUpperCase() && word.length > 1) {
                var lower = word.toLowerCase();
                var titled = lower.charAt(0).toUpperCase() + lower.slice(1);
                if (dictionary.check(lower) || dictionary.check(titled)) return false;
                if (base !== word) {
                    var baseLower = base.toLowerCase();
                    if (dictionary.check(baseLower)) return false;
                }
            }
            return true;
        } catch (e) {
            return false;
        }
    }

    function findErrors(text) {
        var allow = buildAllowlist();
        var errors = [];
        tokenize(text).forEach(function(tok) {
            if (isMisspelled(tok.word, allow)) {
                errors.push(tok);
            }
        });
        return errors;
    }

    function suggestionsFor(word) {
        if (!dictionary || !dictionary.loaded) return [];
        try {
            var query = word;
            if (word === word.toUpperCase() && word.length > 1) {
                query = word.toLowerCase();
            }
            var list = dictionary.suggest(query, MAX_SUGGESTIONS) || [];
            if (word === word.toUpperCase()) {
                list = list.map(function(s) { return s.toUpperCase(); });
            } else if (word.charAt(0) === word.charAt(0).toUpperCase() &&
                       word.slice(1) === word.slice(1).toLowerCase()) {
                list = list.map(function(s) {
                    return s.charAt(0).toUpperCase() + s.slice(1);
                });
            }
            return list.slice(0, MAX_SUGGESTIONS);
        } catch (e) {
            return [];
        }
    }

    function ensureOverlay(form) {
        var overlay = form.querySelector('.scripty-spell-overlay');
        if (overlay) return overlay;
        overlay = document.createElement('div');
        overlay.className = 'scripty-spell-overlay script-block-text';
        overlay.setAttribute('aria-hidden', 'true');
        var textarea = form.querySelector('textarea.block-input-textarea[name="content"]');
        if (textarea && textarea.parentNode === form) {
            form.insertBefore(overlay, textarea);
        } else {
            form.appendChild(overlay);
        }
        return overlay;
    }

    function findReaderMirror(form) {
        if (!form) return null;
        var blockContent = form.closest('.block-content');
        if (blockContent) {
            var sibling = blockContent.querySelector(':scope > .reader-visible-text');
            if (sibling) return sibling;
        }
        return form.querySelector('.reader-visible-text');
    }

    function clearOverlay(form) {
        var overlay = form.querySelector('.scripty-spell-overlay');
        if (overlay) {
            overlay.innerHTML = '';
            overlay.hidden = true;
        }
        form.classList.remove('scripty-spell-active');
        var mirror = findReaderMirror(form);
        var ta = form.querySelector('textarea.block-input-textarea[name="content"]');
        if (mirror && ta && mirror.getAttribute('data-scripty-spell-mirror') === '1') {
            mirror.textContent = ta.value || '\u00a0';
            mirror.removeAttribute('data-scripty-spell-mirror');
        }
        if (ta) {
            ta.removeAttribute('data-scripty-spell');
            if (!isEnabled()) {
                ta.setAttribute('spellcheck', 'false');
                ta.spellcheck = false;
            } else if (typeof window.scriptyApplySpellcheck === 'function') {
                window.scriptyApplySpellcheck(ta, true);
            }
        }
    }

    function buildMarkedHtml(text, errors) {
        if (!errors.length) {
            return escapeHtml(text || '') || '\u00a0';
        }
        var html = '';
        var cursor = 0;
        errors.forEach(function(err) {
            if (err.start > cursor) {
                html += escapeHtml(text.slice(cursor, err.start));
            }
            html += '<span class="scripty-spell-error" data-start="' + err.start +
                '" data-end="' + err.end + '">' + escapeHtml(text.slice(err.start, err.end)) + '</span>';
            cursor = err.end;
        });
        if (cursor < text.length) {
            html += escapeHtml(text.slice(cursor));
        }
        return html || '\u00a0';
    }

    function renderOverlay(textarea, errors) {
        var form = textarea.closest('form');
        if (!form) return;
        if (!isEnabled() || !dictionary || !dictionary.loaded) {
            clearOverlay(form);
            return;
        }

        var text = textarea.value || '';
        var overlay = ensureOverlay(form);
        var mirror = findReaderMirror(form);
        form.classList.add('scripty-spell-active');
        textarea.setAttribute('data-scripty-spell', '1');

        var marked = buildMarkedHtml(text, errors);
        var focused = form.matches(':focus-within') || document.activeElement === textarea;

        // While focused: keep textarea ink + native caret. Custom marks live on the
        // mirror and show after blur (overlay stays hidden during edit).
        overlay.innerHTML = '';
        overlay.hidden = true;

        if (focused) {
            // Browser underlines while typing; custom marks return on blur
            textarea.setAttribute('spellcheck', 'true');
            textarea.spellcheck = true;
        } else {
            textarea.setAttribute('spellcheck', 'false');
            textarea.spellcheck = false;
        }

        if (mirror) {
            if (focused) {
                mirror.textContent = text || '\u00a0';
                mirror.removeAttribute('data-scripty-spell-mirror');
            } else {
                mirror.innerHTML = marked;
                mirror.setAttribute('data-scripty-spell-mirror', '1');
            }
        }
        lastErrors.set(textarea, errors);
    }

    function scheduleCheck(textarea) {
        if (!isBlockTextarea(textarea)) return;
        var prev = checkTimers.get(textarea);
        if (prev) clearTimeout(prev);
        checkTimers.set(textarea, setTimeout(function() {
            checkTimers.delete(textarea);
            runCheck(textarea);
        }, DEBOUNCE_MS));
    }

    function runCheck(textarea) {
        if (!isBlockTextarea(textarea) || !isEnabled()) {
            var form = textarea && textarea.closest('form');
            if (form) clearOverlay(form);
            return;
        }
        loadDictionary().then(function() {
            if (!isEnabled() || document.body.contains(textarea) === false) return;
            var errors = findErrors(textarea.value || '');
            renderOverlay(textarea, errors);
        }).catch(function() {
            /* dictionary unavailable — leave browser spellcheck alone */
        });
    }

    function wordAtOffset(text, offset) {
        if (offset < 0 || offset > text.length) return null;
        var tokens = tokenize(text);
        for (var i = 0; i < tokens.length; i++) {
            if (offset >= tokens[i].start && offset <= tokens[i].end) {
                return tokens[i];
            }
        }
        // Caret just after word
        for (var j = 0; j < tokens.length; j++) {
            if (offset === tokens[j].end) return tokens[j];
        }
        return null;
    }

    function ensurePopup() {
        if (popupEl) return popupEl;
        popupEl = document.createElement('ul');
        popupEl.id = 'scripty-spell-suggestions';
        popupEl.className = 'scripty-spell-suggestions hide-in-reader-view';
        popupEl.setAttribute('role', 'listbox');
        popupEl.hidden = true;
        document.body.appendChild(popupEl);

        popupEl.addEventListener('mousedown', function(e) {
            e.preventDefault();
            e.stopPropagation();
            var li = e.target.closest('li[data-suggestion], li[data-action]');
            if (!li) return;
            if (li.getAttribute('data-action') === 'ignore') {
                ignoreCurrentWord();
                return;
            }
            if (li.getAttribute('data-action') === 'delete') {
                deleteCurrentWord();
                return;
            }
            var suggestion = li.getAttribute('data-suggestion');
            if (suggestion != null) applySuggestion(suggestion);
        });
        return popupEl;
    }

    function hidePopup() {
        if (!popupEl) return;
        popupEl.hidden = true;
        popupEl.innerHTML = '';
        popupIndex = -1;
        popupTarget = null;
        popupRange = null;
    }

    function positionPopup(textarea, start, end) {
        var el = ensurePopup();
        var rect = textarea.getBoundingClientRect();
        // Approximate caret Y using line height and newlines before start
        var text = textarea.value || '';
        var before = text.slice(0, start);
        var lines = before.split('\n');
        var lineIndex = lines.length - 1;
        var cs = window.getComputedStyle(textarea);
        var lineHeight = parseFloat(cs.lineHeight);
        if (!lineHeight || isNaN(lineHeight)) {
            lineHeight = parseFloat(cs.fontSize) * 1.3 || 18;
        }
        var paddingTop = parseFloat(cs.paddingTop) || 0;
        var top = rect.top + window.scrollY + paddingTop + (lineIndex + 1) * lineHeight + 4 - textarea.scrollTop;
        var left = rect.left + window.scrollX;
        el.style.left = Math.round(left) + 'px';
        el.style.top = Math.round(top) + 'px';
        el.style.minWidth = Math.max(140, Math.round(rect.width * 0.35)) + 'px';
        void end;
    }

    function showSuggestions(textarea, token) {
        if (!token || !isEnabled()) {
            hidePopup();
            return;
        }
        var allow = buildAllowlist();
        if (!isMisspelled(token.word, allow)) {
            hidePopup();
            return;
        }
        var suggestions = suggestionsFor(token.word);
        var el = ensurePopup();
        popupTarget = textarea;
        popupRange = { start: token.start, end: token.end, word: token.word };
        popupIndex = suggestions.length ? 0 : -1;

        var items = suggestions.map(function(s, i) {
            return '<li role="option" data-suggestion="' + escapeHtml(s) + '" data-index="' + i + '"' +
                (i === popupIndex ? ' aria-selected="true" class="is-active"' : '') +
                '>' + escapeHtml(s) + '</li>';
        });
        items.push(
            '<li role="option" class="scripty-spell-ignore" data-action="ignore" data-index="' +
            suggestions.length + '">Ignore &ldquo;' + escapeHtml(token.word) + '&rdquo;</li>'
        );
        items.push(
            '<li role="option" class="scripty-spell-delete" data-action="delete" data-index="' +
            (suggestions.length + 1) + '">Delete &ldquo;' + escapeHtml(token.word) + '&rdquo;</li>'
        );
        el.innerHTML = items.join('');
        positionPopup(textarea, token.start, token.end);
        el.hidden = false;
    }

    function applySuggestion(suggestion) {
        var textarea = popupTarget;
        var range = popupRange;
        hidePopup();
        if (!textarea || !range || suggestion == null) return;
        var value = textarea.value || '';
        textarea.value = value.slice(0, range.start) + suggestion + value.slice(range.end);
        var caret = range.start + suggestion.length;
        textarea.focus();
        textarea.setSelectionRange(caret, caret);
        if (typeof window.scriptyGrowTextarea === 'function') {
            window.scriptyGrowTextarea(textarea);
        }
        textarea.dispatchEvent(new Event('input', { bubbles: true }));
        runCheck(textarea);
    }

    function ignoreCurrentWord() {
        var range = popupRange;
        var textarea = popupTarget;
        hidePopup();
        if (!range || !range.word) return;
        ignoredWords[range.word.toUpperCase()] = true;
        try {
            var stored = JSON.parse(localStorage.getItem('scripty-spell-ignored') || '{}');
            stored[range.word.toUpperCase()] = true;
            localStorage.setItem('scripty-spell-ignored', JSON.stringify(stored));
        } catch (e) { /* ignore */ }
        if (textarea) runCheck(textarea);
    }

    function deleteCurrentWord() {
        var range = popupRange;
        var textarea = popupTarget;
        hidePopup();
        if (!range || !textarea) return;
        var value = textarea.value || '';
        textarea.value = value.slice(0, range.start) + value.slice(range.end);
        textarea.focus();
        textarea.setSelectionRange(range.start, range.start);
        if (typeof window.scriptyGrowTextarea === 'function') {
            window.scriptyGrowTextarea(textarea);
        }
        textarea.dispatchEvent(new Event('input', { bubbles: true }));
        runCheck(textarea);
    }

    function loadIgnored() {
        try {
            var stored = JSON.parse(localStorage.getItem('scripty-spell-ignored') || '{}');
            var key;
            for (key in stored) {
                if (stored.hasOwnProperty(key) && stored[key]) ignoredWords[key] = true;
            }
        } catch (e) { /* ignore */ }
    }

    function tokenFromEvent(textarea, e) {
        var offset = textarea.selectionStart;
        if (typeof textarea.selectionEnd === 'number' && textarea.selectionEnd !== textarea.selectionStart) {
            // Prefer start of selection
            offset = Math.min(textarea.selectionStart, textarea.selectionEnd);
        }
        // For mouse events, try to place caret first (browser usually has)
        if (e && (e.clientX || e.clientY) && document.caretPositionFromPoint) {
            // Textarea caret from point is unreliable; use selection after contextmenu
        }
        return wordAtOffset(textarea.value || '', offset);
    }

    function openSuggestionsForTextarea(textarea) {
        if (!isBlockTextarea(textarea) || !isEnabled()) return;
        loadDictionary().then(function() {
            var token = wordAtOffset(textarea.value || '', textarea.selectionStart);
            if (!token) {
                hidePopup();
                return;
            }
            showSuggestions(textarea, token);
        }).catch(function() { /* no dict */ });
    }

    function refreshAll() {
        if (!isScriptPage()) return;
        if (!isEnabled()) {
            document.querySelectorAll('.project-script form.scripty-spell-active, .table-blocks-all form.scripty-spell-active').forEach(function(form) {
                clearOverlay(form);
            });
            hidePopup();
            // Re-apply browser spellcheck preference
            if (typeof window.scriptyUpdateAllSpellcheckElements === 'function') {
                window.scriptyUpdateAllSpellcheckElements();
            }
            return;
        }
        loadDictionary().then(function() {
            document.querySelectorAll('.project-script textarea.block-input-textarea[name="content"], .table-blocks-all textarea.block-input-textarea[name="content"]').forEach(function(ta) {
                runCheck(ta);
            });
        }).catch(function() { /* ignore */ });
    }

    function onToggle() {
        hidePopup();
        refreshAll();
    }

    // --- Events ---

    document.addEventListener('input', function(e) {
        if (!isScriptPage()) return;
        if (isBlockTextarea(e.target)) {
            scheduleCheck(e.target);
            if (popupTarget === e.target) hidePopup();
        }
    }, true);

    document.addEventListener('focusin', function(e) {
        if (!isScriptPage()) return;
        if (isBlockTextarea(e.target) && isEnabled()) {
            var errors = lastErrors.get(e.target);
            if (errors) renderOverlay(e.target, errors);
            scheduleCheck(e.target);
        }
    }, true);

    document.addEventListener('focusout', function(e) {
        if (isBlockTextarea(e.target) && isEnabled()) {
            var ta = e.target;
            setTimeout(function() {
                if (document.activeElement === ta) return;
                var errors = lastErrors.get(ta);
                if (errors) renderOverlay(ta, errors);
                else runCheck(ta);
            }, 0);
        }
        if (!popupEl || popupEl.hidden) return;
        // Delay so mousedown on popup can run first
        setTimeout(function() {
            if (popupEl && !popupEl.hidden && document.activeElement !== popupTarget) {
                if (!popupEl.contains(document.activeElement)) hidePopup();
            }
        }, 0);
    }, true);

    document.addEventListener('contextmenu', function(e) {
        if (!isScriptPage() || !isEnabled()) return;
        var textarea = e.target.closest && e.target.closest('textarea');
        if (!isBlockTextarea(textarea)) return;
        if (!dictionary || !dictionary.loaded) return;

        var token = wordAtOffset(textarea.value || '', textarea.selectionStart);
        var allow = buildAllowlist();
        if (token && isMisspelled(token.word, allow)) {
            e.preventDefault();
            showSuggestions(textarea, token);
        }
    }, true);

    document.addEventListener('keydown', function(e) {
        if (!isScriptPage()) return;

        // Suggestion list navigation
        if (popupEl && !popupEl.hidden) {
            var options = popupEl.querySelectorAll('li');
            if (e.key === 'Escape') {
                e.preventDefault();
                hidePopup();
                return;
            }
            if (e.key === 'ArrowDown' && options.length) {
                e.preventDefault();
                popupIndex = Math.min(options.length - 1, popupIndex + 1);
                options.forEach(function(li, i) {
                    li.classList.toggle('is-active', i === popupIndex);
                    li.setAttribute('aria-selected', i === popupIndex ? 'true' : 'false');
                });
                return;
            }
            if (e.key === 'ArrowUp' && options.length) {
                e.preventDefault();
                popupIndex = Math.max(0, popupIndex - 1);
                options.forEach(function(li, i) {
                    li.classList.toggle('is-active', i === popupIndex);
                    li.setAttribute('aria-selected', i === popupIndex ? 'true' : 'false');
                });
                return;
            }
            if ((e.key === 'Enter' || e.key === 'Tab') && !e.shiftKey && popupIndex >= 0 && options[popupIndex]) {
                e.preventDefault();
                e.stopImmediatePropagation();
                var chosen = options[popupIndex];
                if (chosen.getAttribute('data-action') === 'ignore') {
                    ignoreCurrentWord();
                } else if (chosen.getAttribute('data-action') === 'delete') {
                    deleteCurrentWord();
                } else {
                    applySuggestion(chosen.getAttribute('data-suggestion'));
                }
                return;
            }
            if (e.key === 'ArrowLeft' || e.key === 'ArrowRight' || e.key === 'Home' || e.key === 'End' ||
                e.key === 'Backspace' || e.key === 'Delete') {
                hidePopup();
            }
        }

        // Ctrl/Cmd+. — open suggestions for word at caret
        if ((e.ctrlKey || e.metaKey) && e.key === '.' && !e.altKey) {
            var ta = e.target;
            if (isBlockTextarea(ta) && isEnabled()) {
                e.preventDefault();
                openSuggestionsForTextarea(ta);
            }
        }
    }, true);

    document.addEventListener('click', function(e) {
        if (!popupEl || popupEl.hidden) return;
        if (popupEl.contains(e.target)) return;
        hidePopup();
    }, true);


    document.addEventListener('mousedown', function(e) {
        if (!isScriptPage() || !isEnabled()) return;
        var span = e.target.closest && e.target.closest('.scripty-spell-error');
        if (!span) return;
        var start = parseInt(span.getAttribute('data-start'), 10);
        var end = parseInt(span.getAttribute('data-end'), 10);
        window._pendingSpellcheckClick = {
            start: start,
            end: end
        };
    }, true);

    var refreshTimer = null;
    function scheduleRefreshAll() {
        if (refreshTimer) clearTimeout(refreshTimer);
        refreshTimer = setTimeout(function() {
            refreshTimer = null;
            refreshAll();
        }, 50);
    }

    document.body.addEventListener('htmx:afterSwap', function() {
        hidePopup();
        if (isEnabled()) scheduleRefreshAll();
    });
    document.body.addEventListener('htmx:afterSettle', function() {
        if (isEnabled()) scheduleRefreshAll();
    });

    // Hook toggle (nav.html defines toggleSpellcheck before this script loads)
    if (typeof window.toggleSpellcheck === 'function') {
        var originalToggle = window.toggleSpellcheck;
        window.toggleSpellcheck = function() {
            originalToggle();
            onToggle();
        };
    }

    // Re-apply marks after layout sync (nav.html sets mirror.textContent)
    if (typeof window.scriptyGrowTextarea === 'function') {
        var originalGrow = window.scriptyGrowTextarea;
        window.scriptyGrowTextarea = function(textarea) {
            originalGrow(textarea);
            if (!isBlockTextarea(textarea) || !isEnabled()) return;
            var form = textarea.closest('form');
            if (!form || !form.classList.contains('scripty-spell-active')) return;
            if (form.matches(':focus-within')) return;
            var errors = lastErrors.get(textarea);
            if (errors && errors.length) {
                var mirror = findReaderMirror(form);
                if (mirror) {
                    mirror.innerHTML = buildMarkedHtml(textarea.value || '', errors);
                    mirror.setAttribute('data-scripty-spell-mirror', '1');
                }
            }
        };
    }

    window.scriptyRefreshSpellcheck = refreshAll;
    window.scriptyOpenSpellcheckSuggestions = openSuggestionsForTextarea;
    window.scriptySpellcheckIgnoreWord = function(word) {
        if (!word) return;
        ignoredWords[String(word).toUpperCase()] = true;
    };

    function init() {
        loadIgnored();
        if (!isScriptPage()) return;
        if (isEnabled()) {
            loadDictionary().then(function() {
                refreshAll();
            }).catch(function() { /* offline / missing dict */ });
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
