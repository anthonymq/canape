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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.sync.Semaphore
import java.text.NumberFormat
import java.util.Locale

private enum class PreviewState { WAITING, BUFFERING, READY, ERROR }

private val qualityExemptLanes = setOf(BrowseLane.EVENTS, BrowseLane.FAVORITES, BrowseLane.RECENT)

@Composable
fun BrowserScreen(state: AppState, page: Page.Browse) {
    val summaryRequests = remember { Semaphore(2) }
    var programmeNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            programmeNow = System.currentTimeMillis()
        }
    }
    val number = remember { NumberFormat.getIntegerInstance(Locale.FRANCE) }
    val category = page.categoryId?.let(state.categoriesById::get)
    // Favorites/recents are captured when the list opens so un-starring a row does not yank focus away.
    // Récents is the exception: removing one from the preview must update the list.
    val recentKey = if (page.lane == BrowseLane.RECENT && page.categoryId == null) state.recent.toList() else null
    val loadedBase = backgroundCalculation(state.channels, page.lane, page.categoryId, recentKey) {
        state.channelsFor(page.lane, page.categoryId)
    }
    val base = loadedBase.orEmpty()
    // Events rarely carry a quality tag, and favorites/recents are the viewer's own picks: no quality filter there.
    val qualityApplies = page.categoryId != null || page.lane !in qualityExemptLanes
    val quality = if (qualityApplies) state.qualityFilter else null
    val loadedCountries = backgroundCalculation(base) { browseCountryOptions(base) }
    val countries = loadedCountries.orEmpty()
    val country = page.country?.takeIf { it in countries }
    val filterRowShown = qualityApplies || countries.isNotEmpty()
    val loadedResults = backgroundCalculation(base, page.query, quality, country) { checkActive ->
        state.search(if (country == null) base else base.filter { it.country == country }, page.query, quality, checkActive)
    }
    val calculating = loadedBase == null || loadedCountries == null || loadedResults == null
    val results = if (calculating) emptyList() else loadedResults.orEmpty()
    val filter = listOf(page.lane, page.categoryId, page.query, quality, page.country)
    var lastFilter by remember { mutableStateOf(filter) }
    val selected = results.firstOrNull { lastFilter == filter && it.id == state.selectedChannelId } ?: results.firstOrNull()
    val selectedSchedule = rememberProgrammeSchedule(state.account, selected?.id, state.guideRepository, programmeNow)
    LaunchedEffect(page, calculating, selected?.id) {
        if (state.page == page && !calculating && selected == null) state.livePlayback.stop()
    }
    val listState = state.browserListState

    val searchFocus = remember { FocusRequester() }
    var qualityDialogOpen by remember { mutableStateOf(false) }
    val qualityButtonFocus = remember { FocusRequester() }
    var countryDialogOpen by remember { mutableStateOf(false) }
    val countryButtonFocus = remember { FocusRequester() }
    val selectedRowFocus = remember { FocusRequester() }
    val watchFocus = remember { FocusRequester() }
    var editing by remember { mutableStateOf(page.startSearch) }
    var listHasFocus by remember { mutableStateOf(false) }

    // Back in a scrolled list returns to the top. On a screen opened from the drawer it also opens the
    // drawer (left navigation guideline); in a sub-page (a category from the picker) the next Back leaves.
    // Registered before the search field's handler, so Back while typing still ends editing first.
    val scope = rememberCoroutineScope()
    var screenHasFocus by remember { mutableStateOf(false) }
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
    BackHandler(enabled = screenHasFocus && state.isDrawerRoot && !editing) { state.focusDrawer() }
    BackHandler(enabled = screenHasFocus && scrolled && results.isNotEmpty()) {
        scope.launch {
            state.selectedChannelId = results.first().id
            listState.scrollToItem(0)
            delay(32)
            if (state.isDrawerRoot) state.focusDrawer() else runCatching { selectedRowFocus.requestFocus() }
        }
    }

    var initialFocusRestored by remember { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (!editing && initialFocusRestored) {
            repeat(2) { androidx.compose.runtime.withFrameNanos { } }
            focusWhenReady(searchFocus)
        }
    }
    LaunchedEffect(calculating) {
        if (calculating || initialFocusRestored) return@LaunchedEffect
        if (page.startSearch || editing) {
            initialFocusRestored = true
            if (page.startSearch) state.replaceTop(page.copy(startSearch = false))
            return@LaunchedEffect
        }
        val index = results.indexOfFirst { it.id == selected?.id }
        delay(16)
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.scrollToItem((index - 2).coerceAtLeast(0))
        }
        focusWhenReady(
            selectedRowFocus.takeIf { index >= 0 },
            searchFocus
        )
        initialFocusRestored = true
    }

    // A new query or lane starts again from the best match.
    LaunchedEffect(page.lane, page.categoryId, page.query, quality, country, calculating) {
        if (calculating) return@LaunchedEffect
        if (filter != lastFilter) {
            state.selectedChannelId = null
            listState.scrollToItem(0)
        }
        lastFilter = filter
    }
    LaunchedEffect(selected?.id) { if (selected != null && state.selectedChannelId == null) state.selectedChannelId = selected.id }

    val title = when {
        category != null -> category.displayName
        page.lane == BrowseLane.ALL && page.query.isNotBlank() -> "Recherche"
        page.lane == BrowseLane.ALL -> "Toutes les chaînes"
        else -> "En direct"
    }
    val noun = if (page.lane == BrowseLane.EVENTS && page.categoryId == null) "événement" else "chaîne"
    val countLabel = listOfNotNull(
        "${number.format(results.size)} $noun${plural(results.size)}",
        quality?.let { "en ${it.label}" },
        country?.let(ChannelText::countryName)
    ).joinToString(" · ").replace(" · en ", " en ")

    Column(
        Modifier.fillMaxSize()
            .onFocusChanged { screenHasFocus = it.hasFocus }
            .padding(horizontal = Spacing.Side, vertical = Spacing.Top)
    ) {
        val showSearch = editing
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showSearch) TvTextField(
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
                modifier = Modifier.weight(1f)
            )
            if (!showSearch) {
        if (filterRowShown) {
        TvChip(
            text = "Pays : ${country?.let(ChannelText::countryName) ?: "Tous"}",
            selected = country != null,
            onClick = { countryDialogOpen = true },
            modifier = Modifier.widthIn(max = 200.dp).focusRequester(countryButtonFocus).downToList(selectedRowFocus, results.isNotEmpty()),
            compact = true, maxLines = 1
        )
        }
        if (qualityApplies) TvChip("Qualité : ${quality?.label ?: "Toutes"}", quality != null,
            { qualityDialogOpen = true }, Modifier.focusRequester(qualityButtonFocus)
                .downToList(selectedRowFocus, results.isNotEmpty()), compact = true)
        FocusSurface({ editing = true }, Modifier.size(38.dp).focusRequester(searchFocus).downToList(selectedRowFocus, results.isNotEmpty())
            .semantics { contentDescription = if (page.query.isBlank()) "Rechercher dans cette sélection" else "Modifier la recherche : ${page.query}" },
            container = if (page.query.isBlank()) Palette.Surface1 else Palette.AccentDeep, focusedScale = 1f) {
            Icon(Icons.Rounded.Search, null, Modifier.align(Alignment.Center).size(20.dp))
        }
        Text(listOfNotNull(title.takeIf { category != null }, countLabel, page.query.takeIf { it.isNotBlank() }?.let { "« $it »" }).joinToString(" · "), style = Type.Caption, color = Palette.Muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))

        if (results.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (calculating) Text("Recherche…", style = Type.Body, color = Palette.Muted)
                else BrowseEmptyState(page, state, quality, country, base) {
                    scope.launch { focusWhenReady(selectedRowFocus, searchFocus) }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.Row)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(0.8f).fillMaxHeight()
                        .onFocusChanged { listHasFocus = it.hasFocus }
                        .focusRestorer(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ListGap),
                    contentPadding = PaddingValues(vertical = Spacing.ListPadding, horizontal = 10.dp)
                ) {
                    // Événements: day sections (En cours, Aujourd’hui, Demain…), like the Catégories sections.
                    val sectioned = page.lane == BrowseLane.EVENTS && page.categoryId == null && page.query.isBlank()
                    itemsIndexed(results, key = { _, channel -> channel.id }) { index, channel ->
                        val isSelected = channel.id == selected?.id
                        val section = if (sectioned) eventSection(channel) else null
                        Column {
                        if (section != null && (index == 0 || eventSection(results[index - 1]) != section)) {
                            Text(
                                section.uppercase(), style = Type.Eyebrow, color = Palette.Accent,
                                modifier = Modifier.padding(start = 4.dp, top = if (index == 0) 0.dp else 10.dp, bottom = 2.dp)
                            )
                        }
                        ChannelRow(
                            channel = channel,
                            programmeSummary = if (channel.event == null) state.account?.let { account ->
                                currentProgrammeSummary(account, channel.id, state.guideRepository, summaryRequests, programmeNow,
                                    active = isSelected, selectedRows = selectedSchedule.rows,
                                    selectedError = if (selectedSchedule.failed) "Programme indisponible" else null, showStatus = false)
                            } else null,
                            timeOnly = sectioned,
                            // The group name only where it changes; the preview always shows it.
                            showCategory = page.categoryId == null &&
                                (index == 0 || results[index - 1].categoryDisplay != channel.categoryDisplay),
                            isFavorite = channel.id in state.favorites,
                            isUnavailable = state.isUnavailable(channel.id),
                            isPreviewing = isSelected && !listHasFocus,
                            // Right always lands on "Regarder", never on the search field above it.
                            modifier = (if (isSelected) Modifier.focusRequester(selectedRowFocus) else Modifier)
                                .focusProperties {
                                    right = watchFocus
                                    if (index == 0) {
                                        up = if (editing || !filterRowShown) searchFocus else countryButtonFocus
                                    }
                                },
                            onFocus = { state.selectedChannelId = channel.id },
                            onPlay = { state.play(channel, results) },
                            // A match feed shows other programmes after the match: not a favourite.
                            onToggleFavorite = if (channel.event != null) null else ({ state.toggleFavorite(channel) })
                        )
                        }
                    }
                }
                if (selected != null) {
                    PreviewPane(
                        state = state,
                        channel = selected,
                        schedule = selectedSchedule,
                        now = programmeNow,
                        isFavorite = selected.id in state.favorites,
                        onWatch = { state.play(selected, results) },
                        onGuide = { state.openGuide(selected, results) },
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
                        listFocus = selectedRowFocus,
                        modifier = Modifier.weight(1.2f).fillMaxHeight()
                    )
                }
            }
        }
    }
    if (countryDialogOpen) BrowseCountryDialog(countries, country,
        onDismiss = {
            countryDialogOpen = false
            scope.launch { delay(32); focusWhenReady(countryButtonFocus) }
        },
        onSelect = { code -> state.setCountry(page, code) }
    )
    if (qualityDialogOpen) LiveQualityDialog(quality, onDismiss = {
        qualityDialogOpen = false
        scope.launch { delay(32); focusWhenReady(qualityButtonFocus) }
    }, onSelect = state::setQuality)
}

@Composable
private fun BrowseEmptyState(page: Page.Browse, state: AppState, quality: ChannelText.VideoQuality?, country: String?, base: List<Channel>, onRecover: () -> Unit) {
    val recovery = backgroundCalculation(base, page.query, quality) { checkActive ->
        (state.search(base, page.query, quality, checkActive).isNotEmpty()) to (state.search(base, page.query, checkActive = checkActive).isNotEmpty())
    }
    when {
        recovery == null -> Text("Recherche…", style = Type.Body, color = Palette.Muted)
        country != null && recovery.first -> EmptyState(
            Icons.Rounded.SearchOff,
            "Aucun résultat pour ce pays",
            "Essayez les autres pays de cette sélection."
        ) {
            TvButton("Tous les pays", { state.setCountry(page, null); onRecover() }, icon = Icons.Rounded.Clear, primary = true)
        }
        quality != null && recovery.second -> EmptyState(
            Icons.Rounded.SearchOff,
            "Aucune chaîne en ${quality.label} ici",
            "Cette sélection existe dans d’autres qualités."
        ) {
            TvButton("Toutes les qualités", { state.setQuality(null); onRecover() }, icon = Icons.Rounded.Clear, primary = true)
        }
        page.query.isNotBlank() -> EmptyState(
            Icons.Rounded.SearchOff,
            "Aucun résultat pour « ${page.query.trim()} »",
            if (page.lane == BrowseLane.ALL && page.categoryId == null) "Essayez un autre nom, par exemple TF1."
            else "Rien dans cette sélection. Essayez de chercher dans toutes les chaînes."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (page.lane != BrowseLane.ALL || page.categoryId != null) {
                    TvButton("Chercher partout", {
                        state.replaceTop(page.copy(lane = BrowseLane.ALL, categoryId = null, country = null))
                    },
                        icon = Icons.Rounded.Search, primary = true)
                }
                TvButton("Effacer la recherche", { state.replaceTop(page.copy(query = "")); onRecover() }, icon = Icons.Rounded.Clear)
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
    programmeSummary: String?,
    timeOnly: Boolean,
    showCategory: Boolean,
    isFavorite: Boolean,
    isUnavailable: Boolean,
    isPreviewing: Boolean,
    modifier: Modifier,
    onFocus: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: (() -> Unit)?
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    FocusSurface(
        onClick = onPlay,
        onLongClick = onToggleFavorite,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).onFocusChanged { if (it.isFocused) onFocus() },
        container = if (isPreviewing) Palette.AccentDeep else Palette.Surface1,
        focusedScale = 1.03f
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = Spacing.ListPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Recently failed channels stay listed but recede, so working ones stand out.
            ChannelLogo(channel, Modifier.size(width = 42.dp, height = 28.dp).alpha(if (isUnavailable && !focused) 0.45f else 1f), corner = 7.dp, initialsSize = 13)
            Column(Modifier.weight(1f).alpha(if (isUnavailable && !focused) 0.6f else 1f)) {
                Text(channel.displayName, style = Type.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (isUnavailable || channel.event != null || (showCategory && programmeSummary.isNullOrBlank())) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (isUnavailable) UnavailableTag()
                    EventTag(channel, timeOnly = timeOnly)
                    if ((showCategory && programmeSummary.isNullOrBlank()) || channel.event != null) {
                        Text(channel.categoryDisplay, style = Type.Caption, color = secondaryColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!programmeSummary.isNullOrBlank()) Text(programmeSummary, style = Type.Caption, color = secondaryColor(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isFavorite) Icon(Icons.Rounded.Star, contentDescription = "Favori", tint = if (focused) Palette.GoldOnLight else Palette.Gold, modifier = Modifier.size(18.dp))
            // Lists guideline: an indicator, not only a background colour, marks the row shown in the preview.
            if (isPreviewing) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Chaîne sélectionnée pour l’aperçu", tint = Palette.Accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PreviewPane(
    state: AppState,
    channel: Channel,
    schedule: ProgrammeSchedule,
    now: Long,
    isFavorite: Boolean,
    onWatch: () -> Unit,
    onGuide: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveRecent: (() -> Unit)?,
    watchFocus: FocusRequester,
    listFocus: FocusRequester,
    modifier: Modifier
) {
    val notStarted = ChannelText.notStartedYet(channel.event)
    // No preview while the app is in the background (Home button, screensaver): it would use the provider's
    // single connection for nothing. It restarts when the viewer comes back.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val started = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val account = state.account ?: return
    val favoriteFocus = remember { FocusRequester() }
    val guideFocus = remember { FocusRequester() }
    val removeFocus = remember { FocusRequester() }
    val session = state.livePlayback
    val player = remember(session) { session.player() }
    var reportedState by remember { mutableStateOf(PreviewState.WAITING) }
    var reportedFor by remember { mutableStateOf<String?>(null) }
    // A state reported for the previous channel never shows on the new one ("EN DIRECT" over a loading logo).
    val previewState = if (reportedFor == channel.id) reportedState else PreviewState.WAITING
    var errorText by remember { mutableStateOf("") }
    // Many provider failures (HTTP 407, timeouts) are intermittent: retry twice before calling it unavailable.
    var retry by remember { mutableIntStateOf(0) }
    var manualAttempt by remember(channel.id) { mutableIntStateOf(0) }
    var buffering by remember { mutableStateOf(false) }
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
                        buffering = false
                        report(PreviewState.READY)
                        state.markWorking(currentChannel.id)
                    }
                    Player.STATE_BUFFERING -> {
                        buffering = true
                        if (reportedFor != currentChannel.id || reportedState != PreviewState.READY) report(PreviewState.BUFFERING)
                    }
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (loadedFor != currentChannel.id) return
                val problem = playbackProblem(error)
                if (problem.retryAutomatically && retry < 2) {
                    retry++
                    return
                }
                errorText = problem.message
                report(PreviewState.ERROR)
                if (problem.marksChannelUnavailable) state.markUnavailable(currentChannel.id)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Start the stream only once focus settles, so fast D-pad movement does not open a stream per row.
    LaunchedEffect(channel.id, account, retry, manualAttempt, started) {
        if (state.page !is Page.Browse) return@LaunchedEffect
        if (started && !notStarted && retry == 0 && session.hasStream(channel.id)) {
            loadedFor = channel.id
            report(if (player.playbackState == Player.STATE_READY) PreviewState.READY else PreviewState.BUFFERING)
            player.playWhenReady = true
            return@LaunchedEffect
        }
        loadedFor = null
        buffering = false
        if (retry == 0) report(PreviewState.WAITING)
        session.stop()
        // An event feed is empty before kickoff: show when it starts instead of opening a stream.
        if (notStarted || !started) return@LaunchedEffect
        delay(if (retry == 0) 650L else 1_500L * retry)
        loadedFor = channel.id
        if (retry == 0) report(PreviewState.BUFFERING)
        session.play(channel.id, { MediaItem.fromUri(state.repository.streamUrl(account, channel)) }, state.audioLanguages)
    }

    BoxWithConstraints(modifier) {
    val reserve = if (LocalDensity.current.fontScale > 1.15f) 200.dp else 152.dp
    val videoHeight = (maxHeight - reserve).coerceAtLeast(88.dp).coerceAtMost(maxWidth * (9f / 16f))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
        Box(
            Modifier.fillMaxWidth().height(videoHeight).clip(RoundedCornerShape(16.dp)).background(Color.Black)
        ) {
            AndroidView(
                factory = { videoSurface(it).also(session::attach) },
                onReset = null,
                onRelease = session::detach,
                update = { view -> if (state.page is Page.Browse) session.attach(view) },
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
                            Text("L’aperçu sera disponible au début du direct.", style = Type.Caption, color = Palette.Muted, textAlign = TextAlign.Center)
                        }
                    } else if (previewState == PreviewState.ERROR) {
                        Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Error, modifier = Modifier.size(if (videoHeight < 150.dp) 22.dp else 34.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Aperçu indisponible", style = Type.Label, color = Palette.OnSurface)
                            if (videoHeight >= 150.dp) Text(errorText, style = Type.Caption, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            if (videoHeight >= 150.dp) Text("Réessayez ci-dessous.", style = Type.Caption, color = Palette.OnSurface, textAlign = TextAlign.Center)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (videoHeight >= 150.dp) {
                                ChannelLogo(channel, Modifier.size(width = 120.dp, height = 72.dp), initialsSize = 24)
                                Spacer(Modifier.height(14.dp))
                            }
                            Spinner(Modifier.size(26.dp), color = Palette.Muted, stroke = 3.dp)
                            Spacer(Modifier.height(8.dp))
                            Text("Chargement de l’aperçu…", style = Type.Caption, color = Palette.Muted)
                        }
                    }
                }
            }
            if (previewState == PreviewState.READY) {
                if (buffering) Spinner(Modifier.align(Alignment.Center).size(26.dp), color = Palette.OnSurface, stroke = 3.dp)
                Row(
                    Modifier.align(Alignment.TopStart).padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(8.dp).background(Palette.Live, CircleShape))
                    Text("EN DIRECT", style = Type.Badge, color = Color.White)
                }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(10.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp)).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                channel.countryTag?.let { Badge(it, tone = Color.White) }
                channel.badges.forEach { Badge(it, tone = Color.White) }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(channel.displayName, style = Type.Title, color = Palette.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        if (channel.event != null) EventTag(channel)
        ProgrammeInfo(schedule, now)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val afterWatch = guideFocus
            TvButton(if (previewState == PreviewState.ERROR) "Réessayer" else "Regarder",
                { if (previewState == PreviewState.ERROR) { retry = 0; manualAttempt++ } else onWatch() },
                Modifier.focusRequester(watchFocus).returnToList(listFocus, afterWatch), icon = Icons.Rounded.PlayArrow, primary = true, compact = true)
            TvButton("Guide TV", onGuide, Modifier.focusRequester(guideFocus).returnToList(listFocus,
                if (channel.event == null) favoriteFocus else if (onRemoveRecent != null) removeFocus else listFocus), icon = Icons.Rounded.Schedule, compact = true)
            if (channel.event == null) FavoriteButton(isFavorite, onToggleFavorite,
                Modifier.focusRequester(favoriteFocus).returnToList(listFocus, if (onRemoveRecent != null) removeFocus else listFocus), compact = true)
            if (onRemoveRecent != null) TvButton("Retirer", onRemoveRecent, Modifier.focusRequester(removeFocus).returnToList(listFocus), icon = Icons.Rounded.Clear, compact = true)
        }

    }
    }
}

private fun plural(count: Int) = if (count > 1) "s" else ""

/** Down from a filter chip lands on the list, never on the preview buttons that sit lower right. */
private fun Modifier.downToList(list: FocusRequester, hasResults: Boolean): Modifier =
    if (hasResults) focusProperties { down = list } else this

private fun Modifier.returnToList(list: FocusRequester, nextAction: FocusRequester = list): Modifier = focusProperties {
    left = list
    down = nextAction
    if (nextAction != list) right = nextAction
}

/** Day section of a match feed in Événements. */
private fun eventSection(channel: Channel): String? {
    val info = channel.event ?: return null
    return when (info.status()) {
        ChannelText.EventStatus.LIVE -> "En cours"
        ChannelText.EventStatus.UPCOMING -> ChannelText.eventDayLabel(info)
        ChannelText.EventStatus.FEED -> "Pendant les matchs"
        else -> "Terminés ou sans programme"
    }
}
