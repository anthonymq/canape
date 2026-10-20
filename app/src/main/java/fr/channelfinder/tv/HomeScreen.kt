package fr.channelfinder.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Stadium
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

private const val FIRST_TILE = "lane:FRANCE"
private const val ROW_LIMIT = 12

private data class LaneStyle(val lane: BrowseLane, val icon: ImageVector)

private val laneDest = mapOf(
    BrowseLane.FRANCE to NavDest.FRANCE, BrowseLane.PORTUGUESE to NavDest.PORTUGUESE,
    BrowseLane.SPORTS to NavDest.SPORTS, BrowseLane.EVENTS to NavDest.EVENTS
)

private val laneStyles = listOf(
    LaneStyle(BrowseLane.FRANCE, Icons.Rounded.LiveTv),
    LaneStyle(BrowseLane.PORTUGUESE, Icons.Rounded.Language),
    LaneStyle(BrowseLane.SPORTS, Icons.Rounded.SportsSoccer),
    LaneStyle(BrowseLane.EVENTS, Icons.Rounded.Stadium)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(state: AppState, saved: Boolean = false) {
    val summaryRequests = remember { Semaphore(2) }
    var programmeNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); programmeNow = System.currentTimeMillis() } }
    @Composable
    fun programmeTitle(channel: Channel): String = state.account?.let { account ->
        currentProgrammeSummary(account, channel.id, state.guideRepository, summaryRequests, programmeNow, showStatus = false)
    }.orEmpty()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }
    var homeHasFocus by remember { mutableStateOf(false) }
    var removalFocusKey by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val number = remember { NumberFormat.getIntegerInstance(Locale.FRANCE) }
    val listState = if (saved) state.savedListState else state.homeListState
    val history = state.account?.let { state.watchHistory.entries(it) }.orEmpty()
    val resume = LibraryKind.entries.flatMap { continueWatching(history, it) }.sortedByDescending { it.updatedAt }
    val firstHomeFocus = FIRST_TILE
    val resumeSessions = LibraryKind.entries.associateWith { kind ->
        val key = "$saved:${kind.name}"
        state.homeResumeSessions.getOrPut(key) { LibrarySession(state.account) }.let {
            if (it.account == state.account) it else LibrarySession(state.account).also { fresh -> state.homeResumeSessions[key] = fresh }
        }
    }
    val savedTitles = state.account?.let { state.watchHistory.savedTitles(it) }.orEmpty()

    fun Modifier.homeFocus(key: String) = this.focusRequester(requester(key)).onFocusChanged {
        if (it.isFocused) {
            if (saved) state.savedFocusKey = key else state.homeFocusKey = key
        }
    }

    LaunchedEffect(Unit) {
        if (saved) {
            val target = state.savedFocusKey?.takeUnless { key ->
                key.startsWith("title:") && savedTitles.none { "title:${it.key}" == key }
            }
            if (target == null) listState.scrollToItem(0)
            focusWhenReady(target?.let(::requester), requester("saved:live"))
        }
        else focusWhenReady(state.homeFocusKey?.let { requesters[it] }, requester(firstHomeFocus), requester(FIRST_TILE))
    }

    LaunchedEffect(removalFocusKey) {
        val target = removalFocusKey ?: return@LaunchedEffect
        if (target == FIRST_TILE || target == "saved:live") listState.scrollToItem(0)
        focusWhenReady(requester(target))
        removalFocusKey = null
    }

    // Left navigation guideline: Back opens the drawer on Accueil (Home scrolls back to the top behind it);
    // a second Back, from the drawer, leaves the app.
    BackHandler(enabled = homeHasFocus) {
        scope.launch {
            state.homeFocusKey = firstHomeFocus
            listState.animateScrollToItem(0)
            state.focusDrawer()
        }
    }

    val recentChannels = state.recent.mapNotNull(state.channelsById::get).filter { it.lane != BrowseLane.EVENTS.label }
    val favoriteChannels = state.favorites.mapNotNull(state.channelsById::get)
    val pinned = state.pinnedCategories.mapNotNull(state.categoriesById::get)
    val backdropWatch = if (recentChannels.isEmpty()) resume.firstOrNull() else null
    val homeResumeSession = state.homeResumeSessions.getOrPut("home:all") { LibrarySession(state.account) }.let {
        if (it.account == state.account) it else LibrarySession(state.account).also { fresh -> state.homeResumeSessions["home:all"] = fresh }
    }
    fun LazyListScope.resumeRows() {
        if (!saved) {
            if (resume.isNotEmpty()) item(key = "resume:all") {
                Column(Modifier.padding(horizontal = Spacing.Side, vertical = 8.dp)) {
                    VodContinueRow(state, homeResumeSession, resume, requester("resume:all"),
                        onEntryFocused = { state.homeFocusKey = "resume:all" }, title = "À reprendre")
                }
            }
            return
        }
        LibraryKind.entries.forEach { kind ->
            val entries = resume.filter { it.kind == kind }
            if (entries.isNotEmpty()) item(key = "resume:${kind.name}") {
                Column(Modifier.padding(horizontal = Spacing.Side, vertical = 8.dp)) {
                    VodContinueRow(state, resumeSessions.getValue(kind), entries, requester("resume:${kind.name}"), onEntryFocused = {
                        if (saved) state.savedFocusKey = "resume:${kind.name}" else state.homeFocusKey = "resume:${kind.name}"
                    }, title = if (kind == LibraryKind.MOVIES) "Films à reprendre" else "Séries à reprendre")
                }
            }
        }
    }
    fun LazyListScope.recentRows(cardWidth: Dp) {
        if (!saved && recentChannels.isNotEmpty()) item(key = "recent") {
            HomeRow(title = "Vos dernières chaînes") {
                recentChannels.take(ROW_LIMIT).forEach { channel ->
                    key(channel.id) {
                        ChannelCard(channel, isFavorite = channel.id in state.favorites, width = cardWidth,
                            modifier = Modifier.homeFocus("recent:${channel.id}"), programmeSummary = programmeTitle(channel),
                            onLongClick = { state.confirmPressGuard.consumeCurrentPress(); state.toggleFavorite(channel) }) {
                            state.play(channel, recentChannels)
                        }
                    }
                }
                SeeAllCard(Modifier.homeFocus("recent:all")) { state.navigate(Page.Browse(BrowseLane.RECENT)) }
            }
        }
    }
    val focusClearance = with(LocalDensity.current) { 10.dp.toPx() }
    val bringIntoView = remember(focusClearance) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val leading = offset - focusClearance
                val trailing = offset + size + focusClearance - containerSize
                return when {
                    size + focusClearance * 2 > containerSize -> leading
                    leading < 0 -> leading
                    trailing > 0 -> trailing
                    else -> 0f
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (!saved) ProgrammeBackdrop(backdropWatch?.displayItem, Modifier.matchParentSize(), brandFallback = true)
        val contentInset = if (saved) 0.dp else Spacing.RailInset
        val cardWidth = (maxWidth - contentInset - Spacing.Side * 2 - Spacing.Gutter * 5) / 6
        Column(Modifier.fillMaxSize().padding(start = contentInset)) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).onFocusChanged { homeHasFocus = it.hasFocus },
                contentPadding = PaddingValues(top = Spacing.Top, bottom = Spacing.Top)
            ) {
                if (saved) item(key = "header") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.Side),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Text("Ma liste", style = Type.Headline,
                            color = Palette.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Clock(style = Type.Caption)
                    }
                }

                if (!saved) item(key = "lanes") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.Side, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter)
                    ) {
                        // Counted in the background (scanning 24k channels here froze Home after launch).
                        val (liveEvents, soonEvents) = state.eventCounts
                        // Counts match what the lane shows once opened, including the saved quality filter.
                        val quality = state.qualityFilter
                        // Null until the background pass has counted; the cards show plain totals meanwhile.
                        val qualityCounts = quality?.let { state.qualityLaneCounts[it] }
                        laneStyles.forEach { style ->
                            LaneCard(
                                style = style,
                                count = if (style.lane == BrowseLane.EVENTS) {
                                    when {
                                        liveEvents > 0 -> "$liveEvents en cours"
                                        soonEvents > 0 -> "$soonEvents à venir"
                                        else -> "Voir les matchs"
                                    }
                                } else if (qualityCounts != null && quality != null) {
                                    "${number.format(qualityCounts[style.lane.label] ?: 0)} en ${quality.label}"
                                } else "${number.format(state.laneCounts[style.lane.label] ?: 0)} chaînes",
                                modifier = Modifier.weight(1f).homeFocus("lane:${style.lane.name}")
                            ) { state.openDestination(laneDest.getValue(style.lane)) }
                        }
                    }
                }

                if (saved) item(key = "saved-actions") {
                    Row(Modifier.padding(horizontal = Spacing.Side, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
                        TvButton("Mes chaînes favorites", { state.navigate(Page.Browse(BrowseLane.FAVORITES)) }, Modifier.homeFocus("saved:live"), icon = Icons.Rounded.StarOutline)
                        TvButton("Parcourir les catégories", { state.navigate(Page.CategoryPicker()) }, icon = Icons.Rounded.GridView)
                    }
                }

                if (savedTitles.isNotEmpty() && saved) {
                    LibraryKind.entries.forEach { kind ->
                        val titles = savedTitles.filter { it.kind == kind }
                        if (titles.isNotEmpty()) item(key = "saved:${kind.name}") {
                            HomeRow(if (kind == LibraryKind.MOVIES) "Films enregistrés" else "Séries enregistrées", "OK long pour retirer") {
                                titles.forEach { title ->
                                    key(title.key) {
                                        Column(Modifier.width(cardWidth)) {
                                            VodPoster(title.item, null, Modifier.homeFocus("title:${title.key}"), onLongClick = {
                                                state.confirmPressGuard.consumeCurrentPress()
                                                removalFocusKey = homeFocusAfterRemoval(titles.map { "title:${it.key}" }, "title:${title.key}", "saved:live")
                                                state.account?.let { state.watchHistory.toggleSaved(it, title.item, kind) }
                                            }) {
                                                state.navigate(if (kind == LibraryKind.MOVIES) Page.MovieDetails(title.item) else Page.Episodes(title.item))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (saved) item(key = "saved-titles-empty") {
                    Column(Modifier.padding(horizontal = Spacing.Side, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Gardez vos prochaines envies ici", style = Type.Title, color = Palette.OnSurface)
                        Text("Sur la fiche d’un film ou d’une série, choisissez « Ajouter à ma liste ».", style = Type.Body, color = Palette.Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
                            TvButton("Parcourir les films", { state.openDestination(NavDest.MOVIES) })
                            TvButton("Parcourir les séries", { state.openDestination(NavDest.SERIES) })
                        }
                    }
                }

                if (saved && resume.isNotEmpty()) resumeRows()
                else if (saved) item(key = "saved-empty") {
                    Column(Modifier.padding(horizontal = Spacing.Side, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Films et séries à reprendre", style = Type.Title, color = Palette.OnSurface)
                        Text("Vos films et épisodes commencés apparaîtront ici.", style = Type.Body, color = Palette.Muted)
                    }
                }

                item(key = "favorites") {
                    HomeRow(title = "Vos chaînes favorites", subtitle = if (favoriteChannels.isEmpty()) null else "En un clic") {
                        if (favoriteChannels.isEmpty()) {
                            HintCard(Icons.Rounded.StarOutline, "Aucun favori pour l’instant", "Sur une chaîne, faites un appui long sur OK pour l’ajouter ici.")
                        } else {
                            favoriteChannels.take(ROW_LIMIT).forEach { channel ->
                                key(channel.id) {
                                ChannelCard(channel, isFavorite = true, width = cardWidth, modifier = Modifier.homeFocus("fav:${channel.id}"), programmeSummary = programmeTitle(channel),
                                    onLongClick = {
                                        state.confirmPressGuard.consumeCurrentPress()
                                        removalFocusKey = homeFocusAfterRemoval(
                                            favoriteChannels.map { "fav:${it.id}" }, "fav:${channel.id}", if (saved) "saved:live" else FIRST_TILE, ROW_LIMIT
                                        )
                                        state.toggleFavorite(channel)
                                    }) { state.play(channel, favoriteChannels) }
                                }
                            }
                            if (favoriteChannels.size > ROW_LIMIT) SeeAllCard(Modifier.homeFocus("fav:all")) { state.openDestination(NavDest.FAVORITES) }
                        }
                    }
                }

                if (!saved) resumeRows()

                item(key = "pinned") {
                    HomeRow(title = "Catégories épinglées", subtitle = if (pinned.isEmpty()) null else "Appui long sur OK pour retirer") {
                        pinned.forEach { category ->
                            key(category.id) {
                            CategoryCard(
                                category, number.format(state.categoryCounts[category.id] ?: 0),
                                Modifier.homeFocus("cat:${category.id}"),
                                onLongClick = {
                                    state.confirmPressGuard.consumeCurrentPress()
                                    removalFocusKey = homeFocusAfterRemoval(
                                        pinned.map { "cat:${it.id}" }, "cat:${category.id}", "cat:add"
                                    )
                                    state.togglePin(category)
                                }
                            ) { state.navigate(Page.Browse(BrowseLane.ALL, categoryId = category.id)) }
                            }
                        }
                        AddCategoryCard(Modifier.homeFocus("cat:add")) { state.openDestination(NavDest.CATEGORIES) }
                    }
                }

                recentRows(cardWidth)

                item(key = "utilities") {
                    Row(Modifier.padding(horizontal = Spacing.Side, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
                        TvButton("Guide TV", { state.openDestination(NavDest.GUIDE) }, icon = Icons.Rounded.Schedule)
                        TvButton("Toutes les catégories", { state.openDestination(NavDest.CATEGORIES) }, icon = Icons.Rounded.GridView)
                    }
                }

            }
            }
        }
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Bonjour"
        in 12..17 -> "Bon après-midi"
        else -> "Bonsoir"
    }
}


@Composable
private fun HomeRow(
    title: String?,
    subtitle: String? = null,
    topPadding: androidx.compose.ui.unit.Dp = 6.dp,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = topPadding)) {
        if (title != null) {
            Row(Modifier.padding(horizontal = Spacing.Side), verticalAlignment = Alignment.Bottom) {
                Text(title, style = Type.Headline, color = Palette.OnSurface)
                if (subtitle != null) {
                    Spacer(Modifier.width(14.dp))
                    Text(subtitle, style = Type.Caption, color = Palette.Muted, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
        // Vertical padding leaves room for the focused card to grow without being clipped.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.Side, vertical = 10.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter)
        ) { content() }
    }
}

@Composable
private fun LaneCard(style: LaneStyle, count: String, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier.heightIn(min = 56.dp), focusedScale = 1.02f) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(style.icon, null, Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(style.lane.label, style = Type.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(count, style = Type.Caption, color = secondaryColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}


@Composable
fun ChannelCard(channel: Channel, isFavorite: Boolean, modifier: Modifier, width: Dp, programmeSummary: String = "", onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    // Height follows the text, so nothing is cut off with a larger system text size.
    CardSurface(onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(width).heightIn(min = 108.dp).fillMaxHeight()) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(Spacing.ChannelArtwork)) {
                ChannelLogo(channel, Modifier.fillMaxSize(), corner = 0.dp, initialsSize = 22)
                if (isFavorite) {
                    Icon(
                        Icons.Rounded.Star, contentDescription = "Favori", tint = Palette.Gold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp)
                    )
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text(channel.displayName, style = Type.Label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                channel.badges.firstOrNull()?.let { Badge(it, tone = Palette.Muted) }
                if (programmeSummary.isNotBlank()) Text(programmeSummary, style = Type.Caption, color = Palette.Muted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CategoryCard(category: ChannelCategory, count: String, modifier: Modifier, onLongClick: () -> Unit, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(212.dp).heightIn(min = 72.dp).fillMaxHeight(), outline = Palette.Outline) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(category.displayName, style = Type.TitleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                category.countryTag?.let { Badge(it) }
                Text("$count chaînes", style = Type.Caption, color = secondaryColor())
            }
        }
    }
}

@Composable
private fun AddCategoryCard(modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier.width(212.dp).heightIn(min = 72.dp).fillMaxHeight(), container = Palette.Ink, outline = Palette.Outline) {
        Row(
            Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(22.dp))
            Column {
                Text("Épingler", style = Type.Label, maxLines = 1)
                Text("Une catégorie ici", style = Type.Caption, color = secondaryColor(), maxLines = 2)
            }
        }
    }
}

@Composable
private fun SeeAllCard(modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier.width(108.dp).heightIn(min = 108.dp).fillMaxHeight(), container = Palette.Ink, outline = Palette.Outline) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text("Tout voir", style = Type.Label)
        }
    }
}

@Composable
private fun HintCard(icon: ImageVector, title: String, message: String) {
    Row(
        Modifier.width(520.dp).heightIn(min = 72.dp)
            .border(1.dp, Palette.Outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(30.dp))
        Column {
            Text(title, style = Type.Label, color = Palette.OnSurface)
            Text(message, style = Type.Caption, color = Palette.Muted)
        }
    }
}
