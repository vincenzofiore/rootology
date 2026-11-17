// Rootology's service worker (#134). The app needs the server (ADR 0001): nothing of a garden is kept
// offline. It keeps only the offline page, and shows it when a page cannot be loaded for lack of a network.
const CACHE = 'rootology-offline-v2';
const OFFLINE = '/offline.html';
const FILES = [OFFLINE];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
    .then(() => self.clients.claim()));
});

// Only page loads: every other request (Vaadin's, the map's, the icons') goes to the network untouched.
self.addEventListener('fetch', (event) => {
  if (event.request.mode !== 'navigate') {
    return;
  }
  event.respondWith(fetch(event.request).catch(() => caches.match(OFFLINE)));
});
