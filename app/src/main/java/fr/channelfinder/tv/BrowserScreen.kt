package fr.channelfinder.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

private enum class PreviewState { WAITING, BUFFERING, READY, ERROR }

private val browseLanes = listOf(
    BrowseLane.FRANCE, BrowseLane.PORTUGUESE, BrowseLane.SPORTS, BrowseLane.EVENTS, BrowseLane.FAVORITES, BrowseLane.RECENT, BrowseLane.ALL
)

@Composable
fun BrowserScreen(state: AppState, page: Page.Browse) {
    val number = remember { NumberFormat.getIntegerInstance(Locale.FRANCE) }
    val category = page.categoryId?.let(state.categoriesById::get)
    // Favorites/recents are captured when the list opens so un-starring a row does not yank focus away.
    val base = remember(state.channels, page.lane, page.categoryId) { state.channelsFor(page.lane, page.categoryId) }
    val results = remember(base, page.query) { state.search(base, page.query) }
    val selected = results.firstOrNull { it.id == state.selectedChannelId } ?: results.firstOrNull()
    val listState = state.browserListState

    val searchFocus = remember { FocusRequester() }
    val selectedRowFocus = remember { FocusRequester() }
    val watchFocus = remember { FocusRequester() }
    val chipFocus = remember { browseLanes.associateWith { FocusRequester() } }
    var editing by remember { mutableStateOf(page.startSearch) }
    var listHasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (page.startSearch) {
            state.replaceTop(page.copy(startSearch = false))
            return@LaunchedEffect
        }
        val index = results.indexOfFirst { it.id == selected?.id }
        delay(16)
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.scrollToItem((index - 2).coerceAtLeast(0))
        }
        delay(60)
        val focused = index >= 0 && runCatching { selectedRowFocus.requestFocus(); true }.getOrDefault(false)
        if (!focused) runCatching { searchFocus.requestFocus() }
    }

    // A new query or lane starts again from the best match.
    var lastFilter by remember { mutableStateOf(Triple(page.lane, page.categoryId, page.query)) }
    LaunchedEffect(page.lane, page.categoryId, page.query) {
        val filter = Triple(page.lane, page.categoryId, page.query)
        if (filter != lastFilter) {
            lastFilter = filter
            state.selectedChannelId = null
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(selected?.id) { if (selected != null && state.selectedChannelId == null) state.selectedChannelId = selected.id }

    val title = when {
        category != null -> category.displayName
        page.lane == BrowseLane.ALL && page.query.isNotBlank() -> "Recherche"
        page.lane == BrowseLane.ALL -> "Toutes les chaînes"
        else -> page.lane.label
    }
    val eyebrow = when {
        category != null -> listOfNotNull("Catégorie", category.countryTag).joinToString(" · ")
        page.query.isNotBlank() -> "${number.format(results.size)} résultats pour « ${page.query.trim()} »"
        else -> "${number.format(results.size)} chaînes"
    }

    Column(Modifier.fillMaxSize().padding(start = Spacing.Side, end = Spacing.Side, top = Spacing.Top, bottom = 20.dp)) {
        ScreenHeader(eyebrow, title) {
            TvTextField(
                value = page.query,
                onValueChange = { state.replaceTop(page.copy(query = it)) },
                placeholder = "Rechercher une chaîne",
                editing = editing,
                onEditingChange = { editing = it },
                icon = Icons.Rounded.Search,
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                onImeAction = { if (results.isNotEmpty()) runCatching { selectedRowFocus.requestFocus() } },
                outerFocusRequester = searchFocus,
                modifier = Modifier.width(380.dp)
            )
        }
        if (page.categoryId == null) {
            Row(
                Modifier.padding(top = 12.dp)
                    .focusProperties { onEnter = { runCatching { chipFocus.getValue(page.lane).requestFocus() } } }
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                browseLanes.forEach { lane ->
                    TvChip(
                        text = lane.label,
                        selected = lane == page.lane,
                        onClick = { if (lane != page.lane) state.replaceTop(page.copy(lane = lane)) },
                        modifier = Modifier.focusRequester(chipFocus.getValue(lane))
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        if (results.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                BrowseEmptyState(page, state)
            }
        } else {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1.12f).fillMaxHeight()
                        .onFocusChanged { listHasFocus = it.hasFocus }
                        .focusRestorer(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp)
                ) {
                    itemsIndexed(results, key = { _, channel -> channel.id }) { _, channel ->
                        val isSelected = channel.id == selected?.id
                        ChannelRow(
                            channel = channel,
                            isFavorite = channel.id in state.favorites,
                            isUnavailable = state.isUnavailable(channel.id),
                            isPreviewing = isSelected && !listHasFocus,
                            // Right always lands on "Regarder", never on the search field above it.
                            modifier = (if (isSelected) Modifier.focusRequester(selectedRowFocus) else Modifier)
                                .focusProperties { right = watchFocus },
                            onFocus = { state.selectedChannelId = channel.id },
                            onPlay = { state.play(channel, results) },
                            onToggleFavorite = { state.toggleFavorite(channel) }
                        )
                    }
                }
                if (selected != null) {
                    PreviewPane(
                        state = state,
                        channel = selected,
                        isFavorite = selected.id in state.favorites,
                        onWatch = { state.play(selected, results) },
                        onToggleFavorite = { state.toggleFavorite(selected) },
                        watchFocus = watchFocus,
                        modifier = Modifier.weight(0.88f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseEmptyState(page: Page.Browse, state: AppState) {
    when {
        page.query.isNotBlank() -> EmptyState(
            Icons.Rounded.SearchOff,
            "Aucun résultat pour « ${page.query.trim()} »",
            if (page.lane == BrowseLane.ALL && page.categoryId == null) "Vérifiez l’orthographe ou essayez un nom plus court, par exemple « TF1 »."
            else "Rien dans cette sélection. Essayez de chercher dans toutes les chaînes."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (page.lane != BrowseLane.ALL || page.categoryId != null) {
                    TvButton("Chercher partout", { state.replaceTop(page.copy(lane = BrowseLane.ALL, categoryId = null)) },
                        icon = Icons.Rounded.Search, primary = true)
                }
                TvButton("Effacer la recherche", { state.replaceTop(page.copy(query = "")) }, icon = Icons.Rounded.Clear)
            }
        }
        page.lane == BrowseLane.FAVORITES -> EmptyState(
            Icons.Rounded.StarOutline, "Aucun favori pour l’instant",
            "Sur n’importe quelle chaîne, faites un appui long sur OK pour l’ajouter à vos favoris."
        )
        page.lane == BrowseLane.RECENT -> EmptyState(
            Icons.Rounded.History, "Aucune chaîne récente", "Les chaînes que vous regardez apparaîtront ici."
        )
        page.lane == BrowseLane.EVENTS -> EmptyState(
            Icons.Rounded.SearchOff, "Aucun événement", "Les matchs et directs ponctuels apparaîtront ici quand le fournisseur les annonce."
        )
        else -> EmptyState(Icons.Rounded.SearchOff, "Aucune chaîne dans cette sélection", "Choisissez une autre catégorie.")
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    isFavorite: Boolean,
    isUnavailable: Boolean,
    isPreviewing: Boolean,
    modifier: Modifier,
    onFocus: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    FocusSurface(
        onClick = onPlay,
        onLongClick = onToggleFavorite,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().height(68.dp).onFocusChanged { if (it.isFocused) onFocus() },
        container = if (isPreviewing) Palette.AccentDeep else Palette.Surface1,
        outline = if (isPreviewing) Palette.Accent else null,
        focusedScale = 1.03f
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Recently failed channels stay listed but recede, so working ones stand out.
            ChannelLogo(channel, Modifier.size(width = 72.dp, height = 46.dp).alpha(if (isUnavailable && !focused) 0.45f else 1f), corner = 8.dp)
            Column(Modifier.weight(1f).alpha(if (isUnavailable && !focused) 0.6f else 1f)) {
                Text(channel.displayName, style = Type.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isUnavailable) UnavailableTag()
                    EventTag(channel)
                    if (channel.event == null) channel.countryTag?.let { Badge(it) }
                    if (channel.event == null) channel.badges.take(2).forEach { Badge(it) }
                    Text(channel.categoryDisplay, style = Type.Caption, color = secondaryColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (isFavorite) Icon(Icons.Rounded.Star, contentDescription = "Favori", tint = if (focused) Palette.GoldOnLight else Palette.Gold, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PreviewPane(
    state: AppState,
    channel: Channel,
    isFavorite: Boolean,
    onWatch: () -> Unit,
    onToggleFavorite: () -> Unit,
    watchFocus: FocusRequester,
    modifier: Modifier
) {
    val context = LocalContext.current
    val account = state.account ?: return
    val player = remember { buildStreamPlayer(context) }
    var previewState by remember { mutableStateOf(PreviewState.WAITING) }
    var errorText by remember { mutableStateOf("") }
    // Many provider failures (HTTP 407, timeouts) are intermittent: retry twice before calling it unavailable.
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(channel.id) { retry = 0 }
    val currentChannel by rememberUpdatedState(channel)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> { previewState = PreviewState.READY; state.markWorking(currentChannel.id) }
                    Player.STATE_BUFFERING -> if (previewState != PreviewState.READY) previewState = PreviewState.BUFFERING
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (retry < 2) {
                    retry++
                    return
                }
                errorText = playbackErrorMessage(error)
                previewState = PreviewState.ERROR
                state.markUnavailable(currentChannel.id)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Start the stream only once focus settles, so fast D-pad movement does not open a stream per row.
    LaunchedEffect(channel.id, account, retry) {
        if (retry == 0) previewState = PreviewState.WAITING
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        delay(if (retry == 0) 650L else 1_500L * retry)
        player.setMediaItem(MediaItem.fromUri(state.repository.streamUrl(account, channel)))
        player.volume = 0f
        if (retry == 0) previewState = PreviewState.BUFFERING
        player.playWhenReady = true
        player.prepare()
    }

    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Color.Black)
        ) {
            AndroidView(
                factory = { videoSurface(it).apply { this.player = player } },
                update = { view -> if (view.player !== player) view.player = player },
                modifier = Modifier.fillMaxSize()
            )
            val overlayAlpha by animateFloatAsState(
                if (previewState == PreviewState.READY) 0f else 1f,
                tween(if (previewState == PreviewState.READY) 400 else 150), label = "overlay"
            )
            if (overlayAlpha > 0f) {
                Box(Modifier.fillMaxSize().alpha(overlayAlpha).background(Brush.verticalGradient(listOf(Palette.Surface2, Palette.Ink))), contentAlignment = Alignment.Center) {
                    if (previewState == PreviewState.ERROR) {
                        Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Error, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Aperçu indisponible", style = Type.Label, color = Palette.OnSurface)
                            Text(errorText, style = Type.Caption, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ChannelLogo(channel, Modifier.size(width = 120.dp, height = 72.dp), initialsSize = 24)
                            Spacer(Modifier.height(14.dp))
                            Spinner(Modifier.size(26.dp), color = Palette.Muted, stroke = 3.dp)
                        }
                    }
                }
            }
            if (previewState == PreviewState.READY) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(8.dp).background(Palette.Live, CircleShape))
                    Text("EN DIRECT", style = Type.Badge, color = Color.White)
                    Icon(Icons.Rounded.VolumeOff, contentDescription = "Son coupé", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(channel.displayName, style = Type.Title, color = Palette.OnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.isUnavailable(channel.id)) UnavailableTag()
            EventTag(channel)
            channel.countryTag?.let { Badge(it, tone = Palette.Muted) }
            channel.badges.forEach { Badge(it, tone = Palette.Muted) }
            if (channel.epgId.isNotBlank()) Badge("GUIDE", tone = Palette.Muted)
            Text(channel.categoryDisplay, style = Type.Caption, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton("Regarder", onWatch, Modifier.focusRequester(watchFocus), icon = Icons.Rounded.PlayArrow, primary = true)
            TvButton(
                if (isFavorite) "Favori" else "Ajouter",
                onToggleFavorite,
                icon = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                iconTint = if (isFavorite) Palette.Gold else null
            )
        }
        Spacer(Modifier.height(14.dp))
        HintBar("OK long" to if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris")
    }
}
