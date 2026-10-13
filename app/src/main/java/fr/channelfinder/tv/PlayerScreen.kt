package fr.channelfinder.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private enum class PlayStatus { LOADING, PLAYING, ERROR, NOT_STARTED }

@Composable
fun PlayerScreen(state: AppState, page: Page.Player) {
    val context = LocalContext.current
    val account = state.account ?: return
    val playlist = page.playlist.ifEmpty { listOf(page.channelId) }
    val player = remember { buildStreamPlayer(context, fullScreen = true, audioLanguages = state.audioLanguages) }
    // Leaving the app (Home button, another app, screensaver) stops the stream: nothing plays out of sight,
    // and the provider's single connection is freed. Coming back reloads at the live point.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val started = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    // Play/Pause on the remote (TV-PP). Live TV has no timeshift, so resuming returns to the live point.
    var userPaused by remember { mutableStateOf(false) }
    var tracksOpen by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    val number = remember { NumberFormat.getIntegerInstance(Locale.FRANCE) }

    var status by remember { mutableStateOf(PlayStatus.LOADING) }
    var firstFrame by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var autoRetries by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val currentId by rememberUpdatedState(page.channelId)
    var attempt by remember { mutableIntStateOf(0) }
    // "Essayer quand même" on an event that has not started (kickoff times can be in another time zone).
    var forceStart by remember(page.channelId) { mutableStateOf(false) }
    var bannerVisible by remember { mutableStateOf(true) }
    var bannerTick by remember { mutableIntStateOf(0) }
    // While zapping, the banner follows the D-pad immediately; the stream switches once the viewer pauses.
    var pendingIndex by remember { mutableIntStateOf(playlist.indexOf(page.channelId).coerceAtLeast(0)) }
    val rootFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }

    val shownChannel = state.channelsById[playlist[pendingIndex]] ?: state.channelsById[page.channelId] ?: return

    fun showBanner() { bannerVisible = true; bannerTick++ }
    fun setPaused(paused: Boolean) {
        if (status != PlayStatus.PLAYING || paused == userPaused) return
        userPaused = paused
        if (paused) {
            player.pause()
        } else {
            player.seekToDefaultPosition()
            player.play()
        }
        showBanner()
    }
    val hasTrackChoice = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.sumOf { it.length } > 1 ||
        tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
    fun zap(step: Int) {
        if (playlist.size < 2) { showBanner(); return }
        // Skip channels that just failed, unless every channel in the list did.
        var index = pendingIndex
        repeat(playlist.size) {
            index = (index + step + playlist.size) % playlist.size
            if (!state.isUnavailable(playlist[index])) {
                pendingIndex = index
                showBanner()
                return
            }
        }
        pendingIndex = (pendingIndex + step + playlist.size) % playlist.size
        showBanner()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    status = PlayStatus.PLAYING
                    state.markWorking(currentId)
                }
            }

            override fun onRenderedFirstFrame() { firstFrame = true }

            override fun onTracksChanged(newTracks: Tracks) { tracks = newTracks }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    return
                }
                // Provider failures are often intermittent: retry twice, a little later each time, before bothering the viewer.
                if (autoRetries < 2) {
                    autoRetries++
                    val wait = 1_500L * autoRetries
                    scope.launch { delay(wait); player.prepare() }
                    return
                }
                errorText = playbackErrorMessage(error)
                status = PlayStatus.ERROR
                state.markUnavailable(currentId)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(page.channelId, attempt, started) {
        if (!started) {
            player.stop()
            return@LaunchedEffect
        }
        userPaused = false
        status = PlayStatus.LOADING
        firstFrame = false
        autoRetries = 0
        val channel = state.channelsById[page.channelId] ?: return@LaunchedEffect
        if (!forceStart && ChannelText.notStartedYet(channel.event)) {
            player.stop()
            player.clearMediaItems()
            status = PlayStatus.NOT_STARTED
            return@LaunchedEffect
        }
        player.setMediaItem(MediaItem.fromUri(state.repository.streamUrl(account, channel)))
        player.volume = 1f
        player.playWhenReady = true
        player.prepare()
        showBanner()
    }
    LaunchedEffect(pendingIndex) {
        val target = playlist[pendingIndex]
        if (target != page.channelId) {
            delay(250)
            state.zapTo(target)
        }
    }
    LaunchedEffect(bannerTick, status, userPaused) {
        if (status == PlayStatus.PLAYING && !userPaused) {
            delay(5_000)
            bannerVisible = false
        }
    }
    LaunchedEffect(status) {
        delay(50)
        runCatching { if (status == PlayStatus.ERROR || status == PlayStatus.NOT_STARTED) retryFocus.requestFocus() else rootFocus.requestFocus() }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (tracksOpen) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause -> { setPaused(!userPaused); true }
                    Key.MediaPause -> { setPaused(true); true }
                    Key.MediaPlay -> { setPaused(false); true }
                    Key.LastChannel -> {
                        val previous = state.previousChannelId?.takeIf { it in playlist || state.channelsById.containsKey(it) }
                        if (previous != null) {
                            playlist.indexOf(previous).takeIf { it >= 0 }?.let { pendingIndex = it }
                            state.zapTo(previous)
                            showBanner()
                        }
                        true
                    }
                    Key.DirectionUp, Key.ChannelDown -> { zap(-1); true }
                    Key.DirectionDown, Key.ChannelUp -> { zap(1); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (status == PlayStatus.ERROR || status == PlayStatus.NOT_STARTED) false
                        // OK resumes a paused stream (TV-PC); otherwise it shows or hides the channel banner.
                        else if (userPaused) { setPaused(false); true }
                        else { if (bannerVisible) bannerVisible = false else showBanner(); true }
                    }
                    Key.DirectionRight -> {
                        if (status == PlayStatus.PLAYING && hasTrackChoice) { tracksOpen = true; true }
                        else if (status == PlayStatus.ERROR || status == PlayStatus.NOT_STARTED) false
                        else { showBanner(); true }
                    }
                    Key.DirectionLeft, Key.Guide -> { state.openGuide(shownChannel, playlist.mapNotNull(state.channelsById::get)); true }
                    Key.Info -> {
                        if (status == PlayStatus.ERROR || status == PlayStatus.NOT_STARTED) false else { showBanner(); true }
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        AndroidView(
            factory = { videoSurface(it, keepAwake = true).apply { this.player = player } },
            update = { view -> if (view.player !== player) view.player = player },
            modifier = Modifier.fillMaxSize()
        )

        if (status != PlayStatus.ERROR && status != PlayStatus.NOT_STARTED && (!firstFrame || pendingIndex != playlist.indexOf(page.channelId))) {
            // Dim the previous picture while the next channel loads so the loading message stays readable.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (firstFrame) 0.7f else 0f)))
            Column(Modifier.align(Alignment.Center).padding(bottom = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ChannelLogo(shownChannel, Modifier.size(width = 200.dp, height = 120.dp), corner = 16.dp, initialsSize = 40)
                Spacer(Modifier.height(24.dp))
                Spinner(Modifier.size(44.dp))
                Spacer(Modifier.height(14.dp))
                Text("Chargement de ${shownChannel.displayName}…", style = Type.Body, color = Palette.Muted)
            }
        } else if (status == PlayStatus.PLAYING && buffering) {
            Spinner(Modifier.align(Alignment.Center).size(44.dp))
        }

        if (status == PlayStatus.NOT_STARTED) {
            Column(
                Modifier.align(Alignment.Center).widthIn(max = 620.dp)
                    .background(Palette.Surface1.copy(alpha = 0.96f), RoundedCornerShape(24.dp))
                    .padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(14.dp))
                Text(shownChannel.displayName, style = Type.Headline, color = Palette.OnSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${shownChannel.event?.let(ChannelText::eventStartSentence).orEmpty()}. Le direct n’a pas encore commencé.",
                    style = Type.Body, color = Palette.Muted, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvButton("Essayer quand même", { forceStart = true; attempt++ }, Modifier.focusRequester(retryFocus), icon = Icons.Rounded.PlayArrow, primary = true)
                    if (playlist.size > 1) TvButton("Chaîne suivante", { zap(1) }, icon = Icons.Rounded.SkipNext)
                }
                Spacer(Modifier.height(18.dp))
                HintBar("▲▼" to "Changer de chaîne", "RETOUR" to "Revenir à la liste")
            }
        }

        if (status == PlayStatus.ERROR) {
            Column(
                Modifier.align(Alignment.Center).widthIn(max = 620.dp)
                    .background(Palette.Surface1.copy(alpha = 0.96f), RoundedCornerShape(24.dp))
                    .padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Error, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(14.dp))
                Text("Impossible de lire ${shownChannel.displayName}", style = Type.Headline, color = Palette.OnSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(errorText, style = Type.Body, color = Palette.Muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvButton("Réessayer", { attempt++ }, Modifier.focusRequester(retryFocus), icon = Icons.Rounded.Refresh, primary = true)
                    if (playlist.size > 1) TvButton("Chaîne suivante", { zap(1) }, icon = Icons.Rounded.SkipNext)
                }
                Spacer(Modifier.height(18.dp))
                HintBar("▲▼" to "Changer de chaîne", "RETOUR" to "Revenir à la liste")
            }
        }

        AnimatedVisibility(
            visible = bannerVisible && status != PlayStatus.ERROR && status != PlayStatus.NOT_STARTED,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 },
            exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ChannelBanner(
                channel = shownChannel,
                paused = userPaused,
                hasTrackChoice = hasTrackChoice && status == PlayStatus.PLAYING,
                position = "${number.format(pendingIndex + 1)} / ${number.format(playlist.size)}",
                isFavorite = shownChannel.id in state.favorites,
                previous = if (playlist.size > 1) state.channelsById[playlist[(pendingIndex - 1 + playlist.size) % playlist.size]] else null,
                next = if (playlist.size > 1) state.channelsById[playlist[(pendingIndex + 1) % playlist.size]] else null
            )
        }

        if (userPaused) {
            Box(
                Modifier.align(Alignment.Center).padding(bottom = 120.dp).size(96.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Pause, contentDescription = "En pause", tint = Color.White, modifier = Modifier.size(56.dp)) }
        }

        TracksPanel(
            visible = tracksOpen,
            tracks = tracks,
            parameters = player.trackSelectionParameters,
            onSelect = { parameters, language ->
                player.trackSelectionParameters = parameters
                state.rememberAudioLanguage(language)
            },
            onClose = {
                tracksOpen = false
                runCatching { rootFocus.requestFocus() }
            },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun ChannelBanner(
    channel: Channel,
    paused: Boolean,
    hasTrackChoice: Boolean,
    position: String,
    isFavorite: Boolean,
    previous: Channel?,
    next: Channel?
) {
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.92f))))
            .padding(start = Spacing.Side, end = Spacing.Side, top = 72.dp, bottom = Spacing.Top)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ChannelLogo(channel, Modifier.size(width = 150.dp, height = 90.dp), corner = 14.dp, initialsSize = 30)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.background(if (paused) Palette.Surface2 else Palette.Live, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (paused) Icon(Icons.Rounded.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        else Box(Modifier.size(7.dp).background(Color.White, CircleShape))
                        Text(if (paused) "EN PAUSE" else "EN DIRECT", style = Type.Badge, color = Color.White)
                    }
                    EventTag(channel)
                    Text(position, style = Type.Label, color = Palette.Muted)
                    if (isFavorite) Icon(Icons.Rounded.Star, contentDescription = "Favori", tint = Palette.Gold, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(channel.displayName, style = Type.Display, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    channel.countryTag?.let { Badge(it, tone = Palette.Muted) }
                    channel.badges.forEach { Badge(it, tone = Palette.Muted) }
                    Text(channel.categoryDisplay, style = Type.Body, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Clock(style = Type.Display)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
            if (previous != null) HintBar("▲" to previous.displayName)
            if (next != null) HintBar("▼" to next.displayName)
            Spacer(Modifier.weight(1f))
            if (hasTrackChoice) HintBar("▶" to "Audio et sous-titres")
            HintBar(if (paused) "OK" to "Reprendre le direct" else "OK" to "Masquer", "RETOUR" to "Liste")
            HintBar("◀" to "Guide TV")
        }
    }
}
