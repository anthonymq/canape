package fr.channelfinder.tv

import android.net.Uri
import android.os.CancellationSignal
import org.json.JSONArray
import org.json.JSONObject

enum class LibraryKind(val title: String, val path: String) {
    MOVIES("Films", "movie"), SERIES("Séries", "series")
}

data class LibraryItem(
    val id: String, val title: String, val categoryId: String = "",
    val artwork: String = "", val extension: String = "mp4",
    val season: Int = 0, val episode: Int = 0,
    val overview: String = "", val year: String = "", val rating: String = "", val backdrop: String = ""
) {
    val searchKey: String by lazy { ChannelText.normalize(title) }
    val presentation: LibraryPresentation by lazy { libraryPresentation(title, year) }
    val sortKey: String by lazy { ChannelText.normalize(presentation.title) }
}

data class LibraryPresentation(val title: String, val year: Int?, val quality: String?)

private val libraryYearSuffix = Regex("\\s+[\\[(]((?:19|20)\\d{2})[\\])]$")
private val libraryQualitySuffix = Regex("\\s+(?:[\\[(](4K|UHD|FHD|HD|SD|2160p|1080p|720p)[\\])]|[-|]\\s*(4K|UHD|FHD|HD|SD|2160p|1080p|720p))$", RegexOption.IGNORE_CASE)
private val libraryDatedSuffix = Regex("\\s+[-|]\\s*((?:19|20)\\d{2})(?:\\s+(4K|UHD|FHD|HD|SD|2160p|1080p|720p))?$", RegexOption.IGNORE_CASE)

internal fun libraryPresentation(rawTitle: String, metadataYear: String): LibraryPresentation {
    var title = rawTitle.trim()
    var year = metadataYear.trim().takeIf { it.matches(Regex("(?:19|20)\\d{2}")) }?.toInt()
    var quality: String? = null
    val datedSuffix = libraryDatedSuffix.find(title)
    if (datedSuffix != null && (year == null || year == datedSuffix.groupValues[1].toInt())) {
        year = datedSuffix.groupValues[1].toInt()
        quality = datedSuffix.groupValues[2].takeIf { it.isNotBlank() }?.uppercase()
        title = title.substring(0, datedSuffix.range.first).trimEnd()
    }
    repeat(2) {
        val qualityMatch = libraryQualitySuffix.find(title)
        val yearMatch = libraryYearSuffix.find(title)
        if (quality == null && qualityMatch != null) {
            quality = qualityMatch.groupValues.drop(1).first { it.isNotBlank() }.uppercase()
            title = title.substring(0, qualityMatch.range.first).trimEnd()
        } else if (yearMatch != null && (year == null || year == yearMatch.groupValues[1].toInt())) {
            year = yearMatch.groupValues[1].toInt()
            title = title.substring(0, yearMatch.range.first).trimEnd()
        }
    }
    return LibraryPresentation(title, year, quality)
}

data class LibraryCatalog(val categories: List<ChannelCategory>, val items: List<LibraryItem>)

internal fun parseLibraryItems(json: JSONArray, kind: LibraryKind): List<LibraryItem> = buildList {
    for (index in 0 until json.length()) {
        val item = json.optJSONObject(index) ?: continue
        val id = item.optString(if (kind == LibraryKind.MOVIES) "stream_id" else "series_id")
        val title = item.optString("name").trim()
        if (id.isBlank() || id == "null" || title.isBlank() || title == "null") continue
        add(LibraryItem(id, title, item.optString("category_id"),
            item.optString(if (kind == LibraryKind.MOVIES) "stream_icon" else "cover"),
            item.optString("container_extension").takeIf { it.matches(Regex("[a-zA-Z0-9]+")) } ?: "mp4",
            overview = item.optString("plot").takeUnless { it == "null" }.orEmpty(),
            year = item.optString("year").takeUnless { it == "null" }.orEmpty(),
            rating = item.optString("rating").takeUnless { it == "null" || it == "0" }.orEmpty(),
            backdrop = item.optJSONArray("backdrop_path")?.optString(0).orEmpty()))
    }
}.distinctBy { it.id }

internal fun parseEpisodes(json: JSONObject): List<LibraryItem> {
    val seasons = json.optJSONObject("episodes")
    val groups = seasons?.keys()?.asSequence()?.mapNotNull { key ->
        seasons.optJSONArray(key)?.let { (key.toIntOrNull() ?: 0) to it }
    }?.toList() ?: listOfNotNull(json.optJSONArray("episodes")?.let { 0 to it })
    return buildList {
        groups.forEach { (season, entries) ->
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val id = entry.optString("id")
                if (id.isBlank() || id == "null") continue
                val number = entry.optInt("episode_num", index + 1)
                add(LibraryItem(id, entry.optString("title").takeUnless { it.isBlank() || it == "null" }
                    ?: "Épisode $number", extension = entry.optString("container_extension")
                    .takeIf { it.matches(Regex("[a-zA-Z0-9]+")) } ?: "mp4",
                    season = entry.optInt("season", season), episode = number))
            }
        }
    }.distinctBy { it.id }.sortedWith(compareBy({ it.season }, { it.episode }))
}

class VodRepository(private val api: XtreamRepository) {
    fun load(account: XcAccount, kind: LibraryKind, cancellation: CancellationSignal): LibraryCatalog = api.translateErrors {
        val prefix = if (kind == LibraryKind.MOVIES) "vod" else "series"
        val categories = JSONArray(api.request(account, "get_${prefix}_categories", cancellation = cancellation))
        val rows = buildList {
            for (index in 0 until categories.length()) {
                val row = categories.optJSONObject(index) ?: continue
                val id = row.optString("category_id")
                if (id.isNotBlank()) add(ChannelCategory(id, row.optString("category_name", "Autres")))
            }
        }
        val action = if (kind == LibraryKind.MOVIES) "get_vod_streams" else "get_series"
        LibraryCatalog(rows, parseLibraryItems(JSONArray(api.request(account, action, cancellation = cancellation)), kind))
    }

    fun episodes(account: XcAccount, id: String, cancellation: CancellationSignal): List<LibraryItem> = api.translateErrors {
        parseEpisodes(JSONObject(api.request(account, "get_series_info", "&series_id=${Uri.encode(id)}", cancellation = cancellation)))
    }

    fun movieDetails(account: XcAccount, item: LibraryItem, cancellation: CancellationSignal): LibraryItem = api.translateErrors {
        val info = JSONObject(api.request(account, "get_vod_info", "&vod_id=${Uri.encode(item.id)}", cancellation = cancellation))
            .optJSONObject("info") ?: return@translateErrors item
        item.copy(overview = info.optString("plot").takeUnless { it == "null" }.orEmpty(),
            year = info.optString("releasedate").take(4).takeUnless { it == "null" }.orEmpty(),
            rating = info.optString("rating").takeUnless { it == "null" || it == "0" }.orEmpty(),
            backdrop = info.optJSONArray("backdrop_path")?.optString(0).orEmpty())
    }

    fun stream(account: XcAccount, item: LibraryItem, kind: LibraryKind): Uri = Uri.parse(
        "${account.serverUrl.trimEnd('/')}/${kind.path}/${Uri.encode(account.username)}/${Uri.encode(account.password)}/${Uri.encode(item.id)}.${item.extension}"
    )
}
