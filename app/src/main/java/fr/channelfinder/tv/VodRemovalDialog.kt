package fr.channelfinder.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text

@Composable
fun VodRemovalDialog(state: AppState, entry: VodWatch, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(500.dp)
            .guardConfirmPress(state.confirmPressGuard)
            .background(Cinema.Background, RoundedCornerShape(Cinema.CardRadius))
            .padding(Spacing.Top), verticalArrangement = Arrangement.spacedBy(Spacing.Row)) {
            Text("Retirer de Continuer à regarder ?", style = Type.Title, color = Palette.OnSurface)
            Text(entry.displayItem.title, style = Type.Body, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
                TvButton("Annuler", onDismiss, Modifier.focusRequester(cancelFocus))
                TvButton("Retirer", onConfirm, icon = Icons.Rounded.Delete, iconTint = Cinema.Accent)
            }
        }
        LaunchedEffect(Unit) { focusWhenReady(cancelFocus) }
    }
}
