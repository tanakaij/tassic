/* Tassic service worker - offline app shell, runtime caching, Web Push. */
// Bumped so every existing install picks up the shell-precache fix below
// (SW updates are keyed off byte-for-byte changes to this file).
const CACHE = 'tassic-cache-v2';

const SHELL = [
    './',
    './index.html',
    './styles.css',
    './manifest.json',
    './logo.png',
    './icons/icon-192.png',
    './icons/icon-512.png',
    './icons/maskable-512.png',
    './icons/favicon.png',
    './widgets/today-widget-template.json',
    './widgets/today-widget-data.json',
    // The compiled Compose/Wasm app itself. Previously missing from this
    // list, which caused a real "can't open the app offline" bug: the
    // service worker only starts controlling fetches *after* it activates
    // (`clients.claim()`), which happens after the very first page's own
    // <script> requests for these files have already gone out uncontrolled.
    // On a device that is installed once and never fully reloaded online
    // again, that meant the app shell's own JS/Wasm binaries never made it
    // into the cache at all - so the first fully-offline open just hung on
    // the splash screen forever (reported as "can't log into the app").
    // Precaching them explicitly here means the SW fetches and caches them
    // itself during `install`, independent of page/client timing.
    // Filenames are the stable ones the toolchain/index.html actually use
    // (see composeApp/build.gradle.kts `outputFileName` and index.html);
    // any entry that doesn't exist for a given build is skipped safely by
    // Promise.allSettled below rather than failing the whole install.
    './skiko.js',
    './skiko.wasm',
    './composeApp.js',
    './composeApp.wasm'
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
