/**
 * Passkey (WebAuthn) wiring for the login page button (#passkey-signin) and the
 * management page (/webauthn/register).
 *
 * Loaded from nav.html so it survives HTMX-boosted navigation: swapped-in pages
 * cannot run their own <script> tags (htmx-security strips them), so this file
 * re-wires whatever passkey markup is present after every swap. Spring
 * Security's webauthn.js is injected lazily only when passkey markup exists.
 */
(function () {
    'use strict';

    if (window._scriptyPasskeysInit) return;
    window._scriptyPasskeysInit = true;

    function csrfHeaders() {
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        var headers = {};
        if (token && header && token.content && header.content) {
            headers[header.content] = token.content;
        }
        return headers;
    }

    var webauthnScriptPromise = null;
    function loadWebauthnScript() {
        if (window.setupLogin && window.setupRegistration) {
            return Promise.resolve();
        }
        if (!webauthnScriptPromise) {
            webauthnScriptPromise = new Promise(function (resolve, reject) {
                var script = document.createElement('script');
                script.src = '/login/webauthn.js';
                script.onload = resolve;
                script.onerror = function () {
                    webauthnScriptPromise = null;
                    reject(new Error('webauthn.js failed to load'));
                };
                document.head.appendChild(script);
            });
        }
        return webauthnScriptPromise;
    }

    function wireLoginButton() {
        var signinButton = document.getElementById('passkey-signin');
        if (!signinButton || signinButton.dataset.scriptyPasskeyWired === '1') return;
        signinButton.dataset.scriptyPasskeyWired = '1';
        loadWebauthnScript().then(function () {
            window.setupLogin(csrfHeaders(), '', signinButton);
        }).catch(function (err) {
            console.error('Passkey login setup failed:', err);
        });
    }

    function wireRegistrationPage() {
        var registerButton = document.getElementById('register');
        var labelInput = document.getElementById('label');
        if (!registerButton || !labelInput
                || registerButton.dataset.scriptyPasskeyWired === '1') {
            return;
        }
        registerButton.dataset.scriptyPasskeyWired = '1';
        var ui = {
            getRegisterButton: function () { return registerButton; },
            getLabelInput: function () { return labelInput; },
            getSuccess: function () { return document.getElementById('success'); },
            getError: function () { return document.getElementById('error'); },
            getDeleteForms: function () {
                return Array.prototype.slice.call(document.querySelectorAll('form.delete-form'));
            }
        };
        loadWebauthnScript().then(function () {
            window.setupRegistration(csrfHeaders(), '', ui);
        }).catch(function (err) {
            console.error('Passkey registration setup failed:', err);
        });
    }

    function initPasskeys() {
        wireLoginButton();
        wireRegistrationPage();
    }

    window.scriptyInitPasskeys = initPasskeys;

    document.body.addEventListener('htmx:afterSwap', initPasskeys);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initPasskeys);
    } else {
        initPasskeys();
    }
})();
