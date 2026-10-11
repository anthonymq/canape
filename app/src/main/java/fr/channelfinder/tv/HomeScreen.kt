package fr.channelfinder.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

private const val FIRST_TILE = "lane:FRANCE"
private const val ROW_LIMIT = 12

private data class LaneStyle(val lane: BrowseLane, val emoji: String, val subtitle: String, val colors: List<Color>)

private val laneStyles = listOf(
    LaneStyle(BrowseLane.FRANCE, "🇫🇷", "Chaînes françaises", listOf(Color(0xFF27448F), Color(0xFF162550))),
    LaneStyle(BrowseLane.PORTUGUESE, "🇵🇹", "Portugal et Brésil", listOf(Color(0xFF1C6B45), Color(0xFF0F3527))),
    LaneStyle(BrowseLane.SPORTS, "⚽", "Chaînes sport", listOf(Color(0xFF8A4A16), Color(0xFF45240D))),
    LaneStyle(BrowseLane.EVENTS, "🏟️", "Matchs et directs", listOf(Color(0xFF6A2A6E), Color(0xFF2E1533)))
)

@Composable
fun HomeScreen(state: AppState) {
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }
    var homeHasFocus by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val number = remember { NumberFormat.getIntegerInstance(Locale.FRANCE) }

    fun Modifier.homeFocus(key: String) = this.focusRequester(requester(key)).onFocusChanged {
        if (it.isFocused) state.homeFocusKey = key
    }

    LaunchedEffect(Unit) {
        delay(40)
        state.homeFocusKey?.let { key -> runCatching { requesters[key]?.requestFocus() } }
        delay(60)
        if (!homeHasFocus) runCatching { requester(FIRST_TILE).requestFocus() }
    }

    // TV convention: Back first returns to the top of Home, then leaves the app.
    BackHandler(enabled = homeHasFocus && state.homeFocusKey != FIRST_TILE) {
        scope.launch {
            state.homeListState.animateScrollToItem(0)
            runCatching { requester(FIRST_TILE).requestFocus() }
        }
    }

    val recentChannels = state.recent.mapNotNull(state.channelsById::get)
    val favoriteChannels = state.favorites.mapNotNull(state.channelsById::get)
    val pinned = state.pinnedCategories.mapNotNull(state.categoriesById::get)

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
                FocusSurface(
                    onClick = { state.navigate(Page.Settings) },
                    modifier = Modifier.size(48.dp).homeFocus("settings"),
                    shape = CircleShape,
                    container = Palette.Surface2,
                    outline = Palette.Outline
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Réglages", modifier = Modifier.align(Alignment.Center).size(24.dp))
                }
                Clock(style = Type.Headline)
            }
        }

        item(key = "lanes") {
            HomeRow(title = null) {
                val (liveEvents, soonEvents) = remember(state.channels) { state.upcomingEventCount() }
                laneStyles.forEach { style ->
                    LaneCard(
                        style = style,
                        count = if (style.lane == BrowseLane.EVENTS) {
                            when {
                                liveEvents > 0 -> "$liveEvents en cours"
                                soonEvents > 0 -> "$soonEvents à venir"
                                else -> "Voir les matchs"
                            }
                        } else "${number.format(state.laneCounts[style.lane.label] ?: 0)} chaînes",
                        modifier = Modifier.homeFocus("lane:${style.lane.name}")
                    ) { state.navigate(Page.Browse(style.lane)) }
                }
            }
        }

        item(key = "shortcuts") {
            HomeRow(title = null, topPadding = 0.dp) {
                ShortcutCard(Icons.Rounded.Search, Palette.Accent, "Rechercher", "Par nom", Modifier.homeFocus("search")) {
                    state.navigate(Page.Browse(BrowseLane.ALL, startSearch = true))
                }
                ShortcutCard(Icons.Rounded.Star, Palette.Gold, "Favoris", countLabel(state.favorites.size, "chaîne"), Modifier.homeFocus("favorites")) {
                    state.navigate(Page.Browse(BrowseLane.FAVORITES))
                }
                ShortcutCard(Icons.Rounded.History, Palette.Muted, "Récents", countLabel(state.recent.size, "chaîne"), Modifier.homeFocus("recent")) {
                    state.navigate(Page.Browse(BrowseLane.RECENT))
                }
                ShortcutCard(
                    Icons.Rounded.GridView, Palette.Muted, "Catégories",
                    if (state.pinnedCategories.isEmpty()) "À épingler" else countLabel(state.pinnedCategories.size, "épinglée"),
                    Modifier.homeFocus("categories")
                ) { state.navigate(Page.CategoryPicker()) }
            }
        }

        if (recentChannels.isNotEmpty()) {
            item(key = "recent") {
                HomeRow(title = "Reprendre", subtitle = "Vos dernières chaînes") {
                    recentChannels.take(ROW_LIMIT).forEach { channel ->
                        ChannelCard(channel, isFavorite = channel.id in state.favorites, modifier = Modifier.homeFocus("recent:${channel.id}"),
                            onLongClick = { state.toggleFavorite(channel) }) { state.play(channel, recentChannels) }
                    }
                    if (recentChannels.size > ROW_LIMIT) SeeAllCard(Modifier.homeFocus("recent:all")) { state.navigate(Page.Browse(BrowseLane.RECENT)) }
                }
            }
        }

        item(key = "favorites") {
            HomeRow(title = "Mes favoris", subtitle = if (favoriteChannels.isEmpty()) null else "Appui long sur OK pour retirer") {
                if (favoriteChannels.isEmpty()) {
                    HintCard(Icons.Rounded.StarOutline, "Aucun favori pour l’instant", "Sur une chaîne, faites un appui long sur OK pour l’ajouter ici.")
                } else {
                    favoriteChannels.take(ROW_LIMIT).forEach { channel ->
                        ChannelCard(channel, isFavorite = true, modifier = Modifier.homeFocus("fav:${channel.id}"),
                            onLongClick = { state.toggleFavorite(channel) }) { state.play(channel, favoriteChannels) }
                    }
                    if (favoriteChannels.size > ROW_LIMIT) SeeAllCard(Modifier.homeFocus("fav:all")) { state.navigate(Page.Browse(BrowseLane.FAVORITES)) }
                }
            }
        }

        item(key = "pinned") {
            HomeRow(title = "Catégories épinglées", subtitle = if (pinned.isEmpty()) null else "${state.categories.size} catégories disponibles") {
                pinned.forEach { category ->
                    CategoryCard(
                        category, number.format(state.categoryCounts[category.id] ?: 0),
                        Modifier.homeFocus("cat:${category.id}"),
                        onLongClick = { state.togglePin(category) }
                    ) { state.navigate(Page.Browse(BrowseLane.ALL, categoryId = category.id)) }
                }
                AddCategoryCard(Modifier.homeFocus("cat:add")) { state.navigate(Page.CategoryPicker()) }
            }
        }

        item(key = "hints") {
            HintBar(
                "OK" to "Ouvrir", "OK long" to "Favori / épingler", "RETOUR" to "Haut de page",
                modifier = Modifier.padding(start = Spacing.Side, top = 8.dp)
            )
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

private fun countLabel(count: Int, noun: String): String = when (count) {
    0 -> "Aucune"
    1 -> "1 $noun"
    else -> "$count ${noun}s"
}

@Composable
private fun HomeRow(
    title: String?,
    subtitle: String? = null,
    topPadding: androidx.compose.ui.unit.Dp = 18.dp,
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
                .padding(horizontal = Spacing.Side, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter)
        ) { content() }
    }
}

@Composable
private fun LaneCard(style: LaneStyle, count: String, modifier: Modifier, onClick: () -> Unit) {
    CardSurface(onClick = onClick, modifier = modifier.size(width = 196.dp, height = 150.dp), container = style.colors.last()) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(style.colors))) {
            Text(
                style.emoji, fontSize = 92.sp,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 18.dp, y = 22.dp).rotate(-12f).alpha(0.28f)
            )
            Column(Modifier.padding(20.dp).align(Alignment.TopStart)) {
                Text(style.emoji, fontSize = 26.sp)
                Spacer(Modifier.height(6.dp))
                Text(style.lane.label, style = Type.Headline, color = Color.White)
                Text(style.subtitle, style = Type.Caption, color = Color.White.copy(alpha = 0.82f), maxLines = 1)
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(count, style = Type.Label, color = Color.White)
                Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ShortcutCard(icon: ImageVector, tint: Color, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier.size(width = 196.dp, height = 76.dp), outline = Palette.Outline) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(title, style = Type.TitleSmall, maxLines = 1)
                Text(subtitle, style = Type.Caption, color = secondaryColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ChannelCard(channel: Channel, isFavorite: Boolean, modifier: Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    CardSurface(onClick = onClick, onLongClick = onLongClick, modifier = modifier.size(width = 196.dp, height = 156.dp)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(96.dp)) {
                ChannelLogo(channel, Modifier.fillMaxSize(), corner = 0.dp, initialsSize = 26)
                if (isFavorite) {
                    Icon(
                        Icons.Rounded.Star, contentDescription = "Favori", tint = Palette.Gold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp)
                    )
                }
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text(channel.displayName, style = Type.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    channel.badges.firstOrNull()?.let { Badge(it, tone = Palette.Muted) }
                    Text(channel.categoryDisplay, style = Type.Caption, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(category: ChannelCategory, count: String, modifier: Modifier, onLongClick: () -> Unit, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, onLongClick = onLongClick, modifier = modifier.size(width = 268.dp, height = 96.dp), outline = Palette.Outline) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
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
    FocusSurface(onClick = onClick, modifier = modifier.size(width = 268.dp, height = 96.dp), container = Palette.Ink, outline = Palette.Outline) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(24.dp))
            Column {
                Text("Épingler une catégorie", style = Type.Label)
                Text("Pour l’ouvrir d’ici", style = Type.Caption, color = secondaryColor())
            }
        }
    }
}

@Composable
private fun SeeAllCard(modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier.size(width = 156.dp, height = 156.dp), container = Palette.Ink, outline = Palette.Outline) {
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
        Modifier.width(520.dp).height(96.dp)
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
