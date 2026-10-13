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
            item = withContext(Dispatchers.IO) { state.vodRepository.movieDetails(account, page.item, cancellation) }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: android.os.OperationCanceledException) { throw CancellationException()
        } catch (_: CatalogException) { detailsError = true }
    }
    val watch = state.watchHistory.find(account, item, LibraryKind.MOVIES)
    val resumable = watch != null && !watch.completed && watch.positionMs >= 5_000
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusWhenReady(playFocus) }
    Column(Modifier.fillMaxSize().background(Cinema.Background).padding(horizontal = Spacing.Side, vertical = Spacing.Top)
        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.Row)) {
        VodHero(item, "Film", Modifier.height(240.dp)) {
            if (watch != null) VodProgress(watch, Modifier.width(240.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter), verticalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
            TvButton(if (resumable) "Reprendre à ${watchTime(watch.positionMs)}" else "Regarder",
                { state.navigate(Page.VodPlayer(item, LibraryKind.MOVIES)) }, Modifier.focusRequester(playFocus),
                icon = Icons.Rounded.PlayArrow, primary = true)
            if (watch != null && watch.positionMs > 0) TvButton("Recommencer",
                { state.navigate(Page.VodPlayer(item, LibraryKind.MOVIES, startOver = true)) }, icon = Icons.Rounded.Replay)
            if (watch != null && resumable) TvButton("Retirer de Reprendre", { state.watchHistory.remove(account, watch) })
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
}
