package fr.channelfinder.tv

import android.content.Context
import androidx.core.content.edit
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
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
const val AUDIO_AUTO = "auto"

/** Viewer's own countries first in each lane (ISO codes). */
private val preferredCountries = mapOf(
    BrowseLane.FRANCE to listOf("FR"),
    BrowseLane.PORTUGUESE to listOf("PT", "BR"),
    BrowseLane.SPORTS to listOf("FR", "PT", "BR")
)

/** App-wide state: catalog, back stack, favorites, recents and per-screen positions. */
class AppState(context: Context, private val scope: CoroutineScope) {
    internal val updater = AppUpdater(context.applicationContext, scope)
    internal val livePlayback = LivePlaybackSession(context.applicationContext)
    private val credentialStore = CredentialStore(context)
    private val prefs = context.getSharedPreferences("channel_usage", Context.MODE_PRIVATE)
    private val cache = CatalogCache(context)
    val repository = XtreamRepository()
    internal val libraryCache = LibraryCache(java.io.File(context.filesDir, "library_cache"))
    val vodRepository = VodRepository(repository)
    val watchHistory = VodWatchStore(context)
    val librarySessions = mutableMapOf<LibraryKind, LibrarySession>()
    val episodeSessions = mutableMapOf<String, LibrarySession>()
    val homeResumeSessions = mutableMapOf<String, LibrarySession>()
    val guideRepository = GuideRepository(repository)

    var account by mutableStateOf(credentialStore.load())
        private set

    val backStack = mutableStateListOf<Page>(if (account == null) Page.Setup() else Page.Loading)
    val page: Page get() = backStack.last()
    internal val confirmPressGuard = ConfirmPressGuard()

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
    var qualityLaneCounts by mutableStateOf(emptyMap<ChannelText.VideoQuality, Map<String, Int>>())
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

    /** Audio languages to prefer, most wanted first: the viewer's last choice, then French and Portuguese. */
    /** Preferred audio language: an ISO code, or [AUDIO_AUTO] for the stream's default track. French by default. */
    var audioPreference by mutableStateOf(prefs.getString("audio_language", null) ?: "fr")
        private set

    val audioLanguages: List<String>
        get() = if (audioPreference == AUDIO_AUTO) emptyList() else listOf(audioPreference, "fr", "pt").distinct()

    fun rememberAudioLanguage(language: String?) {
        if (language.isNullOrBlank() || language == "und") return
        audioPreference = language
        prefs.edit().putString("audio_language", language).apply()
    }

    /** Réglages: Français → Português → Automatique → Français. */
    fun cycleAudioPreference() {
        val next = when (audioPreference) {
            "fr" -> "pt"
            "pt" -> AUDIO_AUTO
            else -> "fr"
        }
        audioPreference = next
        prefs.edit().putString("audio_language", next).apply()
    }

    /** Réglages: lift every "unavailable" mark so those channels are tried normally again. */
    fun clearUnavailable() {
        val count = unavailable.size
        unavailable.clear()
        persistHealth()
        notice = Notice(if (count == 1) "1 chaîne sera réessayée" else "$count chaînes seront réessayées", Icons.Rounded.Refresh)
    }

    /** Channel watched before the current one, for the remote's "last channel" key. */
    var previousChannelId by mutableStateOf<String?>(null)

    fun setQuality(quality: ChannelText.VideoQuality?) {
        qualityFilter = quality
        prefs.edit().putString("quality_filter", quality?.name).apply()
    }

    fun setCountry(page: Page.Browse, country: String?) {
        prefs.edit { putString(browseCountryPreferenceKey(page), country) }
        replaceTop(page.copy(country = country))
    }

    private fun restoreCountry(page: Page): Page = if (page is Page.Browse) {
        page.copy(country = prefs.getString(browseCountryPreferenceKey(page), page.country))
    } else page
    var browserListState by mutableStateOf(LazyListState())
        private set
    var categoryListState by mutableStateOf(LazyListState())
        private set
    var categoryFocusId by mutableStateOf<String?>(null)
    val homeListState = LazyListState()
    var homeFocusKey by mutableStateOf<String?>(null)
    val savedListState = LazyListState()
    var savedFocusKey by mutableStateOf<String?>(null)

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
    private var lastLiveLane = BrowseLane.FRANCE

    fun navigate(target: Page) {
        if (target is Page.Browse && target.categoryId == null && target.lane in listOf(
                BrowseLane.FRANCE, BrowseLane.PORTUGUESE, BrowseLane.SPORTS, BrowseLane.EVENTS, BrowseLane.FAVORITES
            )) lastLiveLane = target.lane
        when (target) {
            is Page.Browse -> { browserListState = LazyListState(); selectedChannelId = null }
            is Page.CategoryPicker -> { categoryListState = LazyListState(); categoryFocusId = null }
            else -> Unit
        }
        backStack += restoreCountry(target)
    }

    fun replaceTop(target: Page) {
        val current = backStack.last()
        val changingScope = target is Page.Browse && current is Page.Browse &&
            browseCountryPreferenceKey(target) != browseCountryPreferenceKey(current)
        backStack[backStack.lastIndex] = if (changingScope) restoreCountry(target) else target
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    private fun resetTo(target: Page) {
        backStack.clear()
        backStack += target
    }

    /** Drawer destinations are top level: the back stack becomes Home, then the destination. */
    fun openDestination(dest: NavDest) {
        val target = when (dest) {
            NavDest.HOME -> null
            NavDest.DIRECT -> Page.Browse(lastLiveLane)
            NavDest.SAVED -> Page.Saved
            NavDest.SEARCH -> Page.Browse(BrowseLane.ALL, startSearch = true)
            NavDest.FRANCE -> Page.Browse(BrowseLane.FRANCE)
            NavDest.PORTUGUESE -> Page.Browse(BrowseLane.PORTUGUESE)
            NavDest.SPORTS -> Page.Browse(BrowseLane.SPORTS)
            NavDest.EVENTS -> Page.Browse(BrowseLane.EVENTS)
            NavDest.FAVORITES -> Page.Browse(BrowseLane.FAVORITES)
            NavDest.MOVIES -> Page.Library(LibraryKind.MOVIES)
            NavDest.SERIES -> Page.Library(LibraryKind.SERIES)
            NavDest.GUIDE -> guideHome()
            NavDest.CATEGORIES -> Page.CategoryPicker()
            NavDest.SETTINGS -> Page.Settings
        }
        resetTo(Page.Home)
        if (target != null) navigate(target)
    }

    /** The drawer item for the current screen: the destination the screen was reached from. */
    val activeDest: NavDest
        get() = when (val root = backStack.getOrNull(1)) {
            null -> NavDest.HOME
            Page.Saved -> NavDest.SAVED
            Page.Settings -> NavDest.SETTINGS
            is Page.Library -> if (root.kind == LibraryKind.MOVIES) NavDest.MOVIES else NavDest.SERIES
            is Page.Guide -> NavDest.GUIDE
            is Page.CategoryPicker -> NavDest.CATEGORIES
            is Page.Browse -> when {
                root.categoryId != null -> NavDest.HOME // a pinned category opened from Home
                root.lane == BrowseLane.FRANCE -> NavDest.FRANCE
                root.lane == BrowseLane.PORTUGUESE -> NavDest.PORTUGUESE
                root.lane == BrowseLane.SPORTS -> NavDest.SPORTS
                root.lane == BrowseLane.EVENTS -> NavDest.EVENTS
                root.lane == BrowseLane.FAVORITES -> NavDest.FAVORITES
                root.lane == BrowseLane.ALL -> NavDest.SEARCH
                else -> NavDest.HOME // Récents, opened from Home
            }
            else -> NavDest.HOME
        }

    /** A screen opened from the drawer (not a sub-page of it). Back there opens the drawer. */
    val isDrawerRoot: Boolean get() = backStack.size == 2 && activeDest != NavDest.HOME

    /** Incremented to move focus into the drawer, on the active item. */
    var drawerFocusRequest by mutableStateOf(0)
        private set

    fun focusDrawer() {
        drawerFocusRequest++
    }

    /** Live and upcoming matches (each counted once), for Home and the drawer badge. */
    var eventCounts by mutableStateOf(0 to 0)
        private set

    suspend fun refreshEventCounts() {
        eventCounts = withContext(Dispatchers.Default) { upcomingEventCount() }
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
            resetTo(Page.Home)
            withContext(Dispatchers.IO) { cache.save(current, catalog) }
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
                }
                if (newAccount != account) {
                    recent.clear()
                    persistUsage()
                }
                account = newAccount
                applyCatalog(catalog, System.currentTimeMillis())
                resetTo(Page.Home)
                withContext(Dispatchers.IO) { cache.save(newAccount, catalog) }
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
                // Providers sometimes answer with a fraction of the list (seen: 70 channels instead of ~24,000).
                // Never let such an answer replace a good catalog; keep the current one and its cache.
                if (channels.size >= 1_000 && catalog.channels.size < channels.size / 2) {
                    android.util.Log.w("AppState", "Ignoring partial catalog: ${catalog.channels.size} of ${channels.size} channels")
                    if (!silent) notice = Notice(
                        "Le fournisseur n’a renvoyé qu’une partie des chaînes. La liste actuelle est conservée.",
                        Icons.Rounded.Refresh
                    )
                    return@launch
                }
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
        withContext(Dispatchers.Default) { ChannelText.setCategories(catalog.categories.map { it.name }) }
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
        val eventIds = catalog.channels.asSequence().filter { it.lane == BrowseLane.EVENTS.label }.map { it.id }.toHashSet()
        if (recent.removeAll { it in eventIds }) persistUsage()
        lastUpdated = savedAt
        android.util.Log.i("AppState", "Catalog applied in ${android.os.SystemClock.elapsedRealtime() - started} ms")
        // Derived fields are lazy. Resolve them in the background right after Home shows, so neither Home
        // nor the lists parse 24k names while drawing (a ~4 s freeze), and Home does not wait for them either.
        scope.launch {
            val perQuality = withContext(Dispatchers.Default) {
                catalog.channels.forEach { it.country; it.event; it.searchKey; it.nameKey }
                catalog.channels.filter { it.quality != null }.groupBy { it.quality!! }
                    .mapValues { (_, list) -> list.groupingBy { it.lane }.eachCount() }
            }
            if (channels === catalog.channels) qualityLaneCounts = perQuality
        }
    }
    // endregion

    // region Channels
    fun channelsFor(lane: BrowseLane, categoryId: String?): List<Channel> {
        val list = when {
            categoryId != null -> channels.filter { it.categoryId == categoryId }
            lane == BrowseLane.FAVORITES -> return favorites.mapNotNull(channelsById::get)
            lane == BrowseLane.RECENT -> return recent.mapNotNull(channelsById::get).filter { it.lane != BrowseLane.EVENTS.label }
            lane == BrowseLane.ALL -> channels
            lane == BrowseLane.EVENTS -> return eventsInOrder(channels.filter { it.lane == lane.label })
                // Providers publish most matches several times (main/backup groups, two naming styles):
                // list each match once, keeping a copy that has not failed. All stay reachable from their category.
                .let(ChannelText::distinctMatches)
            else -> channels.filter { it.lane == lane.label }
        }
        // Working channels first, then the viewer's own countries, then general-interest groups.
        // The provider order is kept otherwise (sort is stable).
        val countries = preferredCountries[lane].orEmpty()
        return list.sortedWith(
            compareBy<Channel>(
                { if (isUnavailable(it.id)) 1 else 0 },
                { if (categoryId != null) 0 else countries.indexOf(it.country).let { i -> if (i < 0) countries.size else i } },
                { ChannelText.categoryRank(it.category) }
            )
        )
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
        // Each match once, as in the Événements list.
        val matches = ChannelText.distinctMatches(channels.filter { it.lane == BrowseLane.EVENTS.label })
        var live = 0
        var soon = 0
        matches.forEach { channel ->
            val info = channel.event ?: return@forEach
            when (info.status(now)) {
                ChannelText.EventStatus.LIVE -> live++
                ChannelText.EventStatus.UPCOMING -> if ((info.start ?: 0) - now < 36 * 3_600_000L) soon++
                else -> Unit
            }
        }
        return live to soon
    }

    fun search(all: List<Channel>, query: String, quality: ChannelText.VideoQuality? = null, checkActive: () -> Unit = {}): List<Channel> {
        val base = if (quality == null) all else all.filter { it.quality == quality }
        val tokens = ChannelText.tokens(query)
        if (tokens.isEmpty()) return base
        return base.mapNotNull { channel ->
            checkActive()
            ChannelText.score(channel, tokens)?.let { channel to it }
        }
            .sortedWith(compareBy({ it.second }, { if (isUnavailable(it.first.id)) 1 else 0 }))
            .map { it.first }
    }

    fun play(channel: Channel, playlist: List<Channel>) {
        rememberWatched(channel.id)
        navigate(Page.Player(channel.id, playlist.map { it.id }))
    }

    private fun guideHome(): Page {
        val france = channelsFor(BrowseLane.FRANCE, null)
        val filtered = france.filter { qualityFilter == null || it.quality == qualityFilter }
        val list = filtered.ifEmpty { france }.ifEmpty { channels }
        val scope = listOfNotNull(if (france.isEmpty()) "Toutes les chaînes" else "France",
            qualityFilter?.label.takeIf { filtered.isNotEmpty() }).joinToString(" · ")
        return list.firstOrNull()?.let { Page.Guide(it.id, list.map(Channel::id), scope) } ?: Page.Home
    }

    fun openGuide(channel: Channel, playlist: List<Channel>) {
        val origin = page
        val scope = if (origin is Page.Browse) {
            val selection = origin.categoryId?.let { categoriesById[it]?.displayName } ?: origin.lane.label
            listOfNotNull(selection, qualityFilter?.label.takeIf {
                origin.categoryId != null || origin.lane !in setOf(BrowseLane.FAVORITES, BrowseLane.RECENT, BrowseLane.EVENTS)
            }, origin.country?.let(ChannelText::countryName),
                origin.query.takeIf(String::isNotBlank)?.let { "Recherche « $it »" }).joinToString(" · ")
        } else "Sélection de lecture"
        navigate(Page.Guide(channel.id, playlist.map(Channel::id), scope))
    }

    fun watchFromGuide(channel: Channel, playlist: List<Channel>) {
        val previous = backStack.getOrNull(backStack.lastIndex - 1)
        if (previous is Page.Player) {
            back()
            zapTo(channel.id)
        } else {
            rememberWatched(channel.id)
            navigate(Page.Player(channel.id, playlist.map(Channel::id)))
        }
    }

    fun zapTo(channelId: String) {
        val current = page as? Page.Player ?: return
        if (current.channelId != channelId) previousChannelId = current.channelId
        rememberWatched(channelId)
        replaceTop(current.copy(channelId = channelId))
    }

    private fun rememberWatched(channelId: String) {
        selectedChannelId = channelId
        // An event feed carries a different programme after the match, so it never goes to "Reprendre".
        if (channelsById[channelId]?.lane == BrowseLane.EVENTS.label) return
        recent.remove(channelId)
        recent.add(0, channelId)
        while (recent.size > 20) recent.removeAt(recent.lastIndex)
        persistUsage()
    }

    fun removeRecent(channel: Channel) {
        if (recent.remove(channel.id)) {
            persistUsage()
            notice = Notice("${channel.displayName} retirée des récents", Icons.Rounded.History)
        }
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
