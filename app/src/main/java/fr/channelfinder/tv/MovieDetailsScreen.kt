package fr.channelfinder.tv

import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MovieDetailsScreen(state: AppState, page: Page.MovieDetails) {
    val account = state.account ?: return
    var item by remember(page.item) { mutableStateOf(page.item) }
    var detailsError by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val cancellation = remember(page.item, retry) { CancellationSignal() }
    DisposableEffect(cancellation) { onDispose { cancellation.cancel() } }
    LaunchedEffect(page.item, retry) {
        detailsError = false
        try {
            val key = "movie:${page.item.id}"
            val cached = withContext(Dispatchers.IO) { state.libraryCache.load(account, key) }
            cached?.catalog?.items?.firstOrNull()?.let { item = it }
            if (retry == 0 && cached?.isFresh() == true) return@LaunchedEffect
            val fresh = withContext(Dispatchers.IO) { state.vodRepository.movieDetails(account, page.item, cancellation) }
            item = fresh.copy(overview = fresh.overview.ifBlank { item.overview }, year = fresh.year.ifBlank { item.year },
                rating = fresh.rating.ifBlank { item.rating }, backdrop = fresh.backdrop.ifBlank { item.backdrop })
            withContext(Dispatchers.IO) { state.libraryCache.save(account, key, LibraryCatalog(emptyList(), listOf(item))) }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: android.os.OperationCanceledException) { throw CancellationException()
        } catch (_: CatalogException) { detailsError = item.overview.isBlank() }
    }
    val watch = state.watchHistory.find(account, item, LibraryKind.MOVIES)
    val resumable = watch != null && !watch.completed && watch.positionMs >= 5_000
    val playFocus = remember { FocusRequester() }
    var pendingRemoval by remember { mutableStateOf<VodWatch?>(null) }
    LaunchedEffect(Unit) { focusWhenReady(playFocus) }
    Column(Modifier.fillMaxSize().background(Cinema.Background).padding(start = Spacing.Side, end = Spacing.Side, top = Spacing.ContentTop, bottom = Spacing.Top)
        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.Row)) {
        VodHero(item, "Film", Modifier.heightIn(min = 168.dp)) {
            if (watch != null) VodProgress(watch, Modifier.width(240.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter), verticalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
            TvButton(if (resumable) "Reprendre à ${watchTime(watch.positionMs)}" else "Regarder",
                { state.navigate(Page.VodPlayer(item, LibraryKind.MOVIES)) }, Modifier.focusRequester(playFocus),
                icon = Icons.Rounded.PlayArrow, primary = true)
            if (watch != null && watch.positionMs > 0) TvButton("Recommencer",
                { state.navigate(Page.VodPlayer(item, LibraryKind.MOVIES, startOver = true)) }, icon = Icons.Rounded.Replay)
            if (watch != null && resumable) TvButton("Retirer de Reprendre", { pendingRemoval = watch })
            SaveTitleButton(state, item, LibraryKind.MOVIES)
        }
        var expanded by remember { mutableStateOf(false) }
        if (item.overview.isNotBlank()) FocusSurface({ expanded = !expanded }, Modifier.fillMaxWidth(), container = Cinema.Panel, focusedScale = 1f) {
            Column(Modifier.padding(Spacing.Row), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Synopsis", style = Type.Title)
                Text(item.overview, style = Type.Body, maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(if (expanded) "OK pour réduire" else "OK pour lire la suite", style = Type.Caption)
            }
        }
        if (detailsError) {
            Text("Les détails ne sont pas disponibles. Vous pouvez regarder le film.", style = Type.Caption, color = Palette.Muted)
            TvButton("Actualiser les détails", { retry++ })
        }
    }
    pendingRemoval?.let { entry ->
        VodRemovalDialog(state, entry, onDismiss = { pendingRemoval = null }, onConfirm = {
            pendingRemoval = null
            state.watchHistory.remove(account, entry)
        })
    }
}
