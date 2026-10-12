package fr.channelfinder.tv

import android.content.Context
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

// Many Xtream providers reject Android's default Dalvik User-Agent on stream requests (HTTP 407),
// so identify as a widely accepted IPTV player and follow redirects to the stream servers.
private const val STREAM_USER_AGENT = "VLC/3.0.21 LibVLC/3.0.21"

/**
 * @param fullScreen the full-screen player takes audio focus (ducks/pauses for the Assistant and other
 * apps) and pauses when the audio output disconnects; the muted preview does neither.
 * @param audioLanguages preferred audio languages, most wanted first (ISO 639 codes).
 */
fun buildStreamPlayer(context: Context, fullScreen: Boolean = false, audioLanguages: List<String> = emptyList()): ExoPlayer {
    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(STREAM_USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
    val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory))
        .apply {
            if (fullScreen) {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                    /* handleAudioFocus = */ true
                )
                setHandleAudioBecomingNoisy(true)
            }
        }
        .build()
    if (audioLanguages.isNotEmpty()) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguages(*audioLanguages.toTypedArray())
            .build()
    }
    return player
}

/**
 * A bare video surface: no Media3 controls (live TV has nothing to seek) and never focusable.
 * Only full-screen playback keeps the TV awake; a preview the viewer did not start must not block the
 * screensaver (TV quality criteria TV-BU / TV-BY).
 */
@OptIn(UnstableApi::class)
fun videoSurface(context: Context, keepAwake: Boolean = false): PlayerView = PlayerView(context).apply {
    useController = false
    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    setShutterBackgroundColor(android.graphics.Color.BLACK)
    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
    keepScreenOn = keepAwake
    isFocusable = false
    isFocusableInTouchMode = false
    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
}

private const val PROVIDER_REFUSED =
    "Le fournisseur refuse cette chaîne en ce moment (erreur 407). Essayez une autre version de la chaîne ou réessayez plus tard."

/** Plain-language reason a stream failed. Never includes the URL, which embeds credentials. */
fun playbackErrorMessage(error: PlaybackException): String {
    var cause: Throwable? = error
    while (cause != null && cause !is HttpDataSource.InvalidResponseCodeException) {
        // HttpURLConnection reports a 407 from the stream server as a ProtocolException, not a status code.
        if (cause is java.net.ProtocolException && cause.message.orEmpty().contains("407")) return PROVIDER_REFUSED
        cause = cause.cause
    }
    if (cause is HttpDataSource.InvalidResponseCodeException) {
        return when (val code = cause.responseCode) {
            401, 403 -> "Le fournisseur refuse l’accès à cette chaîne (erreur $code)."
            404, 410 -> "Cette chaîne n’est plus proposée par le fournisseur."
            407 -> PROVIDER_REFUSED
            429, 458, 509 -> "Trop d’appareils regardent en même temps sur ce compte. Arrêtez la lecture ailleurs, puis réessayez."
            in 500..599 -> "Le serveur de la chaîne rencontre un problème (erreur $code)."
            else -> "Le fournisseur a refusé la lecture (erreur $code)."
        }
    }
    return when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "La chaîne ne répond pas. Vérifiez la connexion Internet, puis réessayez."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "Le format de cette chaîne n’est pas pris en charge par cette TV."
        else -> "Cette chaîne ne peut pas être lue pour le moment."
    }
}
