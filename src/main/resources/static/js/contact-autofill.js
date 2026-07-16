/**
 * Name lookup for the invite-by-email fields: type a person's name and pick
 * them from the list to fill in their address.
 *
 * The field stays type="email" throughout — a suggestion only ever writes an
 * address into it, so a name can never be submitted as a recipient.
 *
 * Loaded from nav.html so handlers survive HTMX-boosted navigation into
 * /project/show (page scripts are not executed when allowScriptTags is false).
 */
(function () {
    'use strict';

    if (window._scriptyContactAutofillInit) return;
    window._scriptyContactAutofillInit = true;

    var DEBOUNCE_MS = 150;
    var MIN_QUERY = 1;
    var state = new WeakMap();

    function fieldFor(input) {
        var entry = state.get(input);
        if (entry) return entry;
        var wrapper = input.closest('.contact-suggest-field');
        if (!wrapper) return null;
        var list = document.createElement('ul');
        list.className = 'contact-suggest-list';
        list.id = 'contact-suggest-list-' + Math.random().toString(36).slice(2, 10);
        list.setAttribute('role', 'listbox');
        list.hidden = true;
        wrapper.appendChild(list);

        input.setAttribute('role', 'combobox');
        input.setAttribute('aria-autocomplete', 'list');
        input.setAttribute('aria-expanded', 'false');
        input.setAttribute('aria-controls', list.id);
        // The browser's own saved-address dropdown would cover ours.
        input.setAttribute('autocomplete', 'off');

        entry = { list: list, items: [], active: -1, timer: null, seq: 0 };
        state.set(input, entry);
        return entry;
    }

    function close(input) {
        var entry = state.get(input);
        if (!entry) return;
        entry.list.hidden = true;
        entry.list.textContent = '';
        entry.items = [];
        entry.active = -1;
        input.setAttribute('aria-expanded', 'false');
        input.removeAttribute('aria-activedescendant');
    }

    function setActive(input, index) {
        var entry = state.get(input);
        if (!entry || !entry.items.length) return;
        var count = entry.items.length;
        entry.active = ((index % count) + count) % count;
        entry.items.forEach(function (item, i) {
            var isActive = i === entry.active;
            item.classList.toggle('is-active', isActive);
            item.setAttribute('aria-selected', isActive ? 'true' : 'false');
            if (isActive) {
                input.setAttribute('aria-activedescendant', item.id);
                if (item.scrollIntoView) item.scrollIntoView({ block: 'nearest' });
            }
        });
    }

    function choose(input, contact) {
        input.value = contact.email;
        close(input);
        input.focus();
        input.dispatchEvent(new Event('change', { bubbles: true }));
    }

    function render(input, contacts) {
        var entry = state.get(input);
        if (!entry) return;
        entry.list.textContent = '';
        entry.items = [];
        entry.active = -1;

        if (!contacts.length) {
            close(input);
            return;
        }

        contacts.forEach(function (contact, i) {
            var item = document.createElement('li');
            item.className = 'contact-suggest-item';
            item.id = entry.list.id + '-opt-' + i;
            item.setAttribute('role', 'option');
            item.setAttribute('aria-selected', 'false');

            var name = document.createElement('span');
            name.className = 'contact-suggest-name';
            name.textContent = contact.name;
            var email = document.createElement('span');
            email.className = 'contact-suggest-email muted';
            email.textContent = contact.email;
            item.appendChild(name);
            item.appendChild(email);
            if (contact.sourceLabel) {
                var source = document.createElement('span');
                source.className = 'contact-suggest-source muted';
                source.textContent = contact.sourceLabel;
                item.appendChild(source);
            }

            // mousedown, not click: blur would tear the list down first.
            item.addEventListener('mousedown', function (e) {
                e.preventDefault();
                choose(input, contact);
            });
            entry.list.appendChild(item);
            entry.items.push(item);
        });

        entry.list.hidden = false;
        input.setAttribute('aria-expanded', 'true');
    }

    function search(input) {
        var entry = fieldFor(input);
        if (!entry) return;
        var projectId = input.getAttribute('data-contact-suggest');
        var query = input.value.trim();
        // Once it looks like an address the sender has what they need.
        if (!projectId || query.length < MIN_QUERY || query.indexOf('@') >= 0) {
            close(input);
            return;
        }

        var seq = ++entry.seq;
        fetch('/api/project/' + encodeURIComponent(projectId)
                + '/contact-suggestions?q=' + encodeURIComponent(query), {
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json' }
        }).then(function (response) {
            if (!response.ok) throw new Error('Lookup failed: ' + response.status);
            return response.json();
        }).then(function (contacts) {
            // A slower earlier request must not overwrite newer results.
            if (seq !== entry.seq || document.activeElement !== input) return;
            render(input, Array.isArray(contacts) ? contacts : []);
        }).catch(function () {
            // Lookup is a convenience; typing the address in full still works.
            close(input);
        });
    }

    document.body.addEventListener('input', function (e) {
        var input = e.target;
        if (!input || !input.matches || !input.matches('[data-contact-suggest]')) return;
        var entry = fieldFor(input);
        if (!entry) return;
        if (entry.timer) clearTimeout(entry.timer);
        entry.timer = setTimeout(function () {
            search(input);
        }, DEBOUNCE_MS);
    });

    document.body.addEventListener('keydown', function (e) {
        var input = e.target;
        if (!input || !input.matches || !input.matches('[data-contact-suggest]')) return;
        var entry = state.get(input);
        var isOpen = entry && !entry.list.hidden && entry.items.length;

        if (e.key === 'Escape') {
            if (isOpen) {
                e.preventDefault();
                close(input);
            }
            return;
        }
        if (e.key === 'ArrowDown' && !isOpen) {
            search(input);
            return;
        }
        if (!isOpen) return;

        if (e.key === 'ArrowDown') {
            e.preventDefault();
            setActive(input, entry.active + 1);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            setActive(input, entry.active - 1);
        } else if (e.key === 'Enter' && entry.active >= 0) {
            // Only swallow the submit when a suggestion is actually highlighted.
            e.preventDefault();
            entry.items[entry.active].dispatchEvent(new MouseEvent('mousedown'));
        } else if (e.key === 'Tab') {
            close(input);
        }
    });

    document.body.addEventListener('focusout', function (e) {
        var input = e.target;
        if (!input || !input.matches || !input.matches('[data-contact-suggest]')) return;
        close(input);
    });

    document.body.addEventListener('click', function (e) {
        document.querySelectorAll('[data-contact-suggest]').forEach(function (input) {
            var entry = state.get(input);
            if (!entry || entry.list.hidden) return;
            if (!input.closest('.contact-suggest-field').contains(e.target)) {
                close(input);
            }
        });
    });
})();
