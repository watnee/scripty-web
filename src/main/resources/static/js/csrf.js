/**
 * Attach Spring Security CSRF tokens to HTMX and same-origin mutating fetch calls.
 * Also exposes scriptyAppendCsrfToForm for programmatic form.submit() fallbacks.
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

    /** Inject _csrf into a form so native form.submit() works in prod. */
    window.scriptyAppendCsrfToForm = function (form) {
        if (!form) return;
        var csrf = csrfMeta();
        if (!csrf) return;
        var existing = form.querySelector('input[name="_csrf"]');
        if (existing) {
            existing.value = csrf.token;
            return;
        }
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = '_csrf';
        input.value = csrf.token;
        form.appendChild(input);
    };

    document.addEventListener('htmx:configRequest', function (event) {
        var csrf = csrfMeta();
        if (!csrf || !event.detail || !event.detail.headers) {
            return;
        }
        event.detail.headers[csrf.header] = csrf.token;
    });

    /**
     * If an unauthenticated HTMX response still returns login HTML (e.g. older
     * servers that 302 to /login), do a full navigation instead of swapping
     * "Please sign in" into /project/show while the URL stays put.
     */
    document.addEventListener('htmx:beforeSwap', function (event) {
        if (!event.detail || event.detail.shouldSwap === false) {
            return;
        }
        var path = window.location.pathname || '';
        if (path === '/login') {
            return;
        }
        var xhr = event.detail.xhr;
        var html = xhr && xhr.responseText ? xhr.responseText : '';
        if (html.indexOf('id="login-heading"') === -1
                && html.indexOf('id="login-brand"') === -1
                && html.indexOf('class="login-body"') === -1
                && html.indexOf("class='login-body'") === -1) {
            return;
        }
        event.detail.shouldSwap = false;
        window.location.href = '/login';
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
