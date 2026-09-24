package tassic.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tassic.platform.vibrate

/**
 * Confirmation with a way back.
 *
 * Every delete in the app went straight through a confirm dialog — which stops
 * accidents but also makes routine tidying feel dangerous, so people hoard rows
 * they meant to bin. An undo affordance is the better trade for anything
 * recoverable: no modal in the way, and the mistake costs one tap to reverse.
 *
 * Confirm dialogs stay for the genuinely irreversible things (erase everything,
 * restore over the top of live data).
 */
class Feedback internal constructor(
    private val host: SnackbarHostState,
    private val scope: CoroutineScope
) {

    /** A plain acknowledgement — no action, short duration. */
    fun say(message: String) {
        scope.launch { host.showSnackbar(message, duration = SnackbarDuration.Short) }
    }

    /**
     * Acknowledges something destructive and offers to reverse it.
     *
     * [onUndo] runs on the main scope, so it can touch the store directly.
     */
    fun undoable(message: String, actionLabel: String = "Undo", onUndo: () -> Unit) {
        scope.launch {
            val result = host.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = false,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    /** Confirmation plus a light haptic — used where a tap changes real state. */
    fun confirm(message: String) {
        vibrate(12)
        say(message)
    }
}

@Composable
fun rememberFeedback(): Feedback {
    val host = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    return remember(host, scope) { Feedback(host, scope) }
}
