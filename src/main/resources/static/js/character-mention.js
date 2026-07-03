(function() {
    'use strict';

    var menu = null;
    var items = [];
    var selectedIndex = 0;
    var activeTextarea = null;
    var tokenStart = -1;

    function getPersons(form) {
        var spans = form.querySelectorAll('.character-mention-data [data-id]');
        return Array.prototype.map.call(spans, function(el) {
            return { id: el.getAttribute('data-id'), name: el.textContent };
        });
    }

    // Read by the hx-trigger input filters on the block forms: while the
    // caret sits in an @token that is plausibly still being typed (matches a
    // character, or is a single word so far), hold the delayed autosave so a
    // half-typed mention is never saved. Enter still saves explicitly.
    window.characterMentionHold = function(evt) {
        var t = evt.target;
        if (!t || t.tagName !== 'TEXTAREA' || t.name !== 'content') return false;
        var form = t.closest('form');
        if (!form || !form.querySelector('.character-mention-data')) return false;
        var token = findToken(t);
        if (!token) return false;
        var query = token.query.toLowerCase();
        if (query.indexOf(' ') === -1) return true;
        return getPersons(form).some(function(p) {
            return p.name.toLowerCase().indexOf(query) !== -1;
        });
    };

    // Finds an "@query" token ending at the caret; the @ must start the text
    // or follow whitespace so email-like text doesn't trigger the menu.
    function findToken(textarea) {
        var text = textarea.value.slice(0, textarea.selectionStart);
        var at = text.lastIndexOf('@');
        if (at === -1) return null;
        if (at > 0 && !/\s/.test(text.charAt(at - 1))) return null;
        var query = text.slice(at + 1);
        if (query.indexOf('\n') !== -1) return null;
        return { start: at, query: query };
    }

    function closeMenu() {
        if (menu) {
            menu.remove();
            menu = null;
        }
        items = [];
        activeTextarea = null;
        tokenStart = -1;
        window.characterMentionOpen = false;
    }

    function openMenu(textarea, matches, start) {
        closeMenu();
        activeTextarea = textarea;
        tokenStart = start;
        selectedIndex = 0;
        // Read by the hx-trigger filters on the block forms; set here in the
        // capture phase so it is visible before htmx's own listeners run.
        window.characterMentionOpen = true;

        menu = document.createElement('div');
        menu.className = 'character-mention-menu';
        matches.forEach(function(person, i) {
            var item = document.createElement('div');
            item.className = 'character-mention-item' + (i === 0 ? ' selected' : '');
            item.textContent = person.name;
            item.addEventListener('mousedown', function(e) {
                e.preventDefault(); // keep focus on the textarea
                choose(person);
            });
            menu.appendChild(item);
            items.push({ el: item, person: person });
        });
        document.body.appendChild(menu);

        var rect = textarea.getBoundingClientRect();
        menu.style.left = (rect.left + window.scrollX) + 'px';
        menu.style.top = (rect.bottom + window.scrollY) + 'px';
        menu.style.minWidth = Math.min(rect.width, 300) + 'px';
    }

    function updateSelection() {
        items.forEach(function(it, i) {
            it.el.classList.toggle('selected', i === selectedIndex);
        });
    }

    function showCharacterBadge(form, name) {
        var badge = form.querySelector('.mention-character-badge');
        if (!badge) {
            badge = document.createElement('div');
            badge.className = 'mention-character-badge';
            form.insertBefore(badge, form.querySelector('textarea[name="content"]'));
        }
        badge.textContent = name;
    }

    function choose(person) {
        var textarea = activeTextarea;
        var form = textarea.closest('form');
        var caret = textarea.selectionStart;

        var before = textarea.value.slice(0, tokenStart);
        var after = textarea.value.slice(caret);
        if (after === '') before = before.replace(/\s+$/, '');
        textarea.value = before + after;
        textarea.selectionStart = textarea.selectionEnd = before.length;

        var personInput = form.querySelector('input[name="personId"]');
        if (!personInput) {
            personInput = document.createElement('input');
            personInput.type = 'hidden';
            personInput.name = 'personId';
            form.appendChild(personInput);
        }
        personInput.value = person.id;

        showCharacterBadge(form, person.name);
        closeMenu();
        textarea.focus();

        // Autosave now unless this is a brand-new block with no dialogue yet;
        // in that case wait so the user can keep typing into the same block.
        var isEdit = (form.getAttribute('hx-post') || '').indexOf('editInline') !== -1;
        if (isEdit || textarea.value.trim() !== '') {
            textarea.dispatchEvent(new Event('input', { bubbles: true }));
        }
    }

    document.addEventListener('input', function(e) {
        var t = e.target;
        if (!t || t.tagName !== 'TEXTAREA' || t.name !== 'content') return;
        var form = t.closest('form');
        if (!form || !form.querySelector('.character-mention-data')) return;

        var token = findToken(t);
        if (!token) {
            closeMenu();
            return;
        }
        var query = token.query.toLowerCase();
        var matches = getPersons(form).filter(function(p) {
            return p.name.toLowerCase().indexOf(query) !== -1;
        });
        if (matches.length === 0) {
            closeMenu();
            return;
        }
        openMenu(t, matches, token.start);
    }, true);

    document.addEventListener('keydown', function(e) {
        if (!menu || e.target !== activeTextarea) return;
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            selectedIndex = (selectedIndex + 1) % items.length;
            updateSelection();
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            selectedIndex = (selectedIndex - 1 + items.length) % items.length;
            updateSelection();
        } else if (e.key === 'Enter' || e.key === 'Tab') {
            e.preventDefault();
            e.stopPropagation(); // don't let the form's Enter trigger fire
            choose(items[selectedIndex].person);
        } else if (e.key === 'Escape') {
            closeMenu();
        }
    }, true);

    // While the menu is open the textarea still holds the raw "@query" text,
    // so hold back any pending autosave until a character is chosen or the
    // menu is dismissed.
    document.addEventListener('htmx:beforeRequest', function(e) {
        if (menu && activeTextarea && e.detail.elt.contains(activeTextarea)) {
            e.preventDefault();
        }
    });

    document.addEventListener('blur', function(e) {
        var t = e.target;
        if (t === activeTextarea) closeMenu();
        // An active token holds back the edit form's autosave; flush the
        // pending change when the user leaves the field so it isn't lost.
        if (t && t.tagName === 'TEXTAREA' && t.name === 'content' && window.htmx) {
            var form = t.closest('form');
            if (form && (form.getAttribute('hx-post') || '').indexOf('editInline') !== -1 && findToken(t)) {
                window.htmx.trigger(form, 'forceSave');
            }
        }
    }, true);

    document.addEventListener('scroll', function(e) {
        if (menu && !menu.contains(e.target)) closeMenu();
    }, true);
})();
