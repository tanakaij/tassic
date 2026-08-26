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

private fun jsShow(title: String, body: String, tag: String): Unit = js(
    """(()=>{
        if (typeof Notification === 'undefined') return;

        var opts = {
            body: body,
            // Absolute URLs resolved against the service worker scope. Relative
            // paths were resolved against whatever the SW's own location was,
            // which broke the icon (and on some Android builds made the whole
            // showNotification call reject) once the app was served from a
            // GitHub Pages sub-path such as /Tassic/.
            icon: new URL('icons/icon-192.png', self.location ? self.location.href : location.href).href,
            badge: new URL('icons/icon-192.png', self.location ? self.location.href : location.href).href,
            // Every notification used to share the single tag 'tassic', so each
            // new reminder REPLACED the previous one instead of stacking - two
            // reminders due in the same session showed up as one. Tag per
            // notification instead, and keep renotify (which requires a tag).
            tag: tag,
            renotify: true,
            requireInteraction: false,
            data: { url: './' }
        };

        function viaPage() {
            try { new Notification(title, opts); } catch (e) {}
        }

        function viaWorker() {
            // Inside a TWA, Android's notification delegation only intercepts
            // notifications shown via the service worker's registration - a
            // plain `new Notification()` from page context is not delegated to
            // the real Android tray and silently does nothing.
            if (!('serviceWorker' in navigator)) { viaPage(); return; }

            // navigator.serviceWorker.ready never settles when no worker ever
            // takes control (private windows, a failed registration, the very
            // first load before activation). The old code awaited it with no
            // timeout, so `show()` simply never ran. Race it instead.
            var settled = false;
            var timer = setTimeout(function () {
                if (!settled) { settled = true; viaPage(); }
            }, 1500);

            navigator.serviceWorker.ready.then(function (reg) {
                if (settled) return;
                settled = true;
                clearTimeout(timer);
                try {
                    reg.showNotification(title, opts);
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

        // Previously this bailed out whenever permission !== 'granted', which
        // meant a device that had simply never been asked (permission ===
        // 'default') dropped every single reminder without a prompt and without
        // a trace. Ask, then show.
        if (Notification.permission === 'granted') {
            viaWorker();
        } else if (Notification.permission === 'default') {
            Promise.resolve(Notification.requestPermission()).then(function (p) {
                if (p === 'granted') viaWorker();
            }).catch(function () {});
        }
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

/**
 * Web Notification API bridge. The service worker (sw.js) additionally listens
 * for `push` events so scheduled triggers survive page reloads.
 */
object Notifications {

    fun supported(): Boolean = jsSupported()

    fun permission(): String = jsPermission()

    fun request(): Promise<JsString?> = jsRequest()

    /** Shows a notification, requesting permission first if it was never asked for. */
    fun show(title: String, body: String) {
        // A per-notification tag keeps concurrent reminders from collapsing into
        // one entry in the tray.
        val tag = "tassic-" + title.hashCode().toString()
        jsShow(title, body, tag)
    }
}

/**
 * Pushes live "Tassic Today" numbers to sw.js, which re-renders any installed
 * home-screen / dashboard widget instance via the Widgets API.
 */
object Widgets {
    fun push(dataJson: String) = jsSyncWidgets(dataJson)
}
