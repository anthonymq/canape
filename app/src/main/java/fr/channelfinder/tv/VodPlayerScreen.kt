package fr.channelfinder.tv

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import android.os.CancellationSignal
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(state: AppState, page: Page.VodPlayer) {
    val context = LocalContext.current
    val composeView = androidx.compose.ui.platform.LocalView.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val account = state.account ?: return
    val player = remember(page.item, account) { buildStreamPlayer(context, fullScreen = true, audioLanguages = state.audioLanguages) }
    val initialPosition = remember(player) { if (page.startOver) 0L else state.watchHistory.find(account, page.item, page.kind)?.resumeMs ?: 0L }
    var ready by remember(player) { mutableStateOf(false) }
    var ended by remember(player) { mutableStateOf(false) }
    var controlsVisible by remember(player) { mutableStateOf(true) }
    var error by remember(player) { mutableStateOf<String?>(null) }
    var episodes by remember(page.series, account) { mutableStateOf(emptyList<LibraryItem>()) }
    var episodeLoadError by remember(page.series) { mutableStateOf(false) }
    var episodeRetry by remember(page.series) { mutableIntStateOf(0) }
    val episodeCancellation = remember(page.series, episodeRetry) { CancellationSignal() }
    DisposableEffect(episodeCancellation) { onDispose { episodeCancellation.cancel() } }
    LaunchedEffect(page.series, episodeRetry) {
        val series = page.series ?: return@LaunchedEffect
        episodeLoadError = false
        val cached = state.episodeSessions[series.id]?.takeIf { it.account == account }?.catalog
        if (cached != null) { episodes = cached.items; return@LaunchedEffect }
        try {
            episodes = withContext(Dispatchers.IO) { state.vodRepository.episodes(account, series.id, episodeCancellation) }
            state.episodeSessions[series.id] = LibrarySession(account).also { it.catalog = LibraryCatalog(emptyList(), episodes) }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: android.os.OperationCanceledException) { throw CancellationException()
        } catch (_: CatalogException) { episodeLoadError = true }
    }
    val owner = LocalLifecycleOwner.current
    fun checkpoint() {
        if (!ready) return
        val duration = player.duration.coerceAtLeast(0)
        val position = player.currentPosition.coerceAtLeast(0)
        state.watchHistory.save(account, VodWatch(page.item, page.kind, page.series, position, duration,
            System.currentTimeMillis(), watchCompleted(position, duration, ended)))
    }
    LaunchedEffect(player) {
        while (true) { delay(5_000); checkpoint() }
    }
    DisposableEffect(player, owner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) ready = true
                ended = playbackState == Player.STATE_ENDED
                if (ended) checkpoint()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) checkpoint() }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) { checkpoint() }
            override fun onPlayerError(failure: PlaybackException) {
                error = playbackErrorMessage(failure).replace("chaîne", "vidéo").replace("Chaîne", "Vidéo")
            }
        }
        val lifecycle = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { checkpoint(); player.pause() }
        }
        owner.lifecycle.addObserver(lifecycle)
        player.addListener(listener)
        player.setMediaItem(MediaItem.Builder().setUri(state.vodRepository.stream(account, page.item, page.kind))
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(page.item.title).build()).build(), initialPosition)
        player.prepare()
        player.playWhenReady = true
        onDispose {
            checkpoint()
            owner.lifecycle.removeObserver(lifecycle)
            player.removeListener(listener)
            player.release()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (error == null && !ended) AndroidView(factory = {
            PlayerView(it).apply {
                this.player = player
                useController = true
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility -> controlsVisible = visibility == android.view.View.VISIBLE })
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setShowNextButton(false)
                setShowPreviousButton(false)
                keepScreenOn = true
                isFocusable = true
                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                requestFocus()
                showController()
            }
        }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())
        if (controlsVisible && !ended && error == null) Column(Modifier.align(Alignment.TopStart)
            .padding(horizontal = Spacing.Side, vertical = Spacing.Top).background(Color.Black.copy(alpha = 0.65f)).padding(Spacing.Gutter)) {
            Text(page.series?.title ?: page.item.title, style = Type.Title, color = Palette.OnSurface)
            if (page.series != null) Text("Saison ${page.item.season} · Épisode ${page.item.episode}", style = Type.Caption, color = Palette.Muted)
            if (initialPosition >= 5_000) Text("Reprise à ${watchTime(initialPosition)}", style = Type.Caption, color = Palette.Muted)
        }
        if (ended) {
            val next = episodes.dropWhile { it.id != page.item.id }.drop(1).firstOrNull()
            val focus = remember { FocusRequester() }
            Box(Modifier.fillMaxSize().background(Cinema.Background.copy(alpha = 0.95f)), contentAlignment = Alignment.Center) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Row)) {
                    Text("Lecture terminée", style = Type.Display, color = Palette.OnSurface)
                    if (next != null) TvButton("Épisode suivant · S${next.season} E${next.episode}",
                        { state.replaceTop(Page.VodPlayer(next, LibraryKind.SERIES, page.series)) }, Modifier.focusRequester(focus), primary = true)
                    TvButton("Revoir", { ended = false; player.seekTo(0); player.play() },
                        if (next == null) Modifier.focusRequester(focus) else Modifier)
                    if (episodeLoadError) {
                        Text("Les épisodes suivants ne sont pas disponibles pour le moment.", style = Type.Caption, color = Palette.Muted)
                        TvButton("Réessayer les épisodes", { episodeRetry++ })
                    }
                }
            }
            LaunchedEffect(Unit) {
                delay(100)
                focusManager.clearFocus(force = true)
                composeView.requestFocus()
                focusWhenReady(focus)
            }
        }
        error?.let { message ->
            val retryFocus = remember { FocusRequester() }
            Box(Modifier.fillMaxSize().background(Palette.Ink).padding(horizontal = Spacing.Side, vertical = Spacing.Top)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Row)) {
                    Text(message, style = Type.Body, color = Palette.OnSurface)
                    TvButton("Réessayer", { error = null; player.prepare(); player.play() }, Modifier.focusRequester(retryFocus), primary = true)
                }
            }
            LaunchedEffect(Unit) {
                delay(100)
                focusManager.clearFocus(force = true)
                composeView.requestFocus()
                focusWhenReady(retryFocus)
            }
        }
    }
}
