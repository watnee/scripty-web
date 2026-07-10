const CACHE_NAME = 'scripty-cache-v24';
const ASSETS_TO_CACHE = [
  '/offline.html',
  '/offline-project.html',
  '/css/missing.min.css',
  '/css/scripty.css',
  '/js/htmx.min.js',
  '/js/_hyperscript.min.js',
  '/js/shortcuts.js',
  '/js/help-center.js',
  '/js/csrf.js',
  '/js/text-size.js',
  '/js/block-empty-guard.js',
  '/js/element-type.js',
  '/js/focus-mode.js',
  '/js/outline-mode.js',
  '/js/full-width-toggle.js',
  '/js/toolbar-toggle.js',
  '/js/import-script.js',
  '/js/fountain-power.js',
  '/js/vendor/typo.js',
  '/js/spellcheck.js',
  '/js/offline-store.js',
  '/js/offline.js',
  '/js/offline-edit.js',
  '/dictionaries/en_US/en_US.aff',
  '/dictionaries/en_US/en_US.dic',
  '/favicon.ico',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
  '/manifest.json'
];

function isOnlineProbe(url) {
  return url.searchParams.has('scripty-online-probe');
}

function isStaticAsset(url) {
  return url.pathname.startsWith('/css/') ||
    url.pathname.startsWith('/js/') ||
    url.pathname.startsWith('/dictionaries/') ||
    url.pathname.startsWith('/icons/') ||
    url.pathname.startsWith('/fonts/') ||
    url.pathname === '/favicon.ico' ||
    url.pathname === '/manifest.json' ||
    url.pathname === '/offline.html' ||
    url.pathname === '/offline-project.html';
}

function isProjectShowNavigation(url, request) {
  return request.mode === 'navigate' && url.pathname === '/project/show';
}

function bareAssetRequest(url) {
  return new Request(url.origin + url.pathname, { credentials: 'same-origin' });
}

async function matchCached(request, options) {
  const exact = await caches.match(request, options);
  if (exact) {
    return exact;
  }
  const url = new URL(request.url);
  if (!url.search) {
    return undefined;
  }
  // Pages request assets as /js/foo.js?v=N; precache stores /js/foo.js.
  return caches.match(bareAssetRequest(url), options);
}

async function putInCache(request, response) {
  if (!response || response.status !== 200) {
    return;
  }
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) {
    return;
  }
  // Never put authenticated / private HTML into the Cache API.
  if (!isStaticAsset(url)) {
    return;
  }
  const cache = await caches.open(CACHE_NAME);
  await cache.put(request, response.clone());
  // Also store under the bare pathname so ignoreSearch / offline fallbacks work.
  if (url.search) {
    await cache.put(bareAssetRequest(url), response.clone());
  }
}

async function navigationFallback(request) {
  const url = new URL(request.url);
  try {
    return await fetch(request);
  } catch (err) {
    if (isProjectShowNavigation(url, request)) {
      const projectShell = await caches.match('/offline-project.html');
      if (projectShell) {
        return projectShell;
      }
    }
    const offlinePage = await caches.match('/offline.html');
    if (offlinePage) {
      return offlinePage;
    }
    throw err;
  }
}

async function precacheAssets() {
  const cache = await caches.open(CACHE_NAME);
  await Promise.all(ASSETS_TO_CACHE.map(async (url) => {
    try {
      await cache.add(url);
    } catch (err) {
      console.warn('[Service Worker] Failed to pre-cache', url, err);
    }
  }));
}

self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }
});

async function notifyClientsToSync() {
  const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  clients.forEach((client) => {
    client.postMessage({ type: 'SCRIPTY_SYNC_OUTBOX' });
  });
}

self.addEventListener('sync', (event) => {
  if (event.tag === 'scripty-sync-outbox') {
    event.waitUntil(notifyClientsToSync());
  }
});

self.addEventListener('install', (event) => {
  event.waitUntil(
    precacheAssets().then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => Promise.all(
      cacheNames.map((cache) => {
        if (cache !== CACHE_NAME) {
          console.log('[Service Worker] Removing old cache', cache);
          return caches.delete(cache);
        }
        return undefined;
      })
    )).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') {
    return;
  }

  const url = new URL(event.request.url);

  // Connectivity probes must hit the network; never serve from cache.
  if (isOnlineProbe(url)) {
    event.respondWith(
      fetch(event.request).catch(() => new Response('', { status: 503, statusText: 'Offline' }))
    );
    return;
  }

  // Navigations: network only; on failure serve public offline shells (never cache HTML).
  if (event.request.mode === 'navigate') {
    event.respondWith(navigationFallback(event.request));
    return;
  }

  if (!isStaticAsset(url)) {
    return;
  }

  if (url.pathname.startsWith('/css/') || url.pathname.startsWith('/js/') || url.pathname.startsWith('/dictionaries/')) {
    event.respondWith(
      fetch(event.request)
        .then((response) => {
          putInCache(event.request, response);
          return response;
        })
        .catch(() => matchCached(event.request, { ignoreSearch: true }))
    );
    return;
  }

  event.respondWith(
    matchCached(event.request, { ignoreSearch: true }).then((cachedResponse) => {
      if (cachedResponse) {
        return cachedResponse;
      }
      return fetch(event.request).then((networkResponse) => {
        putInCache(event.request, networkResponse);
        return networkResponse;
      }).catch(() => {
        if (event.request.destination === 'image') {
          return caches.match('/icons/icon-192.png');
        }
        return undefined;
      });
    })
  );
});
