/**
 * Attach Spring Security CSRF tokens to HTMX and same-origin mutating fetch calls.
 */
(function () {
    'use strict';

    function csrfMeta() {
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        if (!token || !header || !token.content || !header.content) {
            return null;
        }
        return { token: token.content, header: header.content };
    }

    function isMutating(method) {
        var m = (method || 'GET').toUpperCase();
        return m !== 'GET' && m !== 'HEAD' && m !== 'OPTIONS' && m !== 'TRACE';
    }

    function sameOrigin(url) {
        try {
            var resolved = new URL(url, window.location.href);
            return resolved.origin === window.location.origin;
        } catch (e) {
            return false;
        }
    }

    document.addEventListener('htmx:configRequest', function (event) {
        var csrf = csrfMeta();
        if (!csrf || !event.detail || !event.detail.headers) {
            return;
        }
        event.detail.headers[csrf.header] = csrf.token;
    });

    if (typeof window.fetch !== 'function') {
        return;
    }

    var originalFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
        init = init ? Object.assign({}, init) : {};
        var method = init.method;
        var url = typeof input === 'string' ? input : (input && input.url);

        if (!method && input && typeof input === 'object' && input.method) {
            method = input.method;
        }

        var csrf = csrfMeta();
        if (csrf && isMutating(method) && (!url || sameOrigin(url))) {
            var headers = new Headers(init.headers || (input && input.headers) || undefined);
            if (!headers.has(csrf.header)) {
                headers.set(csrf.header, csrf.token);
            }
            init.headers = headers;
        }

        return originalFetch(input, init);
    };
})();
