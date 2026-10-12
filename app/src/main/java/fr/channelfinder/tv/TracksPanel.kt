package fr.channelfinder.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.util.Locale

private data class TrackOption(val label: String, val detail: String?, val selected: Boolean, val language: String?, val apply: (TrackSelectionParameters) -> TrackSelectionParameters)

/**
 * Side panel to choose the audio language and subtitles of the current stream. A single-choice list
 * (TV lists guideline): radio indicators, not colour alone, show the current choice; Back closes it.
 */
@Composable
fun TracksPanel(
    visible: Boolean,
    tracks: Tracks,
    parameters: TrackSelectionParameters,
    onSelect: (TrackSelectionParameters, String?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = visible, onBack = onClose)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInHorizontally(tween(220)) { it / 2 },
        exit = fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 2 },
        modifier = modifier
    ) {
        val audio = remember(tracks) { audioOptions(tracks) }
        val text = remember(tracks) { textOptions(tracks) }
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { delay(80); runCatching { firstFocus.requestFocus() } }
        val selectedIndex = (audio + text).indexOfFirst { it.selected }.coerceAtLeast(0)

        Column(
            Modifier.width(460.dp).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Palette.Ink.copy(alpha = 0.96f), Palette.Ink), startX = 0f, endX = 120f))
                .padding(start = 48.dp, end = Spacing.Side, top = Spacing.Top, bottom = Spacing.Top)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Audio et sous-titres", style = Type.Headline, color = Palette.OnSurface)
            Spacer(Modifier.height(12.dp))
            var index = 0
            if (audio.size > 1) {
                Text("AUDIO", style = Type.Eyebrow, color = Palette.Accent, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                audio.forEach { option ->
                    val i = index++
                    OptionRow(option, if (i == selectedIndex) Modifier.focusRequester(firstFocus) else Modifier) {
                        onSelect(option.apply(parameters), option.language)
                    }
                }
                Spacer(Modifier.height(14.dp))
            } else index += audio.size
            if (text.size > 1) {
                Text("SOUS-TITRES", style = Type.Eyebrow, color = Palette.Accent, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                text.forEach { option ->
                    val i = index++
                    OptionRow(option, if (i == selectedIndex) Modifier.focusRequester(firstFocus) else Modifier) {
                        onSelect(option.apply(parameters), null)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            HintBar("OK" to "Choisir", "RETOUR" to "Fermer")
        }
    }
}

@Composable
private fun OptionRow(option: TrackOption, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        container = Color.Transparent,
        focusedScale = 1.02f
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                if (option.selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (option.selected) "Sélectionné" else null,
                modifier = Modifier.size(22.dp),
                tint = LocalContentColor.current
            )
            Column {
                Text(option.label, style = Type.Label, maxLines = 1)
                option.detail?.let { Text(it, style = Type.Caption, color = secondaryColor(), maxLines = 1) }
            }
        }
    }
}

private fun audioOptions(tracks: Tracks): List<TrackOption> =
    tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { i ->
            val format = group.getTrackFormat(i)
            TrackOption(
                label = languageName(format) ?: format.label ?: "Piste audio",
                detail = listOfNotNull(format.label?.takeIf { languageName(format) != null }, channelLayout(format)).joinToString(" · ").ifBlank { null },
                selected = group.isTrackSelected(i),
                language = format.language
            ) { params ->
                params.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                    .build()
            }
        }
    }

private fun textOptions(tracks: Tracks): List<TrackOption> {
    val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val anySelected = groups.any { it.isSelected }
    val off = TrackOption("Désactivés", null, selected = !anySelected, language = null) { params ->
        params.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    }
    return listOf(off) + groups.flatMap { group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { i ->
            val format = group.getTrackFormat(i)
            TrackOption(languageName(format) ?: format.label ?: "Sous-titres", format.label?.takeIf { languageName(format) != null }, group.isTrackSelected(i), format.language) { params ->
                params.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                    .build()
            }
        }
    }
}

private fun languageName(format: Format): String? {
    val code = format.language?.takeIf { it.isNotBlank() && it != "und" && it != C.LANGUAGE_UNDETERMINED } ?: return null
    val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.FRENCH)
    return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
        ?.replaceFirstChar { it.titlecase(Locale.FRENCH) } ?: code.uppercase()
}

private fun channelLayout(format: Format): String? = when (format.channelCount) {
    1 -> "Mono"
    2 -> "Stéréo"
    6 -> "5.1"
    8 -> "7.1"
    else -> null
}
