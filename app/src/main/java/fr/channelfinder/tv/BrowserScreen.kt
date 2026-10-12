package fr.channelfinder.tv

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private enum class PreviewState { WAITING, BUFFERING, READY, ERROR }

private val qualityExemptLanes = setOf(BrowseLane.EVENTS, BrowseLane.FAVORITES, BrowseLane.RECENT)

@Composable
fun BrowserScreen(state: AppState, page: Page.Browse) {
    val number = remember { NumberFormat.getIntegerInstance(Locale.FRANCE) }
    val category = page.categoryId?.let(state.categoriesById::get)
    // Favorites/recents are captured when the list opens so un-starring a row does not yank focus away.
    // Récents is the exception: removing one from the preview must update the list.
    val recentKey = if (page.lane == BrowseLane.RECENT && page.categoryId == null) state.recent.toList() else null
    val base = remember(state.channels, page.lane, page.categoryId, recentKey) { state.channelsFor(page.lane, page.categoryId) }
    // Events rarely carry a quality tag, and favorites/recents are the viewer's own picks: no quality filter there.
    val qualityApplies = page.categoryId != null || page.lane !in qualityExemptLanes
    val quality = if (qualityApplies) state.qualityFilter else null
    // Country filter: offered when the list mixes at least two countries with two channels or more each.
    // A country gets a chip only with a real share of the list (5%, at least 2 channels), so a few
    // stray channels do not add chips that overflow the row.
    val countries = if (!qualityApplies) emptyList() else remember(base) {
        val minimum = maxOf(2, base.size / 20)
        base.groupingBy { it.country }.eachCount()
            .filter { (code, count) -> code != null && count >= minimum }
            .entries.sortedByDescending { it.value }.take(6).map { it.key!! }
            .takeIf { it.size >= 2 }.orEmpty()
    }
    val country = page.country?.takeIf { it in countries }
    val filterRowShown = qualityApplies || countries.isNotEmpty()
    val results = remember(base, page.query, quality, country) {
        state.search(if (country == null) base else base.filter { it.country == country }, page.query, quality)
    }
    val selected = results.firstOrNull { it.id == state.selectedChannelId } ?: results.firstOrNull()
    val listState = state.browserListState

    val searchFocus = remember { FocusRequester() }
    val selectedRowFocus = remember { FocusRequester() }
    val watchFocus = remember { FocusRequester() }
    val countryFocus = remember { mutableMapOf<String?, FocusRequester>() }
    val qualityFocus = remember { (listOf<ChannelText.VideoQuality?>(null) + ChannelText.VideoQuality.entries).associateWith { FocusRequester() } }
    var editing by remember { mutableStateOf(page.startSearch) }
    var listHasFocus by remember { mutableStateOf(false) }

    // Back in a scrolled list returns to the top. On a screen opened from the drawer it also opens the
    // drawer (left navigation guideline); in a sub-page (a category from the picker) the next Back leaves.
    // Registered before the search field's handler, so Back while typing still ends editing first.
    val scope = rememberCoroutineScope()
    var screenHasFocus by remember { mutableStateOf(false) }
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
    BackHandler(enabled = screenHasFocus && scrolled && results.isNotEmpty()) {
        scope.launch {
            state.selectedChannelId = results.first().id
            listState.scrollToItem(0)
            delay(32)
            if (state.isDrawerRoot) state.focusDrawer() else runCatching { selectedRowFocus.requestFocus() }
        }
    }

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
    var lastFilter by remember { mutableStateOf(listOf(page.lane, page.categoryId, page.query, quality, country)) }
    LaunchedEffect(page.lane, page.categoryId, page.query, quality, country) {
        val filter = listOf(page.lane, page.categoryId, page.query, quality, country)
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
    val noun = if (page.lane == BrowseLane.EVENTS && page.categoryId == null) "événement" else "chaîne"
    val countLabel = listOfNotNull(
        "${number.format(results.size)} $noun${plural(results.size)}",
        quality?.let { "en ${it.label}" },
        country?.let(ChannelText::countryName)
    ).joinToString(" · ").replace(" · en ", " en ")
    val eyebrow = when {
        category != null && page.query.isBlank() -> listOfNotNull("Catégorie", category.countryTag, countLabel).joinToString(" · ")
        page.query.isNotBlank() -> "${number.format(results.size)} résultat${plural(results.size)} pour « ${page.query.trim()} »"
        else -> countLabel
    }

    Column(
        Modifier.fillMaxSize()
            .onFocusChanged { screenHasFocus = it.hasFocus }
            .padding(start = Spacing.Side, end = Spacing.Side, top = Spacing.Top, bottom = 20.dp)
    ) {
        ScreenHeader(eyebrow, title) {
            TvTextField(
                value = page.query,
                onValueChange = { state.replaceTop(page.copy(query = it)) },
                // The drawer's Rechercher covers every channel; this field searches the list on screen.
                placeholder = when {
                    category != null -> "Rechercher dans cette catégorie"
                    page.lane == BrowseLane.ALL -> "Rechercher une chaîne"
                    else -> "Rechercher dans ${page.lane.label}"
                },
                editing = editing,
                onEditingChange = { editing = it },
                icon = Icons.Rounded.Search,
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                onImeAction = { if (results.isNotEmpty()) runCatching { selectedRowFocus.requestFocus() } },
                outerFocusRequester = searchFocus,
                modifier = Modifier.width(360.dp)
            )
        }
        // Filters share one row (scrolls sideways if long) so the list keeps its height.
        if (filterRowShown) Row(
            Modifier.padding(top = 8.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        if (qualityApplies) Row(
            Modifier
                .focusProperties { onEnter = { runCatching { qualityFocus.getValue(state.qualityFilter).requestFocus() } } }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("QUALITÉ", style = Type.Eyebrow, color = Palette.Faint, modifier = Modifier.padding(end = 4.dp))
            qualityFocus.forEach { (quality, requester) ->
                TvChip(
                    text = quality?.label ?: "Toutes",
                    selected = state.qualityFilter == quality,
                    onClick = { state.setQuality(quality) },
                    modifier = Modifier.focusRequester(requester).downToList(selectedRowFocus, results.isNotEmpty()),
                    compact = true
                )
            }
        }
        if (countries.isNotEmpty()) Row(
            Modifier
                .focusProperties { onEnter = { runCatching { countryFocus.getOrPut(country) { FocusRequester() }.requestFocus() } } }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PAYS", style = Type.Eyebrow, color = Palette.Faint, modifier = Modifier.padding(end = 4.dp))
            (listOf<String?>(null) + countries).forEach { code ->
                TvChip(
                    text = code?.let(ChannelText::countryLabel) ?: "Tous",
                    selected = country == code,
                    onClick = { state.replaceTop(page.copy(country = code)) },
                    modifier = Modifier.focusRequester(countryFocus.getOrPut(code) { FocusRequester() })
                        .downToList(selectedRowFocus, results.isNotEmpty()),
                    compact = true
                )
            }
        }
        }
        Spacer(Modifier.height(8.dp))

        if (results.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                BrowseEmptyState(page, state, quality)
            }
        } else {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1.12f).fillMaxHeight()
                        .onFocusChanged { listHasFocus = it.hasFocus }
                        .focusRestorer(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp)
                ) {
                    itemsIndexed(results, key = { _, channel -> channel.id }) { index, channel ->
                        val isSelected = channel.id == selected?.id
                        ChannelRow(
                            channel = channel,
                            // The group name only where it changes; the preview always shows it.
                            showCategory = page.categoryId == null &&
                                (index == 0 || results[index - 1].categoryDisplay != channel.categoryDisplay),
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
                        onRemoveRecent = if (page.lane == BrowseLane.RECENT && page.categoryId == null) {
                            {
                                // Keep the viewer's place: the next row (or the previous one at the end) takes over.
                                val index = results.indexOfFirst { it.id == selected.id }
                                state.selectedChannelId = (results.getOrNull(index + 1) ?: results.getOrNull(index - 1))?.id
                                state.removeRecent(selected)
                            }
                        } else null,
                        watchFocus = watchFocus,
                        modifier = Modifier.weight(0.88f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseEmptyState(page: Page.Browse, state: AppState, quality: ChannelText.VideoQuality?) {
    when {
        quality != null && state.search(state.channelsFor(page.lane, page.categoryId), page.query).isNotEmpty() -> EmptyState(
            Icons.Rounded.SearchOff,
            "Aucune chaîne en ${quality.label} ici",
            "Cette sélection existe dans d’autres qualités."
        ) {
            TvButton("Toutes les qualités", { state.setQuality(null) }, icon = Icons.Rounded.Clear, primary = true)
        }
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
    showCategory: Boolean,
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
        modifier = modifier.fillMaxWidth().height(56.dp).onFocusChanged { if (it.isFocused) onFocus() },
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
            ChannelLogo(channel, Modifier.size(width = 58.dp, height = 38.dp).alpha(if (isUnavailable && !focused) 0.45f else 1f), corner = 7.dp, initialsSize = 13)
            Column(Modifier.weight(1f).alpha(if (isUnavailable && !focused) 0.6f else 1f)) {
                Text(channel.displayName, style = Type.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (isUnavailable) UnavailableTag()
                    EventTag(channel)
                    if (channel.event == null) channel.countryTag?.let { Badge(it) }
                    if (channel.event == null) channel.badges.take(2).forEach { Badge(it) }
                    if (showCategory || channel.event != null) {
                        Text(channel.categoryDisplay, style = Type.Caption, color = secondaryColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (isFavorite) Icon(Icons.Rounded.Star, contentDescription = "Favori", tint = if (focused) Palette.GoldOnLight else Palette.Gold, modifier = Modifier.size(24.dp))
            // Lists guideline: an indicator, not only a background colour, marks the row shown in the preview.
            if (isPreviewing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(18.dp))
                    Text("Aperçu", style = Type.Caption, color = Palette.Accent)
                }
            }
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
    onRemoveRecent: (() -> Unit)?,
    watchFocus: FocusRequester,
    modifier: Modifier
) {
    val notStarted = ChannelText.notStartedYet(channel.event)
    // No preview while the app is in the background (Home button, screensaver): it would use the provider's
    // single connection for nothing. It restarts when the viewer comes back.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val started = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val context = LocalContext.current
    val account = state.account ?: return
    val player = remember { buildStreamPlayer(context) }
    var reportedState by remember { mutableStateOf(PreviewState.WAITING) }
    var reportedFor by remember { mutableStateOf<String?>(null) }
    // A state reported for the previous channel never shows on the new one ("EN DIRECT" over a loading logo).
    val previewState = if (reportedFor == channel.id) reportedState else PreviewState.WAITING
    var errorText by remember { mutableStateOf("") }
    // Many provider failures (HTTP 407, timeouts) are intermittent: retry twice before calling it unavailable.
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(channel.id) { retry = 0 }
    val currentChannel by rememberUpdatedState(channel)

    // The stream that is actually loaded. The player can report READY for the old stream after focus has
    // moved on (or after stop()); such reports must not mark the newly selected channel as live.
    var loadedFor by remember { mutableStateOf<String?>(null) }

    fun report(newState: PreviewState) {
        if (newState != PreviewState.WAITING && loadedFor != currentChannel.id) return
        reportedState = newState
        reportedFor = currentChannel.id
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> if (player.currentMediaItem != null && loadedFor == currentChannel.id) {
                        report(PreviewState.READY)
                        state.markWorking(currentChannel.id)
                    }
                    Player.STATE_BUFFERING -> if (previewState != PreviewState.READY) report(PreviewState.BUFFERING)
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (retry < 2) {
                    retry++
                    return
                }
                errorText = playbackErrorMessage(error)
                report(PreviewState.ERROR)
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
    LaunchedEffect(channel.id, account, retry, started) {
        loadedFor = null
        if (retry == 0) report(PreviewState.WAITING)
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        // An event feed is empty before kickoff: show when it starts instead of opening a stream.
        if (notStarted || !started) return@LaunchedEffect
        delay(if (retry == 0) 650L else 1_500L * retry)
        player.setMediaItem(MediaItem.fromUri(state.repository.streamUrl(account, channel)))
        loadedFor = channel.id
        player.volume = 0f
        if (retry == 0) report(PreviewState.BUFFERING)
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
                    if (notStarted) {
                        Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(channel.event?.let(ChannelText::eventStartSentence).orEmpty(), style = Type.Label, color = Palette.OnSurface, textAlign = TextAlign.Center)
                            Text("L’aperçu démarrera au début du direct.", style = Type.Caption, color = Palette.Muted, textAlign = TextAlign.Center)
                        }
                    } else if (previewState == PreviewState.ERROR) {
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
            if (onRemoveRecent != null) TvButton("Retirer", onRemoveRecent, icon = Icons.Rounded.Clear)
        }

    }
}

private fun plural(count: Int) = if (count > 1) "s" else ""

/** Down from a filter chip lands on the list, never on the preview buttons that sit lower right. */
private fun Modifier.downToList(list: FocusRequester, hasResults: Boolean): Modifier =
    if (hasResults) focusProperties { down = list } else this
