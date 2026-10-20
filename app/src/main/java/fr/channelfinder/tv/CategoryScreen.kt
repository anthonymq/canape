package fr.channelfinder.tv

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

private sealed interface CategoryItem {
    val key: String
    data class Header(val title: String) : CategoryItem { override val key = "h:$title" }
    data class Row(val category: ChannelCategory) : CategoryItem { override val key = category.id }
}

private val sectionOrder = listOf(BrowseLane.FRANCE.label, BrowseLane.PORTUGUESE.label, BrowseLane.SPORTS.label, BrowseLane.EVENTS.label, "Autres")

@Composable
fun CategoryPickerScreen(state: AppState, page: Page.CategoryPicker) {
    val number = remember { NumberFormat.getIntegerInstance(Locale.FRANCE) }
    // Sections are computed when the list opens or the query changes, so pinning does not move the focused row.
    val items = remember(state.categories, page.query) {
        val tokens = ChannelText.tokens(page.query)
        val matches = state.categories.filter { category -> tokens.all { it in category.searchKey } }
        val pinned = state.pinnedCategories.mapNotNull { id -> matches.firstOrNull { it.id == id } }
        val others = matches.filter { it.id !in state.pinnedCategories }.sortedBy { ChannelText.normalize(it.displayName) }
        buildList {
            if (pinned.isNotEmpty()) {
                add(CategoryItem.Header("Épinglées sur l’accueil"))
                pinned.forEach { add(CategoryItem.Row(it)) }
            }
            others.groupBy { it.lane }.toSortedMap(compareBy { sectionOrder.indexOf(it).let { i -> if (i < 0) 99 else i } })
                .forEach { (lane, categories) ->
                    add(CategoryItem.Header(if (lane == "Autres") "Autres catégories" else lane))
                    categories.forEach { add(CategoryItem.Row(it)) }
                }
        }
    }
    val rows = items.filterIsInstance<CategoryItem.Row>()
    val target = rows.firstOrNull { it.category.id == state.categoryFocusId }?.category ?: rows.firstOrNull()?.category
    val targetFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val index = items.indexOfFirst { it is CategoryItem.Row && it.category.id == target?.id }
        delay(16)
        if (index >= 0 && state.categoryListState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            state.categoryListState.scrollToItem((index - 2).coerceAtLeast(0))
        }
        focusWhenReady(targetFocus.takeIf { target != null }, searchFocus)
    }
    var lastQuery by remember { mutableStateOf(page.query) }
    LaunchedEffect(page.query) {
        if (page.query != lastQuery) {
            lastQuery = page.query
            state.categoryFocusId = null
            state.categoryListState.scrollToItem(0)
        }
    }

    Column(Modifier.fillMaxSize().padding(start = Spacing.Side, end = Spacing.Side, top = Spacing.ContentTop, bottom = Spacing.Top)) {
        ScreenHeader(
            "${number.format(rows.size)} catégories · ${state.pinnedCategories.size} épinglée${if (state.pinnedCategories.size > 1) "s" else ""}",
            "Catégories"
        ) {
            TvTextField(
                value = page.query,
                onValueChange = { state.replaceTop(page.copy(query = it)) },
                placeholder = "Rechercher une catégorie",
                editing = editing,
                onEditingChange = { editing = it },
                icon = Icons.Rounded.Search,
                imeAction = ImeAction.Search,
                onImeAction = { runCatching { targetFocus.requestFocus() } },
                outerFocusRequester = searchFocus,
                modifier = Modifier.width(380.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Rounded.SearchOff, "Aucune catégorie trouvée", "Essayez un autre mot, par exemple « sport » ou « PT ».") {
                    TvButton("Effacer la recherche", { state.replaceTop(page.copy(query = "")) })
                }
            }
        } else {
            LazyColumn(
                state = state.categoryListState,
                modifier = Modifier.fillMaxWidth().weight(1f).focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(Spacing.ListGap),
                contentPadding = PaddingValues(vertical = Spacing.ListPadding, horizontal = 10.dp)
            ) {
                items(items, key = { it.key }) { item ->
                    when (item) {
                        is CategoryItem.Header -> Text(
                            item.title.uppercase(), style = Type.Eyebrow, color = Palette.Accent,
                            modifier = Modifier.padding(top = 10.dp, bottom = 0.dp, start = 4.dp)
                        )
                        is CategoryItem.Row -> CategoryRow(
                            category = item.category,
                            count = number.format(state.categoryCounts[item.category.id] ?: 0),
                            pinned = item.category.id in state.pinnedCategories,
                            modifier = if (item.category.id == target?.id) Modifier.focusRequester(targetFocus) else Modifier,
                            onFocus = { state.categoryFocusId = item.category.id },
                            onOpen = { state.navigate(Page.Browse(BrowseLane.ALL, categoryId = item.category.id)) },
                            onTogglePin = { state.togglePin(item.category) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: ChannelCategory,
    count: String,
    pinned: Boolean,
    modifier: Modifier,
    onFocus: () -> Unit,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        FocusSurface(
            onClick = onOpen,
            onLongClick = onTogglePin,
            modifier = modifier.weight(1f).heightIn(min = 46.dp).onFocusChanged { if (it.isFocused) onFocus() },
            focusedScale = 1.02f
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                category.countryTag?.let { Badge(it) }
                Text(category.displayName, style = Type.Label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("$count chaînes", style = Type.Caption, color = secondaryColor())
            }
        }
        PinButton(pinned, onTogglePin)
    }
}

@Composable
private fun PinButton(pinned: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    FocusSurface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.heightIn(min = 42.dp).widthIn(min = 42.dp)
            .semantics { contentDescription = if (pinned) "Désépingler la catégorie" else "Épingler la catégorie" },
        shape = CircleShape,
        container = if (pinned) Palette.AccentDeep else Palette.Ink,
        outline = if (pinned) Palette.Accent else Palette.Outline,
        focusedScale = 1.06f
    ) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = if (pinned || focused) 16.dp else 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                contentDescription = null,
                tint = if (pinned && !focused) Palette.Accent else androidx.tv.material3.LocalContentColor.current,
                modifier = Modifier.size(20.dp)
            )
            // Icon only at rest; the label appears on focus or once pinned, so the list stays light.
            if (pinned || focused) Text(if (pinned) "Épinglée" else "Épingler", style = Type.Label)
        }
    }
}
