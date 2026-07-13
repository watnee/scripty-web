/**
 * PWA install affordances: Help menu action, soft banner, and iOS Add to Home Screen guide.
 */
(function () {
    'use strict';

    if (window._scriptyPwaInstallInit) return;
    window._scriptyPwaInstallInit = true;

    var DISMISS_KEY = 'scripty-pwa-install-dismissed';
    var DISMISS_MS = 14 * 24 * 60 * 60 * 1000;
    var deferredPrompt = null;
    var bannerEl = null;
    var guideEl = null;

    function isStandalone() {
        return window.matchMedia('(display-mode: standalone)').matches
            || window.matchMedia('(display-mode: minimal-ui)').matches
            || window.navigator.standalone === true;
    }

    function isIos() {
        var ua = window.navigator.userAgent || '';
        var iOS = /iPad|iPhone|iPod/.test(ua);
        var iPadOs = window.navigator.platform === 'MacIntel' && window.navigator.maxTouchPoints > 1;
        return iOS || iPadOs;
    }

    function isDismissed() {
        try {
            var raw = localStorage.getItem(DISMISS_KEY);
            if (!raw) return false;
            var until = parseInt(raw, 10);
            if (!until || Date.now() > until) {
                localStorage.removeItem(DISMISS_KEY);
                return false;
            }
            return true;
        } catch (err) {
            return false;
        }
    }

    function dismissForAWhile() {
        try {
            localStorage.setItem(DISMISS_KEY, String(Date.now() + DISMISS_MS));
        } catch (err) { /* ignore */ }
    }

    function setInstallControlsVisible(visible) {
        document.querySelectorAll('[data-scripty-install-app]').forEach(function (el) {
            el.hidden = !visible;
        });
    }

    function ensureGuide() {
        if (guideEl) return guideEl;
        guideEl = document.createElement('div');
        guideEl.id = 'scripty-pwa-install-guide';
        guideEl.className = 'scripty-pwa-install-guide';
        guideEl.hidden = true;
        guideEl.setAttribute('role', 'dialog');
        guideEl.setAttribute('aria-modal', 'true');
        guideEl.setAttribute('aria-labelledby', 'scripty-pwa-install-guide-title');
        guideEl.innerHTML =
            '<div class="scripty-pwa-install-guide-card">' +
            '  <h2 id="scripty-pwa-install-guide-title">Install Scripty</h2>' +
            '  <p class="scripty-pwa-install-guide-lead">Add Scripty to your Home Screen for a full-screen app and offline writing.</p>' +
            '  <ol class="scripty-pwa-install-guide-steps">' +
            '    <li>Tap the <strong>Share</strong> button in Safari.</li>' +
            '    <li>Scroll and tap <strong>Add to Home Screen</strong>.</li>' +
            '    <li>Tap <strong>Add</strong>.</li>' +
            '  </ol>' +
            '  <p class="scripty-pwa-install-guide-note muted">On Chrome or Edge, use the browser menu → <strong>Install app</strong> / <strong>Install Scripty</strong>.</p>' +
            '  <div class="scripty-pwa-install-guide-actions">' +
            '    <a class="scripty-pwa-install-guide-help" href="/help#install-app">More help</a>' +
            '    <button type="button" class="scripty-pwa-install-guide-close">Got it</button>' +
            '  </div>' +
            '</div>';
        document.body.appendChild(guideEl);
        guideEl.addEventListener('click', function (e) {
            if (e.target === guideEl || e.target.closest('.scripty-pwa-install-guide-close')) {
                hideGuide();
            }
        });
        return guideEl;
    }

    function showGuide() {
        ensureGuide().hidden = false;
        document.documentElement.classList.add('scripty-pwa-guide-open');
    }

    function hideGuide() {
        if (guideEl) guideEl.hidden = true;
        document.documentElement.classList.remove('scripty-pwa-guide-open');
    }

    function ensureBanner() {
        if (bannerEl) return bannerEl;
        bannerEl = document.createElement('div');
        bannerEl.id = 'scripty-pwa-install-banner';
        bannerEl.className = 'scripty-pwa-install-banner';
        bannerEl.hidden = true;
        bannerEl.setAttribute('role', 'region');
        bannerEl.setAttribute('aria-label', 'Install Scripty');
        bannerEl.innerHTML =
            '<div class="scripty-pwa-install-banner-copy">' +
            '  <strong>Install Scripty</strong>' +
            '  <span>Open it like an app — including offline writing on projects you’ve visited.</span>' +
            '</div>' +
            '<div class="scripty-pwa-install-banner-actions">' +
            '  <button type="button" class="scripty-pwa-install-banner-install">Install</button>' +
            '  <button type="button" class="scripty-pwa-install-banner-dismiss" aria-label="Dismiss">Not now</button>' +
            '</div>';
        document.body.appendChild(bannerEl);
        bannerEl.querySelector('.scripty-pwa-install-banner-install').addEventListener('click', function () {
            promptInstall();
        });
        bannerEl.querySelector('.scripty-pwa-install-banner-dismiss').addEventListener('click', function () {
            dismissForAWhile();
            hideBanner();
        });
        return bannerEl;
    }

    function showBanner() {
        if (isStandalone() || isDismissed()) return;
        ensureBanner().hidden = false;
    }

    function hideBanner() {
        if (bannerEl) bannerEl.hidden = true;
    }

    async function promptInstall() {
        if (deferredPrompt) {
            hideBanner();
            deferredPrompt.prompt();
            try {
                var choice = await deferredPrompt.userChoice;
                if (choice && choice.outcome === 'accepted') {
                    dismissForAWhile();
                }
            } catch (err) { /* ignore */ }
            deferredPrompt = null;
            setInstallControlsVisible(!isStandalone());
            return;
        }
        if (isIos()) {
            showGuide();
            return;
        }
        showGuide();
    }

    function onInstallClick(e) {
        e.preventDefault();
        promptInstall();
        var dropdown = e.target.closest('.nav-dropdown');
        if (dropdown) dropdown.classList.remove('open');
    }

    function wireInstallButtons() {
        document.querySelectorAll('[data-scripty-install-app]').forEach(function (el) {
            if (el._scriptyInstallWired) return;
            el._scriptyInstallWired = true;
            el.addEventListener('click', onInstallClick);
        });
    }

    function init() {
        if (isStandalone()) {
            document.documentElement.classList.add('scripty-pwa-standalone');
            setInstallControlsVisible(false);
            return;
        }

        setInstallControlsVisible(true);
        wireInstallButtons();

        window.addEventListener('beforeinstallprompt', function (e) {
            e.preventDefault();
            deferredPrompt = e;
            setInstallControlsVisible(true);
            wireInstallButtons();
            if (!isDismissed()) {
                window.setTimeout(showBanner, 2500);
            }
        });

        window.addEventListener('appinstalled', function () {
            deferredPrompt = null;
            hideBanner();
            hideGuide();
            dismissForAWhile();
            setInstallControlsVisible(false);
            document.documentElement.classList.add('scripty-pwa-standalone');
        });

        // iOS never fires beforeinstallprompt — still offer Install in Help.
        if (isIos() && !isDismissed()) {
            window.setTimeout(function () {
                // Soft banner only after the user has used the app a bit; keep iOS quieter.
                if (document.querySelector('.has-toolbar-brand') && !isDismissed()) {
                    showBanner();
                    var installBtn = bannerEl && bannerEl.querySelector('.scripty-pwa-install-banner-install');
                    if (installBtn) installBtn.textContent = 'How to install';
                }
            }, 8000);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // HTMX boost may re-insert nav; re-wire install controls.
    document.body.addEventListener('htmx:afterSettle', function () {
        if (isStandalone()) {
            setInstallControlsVisible(false);
            return;
        }
        setInstallControlsVisible(true);
        wireInstallButtons();
    });
})();
