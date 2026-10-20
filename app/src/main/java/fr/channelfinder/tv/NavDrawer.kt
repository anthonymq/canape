package fr.channelfinder.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

private val navigationGroups = listOf(
    listOf(NavDest.SEARCH to "Rechercher"),
    listOf(NavDest.HOME to "Accueil", NavDest.FRANCE to "France", NavDest.PORTUGUESE to "Português",
        NavDest.SPORTS to "Sports", NavDest.EVENTS to "Événements", NavDest.FAVORITES to "Favoris"),
    listOf(NavDest.MOVIES to "Films", NavDest.SERIES to "Séries", NavDest.SAVED to "Ma liste"),
    listOf(NavDest.GUIDE to "Guide TV", NavDest.CATEGORIES to "Catégories", NavDest.SETTINGS to "Réglages")
)
internal val navigationItems = navigationGroups.flatten()

@Composable
fun AppDrawer(state: AppState, content: @Composable () -> Unit) {
    val requesters = remember { navigationItems.associate { it.first to FocusRequester() } }
    val active = state.activeDest
    var navigationFocused by remember { mutableStateOf(false) }
    var contentFocused by remember { mutableStateOf(false) }
    var contentReady by remember { mutableStateOf(false) }
    var enterContent by remember { mutableIntStateOf(0) }
    val contentFocus = remember { FocusRequester() }
    val initialFocusRequest = remember { state.drawerFocusRequest }
    val navigationScroll = rememberScrollState()
    LaunchedEffect(enterContent) {
        if (enterContent > 0) { delay(32); focusWhenReady(contentFocus) }
    }
    LaunchedEffect(state.drawerFocusRequest) {
        if (state.drawerFocusRequest > initialFocusRequest) {
            delay(16)
            focusWhenReady(requesters[active])
        }
    }
    BackHandler(enabled = navigationFocused && state.page != Page.Home) {
        state.openDestination(NavDest.HOME)
        enterContent++
    }
    BackHandler(enabled = contentFocused && state.isDrawerRoot) { state.focusDrawer() }

    val contentInset = when (state.page) {
        Page.Home, is Page.Library -> 0.dp
        else -> Spacing.RailInset
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(start = contentInset)
            .onFocusChanged { contentFocused = it.hasFocus; if (it.hasFocus) contentReady = true }
            .focusRequester(contentFocus).focusRestorer().focusGroup()) { content() }
        if (navigationFocused) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                listOf(Palette.Ink.copy(alpha = 0.90f), Palette.Ink.copy(alpha = 0.40f)))))
            Box(Modifier.width(Spacing.RailExpanded + Spacing.RailStart * 2).fillMaxHeight().background(Palette.Ink))
        }
        Column(Modifier.padding(start = Spacing.RailStart, top = Spacing.Top, bottom = Spacing.Top)
            .width(if (navigationFocused) Spacing.RailExpanded else Spacing.RailWidth)
            .fillMaxHeight()
            .onFocusChanged { navigationFocused = it.hasFocus }
            .focusProperties { onEnter = { requesters.getValue(active).requestFocus() } }
            .focusGroup().onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionRight) {
                    if (event.type == KeyEventType.KeyDown) enterContent++
                    true
                } else false
            }) {
            Column(Modifier.weight(1f).verticalScroll(navigationScroll),
            verticalArrangement = Arrangement.spacedBy(Spacing.ListGap)) {
            navigationGroups.forEachIndexed { index, group ->
            if (index > 0) Spacer(Modifier.height(NavigationLayout.GroupGap))
            group.forEach { (dest, label) ->
                val icon = when (dest) {
                    NavDest.HOME -> Icons.Rounded.Home
                    NavDest.FRANCE, NavDest.PORTUGUESE -> Icons.Rounded.LiveTv
                    NavDest.SPORTS -> Icons.Rounded.SportsSoccer
                    NavDest.EVENTS -> Icons.Rounded.Stadium
                    NavDest.FAVORITES -> Icons.Rounded.Star
                    NavDest.GUIDE -> Icons.Rounded.Schedule
                    NavDest.CATEGORIES -> Icons.Rounded.GridView
                    NavDest.MOVIES -> Icons.Rounded.Movie
                    NavDest.SERIES -> Icons.Rounded.VideoLibrary
                    NavDest.SAVED -> Icons.Rounded.Bookmark
                    NavDest.SEARCH -> Icons.Rounded.Search
                    else -> Icons.Rounded.Settings
                }
                FocusSurface(
                    onClick = {
                        if (active != dest || !state.isDrawerRoot && state.page != Page.Home) state.openDestination(dest)
                        enterContent++
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(requesters.getValue(dest))
                        .focusProperties { canFocus = contentReady || state.drawerFocusRequest > initialFocusRequest }
                        .semantics { selected = active == dest; contentDescription = label },
                    container = if (active == dest) Palette.Surface2 else Color.Transparent,
                    content = if (active == dest) Palette.OnSurface else Palette.Muted,
                    shape = RoundedCornerShape(NavigationLayout.PillRadius), focusedScale = 1f, focusedGlow = false
                ) {
                    Row(Modifier.heightIn(min = NavigationLayout.RowHeight)
                        .padding(horizontal = NavigationLayout.ItemInset, vertical = NavigationLayout.GroupGap),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (dest) {
                            NavDest.FRANCE, NavDest.PORTUGUESE -> Box(Modifier.size(NavigationLayout.IconSize), contentAlignment = Alignment.Center) {
                                Text(if (dest == NavDest.FRANCE) "🇫🇷" else "🇵🇹", style = Type.Title)
                            }
                            else -> Icon(icon, null, Modifier.size(NavigationLayout.IconSize))
                        }
                        if (navigationFocused) Text(label, style = Type.Label, maxLines = 1)
                    }
                }
            }
            }
            }
            Box(Modifier.fillMaxWidth().height(NavigationLayout.IconSize), contentAlignment = Alignment.Center) {
                if (navigationScroll.canScrollForward || navigationScroll.canScrollBackward) {
                    Icon(if (navigationScroll.canScrollForward) Icons.Rounded.ArrowDropDown else Icons.Rounded.ArrowDropUp,
                        contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(NavigationLayout.IconSize))
                }
            }
        }
    }
}
