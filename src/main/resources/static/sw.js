const CACHE_NAME = 'scripty-cache-v2';
const ASSETS_TO_CACHE = [
  '/',
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

// On install, cache all static assets
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      console.log('[Service Worker] Pre-caching static assets');
      return cache.addAll(ASSETS_TO_CACHE);
    }).then(() => {
      return self.skipWaiting();
    })
  );
});

// On activate, clean up old caches
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames.map((cache) => {
          if (cache !== CACHE_NAME) {
            console.log('[Service Worker] Removing old cache', cache);
            return caches.delete(cache);
          }
        })
      );
    }).then(() => {
      return self.clients.claim();
    })
  );
});

// On fetch, handle routing and fallbacks
self.addEventListener('fetch', (event) => {
  // Only intercept GET requests
  if (event.request.method !== 'GET') return;

  const url = new URL(event.request.url);

  // Skip caching for H2 console or API endpoints that shouldn't be cached
  if (url.pathname.startsWith('/h2-console') || url.pathname.startsWith('/api/')) {
    return;
  }

  // Strategy for HTML page navigations (pages): Network-First, fall back to offline.html
  if (event.request.mode === 'navigate') {
    event.respondWith(
      fetch(event.request)
        .then((response) => {
          // If we got a valid response, clone and cache it for offline reading
          if (response.status === 200) {
            const responseClone = response.clone();
            caches.open(CACHE_NAME).then((cache) => {
              cache.put(event.request, responseClone);
            });
          }
          return response;
        })
        .catch(() => {
          // If network fetch fails, attempt to get it from cache or return offline.html
          return caches.match(event.request)
            .then((cachedResponse) => {
              return cachedResponse || caches.match('/offline.html');
            });
        })
    );
    return;
  }

  // Cache-First only for content that can't go stale: images, fonts, and
  // version-pinned CDN assets. Everything else (CSS, JS, htmx partials)
  // must be Network-First or edits never reach the browser.
  const isImmutable = ['image', 'font'].includes(event.request.destination)
    || url.origin !== self.location.origin;

  if (isImmutable) {
    event.respondWith(
      caches.match(event.request).then((cachedResponse) => {
        if (cachedResponse) {
          return cachedResponse;
        }
        return fetch(event.request).then((networkResponse) => {
          if (networkResponse && networkResponse.status === 200) {
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
        });
      })
    );
    return;
  }

  // Strategy for CSS, JS, and htmx partial GETs: Network-First, cache as offline fallback
  event.respondWith(
    fetch(event.request).then((networkResponse) => {
      if (networkResponse && networkResponse.status === 200) {
        const responseClone = networkResponse.clone();
        caches.open(CACHE_NAME).then((cache) => {
          cache.put(event.request, responseClone);
        });
      }
      return networkResponse;
    }).catch(() => {
      return caches.match(event.request);
    })
  );
});
