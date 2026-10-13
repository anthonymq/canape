package fr.channelfinder.tv

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import fi.iki.elonen.NanoHTTPD
import java.net.NetworkInterface
import java.util.UUID

internal class RemoteSettingsServer(
    private val dispatch: (() -> Unit) -> Unit,
    initialAudio: String = "fr",
    private val action: (Map<String, String>) -> Unit
) : NanoHTTPD(0) {
    @Volatile private var selectedAudio = initialAudio
    private val token = UUID.randomUUID().toString()
    private val expires = System.currentTimeMillis() + 10 * 60_000
    @Volatile private var active = true
    val path = "/$token"
    fun close() { active = false; stop() }

    override fun serve(session: IHTTPSession): Response {
        fun reply(status: Response.Status, body: String, mime: String = "text/html; charset=utf-8") =
            newFixedLengthResponse(status, mime, body).apply {
                addHeader("Cache-Control", "no-store")
                addHeader("Referrer-Policy", "no-referrer")
                addHeader("X-Content-Type-Options", "nosniff")
                addHeader("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'")
            }
        if (!active || System.currentTimeMillis() > expires || session.uri != path)
            return reply(Response.Status.NOT_FOUND, "Lien fermé ou expiré.")
        if (session.method == Method.GET) return reply(Response.Status.OK, page())
        if (session.method != Method.POST) return reply(Response.Status.METHOD_NOT_ALLOWED, "Méthode refusée.")
        val length = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (length !in 1..8192 || session.headers["content-type"]?.startsWith("application/x-www-form-urlencoded") != true)
            return reply(Response.Status.BAD_REQUEST, "Formulaire invalide.")
        return try {
            session.parseBody(mutableMapOf())
            val values = session.parameters.mapValues { it.value.firstOrNull().orEmpty() }
            if (values["action"] !in setOf("account", "audio", "refresh", "unavailable", "recent"))
                return reply(Response.Status.BAD_REQUEST, "Action invalide.")
            if (values["action"] == "account") {
                val address = values["server"].orEmpty().trim().let { if ("://" in it) it else "http://$it" }
                val uri = runCatching { java.net.URI(address) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
                    values["username"].isNullOrBlank() || values["password"].isNullOrBlank())
                    return reply(Response.Status.BAD_REQUEST, page("Vérifiez l’adresse, l’utilisateur et le mot de passe."))
            }
            if (values["action"] == "audio" && values["language"] in setOf("fr", "pt", AUDIO_AUTO))
                selectedAudio = values["language"].orEmpty()
            dispatch { if (active && System.currentTimeMillis() <= expires) action(values) }
            reply(Response.Status.OK, page("Demande envoyée. Vérifiez le résultat sur la TV."))
        } catch (_: Exception) {
            reply(Response.Status.BAD_REQUEST, "Formulaire invalide.")
        }
    }

    private fun page(message: String = "") = """<!doctype html><html lang="fr"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Canapé · Réglages</title>
        <style>body{background:#0a0f18;color:#f2f5fa;font:16px/1.5 system-ui;margin:0;padding:24px}main{max-width:520px;margin:auto}form{background:#151e2c;padding:20px;border-radius:14px;margin:20px 0}label{display:block;margin:12px 0}input,button,select{box-sizing:border-box;width:100%;font:inherit;padding:12px;border-radius:8px;border:1px solid #94a2b6}button{background:#8ab8ff;color:#0a0f18;margin-top:12px;font-weight:600}p{color:#bac5d4}.warning{color:#ffcb80}</style>
        <main><h1>Réglages Canapé</h1><p>Ce lien fonctionne pendant 10 minutes, tant que le QR code reste ouvert sur la TV.</p><p role="status">$message</p>
        <form method="post" action="$path"><h2>Compte TV</h2><input type="hidden" name="action" value="account"><label>Adresse du serveur<input name="server" type="text" inputmode="url" placeholder="http://serveur:port" required maxlength="2048"></label><label>Utilisateur<input name="username" autocomplete="username" required maxlength="256"></label><label>Mot de passe<input name="password" type="password" autocomplete="current-password" required maxlength="1024"></label><p class="warning">Cette connexion locale utilise HTTP : les identifiants ne sont pas chiffrés pendant le transport. Utilisez uniquement un Wi-Fi de confiance. Avec un serveur http://, les flux et identifiants du fournisseur ne sont pas chiffrés non plus.</p><button>Connecter la TV</button></form>
        <form method="post" action="$path"><h2>Langue audio</h2><input type="hidden" name="action" value="audio"><label>Langue préférée<select name="language"><option value="fr" ${if (selectedAudio == "fr") "selected" else ""}>Français</option><option value="pt" ${if (selectedAudio == "pt") "selected" else ""}>Português</option><option value="auto" ${if (selectedAudio == AUDIO_AUTO) "selected" else ""}>Automatique</option></select></label><button>Enregistrer la langue</button></form>
        <form method="post" action="$path"><h2>Entretien</h2><button name="action" value="refresh">Actualiser les chaînes</button><button name="action" value="unavailable">Réessayer les chaînes indisponibles</button><label><input type="checkbox" name="confirm" value="yes" style="width:auto"> Confirmer l’effacement de l’historique</label><button name="action" value="recent">Effacer les chaînes récentes</button></form></main></html>""".trimIndent()
}

@Composable
fun RemoteSettingsDialog(state: AppState, onDismiss: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var url by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        val handler = Handler(Looper.getMainLooper())
        val server = RemoteSettingsServer(dispatch = { handler.post(it) }, initialAudio = state.audioPreference) { values ->
            when (values["action"]) {
                "account" -> {
                    val address = values["server"].orEmpty().trim().let { if ("://" in it) it else "http://$it" }.trimEnd('/')
                    status = "Connexion en cours…"
                    state.connect(XcAccount(address, values["username"].orEmpty().trim(), values["password"].orEmpty())) { status = it }
                }
                "audio" -> if (values["language"] in setOf("fr", "pt", AUDIO_AUTO)) {
                    state.rememberAudioLanguage(values["language"]); status = "Langue audio enregistrée."
                }
                "refresh" -> { state.refresh(silent = false); status = "Actualisation demandée." }
                "unavailable" -> { state.clearUnavailable(); status = "Chaînes indisponibles réinitialisées." }
                "recent" -> if (values["confirm"] == "yes") { state.clearRecent(); status = "Historique effacé." }
                    else status = "Confirmez l’effacement sur le téléphone."
            }
        }
        try {
            val host = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }?.hostAddress
            if (host == null) failure = true else {
                server.start(5_000, false)
                url = "http://$host:${server.listeningPort}${server.path}"
            }
        } catch (_: Exception) { failure = true }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { server.close(); onDismiss() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); server.close() }
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(10 * 60_000); url = null; status = "Lien expiré. Fermez puis rouvrez cet écran." }
    val bitmap = remember(url) { url?.let {
        val matrix = MultiFormatWriter().encode(it, BarcodeFormat.QR_CODE, 480, 480)
        Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888).apply {
            setPixels(IntArray(480 * 480) { index -> if (matrix[index % 480, index / 480]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }, 0, 480, 0, 0, 480, 480)
        }.asImageBitmap()
    } }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.heightIn(max = 480.dp).background(Palette.Surface1, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Configurer depuis votre téléphone", style = Type.Headline)
            Text("Connectez le téléphone au même Wi-Fi que la TV, puis scannez ce QR code. Retour ferme le lien.", style = Type.Body)
            if (bitmap != null) Image(bitmap, "QR code pour les réglages", Modifier.size(200.dp).align(androidx.compose.ui.Alignment.CenterHorizontally))
            if (failure) Text("Connexion locale indisponible. Vérifiez le Wi-Fi de la TV.", color = Palette.Warning, style = Type.Body)
            Text("Lien temporaire · 10 minutes · HTTP non chiffré : utilisez un Wi-Fi de confiance.", color = Palette.Warning, style = Type.Caption)
            if (status.isNotBlank()) Text(status, style = Type.Caption)
        }
    }
}
