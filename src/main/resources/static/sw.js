const CACHE_NAME = 'scripty-cache-v13';
const ASSETS_TO_CACHE = [
  '/offline.html',
  '/css/missing.min.css',
  '/css/scripty.css',
  '/js/htmx.min.js',
  '/js/_hyperscript.min.js',
  '/js/text-size.js',
  '/js/block-empty-guard.js',
  '/js/focus-mode.js',
  '/js/toolbar-toggle.js',
  '/js/import-script.js',
  '/js/offline-store.js',
  '/js/offline.js',
  '/favicon.ico',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
  '/manifest.json'
];

function isCacheableAppRequest(url, request) {
  if (request.method !== 'GET') {
    return false;
  }
  if (url.pathname.startsWith('/h2-console') || url.pathname.startsWith('/api/')) {
    return false;
  }
  return request.mode === 'navigate'
    || url.pathname === '/'
    || url.pathname === '/project/show'
    || url.pathname === '/project/list'
    || url.pathname === '/project/showScript'
    || url.pathname === '/project/read'
    || url.pathname.startsWith('/scene/read')
    || url.pathname === '/help'
    || url.pathname === '/shortcuts';
}

async function networkFirstWithCache(request) {
  try {
    const response = await fetch(request);
    if (response && response.status === 200) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, response.clone());
    }
    return response;
  } catch (err) {
    const cachedResponse = await caches.match(request);
    if (cachedResponse) {
      return cachedResponse;
    }
    if (request.mode === 'navigate') {
      const offlinePage = await caches.match('/offline.html');
      if (offlinePage) {
        return offlinePage;
      }
    }
    throw err;
  }
}

self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }
});

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      console.log('[Service Worker] Pre-caching static assets');
      return cache.addAll(ASSETS_TO_CACHE);
    }).then(() => self.skipWaiting())
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

  if (isCacheableAppRequest(url, event.request)) {
    event.respondWith(networkFirstWithCache(event.request));
    return;
  }

  const isStaticAsset =
    url.pathname.startsWith('/css/') ||
    url.pathname.startsWith('/js/') ||
    url.pathname.startsWith('/icons/') ||
    url.pathname.startsWith('/fonts/') ||
    url.pathname === '/favicon.ico' ||
    url.pathname === '/manifest.json' ||
    url.pathname === '/offline.html';

  if (!isStaticAsset) {
    return;
  }

  if (url.pathname.startsWith('/css/') || url.pathname.startsWith('/js/')) {
    event.respondWith(
      fetch(event.request)
        .then((response) => {
          if (response && response.status === 200 && url.origin === self.location.origin) {
            const responseClone = response.clone();
            caches.open(CACHE_NAME).then((cache) => {
              cache.put(event.request, responseClone);
            });
          }
          return response;
        })
        .catch(() => caches.match(event.request))
    );
    return;
  }

  event.respondWith(
    caches.match(event.request).then((cachedResponse) => {
      if (cachedResponse) {
        return cachedResponse;
      }
      return fetch(event.request).then((networkResponse) => {
        if (networkResponse && networkResponse.status === 200 && url.origin === self.location.origin) {
          const responseClone = networkResponse.clone();
          caches.open(CACHE_NAME).then((cache) => {
            cache.put(event.request, responseClone);
          });
        }
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
