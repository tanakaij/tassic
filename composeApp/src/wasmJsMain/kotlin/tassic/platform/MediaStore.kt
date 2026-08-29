@file:OptIn(ExperimentalWasmJsInterop::class)

package tassic.platform

import kotlin.js.Promise

private fun jsPut(key: String, dataUrl: String): Promise<JsAny?> = js(
    """new Promise((res, rej) => {
        const rq = indexedDB.open('tassic-media', 1);
        rq.onupgradeneeded = () => { rq.result.createObjectStore('images'); };
        rq.onerror = () => rej(rq.error);
        rq.onsuccess = () => {
            const tx = rq.result.transaction('images', 'readwrite');
            tx.objectStore('images').put(dataUrl, key);
            tx.oncomplete = () => res(null);
            tx.onerror = () => rej(tx.error);
        };
    })"""
)

private fun jsGet(key: String): Promise<JsString?> = js(
    """new Promise((res, rej) => {
        const rq = indexedDB.open('tassic-media', 1);
        rq.onupgradeneeded = () => { rq.result.createObjectStore('images'); };
        rq.onerror = () => rej(rq.error);
        rq.onsuccess = () => {
            const q = rq.result.transaction('images').objectStore('images').get(key);
            q.onsuccess = () => res(q.result || null);
            q.onerror = () => rej(q.error);
        };
    })"""
)

private fun jsDelete(key: String): Promise<JsAny?> = js(
    """new Promise((res, rej) => {
        const rq = indexedDB.open('tassic-media', 1);
        rq.onupgradeneeded = () => { rq.result.createObjectStore('images'); };
        rq.onerror = () => rej(rq.error);
        rq.onsuccess = () => {
            const tx = rq.result.transaction('images', 'readwrite');
            tx.objectStore('images').delete(key);
            tx.oncomplete = () => res(null);
            tx.onerror = () => rej(tx.error);
        };
    })"""
)

/**
 * Opens the picker (camera on mobile, gallery elsewhere), downscales the chosen
 * image to fit [maxDimension] and re-encodes it as a JPEG data URL.
 *
 * The downscale is not optional. A modern phone photo is 3–6 MB; a journal with
 * thirty of them at full size would blow past the origin's storage quota and
 * start getting evicted, taking the voice notes with it. Long edge capped and
 * re-encoded at 0.72 quality puts a typical entry photo around 120 KB, which is
 * indistinguishable at the size it will ever be displayed.
 *
 * EXIF orientation is honoured by the browser's own image decoding for the
 * common case; a rotated result on some older Android cameras is a known and
 * accepted limitation rather than a reason to ship an EXIF parser.
 */
private fun jsPickImage(maxDimension: Int, quality: Double): Promise<JsString?> = js(
    """new Promise((resolve) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.onchange = () => {
            const file = input.files && input.files[0];
            if (!file) { resolve(null); return; }
            const reader = new FileReader();
            reader.onerror = () => resolve(null);
            reader.onload = () => {
                const img = new Image();
                img.onerror = () => resolve(null);
                img.onload = () => {
                    try {
                        let w = img.naturalWidth || img.width;
                        let h = img.naturalHeight || img.height;
                        const longest = Math.max(w, h);
                        if (longest > maxDimension) {
                            const scale = maxDimension / longest;
                            w = Math.round(w * scale);
                            h = Math.round(h * scale);
                        }
                        const canvas = document.createElement('canvas');
                        canvas.width = w;
                        canvas.height = h;
                        const ctx = canvas.getContext('2d');
                        ctx.drawImage(img, 0, 0, w, h);
                        resolve(canvas.toDataURL('image/jpeg', quality));
                    } catch (e) {
                        resolve(null);
                    }
                };
                img.src = reader.result;
            };
            reader.readAsDataURL(file);
        };
        input.click();
    })"""
)

/**
 * Durable image storage for journal photos.
 *
 * Separate IndexedDB database from [AudioStore] rather than a second object
 * store inside it: adding a store to an existing database means a version bump
 * and an upgrade path for every install that already has clips, and there is no
 * benefit to the shared database that would pay for that risk.
 */
object MediaStore {

    /** Long-edge cap for stored photos, in pixels. */
    const val MAX_DIMENSION = 1600

    fun put(key: String, dataUrl: String): Promise<JsAny?> = jsPut(key, dataUrl)

    fun get(key: String): Promise<JsString?> = jsGet(key)

    fun delete(key: String): Promise<JsAny?> = jsDelete(key)

    /** Picker + downscale in one step; resolves null if cancelled or unreadable. */
    fun pickImage(maxDimension: Int = MAX_DIMENSION, quality: Double = 0.72): Promise<JsString?> =
        jsPickImage(maxDimension, quality)
}
