package fr.channelfinder.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

@Composable
internal fun ProgrammeBackdrop(item: LibraryItem?, modifier: Modifier = Modifier, brandFallback: Boolean = false) {
    Box(modifier.background(Palette.Ink)) {
        val artwork = item?.backdrop?.ifBlank { item.artwork }.orEmpty()
        if (artwork.isNotBlank()) {
            val context = LocalContext.current
            val request = remember(context, artwork) { ImageRequest.Builder(context).data(artwork).size(1280, 720).build() }
            AsyncImage(request,
                null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else if (brandFallback) {
            Image(painterResource(R.drawable.salon_atmosphere), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            0f to Palette.Ink, 0.35f to Palette.Ink.copy(alpha = 0.9f), 1f to Palette.Ink.copy(alpha = 0.25f))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Palette.Ink.copy(alpha = 0.1f), 0.6f to Palette.Ink.copy(alpha = 0.65f), 1f to Palette.Ink)))
    }
}

@Composable
internal fun ProgrammeFeature(item: LibraryItem?, label: String, modifier: Modifier = Modifier) {
    val large = LocalDensity.current.fontScale > 1.15f
    Column(modifier.fillMaxWidth(0.78f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(item?.presentation?.title ?: label, style = Type.Headline, color = Palette.OnSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        val metadata = item?.let { listOfNotNull(it.presentation.year?.toString(), it.presentation.quality,
            it.rating.takeIf(String::isNotBlank)?.let { rating -> "Note $rating" }).joinToString(" · ") }.orEmpty()
        if (metadata.isNotBlank()) Text(metadata, style = Type.Caption, color = Palette.Muted, maxLines = 1)
        if (!item?.overview.isNullOrBlank()) Text(item.overview, style = Type.Caption, color = Palette.Muted,
            maxLines = if (large) 1 else 2, overflow = TextOverflow.Ellipsis)
    }
}
