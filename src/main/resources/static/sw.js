const CACHE_NAME = 'scripty-cache-v12';
const ASSETS_TO_CACHE = [
  '/offline.html',
  '/css/scripty.css',
  '/js/_hyperscript.min.js',
  '/js/text-size.js',
  '/favicon.ico',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
  '/manifest.json',
  'https://unpkg.com/missing.css@1.1.3/dist/missing.min.css',
  'https://unpkg.com/htmx.org@2.0.4'
];

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

  if (url.pathname.startsWith('/h2-console') || url.pathname.startsWith('/api/')) {
    return;
  }

  if (url.pathname === '/scene/all' || url.pathname === '/scene/read' || url.pathname === '/project/read' || url.pathname === '/project/show') {
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

  if (!isStaticAsset && event.request.mode !== 'navigate') {
    return;
  }

  // HTML pages: always fetch fresh from network; only use cache when offline.
  if (event.request.mode === 'navigate') {
    event.respondWith(
      fetch(event.request)
        .then((response) => response)
        .catch(() => caches.match(event.request).then((cachedResponse) => cachedResponse || caches.match('/offline.html')))
    );
    return;
  }

  if (!isStaticAsset) {
    return;
  }

  // CSS/JS: network-first so updates apply immediately when online.
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
