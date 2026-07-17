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

    // Work out where the user actually installs, so the guide shows the real
    // menu names for their browser instead of generic steps.
    function detectPlatform() {
        var ua = window.navigator.userAgent || '';
        var ios = isIos();
        var android = /Android/.test(ua);
        var edge = /Edg(A|iOS|)?\//.test(ua);
        var opera = /OPR\//.test(ua) || /Opera/.test(ua);
        var firefox = /Firefox\//.test(ua) || /FxiOS/.test(ua);
        var samsung = /SamsungBrowser/.test(ua);
        // iOS is always WebKit; treat every iOS browser as the "share sheet" flow.
        var chrome = !ios && /Chrome\//.test(ua) && !edge && !opera && !samsung;
        var safari = !chrome && !edge && !opera && !firefox
            && (ios || /Safari\//.test(ua));

        var os = ios ? 'ios' : android ? 'android' : 'desktop';
        var browser = firefox ? 'firefox'
            : edge ? 'edge'
            : samsung ? 'samsung'
            : opera ? 'opera'
            : chrome ? 'chrome'
            : safari ? 'safari'
            : 'other';
        return { os: os, browser: browser, ios: ios, android: android };
    }

    function esc(s) {
        return String(s).replace(/[&<>"]/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
        });
    }

    // Returns { title, lead, steps[], note } tailored to the current browser/OS.
    function guideContent() {
        var p = detectPlatform();

        if (p.os === 'ios') {
            var iosNote = p.browser === 'safari'
                ? 'The Home Screen icon opens Scripty full-screen, and lets you write offline.'
                : 'Add to Home Screen works from any iPhone browser — if you don’t see it, open Scripty in <strong>Safari</strong> first.';
            return {
                title: 'Add Scripty to your Home Screen',
                lead: 'On iPhone and iPad you install by adding Scripty to the Home Screen.',
                steps: [
                    'Tap the <strong>Share</strong> button (the square with an up arrow) in the toolbar.',
                    'Scroll down and tap <strong>Add to Home Screen</strong>.',
                    'Tap <strong>Add</strong> — Scripty appears on your Home Screen.'
                ],
                note: iosNote
            };
        }

        if (p.os === 'android') {
            var menuName = p.browser === 'firefox' ? 'Install'
                : p.browser === 'samsung' ? 'Add page to' + '…'
                : 'Install app';
            return {
                title: 'Install Scripty',
                lead: 'Install Scripty from your browser menu for a full-screen app icon.',
                steps: [
                    'Open the browser menu (the <strong>⋮</strong> icon, usually top-right).',
                    'Tap <strong>' + esc(menuName) + '</strong> (it may say <strong>Add to Home screen</strong>).',
                    'Confirm with <strong>Install</strong> / <strong>Add</strong>.'
                ],
                note: 'Once installed, Scripty opens like any other app.'
            };
        }

        // Desktop
        if (p.browser === 'chrome' || p.browser === 'edge') {
            var menuLabel = p.browser === 'edge'
                ? 'Apps → Install Scripty'
                : 'Install Scripty';
            return {
                title: 'Install Scripty',
                lead: 'Install Scripty as a desktop app that opens in its own window.',
                steps: [
                    'Look for the <strong>install icon</strong> at the right end of the address bar (a monitor with a down arrow, or ⊕).',
                    'Click it — or open the browser menu (<strong>⋮</strong>) and choose <strong>' + esc(menuLabel) + '</strong>.',
                    'Click <strong>Install</strong> in the dialog.'
                ],
                note: 'No install icon? Reload the page once, then check the address bar again.'
            };
        }

        if (p.browser === 'safari') {
            return {
                title: 'Add Scripty to your Dock',
                lead: 'Safari on macOS installs web apps into your Dock.',
                steps: [
                    'Open the <strong>File</strong> menu in Safari’s menu bar.',
                    'Choose <strong>Add to Dock…</strong> (on older Safari: <strong>Share → Add to Dock</strong>).',
                    'Click <strong>Add</strong> — Scripty opens from your Dock like an app.'
                ],
                note: 'Add to Dock needs macOS Sonoma or later. On older macOS, use Chrome or Edge to install.'
            };
        }

        if (p.browser === 'firefox') {
            return {
                title: 'Install Scripty',
                lead: 'Firefox on desktop can’t install web apps yet.',
                steps: [
                    'Open Scripty in <strong>Chrome</strong> or <strong>Microsoft Edge</strong>.',
                    'Use the <strong>install icon</strong> in the address bar (or menu → <strong>Install Scripty</strong>).',
                    'Or just bookmark this page to keep it handy in Firefox.'
                ],
                note: 'Prefer Firefox? You can keep using Scripty in the browser — installing is optional.'
            };
        }

        // Unknown browser fallback.
        return {
            title: 'Install Scripty',
            lead: 'Install Scripty for a full-screen app icon and offline writing.',
            steps: [
                'Open your browser’s menu.',
                'Look for <strong>Install app</strong>, <strong>Install Scripty</strong>, or <strong>Add to Home Screen</strong>.',
                'Confirm to add Scripty to your device.'
            ],
            note: 'Chrome and Edge give the smoothest install experience.'
        };
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
        guideEl.innerHTML = '<div class="scripty-pwa-install-guide-card"></div>';
        document.body.appendChild(guideEl);
        guideEl.addEventListener('click', function (e) {
            if (e.target === guideEl || e.target.closest('.scripty-pwa-install-guide-close')) {
                hideGuide();
            }
        });
        return guideEl;
    }

    function renderGuide() {
        var c = guideContent();
        var steps = c.steps.map(function (s) { return '<li>' + s + '</li>'; }).join('');
        ensureGuide().querySelector('.scripty-pwa-install-guide-card').innerHTML =
            '<h2 id="scripty-pwa-install-guide-title">' + esc(c.title) + '</h2>' +
            '<p class="scripty-pwa-install-guide-lead">' + esc(c.lead) + '</p>' +
            '<ol class="scripty-pwa-install-guide-steps">' + steps + '</ol>' +
            '<p class="scripty-pwa-install-guide-note muted">' + c.note + '</p>' +
            '<div class="scripty-pwa-install-guide-actions">' +
            '  <a class="scripty-pwa-install-guide-help" href="/help#install-app">More help</a>' +
            '  <button type="button" class="scripty-pwa-install-guide-close">Got it</button>' +
            '</div>';
    }

    function showGuide() {
        renderGuide();
        guideEl.hidden = false;
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
            '  <strong>Install Scripty as an app</strong>' +
            '  <span>Get a Home Screen / desktop icon that opens full-screen — plus offline writing on projects you’ve visited.</span>' +
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
