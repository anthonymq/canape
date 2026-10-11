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

    /** Provider name without the country prefix and quality suffixes, e.g. "|FR| TF1 HD" -> "TF1". */
    val displayName: String get() = label.title
    val categoryDisplay: String get() = categoryLabel.title
    val countryTag: String? get() = label.country ?: categoryLabel.country
    val badges: List<String> get() = label.badges
    val initials: String get() = ChannelText.initials(label.title)

    val nameKey: String by lazy { ChannelText.normalize(label.title) }
    val searchKey: String by lazy { ChannelText.normalize("$name $category $lane") }
}

enum class BrowseLane(val label: String) {
    FRANCE("France"), PORTUGUESE("Português"), SPORTS("Sports"), FAVORITES("Favoris"), RECENT("Récents"), ALL("Toutes")
}

sealed interface Page {
    data object Loading : Page
    data class LoadError(val message: String) : Page
    data class Setup(val message: String = "") : Page
    data object Home : Page
    data object Settings : Page
    data class CategoryPicker(val query: String = "") : Page
    data class Browse(
        val lane: BrowseLane,
        val categoryId: String? = null,
        val query: String = "",
        val startSearch: Boolean = false
    ) : Page
    data class Player(val channelId: String, val playlist: List<String>) : Page
}
