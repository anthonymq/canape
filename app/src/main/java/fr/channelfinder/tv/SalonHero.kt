package fr.channelfinder.tv

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

@Composable
fun SalonHero(state: AppState, watch: VodWatch?, channel: Channel?, modifier: Modifier = Modifier, programme: TvProgramme? = null) {
    val item = watch?.displayItem
    Column(Modifier.fillMaxWidth().heightIn(min = Cinema.FeatureHeight).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(item?.title ?: programme?.title ?: channel?.displayName ?: "Installez-vous.", style = Type.Display,
            maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.OnSurface)
        Text(when {
            watch?.series != null -> "Saison ${watch.item.season} · Épisode ${watch.item.episode} · ${watchTime(watch.positionMs)}"
            watch != null -> "Reprendre à ${watchTime(watch.positionMs)}"
            channel != null -> listOfNotNull(channel.displayName, programme?.timeLabel()).joinToString(" · ")
            else -> "Retrouvez vos chaînes, films et séries."
        }, style = Type.Caption, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        TvButton(if (watch != null) "Continuer" else if (channel != null) "Reprendre le direct" else "Explorer les chaînes", {
            when {
                watch != null -> state.navigate(Page.VodPlayer(watch.item, watch.kind, watch.series))
                channel != null -> state.play(channel, listOf(channel))
                else -> state.openDestination(NavDest.DIRECT)
            }
        }, modifier, icon = Icons.Rounded.PlayArrow, primary = true, compact = true)
    }
}
