package fr.channelfinder.tv

import java.text.Normalizer

/** Display cleanup, lane heuristics and accent-insensitive search for provider labels. */
object ChannelText {
    data class Label(val title: String, val country: String?, val badges: List<String>)

    enum class VideoQuality(val label: String, val badge: String) {
        UHD("4K", "4K"), FHD("Full HD", "FHD"), HD("HD", "HD"), SD("SD", "SD")
    }

    // Provider prefixes that are spelling variants of a country code.
    private val countryAliases = mapOf(
        "IND" to "IN", "USA" to "US", "ITA" to "IT", "UK" to "GB", "SR" to "RS", "PERU" to "PE", "COL" to "CO",
        "EGY" to "EG", "PAN" to "PA", "RDOM" to "DO", "MEX" to "MX", "ESP" to "ES", "POR" to "PT", "BRA" to "BR"
    )
    // Prefixes providers use for regions or languages, which collide with real ISO codes (AR = Arabic, AF = Africa…).
    private val notCountryCodes = setOf("AR", "AF", "AS", "LA", "LT", "EU", "EN", "KU", "KD", "EX")
    private val isoCountries = java.util.Locale.getISOCountries().toSet()

    /** ISO country code behind a provider prefix, or null when it is a region, language or label. */
    fun countryCode(tag: String?): String? {
        val code = tag?.uppercase()?.let { countryAliases[it] ?: it } ?: return null
        return code.takeIf { it.length == 2 && it in isoCountries && it !in notCountryCodes }
    }

    /** "🇧🇷 Brésil" for "BR". */
    fun countryLabel(code: String): String {
        val flag = code.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
        val name = java.util.Locale("", code).getDisplayCountry(java.util.Locale.FRENCH).replaceFirstChar { it.titlecase(java.util.Locale.FRENCH) }
        return "$flag $name"
    }

    /** Best quality named in the channel name, else in its category ("FRANCE • UHD/4K", "FRANCE • SD"). */
    fun quality(nameBadges: List<String>, categoryBadges: List<String>): VideoQuality? {
        fun pick(badges: List<String>) = VideoQuality.entries.firstOrNull { it.badge in badges }
        return pick(nameBadges) ?: pick(categoryBadges)
    }

    // "|FR| TF1", "FR ‖ FRANCE", "┃PT┃ RTP", "[ES] …", "FR: …", "FR - …"
    private val countryPrefix = Regex("""^\s*[|\[(┃‖]?\s*([A-Z]{2,4})\s*[|\])┃‖:\-•]+\s*""")
    private val notCountries = setOf("TV", "HD", "SD", "FHD", "UHD", "VIP", "PPV", "NEW", "LIVE")
    private val quality = Regex("""(?i)(?<![\p{L}\p{N}])(4K|UHD|FHD|FULL\s*HD|HD|SD|HEVC|H\.?265|50\s*FPS|60\s*FPS|RAW)(?![\p{L}\p{N}])""")
    private val trailingJunk = Regex("""[\s/|\-–·•:‖┃]+$""")
    private val leadingJunk = Regex("""^[\s/|\-–·•:‖┃]+""")
    private val spaces = Regex("""\s{2,}""")
    private val diacritics = Regex("""\p{Mn}+""")

    private val categoryLabels = java.util.concurrent.ConcurrentHashMap<String, Label>()

    /** Category labels repeat across thousands of channels, so parse each one once. */
    fun categoryLabel(raw: String): Label = categoryLabels.getOrPut(raw) { parseLabel(raw) }

    fun parseLabel(raw: String): Label {
        var text = raw.trim()
        var country: String? = null
        countryPrefix.find(text)?.let { match ->
            val code = match.groupValues[1]
            if (code !in notCountries && match.range.last + 1 < text.length) {
                country = code
                text = text.substring(match.range.last + 1)
            }
        }
        val badges = LinkedHashSet<String>()
        quality.findAll(text).forEach { badges += normalizeBadge(it.value) }
        val stripped = quality.replace(text, " ")
            .replace(trailingJunk, "")
            .replace(leadingJunk, "")
            .replace(spaces, " ")
            .trim()
        val title = stripped.ifBlank { text.replace(trailingJunk, "").trim().ifBlank { raw.trim() } }
        return Label(title, country, if (stripped.isBlank()) emptyList() else badges.toList())
    }

    private fun normalizeBadge(value: String): String = when (val upper = value.uppercase().replace(" ", "")) {
        "FULLHD" -> "FHD"
        "H265", "H.265" -> "HEVC"
        "UHD" -> "4K"
        else -> upper
    }

    fun initials(title: String): String {
        val words = title.split(' ').filter { it.any(Char::isLetterOrDigit) }
        val first = words.firstOrNull().orEmpty()
        return when {
            first.length in 2..4 && first.any(Char::isDigit) -> first.uppercase()
            words.size >= 2 -> "${words[0].first { it.isLetterOrDigit() }}${words[1].first { it.isLetterOrDigit() }}".uppercase()
            else -> first.filter(Char::isLetterOrDigit).take(2).uppercase()
        }.ifBlank { "TV" }
    }

    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).replace(diacritics, "").lowercase()

    fun tokens(query: String): List<String> = normalize(query).split(' ', '\t').filter(String::isNotBlank)

    /** Lower is better; null means no match. All tokens must appear somewhere in the channel text. */
    fun score(channel: Channel, tokens: List<String>): Int? {
        if (tokens.isEmpty()) return 0
        if (tokens.any { it !in channel.searchKey }) return null
        val name = channel.nameKey
        val phrase = tokens.joinToString(" ")
        return when {
            name == phrase -> 0
            name.startsWith(phrase) -> 1
            tokens.all { token -> name.split(' ').any { it.startsWith(token) } } -> 2
            tokens.all { it in name } -> 3
            else -> 4
        }
    }

    // Event feeds only carry a picture during a match: provider "EVENTS"/"PPV" groups, explicit
    // "event only" wording, match titles with a start time, and numbered match feeds ("CANAL+ LIVE 4").
    private val eventCategory = Regex("""(?i)\b(events?|ppv)\b|\[events]""")
    private val eventName = Regex(
        """(?i)(live event|event only|no event|\(live only\)|\bppv\b|\bevents?\s*\d|pay per view|""" +
            """\d{4}-\d{2}-\d{2}\s+\d{1,2}:\d{2}|@ [A-Z][a-z]{2} \d{1,2} \d{1,2}:\d{2}|^\W*(\d{1,2}:\d{2}|live)\s*[|\-]| vs\.? | @ )"""
    )
    private val matchFeed = Regex("""(?i)(canal\+\s*live|ligue\s*1\s*\+|ligue\+\s*1|ligue⚽1|la\s*liga|laliga|serie\s*a|betclic)\s*\d""")
    private val eventLanguages = setOf("FR", "PT", "BR")

    fun isEvent(category: String, name: String): Boolean =
        eventCategory.containsMatchIn(category) || eventName.containsMatchIn(name) || matchFeed.containsMatchIn(name)

    /**
     * The country code on the provider's category wins over words in the channel name, so a
     * Spanish category is never filed under Português because a channel is called "Trace Brasil".
     */
    fun classifyLane(category: String, name: String): String {
        val text = "$category $name".uppercase()
        val country = categoryLabel(category).country
        val base = when {
            // Sports only in French or Portuguese: other countries' sports stay in their category and search.
            listOf("SPORT", "ESPN", "BEIN", "EUROSPORT", "RMC", "PPV", "NFL", "NBA", "UFC").any(text::contains) -> when {
                country in eventLanguages -> BrowseLane.SPORTS.label
                country == null && listOf("|FR|", "FR |", "┃FR┃", "FR ‖", "FRANCE", "|PT|", "PT |", "PT ‖", "PORTUGAL", "BRASIL").any(text::contains) ->
                    BrowseLane.SPORTS.label
                else -> OTHER
            }
            country != null -> when {
                country == "FR" -> BrowseLane.FRANCE.label
                country in setOf("CA", "BE", "CH") && Regex("""\bFR\b|FRANCE""").containsMatchIn(category.uppercase()) -> BrowseLane.FRANCE.label
                country == "PT" || country == "BR" -> BrowseLane.PORTUGUESE.label
                else -> OTHER
            }
            listOf("FRANCE", "FR |", "FR┃", "┃FR┃", "FR ‖", "|FR|").any(text::contains) -> BrowseLane.FRANCE.label
            listOf("PORTUGAL", "PORTUGUE", "PORTUGUÊ", "BRASIL", "BRAZIL", "PT |", "PT┃", "PT ‖", "|PT|").any(text::contains) -> BrowseLane.PORTUGUESE.label
            else -> OTHER
        }
        if (!isEvent(category, name)) return base
        val relevant = base == BrowseLane.FRANCE.label || base == BrowseLane.PORTUGUESE.label ||
            (base == BrowseLane.SPORTS.label && country in eventLanguages)
        // Events for other audiences stay reachable through their category and search only.
        return if (relevant) BrowseLane.EVENTS.label else OTHER
    }

    const val OTHER = "Autres"

    enum class EventStatus { LIVE, UPCOMING, FEED, ENDED, IDLE }

    /** What an event feed currently carries, parsed from names such as "DAZN FR 003| Rennes vs. Auxerre | Ligue 1+ (2026-10-11 17:15:00)". */
    data class EventInfo(val title: String, val competition: String?, val feed: String?, val start: Long?, val idle: Boolean) {
        fun status(now: Long = System.currentTimeMillis()): EventStatus = when {
            idle -> EventStatus.IDLE
            start == null -> EventStatus.FEED
            now < start -> EventStatus.UPCOMING
            now <= start + EVENT_DURATION_MS -> EventStatus.LIVE
            else -> EventStatus.ENDED
        }
    }

    private const val EVENT_DURATION_MS = 3 * 60 * 60 * 1000L
    private val isoStart = Regex("""\(?\s*(\d{4})-(\d{2})-(\d{2})\s+(\d{1,2}):(\d{2})(:\d{2})?\s*\)?""")
    private val monthStart = Regex("""@\s*([A-Z][a-z]{2})\s+(\d{1,2})\s+(\d{1,2}):(\d{2})\s*(AM|PM)?""")
    private val leadingTime = Regex("""^\W*(\d{1,2})[:h](\d{2})\s*[|\-]\s*""")
    private val leadingLive = Regex("""(?i)^\W*live\s*\|\s*""")
    private val feedCode = Regex("""^\s*([A-Z][A-Za-z+]*(?:\s+[A-Z]{2})?\s+\d{2,3})\s*\|\s*""")
    private val trailingFeed = Regex("""\s*:\s*([A-Z+]+\s+[A-Z]{2}\s+\d{1,3})\s*$""")
    private val noEvent = Regex("""(?i)no event""")
    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun parseEvent(raw: String, fallbackTitle: String): EventInfo {
        var text = raw.trim()
        var start: Long? = null
        var feed: String? = null
        isoStart.find(text)?.let { m ->
            val (y, mo, d, h, mi) = m.destructured
            start = timestamp(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt())
            text = text.removeRange(m.range)
        }
        monthStart.find(text)?.let { m ->
            val month = months.indexOf(m.groupValues[1])
            if (month >= 0) {
                var hour = m.groupValues[3].toInt()
                if (m.groupValues[5] == "PM" && hour < 12) hour += 12
                if (m.groupValues[5] == "AM" && hour == 12) hour = 0
                val now = java.util.Calendar.getInstance()
                start = timestamp(now.get(java.util.Calendar.YEAR), month + 1, m.groupValues[2].toInt(), hour, m.groupValues[4].toInt())
            }
            text = text.removeRange(m.range)
        }
        leadingTime.find(text)?.let { m ->
            val now = java.util.Calendar.getInstance()
            start = timestamp(now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH) + 1,
                now.get(java.util.Calendar.DAY_OF_MONTH), m.groupValues[1].toInt(), m.groupValues[2].toInt())
            text = text.removeRange(m.range)
        }
        text = text.replace(leadingLive, "")
        feedCode.find(text)?.let { m -> feed = m.groupValues[1]; text = text.removeRange(m.range) }
        trailingFeed.find(text)?.let { m -> feed = m.groupValues[1].replace(Regex("""\s+"""), " "); text = text.removeRange(m.range) }
        val parts = text.split('|').map { it.trim().trim('-', '–', '•', '●').trim() }
            .filter { it.isNotBlank() && !it.equals("all", true) && !Regex("""^[A-Z]+-[A-Z]{2}$""").matches(it) }
        val idle = noEvent.containsMatchIn(raw)
        val title = if (idle || start == null) fallbackTitle else parts.firstOrNull() ?: fallbackTitle
        val competition = if (start != null) parts.getOrNull(1) else null
        return EventInfo(title, competition, feed, start, idle)
    }

    private fun timestamp(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        java.util.Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        }.timeInMillis

    /** Short French label such as "En cours", "Aujourd’hui 19:00", "Demain 17:15" or "sam. 12 oct. 21:00". */
    fun eventTimeLabel(info: EventInfo, now: Long = System.currentTimeMillis()): String {
        val start = info.start
        return when (info.status(now)) {
            EventStatus.LIVE -> "En cours"
            EventStatus.ENDED -> "Terminé"
            EventStatus.FEED -> "Pendant les matchs"
            EventStatus.IDLE -> "Aucun événement"
            EventStatus.UPCOMING -> {
                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE).format(java.util.Date(start!!))
                val days = dayIndex(start) - dayIndex(now)
                when (days) {
                    0L -> "Aujourd’hui $time"
                    1L -> "Demain $time"
                    else -> java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.FRANCE).format(java.util.Date(start)) + " $time"
                }
            }
        }
    }

    private fun dayIndex(time: Long): Long {
        val zone = java.util.TimeZone.getDefault()
        return (time + zone.getOffset(time)) / 86_400_000L
    }

    // General-interest groups first, regional and local ones last. Keywords only, never provider IDs.
    private val generalGroups = Regex("""(?i)g[ée]n[ée]ral|g[ée]n[ée]raliste|\btnt\b|^\W*(portugal|brasil|france)\W*$""")
    private val secondaryGroups = Regex("""(?i)r[ée]gion|local|dom-tom|pluto|backup|\bvip\b|hevc|\bsd\b""")

    fun categoryRank(category: String): Int {
        val title = categoryLabel(category).title
        return when {
            generalGroups.containsMatchIn(title) -> 0
            Regex("""(?i)\binfo""").containsMatchIn(title) -> 1
            secondaryGroups.containsMatchIn(title) -> 3
            else -> 2
        }
    }

    /** Provider "separator" rows such as "##### FRANCE #####" carry no stream worth showing. */
    fun isSeparator(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.startsWith("###") || trimmed.startsWith("===") || trimmed.startsWith("***") ||
            trimmed.startsWith("---") || trimmed.startsWith("▬▬")
    }
}
