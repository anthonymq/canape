package fr.channelfinder.tv

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AppMark(size: androidx.compose.ui.unit.Dp = 56.dp) {
    Image(
        painter = painterResource(R.mipmap.ic_launcher),
        contentDescription = null,
        modifier = Modifier.size(size)
    )
}

@Composable
fun LoadingScreen(step: LoadStep) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 560.dp)) {
            AppMark(72.dp)
            Spacer(Modifier.height(28.dp))
            Text("Préparation de vos chaînes", style = Type.Display, color = Palette.OnSurface)
            Spacer(Modifier.height(10.dp))
            Text(step.label, style = Type.Body, color = Palette.Muted)
            Spacer(Modifier.height(26.dp))
            IndeterminateBar(Modifier.width(360.dp))
            if (step != LoadStep.OPENING) {
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LoadStep.entries.drop(1).forEach { item ->
                        Box(
                            Modifier.size(10.dp).background(
                                if (item.ordinal <= step.ordinal) Palette.Accent else Palette.Outline, CircleShape
                            )
                        )
                    }
                }
                Spacer(Modifier.height(30.dp))
                Text(
                    "Le premier chargement peut prendre quelques secondes. Ensuite, l’accueil s’ouvre immédiatement.",
                    style = Type.Caption, color = Palette.Faint, textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun IndeterminateBar(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bar")
    val offset by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "offset"
    )
    Box(modifier.height(6.dp).clip(CircleShape).background(Palette.Surface2)) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(0.35f)
                .padding(start = 0.dp)
                .align(Alignment.CenterStart)
                .graphicsOffset(offset)
                .background(Palette.Accent, CircleShape)
        )
    }
}

private fun Modifier.graphicsOffset(progress: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val parentWidth = constraints.maxWidth / 0.35f
        val x = (-placeable.width + (parentWidth + placeable.width) * progress).toInt()
        layout(placeable.width, placeable.height) { placeable.place(x, 0) }
    }
)

@Composable
fun LoadErrorScreen(message: String, onRetry: () -> Unit, onEditAccount: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusWhenReady(retryFocus) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = Icons.Rounded.CloudOff,
            title = "Impossible de charger vos chaînes",
            message = message
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvButton("Réessayer", onRetry, Modifier.focusRequester(retryFocus), icon = Icons.Rounded.Refresh, primary = true)
                TvButton("Modifier le compte", onEditAccount, icon = Icons.Rounded.ManageAccounts)
            }
        }
    }
}

@Composable
fun SetupScreen(state: AppState, page: Page.Setup) {
    var remoteSettings by remember { mutableStateOf(false) }
    if (remoteSettings) RemoteSettingsDialog(state) { remoteSettings = false }
    val existing = state.account
    var server by remember { mutableStateOf(existing?.serverUrl.orEmpty()) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf(existing?.password.orEmpty()) }
    var error by remember { mutableStateOf(page.message) }
    var editingField by remember { mutableStateOf(-1) }
    val fieldFocus = remember { List(3) { FocusRequester() } }
    val connectFocus = remember { FocusRequester() }
    val formTargets = remember { List(4) { BringIntoViewRequester() } }
    var focusedFormItem by remember { mutableIntStateOf(-1) }
    LaunchedEffect(focusedFormItem, editingField, error) {
        if (focusedFormItem >= 0) formTargets[focusedFormItem].bringIntoView()
    }

    fun formFocus(index: Int) = Modifier.bringIntoViewRequester(formTargets[index])
        .onFocusChanged { if (it.hasFocus) focusedFormItem = index }

    LaunchedEffect(Unit) {
        delay(60)
        val firstEmpty = listOf(server, username, password).indexOfFirst(String::isBlank)
        runCatching { if (firstEmpty >= 0) fieldFocus[firstEmpty].requestFocus() else connectFocus.requestFocus() }
    }

    fun submit() {
        val address = server.trim().let { if (it.contains("://")) it else "http://$it" }.trimEnd('/')
        val host = runCatching { Uri.parse(address).host }.getOrNull()
        error = when {
            server.isBlank() -> "Saisissez l’adresse du serveur fournie par votre fournisseur."
            host.isNullOrBlank() -> "Adresse du serveur invalide. Exemple : http://serveur.exemple:8080"
            !address.startsWith("http://") && !address.startsWith("https://") -> "L’adresse doit commencer par http:// ou https://."
            username.isBlank() || password.isBlank() -> "Saisissez votre utilisateur et votre mot de passe."
            else -> ""
        }
        if (error.isBlank()) {
            server = address
            state.connect(XcAccount(address, username.trim(), password)) { message ->
                error = message
                runCatching { connectFocus.requestFocus() }
            }
        }
    }

    Row(
        Modifier.fillMaxSize().imePadding().padding(horizontal = Spacing.Side, vertical = Spacing.Top),
        horizontalArrangement = Arrangement.spacedBy(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(0.85f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center) {
            AppMark()
            Spacer(Modifier.height(24.dp))
            Text("CANAPÉ", style = Type.Eyebrow, color = Palette.Accent)
            Spacer(Modifier.height(8.dp))
            Text(if (existing == null) "Connectez votre service TV" else "Modifier le compte", style = Type.Display, color = Palette.OnSurface)
            Spacer(Modifier.height(12.dp))
            Text(
                "Saisissez les informations données par votre fournisseur. Elles restent chiffrées sur cette TV.",
                style = Type.Body, color = Palette.Muted
            )
            Spacer(Modifier.height(28.dp))
            TvButton("Configurer avec mon téléphone", { remoteSettings = true }, icon = Icons.Rounded.ManageAccounts)
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel("Adresse du serveur")
            TvTextField(
                value = server, onValueChange = { server = it }, placeholder = "http://serveur:port",
                editing = editingField == 0, onEditingChange = { editingField = if (it) 0 else -1 },
                icon = Icons.Rounded.Dns, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next,
                onImeAction = { editingField = 1 }, outerFocusRequester = fieldFocus[0], modifier = Modifier.fillMaxWidth().then(formFocus(0))
            )
            FieldLabel("Utilisateur")
            TvTextField(
                value = username, onValueChange = { username = it }, placeholder = "Nom d’utilisateur",
                editing = editingField == 1, onEditingChange = { editingField = if (it) 1 else -1 },
                icon = Icons.Rounded.Person, imeAction = ImeAction.Next,
                onImeAction = { editingField = 2 }, outerFocusRequester = fieldFocus[1], modifier = Modifier.fillMaxWidth().then(formFocus(1))
            )
            FieldLabel("Mot de passe")
            TvTextField(
                value = password, onValueChange = { password = it }, placeholder = "Mot de passe",
                editing = editingField == 2, onEditingChange = { editingField = if (it) 2 else -1 },
                icon = Icons.Rounded.Key, keyboardType = KeyboardType.Password, password = true, imeAction = ImeAction.Done,
                onImeAction = { runCatching { connectFocus.requestFocus() } }, outerFocusRequester = fieldFocus[2],
                modifier = Modifier.fillMaxWidth().then(formFocus(2))
            )
            Spacer(Modifier.height(4.dp))
            Callout(
                Icons.Rounded.WarningAmber, Palette.Warning, Palette.WarningSurface,
                "Avec une adresse http://, l’utilisateur, le mot de passe et les flux ne sont pas chiffrés pendant le transport."
            )
            if (error.isNotBlank()) Callout(Icons.Rounded.ErrorOutline, Palette.Error, Palette.ErrorSurface, error)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                TvButton(
                    if (state.connecting) "Connexion…" else "Se connecter",
                    onClick = ::submit,
                    modifier = Modifier.focusRequester(connectFocus).then(formFocus(3)),
                    icon = Icons.Rounded.Lock,
                    primary = true,
                    enabled = !state.connecting
                )
                if (state.connecting) {
                    Spinner(Modifier.size(24.dp), color = Palette.Accent, stroke = 3.dp)
                    Text(state.loadStep.label, style = Type.Caption, color = Palette.Muted)
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = Type.Caption, color = Palette.Muted, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(36.dp).background(Palette.Surface2, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
        }
        Text(text, style = Type.Body, color = Palette.OnSurface)
    }
}

@Composable
fun Callout(icon: ImageVector, tone: Color, surface: Color, text: String) {
    Row(
        Modifier.fillMaxWidth().background(surface, RoundedCornerShape(12.dp))
            .border(1.dp, tone.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(22.dp))
        Text(text, style = Type.Caption, color = tone)
    }
}

@Composable
fun SettingsScreen(state: AppState) {
    var remoteSettings by remember { mutableStateOf(false) }
    if (remoteSettings) RemoteSettingsDialog(state) { remoteSettings = false }
    val context = LocalContext.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusWhenReady(firstFocus) }
    val number = remember { NumberFormat.getIntegerInstance(Locale.FRANCE) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val host = state.account?.serverUrl?.let { runCatching { Uri.parse(it).host }.getOrNull() }.orEmpty()
    val updated = describeUpdate(state.lastUpdated)
    val settingsScroll = rememberScrollState()

    Column(Modifier.fillMaxSize().padding(start = Spacing.Side, end = Spacing.Side, top = Spacing.ContentTop, bottom = Spacing.Top)) {
        ScreenHeader("Canapé", "Réglages", trailing = { Clock() })
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.widthIn(max = 720.dp).weight(1f).verticalScroll(settingsScroll).padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsRow(
                icon = Icons.Rounded.SystemUpdate,
                title = "Mise à jour de l’application",
                modifier = Modifier.focusRequester(firstFocus),
                subtitle = when (val update = state.updater.status) {
                    is UpdateStatus.Available -> "Version ${update.release.versionName} disponible"
                    is UpdateStatus.Downloading -> "Téléchargement : ${update.percent} %"
                    is UpdateStatus.Ready -> "Mise à jour prête à installer"
                    else -> "Version $version · vérifier maintenant"
                }
            ) { state.updater.open() }
            SettingsRow(
                icon = Icons.Rounded.Update,
                title = "Vérification automatique : ${if (state.updater.automatic) "activée" else "désactivée"}",
                subtitle = "Au démarrage · téléchargement après votre confirmation"
            ) { state.updater.toggleAutomatic() }
            SettingsRow(
                icon = Icons.Rounded.Refresh,
                title = if (state.refreshing) "Actualisation en cours…" else "Actualiser la liste des chaînes",
                subtitle = "Dernière mise à jour : $updated",
                trailing = { if (state.refreshing) Spinner(Modifier.size(24.dp), color = LocalContentColor.current, stroke = 3.dp) }
            ) { state.refresh(silent = false) }
            SettingsRow(
                icon = Icons.Rounded.ManageAccounts,
                title = "Configurer avec mon téléphone",
                subtitle = "Afficher un QR code · téléphone sur le même Wi-Fi"
            ) { remoteSettings = true }
            SettingsRow(
                icon = Icons.Rounded.ManageAccounts,
                title = "Modifier le compte",
                // The Xtream username is half of the credentials: never show it on a shared screen.
                subtitle = if (host.isBlank()) "Serveur, utilisateur et mot de passe" else "Serveur : $host"
            ) { state.editAccount() }
            val audioLabel = when (val code = state.audioPreference) {
                AUDIO_AUTO -> "Automatique (piste par défaut de la chaîne)"
                else -> java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.FRENCH)
                    .replaceFirstChar { it.titlecase(java.util.Locale.FRENCH) }
            }
            SettingsRow(
                icon = Icons.Rounded.RecordVoiceOver,
                title = "Langue audio préférée : $audioLabel",
                subtitle = "OK pour changer · utilisée quand une chaîne propose plusieurs langues"
            ) { state.cycleAudioPreference() }
            val unavailableCount = state.unavailable.keys.count(state::isUnavailable)
            SettingsRow(
                icon = Icons.Rounded.Replay,
                title = "Réessayer les chaînes indisponibles",
                subtitle = when (unavailableCount) {
                    0 -> "Aucune chaîne marquée indisponible"
                    1 -> "1 chaîne marquée indisponible depuis moins de 12 h"
                    else -> "$unavailableCount chaînes marquées indisponibles depuis moins de 12 h"
                }
            ) { if (unavailableCount > 0) state.clearUnavailable() }
            // Erasing the whole history takes a second press; the row says so, and resets after a few seconds.
            var confirmClear by remember { mutableStateOf(false) }
            LaunchedEffect(confirmClear) { if (confirmClear) { delay(5_000); confirmClear = false } }
            SettingsRow(
                icon = Icons.Rounded.History,
                title = if (confirmClear) "Appuyez encore pour tout effacer" else "Effacer les chaînes récentes",
                subtitle = when {
                    state.recent.isEmpty() -> "L’historique est vide"
                    confirmClear -> "${state.recent.size} chaînes seront retirées de l’historique"
                    else -> "${state.recent.size} chaînes dans l’historique"
                },
                modifier = Modifier.onFocusChanged { if (!it.isFocused) confirmClear = false }
            ) {
                when {
                    state.recent.isEmpty() -> Unit
                    confirmClear -> { state.clearRecent(); confirmClear = false }
                    else -> confirmClear = true
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Canapé $version · ${number.format(state.channels.size)} chaînes · ${number.format(state.categories.size)} catégories",
            style = Type.Caption, color = Palette.Faint
        )
        Spacer(Modifier.height(12.dp))
        val scrollHint = when {
            settingsScroll.canScrollForward -> arrayOf("↓" to "Autres réglages")
            settingsScroll.canScrollBackward -> arrayOf("↑" to "Réglages précédents")
            else -> emptyArray()
        }
        HintBar("OK" to "Choisir", "RETOUR" to "Menu", *scrollHint)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit
) {
    FocusSurface(onClick = onClick, modifier = modifier.fillMaxWidth(), focusedScale = 1.025f) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.TitleSmall)
                Text(subtitle, style = Type.Caption, color = secondaryColor(), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            trailing()
        }
    }
}

private fun describeUpdate(timestamp: Long): String {
    if (timestamp <= 0) return "jamais"
    val minutes = (System.currentTimeMillis() - timestamp) / 60_000
    val time = java.text.SimpleDateFormat("HH:mm", Locale.FRANCE).format(java.util.Date(timestamp))
    return when {
        minutes < 1 -> "à l’instant"
        minutes < 60 -> "il y a $minutes min"
        isToday(timestamp) -> "aujourd’hui à $time"
        else -> "le ${java.text.SimpleDateFormat("d MMMM", Locale.FRANCE).format(java.util.Date(timestamp))} à $time"
    }
}

private fun isToday(timestamp: Long): Boolean {
    val a = java.util.Calendar.getInstance()
    val b = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
}
