package fr.channelfinder.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

@Composable
fun EpisodeCard(item: LibraryItem, watch: VodWatch?, modifier: Modifier = Modifier, onPlay: () -> Unit, onRestart: () -> Unit) {
    CardSurface(onPlay, modifier.fillMaxWidth(), onLongClick = onRestart,
        shape = RoundedCornerShape(Cinema.CardRadius), container = Cinema.Panel) {
        Column(Modifier.padding(Spacing.Row).heightIn(min = 110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("S${item.season} · ÉPISODE ${item.episode}", style = Type.Eyebrow, color = Cinema.Accent)
            Text(item.title.replace(Regex("^.*?\\bS\\d+E\\d+\\s*[-:]\\s*", RegexOption.IGNORE_CASE), ""),
                style = Type.Label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (watch != null) VodProgress(watch) else Text("Regarder", style = Type.Caption, color = Palette.Muted)
        }
    }
}
