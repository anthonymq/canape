package fr.channelfinder.tv

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** One live connection, owned by the app while browsing or watching, independently of its surface. */
@OptIn(UnstableApi::class)
internal class LivePlaybackSession(private val context: Context) {
    private var instance: ExoPlayer? = null
    private var target: PlayerView? = null
    var channelId: String? = null
        private set

    fun player(): ExoPlayer = instance ?: buildStreamPlayer(context, fullScreen = true).also { instance = it }

    fun hasStream(id: String): Boolean = instance?.let {
        channelId == id && it.currentMediaItem != null && it.playerError == null &&
            it.playbackState in listOf(Player.STATE_BUFFERING, Player.STATE_READY)
    } == true

    fun play(id: String, media: () -> MediaItem, languages: List<String>, restart: Boolean = false) {
        val player = player()
        if (restart || !hasStream(id)) {
            channelId = id
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguages(*languages.toTypedArray()).build()
            player.setMediaItem(media())
            player.prepare()
        }
        player.volume = 1f
        player.playWhenReady = true
    }

    fun attach(view: PlayerView) {
        val player = instance ?: return
        if (target === view) return
        PlayerView.switchTargetView(player, target, view)
        target = view
    }

    fun detach(view: PlayerView) {
        if (target === view) target = null
        view.player = null
    }

    fun stop() {
        channelId = null
        instance?.apply { stop(); clearMediaItems() }
    }

    fun release() {
        target?.player = null
        target = null
        instance?.release()
        instance = null
        channelId = null
    }
}
