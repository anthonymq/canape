package fr.channelfinder.tv

import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
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

class LibrarySession(val account: XcAccount?) {
    var catalog by mutableStateOf<LibraryCatalog?>(null)
    var category by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var focusedId: String? = null
    var resumeFocusId: String? = null
    var highlighted by mutableStateOf<LibraryItem?>(null)
    val list = LazyListState()
    val grid = LazyGridState()
}

@Composable
fun LibraryScreen(state: AppState, page: Page.Library) {
    val session = state.librarySessions.getOrPut(page.kind) { LibrarySession(state.account) }.let {
        if (it.account == state.account) it else LibrarySession(state.account).also { fresh -> state.librarySessions[page.kind] = fresh }
    }
    var retry by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var categoryOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val cancellation = remember(session, retry) { CancellationSignal() }
    DisposableEffect(cancellation) { onDispose { cancellation.cancel() } }
    LaunchedEffect(session, retry) {
        if (session.catalog != null) return@LaunchedEffect
        error = null
        val account = state.account ?: return@LaunchedEffect
        try {
            session.catalog = withContext(Dispatchers.IO) { state.vodRepository.load(account, page.kind, cancellation) }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: android.os.OperationCanceledException) { throw CancellationException()
        } catch (failure: CatalogException) { error = failure.message }
    }
    val catalog = session.catalog
    Column(Modifier.fillMaxSize().background(Cinema.Background).padding(horizontal = Spacing.Side, vertical = Spacing.Top),
        verticalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(page.kind.title, style = Type.Headline, modifier = Modifier.weight(1f), color = Palette.OnSurface)
            TvTextField(session.query, { session.query = it }, "Rechercher un titre", editing, { editing = it },
                icon = Icons.Rounded.Search, modifier = Modifier.width(320.dp))
            TvButton("Catégories", { categoryOpen = true }, icon = Icons.Rounded.GridView, compact = true, enabled = catalog != null)
        }
        if (catalog == null) {
            if (error != null) LibraryStatus(error.orEmpty(), true) { retry++ } else LibrarySkeleton()
        } else {
            LibraryGrid(state, session, page.kind)
            if (categoryOpen) LibraryCategoryDialog(catalog, session, onClose = { categoryOpen = false })
        }
    }
}

@Composable
private fun LibraryGrid(state: AppState, session: LibrarySession, kind: LibraryKind) {
    val catalog = session.catalog ?: return
    val account = state.account ?: return
    val history = state.watchHistory.entries(account)
    val progress = remember(history, kind) { history.filter { it.kind == kind }.associateBy { it.item.id } }
    val resume = remember(history, kind) { continueWatching(history, kind) }
    val rows = remember(catalog, session.query, session.category) {
        val tokens = ChannelText.tokens(session.query)
        catalog.items.filter { item -> (session.category == null || item.categoryId == session.category) && tokens.all { it in item.searchKey } }
    }
    val focus = remember { FocusRequester() }
    val searchActive = session.query.isNotBlank()
    val showResume = resume.isNotEmpty() && !searchActive && session.category == null
    val restoreResume = showResume && (session.resumeFocusId != null || session.focusedId == null)
    val target = rows.firstOrNull { it.id == session.focusedId } ?: rows.firstOrNull()
    val category = catalog.categories.firstOrNull { it.id == session.category }?.name
        ?: if (kind == LibraryKind.MOVIES) "Tous les films" else "Toutes les séries"
    val columns = if (LocalDensity.current.fontScale > 1.15f) 5 else 6
    LaunchedEffect(session.category, resume.map { it.key }) {
        kotlinx.coroutines.delay(50)
        if (restoreResume) {
            session.grid.scrollToItem(0)
            focusWhenReady(focus)
        } else if (target != null) {
            val offset = 1 + if (showResume) 1 else 0
            val index = rows.indexOf(target) + offset
            if (session.grid.layoutInfo.visibleItemsInfo.none { it.index == index }) session.grid.scrollToItem(index)
            focusWhenReady(focus)
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
        VodHero(session.highlighted ?: target, if (kind == LibraryKind.MOVIES) "À découvrir · Films" else "À découvrir · Séries",
            Modifier.height(if (LocalDensity.current.fontScale > 1.15f) 144.dp else Cinema.HeroHeight), titleLines = 1)
    LazyVerticalGrid(GridCells.Fixed(columns), Modifier.weight(1f), state = session.grid,
        horizontalArrangement = Arrangement.spacedBy(Cinema.GridGap), verticalArrangement = Arrangement.spacedBy(Cinema.GridGap),
        contentPadding = PaddingValues(8.dp)) {
        if (showResume) item(span = { GridItemSpan(maxLineSpan) }, key = "resume") { VodContinueRow(state, session, resume, if (restoreResume) focus else null) }
        item(span = { GridItemSpan(maxLineSpan) }, key = "heading") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (searchActive) "Résultats pour « ${session.query} »" else category, style = Type.Title, color = Palette.OnSurface,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${rows.size} titre${if (rows.size == 1) "" else "s"}", style = Type.Caption, color = Palette.Muted)
            }
        }
        if (rows.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
                LibraryStatus("Aucun titre dans cette sélection.", false) {}
                TvButton("Afficher tous les titres", { session.query = ""; session.category = null })
            }
        }
        items(rows, key = { "poster:${it.id}" }) { item ->
            VodPoster(item, progress[item.id], Modifier
                .then(if (!restoreResume && item.id == target?.id) Modifier.focusRequester(focus) else Modifier)
                .onFocusChanged { if (it.hasFocus) { session.focusedId = item.id; session.resumeFocusId = null; session.highlighted = item } }) {
                state.navigate(if (kind == LibraryKind.MOVIES) Page.MovieDetails(item) else Page.Episodes(item))
            }
        }
    }
    }
}

@Composable
private fun LibrarySkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Cinema.GridGap)) {
        Text("Préparation de votre bibliothèque…", style = Type.Body, color = Palette.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(Cinema.GridGap)) {
            repeat(6) { Box(Modifier.weight(1f).aspectRatio(Cinema.PosterRatio).background(Cinema.Panel)) }
        }
    }
}

@Composable
fun LibraryStatus(message: String, retry: Boolean, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
        Text(message, style = Type.Body, color = Palette.Muted)
        if (retry) TvButton("Réessayer", onRetry)
    }
}
