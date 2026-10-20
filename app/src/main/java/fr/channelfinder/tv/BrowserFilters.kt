package fr.channelfinder.tv

internal fun browseCountryPreferenceKey(page: Page.Browse): String =
    page.categoryId?.let { "browse_country:category:$it" } ?: "browse_country:lane:${page.lane.name}"

internal fun browseCountryOptions(channels: List<Channel>): List<String> = channels
    .mapNotNull { it.country }
    .groupingBy { it }.eachCount()
    .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    .map { it.key }
