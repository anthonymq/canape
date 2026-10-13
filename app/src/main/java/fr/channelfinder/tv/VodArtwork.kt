package fr.channelfinder.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

@Composable
fun VodHero(item: LibraryItem?, label: String, modifier: Modifier = Modifier, titleLines: Int = 2, actions: @Composable () -> Unit = {}) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(Cinema.CardRadius)).background(Cinema.Panel)) {
        item?.let {
            AsyncImage(it.backdrop.ifBlank { it.artwork }, null,
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.58f), contentScale = ContentScale.Crop)
        }
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            0f to Cinema.Background, 0.42f to Cinema.Background, 0.78f to Cinema.Background.copy(alpha = 0.5f),
            1f to Cinema.Background.copy(alpha = 0.2f))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Cinema.Background.copy(alpha = 0.15f), 1f to Cinema.Background)))
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.64f).padding(Spacing.Row),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label.uppercase(), style = Type.Eyebrow, color = Cinema.Accent)
            Text(item?.title ?: "Votre prochaine histoire", style = Type.Display, maxLines = titleLines,
                overflow = TextOverflow.Ellipsis, color = Palette.OnSurface)
            val metadata = item?.let { listOfNotNull(it.year.takeIf(String::isNotBlank),
                it.rating.takeIf(String::isNotBlank)?.let { rating -> "Note $rating" },
                if (it.episode > 0) "S${it.season} · E${it.episode}" else null).joinToString("  ·  ") }.orEmpty()
            if (metadata.isNotBlank()) Text(metadata, style = Type.Caption, color = Palette.Muted)
            actions()
        }
    }
}

@Composable
fun VodProgress(watch: VodWatch, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Palette.Faint.copy(alpha = 0.3f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(if (watch.completed) 1f else watch.fraction).background(Cinema.Accent))
        }
        Text(if (watch.completed) "✓ Vu" else "${watchTime(watch.positionMs)}${if (watch.durationMs > 0) " / ${watchTime(watch.durationMs)}" else ""}",
            style = Type.Caption, color = Palette.Muted, maxLines = 1)
    }
}

@Composable
fun VodPoster(item: LibraryItem, watch: VodWatch?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.15f
    val presentation = item.presentation
    var artworkLoaded by remember(item.artwork) { mutableStateOf(false) }
    CardSurface(onClick, modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Cinema.CardRadius), container = Cinema.Panel, focusedScale = 1f) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(if (largeText) Cinema.LargePosterRatio else Cinema.PosterRatio), contentAlignment = Alignment.Center) {
                if (!artworkLoaded) Text(presentation.title, style = Type.Label, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(Spacing.Gutter), color = Palette.Muted)
                if (item.artwork.isNotBlank()) AsyncImage(item.artwork, null, Modifier.fillMaxSize(),
                    onSuccess = { artworkLoaded = true }, onError = { artworkLoaded = false },
                    contentScale = if (largeText) ContentScale.Fit else ContentScale.Crop)
                if (watch?.completed == true) Text("✓ Vu", style = Type.Badge, color = Palette.OnSurface,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Cinema.Background.copy(alpha = 0.9f), RoundedCornerShape(4.dp)).padding(6.dp))
            }
            Text(presentation.title, style = Type.Caption, color = Palette.OnSurface, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 36.dp))
            val metadata = listOfNotNull(presentation.year?.toString(), presentation.quality).joinToString(" · ")
            if (metadata.isNotBlank()) Text(metadata, style = Type.Caption, color = Palette.Muted, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
            if (watch != null && !watch.completed && watch.positionMs >= 5_000) VodProgress(watch, Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(4.dp))
        }
    }
}
