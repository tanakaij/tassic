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

private fun jsShow(title: String, body: String): Unit = js(
    """(()=>{
        if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return;
        var opts = {
            body: body,
            icon: 'icons/icon-192.png',
            badge: 'icons/icon-192.png',
            tag: 'tassic',
            renotify: true,
            data: { url: './' }
        };
        // Inside a TWA, Android's notification delegation only intercepts
        // notifications shown via the service worker's registration —
        // a plain `new Notification()` from page context is not delegated
        // to the real Android notification tray and silently does nothing.
        // sw.js already exposes the matching self.registration.showNotification
        // call (used for real push events); route through it here too so
        // in-app reminders and the test button actually surface a system
        // notification. Falls back to the plain constructor for contexts
        // with no active service worker (e.g. a normal desktop browser tab).
        if ('serviceWorker' in navigator) {
            navigator.serviceWorker.ready.then(function (reg) {
                try {
                    reg.showNotification(title, opts);
                } catch (e) {
                    try { new Notification(title, opts); } catch (e2) {}
                }
            }).catch(function () {
                try { new Notification(title, opts); } catch (e) {}
            });
        } else {
            try { new Notification(title, opts); } catch (e) {}
        }
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

    fun show(title: String, body: String): Unit = jsShow(title, body)
}
