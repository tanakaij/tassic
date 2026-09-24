@file:OptIn(ExperimentalWasmJsInterop::class)

package tassic.platform

import kotlin.js.Promise

private fun jsOpen(): Promise<JsAny?> = js(
    """new Promise((res, rej) => {
        const rq = indexedDB.open('tassic-audio', 1);
        rq.onupgradeneeded = () => { rq.result.createObjectStore('clips'); };
        rq.onerror = () => rej(rq.error);
        rq.onsuccess = () => res(rq.result);
    })"""
)

private fun jsPut(key: String, dataUrl: String): Promise<JsAny?> = js(
    """new Promise((res, rej) => {
        const rq = indexedDB.open('tassic-audio', 1);
        rq.onupgradeneeded = () => { rq.result.createObjectStore('clips'); };
        rq.onerror = () => rej(rq.error);
        rq.onsuccess = () => {
            const tx = rq.result.transaction('clips', 'readwrite');
            tx.objectStore('clips').put(dataUrl, key);
            tx.oncomplete = () => res(null);
            tx.onerror = () => rej(tx.error);
        };
    })"""
)

private fun jsGet(key: String): Promise<JsString?> = js(
    """new Promise((res, rej) => {
        const rq = indexedDB.open('tassic-audio', 1);
        rq.onupgradeneeded = () => { rq.result.createObjectStore('clips'); };
        rq.onerror = () => rej(rq.error);
        rq.onsuccess = () => {
            const q = rq.result.transaction('clips').objectStore('clips').get(key);
            q.onsuccess = () => res(q.result || null);
            q.onerror = () => rej(q.error);
        };
    })"""
)

private fun jsDelete(key: String): Promise<JsAny?> = js(
    """new Promise((res, rej) => {
        const rq = indexedDB.open('tassic-audio', 1);
        rq.onupgradeneeded = () => { rq.result.createObjectStore('clips'); };
        rq.onerror = () => rej(rq.error);
        rq.onsuccess = () => {
            const tx = rq.result.transaction('clips', 'readwrite');
            tx.objectStore('clips').delete(key);
            tx.oncomplete = () => res(null);
            tx.onerror = () => rej(tx.error);
        };
    })"""
)

/**
 * Durable audio clip storage in IndexedDB (data: URLs are too large for the
 * 5 MB localStorage quota). Journal rows only keep the clip key.
 */
object AudioStore {

    private const val DB = "tassic-audio"
    private const val STORE = "clips"

    fun open(): Promise<JsAny?> = jsOpen()

    fun put(key: String, dataUrl: String): Promise<JsAny?> = jsPut(key, dataUrl)

    fun get(key: String): Promise<JsString?> = jsGet(key)

    fun delete(key: String): Promise<JsAny?> = jsDelete(key)
}
