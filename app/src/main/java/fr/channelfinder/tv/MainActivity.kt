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
        setContent { ChannelFinderTheme { ChannelFinderApp() } }
    }
}

@Composable
private fun ChannelFinderApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { AppState(context.applicationContext, scope) }

    LaunchedEffect(Unit) { state.start() }
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
        AnimatedContent(
            targetState = state.page,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "page"
        ) { page ->
            when (page) {
                Page.Loading -> LoadingScreen(state.loadStep)
                is Page.LoadError -> LoadErrorScreen(page.message, onRetry = state::retry, onEditAccount = state::editAccount)
                is Page.Setup -> SetupScreen(state, page)
                Page.Home -> HomeScreen(state)
                Page.Settings -> SettingsScreen(state)
                is Page.CategoryPicker -> CategoryPickerScreen(state, page)
                is Page.Browse -> BrowserScreen(state, page)
                is Page.Player -> PlayerScreen(state, page)
            }
        }
        NoticeHost(state.notice)
    }
}
