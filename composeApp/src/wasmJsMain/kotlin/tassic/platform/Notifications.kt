@file:OptIn(ExperimentalWasmJsInterop::class)

package tassic.platform

import kotlin.js.Promise

private fun jsSupported(): Boolean = js("typeof Notification !== 'undefined'")

private fun jsPermission(): String = js(
    "typeof Notification === 'undefined' ? 'unsupported' : Notification.permission"
)

private fun jsRequest(): Promise<JsString?> = js(
    "typeof Notification === 'undefined' ? Promise.resolve('unsupported') : Promise.resolve(Notification.requestPermission())"
)

/**
 * Shows a notification.
 *
 * Everything routes through the service worker registration rather than
 * `new Notification()`. Two reasons: inside a TWA, Android only delegates
 * SW-shown notifications to the real system tray, and only SW notifications
 * can carry action buttons. Page-context `new Notification()` is kept purely
 * as a last-resort fallback for desktop browsers with no controlling worker.
 */
private fun jsShow(
    title: String,
    body: String,
    tag: String,
    kind: String,
    refId: Double,
    withActions: Boolean,
    ongoing: Boolean
): Unit = js(
    """(()=>{
        if (typeof Notification === 'undefined') return;

        var base = (self.location ? self.location.href : location.href);
        var icon = new URL('icons/icon-192.png', base).href;

        var opts = {
            body: body,
            icon: icon,
            badge: icon,
            // A tag per notification: every reminder previously shared the tag
            // 'tassic', so each new one REPLACED the last and two reminders in
            // one session showed up as one.
            tag: tag,
            renotify: !ongoing,
            silent: ongoing,
            requireInteraction: ongoing,
            timestamp: Date.now(),
            data: { url: './', kind: kind, refId: refId }
        };

        if (withActions) {
            opts.actions = [
                { action: 'done', title: 'Mark done' },
                { action: 'snooze', title: 'Snooze' }
            ];
        }

        function viaPage() {
            // No actions here — the page-context Notification constructor
            // rejects an options object containing `actions`.
            try {
                var pageOpts = {};
                for (var k in opts) { if (k !== 'actions') pageOpts[k] = opts[k]; }
                new Notification(title, pageOpts);
            } catch (e) {}
        }

        function viaWorker() {
            if (!('serviceWorker' in navigator)) { viaPage(); return; }

            // navigator.serviceWorker.ready never settles when no worker takes
            // control (private windows, failed registration, the first load
            // before activation). Awaiting it bare meant show() simply never
            // ran, so race it against a deadline.
            var settled = false;
            var timer = setTimeout(function () {
                if (!settled) { settled = true; viaPage(); }
            }, 1500);

            navigator.serviceWorker.ready.then(function (reg) {
                if (settled) return;
                settled = true;
                clearTimeout(timer);
                try {
                    reg.showNotification(title, opts).catch(function () { viaPage(); });
                } catch (e) {
                    viaPage();
                }
            }).catch(function () {
                if (settled) return;
                settled = true;
                clearTimeout(timer);
                viaPage();
            });
        }

        if (Notification.permission === 'granted') {
            viaWorker();
        } else if (Notification.permission === 'default') {
            // Only reachable from a user gesture in practice; browsers ignore
            // requestPermission() from a timer, which is why the settings screen
            // owns the real prompt.
            Promise.resolve(Notification.requestPermission()).then(function (p) {
                if (p === 'granted') viaWorker();
            }).catch(function () {});
        }
    })()"""
)

/** Dismisses the pinned summary notification. */
private fun jsClearOngoing(): Unit = js(
    """(()=>{
        if (!('serviceWorker' in navigator)) return;
        navigator.serviceWorker.ready.then(function (reg) {
            return reg.getNotifications({ tag: 'tassic-ongoing' });
        }).then(function (list) {
            (list || []).forEach(function (n) { n.close(); });
        }).catch(function () {});
    })()"""
)

private fun jsSyncWidgets(dataJson: String): Unit = js(
    """(()=>{
        if (!('serviceWorker' in navigator)) return;
        navigator.serviceWorker.ready.then(function (reg) {
            var target = reg.active || navigator.serviceWorker.controller;
            if (target) target.postMessage({ type: 'UPDATE_WIDGETS', data: dataJson });
        }).catch(function () {});
    })()"""
)

private fun jsPushSchedule(scheduleJson: String): Unit = js(
    """(()=>{
        if (!('serviceWorker' in navigator)) return;
        navigator.serviceWorker.ready.then(function (reg) {
            var target = reg.active || navigator.serviceWorker.controller;
            if (target) target.postMessage({ type: 'SET_SCHEDULE', data: scheduleJson });
        }).catch(function () {});
    })()"""
)

/** Asks the worker to run a delivery pass right now (used by the test button). */
private fun jsKickScheduler(): Unit = js(
    """(()=>{
        if (!('serviceWorker' in navigator)) return;
        navigator.serviceWorker.ready.then(function (reg) {
            var target = reg.active || navigator.serviceWorker.controller;
            if (target) target.postMessage({ type: 'CHECK_REMINDERS' });
        }).catch(function () {});
    })()"""
)

private fun jsSetBadge(count: Int): Unit = js(
    """(()=>{
        try {
            if (navigator.setAppBadge) {
                if (count > 0) navigator.setAppBadge(count); else navigator.clearAppBadge();
            }
        } catch (e) {}
    })()"""
)

private fun jsClearBadge(): Unit = js(
    "(()=>{ try { if (navigator.clearAppBadge) navigator.clearAppBadge(); } catch (e) {} })()"
)

/** Registers periodic background sync — the only "wake me later" primitive a PWA gets. */
private fun jsRegisterPeriodicSync(): Unit = js(
    """(()=>{
        if (!('serviceWorker' in navigator)) return;
        navigator.serviceWorker.ready.then(function (reg) {
            if (!reg.periodicSync) return;
            reg.periodicSync.register('tassic-reminders', { minInterval: 30 * 60 * 1000 })
                .catch(function () {});
            reg.periodicSync.register('tassic-widget-refresh', { minInterval: 60 * 60 * 1000 })
                .catch(function () {});
        }).catch(function () {});
        // A one-off sync fires on the next connectivity event, which is a
        // cheap extra chance to catch up on missed reminders.
        navigator.serviceWorker.ready.then(function (reg) {
            if (reg.sync) reg.sync.register('tassic-catchup').catch(function () {});
        }).catch(function () {});
    })()"""
)

/** Diagnostics: a compact JSON string describing every delivery-path capability. */
private fun jsCapabilities(): String = js(
    """(()=>{
        function has(x) { return x ? 'true' : 'false'; }
        var standalone = false;
        try {
            standalone = window.matchMedia('(display-mode: standalone)').matches ||
                window.navigator.standalone === true;
        } catch (e) {}
        var sw = ('serviceWorker' in navigator);
        var controlled = sw && !!navigator.serviceWorker.controller;
        return '{' +
            '"notifications":' + has(typeof Notification !== 'undefined') + ',' +
            '"permission":"' + (typeof Notification === 'undefined' ? 'unsupported' : Notification.permission) + '",' +
            '"serviceWorker":' + has(sw) + ',' +
            '"controlled":' + has(controlled) + ',' +
            '"periodicSync":' + has(sw && 'periodicSync' in ServiceWorkerRegistration.prototype) + ',' +
            '"backgroundSync":' + has(sw && 'sync' in ServiceWorkerRegistration.prototype) + ',' +
            '"badging":' + has(!!navigator.setAppBadge) + ',' +
            '"widgets":' + has(sw && 'widgets' in ServiceWorkerRegistration.prototype) + ',' +
            '"push":' + has(sw && 'PushManager' in window) + ',' +
            '"installed":' + has(standalone) +
            '}';
    })()"""
)

/**
 * Web Notification bridge.
 *
 * All delivery goes through sw.js so notifications survive the page, carry
 * action buttons, and reach the Android system tray inside a TWA.
 */
object Notifications {

    fun supported(): Boolean = jsSupported()

    fun permission(): String = jsPermission()

    fun request(): Promise<JsString?> = jsRequest()

    /** Plain informational notification with no buttons. */
    fun show(title: String, body: String) {
        jsShow(title, body, tagFor(title), "info", 0.0, withActions = false, ongoing = false)
    }

    /** Task reminder carrying "Mark done" / "Snooze" buttons. */
    fun showTask(title: String, body: String, refId: Long) {
        jsShow("Reminder: $title", body, "tassic-todo-$refId", "todo", refId.toDouble(), true, false)
    }

    /** Faith-routine reminder carrying a "Mark done" button. */
    fun showRoutine(title: String, body: String, refId: Long) {
        jsShow("Tassic \u00b7 $title", body, "tassic-routine-$refId", "routine", refId.toDouble(), true, false)
    }

    /**
     * The pinned "Today at a glance" notification.
     *
     * On Android this is the only widget-shaped surface a PWA can own, so it
     * stands in for the home-screen widget the Widgets API can't deliver
     * outside Windows/Edge. Silent and sticky so it reads as a panel rather
     * than an interruption.
     */
    fun showOngoing(title: String, body: String, nextAction: String?) {
        val text = if (nextAction != null) "$body\nNext: $nextAction" else body
        jsShow(title, text, "tassic-ongoing", "ongoing", 0.0, withActions = false, ongoing = true)
    }

    fun clearOngoing() = jsClearOngoing()

    /** Asks the worker to run a delivery pass immediately. */
    fun kickScheduler() = jsKickScheduler()

    /** Registers periodic + one-off background sync for closed-app delivery. */
    fun registerBackgroundDelivery() = jsRegisterPeriodicSync()

    /** Raw capability JSON for the settings diagnostics panel. */
    fun capabilitiesJson(): String = jsCapabilities()

    private fun tagFor(title: String) = "tassic-" + title.hashCode().toString()
}

/** Hands the reminder schedule to sw.js so it can fire while the app is closed. */
object ReminderBridge {
    fun push(scheduleJson: String) = jsPushSchedule(scheduleJson)
}

/** Badging API — the outstanding count on the installed app icon. */
object Badge {
    fun set(count: Int) = jsSetBadge(count)
    fun clear() = jsClearBadge()
}

/**
 * Pushes live "Tassic Today" numbers to sw.js, which re-renders any installed
 * widget instance via the Widgets API.
 */
object Widgets {
    fun push(dataJson: String) = jsSyncWidgets(dataJson)
}
