package fr.channelfinder.tv

internal fun homeFocusAfterRemoval(
    orderedKeys: List<String>, removedKey: String, emptyKey: String, visibleLimit: Int = orderedKeys.size
): String {
    val removedIndex = orderedKeys.indexOf(removedKey)
    val survivors = orderedKeys.filterNot { it == removedKey }.take(visibleLimit)
    return survivors.getOrNull(removedIndex.coerceAtMost(survivors.lastIndex)) ?: emptyKey
}
