package fr.channelfinder.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text

private val liveDestinations = listOf(
    NavDest.FRANCE to "France", NavDest.PORTUGUESE to "Português", NavDest.SPORTS to "Sports",
    NavDest.EVENTS to "Événements", NavDest.FAVORITES to "Favoris", NavDest.GUIDE to "Guide TV",
    NavDest.CATEGORIES to "Catégories", NavDest.SEARCH to "Toutes les chaînes"
)

@Composable
internal fun LiveNavigationPanel(state: AppState, onDismiss: () -> Unit, onSelect: (NavDest) -> Unit) {
    val selected = liveDestinations.indexOfFirst { it.first == state.activeDest }.coerceAtLeast(0)
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 5).coerceAtLeast(0))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(horizontal = Spacing.Side, vertical = Spacing.Top), contentAlignment = Alignment.CenterStart) {
            Column(Modifier.width(280.dp).fillMaxHeight().background(Palette.Surface1, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("En direct", style = Type.Headline, color = Palette.OnSurface)
                LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(4.dp)) {
                    itemsIndexed(liveDestinations) { index, (destination, label) ->
                        TvChip(label, destination == state.activeDest, { onSelect(destination) },
                            Modifier.fillMaxWidth().then(if (index == selected) Modifier.focusRequester(focus) else Modifier))
                    }
                }
            }
        }
        LaunchedEffect(Unit) { focusWhenReady(focus) }
    }
}

@Composable
internal fun LiveQualityDialog(selected: ChannelText.VideoQuality?, onDismiss: () -> Unit, onSelect: (ChannelText.VideoQuality?) -> Unit) {
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(360.dp).background(Palette.Surface1, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Qualité", style = Type.Headline, color = Palette.OnSurface)
            (listOf<ChannelText.VideoQuality?>(null) + ChannelText.VideoQuality.entries).forEach { quality ->
                TvChip(quality?.label ?: "Toutes les qualités", selected == quality, { onSelect(quality); onDismiss() },
                    Modifier.fillMaxWidth().then(if (selected == quality) Modifier.focusRequester(focus) else Modifier))
            }
        }
        LaunchedEffect(Unit) { focusWhenReady(focus) }
    }
}
