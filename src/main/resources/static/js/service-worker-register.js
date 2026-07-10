/**
 * PWA service worker registration / migration.
 *
 * Reads flags from meta tags set by Thymeleaf in nav.html:
 *   <meta name="app-asset-version" content="...">
 *   <meta name="service-worker-enabled" content="true|false">
 */
(function () {
    'use strict';

    if (window._scriptyServiceWorkerRegisterInit) return;
    window._scriptyServiceWorkerRegisterInit = true;

    if (!('serviceWorker' in navigator)) return;

    function readMeta(name) {
        var meta = document.querySelector('meta[name="' + name + '"]');
        return meta ? meta.getAttribute('content') : null;
    }

    var enabledRaw = readMeta('service-worker-enabled');
    var serviceWorkerEnabled = enabledRaw == null || enabledRaw === '' || enabledRaw === 'true';
    var assetVersion = readMeta('app-asset-version') || '1';
    var SW_MIGRATION_KEY = 'sw-migration-v27';
    var refreshingForSw = false;

    async function clearServiceWorkerState() {
        if ('caches' in window) {
            var keys = await caches.keys();
            await Promise.all(keys.map(function (key) { return caches.delete(key); }));
        }
        var regs = await navigator.serviceWorker.getRegistrations();
        await Promise.all(regs.map(function (reg) { return reg.unregister(); }));
    }

    async function needsServiceWorkerMigration() {
        return !localStorage.getItem(SW_MIGRATION_KEY);
    }

    async function migrateServiceWorker() {
        await clearServiceWorkerState();
        localStorage.setItem(SW_MIGRATION_KEY, '1');
        window.location.reload();
    }

    function activateWaitingWorker(reg) {
        if (reg.waiting) {
            reg.waiting.postMessage({ type: 'SKIP_WAITING' });
        }
    }

    function registerServiceWorker() {
        navigator.serviceWorker.addEventListener('controllerchange', function () {
            if (refreshingForSw) return;
            refreshingForSw = true;
            window.location.reload();
        });

        navigator.serviceWorker.register('/sw.js?v=' + assetVersion)
            .then(function (reg) {
                console.log('SW registered:', reg.scope);
                if (reg.waiting) {
                    activateWaitingWorker(reg);
                }
                reg.addEventListener('updatefound', function () {
                    var installing = reg.installing;
                    if (!installing) return;
                    installing.addEventListener('statechange', function () {
                        if (installing.state === 'installed' && navigator.serviceWorker.controller) {
                            activateWaitingWorker(reg);
                        }
                    });
                });
                reg.update();
            })
            .catch(function (err) {
                console.error('SW registration failed:', err);
            });
    }

    async function initServiceWorker() {
        try {
            if (!serviceWorkerEnabled) {
                await clearServiceWorkerState();
                return;
            }
            if (await needsServiceWorkerMigration()) {
                await migrateServiceWorker();
                return;
            }
            registerServiceWorker();
        } catch (err) {
            console.error('SW migration check failed:', err);
            if (serviceWorkerEnabled) {
                registerServiceWorker();
            }
        }
    }

    if (document.readyState === 'complete') {
        initServiceWorker();
    } else {
        window.addEventListener('load', initServiceWorker);
    }
})();
