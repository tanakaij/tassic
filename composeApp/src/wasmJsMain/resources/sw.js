/* Tassic service worker — offline shell, reminder delivery, widgets, badging.
 *
 * The headline change over v3: this worker now owns reminder DELIVERY, not just
 * caching. Previously every reminder was fired from a setInterval on the page,
 * so nothing could arrive unless the app happened to be open — which is exactly
 * what "notifications don't work" looked like. The app now hands this worker a
 * rolling 7-day schedule, and the worker fires anything due on EVERY wake it
 * gets: periodic sync, one-off sync, push, a notification click, a navigation,
 * or an explicit nudge from the page.
 *
 * A PWA with no push server can't be woken on a precise schedule by anything.
 * This is the closest achievable approximation, and it is a very large step up
 * from page-only timers.
 */
const CACHE = 'tassic-cache-v4';

// Synthetic same-origin URLs used as a key/value store. The Cache API is the
// only storage a service worker and a page can both reach synchronously enough
// for this purpose (a worker has no localStorage at all).
const WIDGET_DATA_URL = './widgets/today-widget-data.json';
const WIDGET_TEMPLATE_URL = './widgets/today-widget-template.json';
const SCHEDULE_URL = './__tassic/schedule.json';
const FIRED_URL = './__tassic/fired.json';
const ACTIONS_URL = './__tassic/actions.json';
const LASTRUN_URL = './__tassic/lastrun.json';

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
    // given build are skipped by allSettled rather than failing the install —
    // one 404 in cache.addAll aborts the whole precache, which alone used to
    // leave installs with an empty cache and no offline launch.
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
            // Activation is itself a wake: catch up on anything missed while the
            // previous worker version was asleep.
            .then(() => checkReminders('activate'))
    );
});

/* ------------------------------------------------------- tiny JSON kv store */

async function kvRead(url, fallback) {
    try {
        const cached = await caches.match(url);
        if (cached) return await cached.json();
    } catch (e) { /* corrupt entry — fall through */ }
    return fallback;
}

async function kvWrite(url, value) {
    try {
        const cache = await caches.open(CACHE);
        await cache.put(
            url,
            new Response(JSON.stringify(value), {
                headers: { 'Content-Type': 'application/json' }
            })
        );
        return true;
    } catch (e) {
        return false;
    }
}

async function kvWriteRaw(url, rawJson) {
    try {
        const cache = await caches.open(CACHE);
        await cache.put(
            url,
            new Response(rawJson, { headers: { 'Content-Type': 'application/json' } })
        );
        return true;
    } catch (e) {
        return false;
    }
}

/* ------------------------------------------------------------------ fetch */

// Network with a deadline. A device with "no internet" is often still attached
// to a dead wifi/mobile connection, where fetch() neither resolves nor rejects
// for 30+ seconds. Awaiting that bare fetch hung the app on the splash screen
// instead of falling back to cache.
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

    // Internal kv keys are never real network resources.
    if (url.pathname.indexOf('/__tassic/') !== -1) {
        event.respondWith(
            caches.match(req).then((r) => r || new Response('null', {
                headers: { 'Content-Type': 'application/json' }
            }))
        );
        return;
    }

    // App navigations: network-first with a short deadline, then cached shell.
    if (req.mode === 'navigate') {
        // A navigation is a wake. Piggyback a delivery pass on it.
        event.waitUntil(checkReminders('navigate'));
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
                // Previously `.catch(() => cached)`, which resolved to undefined
                // for anything not yet cached — respondWith(undefined) is a hard
                // network error, so one uncached chunk killed the whole boot.
                .catch(() => cached || Response.error());
            return cached || refresh;
        })
    );
});

/* ------------------------------------------------------- reminder delivery */

function isQuiet(schedule, localHour) {
    if (!schedule || !schedule.quiet) return false;
    const start = schedule.quietStart;
    const end = schedule.quietEnd;
    if (start === end) return false;
    // Windows wrapping past midnight (22 → 07) need the OR form.
    return start <= end
        ? (localHour >= start && localHour < end)
        : (localHour >= start || localHour < end);
}

function localNow(schedule) {
    const offset = (schedule && typeof schedule.tzOffsetMs === 'number') ? schedule.tzOffsetMs : 0;
    return Date.now() + offset;
}

function showReminder(item, snoozeMinutes) {
    const icon = new URL('icons/icon-192.png', self.location.href).href;
    const opts = {
        body: item.body || '',
        icon: icon,
        badge: icon,
        // Unique per notification so concurrent reminders stack in the tray
        // instead of collapsing under one shared tag.
        tag: 'tassic-' + item.id,
        renotify: true,
        timestamp: Date.now(),
        data: {
            url: './',
            kind: item.kind,
            refId: item.refId,
            snoozeMinutes: snoozeMinutes || 15
        }
    };
    if (item.actions) {
        opts.actions = [
            { action: 'done', title: 'Mark done' },
            { action: 'snooze', title: 'Snooze' }
        ];
    }
    const title = item.kind === 'todo' ? ('Reminder: ' + item.title) : item.title;
    return self.registration.showNotification(title, opts).catch(function () {});
}

/**
 * The delivery pass. Called on every wake the worker gets.
 *
 * `fired` ids live in their own kv entry rather than inside the schedule,
 * because the page overwrites the schedule wholesale on every sync — keeping
 * the delivered set separate is what stops a re-synced schedule from re-firing
 * everything it already sent.
 */
async function checkReminders(reason) {
    if (Notification.permission !== 'granted') return 0;

    const schedule = await kvRead(SCHEDULE_URL, null);
    if (!schedule || !Array.isArray(schedule.items)) return 0;

    const fired = await kvRead(FIRED_URL, { ids: [] });
    const firedIds = new Set(fired.ids || []);

    const now = localNow(schedule);
    const hour = Math.floor((((now % 86400000) + 86400000) % 86400000) / 3600000);
    if (isQuiet(schedule, hour)) {
        await kvWrite(LASTRUN_URL, { at: Date.now(), reason: reason + ':quiet', shown: 0 });
        return 0;
    }

    // Anything more than 6 hours late is stale — retire it silently rather than
    // dumping a backlog of old alerts the moment the device comes back.
    const STALE_MS = 6 * 60 * 60 * 1000;

    let shown = 0;
    for (const item of schedule.items) {
        if (!item || typeof item.fireAt !== 'number') continue;
        if (item.fired) { firedIds.add(item.id); continue; }
        if (firedIds.has(item.id)) continue;
        if (now < item.fireAt) continue;

        if (now - item.fireAt > STALE_MS) {
            firedIds.add(item.id);
            continue;
        }

        await showReminder(item, schedule.snoozeMinutes);
        firedIds.add(item.id);
        shown++;

        // Tell the app this one has been delivered so it doesn't fire again
        // from the in-page loop on next launch.
        if (item.kind === 'todo') {
            await queueAction({ kind: 'todo', refId: item.refId, action: 'fired', at: Date.now() });
        }

        // Don't carpet-bomb the tray if the device has been offline for a while.
        if (shown >= 4) break;
    }

    // Keep the delivered set bounded.
    const ids = Array.from(firedIds).slice(-400);
    await kvWrite(FIRED_URL, { ids: ids });
    await kvWrite(LASTRUN_URL, { at: Date.now(), reason: reason, shown: shown });

    await refreshBadge(schedule);
    await renderAllWidgets();
    return shown;
}

async function refreshBadge(schedule) {
    try {
        if (!self.navigator || !self.navigator.setAppBadge) return;
        if (schedule && schedule.badge === false) {
            if (self.navigator.clearAppBadge) await self.navigator.clearAppBadge();
            return;
        }
        const data = await kvRead(WIDGET_DATA_URL, null);
        const count = data && typeof data.badge === 'number' ? data.badge : 0;
        if (count > 0) await self.navigator.setAppBadge(count);
        else if (self.navigator.clearAppBadge) await self.navigator.clearAppBadge();
    } catch (e) { /* unsupported */ }
}

/* ------------------------------------------------------------ action queue */

/**
 * A worker cannot write localStorage, and the Compose app cannot read the Cache
 * API synchronously — so notification-button actions are parked here and the
 * page drains them into localStorage on next load (see index.html), where the
 * Kotlin store replays them.
 */
async function queueAction(action) {
    const queue = await kvRead(ACTIONS_URL, []);
    const list = Array.isArray(queue) ? queue : [];
    list.push(action);
    await kvWrite(ACTIONS_URL, list.slice(-100));

    // If a client happens to be open, hand it over immediately too.
    try {
        const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
        clients.forEach((c) => c.postMessage({ type: 'TASSIC_ACTION', action: action }));
    } catch (e) { /* no clients */ }
}

/* ------------------------------------------------------------------- wakes */

self.addEventListener('periodicsync', (event) => {
    if (event.tag === 'tassic-reminders') {
        event.waitUntil(checkReminders('periodicsync'));
    } else if (event.tag === 'tassic-widget-refresh') {
        event.waitUntil(renderAllWidgets().then(() => checkReminders('widgetsync')));
    }
});

self.addEventListener('sync', (event) => {
    if (event.tag === 'tassic-catchup') {
        event.waitUntil(checkReminders('sync'));
    }
});

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
    event.waitUntil(
        show(title, body, tag).then(() => checkReminders('push'))
    );
});

self.addEventListener('notificationclick', (event) => {
    const data = event.notification.data || {};
    const action = event.action;
    event.notification.close();

    if (action === 'done' && data.kind) {
        event.waitUntil(
            queueAction({ kind: data.kind, refId: data.refId, action: 'done', at: Date.now() })
                .then(() => focusApp(false))
        );
        return;
    }

    if (action === 'snooze' && data.kind) {
        event.waitUntil(
            queueAction({ kind: data.kind, refId: data.refId, action: 'snooze', at: Date.now() })
                .then(() => reschedule(data.refId, data.snoozeMinutes || 15))
        );
        return;
    }

    event.waitUntil(focusApp(true));
});

/** Pushes one item's fireAt forward and un-fires it so it comes round again. */
async function reschedule(refId, minutes) {
    const schedule = await kvRead(SCHEDULE_URL, null);
    if (!schedule || !Array.isArray(schedule.items)) return;
    const fired = await kvRead(FIRED_URL, { ids: [] });
    const ids = new Set(fired.ids || []);

    schedule.items.forEach((item) => {
        if (item.kind === 'todo' && item.refId === refId) {
            item.fireAt = localNow(schedule) + minutes * 60000;
            item.fired = false;
            ids.delete(item.id);
        }
    });

    await kvWrite(SCHEDULE_URL, schedule);
    await kvWrite(FIRED_URL, { ids: Array.from(ids) });
}

function focusApp(openIfClosed) {
    return self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
        for (const client of list) {
            if ('focus' in client) return client.focus();
        }
        if (openIfClosed) return self.clients.openWindow('./');
        return undefined;
    });
}

function show(title, body, tag) {
    return self.registration.showNotification(title, {
        body,
        icon: new URL('icons/icon-192.png', self.location.href).href,
        badge: new URL('icons/icon-192.png', self.location.href).href,
        tag: tag || 'tassic',
        renotify: true,
        data: { url: './' }
    }).catch(function () {});
}

/* ---------------------------------------------------------------- widgets */
// The manifest declared a widget but nothing implemented the Widgets API
// lifecycle, so no instance was ever rendered or refreshed — it simply never
// appeared. These handlers are what the spec requires.
//
// Note for anyone reading this expecting an Android home-screen widget: the
// Widgets API only has a host on Windows (the Edge widgets board). On Android
// the equivalent surfaces a PWA can genuinely own are the app-icon badge and
// the pinned "Today at a glance" notification, both of which this worker also
// drives.

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
        focusTitle: 'No shape scheduled yet',
        headline: 'Open Tassic to sync',
        nextAction: 'Nothing pending',
        nextReason: '',
        momentum: '0',
        progressLabel: ''
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
    await kvWriteRaw(WIDGET_DATA_URL, json);
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

/* --------------------------------------------------------------- messages */

self.addEventListener('message', (event) => {
    const msg = event.data;

    if (msg === 'SKIP_WAITING' || (msg && msg.type === 'SKIP_WAITING')) {
        self.skipWaiting();
        return;
    }

    // The page reports every same-origin URL it actually loaded (hashed wasm
    // chunks, Compose resources, fonts) so they get cached even though the
    // worker wasn't controlling those first requests. Without this the very
    // first install cached the shell but NOT the compiled app, and the first
    // fully-offline launch hung on the splash screen forever.
    if (msg && msg.type === 'CACHE_URLS' && Array.isArray(msg.urls)) {
        event.waitUntil(
            caches.open(CACHE).then((cache) =>
                Promise.allSettled(
                    msg.urls
                        .filter((u) => {
                            try {
                                const x = new URL(u, self.location.href);
                                return x.origin === self.location.origin &&
                                    !x.pathname.endsWith('/sw.js') &&
                                    x.pathname.indexOf('/__tassic/') === -1;
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
        event.waitUntil(
            storeWidgetData(msg.data)
                .then(renderAllWidgets)
                .then(() => kvRead(SCHEDULE_URL, null))
                .then(refreshBadge)
        );
        return;
    }

    // The app's rolling 7-day reminder schedule.
    if (msg && msg.type === 'SET_SCHEDULE' && typeof msg.data === 'string') {
        event.waitUntil(
            kvWriteRaw(SCHEDULE_URL, msg.data).then(() => checkReminders('schedule'))
        );
        return;
    }

    if (msg && msg.type === 'CHECK_REMINDERS') {
        event.waitUntil(checkReminders('manual'));
        return;
    }

    // The page has replayed the queued actions into its own store.
    if (msg && msg.type === 'ACTIONS_DRAINED') {
        event.waitUntil(kvWrite(ACTIONS_URL, []));
        return;
    }

    // Diagnostics for the settings screen.
    if (msg && msg.type === 'PING' && event.ports && event.ports[0]) {
        Promise.all([
            kvRead(LASTRUN_URL, null),
            kvRead(SCHEDULE_URL, null),
            kvRead(FIRED_URL, { ids: [] })
        ]).then(([lastRun, schedule, fired]) => {
            event.ports[0].postMessage({
                version: CACHE,
                lastRun: lastRun,
                scheduled: schedule && schedule.items ? schedule.items.length : 0,
                delivered: (fired.ids || []).length,
                widgets: !!self.widgets
            });
        });
    }
});
