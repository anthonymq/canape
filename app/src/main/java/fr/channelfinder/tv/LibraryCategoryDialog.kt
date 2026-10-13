package fr.channelfinder.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text

@Composable
fun LibraryCategoryDialog(catalog: LibraryCatalog, session: LibrarySession, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(560.dp).fillMaxHeight(0.85f).background(Cinema.Background, RoundedCornerShape(Cinema.CardRadius))
            .padding(Spacing.Top), verticalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
            Text("Choisir une catégorie", style = Type.Headline, color = Palette.OnSurface)
            TvTextField(query, { query = it }, "Rechercher une catégorie", editing, { editing = it }, modifier = Modifier.fillMaxWidth())
            val rows = remember(query, catalog) { val tokens = ChannelText.tokens(query); catalog.categories.filter { category -> tokens.all { it in category.searchKey } } }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.ListGap), contentPadding = PaddingValues(Spacing.ListPadding)) {
                item {
                    CategoryChoice("Toutes les catégories", session.category == null, Modifier.focusRequester(focus)) { session.category = null; onClose() }
                }
                items(rows, key = { it.id }) { category ->
                    CategoryChoice(category.name, session.category == category.id) { session.category = category.id; onClose() }
                }
                if (rows.isEmpty()) item { Text("Aucune catégorie trouvée", style = Type.Body, color = Palette.Muted) }
            }
        }
        LaunchedEffect(Unit) { focusWhenReady(focus) }
    }
}

@Composable
private fun CategoryChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusSurface(onClick, modifier.fillMaxWidth(), container = if (selected) Palette.AccentDeep else Cinema.Panel, focusedScale = 1f) {
        Text((if (selected) "✓ " else "") + label, style = Type.Label, maxLines = 2, modifier = Modifier.padding(Spacing.Row))
    }
}
