package fr.channelfinder.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

private data class LaneStyle(val lane: BrowseLane, val emoji: String, val subtitle: String, val colors: List<Color>)

private val laneDest = mapOf(
    BrowseLane.FRANCE to NavDest.FRANCE, BrowseLane.PORTUGUESE to NavDest.PORTUGUESE,
    BrowseLane.SPORTS to NavDest.SPORTS, BrowseLane.EVENTS to NavDest.EVENTS
)

private val laneStyles = listOf(
    LaneStyle(BrowseLane.FRANCE, "🇫🇷", "Chaînes françaises", listOf(Color(0xFF27448F), Color(0xFF162550))),
    LaneStyle(BrowseLane.PORTUGUESE, "🇵🇹", "Portugal et Brésil", listOf(Color(0xFF1C6B45), Color(0xFF0F3527))),
    LaneStyle(BrowseLane.SPORTS, "⚽", "France et Portugal", listOf(Color(0xFF8A4A16), Color(0xFF45240D))),
    LaneStyle(BrowseLane.EVENTS, "🏟️", "Matchs et directs", listOf(Color(0xFF6A2A6E), Color(0xFF2E1533)))
)

@Composable
fun HomeScreen(state: AppState) {
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
    val scope = rememberCoroutineScope()
    val number = remember { NumberFormat.getIntegerInstance(Locale.FRANCE) }

    fun Modifier.homeFocus(key: String) = this.focusRequester(requester(key)).onFocusChanged {
        if (it.isFocused) state.homeFocusKey = key
    }

    LaunchedEffect(Unit) {
        focusWhenReady(state.homeFocusKey?.let { requesters[it] }, requester(FIRST_TILE))
    }

    // Left navigation guideline: Back opens the drawer on Accueil (Home scrolls back to the top behind it);
    // a second Back, from the drawer, leaves the app.
    BackHandler(enabled = homeHasFocus) {
        scope.launch {
            state.homeFocusKey = FIRST_TILE
            state.homeListState.animateScrollToItem(0)
            state.focusDrawer()
        }
    }

    val recentChannels = state.recent.mapNotNull(state.channelsById::get).filter { it.lane != BrowseLane.EVENTS.label }
    val favoriteChannels = state.favorites.mapNotNull(state.channelsById::get)
    val pinned = state.pinnedCategories.mapNotNull(state.categoriesById::get)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardWidth = (maxWidth - Spacing.Side * 2 - Spacing.Gutter * 4) / 5
        LazyColumn(
            state = state.homeListState,
            modifier = Modifier.fillMaxSize().onFocusChanged { homeHasFocus = it.hasFocus },
            contentPadding = PaddingValues(top = Spacing.Top, bottom = 48.dp)
        ) {
            item(key = "header") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.Side),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting().uppercase(), style = Type.Eyebrow, color = Palette.Accent)
                        Spacer(Modifier.height(4.dp))
                        Text("Qu’est-ce qu’on regarde ?", style = Type.Display, color = Palette.OnSurface)
                    }
                    Clock(style = Type.Headline)
                }
            }

            item(key = "lanes") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.Side, vertical = 12.dp),
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

            if (recentChannels.isNotEmpty()) {
                item(key = "recent") {
                    HomeRow(title = "Reprendre", subtitle = "Vos dernières chaînes") {
                        recentChannels.take(ROW_LIMIT).forEach { channel ->
                            ChannelCard(channel, isFavorite = channel.id in state.favorites, width = cardWidth, modifier = Modifier.homeFocus("recent:${channel.id}"), programmeSummary = programmeTitle(channel),
                                onLongClick = { state.toggleFavorite(channel) }) { state.play(channel, recentChannels) }
                        }
                        // Récents has no drawer item: its full list (with "Retirer") opens from here.
                        SeeAllCard(Modifier.homeFocus("recent:all")) { state.navigate(Page.Browse(BrowseLane.RECENT)) }
                    }
                }
            }

            item(key = "favorites") {
                HomeRow(title = "Mes favoris", subtitle = if (favoriteChannels.isEmpty()) null else "Appui long sur OK pour retirer") {
                    if (favoriteChannels.isEmpty()) {
                        HintCard(Icons.Rounded.StarOutline, "Aucun favori pour l’instant", "Sur une chaîne, faites un appui long sur OK pour l’ajouter ici.")
                    } else {
                        favoriteChannels.take(ROW_LIMIT).forEach { channel ->
                            ChannelCard(channel, isFavorite = true, width = cardWidth, modifier = Modifier.homeFocus("fav:${channel.id}"), programmeSummary = programmeTitle(channel),
                                onLongClick = { state.toggleFavorite(channel) }) { state.play(channel, favoriteChannels) }
                        }
                        if (favoriteChannels.size > ROW_LIMIT) SeeAllCard(Modifier.homeFocus("fav:all")) { state.openDestination(NavDest.FAVORITES) }
                    }
                }
            }

            item(key = "pinned") {
                HomeRow(title = "Catégories épinglées", subtitle = if (pinned.isEmpty()) null else "Appui long sur OK pour retirer") {
                    pinned.forEach { category ->
                        CategoryCard(
                            category, number.format(state.categoryCounts[category.id] ?: 0),
                            Modifier.homeFocus("cat:${category.id}"),
                            onLongClick = { state.togglePin(category) }
                        ) { state.navigate(Page.Browse(BrowseLane.ALL, categoryId = category.id)) }
                    }
                    AddCategoryCard(Modifier.homeFocus("cat:add")) { state.openDestination(NavDest.CATEGORIES) }
                }
            }

            item(key = "hints") {
                HintBar(
                    "OK" to "Ouvrir", "OK long" to "Favori / épingler", "◀" to "Menu",
                    modifier = Modifier.padding(start = Spacing.Side, top = 8.dp)
                )
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
                .padding(horizontal = Spacing.Side, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter)
        ) { content() }
    }
}

@Composable
private fun LaneCard(style: LaneStyle, count: String, modifier: Modifier, onClick: () -> Unit) {
    CardSurface(onClick = onClick, modifier = modifier.heightIn(min = 88.dp), container = style.colors.last()) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(style.colors))) {
            Text(
                style.emoji, fontSize = 72.sp,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 16.dp, y = 18.dp).rotate(-12f).alpha(0.28f)
                    .clearAndSetSemantics { } // decorative: screen readers announce the emoji once
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // With a larger system text size the title needs the room; the big faded emoji stays as the cue.
                val roomy = LocalDensity.current.fontScale <= 1.15f
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (roomy && style.lane != BrowseLane.EVENTS) Text(style.emoji, fontSize = 20.sp)
                    Text(style.lane.label, style = Type.Title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(style.subtitle, style = Type.Caption, color = Color.White.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(count, style = Type.Label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}


@Composable
fun ChannelCard(channel: Channel, isFavorite: Boolean, modifier: Modifier, width: Dp, programmeSummary: String = "", onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    // Five columns share the width remaining after the drawer and safe margins.
    // Height follows the text, so nothing is cut off with a larger system text size.
    CardSurface(onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(width).heightIn(min = 108.dp)) {
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
                Text(channel.displayName, style = Type.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    channel.badges.firstOrNull()?.let { Badge(it, tone = Palette.Muted) }
                    Text(channel.categoryDisplay, style = Type.Caption, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (programmeSummary.isNotBlank()) Text(programmeSummary, style = Type.Caption, color = Palette.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CategoryCard(category: ChannelCategory, count: String, modifier: Modifier, onLongClick: () -> Unit, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, onLongClick = onLongClick, modifier = modifier.size(width = 212.dp, height = 72.dp), outline = Palette.Outline) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(category.displayName, style = Type.TitleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                category.countryTag?.let { Badge(it) }
                Text("$count chaînes", style = Type.Caption, color = secondaryColor())
            }
        }
    }
}

@Composable
private fun AddCategoryCard(modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier.size(width = 212.dp, height = 72.dp), container = Palette.Ink, outline = Palette.Outline) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(22.dp))
            Column {
                Text("Épingler", style = Type.Label, maxLines = 1)
                Text("Une catégorie ici", style = Type.Caption, color = secondaryColor(), maxLines = 1)
            }
        }
    }
}

@Composable
private fun SeeAllCard(modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier.size(width = 108.dp, height = 108.dp), container = Palette.Ink, outline = Palette.Outline) {
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
        Modifier.width(520.dp).height(72.dp)
            .border(1.dp, Palette.Outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp),
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
