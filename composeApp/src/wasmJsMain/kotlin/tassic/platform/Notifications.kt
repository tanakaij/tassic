@file:OptIn(ExperimentalWasmJsInterop::class)

package tassic.platform

import kotlin.js.Promise

/**
 * Web Notification API bridge. The service worker (sw.js) additionally listens
 * for `push` events so scheduled triggers survive page reloads.
 */
object Notifications {

    fun supported(): Boolean = js("typeof Notification !== 'undefined'")

    fun permission(): String = js(
        "typeof Notification === 'undefined' ? 'unsupported' : Notification.permission"
    )

    fun request(): Promise<JsString?> = js(
        "typeof Notification === 'undefined' ? Promise.resolve('unsupported') : Promise.resolve(Notification.requestPermission())"
    )

    fun show(title: String, body: String): Unit = js(
        "(()=>{ if (typeof Notification !== 'undefined' && Notification.permission === 'granted') { try { new Notification(title, { body: body, icon: 'icons/icon-192.png', badge: 'icons/icon-192.png', tag: 'tassic' }); } catch (e) {} } })()"
    )
}
