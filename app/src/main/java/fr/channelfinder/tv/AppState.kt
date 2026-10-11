package fr.channelfinder.tv

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val UNAVAILABLE_FOR_MS = 12 * 60 * 60 * 1000L

/** App-wide state: catalog, back stack, favorites, recents and per-screen positions. */
class AppState(context: Context, private val scope: CoroutineScope) {
    private val credentialStore = CredentialStore(context)
    private val prefs = context.getSharedPreferences("channel_usage", Context.MODE_PRIVATE)
    private val cache = CatalogCache(context)
    val repository = XtreamRepository()

    var account by mutableStateOf(credentialStore.load())
        private set

    val backStack = mutableStateListOf<Page>(if (account == null) Page.Setup() else Page.Loading)
    val page: Page get() = backStack.last()

    var channels by mutableStateOf(emptyList<Channel>())
        private set
    var categories by mutableStateOf(emptyList<ChannelCategory>())
        private set
    var channelsById by mutableStateOf(emptyMap<String, Channel>())
        private set
    var categoriesById by mutableStateOf(emptyMap<String, ChannelCategory>())
        private set
    var categoryCounts by mutableStateOf(emptyMap<String, Int>())
        private set
    var laneCounts by mutableStateOf(emptyMap<String, Int>())
        private set
    var lastUpdated by mutableStateOf(0L)
        private set

    var loadStep by mutableStateOf(LoadStep.OPENING)
        private set
    var connecting by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    private var refreshJob: Job? = null

    /** Newest first. */
    val favorites = mutableStateListOf<String>()
    val pinnedCategories = mutableStateListOf<String>()
    val recent = mutableStateListOf<String>()

    /** Channels whose stream failed recently (id -> time). They sink to the end of lists and are skipped when zapping. */
    val unavailable = mutableStateMapOf<String, Long>()

    var selectedChannelId by mutableStateOf<String?>(null)

    /** Video quality filter for channel lists; null shows every quality. Remembered between sessions. */
    var qualityFilter by mutableStateOf(
        prefs.getString("quality_filter", null)?.let { name -> ChannelText.VideoQuality.entries.firstOrNull { it.name == name } }
    )
        private set

    fun setQuality(quality: ChannelText.VideoQuality?) {
        qualityFilter = quality
        prefs.edit().putString("quality_filter", quality?.name).apply()
    }
    var browserListState by mutableStateOf(LazyListState())
        private set
    var categoryListState by mutableStateOf(LazyListState())
        private set
    var categoryFocusId by mutableStateOf<String?>(null)
    val homeListState = LazyListState()
    var homeFocusKey by mutableStateOf<String?>(null)

    var notice by mutableStateOf<Notice?>(null)

    init {
        val orderedFavorites = prefs.getString("favorites_list", null)
        if (orderedFavorites != null) favorites += orderedFavorites.split('|').filter(String::isNotBlank)
        else favorites += prefs.getStringSet("favorites", emptySet()).orEmpty()
        val orderedPins = prefs.getString("favorite_categories_list", null)
        if (orderedPins != null) pinnedCategories += orderedPins.split('|').filter(String::isNotBlank)
        else pinnedCategories += prefs.getStringSet("favorite_categories", emptySet()).orEmpty()
        recent += prefs.getString("recent", "").orEmpty().split('|').filter(String::isNotBlank)
        val cutoff = System.currentTimeMillis() - UNAVAILABLE_FOR_MS
        prefs.getString("unavailable", "").orEmpty().split('|').forEach { entry ->
            val id = entry.substringBefore(':')
            val time = entry.substringAfter(':', "").toLongOrNull() ?: return@forEach
            if (id.isNotBlank() && time > cutoff) unavailable[id] = time
        }
    }

    fun isUnavailable(channelId: String): Boolean =
        unavailable[channelId]?.let { System.currentTimeMillis() - it < UNAVAILABLE_FOR_MS } == true

    /** Called once retries are exhausted, from the preview or full screen. */
    fun markUnavailable(channelId: String) {
        unavailable[channelId] = System.currentTimeMillis()
        persistHealth()
    }

    fun markWorking(channelId: String) {
        if (unavailable.remove(channelId) != null) persistHealth()
    }

    private fun persistHealth() {
        val entries = unavailable.entries.sortedByDescending { it.value }.take(2000)
        prefs.edit().putString("unavailable", entries.joinToString("|") { "${it.key}:${it.value}" }).apply()
    }

    // region Navigation
    fun navigate(target: Page) {
        when (target) {
            is Page.Browse -> { browserListState = LazyListState(); selectedChannelId = null }
            is Page.CategoryPicker -> { categoryListState = LazyListState(); categoryFocusId = null }
            else -> Unit
        }
        backStack += target
    }

    fun replaceTop(target: Page) {
        backStack[backStack.lastIndex] = target
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    private fun resetTo(target: Page) {
        backStack.clear()
        backStack += target
    }
    // endregion

    // region Catalog
    fun start() {
        val current = account ?: return
        scope.launch {
            val started = android.os.SystemClock.elapsedRealtime()
            val cached = withContext(Dispatchers.IO) { cache.load(current) }
            android.util.Log.i("AppState", "Catalog cache ${if (cached == null) "missing" else "loaded"} in ${android.os.SystemClock.elapsedRealtime() - started} ms")
            if (cached != null) {
                applyCatalog(cached.catalog, cached.savedAt)
                resetTo(Page.Home)
                refresh(silent = true)
            } else {
                loadFromNetwork(current)
            }
        }
    }

    fun retry() {
        val current = account ?: return resetTo(Page.Setup())
        scope.launch { loadFromNetwork(current) }
    }

    private suspend fun loadFromNetwork(current: XcAccount) {
        resetTo(Page.Loading)
        loadStep = LoadStep.CONNECTING
        try {
            val catalog = withContext(Dispatchers.IO) { repository.loadCatalog(current) { loadStep = it } }
            applyCatalog(catalog, System.currentTimeMillis())
            withContext(Dispatchers.IO) { cache.save(current, catalog) }
            resetTo(Page.Home)
        } catch (error: CatalogException) {
            resetTo(Page.LoadError(error.message.orEmpty()))
        } catch (error: Exception) {
            resetTo(Page.LoadError("Impossible de charger les chaînes. Réessayez dans un instant."))
        }
    }

    /** Validates the account against the provider before storing it. */
    fun connect(newAccount: XcAccount, onError: (String) -> Unit) {
        if (connecting) return
        scope.launch {
            connecting = true
            loadStep = LoadStep.CONNECTING
            try {
                val catalog = withContext(Dispatchers.IO) { repository.loadCatalog(newAccount) { loadStep = it } }
                withContext(Dispatchers.IO) {
                    credentialStore.save(newAccount)
                    cache.save(newAccount, catalog)
                }
                if (newAccount != account) {
                    recent.clear()
                    persistUsage()
                }
                account = newAccount
                applyCatalog(catalog, System.currentTimeMillis())
                resetTo(Page.Home)
            } catch (error: CatalogException) {
                onError(error.message.orEmpty())
            } catch (error: Exception) {
                onError("Impossible de se connecter. Vérifiez l’adresse et réessayez.")
            } finally {
                connecting = false
            }
        }
    }

    fun refresh(silent: Boolean) {
        val current = account ?: return
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            refreshing = true
            try {
                val catalog = withContext(Dispatchers.IO) { repository.loadCatalog(current) }
                val previousChannels = channels
                val previousCategories = categories
                val changed = withContext(Dispatchers.Default) {
                    catalog.channels != previousChannels || catalog.categories != previousCategories
                }
                if (changed) applyCatalog(catalog, System.currentTimeMillis()) else lastUpdated = System.currentTimeMillis()
                withContext(Dispatchers.IO) { cache.save(current, catalog) }
                if (!silent) notice = Notice(
                    if (changed) "Liste des chaînes mise à jour" else "La liste des chaînes est à jour",
                    Icons.Rounded.Refresh
                )
            } catch (error: Exception) {
                if (!silent) notice = Notice(error.message ?: "Actualisation impossible")
            } finally {
                refreshing = false
            }
        }
    }

    fun editAccount() {
        navigate(Page.Setup())
    }

    private suspend fun applyCatalog(catalog: XtreamCatalog, savedAt: Long) {
        val started = android.os.SystemClock.elapsedRealtime()
        val byId = withContext(Dispatchers.Default) { catalog.channels.associateBy { it.id } }
        val categoryMap = withContext(Dispatchers.Default) { catalog.categories.associateBy { it.id } }
        val perCategory = withContext(Dispatchers.Default) { catalog.channels.groupingBy { it.categoryId }.eachCount() }
        val perLane = withContext(Dispatchers.Default) { catalog.channels.groupingBy { it.lane }.eachCount() }
        channels = catalog.channels
        categories = catalog.categories
        channelsById = byId
        categoriesById = categoryMap
        categoryCounts = perCategory
        laneCounts = perLane
        lastUpdated = savedAt
        android.util.Log.i("AppState", "Catalog applied in ${android.os.SystemClock.elapsedRealtime() - started} ms")
        // Warm search keys off the main thread so the first keystroke is instant.
        scope.launch(Dispatchers.Default) { catalog.channels.forEach { it.searchKey; it.nameKey } }
    }
    // endregion

    // region Channels
    fun channelsFor(lane: BrowseLane, categoryId: String?): List<Channel> {
        val list = when {
            categoryId != null -> channels.filter { it.categoryId == categoryId }
            lane == BrowseLane.FAVORITES -> return favorites.mapNotNull(channelsById::get)
            lane == BrowseLane.RECENT -> return recent.mapNotNull(channelsById::get)
            lane == BrowseLane.ALL -> channels
            lane == BrowseLane.EVENTS -> return eventsInOrder(channels.filter { it.lane == lane.label })
                // Providers publish most matches twice (main and backup group): list each match once,
                // keeping a copy that has not failed. Both stay reachable from their category.
                .distinctBy { channel -> channel.event?.takeIf { it.start != null }?.let { "${it.title}|${it.start}" } ?: channel.id }
            else -> channels.filter { it.lane == lane.label }.sortedBy { ChannelText.categoryRank(it.category) }
        }
        // Channels that just failed go last; the provider order is kept otherwise (sort is stable).
        return list.sortedBy { if (isUnavailable(it.id)) 1 else 0 }
    }

    /** Live now, then upcoming by start time, then generic match feeds, then finished or idle feeds. */
    private fun eventsInOrder(events: List<Channel>): List<Channel> {
        val now = System.currentTimeMillis()
        return events.sortedWith(
            compareBy<Channel> {
                when (it.event?.status(now)) {
                    ChannelText.EventStatus.LIVE -> 0
                    ChannelText.EventStatus.UPCOMING -> 1
                    ChannelText.EventStatus.FEED -> 2
                    else -> 3
                } + if (isUnavailable(it.id)) 1 else 0
            }.thenBy { channel ->
                val start = channel.event?.start ?: Long.MAX_VALUE
                if (channel.event?.status(now) == ChannelText.EventStatus.LIVE) -start else start
            }
        )
    }

    /** Events on now or later today/tomorrow, for the Home card. */
    fun upcomingEventCount(): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        var live = 0
        var soon = 0
        channels.forEach { channel ->
            if (channel.lane != BrowseLane.EVENTS.label) return@forEach
            val info = channel.event ?: return@forEach
            when (info.status(now)) {
                ChannelText.EventStatus.LIVE -> live++
                ChannelText.EventStatus.UPCOMING -> if ((info.start ?: 0) - now < 36 * 3_600_000L) soon++
                else -> Unit
            }
        }
        return live to soon
    }

    fun search(all: List<Channel>, query: String, quality: ChannelText.VideoQuality? = null): List<Channel> {
        val base = if (quality == null) all else all.filter { it.quality == quality }
        val tokens = ChannelText.tokens(query)
        if (tokens.isEmpty()) return base
        return base.mapNotNull { channel -> ChannelText.score(channel, tokens)?.let { channel to it } }
            .sortedWith(compareBy({ it.second }, { if (isUnavailable(it.first.id)) 1 else 0 }))
            .map { it.first }
    }

    fun play(channel: Channel, playlist: List<Channel>) {
        rememberWatched(channel.id)
        navigate(Page.Player(channel.id, playlist.map { it.id }))
    }

    fun zapTo(channelId: String) {
        val current = page as? Page.Player ?: return
        rememberWatched(channelId)
        replaceTop(current.copy(channelId = channelId))
    }

    private fun rememberWatched(channelId: String) {
        selectedChannelId = channelId
        recent.remove(channelId)
        recent.add(0, channelId)
        while (recent.size > 20) recent.removeAt(recent.lastIndex)
        persistUsage()
    }

    fun clearRecent() {
        recent.clear()
        persistUsage()
        notice = Notice("Historique effacé")
    }

    fun toggleFavorite(channel: Channel) {
        if (favorites.remove(channel.id)) {
            notice = Notice("${channel.displayName} retirée des favoris", Icons.Rounded.StarOutline)
        } else {
            favorites.add(0, channel.id)
            notice = Notice("${channel.displayName} ajoutée aux favoris", Icons.Rounded.Star, Palette.OnFocus)
        }
        persistUsage()
    }

    fun togglePin(category: ChannelCategory) {
        if (pinnedCategories.remove(category.id)) {
            notice = Notice("Catégorie retirée de l’accueil", Icons.Rounded.PushPin)
        } else {
            pinnedCategories.add(0, category.id)
            notice = Notice("Catégorie épinglée sur l’accueil", Icons.Rounded.PushPin)
        }
        persistUsage()
    }

    private fun persistUsage() {
        prefs.edit()
            .putString("favorites_list", favorites.joinToString("|"))
            .putString("favorite_categories_list", pinnedCategories.joinToString("|"))
            .putStringSet("favorites", favorites.toSet())
            .putStringSet("favorite_categories", pinnedCategories.toSet())
            .putString("recent", recent.joinToString("|"))
            .apply()
    }
    // endregion
}
