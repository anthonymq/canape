package fr.channelfinder.tv

import android.os.CancellationSignal
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun GuideScreen(state: AppState, page: Page.Guide) {
    val account = state.account ?: return
    val channels = remember(page.playlist, state.channelsById) { page.playlist.mapNotNull(state.channelsById::get) }
    val channel = state.channelsById[page.channelId] ?: return
    val repository = state.guideRepository
    var programmes by remember(channel.id) { mutableStateOf<List<TvProgramme>?>(null) }
    var error by remember(channel.id) { mutableStateOf<String?>(null) }
    var selected by remember(channel.id) { mutableStateOf<TvProgramme?>(null) }
    var retry by remember(channel.id) { mutableIntStateOf(0) }
    val cancellation = remember(channel.id, retry) { CancellationSignal() }
    DisposableEffect(cancellation) { onDispose { cancellation.cancel() } }
    var actionLabel by remember { mutableStateOf("Choisir") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val channelFocus = remember { FocusRequester() }
    val programmeFocus = remember { FocusRequester() }
    val watchFocus = remember { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }
    val channelList = rememberLazyListState(channels.indexOf(channel).coerceAtLeast(0))
    val scheduleList = rememberLazyListState()
    val requesters = remember(channels) { channels.associate { it.id to FocusRequester() } }
    val currentFocus = requesters[channel.id] ?: channelFocus

    LaunchedEffect(Unit) { focusWhenReady(currentFocus); while (true) { delay(30_000); now = System.currentTimeMillis() } }
    LaunchedEffect(channel.id, retry) {
        delay(if (retry == 0) 350 else 0)
        try {
            val rows = withContext(Dispatchers.IO) { repository.load(account, channel.id, cancellation, retry > 0) }
            programmes = rows
            selected = rows.firstOrNull { it.end > System.currentTimeMillis() } ?: rows.lastOrNull()
            scheduleList.scrollToItem(rows.indexOf(selected).coerceAtLeast(0))
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (failure: CatalogException) { error = failure.message }
          catch (_: Exception) { error = "Impossible de charger le guide. Réessayez dans un instant." }
    }
    fun watch() { state.watchFromGuide(channel, channels) }
    fun refresh() { error = null; programmes = null; retry++ }

    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.Side, vertical = Spacing.Top)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Guide TV", style = Type.Display, color = Palette.OnSurface)
                Text("Programmes du fournisseur · horaires de la TV", style = Type.Caption, color = Palette.Muted)
            }
            TvButton("Actualiser", ::refresh, Modifier.focusRequester(refreshFocus)
                .onFocusChanged { if (it.isFocused) actionLabel = "Actualiser" }
                .focusProperties { down = currentFocus }, icon = Icons.Rounded.Refresh)
        }
        Spacer(Modifier.height(Spacing.Gutter))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
            LazyColumn(Modifier.width(200.dp).fillMaxHeight().onFocusChanged { if (it.hasFocus) actionLabel = "Choisir" }, state = channelList,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = Spacing.ListPadding), verticalArrangement = Arrangement.spacedBy(Spacing.ListGap)) {
                items(channels, key = Channel::id) { item ->
                    val active = item.id == channel.id
                    FocusSurface(onClick = { if (active) runCatching { if (programmes?.isNotEmpty() == true || error != null) programmeFocus.requestFocus() else watchFocus.requestFocus() }
                        else state.replaceTop(page.copy(channelId = item.id)) },
                        modifier = Modifier.fillMaxWidth().focusRequester(requesters.getValue(item.id))
                            .focusProperties { right = if (active && (programmes?.isNotEmpty() == true || error != null)) programmeFocus else watchFocus }
                            .onFocusChanged { if (it.isFocused && !active) state.replaceTop(page.copy(channelId = item.id)) },
                        container = if (active) Palette.AccentDeep else Palette.Surface1, focusedScale = 1.02f) {
                        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChannelLogo(item, Modifier.size(42.dp, 32.dp), initialsSize = 14)
                            Column(Modifier.weight(1f)) {
                                Text(item.displayName, style = Type.Label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text((listOfNotNull(item.countryTag) + item.badges + item.categoryDisplay).joinToString(" · "),
                                    style = Type.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(channel.displayName, style = Type.Title, color = Palette.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                val rows = programmes
                if (rows == null || rows.isEmpty()) {
                    Column(Modifier.fillMaxWidth().weight(1f).padding(8.dp), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        if (error != null) {
                            Text("Guide indisponible", style = Type.TitleSmall, color = Palette.OnSurface)
                            Text(error.orEmpty(), style = Type.Caption, color = Palette.Muted)
                            Spacer(Modifier.height(14.dp))
                            TvButton("Réessayer", ::refresh, Modifier.focusRequester(programmeFocus)
                                .onFocusChanged { if (it.isFocused) actionLabel = "Réessayer" }
                                .focusProperties { left = currentFocus; right = watchFocus })
                        } else if (rows == null) {
                            Spinner(Modifier.size(30.dp))
                            Spacer(Modifier.height(14.dp))
                            Text("Chargement du guide…", style = Type.Body, color = Palette.Muted)
                        } else {
                            Text("Aucun programme fourni", style = Type.TitleSmall, color = Palette.OnSurface)
                            Text("La chaîne reste disponible en direct.", style = Type.Caption, color = Palette.Muted)
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f).onFocusChanged { if (it.hasFocus) actionLabel = "Direct" }, state = scheduleList,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = Spacing.ListPadding), verticalArrangement = Arrangement.spacedBy(Spacing.ListGap)) {
                        items(rows, key = TvProgramme::key) { programme ->
                            val active = selected?.key == programme.key
                            FocusSurface(onClick = ::watch,
                                modifier = Modifier.fillMaxWidth().then(if (active) Modifier.focusRequester(programmeFocus) else Modifier)
                                    .focusProperties { left = currentFocus; right = watchFocus }
                                    .onFocusChanged { if (it.isFocused) selected = programme },
                                container = if (active) Palette.AccentDeep else Palette.Surface1, focusedScale = 1.02f) {
                                Column(Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text("${programme.dayLabel()} · ${programme.timeLabel()}", style = Type.Badge)
                                    Text(programme.title, style = Type.Label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (programme.isLive(now)) Text("EN COURS", style = Type.Badge)
                                }
                            }
                        }
                    }
                }
            }
            GuideDetails(channel, selected, now, ::watch, watchFocus, currentFocus, programmeFocus, refreshFocus,
                programmes?.isNotEmpty() == true, onActionFocus = { actionLabel = it })
        }
        Spacer(Modifier.height(10.dp))
        HintBar((if (actionLabel == "Lire") "↑ ↓" else "OK") to actionLabel, "◀ ▶" to "Chaînes / programmes", "RETOUR" to "Revenir")
    }
}
