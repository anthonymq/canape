package fr.channelfinder.tv

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

@Composable
internal fun AppUpdateDialog(updater: AppUpdater) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    var installMessage by remember { mutableStateOf<String?>(null) }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        installMessage = "Si l’installation a été annulée, choisissez Installer pour réessayer."
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        installMessage = if (Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls())
            "Autorisation accordée. Choisissez Installer pour continuer."
        else "Autorisation non accordée. Autorisez Canapé dans les réglages Android pour continuer."
    }
    val status = updater.status
    var showSpinner by remember(status is UpdateStatus.Checking) { mutableStateOf(false) }
    LaunchedEffect(status is UpdateStatus.Checking) {
        if (status is UpdateStatus.Checking) {
            delay(1_000)
            showSpinner = true
        }
    }
    val focusKey = when (status) {
        UpdateStatus.Idle, UpdateStatus.Checking -> "checking"
        is UpdateStatus.Available -> "available"
        is UpdateStatus.Downloading -> "downloading"
        is UpdateStatus.Ready -> "ready"
        is UpdateStatus.Failed -> "failed"
        UpdateStatus.Current -> "current"
    }
    Dialog(onDismissRequest = updater::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focusManager = LocalFocusManager.current
        val owner = LocalView.current
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 460.dp)
            .background(Palette.Surface1, RoundedCornerShape(UpdateLayout.DialogRadius))
            .verticalScroll(rememberScrollState()).padding(Spacing.Top),
            verticalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
            Text("Mise à jour de Canapé", style = Type.Headline, color = Palette.OnSurface)
            Text("Version installée : ${updater.installedVersion}", style = Type.Caption, color = Palette.Muted)
            when (status) {
                UpdateStatus.Idle, UpdateStatus.Checking -> {
                    Text("Recherche d’une nouvelle version…", style = Type.Body, color = Palette.OnSurface)
                    if (showSpinner) Spinner()
                    TvButton("Fermer", updater::dismiss, Modifier.focusRequester(focus))
                }
                UpdateStatus.Current -> {
                    Text("Canapé est à jour.", style = Type.Body, color = Palette.OnSurface)
                    TvButton("Fermer", updater::dismiss, Modifier.focusRequester(focus))
                }
                is UpdateStatus.Available -> {
                    Text("Version ${status.release.versionName} disponible", style = Type.Title, color = Palette.OnSurface)
                    Text("Téléchargez la mise à jour ici. Votre compte et vos réglages seront conservés.", style = Type.Body, color = Palette.Muted)
                    Text("Android vous demandera de confirmer l’installation.", style = Type.Caption, color = Palette.Muted)
                    Column(verticalArrangement = Arrangement.spacedBy(UpdateLayout.ActionGap)) {
                        TvButton("Télécharger", { updater.download(status.release) })
                        TvButton("Plus tard", updater::dismiss, Modifier.focusRequester(focus))
                    }
                }
                is UpdateStatus.Downloading -> {
                    Text("Téléchargement : ${status.percent} %", style = Type.Title, color = Palette.OnSurface)
                    Box(Modifier.fillMaxWidth().height(UpdateLayout.ProgressHeight).background(Palette.Outline)) {
                        Box(Modifier.fillMaxWidth(status.percent / 100f).height(UpdateLayout.ProgressHeight).background(Palette.Accent))
                    }
                    Text("Retour annule le téléchargement.", style = Type.Caption, color = Palette.Muted)
                    TvButton("Annuler", updater::dismiss, Modifier.focusRequester(focus))
                }
                is UpdateStatus.Ready -> {
                    Text("Version ${status.release.versionName} prête à installer", style = Type.Title, color = Palette.OnSurface)
                    Text(installMessage ?: "La première fois, autorisez Canapé à installer des applications. Revenez ensuite ici pour continuer.",
                        style = Type.Body, color = Palette.Muted)
                    TvButton("Installer", {
                        try {
                            if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    "package:${context.packageName}".toUri()))
                            } else {
                                val file = updater.validateReady()
                                if (file == null) updater.installationFailed() else {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
                                    installer.launch(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                }
                            }
                        } catch (_: android.content.ActivityNotFoundException) { updater.installationFailed()
                        } catch (_: SecurityException) { updater.installationFailed() }
                    })
                    TvButton("Plus tard", updater::dismiss, Modifier.focusRequester(focus))
                }
                is UpdateStatus.Failed -> {
                    Text(status.message, style = Type.Body, color = Palette.OnSurface)
                    TvButton("Réessayer", {
                        val release = status.release
                        if (release == null) updater.check(manual = true) else updater.download(release)
                    }, Modifier.focusRequester(focus))
                    TvButton("Fermer", updater::dismiss)
                }
            }
        }
        LaunchedEffect(focusKey) {
            withFrameNanos { }
            withFrameNanos { }
            focusManager.clearFocus(force = true)
            owner.requestFocus()
            focusWhenReady(focus)
        }
    }
}
