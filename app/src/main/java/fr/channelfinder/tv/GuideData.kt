package fr.channelfinder.tv

import android.os.CancellationSignal
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.io.encoding.Base64

internal fun guideChannelsForDisplay(
    channels: List<Channel>, onlyWithProgramme: Boolean, availability: Map<String, Boolean>, selectedId: String
): List<Channel> = channels.filter {
    !onlyWithProgramme || it.id == selectedId || availability[it.id] != false
}

data class TvProgramme(val title: String, val description: String, val start: Long, val end: Long) {
    val key: String get() = "$start:$end:$title"
    fun isLive(now: Long): Boolean = start <= now && now < end
    fun timeLabel(): String = "${guideTime(start)} – ${guideTime(end)}"
    fun dayLabel(): String = SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(Date(start))
}

fun guideTime(time: Long): String = SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(time))

/** Epoch timestamps are authoritative; timezone-less provider date strings are never guessed. */
internal fun parseGuide(body: String): List<TvProgramme> {
    val trimmed = body.trim()
    if (trimmed == "[]") return emptyList()
    val root = JSONObject(trimmed)
    if (root.optJSONObject("user_info")?.optInt("auth", 1) == 0) {
        throw CatalogException("Le fournisseur a refusé l’accès au guide.")
    }
    val rows = root.optJSONArray("epg_listings")
        ?: throw CatalogException("Le fournisseur n’a pas renvoyé de guide lisible.")
    return parseProgrammes(rows)
}

private fun parseProgrammes(rows: JSONArray): List<TvProgramme> = buildList {
    for (index in 0 until rows.length()) {
        val row = rows.optJSONObject(index) ?: continue
        val start = epochMillis(row.optString("start_timestamp")) ?: continue
        val end = epochMillis(row.optString("stop_timestamp")) ?: continue
        if (end <= start) continue
        val title = decodeGuideText(row.optString("title"))
        if (title.isBlank()) continue
        add(TvProgramme(title.take(500), decodeGuideText(row.optString("description")).take(12_000), start, end))
    }
}.distinctBy(TvProgramme::key).sortedBy(TvProgramme::start)

private fun epochMillis(value: String): Long? {
    val number = value.toLongOrNull() ?: return null
    if (number <= 0) return null
    val millis = if (number < 100_000_000_000L) number * 1000 else number
    return millis.takeIf { it in 946_684_800_000L..4_102_444_800_000L }
}

internal fun decodeGuideText(value: String): String {
    val raw = value.trim()
    if (raw == "null") return ""
    val compact = raw.filterNot(Char::isWhitespace)
    val decoded = try {
        val bytes = Base64.Default.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(compact)
        val canonical = Base64.Default.encode(bytes).trimEnd('=')
        if (canonical != compact.trimEnd('=')) raw else {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            if (text.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) raw else text
        }
    } catch (_: IllegalArgumentException) { raw }
      catch (_: java.nio.charset.CharacterCodingException) { raw }
    return decoded.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&#39;", "’").replace("&nbsp;", " ").trim()
}

/** Bounded metadata cache, isolated by account, with no credential or programme logging. */
class GuideRepository(private val repository: XtreamRepository) {
    private data class Entry(val loadedAt: Long, val programmes: List<TvProgramme>)
    private val entries = LinkedHashMap<String, Entry>()
    private var cacheAccount: XcAccount? = null

    fun load(account: XcAccount, channelId: String, cancellation: CancellationSignal, refresh: Boolean = false): List<TvProgramme> {
        synchronized(entries) {
            if (cacheAccount != account) { entries.clear(); cacheAccount = account }
            val cached = entries[channelId]
            if (!refresh && cached != null && System.currentTimeMillis() - cached.loadedAt < 15 * 60_000L) return cached.programmes
        }
        val programmes = repository.loadGuide(account, channelId, cancellation)
        cancellation.throwIfCanceled()
        synchronized(entries) {
            if (cacheAccount == account) {
                entries.remove(channelId)
                entries[channelId] = Entry(System.currentTimeMillis(), programmes)
                while (entries.size > 40) entries.remove(entries.keys.first())
            }
        }
        return programmes
    }
}
