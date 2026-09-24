@file:OptIn(ExperimentalWasmJsInterop::class)

package tassic.platform

import kotlin.js.Promise

/**
 * Getting data in and out of the browser.
 *
 * Everything the app knows lives in localStorage, which is not durable storage:
 * a browser is free to evict it under pressure, "clear site data" wipes it
 * instantly, and no reinstall or device change carries it across. An app that
 * asks someone to record their life in it owes them a way to hold a copy.
 *
 * All of this is plain DOM work — no File System Access API — so it behaves the
 * same in the installed TWA, in Safari and in a desktop browser. Each js() body
 * is a single string literal on purpose: the intrinsic needs a constant, and
 * concatenating fragments is a good way to find that out at compile time.
 */

/** Triggers a download of [text] as [filename] via an object URL. */
fun downloadText(filename: String, text: String, mime: String): Unit = js("(() => { const blob = new Blob([text], { type: mime }); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = filename; document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 5000); })()")

/**
 * Opens the system file picker and resolves the chosen file's text, or null if
 * the user cancelled. A read failure resolves null too, so the caller has one
 * empty case to handle rather than two.
 */
fun pickTextFile(accept: String): Promise<JsString?> = js("new Promise((resolve) => { const input = document.createElement('input'); input.type = 'file'; input.accept = accept; input.onchange = () => { const file = input.files && input.files[0]; if (!file) { resolve(null); return; } const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = () => resolve(null); reader.readAsText(file); }; input.click(); })")

/**
 * Native share sheet where one exists, falling back to the clipboard.
 * Resolves "shared", "copied" or "unavailable" so the UI can say what actually
 * happened rather than claiming a success it cannot verify.
 */
fun shareText(title: String, text: String): Promise<JsString?> = js("(navigator.share ? navigator.share({ title: title, text: text }).then(() => 'shared').catch(() => 'unavailable') : (navigator.clipboard ? navigator.clipboard.writeText(text).then(() => 'copied').catch(() => 'unavailable') : Promise.resolve('unavailable')))")

/**
 * A short haptic tick. Android honours this; iOS Safari ignores it silently,
 * which is the correct degradation — nothing should depend on it firing.
 */
fun vibrate(ms: Int): Unit = js("(() => { if (navigator.vibrate) { try { navigator.vibrate(ms); } catch (e) {} } })()")

/** Two-pulse pattern used when a focus session ends. */
fun vibratePattern(): Unit = js("(() => { if (navigator.vibrate) { try { navigator.vibrate([120, 80, 120]); } catch (e) {} } })()")

/**
 * A short chime for the end of a focus session, synthesised with the Web Audio
 * API rather than shipped as an audio file — one fewer asset to cache, and it
 * cannot fail to load offline.
 */
fun chime(): Unit = js("(() => { try { const Ctx = window.AudioContext || window.webkitAudioContext; if (!Ctx) return; const ctx = new Ctx(); const now = ctx.currentTime; [880, 1320].forEach((freq, i) => { const osc = ctx.createOscillator(); const gain = ctx.createGain(); osc.type = 'sine'; osc.frequency.value = freq; gain.gain.setValueAtTime(0.0001, now + i * 0.18); gain.gain.exponentialRampToValueAtTime(0.25, now + i * 0.18 + 0.02); gain.gain.exponentialRampToValueAtTime(0.0001, now + i * 0.18 + 0.35); osc.connect(gain); gain.connect(ctx.destination); osc.start(now + i * 0.18); osc.stop(now + i * 0.18 + 0.4); }); setTimeout(() => ctx.close(), 1500); } catch (e) {} })()")

/** True when the page is running as an installed app rather than a browser tab. */
fun isStandalone(): Boolean = js("((window.matchMedia && window.matchMedia('(display-mode: standalone)').matches) === true || window.navigator.standalone === true)")

/**
 * Fetches a text document (used for .ics calendar subscriptions).
 *
 * Most calendar providers do not send CORS headers, so a browser will refuse
 * the read for a URL the user pastes in. That is a limitation of the platform,
 * not a bug to work around with a proxy — routing someone's private calendar
 * through a third party to dodge a security control would be a poor trade. The
 * failure resolves to null and the UI says to use the file import instead.
 */
fun fetchText(url: String): Promise<JsString?> = js("fetch(url, { redirect: 'follow' }).then(r => r.ok ? r.text() : null).catch(() => null)")
