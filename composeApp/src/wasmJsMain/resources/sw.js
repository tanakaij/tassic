/* Tassic service worker - offline app shell, runtime caching, Web Push, widgets. */
// Bumped so every existing install picks up the fixes below (SW updates are
// keyed off byte-for-byte changes to this file).
const CACHE = 'tassic-cache-v3';

// Where the live widget payload is parked so it survives SW restarts.
const WIDGET_DATA_URL = './widgets/today-widget-data.json';
const WIDGET_TEMPLATE_URL = './widgets/today-widget-template.json';

// Static files whose names we know up front. NOTE: this list can never be
// complete on its own - the Kotlin/Wasm + webpack toolchain emits
// content-hashed chunks (and Compose packs its resources under
// composeResources/) whose filenames change every build, so hardcoding them
// here is guesswork. Anything missing is picked up at runtime instead, via the
// CACHE_URLS message the page sends with the real list of everything it
// loaded. That combination is what actually makes a cold offline launch work.
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
    WIDGET_TEMPLATE_URL,
    WIDGET_DATA_URL,
    // Best-effort guesses at the compiled app. Entries that don't exist for a
    // given build are skipped by allSettled rather than failing the install
    // (a single 404 in cache.addAll aborts the whole precache - that alone
    // used to leave installs with an empty cache and no offline launch).
    './skiko.js',
    './skiko.mjs',
    './skiko.wasm',
    './composeApp.js',
    './composeApp.wasm',
    './composeApp.uninstantiated.mjs'
];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE)
            .then((cache) => Promise.allSettled(SHELL.map((url) => cache.add(url))))
            .then(() => self.skipWaiting())
    );
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys()
            .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
            .then(() => self.clients.claim())
    );
});

/* ------------------------------------------------------------------ fetch */

// Network with a deadline. A device with "no internet" is often still attached
// to a dead wifi/mobile connection, where fetch() neither resolves nor rejects
// for 30+ seconds. The old navigation handler awaited that bare fetch, so the
// app hung on the splash screen instead of falling back to cache - which is
// exactly what "I can't log into the app without wifi" looks like.
function fetchWithTimeout(req, ms) {
    return new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('timeout')), ms);
        fetch(req).then(
            (res) => { clearTimeout(timer); resolve(res); },
            (err) => { clearTimeout(timer); reject(err); }
        );
    });
}

const OFFLINE_FALLBACK = new Response(
    '<!DOCTYPE html><meta charset="utf-8"><title>Tassic</title>' +
    '<body style="font-family:system-ui;padding:2rem;background:#BBD8EC;color:#0F2B4C">' +
    '<h1>Tassic is offline</h1><p>The app shell has not finished caching yet. ' +
    'Open Tassic once with a connection and it will work offline from then on.</p>',
    { headers: { 'Content-Type': 'text/html; charset=utf-8' } }
);

self.addEventListener('fetch', (event) => {
    const req = event.request;
    if (req.method !== 'GET') return;

    let url;
    try {
        url = new URL(req.url);
    } catch (e) {
        return;
    }
    if (url.origin !== self.location.origin) return;

    // App navigations: network-first with a short deadline, then cached shell.
    if (req.mode === 'navigate') {
        event.respondWith(
            fetchWithTimeout(req, 3000)
                .then((res) => {
                    if (res && res.ok) {
                        const copy = res.clone();
                        caches.open(CACHE).then((c) => c.put('./index.html', copy));
                    }
                    return res;
                })
                .catch(() =>
                    caches.match('./index.html')
                        .then((r) => r || caches.match('./'))
                        .then((r) => r || OFFLINE_FALLBACK.clone())
                )
        );
        return;
    }

    // Static assets: cache-first, refreshed in the background.
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
                // Previously `.catch(() => cached)`, which resolved to `undefined`
                // for anything not yet cached - respondWith(undefined) is a hard
                // network error, so one uncached chunk killed the whole boot.
                .catch(() => cached || Response.error());
            return cached || refresh;
        })
    );
});

/* ------------------------------------------------------------------- push */

self.addEventListener('push', (event) => {
    let title = 'Tassic reminder';
    let body = 'Time to check in with your routines.';
    let tag = 'tassic-push';
    try {
        if (event.data) {
            const data = event.data.json();
            title = data.title || title;
            body = data.body || body;
            if (data.tag) tag = data.tag;
        }
    } catch (e) {
        if (event.data) body = event.data.text();
    }
    event.waitUntil(show(title, body, tag));
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

function show(title, body, tag) {
    return self.registration.showNotification(title, {
        body,
        icon: new URL('icons/icon-192.png', self.location.href).href,
        badge: new URL('icons/icon-192.png', self.location.href).href,
        // Unique per notification so reminders stack instead of overwriting
        // each other under a single shared 'tassic' tag.
        tag: tag || 'tassic',
        renotify: true,
        data: { url: './' }
    });
}

/* ---------------------------------------------------------------- widgets */
// The manifest declared a widget but nothing ever implemented the Widgets API
// lifecycle, so no widget instance was ever rendered or refreshed - it simply
// never appeared. These handlers are what the spec requires.

async function readCachedJson(url, fallback) {
    try {
        const cached = await caches.match(url);
        if (cached) return await cached.json();
    } catch (e) { /* fall through */ }
    try {
        const res = await fetch(url);
        if (res && res.ok) return await res.json();
    } catch (e) { /* fall through */ }
    return fallback;
}

async function widgetPayload() {
    const template = await readCachedJson(WIDGET_TEMPLATE_URL, null);
    const data = await readCachedJson(WIDGET_DATA_URL, {
        dayLabel: 'Open Tassic to sync today\u2019s numbers',
        modulesDone: '0',
        openTodos: '0',
        streakDays: '0',
        focusTitle: 'No shape scheduled yet'
    });
    return {
        template: JSON.stringify(template),
        data: JSON.stringify(data)
    };
}

async function renderWidget(widget) {
    if (!self.widgets || !widget || !widget.definition) return;
    const payload = await widgetPayload();
    if (!payload.template) return;
    try {
        await self.widgets.updateByTag(widget.definition.tag, payload);
    } catch (e) { /* widget host went away */ }
}

async function renderAllWidgets() {
    if (!self.widgets) return;
    try {
        const installed = await self.widgets.getByTag('today');
        if (!installed) return;
        await renderWidget(installed);
    } catch (e) { /* not supported here */ }
}

async function storeWidgetData(json) {
    try {
        const cache = await caches.open(CACHE);
        await cache.put(
            WIDGET_DATA_URL,
            new Response(json, { headers: { 'Content-Type': 'application/json' } })
        );
    } catch (e) { /* quota or unsupported */ }
}

self.addEventListener('widgetinstall', (event) => {
    event.waitUntil(renderWidget(event.widget));
});

self.addEventListener('widgetresume', (event) => {
    event.waitUntil(renderWidget(event.widget));
});

self.addEventListener('widgetuninstall', (event) => {
    event.waitUntil(Promise.resolve());
});

self.addEventListener('widgetclick', (event) => {
    event.waitUntil(renderWidget(event.widget));
});

self.addEventListener('periodicsync', (event) => {
    if (event.tag === 'tassic-widget-refresh') event.waitUntil(renderAllWidgets());
});

/* --------------------------------------------------------------- messages */

self.addEventListener('message', (event) => {
    const msg = event.data;

    if (msg === 'SKIP_WAITING' || (msg && msg.type === 'SKIP_WAITING')) {
        self.skipWaiting();
        return;
    }

    // The page reports every same-origin URL it actually loaded (hashed wasm
    // chunks, Compose resources, fonts) so they get cached even though the
    // service worker wasn't controlling those first requests. Without this the
    // very first install cached the shell but NOT the compiled app, and the
    // first fully-offline launch hung on the splash screen forever.
    if (msg && msg.type === 'CACHE_URLS' && Array.isArray(msg.urls)) {
        event.waitUntil(
            caches.open(CACHE).then((cache) =>
                Promise.allSettled(
                    msg.urls
                        .filter((u) => {
                            try {
                                const x = new URL(u, self.location.href);
                                return x.origin === self.location.origin &&
                                    !x.pathname.endsWith('/sw.js');
                            } catch (e) {
                                return false;
                            }
                        })
                        .map((u) => cache.add(new Request(u, { cache: 'reload' })).catch(() => cache.add(u)))
                )
            )
        );
        return;
    }

    if (msg && msg.type === 'UPDATE_WIDGETS' && typeof msg.data === 'string') {
        event.waitUntil(storeWidgetData(msg.data).then(renderAllWidgets));
    }
});
