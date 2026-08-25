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
    "(()=>{ if (typeof Notification !== 'undefined' && Notification.permission === 'granted') { try { new Notification(title, { body: body, icon: 'icons/icon-192.png', badge: 'icons/icon-192.png', tag: 'tassic' }); } catch (e) {} } })()"
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
