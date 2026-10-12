package fr.channelfinder.tv

import android.os.CancellationSignal
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@Composable
internal fun currentProgrammeSummary(
    account: XcAccount, channelId: String, repository: GuideRepository, requests: Semaphore,
    now: Long, revision: Int = 0, active: Boolean = false,
    selectedRows: List<TvProgramme>? = null, selectedError: String? = null, showStatus: Boolean = true,
    onAvailability: (Boolean) -> Unit = {}
): String {
    var rows by remember(account, channelId) { mutableStateOf<List<TvProgramme>?>(null) }
    var failed by remember(account, channelId) { mutableStateOf(false) }
    val epoch = now / (15 * 60_000L)
    var loadedRevision by remember(account, channelId) { mutableStateOf<Int?>(null) }
    var loadedEpoch by remember(account, channelId) { mutableStateOf<Long?>(null) }
    val cancellation = remember(account, channelId, active, revision, epoch) { CancellationSignal() }
    val reportAvailability by rememberUpdatedState(onAvailability)
    DisposableEffect(cancellation) { onDispose { cancellation.cancel() } }
    LaunchedEffect(cancellation) {
        if (active) return@LaunchedEffect
        failed = false
        try {
            delay(650)
            rows = requests.withPermit {
                withContext(Dispatchers.IO) { repository.load(account, channelId, cancellation, revision > 0) }
            }
            loadedRevision = revision
            loadedEpoch = epoch
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { failed = true; loadedRevision = revision; loadedEpoch = epoch }
    }
    val loaded = loadedRevision == revision && loadedEpoch == epoch
    val schedule = if (active) selectedRows else rows.takeIf { loaded }
    val unavailable = if (active) selectedError != null else failed && loaded
    val current = schedule?.firstOrNull { it.isLive(now) }
    LaunchedEffect(current?.key, schedule != null, unavailable, revision, now / (15 * 60_000L)) {
        if (unavailable || schedule != null) reportAvailability(!unavailable && current != null)
    }
    return when {
        !showStatus -> current?.title.orEmpty()
        unavailable -> "Guide indisponible"
        schedule == null -> "Chargement…"
        else -> current?.title ?: "Programme non fourni"
    }
}
