@file:OptIn(ExperimentalWasmJsInterop::class)

package tassic.platform

import kotlin.js.Promise
import kotlinx.coroutines.await

/** localStorage-backed key/value persistence (offline-first PWA store). */
fun lsGet(key: String): String? = js("localStorage.getItem(key)")

fun lsSet(key: String, value: String): Unit = js("localStorage.setItem(key, value)")

fun lsRemove(key: String): Unit = js("localStorage.removeItem(key)")

/** High-resolution wall clock (ms since epoch, UTC). */
fun jsNow(): Double = js("Date.now()")

/**
 * Minutes that must be ADDED to local time to get UTC, exactly as
 * `Date.prototype.getTimezoneOffset()` defines it (so UTC+2 reports -120).
 *
 * Everything in [tassic.data.T] used to derive "today", weekday tags and
 * reminder hours straight from the UTC epoch, which silently shifted the whole
 * app by the device's UTC offset: in UTC+2 the day rolled over at 02:00 local
 * and a routine set for 06:00 only fired at 08:00. Reading the real offset here
 * lets the calendar maths run on local wall-clock time.
 */
fun jsTimezoneOffsetMinutes(): Double = js("new Date().getTimezoneOffset()")

/** Opens a URL in a new browser tab (used for career resource links, product links). */
fun openUrl(url: String): Unit = js("window.open(url, '_blank', 'noopener,noreferrer')")

/** Reads a query-string parameter from the current page URL (used for TWA app-shortcut deep links). */
fun queryParam(name: String): String? = js(
    "new URLSearchParams(window.location.search).get(name)"
)

/** Removes the HTML splash overlay once Compose has painted its first frame. */
fun hideSplash(): Unit = js("(()=>{ const el = document.getElementById('splash'); if (el) el.remove(); })()")

/** Fetches a same-origin resource and resolves it as a data: URL (used to load logo.png). */
fun fetchAsDataUrl(path: String): Promise<JsString?> = js(
    "fetch(path).then(r => r.blob()).then(b => new Promise((res, rej) => { const fr = new FileReader(); fr.onload = () => res(fr.result); fr.onerror = rej; fr.readAsDataURL(b); }))"
)

/** Suspend-friendly helper: await a JS promise, mapping failures to null. */
suspend fun <T : JsAny?> Promise<T>.awaitOrNull(): T? = try {
    await()
} catch (_: Throwable) {
    null
}
