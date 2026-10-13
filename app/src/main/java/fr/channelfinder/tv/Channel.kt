package fr.channelfinder.tv

data class XcAccount(
    val serverUrl: String,
    val username: String,
    val password: String
)

data class ChannelCategory(
    val id: String,
    val name: String
) {
    private val label get() = ChannelText.categoryLabel(name)
    val displayName: String get() = label.title
    val countryTag: String? get() = label.country
    val lane: String by lazy { ChannelText.classifyLane(name, "") }
    val searchKey: String by lazy { ChannelText.normalize(name) }
}

data class XtreamCatalog(
    val categories: List<ChannelCategory>,
    val channels: List<Channel>
)

data class Channel(
    val id: String,
    val name: String,
    val categoryId: String,
    val category: String,
    val logoUrl: String,
    val epgId: String,
    val lane: String
) {
    // Derived lazily: building ~24k channels must stay fast, and most are never displayed or searched.
    private val label by lazy { ChannelText.parseLabel(name) }
    private val categoryLabel get() = ChannelText.categoryLabel(category)

    /** Set for match/event feeds, which only carry a picture during an event. */
    val event: ChannelText.EventInfo? by lazy {
        if (lane == BrowseLane.EVENTS.label || ChannelText.isEvent(category, name)) ChannelText.parseEvent(name, label.title) else null
    }

    /** Provider name without the country prefix and quality suffixes, e.g. "|FR| TF1 HD" -> "TF1"; the match for events. */
    val displayName: String get() = event?.title ?: label.title
    val categoryDisplay: String
        get() = event?.let { info -> listOfNotNull(info.competition, info.feed).joinToString(" · ").ifBlank { null } } ?: categoryLabel.title
    val countryTag: String? get() = label.country ?: categoryLabel.country
    val badges: List<String> get() = label.badges
    val country: String? by lazy { ChannelText.countryCode(label.country) ?: ChannelText.countryCode(categoryLabel.country) }
    val quality: ChannelText.VideoQuality? by lazy { ChannelText.quality(label.badges, categoryLabel.badges) }
    val initials: String get() = ChannelText.initials(label.title)

    val nameKey: String by lazy { ChannelText.normalize(label.title) }
    val searchKey: String by lazy { ChannelText.normalize("$name $category $lane") }
}

enum class BrowseLane(val label: String) {
    FRANCE("France"), PORTUGUESE("Português"), SPORTS("Sports"), EVENTS("Événements"),
    FAVORITES("Favoris"), RECENT("Récents"), ALL("Toutes")
}

/** Destinations of the navigation drawer, in drawer order. */
enum class NavDest { SEARCH, HOME, FRANCE, PORTUGUESE, SPORTS, EVENTS, FAVORITES, MOVIES, SERIES, GUIDE, CATEGORIES, SETTINGS }

sealed interface Page {
    data object Loading : Page
    data class LoadError(val message: String) : Page
    data class Setup(val message: String = "") : Page
    data object Home : Page
    data object Settings : Page
    data class Library(val kind: LibraryKind) : Page
    data class Episodes(val series: LibraryItem) : Page
    data class MovieDetails(val item: LibraryItem) : Page
    data class VodPlayer(val item: LibraryItem, val kind: LibraryKind, val series: LibraryItem? = null, val startOver: Boolean = false) : Page
    data class CategoryPicker(val query: String = "") : Page
    data class Browse(
        val lane: BrowseLane,
        val categoryId: String? = null,
        val query: String = "",
        val startSearch: Boolean = false,
        /** ISO country filter, only offered when the list mixes several countries. */
        val country: String? = null
    ) : Page
    data class Player(val channelId: String, val playlist: List<String>) : Page
    data class Guide(val channelId: String, val playlist: List<String>) : Page
}
