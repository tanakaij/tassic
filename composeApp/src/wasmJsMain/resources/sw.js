/* Tassic service worker — offline app shell, runtime caching, Web Push. */
const CACHE = 'tassic-cache-v1';

const SHELL = [
    './',
    './index.html',
    './styles.css',
    './manifest.json',
    './logo.png',
    './icons/icon-192.png',
    './icons/icon-512.png',
    './icons/maskable-512.png',
    './icons/favicon.png'
];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE).then((cache) =>
            Promise.allSettled(SHELL.map((url) => cache.add(url)))
        ).then(() => self.skipWaiting())
    );
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys()
            .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
            .then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', (event) => {
    const req = event.request;
    if (req.method !== 'GET') return;

    const url = new URL(req.url);
    if (url.origin !== self.location.origin) return;

    // App navigations: network first, fall back to the cached shell (offline).
    if (req.mode === 'navigate') {
        event.respondWith(
            fetch(req)
                .then((res) => {
                    const copy = res.clone();
                    caches.open(CACHE).then((c) => c.put('./index.html', copy));
                    return res;
                })
                .catch(() => caches.match('./index.html').then((r) => r || caches.match('./')))
        );
        return;
    }

    // Static assets: stale-while-revalidate (instant + offline after first load).
    event.respondWith(
        caches.match(req).then((cached) => {
            const refresh = fetch(req)
                .then((res) => {
                    if (res && res.status === 200) {
                        const copy = res.clone();
                        caches.open(CACHE).then((c) => c.put(req, copy));
                    }
                    return res;
                })
                .catch(() => cached);
            return cached || refresh;
        })
    );
});

/* ---- Web Push: scheduled triggers from a push service hit this listener. ---- */
self.addEventListener('push', (event) => {
    let title = 'Tassic reminder';
    let body = 'Time to check in with your routines.';
    try {
        if (event.data) {
            const data = event.data.json();
            title = data.title || title;
            body = data.body || body;
        }
    } catch (e) {
        if (event.data) body = event.data.text();
    }
    event.waitUntil(show(title, body));
});

self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    event.waitUntil(
        self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
            for (const client of list) {
                if ('focus' in client) return client.focus();
            }
            return self.clients.openWindow('./');
        })
    );
});

self.addEventListener('message', (event) => {
    if (event.data === 'SKIP_WAITING') self.skipWaiting();
});

function show(title, body) {
    return self.registration.showNotification(title, {
        body,
        icon: './icons/icon-192.png',
        badge: './icons/icon-192.png',
        tag: 'tassic',
        renotify: true,
        data: { url: './' }
    });
}
