package fr.channelfinder.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Stadium
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Collapsed rail width; screens inside the drawer start their content just after it. */
private val RailWidth = 80.dp

/** Extra start padding for drawer screens, so their usual side margin begins clear of the rail. */
val DrawerContentInset = RailWidth - Spacing.Side + 16.dp

private data class DrawerEntry(val dest: NavDest, val label: String, val icon: ImageVector? = null, val emoji: String? = null)

private val topEntry = DrawerEntry(NavDest.SEARCH, "Rechercher", Icons.Rounded.Search)

// Guideline: 5–6 main destinations, by importance, each with an icon.
private val mainEntries = listOf(
    DrawerEntry(NavDest.HOME, "Accueil", Icons.Rounded.Home),
    DrawerEntry(NavDest.FRANCE, "France", emoji = "🇫🇷"),
    DrawerEntry(NavDest.PORTUGUESE, "Português", emoji = "🇵🇹"),
    DrawerEntry(NavDest.SPORTS, "Sports", Icons.Rounded.SportsSoccer),
    DrawerEntry(NavDest.EVENTS, "Événements", Icons.Rounded.Stadium),
    DrawerEntry(NavDest.FAVORITES, "Favoris", Icons.Rounded.Star),
    DrawerEntry(NavDest.MOVIES, "Films", Icons.Rounded.Movie),
    DrawerEntry(NavDest.SERIES, "Séries", Icons.Rounded.VideoLibrary)
)

// Guideline: 1–3 actions at the bottom.
private val bottomEntries = listOf(
    DrawerEntry(NavDest.GUIDE, "Guide TV", Icons.Rounded.Schedule),
    DrawerEntry(NavDest.CATEGORIES, "Catégories", Icons.Rounded.GridView),
    DrawerEntry(NavDest.SETTINGS, "Réglages", Icons.Rounded.Settings)
)

/**
 * Modal navigation drawer (Android TV guideline): a collapsed icon rail is always visible; focusing it
 * expands the menu over the content with a scrim, and OK opens the destination.
 */
@Composable
fun AppDrawer(state: AppState, content: @Composable () -> Unit) {
    val requesters = remember { NavDest.entries.associateWith { FocusRequester() } }
    var drawerHasFocus by remember { mutableStateOf(false) }
    var contentHasFocus by remember { mutableStateOf(false) }
    val active = state.activeDest
    val contentFocus = remember { FocusRequester() }
    var destinationSelected by remember { mutableStateOf(false) }

    LaunchedEffect(active, destinationSelected) {
        if (destinationSelected) {
            delay(16)
            focusWhenReady(contentFocus)
            destinationSelected = false
        }
    }

    // A screen opened from its content (a Home lane card) can have nothing focusable while it loads;
    // without this hold, focus would fall into the drawer and expand it until the screen takes focus.
    var holdDrawer by remember { mutableStateOf(false) }
    LaunchedEffect(state.page) {
        if (drawerHasFocus) return@LaunchedEffect
        holdDrawer = true
        withTimeoutOrNull(3_000) { snapshotFlow { contentHasFocus }.dropWhile { it }.first { it } }
        holdDrawer = false
    }

    LaunchedEffect(state.drawerFocusRequest) {
        if (state.drawerFocusRequest > 0) {
            holdDrawer = false
            delay(16)
            runCatching { requesters.getValue(active).requestFocus() }
        }
    }
    // Back in the drawer returns to Accueil; on Accueil, the system handles it and leaves the app.
    BackHandler(enabled = drawerHasFocus && (active != NavDest.HOME || state.backStack.size > 1)) {
        state.openDestination(NavDest.HOME)
    }
    // Back on a screen opened from the drawer opens the drawer on that screen's item (left navigation
    // guideline). Screens with their own Back step (search typing, scrolled list) are composed later and win.
    BackHandler(enabled = contentHasFocus && state.isDrawerRoot) { state.focusDrawer() }

    ModalNavigationDrawer(
        drawerContent = { _ ->
            Column(
                Modifier.fillMaxHeight()
                    .background(Palette.Ink)
                    .padding(horizontal = 12.dp, vertical = Spacing.Top)
                    .verticalScroll(rememberScrollState())
                    .onFocusChanged { drawerHasFocus = it.hasFocus }
                    // Entering the drawer always lands on the current destination.
                    .focusProperties { onEnter = { runCatching { requesters.getValue(active).requestFocus() } } }
                    .focusGroup()
            ) {
                fun select(dest: NavDest) {
                    // Choosing the current screen just hands focus back to it.
                    if (dest == active && state.isDrawerRoot || dest == NavDest.HOME && state.backStack.size == 1) {
                        destinationSelected = true
                    } else {
                        state.openDestination(dest)
                    }
                }
                DrawerItem(topEntry, active, requesters, ::select, holdDrawer)
                Spacer(Modifier.height(8.dp))
                mainEntries.forEach { entry ->
                    DrawerItem(entry, active, requesters, ::select, holdDrawer, badge = if (entry.dest == NavDest.EVENTS) state.eventCounts.first else 0)
                }
                Spacer(Modifier.height(8.dp))
                bottomEntries.forEach { entry -> DrawerItem(entry, active, requesters, ::select, holdDrawer) }
            }
        },
        scrimBrush = Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.4f))),
        // Any remote key ends the hold, so Left always reaches the drawer.
        modifier = Modifier.fillMaxSize().onPreviewKeyEvent { holdDrawer = false; false }
    ) {
        Box(
            Modifier.fillMaxSize()
                .padding(start = DrawerContentInset)
                .onFocusChanged { contentHasFocus = it.hasFocus }
                .focusRequester(contentFocus)
                .focusGroup()
        ) { content() }
    }
}

@Composable
private fun NavigationDrawerScope.DrawerItem(
    entry: DrawerEntry,
    active: NavDest,
    requesters: Map<NavDest, FocusRequester>,
    onSelect: (NavDest) -> Unit,
    hold: Boolean,
    badge: Int = 0
) {
    NavigationDrawerItem(
        selected = entry.dest == active,
        onClick = { onSelect(entry.dest) },
        modifier = Modifier.padding(vertical = 1.dp).focusRequester(requesters.getValue(entry.dest)).focusProperties { canFocus = !hold },
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                if (entry.icon != null) Icon(entry.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                else Text(entry.emoji.orEmpty(), fontSize = 20.sp)
                // Collapsed rail: a red dot says something is live; the expanded item shows the count.
                if (badge > 0 && !hasFocus) {
                    Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp).size(9.dp).background(Palette.Live, CircleShape))
                }
            }
        },
        trailingContent = if (badge > 0) {
            { NavigationDrawerItemDefaults.TrailingBadge(badge.toString(), containerColor = Palette.Live, contentColor = Color.White) }
        } else null,
        colors = NavigationDrawerItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Palette.OnSurface,
            inactiveContentColor = Palette.Muted,
            focusedContainerColor = Palette.FocusFill,
            focusedContentColor = Palette.OnFocus,
            pressedContainerColor = Palette.Muted,
            pressedContentColor = Palette.OnFocus,
            selectedContainerColor = Palette.AccentDeep,
            selectedContentColor = Palette.OnSurface,
            focusedSelectedContainerColor = Palette.FocusFill,
            focusedSelectedContentColor = Palette.OnFocus,
            pressedSelectedContainerColor = Palette.Muted,
            pressedSelectedContentColor = Palette.OnFocus
        )
    ) {
        Text(entry.label, style = Type.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
