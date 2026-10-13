package fr.channelfinder.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        val probe = if (debuggable) intent.getStringExtra("probe") else null
        setContent { ChannelFinderTheme { ChannelFinderApp(probe) } }
    }
}

@Composable
private fun ChannelFinderApp(probe: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { AppState(context.applicationContext, scope) }

    LaunchedEffect(Unit) { state.start() }
    // Debug builds only: `adb shell am start -n fr.channelfinder.tv/.MainActivity -e probe 30` checks streams.
    LaunchedEffect(state.channels.isNotEmpty()) {
        val account = state.account
        val parts = probe?.split(':') ?: return@LaunchedEffect
        val count = parts[0].toIntOrNull() ?: return@LaunchedEffect
        StreamProbe.gapMs = parts.getOrNull(1)?.toLongOrNull() ?: 400L
        if (account == null || state.channels.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            StreamProbe.accountInfo(account)
            listOf(BrowseLane.FRANCE, BrowseLane.PORTUGUESE).forEach { lane ->
                val all = state.channelsFor(lane, null)
                val step = (all.size / count).coerceAtLeast(1)
                StreamProbe.probe(account, "${lane.name}-first", all.take(count))
                if (parts.getOrNull(2) != "first") StreamProbe.probe(account, "${lane.name}-spread", all.filterIndexed { i, _ -> i % step == 0 }.take(count))
            }
        }
    }
    // Live/upcoming match counts change with the clock as well as the catalog.
    LaunchedEffect(state.channels) {
        while (state.channels.isNotEmpty()) {
            state.refreshEventCounts()
            delay(5 * 60_000L)
        }
    }
    LaunchedEffect(state.notice?.id) {
        if (state.notice != null) {
            delay(2600)
            state.notice = null
        }
    }
    // Back pops the in-app stack; on the root screen the system handles it and returns to the launcher.
    BackHandler(enabled = state.backStack.size > 1) { state.back() }

    Box(
        Modifier.fillMaxSize()
            .background(Palette.Ink)
            .background(Brush.verticalGradient(0f to Palette.InkTop, 0.55f to Palette.Ink))
    ) {
        val pages: @Composable () -> Unit = {
            AnimatedContent(
                targetState = state.page,
                // Each lane/category is its own screen (fresh focus and scroll); query and filter edits are not.
                contentKey = {
                    when (it) {
                        is Page.Browse -> "browse:${it.lane}:${it.categoryId}"
                        is Page.Library -> "library:${it.kind}"
                        is Page.Episodes -> "episodes:${it.series.id}"
                        is Page.MovieDetails -> "movie-details:${it.item.id}"
                        is Page.VodPlayer -> "vod:${it.kind}:${it.item.id}"
                        else -> it::class
                    }
                },
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "page"
            ) { page ->
                when (page) {
                    Page.Loading -> LoadingScreen(state.loadStep)
                    is Page.LoadError -> LoadErrorScreen(page.message, onRetry = state::retry, onEditAccount = state::editAccount)
                    is Page.Setup -> SetupScreen(state, page)
                    Page.Home -> HomeScreen(state)
                    Page.Settings -> SettingsScreen(state)
                    is Page.Library -> LibraryScreen(state, page)
                    is Page.Episodes -> EpisodesScreen(state, page)
                    is Page.MovieDetails -> MovieDetailsScreen(state, page)
                    is Page.VodPlayer -> VodPlayerScreen(state, page)
                    is Page.CategoryPicker -> CategoryPickerScreen(state, page)
                    is Page.Browse -> BrowserScreen(state, page)
                    is Page.Player -> PlayerScreen(state, page)
                    is Page.Guide -> GuideScreen(state, page)
                }
            }
        }
        // The navigation drawer frames the browsing screens; playback, setup and loading stay full screen.
        val withDrawer = when (state.page) {
            Page.Home, Page.Settings, is Page.CategoryPicker, is Page.Browse, is Page.Library, is Page.Episodes, is Page.MovieDetails -> true
            else -> false
        }
        if (withDrawer) AppDrawer(state) { pages() } else pages()
        NoticeHost(state.notice, atBottom = state.page == Page.Home)
    }
}
