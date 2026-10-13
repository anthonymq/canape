package fr.channelfinder.tv

import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibrarySort(val label: String) {
    PROVIDER("Ordre du fournisseur"), TITLE("Titre A–Z"), YEAR("Année décroissante")
}

internal fun libraryRows(items: List<LibraryItem>, query: String, category: String?, sort: LibrarySort,
    checkActive: () -> Unit): List<LibraryItem> {
    val tokens = ChannelText.tokens(query)
    val rows = items.filter { item ->
        checkActive()
        (category == null || item.categoryId == category) && tokens.all { it in item.searchKey }
    }
    if (sort == LibrarySort.PROVIDER) return rows
    return rows.sortedWith { left, right ->
        checkActive()
        when (sort) {
            LibrarySort.TITLE -> left.sortKey.compareTo(right.sortKey)
            LibrarySort.YEAR -> (right.presentation.year ?: 0).compareTo(left.presentation.year ?: 0)
            LibrarySort.PROVIDER -> 0
        }
    }
}

internal fun libraryScrollDistance(offset: Float, size: Float, containerSize: Float, clearance: Float): Float = when {
    size > containerSize - 2 * clearance -> offset - clearance
    offset < clearance -> offset - clearance
    offset + size > containerSize - clearance -> offset + size - containerSize + clearance
    else -> 0f
}

class LibrarySession(val account: XcAccount?) {
    var catalog by mutableStateOf<LibraryCatalog?>(null)
    var category by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var sort by mutableStateOf(LibrarySort.PROVIDER)
        private set
    var focusedId: String? = null
    var resumeFocusId: String? = null
    var highlighted by mutableStateOf<LibraryItem?>(null)
    val list = LazyListState()
    var grid by mutableStateOf(LazyGridState())
        private set

    fun selectSort(value: LibrarySort) {
        if (sort == value) return
        sort = value
        resetPosition()
    }

    fun selectCategory(id: String?) {
        if (category == id && query.isBlank()) return
        category = id
        query = ""
        resetPosition()
    }

    private fun resetPosition() {
        focusedId = null
        resumeFocusId = null
        highlighted = null
        grid = LazyGridState()
    }

    fun showsContinueWatching(entries: List<VodWatch>): Boolean =
        entries.isNotEmpty() && query.isBlank()
}

@Composable
fun LibraryScreen(state: AppState, page: Page.Library) {
    val session = state.librarySessions.getOrPut(page.kind) { LibrarySession(state.account) }.let {
        if (it.account == state.account) it else LibrarySession(state.account).also { fresh -> state.librarySessions[page.kind] = fresh }
    }
    var retry by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var categoryOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var enterContent by remember { mutableStateOf<(() -> Unit)?>(null) }
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
    val headerFocus = remember { FocusRequester() }
    LaunchedEffect(catalog) {
        if (catalog == null) focusWhenReady(headerFocus)
    }
    LaunchedEffect(catalog) {
        if (catalog != null) withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            catalog.items.forEach { item -> context.ensureActive(); item.searchKey }
        }
    }
    Column(Modifier.fillMaxSize().background(Cinema.Background).padding(horizontal = Spacing.Side, vertical = Spacing.Top),
        verticalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
        Row(Modifier.onPreviewKeyEvent { event ->
            if (!editing && event.key == Key.DirectionDown && enterContent != null) {
                if (event.type == KeyEventType.KeyDown) enterContent?.invoke()
                true
            } else false
        }, horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(page.kind.title, style = Type.Headline, color = Palette.OnSurface)
            TvTextField(session.query, { session.query = it }, "Rechercher un titre", editing, { editing = it },
                icon = Icons.Rounded.Search, modifier = Modifier.weight(1f).focusRequester(headerFocus))
            TvButton("Catégories", { categoryOpen = true }, icon = Icons.Rounded.GridView, compact = true, enabled = catalog != null)
            TvButton("Trier", { sortOpen = true }, compact = true, enabled = catalog != null)
        }
        if (catalog == null) {
            if (error != null) LibraryStatus(error.orEmpty(), true) { retry++ } else LibrarySkeleton()
        } else {
            LibraryGrid(state, session, page.kind, editing, headerFocus) { enterContent = it }
            if (categoryOpen) LibraryCategoryDialog(catalog, session, onClose = { categoryOpen = false })
            if (sortOpen) LibrarySortDialog(session) { sortOpen = false }
        }
    }
}

@Composable
private fun LibrarySortDialog(session: LibrarySession, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.background(Cinema.Background, RoundedCornerShape(Cinema.CardRadius)).padding(Spacing.Top),
            verticalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
            Text("Trier les titres", style = Type.Headline, color = Palette.OnSurface)
            LibrarySort.entries.forEach { sort ->
                FocusSurface({ session.selectSort(sort); onClose() },
                    Modifier.fillMaxWidth().then(if (sort == session.sort) Modifier.focusRequester(focus) else Modifier),
                    container = if (sort == session.sort) Palette.AccentDeep else Cinema.Panel, focusedScale = 1f) {
                    Text((if (sort == session.sort) "✓ " else "") + sort.label, style = Type.Label, modifier = Modifier.padding(Spacing.Row))
                }
            }
        }
        LaunchedEffect(Unit) { focusWhenReady(focus) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryGrid(state: AppState, session: LibrarySession, kind: LibraryKind, editing: Boolean, headerFocus: FocusRequester,
    onEntryChanged: ((() -> Unit)?) -> Unit) {
    val catalog = session.catalog ?: return
    val account = state.account ?: return
    val history = state.watchHistory.entries(account)
    val progress = remember(history, kind) { history.filter { it.kind == kind }.associateBy { it.item.id } }
    val resume = remember(history, kind) { continueWatching(history, kind) }
    val loadedRows = backgroundCalculation(catalog, session.query, session.category, session.sort) { checkActive ->
        libraryRows(catalog.items, session.query, session.category, session.sort, checkActive)
    }
    val rows = loadedRows.orEmpty()
    val focus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val searchActive = session.query.isNotBlank()
    val showResume = session.showsContinueWatching(resume)
    val restoreResume = showResume && (session.resumeFocusId != null || session.focusedId == null)
    val target = rows.firstOrNull { it.id == session.focusedId } ?: rows.firstOrNull()
    val category = catalog.categories.firstOrNull { it.id == session.category }?.name
        ?: if (kind == LibraryKind.MOVIES) "Tous les films" else "Toutes les séries"
    val columns = if (LocalDensity.current.fontScale > 1.15f) 5 else 6
    DisposableEffect(session.grid, loadedRows, showResume) {
        onEntryChanged(if (loadedRows == null || (rows.isEmpty() && !showResume)) null else {
            {
                scope.launch {
                    session.grid.scrollToItem(0)
                    focusWhenReady(firstFocus)
                }
            }
        })
        onDispose { onEntryChanged(null) }
    }
    val focusInputs = listOf(session.category, session.grid, resume.map { it.key })
    val currentlyEditing by rememberUpdatedState(editing)
    var restoredInputs by remember { mutableStateOf<List<Any?>?>(null) }
    LaunchedEffect(session.category, session.grid, resume.map { it.key }, loadedRows != null) {
        if (loadedRows == null || restoredInputs == focusInputs) return@LaunchedEffect
        if (editing) {
            restoredInputs = focusInputs
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(50)
        if (currentlyEditing) {
            restoredInputs = focusInputs
            return@LaunchedEffect
        }
        if (restoreResume) {
            session.grid.scrollToItem(0)
            focusWhenReady(focus)
        } else if (target != null) {
            val offset = 1 + if (showResume) 1 else 0
            val index = rows.indexOf(target) + offset
            if (session.grid.layoutInfo.visibleItemsInfo.none { it.index == index }) session.grid.scrollToItem(index)
            focusWhenReady(focus)
        } else {
            focusWhenReady(headerFocus)
        }
        restoredInputs = focusInputs
    }
    val clearance = with(LocalDensity.current) { 8.dp.toPx() }
    val bringIntoView = remember(clearance) { object : BringIntoViewSpec {
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
            libraryScrollDistance(offset, size, containerSize, clearance)
    } }
    CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
    LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize(), state = session.grid,
        horizontalArrangement = Arrangement.spacedBy(Cinema.GridGap), verticalArrangement = Arrangement.spacedBy(Cinema.GridGap),
        contentPadding = PaddingValues(8.dp)) {
        if (showResume) item(span = { GridItemSpan(maxLineSpan) }, key = "resume") { VodContinueRow(state, session, resume, if (restoreResume) focus else null, firstFocus) }
        item(span = { GridItemSpan(maxLineSpan) }, key = "heading") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (searchActive) "Résultats pour « ${session.query} »" else category, style = Type.Title, color = Palette.OnSurface,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${rows.size} titre${if (rows.size == 1) "" else "s"}", style = Type.Caption, color = Palette.Muted)
            }
        }
        if (rows.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
                LibraryStatus(if (loadedRows == null) "Recherche…" else "Aucun titre dans cette sélection.", false) {}
                if (loadedRows != null) TvButton("Afficher tous les titres", { session.query = ""; session.category = null })
            }
        }
        items(rows, key = { "poster:${it.id}" }) { item ->
            VodPoster(item, progress[item.id], Modifier
                .then(if (!showResume && item.id == rows.firstOrNull()?.id) Modifier.focusRequester(firstFocus) else Modifier)
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
