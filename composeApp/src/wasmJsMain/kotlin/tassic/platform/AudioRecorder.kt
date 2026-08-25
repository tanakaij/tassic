@file:OptIn(ExperimentalWasmJsInterop::class)

package tassic.platform

import kotlin.js.Promise

/**
 * Multimodal journal voice notes via the Web MediaRecorder API.
 *
 * [start] returns a JS controller object; [stop] resolves with a data: URL of
 * the recorded clip which is persisted into IndexedDB by [AudioStore].
 */
object AudioRecorder {

    fun isSupported(): Boolean = js(
        "typeof MediaRecorder !== 'undefined' && typeof navigator !== 'undefined' && navigator.mediaDevices !== undefined && typeof navigator.mediaDevices.getUserMedia === 'function'"
    )

    /** Best supported container/codec for this browser (webm/opus, mp4/aac, ogg/opus). */
    fun pickMime(): JsString? = js(
        "(()=>{ if (typeof MediaRecorder === 'undefined') return null; const candidates = ['audio/webm;codecs=opus','audio/webm','audio/mp4','audio/ogg;codecs=opus']; for (const m of candidates) { if (MediaRecorder.isTypeSupported(m)) return m; } return null; })()"
    )

    /**
     * Requests the microphone, starts a MediaRecorder and returns a controller.
     * Calling controller.stop() finalizes the clip, releases the mic and
     * resolves to the recording as a data: URL.
     */
    fun start(mime: String): Promise<JsAny?> = js(
        """(async () => {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            const recorder = new MediaRecorder(stream, { mimeType: mime });
            const chunks = [];
            recorder.ondataavailable = (e) => { if (e.data && e.data.size > 0) chunks.push(e.data); };
            const finished = new Promise((resolve) => {
                recorder.onstop = async () => {
                    const blob = new Blob(chunks, { type: mime.split(';')[0] });
                    const url = await new Promise((res, rej) => {
                        const fr = new FileReader();
                        fr.onload = () => res(fr.result);
                        fr.onerror = rej;
                        fr.readAsDataURL(blob);
                    });
                    stream.getTracks().forEach((t) => t.stop());
                    resolve(url);
                };
            });
            recorder.start();
            return { stop: async () => { if (recorder.state !== 'inactive') recorder.stop(); return await finished; } };
        })()"""
    )

    fun stop(controller: JsAny): Promise<JsString?> = js("controller.stop()")

    /** Plays a data: URL clip; returns the HTMLAudioElement so it can be paused later. */
    fun play(dataUrl: String): JsAny? = js("(()=>{ try { const el = new Audio(dataUrl); el.play(); return el; } catch (e) { return null; } })()")

    fun pause(player: JsAny): Unit = js("(()=>{ try { player.pause(); } catch (e) {} })()")
}
