package fr.channelfinder.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest

data class VodWatch(
    val item: LibraryItem, val kind: LibraryKind, val series: LibraryItem?,
    val positionMs: Long, val durationMs: Long, val updatedAt: Long, val completed: Boolean
) {
    val key: String get() = "${kind.name}:${item.id}"
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val resumeMs: Long get() = if (completed) 0L else positionMs
    val displayItem: LibraryItem get() = series ?: item
}

data class SavedTitle(val item: LibraryItem, val kind: LibraryKind) {
    val key: String get() = "${kind.name}:${item.id}"
}

internal fun watchCompleted(position: Long, duration: Long, ended: Boolean): Boolean =
    ended || (duration > 0 && position >= duration * 0.95)

internal fun continueWatching(entries: List<VodWatch>, kind: LibraryKind): List<VodWatch> = entries
    .filter { it.kind == kind }
    .sortedByDescending { it.updatedAt }
    .distinctBy { it.series?.id ?: it.item.id }
    .filter { !it.completed && it.positionMs >= 5_000 }

class VodWatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("vod_watch_history", Context.MODE_PRIVATE)
    private var accountKey = ""
    private var history by mutableStateOf(emptyList<VodWatch>())
    private var saved by mutableStateOf(emptyList<SavedTitle>())

    fun entries(account: XcAccount): List<VodWatch> {
        val key = MessageDigest.getInstance("SHA-256").digest(
            "${account.serverUrl.trimEnd('/')}\n${account.username}\n${account.password}".toByteArray()
        ).joinToString("") { "%02x".format(it) }
        if (key != accountKey) {
            accountKey = key
            history = try { parseWatchHistory(prefs.getString(key, "[]").orEmpty()) }
            catch (_: JSONException) { emptyList() }
            saved = try { parseSavedTitles(prefs.getString("$key:saved", "[]").orEmpty()) }
            catch (_: JSONException) { emptyList() }
        }
        return history
    }

    fun find(account: XcAccount, item: LibraryItem, kind: LibraryKind): VodWatch? =
        entries(account).firstOrNull { it.item.id == item.id && it.kind == kind }

    fun savedTitles(account: XcAccount): List<SavedTitle> {
        entries(account)
        return saved
    }

    fun toggleSaved(account: XcAccount, item: LibraryItem, kind: LibraryKind) {
        entries(account)
        val title = SavedTitle(item, kind)
        saved = if (saved.any { it.key == title.key }) saved.filter { it.key != title.key }
        else (listOf(title) + saved).take(500)
        prefs.edit().putString("$accountKey:saved", serializeSavedTitles(saved)).apply()
    }

    fun save(account: XcAccount, entry: VodWatch) {
        val current = entries(account)
        history = (listOf(entry) + current.filter { it.key != entry.key }).take(100)
        persist()
    }

    fun remove(account: XcAccount, entry: VodWatch) {
        history = entries(account).filter { it.key != entry.key }
        persist()
    }

    private fun persist() {
        prefs.edit().putString(accountKey, serializeWatchHistory(history)).apply()
    }
}

internal fun serializeWatchHistory(entries: List<VodWatch>): String = JSONArray().apply { entries.forEach { put(it.toJson()) } }.toString()

private fun LibraryItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("title", title); put("category", categoryId); put("art", artwork)
    put("extension", extension); put("season", season); put("episode", episode)
    put("backdrop", backdrop); put("year", year); put("rating", rating); put("overview", overview)
}

private fun parseWatchItem(json: JSONObject): LibraryItem? {
    val id = json.optString("id")
    val title = json.optString("title")
    if (id.isBlank() || title.isBlank()) return null
    return LibraryItem(id, title, json.optString("category"), json.optString("art"),
        json.optString("extension", "mp4"), json.optInt("season"), json.optInt("episode"),
        backdrop = json.optString("backdrop"), year = json.optString("year"), rating = json.optString("rating"),
        overview = json.optString("overview"))
}

internal fun serializeSavedTitles(titles: List<SavedTitle>): String = JSONArray().apply {
    titles.forEach { title -> put(JSONObject().put("kind", title.kind.name).put("item", title.item.toJson())) }
}.toString()

internal fun parseSavedTitles(text: String): List<SavedTitle> = buildList {
    val array = JSONArray(text)
    for (index in 0 until array.length()) {
        val json = array.optJSONObject(index) ?: continue
        val kind = LibraryKind.entries.firstOrNull { it.name == json.optString("kind") } ?: continue
        val item = json.optJSONObject("item")?.let(::parseWatchItem) ?: continue
        add(SavedTitle(item, kind))
    }
}.distinctBy { it.key }.take(500)

private fun VodWatch.toJson(): JSONObject = JSONObject().apply {
    put("item", item.toJson()); put("kind", kind.name); put("series", series?.toJson())
    put("position", positionMs); put("duration", durationMs); put("updated", updatedAt); put("completed", completed)
}

internal fun parseWatchHistory(text: String): List<VodWatch> = buildList {
    val array = JSONArray(text)
    for (index in 0 until array.length()) {
        val json = array.optJSONObject(index) ?: continue
        val item = json.optJSONObject("item")?.let(::parseWatchItem) ?: continue
        val kind = LibraryKind.entries.firstOrNull { it.name == json.optString("kind") } ?: continue
        add(VodWatch(item, kind, json.optJSONObject("series")?.let(::parseWatchItem),
            json.optLong("position").coerceAtLeast(0), json.optLong("duration").coerceAtLeast(0),
            json.optLong("updated"), json.optBoolean("completed")))
    }
}

fun watchTime(position: Long): String {
    val seconds = (position / 1000).coerceAtLeast(0)
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
