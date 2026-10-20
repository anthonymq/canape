package fr.channelfinder.tv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.runtime.Composable

@Composable
fun SaveTitleButton(state: AppState, item: LibraryItem, kind: LibraryKind) {
    val account = state.account ?: return
    val saved = state.watchHistory.savedTitles(account).any { it.item.id == item.id && it.kind == kind }
    TvButton(if (saved) "Retirer de ma liste" else "Ajouter à ma liste", {
        state.watchHistory.toggleSaved(account, item, kind)
        state.notice = Notice(if (saved) "Retiré de votre liste" else "Ajouté à votre liste")
    }, icon = if (saved) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd, compact = true)
}
