package fr.channelfinder.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

@Composable
fun VodContinueRow(state: AppState, session: LibrarySession, entries: List<VodWatch>, focus: FocusRequester?) {
    val account = state.account ?: return
    val targetKey = entries.firstOrNull { it.key == session.resumeFocusId }?.key ?: entries.first().key
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
        Text("Continuer à regarder", style = Type.Title, color = Palette.OnSurface)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Cinema.GridGap), contentPadding = PaddingValues(8.dp)) {
            items(entries, key = { it.key }) { entry ->
                Column(Modifier.width(Cinema.ResumeWidth), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardSurface({ state.navigate(Page.VodPlayer(entry.item, entry.kind, entry.series)) },
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            .then(if (focus != null && entry.key == targetKey) Modifier.focusRequester(focus) else Modifier)
                            .onFocusChanged {
                            if (it.hasFocus) { session.highlighted = entry.displayItem; session.resumeFocusId = entry.key; session.focusedId = null }
                        }, onLongClick = { state.watchHistory.remove(account, entry) },
                        shape = RoundedCornerShape(Cinema.CardRadius), container = Cinema.Panel) {
                        Row(Modifier.fillMaxSize()) {
                            AsyncImage(entry.displayItem.artwork, null, Modifier.fillMaxHeight().width(72.dp), contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f).padding(Spacing.Gutter), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("REPRENDRE", style = Type.Badge, color = Cinema.Accent)
                                Text(entry.displayItem.title, style = Type.Label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (entry.series != null) Text("S${entry.item.season} · E${entry.item.episode}", style = Type.Caption)
                            }
                        }
                    }
                    VodProgress(entry)
                }
            }
        }
        Text("OK pour reprendre · OK long pour retirer", style = Type.Caption, color = Palette.Faint)
    }
}
