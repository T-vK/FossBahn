package de.openbahn.navigator.ui.tickets

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** Raises screen brightness while the Tickets tab is visible (e.g. for ticket inspection). */
@Composable
fun TicketTabBrightness() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        if (window == null) {
            return@DisposableEffect onDispose { }
        }
        val attrs = window.attributes
        val previousBrightness = attrs.screenBrightness
        attrs.screenBrightness = 1f
        window.attributes = attrs
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            attrs.screenBrightness = previousBrightness
            window.attributes = attrs
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
