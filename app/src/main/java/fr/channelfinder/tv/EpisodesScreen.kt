package fr.channelfinder.tv

import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EpisodesScreen(state: AppState, page: Page.Episodes) {
    val account = state.account ?: return
    val session = state.episodeSessions.getOrPut(page.series.id) { LibrarySession(account) }.let {
        if (it.account == account) it else LibrarySession(account).also { fresh -> state.episodeSessions[page.series.id] = fresh }
    }
    var retry by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val cancellation = remember(session, retry) { CancellationSignal() }
    DisposableEffect(cancellation) { onDispose { cancellation.cancel() } }
    LaunchedEffect(session, retry) {
        error = null
        if (session.catalog != null) return@LaunchedEffect
        try {
            val episodes = withContext(Dispatchers.IO) { state.vodRepository.episodes(account, page.series.id, cancellation) }
            session.catalog = LibraryCatalog(emptyList(), episodes)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: android.os.OperationCanceledException) { throw CancellationException()
        } catch (failure: CatalogException) { error = failure.message }
    }
    val rows = session.catalog?.items
    val loadingFocus = remember { FocusRequester() }
    LaunchedEffect(page.series.id) {
        if (rows == null) loadingFocus.requestFocus()
    }
    val history = state.watchHistory.entries(account).filter { it.series?.id == page.series.id }
    val latest = history.maxByOrNull { it.updatedAt }
    val next = rows?.let { all -> latest?.let { last -> all.dropWhile { it.id != last.item.id }.drop(1).firstOrNull() } }
    val suggested = if (latest?.completed == false) rows?.firstOrNull { it.id == latest.item.id } else next ?: rows?.firstOrNull()
    val seasonCount = rows?.map { it.season }?.distinct()?.size ?: 0
    Column(Modifier.fillMaxSize().background(Cinema.Background).padding(horizontal = Spacing.Side, vertical = Spacing.Top)
        .focusRequester(loadingFocus).focusable(enabled = rows == null),
        verticalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
        VodHero(page.series, if (rows == null) "Série" else "Série · $seasonCount saison${if (seasonCount == 1) "" else "s"}",
            Modifier.height(if (LocalDensity.current.fontScale > 1.15f) 190.dp else 160.dp), titleLines = 1) {
            if (suggested != null) TvButton(
                if (latest?.completed == false) "Reprendre S${suggested.season} · E${suggested.episode}" else if (next != null) "Épisode suivant" else "Commencer la série",
                { state.navigate(Page.VodPlayer(suggested, LibraryKind.SERIES, page.series)) }, icon = Icons.Rounded.PlayArrow, primary = true, compact = true)
        }
        if (rows == null) LibraryStatus(error ?: "Chargement des épisodes…", error != null) { retry++ }
        else if (rows.isEmpty()) LibraryStatus("Le fournisseur ne propose aucun épisode pour cette série.", true) { session.catalog = null; retry++ }
        else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter), contentPadding = PaddingValues(8.dp)) {
                item { TvChip("Toutes les saisons", session.category == null, { session.category = null }) }
                items(rows.map { it.season }.distinct()) { season ->
                    TvChip("Saison $season", session.category == season.toString(), { session.category = season.toString() })
                }
            }
            val filtered = remember(rows, session.category) { rows.filter { session.category == null || it.season.toString() == session.category } }
            val target = filtered.firstOrNull { it.id == session.focusedId } ?: suggested?.takeIf { it in filtered } ?: filtered.firstOrNull()
            val focus = remember { FocusRequester() }
            LaunchedEffect(session.category) {
                kotlinx.coroutines.delay(50)
                if (target != null) {
                    val index = filtered.indexOf(target)
                    if (session.grid.layoutInfo.visibleItemsInfo.none { it.index == index }) session.grid.scrollToItem(index)
                    focusWhenReady(focus)
                }
            }
            LazyVerticalGrid(GridCells.Fixed(if (LocalDensity.current.fontScale > 1.15f) 2 else 3), Modifier.weight(1f), state = session.grid,
                contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(Cinema.GridGap),
                verticalArrangement = Arrangement.spacedBy(Cinema.GridGap)) {
                items(filtered, key = { it.id }) { episode ->
                    val watch = history.firstOrNull { it.item.id == episode.id }
                    EpisodeCard(episode, watch, Modifier
                        .then(if (episode.id == target?.id) Modifier.focusRequester(focus) else Modifier)
                        .onFocusChanged { if (it.hasFocus) session.focusedId = episode.id },
                        onPlay = { state.navigate(Page.VodPlayer(episode, LibraryKind.SERIES, page.series)) },
                        onRestart = { state.navigate(Page.VodPlayer(episode, LibraryKind.SERIES, page.series, startOver = true)) })
                }
            }
            Text("OK pour regarder · OK long pour recommencer", style = Type.Caption, color = Palette.Faint)
        }
    }
}
