package fr.channelfinder.tv

import android.view.KeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent

internal fun Modifier.guardConfirmPress(guard: ConfirmPressGuard): Modifier = onPreviewKeyEvent { event ->
    val native = event.nativeKeyEvent
    when (native.keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
            guard.onEvent(native.keyCode, native.downTime, native.action == KeyEvent.ACTION_DOWN)
        else -> false
    }
}

internal class ConfirmPressGuard {
    private var active: Pair<Int, Long>? = null
    private var consumed: Pair<Int, Long>? = null

    fun onEvent(keyCode: Int, downTime: Long, isDown: Boolean): Boolean {
        val press = keyCode to downTime
        if (isDown && active != press) {
            active = press
            consumed = null
        }
        val swallow = consumed == press
        if (!isDown) {
            active = null
            consumed = null
        }
        return swallow
    }

    fun consumeCurrentPress() { consumed = active }
}
