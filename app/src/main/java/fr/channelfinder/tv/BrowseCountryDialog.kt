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
internal fun BrowseCountryDialog(countries: List<String>, selected: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val initialFocus = remember { FocusRequester() }
    val rows = remember(countries, query) {
        val tokens = ChannelText.tokens(query)
        countries.filter { code -> tokens.all { it in ChannelText.normalize("$code ${ChannelText.countryName(code)}") } }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(520.dp).fillMaxHeight(0.85f)
            .background(Palette.Surface1, RoundedCornerShape(16.dp)).padding(Spacing.Top),
            verticalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
            Text("Langues / pays", style = Type.Headline, color = Palette.OnSurface)
            TvTextField(query, { query = it }, "Rechercher un pays", editing, { editing = it }, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.ListGap),
                contentPadding = PaddingValues(Spacing.ListPadding)) {
                item {
                    TvChip("Tous les pays", selected == null, { onSelect(null); onDismiss() },
                        Modifier.fillMaxWidth().focusRequester(initialFocus))
                }
                items(rows, key = { it }) { code ->
                    TvChip(ChannelText.countryLabel(code), selected == code, { onSelect(code); onDismiss() }, Modifier.fillMaxWidth())
                }
                if (rows.isEmpty()) item { Text("Aucun pays trouvé", style = Type.Body, color = Palette.Muted) }
            }
        }
        LaunchedEffect(Unit) { focusWhenReady(initialFocus) }
    }
}
