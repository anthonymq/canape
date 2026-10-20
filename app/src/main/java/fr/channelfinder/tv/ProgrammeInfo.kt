package fr.channelfinder.tv

import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal data class ProgrammeWindow(val current: TvProgramme?, val next: TvProgramme?, val progress: Float)

internal fun programmeWindow(rows: List<TvProgramme>, now: Long): ProgrammeWindow {
    val current = rows.filter { it.isLive(now) }.maxByOrNull { it.start }
    val next = rows.filter { it.start > now && (current == null || it.start >= current.end) }.minByOrNull { it.start }
    val progress = current?.let { ((now - it.start).toDouble() / (it.end - it.start)).toFloat().coerceIn(0f, 1f) } ?: 0f
    return ProgrammeWindow(current, next, progress)
}

internal data class ProgrammeSchedule(val rows: List<TvProgramme>? = null, val failed: Boolean = false)

@Composable
internal fun rememberProgrammeSchedule(account: XcAccount?, channelId: String?, repository: GuideRepository, now: Long): ProgrammeSchedule {
    if (account == null || channelId == null) return ProgrammeSchedule()
    var schedule by remember(account, channelId) { mutableStateOf(ProgrammeSchedule()) }
    val cancellation = remember(account, channelId, now / (15 * 60_000L)) { CancellationSignal() }
    DisposableEffect(cancellation) { onDispose { cancellation.cancel() } }
    LaunchedEffect(cancellation) {
        try {
            delay(650)
            val rows = withContext(Dispatchers.IO) { repository.load(account, channelId, cancellation) }
            schedule = ProgrammeSchedule(rows)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: android.os.OperationCanceledException) { throw CancellationException()
        } catch (_: CatalogException) { schedule = ProgrammeSchedule(failed = true) }
    }
    return schedule
}

@Composable
internal fun ProgrammeInfo(schedule: ProgrammeSchedule, now: Long, modifier: Modifier = Modifier, showMissing: Boolean = true) {
    val window = programmeWindow(schedule.rows.orEmpty(), now)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        window.current?.let { current ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(current.title, style = Type.Label, color = Palette.OnSurface, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(current.timeLabel(), style = Type.Caption, color = Palette.Muted, maxLines = 1)
            }
            Box(Modifier.fillMaxWidth().height(3.dp).progressSemantics(window.progress)
                .background(Palette.Surface2, RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth(window.progress).fillMaxHeight().background(Palette.Accent, RoundedCornerShape(2.dp)))
            }
        }
        if (window.current == null && showMissing) Text(when {
            schedule.failed -> "Programme indisponible"
            schedule.rows == null -> "Chargement du programme…"
            else -> "Programme non fourni"
        }, style = Type.Caption, color = Palette.Muted)
        window.next?.let { next ->
            Text("Ensuite · ${guideTime(next.start)} · ${next.title}", style = Type.Caption,
                color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
