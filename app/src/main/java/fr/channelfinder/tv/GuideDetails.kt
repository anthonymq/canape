package fr.channelfinder.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

@Composable
internal fun GuideDetails(
    channel: Channel, programme: TvProgramme?, now: Long, onWatch: () -> Unit,
    watchFocus: FocusRequester, channelFocus: FocusRequester, programmeFocus: FocusRequester, refreshFocus: FocusRequester,
    hasProgrammes: Boolean, onActionFocus: (String) -> Unit
) {
    val detailsFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(programme?.key) { scroll.scrollTo(0) }
    Column(Modifier.width(260.dp).fillMaxHeight()) {
        TvButton("Regarder le direct", onWatch, Modifier.fillMaxWidth().focusRequester(watchFocus)
            .onFocusChanged { if (it.isFocused) onActionFocus("Direct") }
            .focusProperties { left = if (hasProgrammes) programmeFocus else channelFocus; down = detailsFocus; up = refreshFocus },
            icon = Icons.Rounded.PlayArrow, primary = true)
        Spacer(Modifier.height(10.dp))
        Text("Sur ${channel.displayName} · pas de replay", style = Type.Caption, color = Palette.Muted)
        Spacer(Modifier.height(14.dp))
        Column(Modifier.weight(1f).fillMaxWidth()
            .background(if (focused) Palette.Surface2 else Palette.Surface1, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .then(if (focused) Modifier.border(3.dp, Palette.FocusFill, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) else Modifier)
            .focusRequester(detailsFocus).focusProperties { up = watchFocus; left = if (hasProgrammes) programmeFocus else channelFocus }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onActionFocus("Lire") }
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) false else when (it.key) {
                    Key.DirectionDown -> { scope.launch { scroll.animateScrollTo((scroll.value + 140).coerceAtMost(scroll.maxValue)) }; true }
                    Key.DirectionUp -> {
                        if (scroll.value == 0) watchFocus.requestFocus()
                        else scope.launch { scroll.animateScrollTo((scroll.value - 140).coerceAtLeast(0)) }
                        true
                    }
                    else -> false
                }
            }.focusable().verticalScroll(scroll).padding(16.dp)) {
            Text(if (focused) "↑ ↓ Lire la description" else "Détails du programme", style = Type.Badge, color = Palette.Accent)
            Spacer(Modifier.height(12.dp))
            if (programme == null) {
                Text("Choisissez un programme pour lire ses détails.", style = Type.Body, color = Palette.Muted)
            } else {
                Text(when { programme.isLive(now) -> "EN COURS"; programme.start > now -> "À VENIR"; else -> "TERMINÉ" },
                    style = Type.Eyebrow, color = Palette.Accent)
                Spacer(Modifier.height(8.dp))
                Text(programme.title, style = Type.Title, color = Palette.OnSurface)
                Spacer(Modifier.height(8.dp))
                Text("${programme.dayLabel()}\n${programme.timeLabel()}", style = Type.Caption, color = Palette.Muted)
                Spacer(Modifier.height(14.dp))
                Text(programme.description.ifBlank { "Le fournisseur n’a pas transmis de description." }, style = Type.Body, color = Palette.OnSurface)
            }
        }
    }
}
