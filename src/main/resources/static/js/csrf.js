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

    function isLoginPagePath() {
        return (window.location.pathname || '') === '/login';
    }

    function looksLikeLoginHtml(html) {
        if (!html) {
            return false;
        }
        // Only markers unique to the sign-in page itself. Do NOT match shared
        // chrome like login-body / login-page: the change-password and passkey
        // pages reuse those styling classes, and matching them cancels every
        // boosted navigation to /account/password (bounced back via /login).
        return html.indexOf('id="login-brand"') !== -1
            || html.indexOf("id='login-brand'") !== -1
            || html.indexOf('id="login-heading"') !== -1
            || html.indexOf('class="login-panel"') !== -1
            || html.indexOf("class='login-panel'") !== -1;
    }

    function forceFullLoginNavigation() {
        if (isLoginPagePath()) {
            return;
        }
        window.location.href = '/login';
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
     * If an unauthenticated / CSRF-failed HTMX response still returns login HTML
     * (e.g. a 302 that XHR follows), do a full navigation instead of swapping
     * the sign-in page into /project/show while the URL stays put.
     */
    document.addEventListener('htmx:beforeSwap', function (event) {
        if (!event.detail || isLoginPagePath()) {
            return;
        }
        var xhr = event.detail.xhr;
        var html = (xhr && xhr.responseText) || event.detail.serverResponse || '';
        if (!looksLikeLoginHtml(html)) {
            return;
        }
        event.detail.shouldSwap = false;
        forceFullLoginNavigation();
    });

    /**
     * Safety net: if login markup still lands in the DOM on a non-login URL
     * (stale csrf.js, missed beforeSwap, etc.), bounce to a real /login load.
     */
    function reclaimSwappedLogin() {
        if (isLoginPagePath()) {
            return;
        }
        // .login-panel / #login-brand exist only on the real sign-in page;
        // main.login-page is shared with account pages and must not match here.
        if (document.getElementById('login-brand') || document.querySelector('.login-panel')) {
            forceFullLoginNavigation();
        }
    }

    document.body.addEventListener('htmx:afterSwap', reclaimSwappedLogin);
    document.body.addEventListener('htmx:afterSettle', reclaimSwappedLogin);

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
